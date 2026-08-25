package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.data.content.AfadianAccount
import com.hrsthrt74.afdian.data.content.AfadianProfile
import com.hrsthrt74.afdian.data.content.ProfileCache
import com.hrsthrt74.afdian.data.content.SponsoringItem
import com.hrsthrt74.afdian.data.content.TodayIncome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    // 个人资料磁盘缓存：冷启动先回填，消除「我的」页用户名/头像闪烁
    private val profileCache = ProfileCache(application)
    private val _posts = MutableStateFlow<List<RecommendedPost>>(emptyList())
    val posts = _posts.asStateFlow()
    private val _account = MutableStateFlow<AfadianAccount?>(null)
    val account = _account.asStateFlow()
    private val _profile = MutableStateFlow<AfadianProfile?>(null)
    val profile = _profile.asStateFlow()
    private val _sponsoring = MutableStateFlow<List<SponsoringItem>>(emptyList())
    val sponsoring = _sponsoring.asStateFlow()
    // 创作者今日收入（null 表示未加载成功，主页对应板块整体隐藏）
    private val _todayIncome = MutableStateFlow<TodayIncome?>(null)
    val todayIncome = _todayIncome.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()
    private var nextPublishSN = ""
    // 动态点赞/收藏的全局共享互动状态（乐观更新 + 失败回滚；状态来自服务端 has_like / marked-post）
    val interactions = PostInteractionController.get(application)

    init {
        // 先异步回填上次的资料缓存，让「我的」页立即显示真实用户名/头像；
        // 随后的 refresh() 拿到网络结果后会覆盖（失败时保留缓存，断网也能显示）
        viewModelScope.launch { profileCache.load()?.let { _profile.value = it } }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                        val accountDeferred = async { service.fetchAccount() }
                        val profileDeferred = async { service.fetchProfile() }
                        val sponsoringDeferred = async { service.fetchSponsoring() }
                        val postsDeferred = async { service.fetchRecommendedPosts() }
                        // 并行拉取服务端「我收藏的动态」ID 集合，供卡片收藏图标在首帧就显示正确
                        val markedIdsDeferred = async { interactions.fetchServerMarkedIds() }
                        AccountResult(
                            accountDeferred.await(),
                            profileDeferred.await(),
                            sponsoringDeferred.await(),
                            postsDeferred.await(),
                            markedIdsDeferred.await()
                        )
                    }
                }
                _account.value = result.account
                _profile.value = result.profile
                // 刷新成功后把资料写回磁盘缓存，供下次冷启动回填（内部已容错，失败不影响主流程）
                profileCache.save(result.profile)
                _sponsoring.value = result.sponsoring
                // 先回填收藏/点赞状态再上屏，保证列表首帧图标就是正确的
                interactions.seedMarked(result.markedIds)
                interactions.seedLiked(result.postsPage.posts.filter { it.has_like == 1 }.map { it.id }.toSet())
                _posts.value = result.postsPage.posts
                nextPublishSN = result.postsPage.nextPublishSN
                _hasMore.value = result.postsPage.hasMore
                AppLog.d("Discover", "refresh: posts=${result.postsPage.posts.size}, nextSN=$nextPublishSN, hasMore=${result.postsPage.hasMore}")
            } catch (e: Exception) {
                // 错误日志：用户模式下也能看到首页刷新失败
                AppLog.e("Discover", "refresh failed", e)
                _error.value = e.message ?: "首页内容加载失败"
            } finally {
                _isLoading.value = false
            }
            // 创作者数据独立加载：失败只置空对应板块，不影响首页其他内容刷新
            viewModelScope.launch {
                try {
                    val income = withContext(Dispatchers.IO) { service.fetchTodayIncome() }
                    _todayIncome.value = income
                    AppLog.d("Discover", "今日收入: ¥${income.amount}, 订单数=${income.orderCount}")
                } catch (e: Exception) {
                    // 加载失败时保留旧值（首次则为 null，主页隐藏该板块）
                    AppLog.w("Discover", "今日收入加载失败", e)
                }
            }
        }
    }

    /** 登出时清理本地资料：内存态立即置空 + 异步清空磁盘缓存，避免换账号后串显旧名字 */
    fun onLoggedOut() {
        _profile.value = null
        viewModelScope.launch { profileCache.clear() }
    }

    fun loadMore() {
        AppLog.d("Discover", "loadMore called: isLoadingMore=${_isLoadingMore.value}, hasMore=${_hasMore.value}, nextSN=$nextPublishSN")
        if (_isLoadingMore.value || !_hasMore.value || nextPublishSN.isBlank()) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = withContext(Dispatchers.IO) { service.fetchRecommendedPosts(nextPublishSN) }
                AppLog.d("Discover", "loadMore result: newPosts=${page.posts.size}, nextSN=${page.nextPublishSN}, hasMore=${page.hasMore}")
                // 翻页数据同样要先播种点赞状态再追加
                interactions.seedLiked(page.posts.filter { it.has_like == 1 }.map { p -> p.id }.toSet())
                _posts.value = _posts.value + page.posts
                nextPublishSN = page.nextPublishSN
                _hasMore.value = page.hasMore
            } catch (e: Exception) {
                AppLog.e("Discover", "loadMore failed", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}

private data class AccountResult(
    val account: AfadianAccount,
    val profile: AfadianProfile,
    val sponsoring: List<SponsoringItem>,
    val postsPage: com.hrsthrt74.afdian.data.content.RecommendedPostsPage,
    // 服务端「我收藏的动态」ID 集合，用于收藏图标首帧正确显示
    val markedIds: Set<String>,
)
