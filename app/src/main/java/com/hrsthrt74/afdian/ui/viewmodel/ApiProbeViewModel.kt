package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.probe.ApiProbeCatalog
import com.hrsthrt74.afdian.data.probe.ApiProbeExecutor
import com.hrsthrt74.afdian.data.probe.ApiProbeResult
import com.hrsthrt74.afdian.data.probe.ApiProbeSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 页面顶部的环境信息快照。
 *
 * 需要刷新时由用户点页面里的「刷新」或重新进入页面触发 [ApiProbeViewModel.refreshEnvironment]。
 */
data class ProbeEnvironment(
    /** 打包变体名，如 standardDebug / legacyRelease */
    val variantName: String,
    /** 本地是否已有 auth_token。注意只暴露布尔值，绝不暴露 token 本身 */
    val loggedIn: Boolean,
)

/**
 * 接口调试页的状态编排。
 *
 * 职责划分：
 * - 每张探针卡拥有**完全独立**的状态（未运行 / 请求中 / 成功 / 失败），
 *   一个接口失败不影响其他接口；
 * - 一键全跑用信号量限制并发，避免十几个请求瞬时打爆服务端被限流；
 * - 用「批次号（[runEpoch]）」丢弃过期的在飞结果，避免取消后旧结果回写覆盖新一轮状态。
 *
 * 线程约定：所有 StateFlow 写入都发生在 viewModelScope（主线程）；
 * 只有 token 读取与网络请求下到 [Dispatchers.IO]（由 [ApiProbeExecutor] 内部负责）。
 */
class ApiProbeViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 单张探针卡的状态：定义固定 + 展开位 + 运行结果。
     *
     * 展开位放在这里而不是 Composable 的 remember 里，是为了让「清空结果」能一并收起，
     * 滚动离屏重建时也不会丢。
     */
    data class Card(
        val spec: ApiProbeSpec,
        val expanded: Boolean = false,
        val result: ApiProbeResult = ApiProbeResult.Idle,
    )

    private val tokenStore = AuthTokenStore(application)

    /** 探针执行器。internal 类型只作为私有属性持有，不外泄到公开签名。 */
    private val executor = ApiProbeExecutor(tokenStore)

    private val _cards = MutableStateFlow(ApiProbeCatalog.all.map { Card(spec = it) })

    /** 探针卡片列表，顺序与 [ApiProbeCatalog.all] 一致。 */
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    private val _environment = MutableStateFlow<ProbeEnvironment?>(null)

    /** 顶部环境信息；null 表示尚未读取完成（UI 显示占位文案）。 */
    val environment: StateFlow<ProbeEnvironment?> = _environment.asStateFlow()

    /** 「一键全跑」的作业句柄：再次点击时先取消旧的再重跑，避免两批请求叠着跑。 */
    private var runAllJob: Job? = null

    /** 正在请求中的探针 id 集合，用于重入保护（单卡点击与全跑可能同时命中同一个 id）。 */
    private val runningIds = mutableSetOf<String>()

    /**
     * 批次号。每开启一批新的运行（全跑 / 清空）就自增，
     * 在飞协程带着自己启动时的批次号回写，批次对不上就直接丢弃，
     * 防止「取消后旧结果把新一轮的『请求中』覆盖成旧值」。
     */
    private var runEpoch = 0

    init {
        // 进入页面即读一次登录态与构建信息，未登录时 UI 直接给空态
        refreshEnvironment()
    }

    /** 重新读取登录态与构建信息。token 读取会阻塞一次磁盘 IO，必须下 IO 线程。 */
    fun refreshEnvironment() {
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { tokenStore.token }
            _environment.value = ProbeEnvironment(
                variantName = BuildConfig.APP_VARIANT_NAME,
                loggedIn = !token.isNullOrBlank(),
            )
            // 只记录「有没有 token」，绝不打印 token 内容
            AppLog.d(TAG, "环境信息已刷新：登录态=${if (token.isNullOrBlank()) "未登录" else "已登录"}")
        }
    }

    /** 展开 / 收起某张卡的原始 JSON。 */
    fun toggleExpanded(id: String) {
        updateCard(id) { it.copy(expanded = !it.expanded) }
    }

    /** 运行单个探针。同一个接口已在请求中时直接忽略（重入保护）。 */
    fun runOne(id: String) {
        val spec = ApiProbeCatalog.all.firstOrNull { it.id == id } ?: return
        if (!runningIds.add(id)) {
            AppLog.d(TAG, "$id 已在请求中，忽略本次重复触发")
            return
        }
        val epoch = runEpoch
        viewModelScope.launch {
            setResult(id, epoch, ApiProbeResult.Running)
            try {
                setResult(id, epoch, executor.run(spec))
            } finally {
                runningIds.remove(id)
            }
        }
    }

    /**
     * 一键全跑：并发度限制为 [RUN_ALL_CONCURRENCY]。
     *
     * 先取消上一批并整体复位，再启动新一批。不做纯串行——串行时只要有一个慢接口
     * （如 `/api/my/stat`）就会把后面全部堵住，核对的体感很差。
     */
    fun runAll() {
        runAllJob?.cancel()
        runningIds.clear()
        val epoch = ++runEpoch
        // 整批复位，避免上一批残留的「成功 / 失败」与新一轮的「请求中」混淆
        _cards.update { list -> list.map { it.copy(result = ApiProbeResult.Idle) } }

        runAllJob = viewModelScope.launch {
            AppLog.d(TAG, "一键全跑开始：共 ${ApiProbeCatalog.all.size} 个接口，并发 $RUN_ALL_CONCURRENCY")
            // 信号量限流：最多同时 3 个请求在飞
            val gate = Semaphore(RUN_ALL_CONCURRENCY)
            ApiProbeCatalog.all
                .map { spec ->
                    launch {
                        // withPermit 会在拿到许可后执行，退出时自动归还，异常/取消都安全
                        gate.withPermit {
                            if (!runningIds.add(spec.id)) return@withPermit
                            try {
                                setResult(spec.id, epoch, ApiProbeResult.Running)
                                setResult(spec.id, epoch, executor.run(spec))
                            } catch (e: CancellationException) {
                                // 被新一轮取消：把这张卡拉回未运行，别留下「请求中」的僵尸态
                                setResult(spec.id, epoch, ApiProbeResult.Idle)
                                throw e
                            } finally {
                                runningIds.remove(spec.id)
                            }
                        }
                    }
                }
                .joinAll()
            AppLog.d(TAG, "一键全跑结束")
        }
    }

    /** 清空全部结果与展开态，并取消在飞请求。 */
    fun clearAll() {
        runAllJob?.cancel()
        runAllJob = null
        runningIds.clear()
        // 批次号自增：在飞的旧结果回来时会被判定为过期并丢弃
        runEpoch++
        _cards.update { list -> list.map { it.copy(result = ApiProbeResult.Idle, expanded = false) } }
        AppLog.d(TAG, "已清空全部探针结果")
    }

    /** 写入某张卡的结果；批次号对不上说明这一批已作废，整批丢弃。 */
    private fun setResult(id: String, epoch: Int, result: ApiProbeResult) {
        _cards.update { list ->
            if (epoch != runEpoch) {
                list
            } else {
                list.map { if (it.spec.id == id) it.copy(result = result) else it }
            }
        }
    }

    /** 按 id 原地变换一张卡（展开位用，不参与批次判定）。 */
    private fun updateCard(id: String, block: (Card) -> Card) {
        _cards.update { list -> list.map { if (it.spec.id == id) block(it) else it } }
    }

    private companion object {
        const val TAG = "ApiProbePage"

        /** 一键全跑的并发度：3 兼顾速度与「不触发服务端限流」。实测被限流时降到 2。 */
        const val RUN_ALL_CONCURRENCY = 3
    }
}
