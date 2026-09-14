package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.ReceivedSponsor
import com.hrsthrt74.afdian.data.content.ReceivedShipmentFilter
import com.hrsthrt74.afdian.data.content.ReceivedSponsorsSort
import com.hrsthrt74.afdian.data.content.ReceivedTimeRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 独立「收到发电」页的状态与 sponsored-bill-filter 分页。 */
class ReceivedSponsorsViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))

    private val _orders = MutableStateFlow<List<ReceivedSponsor>>(emptyList())
    val orders = _orders.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _redeemOnly = MutableStateFlow(false)
    val redeemOnly = _redeemOnly.asStateFlow()
    private val _remarkOnly = MutableStateFlow(false)
    val remarkOnly = _remarkOnly.asStateFlow()
    private val _shipmentFilter = MutableStateFlow(ReceivedShipmentFilter.All)
    val shipmentFilter = _shipmentFilter.asStateFlow()
    private val _sort = MutableStateFlow(ReceivedSponsorsSort.Newest)
    val sort = _sort.asStateFlow()
    private val _timeRange = MutableStateFlow(ReceivedTimeRange())
    val timeRange = _timeRange.asStateFlow()

    private var nextPage = 1
    private var lastOrderId: String? = null

    init {
        load()
    }

    /** 首次加载或错误重试：清空旧列表并回到第一页。 */
    fun load() {
        if (_isLoading.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) { fetchPage(1, null) }
                _orders.value = result.orders
                _hasMore.value = result.hasMore
                lastOrderId = result.lastOrderId
                nextPage = 2
                AppLog.d("ReceivedSponsors", "load: ${result.orders.size} 条，hasMore=${result.hasMore}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ReceivedSponsors", "load failed", e)
                _error.value = e.message ?: "收到发电加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 下拉刷新：完成后保留最新第一页数据，并重置分页游标。 */
    fun refresh() {
        if (_isLoading.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) { fetchPage(1, null) }
                _orders.value = result.orders
                _hasMore.value = result.hasMore
                lastOrderId = result.lastOrderId
                nextPage = 2
                AppLog.d("ReceivedSponsors", "refresh: ${result.orders.size} 条，hasMore=${result.hasMore}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ReceivedSponsors", "refresh failed", e)
                _error.value = e.message ?: "收到发电刷新失败"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 滚动到列表底部附近时追加下一页，使用游标避免重复订单。 */
    fun loadMore() {
        if (_isLoading.value || _isRefreshing.value || _isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = nextPage
                val previousLastOrderId = lastOrderId
                val result = withContext(Dispatchers.IO) {
                    fetchPage(page, previousLastOrderId)
                }
                _orders.value = _orders.value + result.orders.filter { order ->
                    _orders.value.none { it.orderNo == order.orderNo }
                }
                _hasMore.value = result.hasMore
                lastOrderId = result.lastOrderId
                nextPage = page + 1
                AppLog.d("ReceivedSponsors", "loadMore page=$page: +${result.orders.size} 条，hasMore=${result.hasMore}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ReceivedSponsors", "loadMore failed", e)
                _error.value = e.message ?: "更多发电加载失败"
            } finally {
                _isLoadingMore.value = false
            }

        }
    }

    fun toggleRedeemOnly() {
        _redeemOnly.value = !_redeemOnly.value
        reloadForFilter()
    }

    fun toggleRemarkOnly() {
        _remarkOnly.value = !_remarkOnly.value
        reloadForFilter()
    }

    fun setShipmentFilter(filter: ReceivedShipmentFilter) {
        if (_shipmentFilter.value == filter) return
        _shipmentFilter.value = filter
        reloadForFilter()
    }

    fun setSort(sort: ReceivedSponsorsSort) {
        if (_sort.value == sort) return
        _sort.value = sort
        reloadForFilter()
    }

    /** 设置时间范围（毫秒级）；任一端为 null 都表示该端不限制。 */
    fun setTimeRange(beginMillis: Long?, endMillis: Long?) {
        val range = ReceivedTimeRange(beginMillis, endMillis)
        if (_timeRange.value == range) return
        _timeRange.value = range
        reloadForFilter()
    }

    private fun reloadForFilter() {
        if (_isLoading.value || _isRefreshing.value || _isLoadingMore.value) return
        _orders.value = emptyList()
        _hasMore.value = false
        _error.value = null
        nextPage = 1
        lastOrderId = null
        load()
    }

    private suspend fun fetchPage(page: Int, lastOrderId: String?) =
        service.fetchReceivedSponsors(
            page = page,
            lastOrderId = lastOrderId,
            redeemOnly = _redeemOnly.value,
            remarkOnly = _remarkOnly.value,
            shipmentStatus = _shipmentFilter.value,
            sort = _sort.value,
            beginTime = _timeRange.value.beginTime,
            endTime = _timeRange.value.endTime,
        )
}
