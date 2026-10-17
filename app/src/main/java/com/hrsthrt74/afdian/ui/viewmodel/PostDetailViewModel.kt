package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.FeedService
import com.hrsthrt74.afdian.data.content.PostComment
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.data.content.SubReplyState
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.data.history.HistoryStore
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 动态详情页 ViewModel。
 *
 * 数据策略：路由里携带的列表页 post 先直接上屏（秒开，不转圈），
 * 后台再调 get-detail 拉取完整数据（含全文 HTML 正文）覆盖刷新。
 * 点赞/收藏使用全局共享的 [PostInteractionController]，与列表页高亮一致。
 *
 * 评论（本页只读 + 点赞，不含发布/删除）：
 * - 懒加载：详情页不会一进来就请求，评论区首次滚动进视口才由 UI 调用 [loadComments]。
 * - 分页：游标取当前最旧一条的 `publish_sn`（服务端返回严格更早的数据）。
 * - 楼中楼：主列表接口内嵌前几条回复，`sub_comment_has_more == 1` 时由
 *   [toggleSubReplies] 展开时调 get-sub-list 补齐，翻页走 [loadMoreReplies]。
 * - 点赞：乐观更新，失败回滚（与动态点赞同一套口径，见 `displayLikeCount`）。
 */
class PostDetailViewModel(
    application: Application,
    initialPost: RecommendedPost,
) : AndroidViewModel(application) {

    private val service = AfadianContentService(AuthTokenStore(application))
    // 评论接口没有走门面，直接依赖动态/互动领域服务（与 FaqViewModel / SponsorManageViewModel 同款写法）
    private val commentService = FeedService(AuthTokenStore(application), sharedAfdianHttpClient)
    private val historyStore = HistoryStore(application)
    // 全局共享的点赞/收藏状态（与发现页、收藏页互通）
    val interactions = PostInteractionController.get(application)
    private val _post = MutableStateFlow(initialPost)
    val post = _post.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    // 仅当路由只带 id（占位 post）、还没有真实内容时处于加载中；
    // 列表页携带完整 post 进入时初始即为 false，保持「秒显」体验
    private val _loading = MutableStateFlow(initialPost.user.user_id.isBlank())
    val loading = _loading.asStateFlow()

    /** 完整详情请求是否已出结果（成功 / 失败都算）。
     *  UI 用它在「路由预览态 → 全文态」切换期间避免按旧结构定位滚动锚点。 */
    private val _detailSettled = MutableStateFlow(false)
    val detailSettled = _detailSettled.asStateFlow()

    // ──────── 评论状态 ────────

    /** 根评论列表（已把服务端 hot_list 前置合并，按接口顺序追加） */
    private val _comments = MutableStateFlow<List<PostComment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading = _commentsLoading.asStateFlow()

    /** 首屏评论是否已请求过：懒加载只触发一次，重试入口才复用 [loadComments] */
    private val _commentsLoaded = MutableStateFlow(false)

    private val _commentsHasMore = MutableStateFlow(false)
    val commentsHasMore = _commentsHasMore.asStateFlow()

    private val _commentsLoadingMore = MutableStateFlow(false)
    val commentsLoadingMore = _commentsLoadingMore.asStateFlow()

    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError = _commentsError.asStateFlow()

    /** 各根评论的「展开更多回复」状态，key 为根评论 id */
    private val _subReplies = MutableStateFlow<Map<String, SubReplyState>>(emptyMap())
    val subReplies = _subReplies.asStateFlow()

    /** 本地评论点赞态：加载时按服务端 has_like == 1 种子化，切换时乐观更新 */
    private val _likedCommentIds = MutableStateFlow<Set<String>>(emptySet())
    val likedCommentIds = _likedCommentIds.asStateFlow()

    /** 下一页游标（最近一次响应的 nextPublishSn），私有保存即可，不暴露给 UI */
    private var nextPublishSn: String = ""

    init { load() }

    /** 拉取动态完整详情（全文 HTML），成功后覆盖当前展示的数据 */
    fun load() {
        // 先固定 postId，避免协程期间 _post 被覆盖后日志口径不一致
        val postId = _post.value.id
        viewModelScope.launch {
            // 先记录入口，再请求全文；即使网络失败，用户也能在本地历史里找回这条动态。
            withContext(Dispatchers.IO) {
                historyStore.recordOpened(HistoryEntry.from(_post.value).target)
            }
            // 调试日志：记录详情加载起点
            AppLog.d("PostDetail", "load: postId=$postId")
            runCatching {
                withContext(Dispatchers.IO) { service.fetchPostDetail(postId) }
            }.onSuccess {
                _post.value = it
                _loading.value = false
                _detailSettled.value = true
                // 占位路由（只有 post_id）成功加载后补全历史条目展示信息。
                viewModelScope.launch(Dispatchers.IO) {
                    historyStore.updateSnapshot(HistoryEntry.from(it).target)
                }
                // 调试日志：只输出信息量字段（标题长度、点赞/评论数），不打印全文正文
                AppLog.d(
                    "PostDetail",
                    "load ok: postId=$postId, titleLen=${it.title.length}, likes=${it.like_count}, comments=${it.comment_count}"
                )
            }.onFailure {
                _loading.value = false
                _detailSettled.value = true
                // 错误日志：用户模式下也能看到加载失败原因
                AppLog.e("PostDetail", "load failed: postId=$postId", it)
                _error.value = it.message ?: "动态详情加载失败"
            }
        }
    }

    // ──────── 评论：列表 ────────

    /**
     * 加载评论首屏。由详情页在评论区首次进入视口时调用；已加载过则直接返回，
     * 避免滚动来回经过评论区反复请求。
     */
    fun loadComments() {
        if (_commentsLoaded.value || _commentsLoading.value) return
        val postId = _post.value.id
        if (postId.isBlank()) return
        _commentsLoading.value = true
        _commentsError.value = null
        viewModelScope.launch {
            AppLog.d("PostComments", "load: postId=$postId")
            runCatching {
                withContext(Dispatchers.IO) { commentService.fetchCommentList(postId) }
            }.onSuccess { page ->
                _comments.value = page.comments
                nextPublishSn = page.nextPublishSn
                _commentsHasMore.value = page.hasMore
                _commentsLoaded.value = true
                _commentsLoading.value = false
                seedLiked(page.comments)
            }.onFailure {
                _commentsLoading.value = false
                AppLog.e("PostComments", "load failed: postId=$postId", it)
                _commentsError.value = it.message ?: "评论加载失败"
            }
        }
    }

    /** 加载下一页评论（游标 = 当前最旧一条的 publish_sn） */
    fun loadMoreComments() {
        if (_commentsLoadingMore.value || !_commentsHasMore.value) return
        val postId = _post.value.id
        if (postId.isBlank()) return
        _commentsLoadingMore.value = true
        viewModelScope.launch {
            val cursor = nextPublishSn
            runCatching {
                withContext(Dispatchers.IO) { commentService.fetchCommentList(postId, cursor) }
            }.onSuccess { page ->
                _comments.value = _comments.value + page.comments
                nextPublishSn = page.nextPublishSn
                _commentsHasMore.value = page.hasMore
                _commentsLoadingMore.value = false
                seedLiked(page.comments)
            }.onFailure {
                _commentsLoadingMore.value = false
                AppLog.w("PostComments", "load more failed: postId=$postId, cursor=$cursor", it)
            }
        }
    }

    /** 错误态的「重试」：清掉已加载标记，回到首屏重新请求 */
    fun retryComments() {
        _commentsLoaded.value = false
        loadComments()
    }

    // ──────── 评论：楼中楼 ────────

    /**
     * 展开某条根评论的更多回复。
     *
     * **单向**，没有收起：点开后就一直显示。已经请求过就直接返回，
     * 不重复打接口（即使上次返回空或失败，[SubReplyState.attempted] 也会挡住连点）。
     */
    fun expandSubReplies(rootCommentId: String) {
        val state = _subReplies.value[rootCommentId] ?: SubReplyState()
        if (state.attempted || state.loading) return
        // 官方网页端要求：内嵌回复非空时必须带游标，否则视为契约违规（见 fetchSubReplies 注释）
        fetchSubReplies(rootCommentId, publishSn = embeddedLastPublishSn(rootCommentId), append = false)
    }

    /** 楼中楼回复翻页：游标取「已追加的回复 + 服务端内嵌回复」里最旧一条的 publish_sn */
    fun loadMoreReplies(rootCommentId: String) {
        val state = _subReplies.value[rootCommentId] ?: return
        if (state.loading || !state.hasMore) return
        val cursor = state.extra.lastOrNull()?.publish_sn?.toString()
            ?: embeddedLastPublishSn(rootCommentId)
        if (cursor.isBlank()) return
        fetchSubReplies(rootCommentId, publishSn = cursor, append = true)
    }

    /**
     * 执行一次 get-sub-list 请求并写回状态。
     *
     * ⚠️ 游标规则（对齐官方 `load_more_reply`）：服务端内嵌了 sub_comments 时，
     * 空游标会被判定为契约违规，所以首次展开必须传「内嵌回复里最旧一条的 publish_sn」；
     * 没有内嵌回复时才用空串从头拉。
     *
     * 返回结果按 comment_id 与已有回复去重：服务端分页边界可能与内嵌数据重叠。
     */
    private fun fetchSubReplies(rootCommentId: String, publishSn: String, append: Boolean) {
        val postId = _post.value.id
        if (postId.isBlank()) return
        updateSubReplies(rootCommentId) { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    commentService.fetchSubComments(postId, rootCommentId, publishSn)
                }
            }.onSuccess { page ->
                updateSubReplies(rootCommentId) { current ->
                    val known = knownReplyIds(rootCommentId, current)
                    val fresh = page.comments.filter { it.comment_id.isNotBlank() && it.comment_id !in known }
                    current.copy(
                        extra = (if (append) current.extra else emptyList()) + fresh,
                        // hasMore 以服务端返回为准（官方也是这么覆盖的）
                        hasMore = page.hasMore,
                        loading = false,
                        attempted = true,
                    )
                }
                seedLiked(page.comments)
            }.onFailure {
                // 失败时**不**置 attempted：留着「展开更多回复」这一行，用户才能再点一次重试。
                // 只有服务端明确返回空 / 取完才置位（那时 UI 会把这一行收掉）。
                updateSubReplies(rootCommentId) { it.copy(loading = false) }
                AppLog.w("PostComments", "sub-list failed: root=$rootCommentId, cursor=$publishSn", it)
            }
        }
    }

    /** 已知的回复 id 集合：服务端内嵌的 + 已追加的，用于去重 */
    private fun knownReplyIds(rootCommentId: String, state: SubReplyState): Set<String> {
        val embedded = _comments.value.firstOrNull { it.comment_id == rootCommentId }?.sub_comments.orEmpty()
        return (embedded + state.extra).map { it.comment_id }.toSet()
    }

    /** 内嵌回复（sub_comments）里最旧一条的 publish_sn；没有内嵌回复时返回空串 */
    private fun embeddedLastPublishSn(rootCommentId: String): String =
        _comments.value.firstOrNull { it.comment_id == rootCommentId }
            ?.sub_comments?.lastOrNull()?.publish_sn?.toString().orEmpty()

    private fun updateSubReplies(rootCommentId: String, transform: (SubReplyState) -> SubReplyState) {
        _subReplies.value = _subReplies.value.toMutableMap().apply {
            this[rootCommentId] = transform(this[rootCommentId] ?: SubReplyState())
        }
    }

    // ──────── 评论：点赞 ────────

    /** 切换评论点赞：先乐观翻转本地态，再请求服务端，失败回滚 */
    fun toggleCommentLike(commentId: String) {
        val nowLiked = commentId !in _likedCommentIds.value
        AppLog.d("PostComments", "toggleCommentLike: id=$commentId, action=${if (nowLiked) "like" else "unlike"}")
        _likedCommentIds.value =
            if (nowLiked) _likedCommentIds.value + commentId else _likedCommentIds.value - commentId
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (nowLiked) commentService.likeComment(commentId) else commentService.unlikeComment(commentId)
                }
            }.onFailure {
                AppLog.w("PostComments", "toggleCommentLike failed, rollback: id=$commentId", it)
                _likedCommentIds.value =
                    if (nowLiked) _likedCommentIds.value - commentId else _likedCommentIds.value + commentId
            }
        }
    }

    /**
     * 用服务端返回的 has_like 种子化本地点赞态。
     *
     * 用「合并」而非覆盖：评论是分页追加的，且楼中楼回复会后到，
     * 覆盖会把之前页 / 之前展开的点赞态冲掉。
     */
    private fun seedLiked(comments: List<PostComment>) {
        val serverLiked = comments.filter { it.has_like == 1 }.map { it.comment_id }.toSet()
        if (serverLiked.isEmpty()) return
        _likedCommentIds.value = _likedCommentIds.value + serverLiked
    }
}
