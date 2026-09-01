package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.data.content.MessageFAQ
import com.hrsthrt74.afdian.data.content.MessageItem
import com.hrsthrt74.afdian.data.content.MyCheckData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))

    private val _dialogs = MutableStateFlow<List<MessageDialog>>(emptyList())
    val dialogs = _dialogs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // ── 未读计数：来自轻量接口 /api/my/check（含评论、点赞、私信三种） ──
    private val _checkData = MutableStateFlow(MyCheckData())

    /** 未读消息总数（评论+点赞+私信），用于底部 Tab badge 数字 */
    val unreadCount: StateFlow<Int> = _checkData
        .map { it.unreadMessageNum }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 是否有任何未读消息，用于决定是否显示 badge */
    val hasUnreadMessages: StateFlow<Boolean> = _checkData
        .map { it.unreadMessageNum > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var currentPage = 1
    private var hasMore = true
    private var isLoadingInternal = false

    init {
        refresh()
        // 首次进入立即拉取未读计数，不等 auto-refresh
        viewModelScope.launch { refreshCheck() }
    }

    fun refresh() {
        if (isLoadingInternal) return
        viewModelScope.launch {
            isLoadingInternal = true
            _isLoading.value = true
            _error.value = null
            currentPage = 1
            try {
                val result = withContext(Dispatchers.IO) {
                    service.fetchMessageDialogs(page = 1, unread = 0)
                }
                _dialogs.value = result.dialogs
                hasMore = result.hasMore
                currentPage = 1
                AppLog.d("Messages", "refresh ok: page=1, dialogs=${result.dialogs.size}, hasMore=${result.hasMore}")
            } catch (e: Exception) {
                AppLog.e("Messages", "refresh failed: page=1", e)
                _error.value = e.message ?: "消息列表加载失败"
            } finally {
                _isLoading.value = false
                isLoadingInternal = false
            }
        }
    }

    /** 轻量未读检查：调用 /api/my/check 更新 _checkData */
    private suspend fun refreshCheck() {
        try {
            val data = withContext(Dispatchers.IO) { service.fetchMyCheck() }
            _checkData.value = data
        } catch (e: Exception) {
            AppLog.e("Messages", "refreshCheck failed", e)
        }
    }

    fun loadMore() {
        if (isLoadingInternal || !hasMore || _isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            // 固定本次请求的页码，避免追加过程中变量变化导致日志口径不一致
            val nextPage = currentPage + 1
            try {
                val result = withContext(Dispatchers.IO) {
                    service.fetchMessageDialogs(page = nextPage, unread = 0)
                }
                _dialogs.value = _dialogs.value + result.dialogs
                hasMore = result.hasMore
                currentPage++
                // 调试日志：翻页参数、本页条数与累计总数
                AppLog.d(
                    "Messages",
                    "loadMore ok: page=$nextPage, got=${result.dialogs.size}, total=${_dialogs.value.size}, hasMore=${result.hasMore}"
                )
            } catch (e: Exception) {
                // 错误日志：用户模式下也能看到加载更多失败
                AppLog.e("Messages", "loadMore failed: page=$nextPage", e)
                _error.value = e.message ?: "加载更多失败"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    suspend fun fetchMessages(userID: String): List<MessageItem> {
        return withContext(Dispatchers.IO) {
            service.fetchMessages(userID).messages
        }
    }

    suspend fun fetchFAQs(userID: String): List<MessageFAQ> {
        return withContext(Dispatchers.IO) {
            runCatching { service.fetchFAQs(userID) }
                // FAQ 属于锦上添花的数据，失败时按设计回退为空列表，仅 W 级记录便于排查
                .onFailure { AppLog.w("Messages", "fetchFAQs failed, fallback to empty: userId=$userID", it) }
                .getOrDefault(emptyList())
        }
    }

    suspend fun sendMessage(userID: String, content: String): MessageItem {
        return withContext(Dispatchers.IO) {
            service.sendMessage(userID, content)
        }
    }

    // 发送 FAQ 快捷提问（服务器会自动回复答案）
    suspend fun askFaq(userID: String, faqId: Int) {
        withContext(Dispatchers.IO) {
            service.askFaq(userID, faqId)
        }
    }

    fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(30000)
                // 轮询 /api/my/check 更新未读计数（轻量请求，不影响对话列表加载）
                refreshCheck()
            }
        }
    }
}
