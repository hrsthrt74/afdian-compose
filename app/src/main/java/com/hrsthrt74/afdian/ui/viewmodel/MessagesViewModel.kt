package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import android.widget.Toast
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
import com.hrsthrt74.afdian.data.content.mergeDialogs
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.data.history.HistoryStore
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    internal companion object {
        /**
         * 「全部标为已读」同时处理的会话数。
         *
         * 并发再高收益有限：未读会话通常只有个位数，而每个会话都要拉一整页消息，
         * 并发过大会造成流量尖峰，也更容易触发服务端限流。
         */
        internal const val MARK_ALL_CONCURRENCY = 3
    }
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

    /** 「全部标为已读」是否正在执行，用于禁用确认按钮并防止重复触发 */
    private val _isMarkingAllRead = MutableStateFlow(false)
    val isMarkingAllRead = _isMarkingAllRead.asStateFlow()

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

    /**
     * 列表批次号：每次 [refresh] 自增，用于作废在飞的 [loadMore]。
     *
     * 页码分页在「刷新 + 翻页」交错时会把旧页追加到刷新后的新列表上（既重复又缺页），
     * 所以刷新一发生，之前发出的翻页请求就整页丢弃。与 `ApiProbeViewModel.runEpoch` 同一套做法。
     */
    private var listEpoch = 0

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
        // 防重入标志必须在启动协程前同步置位：写在 launch 内部时，
        // init / 下拉刷新 / 从会话页返回 短时间内连续调用两次，两个调用都读到 false，各发一次首页请求
        if (isLoadingInternal) return
        isLoadingInternal = true
        // 作废所有在飞的 loadMore：旧页的排序基准已失效，回来时批次号对不上就整页丢弃。
        // 这里刻意不挡 _isLoadingMore——下拉刷新是用户的明确意图，等翻页跑完再执行会让人以为刷新失效
        listEpoch++
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    service.fetchMessageDialogs(page = 1, unread = 0)
                }
                // 首页同样过一遍合并：服务端偶发重复会话 / 缺 user 对象时，首屏一样会撞 key
                val merged = mergeDialogs(emptyList(), result.dialogs)
                _dialogs.value = merged
                _hasMoreRaw = result.hasMore
                _hasMore.value = result.hasMore
                // 页码只在成功后拨回 1。原先在请求前就重置，失败时列表还是 1..N 的旧内容，
                // 下一次 loadMore 却按 page=2 取，会再追加一页重复会话
                currentPage = 1
                logMerge("refresh", page = 1, existingSize = 0, incoming = result.dialogs, mergedSize = merged.size)
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

    /**
     * 合并后丢掉过会话才记 W（正常分页不该丢任何一条）。
     * 只打计数不打 user_id：日志会落盘导出，用户标识不该出现在里面。
     */
    private fun logMerge(
        action: String,
        page: Int,
        existingSize: Int,
        incoming: List<MessageDialog>,
        mergedSize: Int,
    ) {
        val dropped = existingSize + incoming.size - mergedSize
        if (dropped <= 0) return
        val blankId = incoming.count { it.user.user_id.isBlank() }
        AppLog.w(
            "Messages",
            "$action 丢弃 $dropped 条重复/无效会话：page=$page, got=${incoming.size}, blankId=$blankId, kept=$mergedSize"
        )
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
        // 同步占位：触底回调可能在一帧内被连续触发，写在协程里会放两个同页码的请求进去
        _isLoadingMore.value = true
        // 固定本次批次与页码：批次变了（期间刷新过）就整页丢弃；页码只在本页真正提交后才推进
        val epoch = listEpoch
        val nextPage = currentPage + 1
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    service.fetchMessageDialogs(page = nextPage, unread = 0)
                }
                if (epoch != listEpoch) {
                    // 期间列表已刷新：这一页的排序基准失效，丢弃它，hasMore 与 currentPage 都不动
                    AppLog.d("Messages", "loadMore 结果已过期（期间已刷新列表），整页丢弃：page=$nextPage")
                    return@launch
                }
                // 提交时才读当前列表：期间 markConversationRead 可能已清掉本地 badge，
                // 用请求发起时的快照合并会把清零结果覆盖回未读
                val current = _dialogs.value
                val merged = mergeDialogs(current, result.dialogs)
                _dialogs.value = merged
                _hasMoreRaw = result.hasMore
                _hasMore.value = result.hasMore
                currentPage = nextPage
                logMerge(
                    action = "loadMore",
                    page = nextPage,
                    existingSize = current.size,
                    incoming = result.dialogs,
                    mergedSize = merged.size,
                )
                // 调试日志：翻页参数、本页条数与累计总数
                AppLog.d(
                    "Messages",
                    "loadMore ok: page=$nextPage, got=${result.dialogs.size}, total=${merged.size}, hasMore=${result.hasMore}"
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
        // map 是 1:1 变换（不增删、不调序、用户 id 不变），不会破坏「id 唯一」这条列表不变式，
        // 所以这条写路径不需要再过一遍 mergeDialogs
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

    /**
     * 「全部标为已读」：把每个未读会话依次读一遍，借此让服务端清零未读。
     *
     * 爱发电没有一键已读接口（网页端也只有 dialogs/messages/send 等五个），
     * 服务端仅在加载历史消息时清除该会话未读，因此这里只能逐个读取：
     * 1. 先用 dialogs?unread=1 拿权威未读清单——/api/my/check 有缓存延迟，不能用来判断；
     * 2. 以 [MARK_ALL_CONCURRENCY] 的并发逐个读取，单个失败不影响其余会话；
     * 3. 每个成功后立刻本地清 badge，让用户看到进度而不是干等；
     * 4. 全部结束后用 refreshUnreadCount() 以服务端状态为准，避免本地推断与服务端不一致。
     */
    fun markAllRead() {
        // 菜单点击后弹层会收起，这里再挡一次，防止重复触发
        if (_isMarkingAllRead.value) return
        viewModelScope.launch {
            _isMarkingAllRead.value = true
            try {
                // 未读清单以服务端为准，不信任本地列表已加载的部分
                val unread = withContext(Dispatchers.IO) { service.fetchUnreadDialogs() }
                if (unread.isEmpty()) {
                    toast("没有未读消息")
                    return@launch
                }

                // Dispatchers.IO 是多线程池，成功/失败计数必须用原子变量，普通 var 并发自增会丢数
                val succeeded = AtomicInteger(0)
                val failed = AtomicInteger(0)
                val semaphore = Semaphore(MARK_ALL_CONCURRENCY)
                unread.map { dialog ->
                    launch(Dispatchers.IO) {
                        semaphore.withPermit {
                            runCatching { service.markDialogRead(dialog.user.user_id) }
                                .onSuccess {
                                    succeeded.incrementAndGet()
                                    // 服务端已清零，本地乐观同步（StateFlow 赋值线程安全，可在 IO 线程直接改）
                                    markConversationRead(dialog.user.user_id)
                                }
                                .onFailure { error ->
                                    failed.incrementAndGet()
                                    AppLog.w("Messages", "markDialogRead failed: userId=${dialog.user.user_id}", error)
                                }
                        }
                    }
                }.joinAll()

                // 关键路径日志：一次标记的总量、成功数与失败数
                AppLog.d(
                    "Messages",
                    "markAllRead done: total=${unread.size}, ok=${succeeded.get()}, failed=${failed.get()}"
                )
                // 以服务端为准刷新全局未读数（底部 Tab badge / 三点菜单徽标都读它）
                refreshUnreadCount()
                toast(
                    if (failed.get() == 0) "已完成全部已读"
                    // 部分失败要如实报数，剩余未读仍会显示在列表徽标上，用户可重试
                    else "已标记 ${succeeded.get()}/${unread.size} 个会话，${failed.get()} 个失败"
                )
            } catch (e: CancellationException) {
                // 页面销毁导致的正常取消不算错误，静默处理
                throw e
            } catch (e: Exception) {
                AppLog.e("Messages", "markAllRead failed", e)
                toast("标记已读失败，请稍后重试")
            } finally {
                _isMarkingAllRead.value = false
            }
        }
    }

    /** 轻量提示；项目内 Toast 一律用 ViewModel 直接弹，不做额外封装 */
    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
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
