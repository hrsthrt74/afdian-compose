package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.ManagementResult
import com.hrsthrt74.afdian.data.content.ManagementSection
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import com.hrsthrt74.afdian.data.stats.CreatorStatsRepository
import com.hrsthrt74.afdian.data.stats.DataCardModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val statsRepository = CreatorStatsRepository(AuthTokenStore(application))
    private val _section = MutableStateFlow(ManagementSection.Overview)
    val section = _section.asStateFlow()
    private val _result = MutableStateFlow<ManagementResult?>(null)
    val result = _result.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    /** 整体概况的类型化卡片数据，key 使用 CardKey.storageValue。 */
    private val _cards = MutableStateFlow<Map<String, DataCardModel>>(emptyMap())
    val cards = _cards.asStateFlow()
    /** 整体概况的图表卡数据，key 使用 ChartType.storageValue。 */
    private val _charts = MutableStateFlow<Map<String, ChartDataItem>>(emptyMap())
    val charts = _charts.asStateFlow()
    private var loadJob: Job? = null

    // 流式加载的回调在 IO 线程触发，本字段在主线程写、IO 线程读，需要 @Volatile 保证可见性
    @Volatile
    private var loadGeneration = 0L

    init {
        load(ManagementSection.Overview)
    }

    fun load(section: ManagementSection) {
        loadJob?.cancel()
        _section.value = section
        _result.value = null
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            // 调试日志：记录请求的管理板块
            AppLog.d("Management", "load: section=${section.name}")
            try {
                // 分区只剩整体概况（赞助者管理已独立成页面），直接拉概况数据；
                // 原 else 分支的 ManagementService.load(section) 已随占位分区一起移除。
                // 流式加载：dashboard 一到就推第一帧并结束加载态，账单翻页等慢数据源
                // 在后台继续推后续帧，避免订单多的账号整页等最慢的接口。
                val overview = withContext(Dispatchers.IO) {
                    statsRepository.loadOverviewStreaming { partial ->
                        if (generation == loadGeneration) {
                            _cards.value = partial.cards
                            _charts.value = partial.charts
                            _result.value = ManagementResult(section, emptyList())
                            // 首帧即含 dashboard 数据（本月收入、历史、可提现等），先上屏；
                            // 未完成的数据源对应卡片显示 “--”，等后续帧补齐。
                            _loading.value = false
                        }
                    }
                }
                if (generation == loadGeneration) {
                    // 最后一帧与流式回调内容一致，这里再写一遍兜底（幂等）
                    _cards.value = overview.cards
                    _charts.value = overview.charts
                    _result.value = ManagementResult(section, emptyList())
                    AppLog.d(
                        "Management",
                        "load ok: section=${section.name}, cards=${overview.cards.size}, charts=${overview.charts.size}",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 错误日志：用户模式下也能看到管理数据加载失败
                if (generation == loadGeneration) {
                    AppLog.e("Management", "load failed: section=${section.name}", e)
                    _error.value = e.message ?: "管理数据加载失败"
                }
            } finally {
                if (generation == loadGeneration) _loading.value = false
            }
        }
    }
}
