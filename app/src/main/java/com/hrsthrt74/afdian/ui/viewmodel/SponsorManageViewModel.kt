package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.ManagementService
import com.hrsthrt74.afdian.data.content.SponsorFilterPlan
import com.hrsthrt74.afdian.data.content.SponsorFilterRow
import com.hrsthrt74.afdian.data.content.SponsorManageItem
import com.hrsthrt74.afdian.data.content.SponsorManageSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 赞助者管理页 ViewModel：封装 who-sponsored-me 的页码分页加载、排序与条件筛选。
 *
 * 排序支持 [SponsorManageSort]：默认档 + 三字段 × 升降序成对；
 * 筛选为多条件 [SponsorFilterRow] 列表（条件构建器，AND 关系，由筛选面板提交）；
 * 切换排序/筛选均回第 1 页重载（加载中不响应）。
 * 状态划分参照 ThankSponsorsViewModel：首屏加载 / 加载更多 / 错误 / 是否还有下一页。
 */
class SponsorManageViewModel(application: Application) : AndroidViewModel(application) {
    private val service = ManagementService(AuthTokenStore(application))

    /** 当前列表（按接口顺序追加） */
    private val _sponsors = MutableStateFlow<List<SponsorManageItem>>(emptyList())
    val sponsors = _sponsors.asStateFlow()
    /** 赞助者总人数（接口 total_count），首屏加载完成前为 0 */
    private val _totalCount = MutableStateFlow(0)
    val totalCount = _totalCount.asStateFlow()
    /** 当前排序；默认档 = 不传排序字段（与网页初始加载顺序一致） */
    private val _sort = MutableStateFlow(SponsorManageSort.Default)
    val sort = _sort.asStateFlow()
    /** 筛选面板的方案选项；加载失败为空列表（方案段只剩「全部」） */
    private val _filterPlans = MutableStateFlow<List<SponsorFilterPlan>>(emptyList())
    val filterPlans = _filterPlans.asStateFlow()
    /** 已应用的筛选条件（由筛选面板「确定」提交） */
    private val _filters = MutableStateFlow<List<SponsorFilterRow>>(emptyList())
    val filters = _filters.asStateFlow()
    /** 首屏加载中 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    /** 加载下一页中 */
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    /** 首屏加载错误（loadMore 失败只记日志，不清空列表） */
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()
    // 下一页页码（接口从 1 开始）
    private var nextPage = 1

    init {
        load()
        loadFilterPlans()
    }

    /** 首次加载 / 错误重试：清空列表回到第 1 页（沿用当前排序与筛选） */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sort = _sort.value
                val filters = _filters.value
                val result = withContext(Dispatchers.IO) {
                    service.fetchSponsors(page = 1, sort = sort, filters = filters)
                }
                _sponsors.value = result.list
                _totalCount.value = result.totalCount
                _hasMore.value = hasNextPage(page = 1, fetched = result.list.size, totalPages = result.totalPages)
                nextPage = 2
                AppLog.d(
                    "SponsorManage",
                    "load sort=${sort.title} filters=${filters.size}: " +
                        "${result.list.size} 条，total=${result.totalCount}，totalPages=${result.totalPages}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 错误日志：用户模式下也能看到赞助者列表加载失败
                AppLog.e("SponsorManage", "load failed", e)
                _error.value = e.message ?: "赞助者列表加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 切换排序：回第 1 页重载（参照 ReceivedSponsorsViewModel.reloadForFilter）。
     * 加载中不响应，避免与进行中的请求竞态。
     */
    fun setSort(sort: SponsorManageSort) {
        if (sort == _sort.value) return
        _sort.value = sort
        reload()
    }

    /** 提交筛选条件（筛选面板「确定」）；与当前一致则不重载。 */
    fun setFilters(filters: List<SponsorFilterRow>) {
        if (filters == _filters.value) return
        _filters.value = filters
        reload()
    }

    /** 清空列表回第 1 页重载当前「排序 + 筛选」组合；加载中丢弃切换请求。 */
    private fun reload() {
        if (_isLoading.value || _isLoadingMore.value) return
        _sponsors.value = emptyList()
        _hasMore.value = false
        _error.value = null
        nextPage = 1
        load()
    }

    /** 拉取筛选面板的方案选项；失败只记警告，不阻断列表（方案段退化为只有「全部」）。 */
    private fun loadFilterPlans() {
        viewModelScope.launch {
            try {
                val plans = withContext(Dispatchers.IO) { service.fetchFilterPlans() }
                _filterPlans.value = plans
                AppLog.d("SponsorManage", "loadFilterPlans: ${plans.size} 个方案")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("SponsorManage", "loadFilterPlans failed", e)
            }
        }
    }

    /** 加载下一页，追加到列表尾部；失败只记日志，保持已有列表可继续浏览 */
    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = nextPage
                val result = withContext(Dispatchers.IO) {
                    service.fetchSponsors(
                        page = page,
                        sort = _sort.value,
                        filters = _filters.value,
                    )
                }
                _sponsors.value = _sponsors.value + result.list
                _totalCount.value = result.totalCount
                _hasMore.value = hasNextPage(page, result.list.size, result.totalPages)
                nextPage = page + 1
                AppLog.d(
                    "SponsorManage",
                    "loadMore page=$page: +${result.list.size} 条，hasMore=${_hasMore.value}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("SponsorManage", "loadMore failed", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * 是否还有下一页：优先用接口 total_page 判断；
     * total_page 缺失（0）时退回「本页是否满页」启发式。
     */
    private fun hasNextPage(page: Int, fetched: Int, totalPages: Int): Boolean = when {
        totalPages > 0 -> page < totalPages
        else -> fetched >= ManagementService.SPONSOR_PER_PAGE
    }
}
