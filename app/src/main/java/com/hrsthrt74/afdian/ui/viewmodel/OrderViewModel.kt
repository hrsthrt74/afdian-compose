package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.OrderItem
import com.hrsthrt74.afdian.data.content.OrderPage
import com.hrsthrt74.afdian.data.content.OrderService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「我的订单」页状态：按状态筛选 + 页码分页。
 * status 与网页端一致：2=已支付（默认）、1=待支付、8=待发货、9=已发货，接口不提供「全部」。
 *
 * 每个 Tab 各存一份列表快照（[snapshots]）：
 * - 切 Tab 立即显示该 Tab 自己的数据并静默刷新，避免显示别的 Tab 的订单；
 * - 转场动画里新旧两页各读各的快照，旧页滑出时冻结在自己原来的数据上，不会「闪成目标页」。
 */
class OrderViewModel(application: Application) : AndroidViewModel(application) {
    private val service = OrderService(AuthTokenStore(application))

    /** 单个 Tab 的已加载状态：列表 + 是否还有更多 + 下一页页码 */
    data class TabSnapshot(
        val orders: List<OrderItem>,
        val hasMore: Boolean,
        val nextPage: Int,
        val totalCount: Int,
    )

    private val _status = MutableStateFlow(STATUS_PAID)
    val status = _status.asStateFlow()

    /** 各 Tab 的快照缓存，key 为 status；UI 按「转场中的那个 Tab」取自己的一份渲染 */
    private val _snapshots = MutableStateFlow<Map<String, TabSnapshot>>(emptyMap())
    val snapshots = _snapshots.asStateFlow()

    // 副标题初始给一个空格占位：避免首次加载完成时顶栏因 subtitle 从无到有而高度突变
    private val _summary = MutableStateFlow(" ")
    val summary = _summary.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        load(_status.value)
    }

    /** 切换筛选或错误重试：有缓存先亮出缓存静默刷新，没缓存进入加载态。 */
    fun load(status: String) {
        if (_loading.value || _isRefreshing.value) return
        _status.value = status
        val cached = _snapshots.value[status]
        viewModelScope.launch {
            if (cached == null) {
                // 首次访问该 Tab：没有数据可显示，进入整页加载态
                _hasMore.value = false
                _loading.value = true
            } else {
                // 有缓存：立即恢复该 Tab 自己的摘要与分页状态，请求在后台静默刷新
                _summary.value = "共 ${cached.totalCount} 笔"
                _hasMore.value = cached.hasMore
            }
            _error.value = null
            // 调试日志：记录筛选条件与是否命中缓存
            AppLog.d("Orders", "load: status=$status, cacheHit=${cached != null}")
            runCatching { withContext(Dispatchers.IO) { service.load(status, 1) } }
                .onSuccess { page ->
                    putSnapshot(status, TabSnapshot(page.orders, page.hasMore, 2, totalCountFor(page)))
                    // 只在仍停留于该 Tab 时才刷新摘要等全局状态，防止快速连切时旧请求覆盖新 Tab
                    if (_status.value == status) {
                        _summary.value = "共 ${totalCountFor(page)} 笔"
                        _hasMore.value = page.hasMore
                    }
                    // 调试日志：筛选条件与结果统计（条数/是否还有更多）
                    AppLog.d("Orders", "load ok: status=$status, orders=${page.orders.size}, total=${page.totalCount}, hasMore=${page.hasMore}")
                }
                .onFailure {
                    // 错误日志：用户模式下也能看到订单加载失败
                    AppLog.e("Orders", "load failed: status=$status", it)
                    if (_status.value == status) _error.value = it.message ?: "订单加载失败"
                }
            _loading.value = false
        }
    }

    /** 下拉刷新：重新拉当前 Tab 第一页，快照里已有列表时旧数据保留到新数据到达。 */
    fun refresh() {
        if (_loading.value || _isRefreshing.value || _isLoadingMore.value) return
        val status = _status.value
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            runCatching { withContext(Dispatchers.IO) { service.load(status, 1) } }
                .onSuccess { page ->
                    putSnapshot(status, TabSnapshot(page.orders, page.hasMore, 2, totalCountFor(page)))
                    _summary.value = "共 ${totalCountFor(page)} 笔"
                    _hasMore.value = page.hasMore
                    AppLog.d("Orders", "refresh ok: status=$status, orders=${page.orders.size}")
                }
                .onFailure {
                    AppLog.e("Orders", "refresh failed", it)
                    // 该 Tab 没有任何数据时才给出错误态，否则保留旧列表只留日志
                    if (_snapshots.value[status]?.orders.isNullOrEmpty()) _error.value = it.message ?: "订单刷新失败"
                }
            _isRefreshing.value = false
        }
    }

    /** 滚动到列表底部附近时追加当前 Tab 的下一页，按订单号去重避免重复条目。 */
    fun loadMore() {
        if (_loading.value || _isRefreshing.value || _isLoadingMore.value || !_hasMore.value) return
        val status = _status.value
        // 页码取自该 Tab 的快照；没有快照（还没成功加载过）就没有「下一页」可言
        val snapshot = _snapshots.value[status] ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = snapshot.nextPage
                val result = withContext(Dispatchers.IO) { service.load(status, page) }
                val merged = snapshot.orders + result.orders.filter { order ->
                    snapshot.orders.none { it.id == order.id }
                }
                putSnapshot(status, TabSnapshot(merged, result.hasMore, page + 1, snapshot.totalCount))
                if (_status.value == status) {
                    _hasMore.value = result.hasMore
                }
                AppLog.d("Orders", "loadMore status=$status page=$page: +${result.orders.size} 条，hasMore=${result.hasMore}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Orders", "loadMore failed", e)
                _error.value = e.message ?: "更多订单加载失败"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /** 写入某个 Tab 的快照（不可变 Map 替换，保证 StateFlow 的观察者能收到新引用） */
    private fun putSnapshot(status: String, snapshot: TabSnapshot) {
        _snapshots.value = _snapshots.value + (status to snapshot)
    }

    companion object {
        const val STATUS_PAID = "2"
        const val STATUS_UNPAID = "1"
        const val STATUS_UNSHIPPED = "8"
        const val STATUS_SHIPPED = "9"
    }

    /**
     * 订单总数兜底：接口的 total_count 不完全可靠（实测「已发货」等筛选会返回 0 但列表有数据），
     * 取 total_count 与本页实际条数的较大值，保证摘要不会出现「共 0 笔」。
     */
    private fun totalCountFor(page: OrderPage): Int =
        maxOf(page.totalCount, page.orders.size)
}
