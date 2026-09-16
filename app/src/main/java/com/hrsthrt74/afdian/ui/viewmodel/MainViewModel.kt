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
import com.hrsthrt74.afdian.data.content.FavoriteItem
import com.hrsthrt74.afdian.data.content.HomeCache
import com.hrsthrt74.afdian.data.content.HomeCacheSnapshot
import com.hrsthrt74.afdian.data.content.HomeCacheCard
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import com.hrsthrt74.afdian.data.stats.CreatorStatsRepository
import com.hrsthrt74.afdian.data.stats.CardValue
import com.hrsthrt74.afdian.data.stats.DataCardModel
import com.hrsthrt74.afdian.data.stats.displayText
import com.hrsthrt74.afdian.data.stats.removeLegacyUnitSuffix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    private val statsRepository = CreatorStatsRepository(AuthTokenStore(application))
    // 个人资料磁盘缓存：冷启动先回填，消除「我的」页用户名/头像闪烁
    private val profileCache = ProfileCache(application)
    // 首页首屏快照缓存：只缓存可展示数据，不缓存加载状态和分页游标。
    private val homeCache = HomeCache(application)
    private val homeCacheSaveMutex = Mutex()
    private val _posts = MutableStateFlow<List<RecommendedPost>>(emptyList())
    val posts = _posts.asStateFlow()
    private val _account = MutableStateFlow<AfadianAccount?>(null)
    val account = _account.asStateFlow()
    private val _profile = MutableStateFlow<AfadianProfile?>(null)
    val profile = _profile.asStateFlow()
    private val _sponsoring = MutableStateFlow<List<SponsoringItem>>(emptyList())
    val sponsoring = _sponsoring.asStateFlow()
    // 首页和管理页共用的类型化创作者卡片；ID 使用稳定的 CardKey.storageValue。
    private val _creatorCards = MutableStateFlow<Map<String, DataCardModel>>(emptyMap())
    val creatorCards = _creatorCards.asStateFlow()
    // 创作者图表卡数据；key 使用 ChartType.storageValue。写入首页快照缓存，
    // 冷启动先用旧图秒显，失败时保留缓存（首次失败才显示加载占位）。
    private val _creatorCharts = MutableStateFlow<Map<String, ChartDataItem>>(emptyMap())
    val creatorCharts = _creatorCharts.asStateFlow()
    private val _markedCreators = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val markedCreators = _markedCreators.asStateFlow()
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
        viewModelScope.launch {
            restoreHomeCache()
            // 先回填缓存，再启动网络刷新，避免慢速读盘把新鲜网络结果覆盖掉。
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            // 创作者数据与主页内容并行加载，但共享同一个 isLoading 窗口：
            // 修复「刷新图标先停、创作者卡片晚一拍才更新」的错位问题
            val creatorJob = launch { refreshCreatorData() }
            try {
                val result = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                        val accountDeferred = async { service.fetchAccount() }
                        val profileDeferred = async { service.fetchProfile() }
                        val sponsoringDeferred = async { service.fetchSponsoring() }
                        val postsDeferred = async { service.fetchRecommendedPosts() }
                        // 并行拉取服务端「我收藏的动态」ID 集合，供卡片收藏图标在首帧就显示正确
                        val markedIdsDeferred = async { interactions.fetchServerMarkedIds() }
                        val markedCreatorsDeferred = async { service.fetchMarkedCreators() }
                        AccountResult(
                            accountDeferred.await(),
                            profileDeferred.await(),
                            sponsoringDeferred.await(),
                            postsDeferred.await(),
                            markedIdsDeferred.await(),
                            markedCreatorsDeferred.await()
                        )
                    }
                }
                _account.value = result.account
                _profile.value = result.profile
                // 刷新成功后把资料写回磁盘缓存，供下次冷启动回填（内部已容错，失败不影响主流程）
                profileCache.save(result.profile)
                _sponsoring.value = result.sponsoring
                _markedCreators.value = result.markedCreators
                // 先回填收藏/点赞状态再上屏，保证列表首帧图标就是正确的
                interactions.seedMarked(result.markedIds)
                interactions.seedLiked(result.postsPage.posts.filter { it.has_like == 1 }.map { it.id }.toSet())
                _posts.value = result.postsPage.posts
                nextPublishSN = result.postsPage.nextPublishSN
                _hasMore.value = result.postsPage.hasMore
                saveHomeCache()
                AppLog.d("Discover", "refresh: posts=${result.postsPage.posts.size}, nextSN=$nextPublishSN, hasMore=${result.postsPage.hasMore}")
            } catch (e: Exception) {
                // 错误日志：用户模式下也能看到首页刷新失败
                AppLog.e("Discover", "refresh failed", e)
                _error.value = e.message ?: "首页内容加载失败"
            } finally {
                // 等创作者数据也结束后再关闭加载态，保证动画停止时首页所有模块都已更新
                creatorJob.join()
                _isLoading.value = false
            }
        }
    }

    /** 创作者数据独立加载：失败只影响创作者卡片/图表，不影响首页其他内容刷新 */
    private suspend fun refreshCreatorData() {
        try {
            val overview = withContext(Dispatchers.IO) {
                statsRepository.loadOverview()
            }
            _creatorCards.value = overview.cards
            // 图表按「本次成功返回的图覆盖、本次缺键的图保留旧值」合并：
            // 单个数据源临时失败时不会把已拿到过的图抹掉（否则坏数据还会被写进缓存）。
            // 与卡片的容错口径一致：加载失败保留旧值，而不是退回加载占位。
            _creatorCharts.value = _creatorCharts.value + overview.charts
            saveHomeCache()
            AppLog.d("Home", "创作者数据加载成功，卡片=${overview.cards.size}，图表=${overview.charts.size}")
        } catch (e: Exception) {
            // 加载失败时保留旧值；首次失败时卡片集合为空，主页隐藏该板块。
            AppLog.w("Home", "创作者数据加载失败", e)
        }
    }

    /** 登出时清理本地资料：内存态立即置空 + 异步清空磁盘缓存，避免换账号后串显旧名字 */
    fun onLoggedOut() {
        _profile.value = null
        viewModelScope.launch {
            profileCache.clear()
            homeCache.clear()
            _sponsoring.value = emptyList()
            _markedCreators.value = emptyList()
            _creatorCards.value = emptyMap()
            _creatorCharts.value = emptyMap()
        }
    }

    /** 将当前首页数据合并写入快照；任一独立模块刷新成功都能更新自己的缓存内容。 */
    private fun saveHomeCache() {
        val ownerUserId = _profile.value?.user?.user_id ?: return
        viewModelScope.launch {
            homeCacheSaveMutex.withLock {
                homeCache.save(
                    HomeCacheSnapshot(
                        ownerUserId = ownerUserId,
                        sponsoring = _sponsoring.value,
                        markedCreators = _markedCreators.value,
                        creatorCards = _creatorCards.value.values.map {
                            HomeCacheCard(it.id, it.title, it.value.displayText(), it.subtitle)
                        },
                        // 图表数据整体可序列化，直接落盘；刷新成功后才能覆盖旧图
                        creatorCharts = _creatorCharts.value,
                    )
                )
            }
        }
    }

    /** 只在网络刷新开始前读取一次缓存，避免缓存回填与网络结果发生竞态。 */
    private suspend fun restoreHomeCache() {
        val cachedProfile = profileCache.load()
        cachedProfile?.let { _profile.value = it }
        homeCache.load(cachedProfile?.user?.user_id)?.let { snapshot ->
            _sponsoring.value = snapshot.sponsoring
            _markedCreators.value = snapshot.markedCreators
            _creatorCards.value = snapshot.creatorCards.associate {
                // 旧版本快照把单位拼进了 value（例如「12天」），去掉后缀避免和指标给的单位重复显示
                it.id to DataCardModel(
                    it.id,
                    it.title,
                    CardValue.Text(it.value.removeLegacyUnitSuffix(it.id)),
                    it.subtitle,
                )
            }
            // 图表卡同样是「有则秒显」：先用缓存画出上一轮的图，网络结果回来后再覆盖
            _creatorCharts.value = snapshot.creatorCharts
            AppLog.d("Home", "已用缓存回填首页首屏（图表=${snapshot.creatorCharts.size}）")
        }
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
    val markedCreators: List<FavoriteItem>,
)
