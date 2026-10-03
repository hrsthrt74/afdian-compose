package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.MessageService
import com.hrsthrt74.afdian.data.content.NoticeAction
import com.hrsthrt74.afdian.data.content.NoticeItem
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「收到的通知」Sheet 的 ViewModel（收到的喜欢 / 收到的评论共用）。
 *
 * 与消息列表的页码分页不同，这个接口是游标分页（publish_sn），因此
 * 单独建 VM 管理游标状态。喜欢与评论各自持有一个实例（action 不同，
 * 未读清除与服务端游标互不干扰），列表数据结构完全一致。
 * 按数据层约定直接依赖领域 Service，不走兼容门面。
 */
class ReceivedNoticeViewModel(
    application: Application,
    /** 通知类型：决定请求的 action 参数，也供 UI 区分「喜欢 / 评论」文案。 */
    val action: NoticeAction,
) : AndroidViewModel(application) {
    private val service = MessageService(AuthTokenStore(application), sharedAfdianHttpClient)

    private val _notices = MutableStateFlow<List<NoticeItem>>(emptyList())
    val notices = _notices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** 下一页游标：当前列表最后一条的 publish_sn；null 表示还没拉过首页。 */
    private var cursor: Long? = null
    private var isLoadingInternal = false

    /**
     * 打开 Sheet 时调用：总是重新拉取首页，用户可能刚收到新的通知。
     * 正在加载更多时跳过，避免游标状态被并发请求交错破坏。
     */
    fun refresh() {
        if (isLoadingInternal || _isLoadingMore.value) return
        viewModelScope.launch {
            isLoadingInternal = true
            _isLoading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) { service.fetchNotices(action, null) }
                cursor = result.notices.lastOrNull()?.publishSn
                _notices.value = result.notices
                _hasMore.value = result.hasMore
                AppLog.d(TAG, "refresh ok: action=${action.apiValue}, got=${result.notices.size}, hasMore=${result.hasMore}")
            } catch (e: Exception) {
                AppLog.e(TAG, "refresh failed: action=${action.apiValue}", e)
                _error.value = e.message ?: "通知加载失败"
            } finally {
                _isLoading.value = false
                isLoadingInternal = false
            }
        }
    }

    /** 触底加载更早的通知：游标传当前列表最后一条的 publish_sn，按 sn 去重后追加。 */
    fun loadMore() {
        if (isLoadingInternal || _isLoadingMore.value || !_hasMore.value) return
        // 没拉过首页时没有游标可传，直接忽略
        val currentCursor = cursor ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val result = withContext(Dispatchers.IO) { service.fetchNotices(action, currentCursor) }
                // publish_sn 全局唯一，去重防止域名回退或并发下拿到重叠数据
                val existingSns = _notices.value.map { it.publishSn }.toHashSet()
                val fresh = result.notices.filterNot { it.publishSn in existingSns }
                _notices.value = _notices.value + fresh
                result.notices.lastOrNull()?.let { cursor = it.publishSn }
                _hasMore.value = result.hasMore
                _error.value = null
                AppLog.d(
                    TAG,
                    "loadMore ok: action=${action.apiValue}, cursor=$currentCursor, got=${fresh.size}, total=${_notices.value.size}, hasMore=${result.hasMore}",
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "loadMore failed: action=${action.apiValue}, cursor=$currentCursor", e)
                _error.value = e.message ?: "加载更多失败"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /** 错误态重试：等价于重新拉取首页。 */
    fun retry() = refresh()

    private companion object {
        const val TAG = "ReceivedNotice"
    }
}
