package com.hrsthrt74.afdian.ui.viewmodel

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.AfadianContentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 动态互动状态控制器：集中管理「已点赞 / 已收藏」的动态 ID 集合。
 *
 * 设计要点：
 * - 采用「乐观更新」：点击瞬间先改本地状态让 UI 立即反馈，
 *   网络请求失败时再回滚，避免等待接口的卡顿感。
 * - 全局单例（[get]）：发现页、收藏页、动态详情页共享同一份状态，
 *   在任意一处点赞/收藏，其他页面的高亮立即同步。
 */
class PostInteractionController private constructor(
    private val service: AfadianContentService,
    // App 级作用域：请求不随页面销毁取消（乐观回滚依赖请求完成回调）
    private val scope: CoroutineScope,
) {

    companion object {
        @Volatile private var instance: PostInteractionController? = null

        /** 获取全局唯一实例（Application 级缓存，换账号时随进程重启自然重置） */
        fun get(context: android.content.Context): PostInteractionController =
            instance ?: synchronized(this) {
                instance ?: PostInteractionController(
                    service = AfadianContentService(com.hrsthrt74.afdian.data.auth.AuthTokenStore(context.applicationContext as android.app.Application)),
                    scope = kotlinx.coroutines.CoroutineScope(
                        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
                    ),
                ).also { instance = it }
            }
    }

    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    val likedIds = _likedIds.asStateFlow()
    private val _markedIds = MutableStateFlow<Set<String>>(emptySet())
    val markedIds = _markedIds.asStateFlow()

    /**
     * 状态代次：每次 [reset]（登出）自增。
     *
     * 点赞/收藏是乐观更新，失败时会把本地状态翻转回去。如果请求在途时正好登出，
     * 回滚回调醒来后会把上一个账号的 id 重新加回已经清空的集合里，
     * 于是下一个账号就看到了上一任的点赞/收藏高亮。回调执行前比对代次即可丢弃这种过期回滚。
     */
    @Volatile private var stateEpoch = 0

    /**
     * 拉取服务端的「我收藏的动态」ID 全集（翻页直到没有更多，最多 10 页防止过重）。
     * 挂起函数，由 ViewModel 在刷新列表时并行调用。
     */
    suspend fun fetchServerMarkedIds(): Set<String> = withContext(Dispatchers.IO) {
        var page = 1
        val ids = mutableListOf<String>()
        while (page <= 10) {
            val result = service.fetchMarkedPosts(page)
            ids += result.posts.map { it.id }
            if (!result.hasMore) break
            page++
        }
        ids.toSet()
    }

    /** 批量预置收藏状态（在列表展示前调用，保证图标一次渲染正确） */
    fun seedMarked(ids: Set<String>) { _markedIds.value = ids }

    /**
     * 清空共享互动状态。
     *
     * 点赞/收藏属于账号维度的数据，而本控制器是进程级单例（生命周期比登录会话长），
     * 登出时不主动清空，下一个账号会看到上一个账号留下的高亮标记。
     */
    fun reset() {
        AppLog.d("Interactions", "登出：清空共享互动状态")
        // 先自增代次：让所有在途请求的回滚回调作废
        stateEpoch++
        _likedIds.value = emptySet()
        _markedIds.value = emptySet()
    }

    /**
     * 批量预置点赞状态：来自动态数据里的 has_like 字段（服务端真实值）。
     * 注意是「合并」而不是覆盖——列表是分页追加的，不能把之前页的点赞状态冲掉。
     */
    fun seedLiked(ids: Set<String>) { _likedIds.value = _likedIds.value + ids }

    /** 切换点赞：先乐观翻转本地状态，再异步请求服务端，失败则回滚 */
    fun toggleLike(postId: String) {
        val nowLiked = postId !in _likedIds.value
        // 记下发起时的代次：回滚前要确认这期间没有登出换过账号
        val epoch = stateEpoch
        // 调试日志：动作发起（postId + 动作类型），改走全局门面以受调试开关控制
        AppLog.d("Interactions", "toggleLike: id=$postId, action=${if (nowLiked) "like" else "unlike"}")
        _likedIds.value = if (nowLiked) _likedIds.value + postId else _likedIds.value - postId
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (nowLiked) service.likePost(postId) else service.unlikePost(postId)
                }
            }.onFailure {
                // 期间已登出：这次回滚属于上一个账号，加到新账号的状态里就是串号
                if (epoch != stateEpoch) {
                    AppLog.d("Interactions", "已登出，丢弃过期的点赞回滚: id=$postId")
                    return@onFailure
                }
                // 请求失败：回滚本地状态，下次可重试；W 级始终输出便于用户模式排查
                AppLog.w("Interactions", "toggleLike failed, rollback: id=$postId", it)
                _likedIds.value = if (nowLiked) _likedIds.value - postId else _likedIds.value + postId
            }
        }
    }

    /** 切换收藏：逻辑同上 */
    fun toggleMark(postId: String) {
        val nowMarked = postId !in _markedIds.value
        val epoch = stateEpoch
        // 调试日志：动作发起（postId + 动作类型）
        AppLog.d("Interactions", "toggleMark: id=$postId, action=${if (nowMarked) "mark" else "unmark"}")
        _markedIds.value = if (nowMarked) _markedIds.value + postId else _markedIds.value - postId
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (nowMarked) service.markPost(postId) else service.unmarkPost(postId)
                }
            }.onFailure {
                if (epoch != stateEpoch) {
                    AppLog.d("Interactions", "已登出，丢弃过期的收藏回滚: id=$postId")
                    return@onFailure
                }
                // 请求失败：回滚本地状态；W 级始终输出
                AppLog.w("Interactions", "toggleMark failed, rollback: id=$postId", it)
                _markedIds.value = if (nowMarked) _markedIds.value - postId else _markedIds.value + postId
            }
        }
    }
}
