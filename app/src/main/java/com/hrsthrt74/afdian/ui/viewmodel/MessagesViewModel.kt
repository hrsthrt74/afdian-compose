package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.auth.CurrentAccount
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.BlackedType
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.data.content.MessageFAQ
import com.hrsthrt74.afdian.data.content.MessageItem
import com.hrsthrt74.afdian.data.content.MyCheckData
import com.hrsthrt74.afdian.data.content.PinnedDialogCard
import com.hrsthrt74.afdian.data.content.SponsorInfo
import com.hrsthrt74.afdian.data.content.mergeDialogs
import com.hrsthrt74.afdian.data.content.mergePinnedIntoDialogs
import com.hrsthrt74.afdian.data.content.toPinnedCard
import com.hrsthrt74.afdian.data.content.withServerData
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
import kotlin.time.Duration.Companion.seconds

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

    /**
     * 本地置顶的会话卡片（顺序即列表里的置顶顺序，新置顶的在前）。
     *
     * 存整卡而不是只存 id：置顶的老会话可能落在后面的分页里、首屏根本拉不到，
     * 只存 id 会出现「有置顶配置但列表里没有入口」。快照在置顶瞬间生成，
     * 之后每次服务端数据里出现同一会话时由 [syncPinnedCardsFromServer] 刷新。
     *
     * 初值空列表，`init` 里从 DataStore 回填；回填前列表按服务端顺序显示。
     */
    private val _pinnedCards = MutableStateFlow<List<PinnedDialogCard>>(emptyList())
    val pinnedCards: StateFlow<List<PinnedDialogCard>> = _pinnedCards.asStateFlow()

    /**
     * 根据过滤条件筛选 + 置顶合并的对话列表。
     *
     * 置顶合并只作用于**展示流**：`_dialogs` 本身必须保持服务端顺序，
     * `mergeDialogs` 的按页追加与 `markConversationRead` 的 map 1:1 变换都依赖这一点。
     *
     * 过滤在合并之后做：置顶会话若已从服务端加载到、只是被筛掉（比如「未读」筛选下已读），
     * 不应再拿快照补一张重复卡片进来。
     */
    val filteredDialogs: StateFlow<List<MessageDialog>> = combine(
        _dialogs, _showUnreadOnly, _showRecentOnly, _pinnedCards
    ) { dialogs, unreadOnly, recentOnly, pinned ->
        val merged = mergePinnedIntoDialogs(dialogs, pinned)
        merged.filter { dialog ->
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
        // 发电明细开关与置顶会话都是「按账号隔离」的设置，必须等账号身份就绪后再读。
        // 不能只读一次：换账号登录时本 ViewModel 会先于资料接口创建，此刻 CurrentAccount
        // 还是空的，只读一次会拿到空分区，导致新账号的置顶与开关整场会话都不生效。
        viewModelScope.launch {
            var lastOwner: String? = null
            CurrentAccount.userId.collect { owner ->
                if (owner == null || owner == lastOwner) return@collect
                val storedDetail = settingsStore.msgShowSponsorDetail.first()
                val storedPinned = settingsStore.msgPinnedDialogCards.first()
                // 换账号（lastOwner 非空）必须整体替换，不能保留上一个账号的内存态；
                // 同一账号的首次回填保留「用户已手动改过」的内存态，避免吞掉刚做的操作。
                if (lastOwner != null || _pinnedCards.value.isEmpty()) {
                    _pinnedCards.value = storedPinned
                }
                if (lastOwner != null || !_showSponsorDetail.value) {
                    _showSponsorDetail.value = storedDetail
                }
                lastOwner = owner
                AppLog.d("Messages", "账号就绪，已恢复置顶与发电明细开关，置顶数=${storedPinned.size}")
            }
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
                // 首屏数据到了：把置顶快照刷新成服务端最新值（置顶会话的新消息会跳到第一页）
                syncPinnedCardsFromServer(merged)
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
                // 翻到置顶会话所在页时，用服务端数据覆盖快照（昵称/头像/最后消息/未读）
                syncPinnedCardsFromServer(merged)
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
        // 置顶会话可能还没被分页加载到（列表上显示的是本地快照），快照里的未读数同样要清，
        // 否则点进会话后卡片上的 badge 还在
        clearPinnedCardUnread(userId)
    }

    /**
     * 把置顶快照里某条会话的未读清零并落盘（没有变化时直接返回，避免无谓写入）。
     */
    private fun clearPinnedCardUnread(userId: String) {
        val current = _pinnedCards.value
        if (current.none { it.userId == userId && it.unreadCount > 0 }) return
        val next = current.map { card ->
            if (card.userId == userId) card.copy(unreadCount = 0) else card
        }
        _pinnedCards.value = next
        persistPinnedCards(next)
    }

    /**
     * 切换某个会话的置顶状态：先更新内存态（列表立刻重排），再异步落盘。
     *
     * 置顶存的是**整张卡片快照**而非 id：置顶的老会话可能落在后面的分页里，
     * 只存 id 会导致列表里根本没有这条会话可显示（详见 [PinnedDialogCard] 的注释）。
     *
     * 内存优先的原因：置顶是即时可见的操作，等 DataStore 写完再刷新会有明显延迟感；
     * 写入失败只影响「下次启动是否还记得」，不值得回滚内存态（会把卡片再弹回去，更像 bug）。
     *
     * 日志只打数量不打 user_id：日志会落盘导出，用户标识不该出现在里面。
     */
    fun togglePinned(dialog: MessageDialog) {
        val userId = dialog.id
        if (userId.isBlank()) return
        val current = _pinnedCards.value
        val alreadyPinned = current.any { it.userId == userId }
        val next = if (alreadyPinned) {
            current.filterNot { it.userId == userId }
        } else {
            // 新置顶的放最前：置顶后立刻在列表顶部看到它，操作结果一目了然
            listOf(dialog.toPinnedCard()) + current
        }
        _pinnedCards.value = next
        AppLog.d(
            "Messages",
            "置顶状态已切换：pinnedCount=${next.size}, 本次=${if (alreadyPinned) "取消" else "置顶"}"
        )
        persistPinnedCards(next)
    }

    /** 落盘置顶快照（失败只记 W：内存态已经生效，下次启动最多丢一次置顶）。 */
    private fun persistPinnedCards(cards: List<PinnedDialogCard>) {
        viewModelScope.launch {
            runCatching { settingsStore.setMsgPinnedDialogCards(cards) }
                .onFailure { AppLog.w("Messages", "保存置顶会话失败", it) }
        }
    }

    /**
     * 用服务端数据刷新置顶快照的展示字段（昵称 / 头像 / 最后一条消息 / 时间 / 未读数）。
     *
     * 时机：每次列表刷新或翻页合并之后。置顶会话一旦有新消息就会跳到服务端第一页，
     * 于是快照被自然纠偏；长期没有新消息的会话，快照本来就是准的。
     * 没有任何字段变化时不写盘（翻页很频繁，无谓的 DataStore 写入会拖慢刷新）。
     */
    private fun syncPinnedCardsFromServer(dialogs: List<MessageDialog>) {
        val current = _pinnedCards.value
        if (current.isEmpty() || dialogs.isEmpty()) return
        val serverById = HashMap<String, MessageDialog>(dialogs.size)
        for (dialog in dialogs) {
            if (dialog.id.isNotBlank()) serverById[dialog.id] = dialog
        }
        var changed = false
        val next = current.map { card ->
            val server = serverById[card.userId] ?: return@map card
            val updated = card.withServerData(server)
            if (updated != card) changed = true
            updated
        }
        if (!changed) return
        _pinnedCards.value = next
        persistPinnedCards(next)
    }

    /**
     * 把单个会话标为已读（长按卡片菜单的「标为已读」）。
     *
     * 与「全部标为已读」同一条链路：服务端只在加载历史消息时清除该会话未读，
     * 所以必须调一次 `service.markDialogRead`；本地先清 badge 让操作即时可见。
     *
     * 服务端失败时**不回滚**本地 badge：与 [markAllRead] 的乐观策略保持一致，
     * 下次列表刷新会以服务端数据为准自然恢复。
     */
    fun markSingleRead(userId: String) {
        if (userId.isBlank()) return
        markConversationRead(userId)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { service.markDialogRead(userId) }
                // 同步底部 Tab badge / 三点菜单徽标（它们读的是 /api/my/check）
                refreshUnreadCount()
                AppLog.d("Messages", "单个会话已标为已读")
            } catch (e: CancellationException) {
                // 页面销毁导致的正常取消不算错误，必须原样抛出，不能被下面的分支吞成 W 日志
                throw e
            } catch (e: Exception) {
                AppLog.w("Messages", "单个会话标已读失败（本地 badge 已清，等下次刷新纠正）", e)
            }
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
                delay(30.seconds)
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
