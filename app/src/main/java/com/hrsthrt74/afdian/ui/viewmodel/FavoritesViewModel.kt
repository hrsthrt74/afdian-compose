package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.FavoriteItem
import com.hrsthrt74.afdian.data.content.RecommendedPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    private val _kind = MutableStateFlow(0)
    val kind = _kind.asStateFlow()
    // 创作者/众筹 tab 的通用收藏条目
    private val _items = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val items = _items.asStateFlow()
    // 动态 tab 专用：完整的动态数据（供 PostCard 渲染）
    private val _posts = MutableStateFlow<List<RecommendedPost>>(emptyList())
    val posts = _posts.asStateFlow()
    // 动态 tab 分页游标与状态
    private var page = 1
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    // 动态点赞/收藏的共享交互状态；收藏页拉到的动态天然是已收藏的，加载成功后批量预置
    val interactions = PostInteractionController(service, viewModelScope)

    init { load() }

    /** 切换 tab：kind 变化时重置状态并重新加载第一页 */
    fun select(kind: Int) { if (_kind.value != kind) { _kind.value = kind; load() } }

    /** 加载当前 tab 的第一页 */
    fun load() {
        viewModelScope.launch {
            _loading.value = true; _error.value = null; page = 1
            runCatching {
                withContext(Dispatchers.IO) {
                    when (_kind.value) {
                        0 -> { _items.value = service.fetchMarkedCreators() }
                        // 动态 tab：拉完整 RecommendedPost，替换列表并记录是否还有更多
                        1 -> service.fetchMarkedPosts(page).let {
                            // 先播种点赞/收藏状态再上屏，保证卡片首帧图标正确
                            interactions.seedLiked(it.posts.filter { p -> p.has_like == 1 }.map { p -> p.id }.toSet())
                            interactions.seedMarked(it.posts.map { p -> p.id }.toSet())
                            _posts.value = it.posts; _hasMore.value = it.hasMore
                        }
                        else -> { _items.value = service.fetchMarkedCrowdfundings() }
                    }
                }
            }.onFailure { _error.value = it.message ?: "收藏加载失败" }
            _loading.value = false
        }
    }

    /** 动态 tab 加载下一页，追加到现有列表尾部 */
    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            runCatching {
                withContext(Dispatchers.IO) { service.fetchMarkedPosts(page + 1) }
            }.onSuccess {
                page += 1
                _posts.value = _posts.value + it.posts
                _hasMore.value = it.hasMore
            }.onFailure { _error.value = it.message ?: "加载更多失败" }
            _isLoadingMore.value = false
        }
    }
}
