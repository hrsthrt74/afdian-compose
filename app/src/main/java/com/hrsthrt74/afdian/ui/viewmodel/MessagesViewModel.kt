package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.BlackedType
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.data.content.MessageFAQ
import com.hrsthrt74.afdian.data.content.MessageItem
import com.hrsthrt74.afdian.data.content.MyCheckData
import com.hrsthrt74.afdian.data.content.SponsorInfo
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.data.history.HistoryStore
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    private val settingsStore = SettingsStore(application)
    // 浏览历史仓库：打开会话时写入「私信对话」记录，开关关闭时仓库内部会忽略
    private val historyStore = HistoryStore(application)

    private val _dialogs = MutableStateFlow<List<MessageDialog>>(emptyList())
    val dialogs = _dialogs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // ── 过滤状态 ──
    private val _showUnreadOnly = MutableStateFlow(false)
    val showUnreadOnly = _showUnreadOnly.asStateFlow()

    private val _showRecentOnly = MutableStateFlow(false)
    val showRecentOnly = _showRecentOnly.asStateFlow()

    /** 发电明细开关（持久化） */
    private val _showSponsorDetail = MutableStateFlow(false)
    val showSponsorDetail = _showSponsorDetail.asStateFlow()

    /** 根据过滤条件筛选后的对话列表 */
    val filteredDialogs: StateFlow<List<MessageDialog>> = combine(
        _dialogs, _showUnreadOnly, _showRecentOnly
    ) { dialogs, unreadOnly, recentOnly ->
        dialogs.filter { dialog ->
            val unreadOk = !unreadOnly || dialog.unread_count > 0
            val recentOk = !recentOnly || dialog.send_time?.let { ts ->
                val twoWeeksAgo = (System.currentTimeMillis() / 1000.0) - 14 * 24 * 60 * 60
                ts >= twoWeeksAgo
            } ?: false
            unreadOk && recentOk
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** 未读评论数（check 接口 unread_count.comment），用于消息页三点菜单「收到的评论」徽标 */
    val unreadCommentCount: StateFlow<Int> = _checkData
        .map { it.unreadComment }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 未读喜欢数（check 接口 unread_count.like），用于消息页三点菜单「收到的喜欢」徽标 */
    val unreadLikeCount: StateFlow<Int> = _checkData
        .map { it.unreadLike }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var currentPage = 1
    private var _hasMoreRaw = true
    private var isLoadingInternal = false

    init {
        refresh()
        // 首次进入立即拉取未读计数，不等 auto-refresh
        viewModelScope.launch { refreshUnreadCount() }
        // 从 DataStore 恢复发电明细开关状态
        viewModelScope.launch {
            _showSponsorDetail.value = settingsStore.msgShowSponsorDetail.first()
        }
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
                _hasMoreRaw = result.hasMore
                _hasMore.value = result.hasMore
                currentPage = 1
                AppLog.d("Messages", "refresh ok: page=1, dialogs=${result.dialogs.size}, hasMore=${result.hasMore}")
                // 下拉刷新同时同步底部导航栏的全局未读数；列表接口不会返回评论/点赞未读数。
                refreshUnreadCount()
            } catch (e: Exception) {
                AppLog.e("Messages", "refresh failed: page=1", e)
                _error.value = e.message ?: "消息列表加载失败"
            } finally {
                _isLoading.value = false
                isLoadingInternal = false
            }
        }
    }

    /** 轻量未读检查：调用 /api/my/check 更新底部导航栏使用的全局未读数。 */
    suspend fun refreshUnreadCount() {
        try {
            val data = withContext(Dispatchers.IO) { service.fetchMyCheck() }
            _checkData.value = data
        } catch (e: Exception) {
            AppLog.e("Messages", "refresh unread count failed", e)
        }
    }

    fun loadMore() {
        if (isLoadingInternal || !_hasMoreRaw || _isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            // 固定本次请求的页码，避免追加过程中变量变化导致日志口径不一致
            val nextPage = currentPage + 1
            try {
                val result = withContext(Dispatchers.IO) {
                    service.fetchMessageDialogs(page = nextPage, unread = 0)
                }
                _dialogs.value = _dialogs.value + result.dialogs
                _hasMoreRaw = result.hasMore
                _hasMore.value = result.hasMore
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

    /**
     * 打开会话时先清除列表中的本地未读 badge。
     *
     * 消息页位于常驻的 HorizontalPager 中，进入会话再返回时不会重新组合列表，
     * 因此不能只依赖服务端下次返回的数据；这里先更新本地状态，随后由会话页同步服务端未读总数。
     */
    fun markConversationRead(userId: String) {
        if (userId.isBlank()) return
        var clearedCount = 0
        _dialogs.value = _dialogs.value.map { dialog ->
            if (dialog.user.user_id == userId && dialog.unread_count > 0) {
                clearedCount += dialog.unread_count
                dialog.copy(unread_count = 0)
            } else {
                dialog
            }
        }
        if (clearedCount > 0) {
            AppLog.d("Messages", "conversation marked read locally: unread=$clearedCount")
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

    /** 拉取我与目标用户的赞助详情（我发电TA / TA发电我），失败返回 null 交由上层展示空态 */
    suspend fun fetchSponsorInfo(userID: String): SponsorInfo? {
        return withContext(Dispatchers.IO) {
            runCatching { service.fetchSponsorInfo(userID) }
                .onFailure { AppLog.w("Messages", "fetchSponsorInfo failed: userId=$userID", it) }
                .getOrNull()
        }
    }

    suspend fun sendMessage(userID: String, content: String): MessageItem {
        return withContext(Dispatchers.IO) {
            service.sendMessage(userID, content)
        }
    }

    /**
     * 加入屏蔽 / 拒绝名单（POST /api/user/black）。
     * 返回服务端提示文案（如「已加入屏蔽名单」）；失败抛出异常，由调用方提示。
     */
    suspend fun blackUser(userId: String, type: BlackedType): String {
        return withContext(Dispatchers.IO) {
            service.black(userId, type)
        }
    }

    /**
     * 打开私信会话时写入浏览历史（fire-and-forget，不阻塞会话加载）。
     * 参数与会话页路由一致；记录、去重置顶、头像缓存均由 HistoryStore 处理。
     */
    fun recordConversationOpened(userId: String, userName: String, avatarUrl: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            historyStore.recordOpened(
                HistoryEntry.fromConversation(userId, userName, avatarUrl).target
            )
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
                refreshUnreadCount()
            }
        }
    }

    fun toggleUnreadFilter() {
        _showUnreadOnly.value = !_showUnreadOnly.value
    }

    fun toggleRecentFilter() {
        _showRecentOnly.value = !_showRecentOnly.value
    }

    fun toggleSponsorDetail() {
        val newValue = !_showSponsorDetail.value
        _showSponsorDetail.value = newValue
        viewModelScope.launch { settingsStore.setMsgShowSponsorDetail(newValue) }
    }
}
