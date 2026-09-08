package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import com.hrsthrt74.afdian.data.stats.IncomeStatRepository
import com.hrsthrt74.afdian.data.stats.IncomeStatType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * 「问 AI」：全量拉取当前维度的数据并生成 Markdown 表格（不做时间过滤）。
     * 结果通过回调返回（Result 成功=文本 / 失败=异常），异常不抛出协程。
     */
    fun requestAiText(onResult: (Result<String>) -> Unit) {
        runExportTask(onResult) { repository.fetchAllMarkdown(_type.value) }
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
    }
}
