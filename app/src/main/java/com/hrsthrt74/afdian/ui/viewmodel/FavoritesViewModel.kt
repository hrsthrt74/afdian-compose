package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.FavoriteItem
import com.hrsthrt74.afdian.data.content.RecommendedPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 单个收藏 tab（创作者 / 动态 / 众筹）的列表状态。
 *
 * 每个 tab 各存一份，而不是三个 tab 共用一个列表：
 * - 切 tab 立即显示该 tab 自己的数据（没访问过就进整页加载态）；
 * - 列表转场动画期间新旧两页各读各的 [FavoritesViewModel.tabs] 条目，
 *   旧页滑出时冻结在自己原来的列表上，不会「闪成」目标 tab 的内容。
 */
data class FavoritesTabState(
    /** 创作者 / 众筹 tab 的通用收藏条目 */
    val items: List<FavoriteItem> = emptyList(),
    /** 动态 tab 专用：完整的动态数据（供 PostCard 渲染） */
    val posts: List<RecommendedPost> = emptyList(),
    /** 动态 tab 分页：下一页页码 */
    val nextPage: Int = 1,
    /** 动态 tab 分页：是否还有更多 */
    val hasMore: Boolean = false,
    /** 首屏加载失败原因；非空时该 tab 显示整页错误态 */
    val error: String? = null,
)

/**
 * 收藏页状态：三个 tab（0=创作者 / 1=动态 / 2=众筹）各自一份 [FavoritesTabState]。
 *
 * 切 tab 时不互相覆盖数据，页面按「转场中的那个 tab 下标」取自己那一份渲染，
 * 这样切换动画期间退场的旧页不会中途变成新页的内容。
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    private val _kind = MutableStateFlow(0)
    val kind = _kind.asStateFlow()

    /** 各 tab 的列表状态，key 为 tab 下标；从未访问过的 tab 没有条目（= 整页加载态） */
    private val _tabs = MutableStateFlow<Map<Int, FavoritesTabState>>(emptyMap())
    val tabs = _tabs.asStateFlow()

    /** 动态 tab 分页加载中（只有动态 tab 会翻页） */
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    // 动态点赞/收藏的全局共享互动状态；收藏页拉到的动态天然是已收藏的，加载成功后批量预置
    val interactions = PostInteractionController.get(application)

    init { load() }

    /** 切换 tab：kind 变化时重新加载该 tab 第一页 */
    fun select(kind: Int) { if (_kind.value != kind) { _kind.value = kind; load() } }

    /**
     * 加载某个 tab 的第一页（默认当前 tab）。
     *
     * 已经有数据时先亮出旧列表再静默刷新，不进整页加载态；
     * 从未访问过的 tab 会立刻写入一条空状态，让页面切到加载态。
     *
     * @param kind tab 下标（0=创作者 1=动态 2=众筹）
     */
    fun load(kind: Int = _kind.value) {
        val cached = _tabs.value[kind]
        viewModelScope.launch {
            // 调试日志：记录 tab 类型与是否命中缓存
            AppLog.d(TAG, "load: kind=$kind, page=1, cacheHit=${cached != null}")
            putTab(kind, currentTab(kind).copy(error = null))
            runCatching {
                withContext(Dispatchers.IO) {
                    when (kind) {
                        0 -> putTab(kind, currentTab(kind).copy(items = service.fetchMarkedCreators()))
                        // 动态 tab：拉完整 RecommendedPost，替换列表并记录是否还有更多
                        1 -> service.fetchMarkedPosts(1).let {
                            // 先播种点赞/收藏状态再上屏，保证卡片首帧图标正确
                            interactions.seedLiked(it.posts.filter { p -> p.has_like == 1 }.map { p -> p.id }.toSet())
                            interactions.seedMarked(it.posts.map { p -> p.id }.toSet())
                            putTab(kind, currentTab(kind).copy(posts = it.posts, nextPage = 2, hasMore = it.hasMore))
                        }
                        else -> putTab(kind, currentTab(kind).copy(items = service.fetchMarkedCrowdfundings()))
                    }
                }
            }.onSuccess {
                // 调试日志：返回条数与分页状态统计
                val tab = currentTab(kind)
                AppLog.d(
                    TAG,
                    "load ok: kind=$kind, items=${tab.items.size}, posts=${tab.posts.size}, hasMore=${tab.hasMore}"
                )
            }.onFailure {
                // 错误日志：用户模式下也能看到收藏加载失败
                AppLog.e(TAG, "load failed: kind=$kind", it)
                // 已经有旧列表时保留内容只留日志；一条都没有才给出整页错误态
                val tab = currentTab(kind)
                if (tab.items.isEmpty() && tab.posts.isEmpty()) {
                    putTab(kind, tab.copy(error = it.message ?: "收藏加载失败"))
                }
            }
        }
    }

    /** 动态 tab 加载下一页，追加到该 tab 现有列表尾部 */
    fun loadMore() {
        if (_isLoadingMore.value) return
        val kind = _kind.value
        val tab = currentTab(kind)
        if (!tab.hasMore) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            runCatching {
                withContext(Dispatchers.IO) { service.fetchMarkedPosts(tab.nextPage) }
            }.onSuccess {
                val next = tab.nextPage + 1
                putTab(kind, currentTab(kind).copy(posts = tab.posts + it.posts, nextPage = next, hasMore = it.hasMore))
                // 调试日志：翻页参数、本页条数与累计总数
                AppLog.d(TAG, "loadMore ok: page=$next, got=${it.posts.size}, total=${tab.posts.size + it.posts.size}, hasMore=${it.hasMore}")
            }.onFailure {
                // 错误日志：用户模式下也能看到加载更多失败
                AppLog.e(TAG, "loadMore failed: page=${tab.nextPage}", it)
                // 已经有列表时不清空，只在没有内容时给出错误态
                if (tab.posts.isEmpty()) {
                    putTab(kind, tab.copy(error = it.message ?: "加载更多失败"))
                }
            }
            _isLoadingMore.value = false
        }
    }

    // ===== 内部实现 =====

    /** 取某个 tab 的状态；从未访问过返回默认空状态 */
    private fun currentTab(kind: Int): FavoritesTabState = _tabs.value[kind] ?: FavoritesTabState()

    /** 写入某个 tab 的状态（StateFlow 按 key 覆盖，避免整表重建） */
    private fun putTab(kind: Int, state: FavoritesTabState) {
        _tabs.value = _tabs.value + (kind to state)
    }

    companion object {
        const val TAG = "Favorites"
    }
}