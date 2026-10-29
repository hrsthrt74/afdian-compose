package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.ai.AiPromptBuilder
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.stats.IncomeAiFeedState
import com.hrsthrt74.afdian.data.stats.IncomeAiRange
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import com.hrsthrt74.afdian.data.stats.IncomeStatRepository
import com.hrsthrt74.afdian.data.stats.IncomeStatType
import com.hrsthrt74.afdian.data.stats.filterIncomeRecords
import com.hrsthrt74.afdian.data.stats.parseIncomeStatDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 「收入明细」页 ViewModel。
 * 管理 按日/周/月 三种统计维度的分页加载：切 tab 重置到第 1 页，滚到底自动拉下一页。
 */
class IncomeDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = IncomeStatRepository(AuthTokenStore(application))

    /** 当前统计维度（按日/按周/按月）。 */
    private val _type = MutableStateFlow(IncomeStatType.Day)
    val type = _type.asStateFlow()

    /** 已加载的表格行（跨页拼接，接口按日期倒序返回，直接追加即可）。 */
    private val _items = MutableStateFlow<List<IncomeStatRecord>>(emptyList())
    val items = _items.asStateFlow()

    /** 首次加载 / 切 tab 的整页加载状态。 */
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    /** 追加下一页时的底部加载状态。 */
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()

    /** 首次加载的错误信息；翻页失败不进这里，只打日志，保留已加载数据。 */
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** 是否还有下一页（来自接口 has_more）。 */
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    /** 「问 AI」/「导出数据」正在全量拉取数据；期间禁用菜单防止重复触发。 */
    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()

    /** 当前已加载到的页码。 */
    private var currentPage = 1
    /** 首次加载协程，切 tab 时取消旧请求。 */
    private var loadJob: Job? = null
    /** 翻页协程，防重入。 */
    private var loadMoreJob: Job? = null

    // ── 「问 AI」：分页预取（边拉边判断够不够用） ──────────────────────────────

    /** AI 分析选中的时间尺度；每次打开结果底栏重置为默认值。 */
    private val _aiRange = MutableStateFlow(IncomeAiRange.Default)
    val aiRange = _aiRange.asStateFlow()

    /** 预取进度（底栏「正在拉取数据」的进度提示用）。 */
    private val _aiFeed = MutableStateFlow(IncomeAiFeedState())
    val aiFeed = _aiFeed.asStateFlow()

    /** 预取协程；发送时若数据已够用会直接取消它，放弃后面还没拉的页。 */
    private var aiFeedJob: Job? = null

    /** 已预取的记录（接口倒序，跨页直接追加；只在主线程读写）。 */
    private val aiRecords = mutableListOf<IncomeStatRecord>()

    /** 下一张要拉的页码（跨协程重启复用，避免重复拉同一页）。 */
    private var aiNextPage = 1

    init {
        load()
    }

    /** 切换统计维度：重置到第 1 页重新加载。 */
    fun switchType(target: IncomeStatType) {
        if (_type.value == target) return
        loadJob?.cancel()
        loadMoreJob?.cancel()
        _type.value = target
        load()
    }

    /** 首次加载（也用于错误重试）。 */
    fun retry() {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        load()
    }

    /** 滚到底触发：追加下一页；加载中 / 无更多 / 已在翻页时忽略。 */
    fun loadMore() {
        if (_loading.value || _loadingMore.value || !_hasMore.value) return
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _loadingMore.value = true
            val nextPage = currentPage + 1
            // 记录发起请求时的维度，期间切 tab 的话结果已过期，直接丢弃
            val requestType = _type.value
            try {
                val page = repository.fetch(requestType, nextPage)
                // 期间切了 tab（load() 已重置列表）时丢弃过期结果
                if (_type.value == requestType) {
                    _items.value = _items.value + page.list
                    // has_more 缺失时乐观假设还有下一页，靠空页兜底停止
                    _hasMore.value = page.list.isNotEmpty() && (page.hasMore ?: true)
                    currentPage = nextPage
                }
                AppLog.d(TAG, "loadMore ok: page=$nextPage, rows=${page.list.size}, hasMore=${page.hasMore}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 翻页失败不打断已展示的数据，仅告警；用户继续滚动可再次尝试
                AppLog.w(TAG, "loadMore failed: page=$nextPage", e)
            } finally {
                _loadingMore.value = false
            }
        }
    }

    /** 统一的第 1 页加载：清空数据、进入 loading、请求、呈现结果。 */
    private fun load() {
        loadJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _items.value = emptyList()
            _hasMore.value = false
            currentPage = 1
            AppLog.d(TAG, "load: type=${_type.value.apiValue}")
            try {
                val page = repository.fetch(_type.value, 1)
                _items.value = page.list
                // has_more 缺失时乐观假设还有下一页，靠空页兜底停止
                _hasMore.value = page.list.isNotEmpty() && (page.hasMore ?: true)
                AppLog.d(TAG, "load ok: type=${_type.value.apiValue}, rows=${page.list.size}, hasMore=${page.hasMore}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "load failed: type=${_type.value.apiValue}", e)
                _error.value = e.message ?: "收入明细加载失败"
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * 「问 AI」（复制方案）：全量拉取当前维度的数据并生成 Markdown 表格（不做时间过滤）。
     * 结果通过回调返回（Result 成功=文本 / 失败=异常），异常不抛出协程。
     */
    fun requestAiText(onResult: (Result<String>) -> Unit) {
        runExportTask(onResult) { repository.fetchAllMarkdown(_type.value) }
    }

    // ── 「问 AI」：预取 + 发送 ────────────────────────────────────────────────

    /** 切换 AI 分析的时间尺度（底栏气泡里的 chips）。 */
    fun setAiRange(range: IncomeAiRange) {
        if (_aiRange.value == range) return
        _aiRange.value = range
        AppLog.d(TAG, "AI 时间尺度 -> ${range.label}")
    }

    /**
     * 打开结果底栏时调用：重置时间尺度，并立刻开始往后翻页取数。
     *
     * 种子用表格里已经加载到的那些页（`_items`）：用户翻过多少页就省多少次请求，
     * 一页都没加载过才从第 1 页重来。
     */
    fun startAiFeed() {
        aiFeedJob?.cancel()
        aiFeedJob = null
        aiRecords.clear()
        aiNextPage = if (_items.value.isEmpty()) 1 else currentPage + 1
        _aiRange.value = IncomeAiRange.Default

        val seed = _items.value
        aiRecords += seed
        _aiFeed.value = IncomeAiFeedState(
            count = seed.size,
            oldestDate = seed.lastOrNull()?.let { parseIncomeStatDate(it.dateStr) },
        )
        AppLog.d(
            TAG,
            "AI 预取开始：type=${_type.value.apiValue}，复用表格 ${seed.size} 条，从第 $aiNextPage 页继续",
        )
        launchAiFeed()
    }

    /** 关闭结果底栏时调用：停掉后台翻页（已拉到的记录留着，重试不必再拉）。 */
    fun stopAiFeed() {
        if (aiFeedJob?.isActive == true) {
            AppLog.d(TAG, "AI 预取停止：已 ${aiRecords.size} 条，覆盖至 ${_aiFeed.value.oldestDate}")
        }
        aiFeedJob?.cancel()
        aiFeedJob = null
    }

    /** 起一个预取协程，从 [aiNextPage] 往后翻；重复调用无效（已在跑就直接返回）。 */
    private fun launchAiFeed() {
        if (aiFeedJob?.isActive == true) return
        val type = _type.value
        aiFeedJob = viewModelScope.launch {
            try {
                while (true) {
                    if (aiNextPage > AI_FEED_MAX_PAGES) {
                        AppLog.w(TAG, "AI 预取超过 $AI_FEED_MAX_PAGES 页，已截断（按已拉到的数据分析）")
                        _aiFeed.value = _aiFeed.value.copy(complete = true)
                        break
                    }
                    val result = repository.fetch(type, aiNextPage)
                    // 期间切了统计维度：这份数据已经不对应底栏的上下文，直接放弃
                    if (_type.value != type) {
                        AppLog.w(TAG, "AI 预取中止：维度已切到 ${_type.value.apiValue}")
                        return@launch
                    }
                    if (result.list.isEmpty()) {
                        _aiFeed.value = _aiFeed.value.copy(complete = true)
                        break
                    }
                    aiRecords += result.list
                    aiNextPage++
                    // 接口按日期倒序返回，最后一条就是当前覆盖到的最旧日期
                    val exhausted = result.hasMore == false
                    _aiFeed.value = IncomeAiFeedState(
                        count = aiRecords.size,
                        oldestDate = parseIncomeStatDate(aiRecords.last().dateStr),
                        complete = exhausted,
                    )
                    if (exhausted) break
                }
                AppLog.d(TAG, "AI 预取结束：${aiRecords.size} 条，完整=${_aiFeed.value.complete}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 拉到一半失败不推翻已有数据：标个错误，发送时会用已拉到的部分继续
                AppLog.w(TAG, "AI 预取失败（将用已拉到的 ${aiRecords.size} 条继续）", e)
                _aiFeed.value = _aiFeed.value.copy(error = e.message ?: "数据拉取失败")
            }
        }
    }

    /**
     * 用户点「发送」后组装提示词。
     *
     * 关键在 [awaitAiFeedCoverage]：手上的数据够覆盖所选窗口就**立刻停掉后面的翻页**，
     * 不够才挂起等（底栏此时显示「正在拉取数据…」的进度），一到界就开跑。
     */
    suspend fun buildAiPromptForRange(): String {
        val type = _type.value
        val range = _aiRange.value
        ensureAiFeedRunning(range)
        awaitAiFeedCoverage(range)
        val filtered = filterIncomeRecords(aiRecords.toList(), range)
        if (filtered.isEmpty()) {
            error("「${range.label}」范围内没有可分析的数据，换个时间尺度再发一次")
        }
        AppLog.d(
            TAG,
            "AI 提示词：type=${type.apiValue}，range=${range.label}，喂入 ${filtered.size}/${aiRecords.size} 条",
        )
        return AiPromptBuilder.incomeAnalysisPrompt(type, filtered, range)
    }

    /** 预取协程没在跑（还没启动 / 已停）且数据又不够时，兜底重新拉起。 */
    private fun ensureAiFeedRunning(range: IncomeAiRange) {
        if (aiFeedJob?.isActive == true) return
        if (_aiFeed.value.covers(range)) return
        AppLog.d(TAG, "AI 预取未运行且数据不足 ${range.label}，重新拉起（第 $aiNextPage 页）")
        launchAiFeed()
    }

    /**
     * 等到已拉取的数据覆盖 [range] 为止；已经覆盖就马上返回。
     *
     * 「覆盖」的判断在 [IncomeAiFeedState.covers]：拉到尽头、或最旧日期不晚于窗口起始日。
     * 预取协程已结束却没覆盖到时不再空等（出错场景），用已有数据继续。
     */
    private suspend fun awaitAiFeedCoverage(range: IncomeAiRange) {
        if (_aiFeed.value.covers(range)) {
            // 用户选的范围比已拉到的窄：后面还没拉的页不用再拉，直接放弃
            stopAiFeed()
            return
        }
        if (aiFeedJob?.isActive != true) {
            AppLog.w(TAG, "AI 发送：预取已停且未覆盖 ${range.label}，用 ${aiRecords.size} 条继续")
            return
        }
        AppLog.d(TAG, "AI 发送：等待数据覆盖 ${range.label}（当前 ${_aiFeed.value.count} 条）")
        _aiFeed.first { it.covers(range) }
        stopAiFeed()
    }

    /** 「导出数据」：全量拉取当前维度的数据并生成 CSV 文本，结果通过回调返回。 */
    fun requestCsv(onResult: (Result<String>) -> Unit) {
        runExportTask(onResult) { repository.fetchAllCsv(_type.value) }
    }

    /**
     * 全量拉取任务的公共骨架：working 置位防重入（加载中同样拒绝），
     * 完成后回调结果并复位；异常统一包装成 Result.failure 交给 UI 呈现。
     */
    private fun runExportTask(onResult: (Result<String>) -> Unit, block: suspend () -> String) {
        if (_working.value || _loading.value) {
            onResult(Result.failure(IllegalStateException("数据加载中，请稍后再试")))
            return
        }
        viewModelScope.launch {
            _working.value = true
            try {
                onResult(Result.success(block()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "全量拉取失败", e)
                onResult(Result.failure(e))
            } finally {
                _working.value = false
            }
        }
    }

    private companion object {
        const val TAG = "IncomeDetail"

        /** AI 预取的页数上限（与全量拉取同一口径，纯防护，正常数据远到不了）。 */
        const val AI_FEED_MAX_PAGES = 100
    }
}
