package com.hrsthrt74.afdian.ui.viewmodel

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
 * - 发现页（MainViewModel）和收藏页（FavoritesViewModel）各持有一个实例，
 *   两边状态独立（列表来源不同），但逻辑只有这一份。
 * - 收藏状态的真实来源是服务端：由调用方在刷新列表时并行拉取
 *   「我收藏的动态」接口，通过 [seedMarked] 在展示前回填。
 */
class PostInteractionController(
    private val service: AfadianContentService,
    // 网络请求跑在这个作用域里（由 ViewModel 传入 viewModelScope，页面销毁时自动取消）
    private val scope: CoroutineScope,
) {

    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    val likedIds = _likedIds.asStateFlow()
    private val _markedIds = MutableStateFlow<Set<String>>(emptySet())
    val markedIds = _markedIds.asStateFlow()

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
     * 批量预置点赞状态：来自动态数据里的 has_like 字段（服务端真实值）。
     * 注意是「合并」而不是覆盖——列表是分页追加的，不能把之前页的点赞状态冲掉。
     */
    fun seedLiked(ids: Set<String>) { _likedIds.value = _likedIds.value + ids }

    /** 切换点赞：先乐观翻转本地状态，再异步请求服务端，失败则回滚 */
    fun toggleLike(postId: String) {
        val nowLiked = postId !in _likedIds.value
        _likedIds.value = if (nowLiked) _likedIds.value + postId else _likedIds.value - postId
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (nowLiked) service.likePost(postId) else service.unlikePost(postId)
                }
            }.onFailure {
                // 请求失败：回滚本地状态，下次可重试
                _likedIds.value = if (nowLiked) _likedIds.value - postId else _likedIds.value + postId
            }
        }
    }

    /** 切换收藏：逻辑同上 */
    fun toggleMark(postId: String) {
        val nowMarked = postId !in _markedIds.value
        _markedIds.value = if (nowMarked) _markedIds.value + postId else _markedIds.value - postId
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (nowMarked) service.markPost(postId) else service.unmarkPost(postId)
                }
            }.onFailure {
                _markedIds.value = if (nowMarked) _markedIds.value - postId else _markedIds.value + postId
            }
        }
    }
}
