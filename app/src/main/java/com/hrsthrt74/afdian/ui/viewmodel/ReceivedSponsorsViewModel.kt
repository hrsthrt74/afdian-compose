package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.BlackedType
import com.hrsthrt74.afdian.data.content.ReceivedOrderStatus
import com.hrsthrt74.afdian.data.content.ReceivedSponsor
import com.hrsthrt74.afdian.data.content.ReceivedShipmentFilter
import com.hrsthrt74.afdian.data.content.ReceivedSponsorsSort
import com.hrsthrt74.afdian.data.content.ReceivedTimeRange
import com.hrsthrt74.afdian.data.content.SponsorFilterPlan
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
    private val _orderStatus = MutableStateFlow(ReceivedOrderStatus.All)
    val orderStatus = _orderStatus.asStateFlow()
    /** 当前档位 plan_id：null = 全部档位，"-1" = 自选金额 */
    private val _planId = MutableStateFlow<String?>(null)
    val planId = _planId.asStateFlow()
    /** 「档位」下拉的方案选项；加载失败为空列表（仍保留全部档位 / 自选金额） */
    private val _filterPlans = MutableStateFlow<List<SponsorFilterPlan>>(emptyList())
    val filterPlans = _filterPlans.asStateFlow()
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
        loadFilterPlans()
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

    /** 切换订单状态：与网页端一致，选择具体状态时取消「含收件信息」。 */
    fun setOrderStatus(status: ReceivedOrderStatus) {
        if (_orderStatus.value == status) return
        _orderStatus.value = status
        if (status != ReceivedOrderStatus.All &&
            _shipmentFilter.value == ReceivedShipmentFilter.WithReceiver
        ) {
            _shipmentFilter.value = ReceivedShipmentFilter.All
        }
        reloadForFilter()
    }

    /** 切换档位：null = 全部档位，"-1" = 自选金额；回第 1 页重载。 */
    fun setPlanId(planId: String?) {
        if (_planId.value == planId) return
        _planId.value = planId
        reloadForFilter()
    }

    /** 拉取「档位」下拉的方案选项；失败只记警告，不阻断订单列表。 */
    private fun loadFilterPlans() {
        viewModelScope.launch {
            try {
                val plans = withContext(Dispatchers.IO) { service.fetchFilterPlans() }
                _filterPlans.value = plans
                AppLog.d("ReceivedSponsors", "loadFilterPlans: ${plans.size} 个方案")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("ReceivedSponsors", "loadFilterPlans failed", e)
            }
        }
    }

    fun setShipmentFilter(filter: ReceivedShipmentFilter) {
        if (_shipmentFilter.value == filter) return
        _shipmentFilter.value = filter
        // 「含收件信息」按所有订单状态查询，选中时清空订单状态
        if (filter == ReceivedShipmentFilter.WithReceiver) {
            _orderStatus.value = ReceivedOrderStatus.All
        }
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

    /**
     * 加入屏蔽 / 拒绝名单（POST /api/user/black）。
     * 返回服务端提示文案（如「已加入屏蔽名单」）；失败抛出异常，由调用方 Toast。
     *
     * @param userId 订单用户 ID（ReceivedSponsor.userId）
     * @param type 名单类型：屏蔽（block）/ 拒绝（reject）
     */
    suspend fun blackUser(userId: String, type: BlackedType): String =
        withContext(Dispatchers.IO) { service.black(userId, type) }

    private suspend fun fetchPage(page: Int, lastOrderId: String?) =
        service.fetchReceivedSponsors(
            page = page,
            lastOrderId = lastOrderId,
            redeemOnly = _redeemOnly.value,
            remarkOnly = _remarkOnly.value,
            orderStatus = _orderStatus.value,
            planId = _planId.value,
            shipmentStatus = _shipmentFilter.value,
            sort = _sort.value,
            beginTime = _timeRange.value.beginTime,
            endTime = _timeRange.value.endTime,
        )
}
