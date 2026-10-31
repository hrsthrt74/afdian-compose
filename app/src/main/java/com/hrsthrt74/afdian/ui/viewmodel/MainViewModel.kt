package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.auth.CurrentAccount
import com.hrsthrt74.afdian.data.auth.SessionEpoch
import com.hrsthrt74.afdian.data.auth.authSessionFingerprint
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.data.content.AfadianAccount
import com.hrsthrt74.afdian.data.content.AfadianProfile
import com.hrsthrt74.afdian.data.content.ProfileCache
import com.hrsthrt74.afdian.data.content.SponsoringItem
import com.hrsthrt74.afdian.data.content.FavoriteItem
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.HomeCache
import com.hrsthrt74.afdian.data.content.HomeCacheSnapshot
import com.hrsthrt74.afdian.data.content.HomeCacheCard
import com.hrsthrt74.afdian.data.content.millisUntilNextLocalDay
import com.hrsthrt74.afdian.data.history.HistoryStore
import com.hrsthrt74.afdian.data.search.SearchHistoryStore
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import com.hrsthrt74.afdian.data.stats.CreatorStatsRepository
import com.hrsthrt74.afdian.data.stats.CardValue
import com.hrsthrt74.afdian.data.stats.DataCardModel
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import com.hrsthrt74.afdian.data.stats.IncomeStatRepository
import com.hrsthrt74.afdian.data.stats.IncomeStatType
import com.hrsthrt74.afdian.data.stats.displayText
import com.hrsthrt74.afdian.data.stats.mergeCardFrame
import com.hrsthrt74.afdian.data.stats.removeLegacyUnitSuffix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    private val searchHistoryStore = SearchHistoryStore(application)
    // 浏览历史仓库：这里只用来在绑定账号时迁移升级前的旧数据，展示逻辑在 HistoryViewModel
    private val historyStore = HistoryStore(application)
    private val settingsStore = SettingsStore(application)
    private val statsRepository = CreatorStatsRepository(AuthTokenStore(application))
    // 首页「近5天收入」模块的数据源：与收入明细页同一个 /api/my/stat 接口（按日维度）
    private val incomeStatRepository = IncomeStatRepository(AuthTokenStore(application))
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
    // dashboard 的未确认金额：结算期（每月 1 日 0:00–10:00 或服务端仍在结算）内它就是
    // 待结算的上月收入，供首页提现提醒卡切换「上月」口径；窗口外不参与任何展示。
    private val _unconfirmedAmount = MutableStateFlow<BigDecimal?>(null)
    val unconfirmedAmount = _unconfirmedAmount.asStateFlow()
    // 服务端是否报告「结算中」（dashboard.balance 是「结算中，请耐心等待」文案）。
    // 本地时钟可能被改、时区也可能不同，这个信号让结算期判定以服务器为准。
    // 刻意只存在内存里：写进首页缓存的话，非结算日冷启动首帧会用上一天的旧信号误判结算期
    // （结算日的冷启动由本地时钟兜住，金额靠缓存里的 unconfirmedAmount 提供）。
    private val _isBalanceSettling = MutableStateFlow(false)
    val isBalanceSettling = _isBalanceSettling.asStateFlow()
    // 创作者图表卡数据；key 使用 ChartType.storageValue。写入首页快照缓存，
    // 冷启动先用旧图秒显，失败时保留缓存（首次失败才显示加载占位）。
    private val _creatorCharts = MutableStateFlow<Map<String, ChartDataItem>>(emptyMap())
    val creatorCharts = _creatorCharts.asStateFlow()
    private val _markedCreators = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val markedCreators = _markedCreators.asStateFlow()
    // 首页「近5天收入」模块的数据：/api/my/stat?type=day 第 1 页的前 5 条（接口按日期倒序返回）。
    // 加载失败时保留旧值（与创作者数据同一容错口径），模块据此显示空态提示。
    private val _recentIncomeRecords = MutableStateFlow<List<IncomeStatRecord>>(emptyList())
    val recentIncomeRecords = _recentIncomeRecords.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()
    private val _searchResults = MutableStateFlow<List<AfadianUser>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading = _searchLoading.asStateFlow()
    private val _searchLoadingMore = MutableStateFlow(false)
    val searchLoadingMore = _searchLoadingMore.asStateFlow()
    private val _searchHasMore = MutableStateFlow(false)
    val searchHasMore = _searchHasMore.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()
    val searchHistory: StateFlow<List<String>> = searchHistoryStore.queries
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val searchHistoryEnabled: StateFlow<Boolean> = settingsStore.historyEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private var searchJob: Job? = null
    private var searchQuery = ""
    private var searchPage = 0
    private var nextPublishSN = ""

    init {
        // 跨天自动刷新：应用一直开着时，每到新的一天刷新一次主页。
        // 放在 ViewModel 而不是首页 Composable 里：ViewModel 活在会话作用域内，
        // 用户切到别的底栏页、甚至把应用退到后台，计时都不会被组合生命周期打断。
        startDailyRefreshTicker()
    }

    /**
     * 跨天自动刷新计时器：每到本地自然日 0 点触发一次 [refresh]，然后重新计时。
     *
     * 与具体日期无关，纯粹按「自然日翻篇」触发；每次循环都重新计算到下一个 0 点的时长，
     * 因此用户改系统时间、换时区、跨夏令时之后都能自动纠正，不会一直沿用旧节拍。
     *
     * 设备休眠时协程不会被执行，但 `delay` 是按时间点算的：进程重新拿到 CPU
     * （例如用户早上点亮屏幕回到应用）时会立刻补上这次刷新，而不是白等一整天。
     */
    private fun startDailyRefreshTicker() {
        viewModelScope.launch {
            while (true) {
                val waitMillis = millisUntilNextLocalDay(ZonedDateTime.now())
                AppLog.d("Home", "跨天自动刷新：${waitMillis / 1000} 秒后进入新的一天")
                delay(waitMillis)
                // 正好在一次手动刷新中途跨天时不并发发起第二次：这一天的数据已经是最新的，
                // 直接进入下一轮计时，避免两轮请求互相覆盖。
                if (_isLoading.value) {
                    AppLog.d("Home", "跨天自动刷新跳过：当前已有刷新在进行")
                    continue
                }
                AppLog.d("Home", "跨天自动刷新：新的一天开始，刷新主页")
                refresh()
                // refresh() 内部自己起协程，这里不等待；下一轮计时从现在开始即可
            }
        }
    }

    fun searchCreators(query: String) {
        searchJob?.cancel()
        val keyword = query.trim()
        searchQuery = keyword
        searchPage = 0
        _searchHasMore.value = false
        _searchLoadingMore.value = false
        if (keyword.isBlank()) {
            _searchResults.value = emptyList()
            _searchError.value = null
            _searchLoading.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _searchLoading.value = true
            _searchError.value = null
            try {
                // 防抖输入，避免用户连续输入时频繁请求并短暂显示错误/空结果。
                delay(400.milliseconds)
                val page = withContext(Dispatchers.IO) { service.searchCreators(keyword, page = 1) }
                _searchResults.value = page.creators
                searchPage = 1
                _searchHasMore.value = page.hasMore && page.creators.isNotEmpty()
                searchHistoryStore.record(keyword)
                AppLog.d("DiscoverSearch", "query=$keyword, results=${page.creators.size}")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLog.e("DiscoverSearch", "search failed", error)
                _searchError.value = error.message ?: "搜索失败"
            } finally {
                _searchLoading.value = false
            }

        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch { searchHistoryStore.remove(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryStore.clear() }
    }

    fun loadMoreSearchCreators(query: String) {
        val keyword = query.trim()
        if (
            keyword.isBlank() ||
            keyword != searchQuery ||
            searchPage == 0 ||
            !_searchHasMore.value ||
            _searchLoading.value ||
            _searchLoadingMore.value
        ) return

        searchJob?.cancel()
        val nextPage = searchPage + 1
        searchJob = viewModelScope.launch {
            _searchLoadingMore.value = true
            try {
                val page = withContext(Dispatchers.IO) {
                    service.searchCreators(keyword, page = nextPage)
                }
                _searchResults.value = (_searchResults.value + page.creators)
                    .distinctBy { it.user_id }
                searchPage = nextPage
                _searchHasMore.value = page.hasMore && page.creators.isNotEmpty()
                AppLog.d(
                    "DiscoverSearch",
                    "load more query=$keyword, page=$nextPage, results=${page.creators.size}",
                )
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLog.e("DiscoverSearch", "load more failed", error)
                _searchHasMore.value = false
            } finally {
                _searchLoadingMore.value = false
            }
        }
    }
    // 动态点赞/收藏的全局共享互动状态（乐观更新 + 失败回滚；状态来自服务端 has_like / marked-post）
    val interactions = PostInteractionController.get(application)

    /**
     * 本 ViewModel 所属的会话代次。
     *
     * 切换账号 / 登出会让代次递增；所有异步结果（首页刷新、创作者流式帧、翻页、缓存写入）
     * 在写回 StateFlow 之前都要比对，否则旧账号的慢请求会把数据写进新账号的界面，
     * 也就是 A→B→A 快速连切时看到的数据错乱。
     */
    private val sessionEpoch = SessionEpoch.current()

    /** 当前是否仍是这个 ViewModel 所属的会话。 */
    private fun sessionAlive(): Boolean = SessionEpoch.current() == sessionEpoch

    private val _homeRestoredFromCache = MutableStateFlow(false)

    /**
     * 本次会话的首页数据是否来自磁盘缓存（冷启动回填）。
     *
     * 首屏门控用它区分两种进入方式：
     * - 冷启动有缓存：缓存是上一轮的完整快照，可直接秒进，不必等网络；
     * - 切号 / 首次登录（无缓存）：必须等整轮刷新结束，否则流式中间帧会让
     *   「创作者数据」模块先露出再消失（账单类卡片还没到，模块判定不可显示）。
     */
    val homeRestoredFromCache: StateFlow<Boolean> = _homeRestoredFromCache.asStateFlow()

    private val _hasCompletedFirstLoad = MutableStateFlow(false)

    /**
     * 本次会话是否已完成至少一轮首页刷新（成功或失败都算）。
     *
     * 门控不能靠 UI 观察 `isLoading` 的 true→false 来推断「刷新结束」：
     * 网络快时这个摆动可能发生在同一帧内，Compose 只看到终值 false，
     * 中间态被跳过，遮罩就会一直等到超时。用 ViewModel 自己的完成标志最可靠。
     */
    val hasCompletedFirstLoad: StateFlow<Boolean> = _hasCompletedFirstLoad.asStateFlow()

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
            if (!sessionAlive()) return@launch
            _isLoading.value = true
            _error.value = null
            // 发起请求前记下会话指纹：切号后 token 会变，写缓存时据此丢弃旧会话的资料
            val requestSessionKey = withContext(Dispatchers.IO) {
                authSessionFingerprint(getApplication())
            }
            // 创作者数据与主页内容并行加载，但共享同一个 isLoading 窗口：
            // 修复「刷新图标先停、创作者卡片晚一拍才更新」的错位问题
            val creatorJob = launch { refreshCreatorData() }
            // 近5天收入模块：与创作者数据并发加载、共享同一个 isLoading 窗口，
            // 这样下拉刷新/顶栏刷新会把两张表一起更新，加载动画停止时数据已就位。
            val recentIncomeJob = launch { refreshRecentIncomeRecords() }
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
                // 会话已切换：这批数据属于上一个账号，绝不能写进新账号的界面
                if (!sessionAlive()) {
                    AppLog.d("Home", "会话已切换，丢弃本次首页刷新结果")
                    return@launch
                }
                _account.value = result.account
                _profile.value = result.profile
                // 资料到手就能确定账号身份了：浏览历史/搜索历史立刻切到这个账号的分区
                bindAccount(result.profile.user.user_id)
                // 刷新成功后把资料写回磁盘缓存，供下次冷启动回填（内部已容错，失败不影响主流程）
                profileCache.save(result.profile, requestSessionKey)
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
                // 等创作者数据、近5天收入都结束后再关闭加载态，保证动画停止时首页所有模块都已更新
                creatorJob.join()
                recentIncomeJob.join()
                _isLoading.value = false
                // 首轮刷新结束（无论成功失败）都通知门控：不能让切号遮罩干等超时
                if (sessionAlive()) _hasCompletedFirstLoad.value = true
            }
        }
    }

    /**
     * 近5天收入模块独立加载：拉 /api/my/stat 第 1 页（按日维度）取最近 5 条。
     *
     * 只取第 1 页即可满足「近 5 天」，不翻页；接口按日期倒序返回，前 5 条就是最近 5 天。
     * 失败时保留旧值并告警，与 [refreshCreatorData] 同一容错口径：
     * 首次失败时列表为空，模块显示「暂无统计数据」而不是整页报错。
     */
    private suspend fun refreshRecentIncomeRecords() {
        try {
            val page = withContext(Dispatchers.IO) {
                incomeStatRepository.fetch(IncomeStatType.Day, page = 1)
            }
            val records = page.list.take(RECENT_INCOME_RECORD_COUNT)
            // 会话已切换：旧账号的统计不能出现在新账号首页
            if (!sessionAlive()) return
            _recentIncomeRecords.value = records
            AppLog.d("Home", "近5天收入加载成功，天数=${records.size}")
        } catch (e: Exception) {
            // 保留旧值：网络抖动时不至于让首页表格整体清空
            AppLog.w("Home", "近5天收入加载失败", e)
        }
    }

    /** 创作者数据独立加载：失败只影响创作者卡片/图表，不影响首页其他内容刷新 */
    private suspend fun refreshCreatorData() {
        try {
            // 流式加载：dashboard 先到先推一帧，账单/按日统计/月度账单谁先完成谁推一帧，
            // 订单多的账号不用陪账单翻页一起等最慢的接口。
            val overview = withContext(Dispatchers.IO) {
                statsRepository.loadOverviewStreaming { partial ->
                    // 流式帧可能在切号之后才到：旧账号的卡片/图表不能再写进新界面
                    if (!sessionAlive()) return@loadOverviewStreaming
                    // 中间帧只覆盖「本帧真正拿到值」的卡片：
                    // 流式加载的早期帧里，账单/按日统计这些还没回来的数据源是 Unavailable，
                    // 整份替换会让冷启动回填的缓存数字（今日收入等）先被抹成 “--”，
                    // 真值到达后滚轮节点重建、动画还得从 0 重播一遍。
                    _creatorCards.value = mergeCardFrame(_creatorCards.value, partial.cards)
                    // 未确认金额随帧写入：dashboard 帧之后就有值，后续帧重复同值无副作用。
                    // dashboard 失败时整个流式加载抛异常，回调不执行，旧值得以保留（与卡片容错口径一致）。
                    _unconfirmedAmount.value = partial.unconfirmedAmount
                    // 结算期信号同理：dashboard 帧写入，后续帧同值；刷新失败时保留上一次的判定
                    _isBalanceSettling.value = partial.isBalanceSettling
                    // 图表按「本次成功返回的图覆盖、本次缺键的图保留旧值」合并：
                    // 单个数据源临时失败时不会把已拿到过的图抹掉（否则坏数据还会被写进缓存）。
                    // 与卡片的容错口径一致：加载失败保留旧值，而不是退回加载占位。
                    // 每帧回调在仓库内部互斥锁内串行触发，这里的读改写不会与其他帧竞态。
                    _creatorCharts.value = _creatorCharts.value + partial.charts
                }
            }
            // 会话已切换：最终快照同样不能落到新账号上
            if (!sessionAlive()) return
            // 最终快照兜底：中间帧为避免闪烁保留了旧值，这里再落一次权威结果，
            // 让真正失败的数据源回到 “--”。成功路径上它与最后一帧内容相等，
            // 会被 StateFlow 的相等合流直接吞掉，不产生任何额外重组。
            _creatorCards.value = overview.cards
            // 磁盘缓存只在全部数据源结束后落盘一次，避免中间帧反复写文件
            saveHomeCache()
            AppLog.d("Home", "创作者数据加载成功，卡片=${overview.cards.size}，图表=${overview.charts.size}")
        } catch (e: Exception) {
            // 加载失败时保留旧值；首次失败时卡片集合为空，主页隐藏该板块。
            AppLog.w("Home", "创作者数据加载失败", e)
        }
    }

    /**
     * 登出时清空本 ViewModel 持有的全部账号相关内存态。
     *
     * 磁盘缓存（资料 / 首页快照）不在这里清：本方法调用后 ViewModel 会被会话作用域销毁，
     * viewModelScope 随之取消，这里的异步清理可能来不及落盘，统一交给 AuthSession 处理。
     */
    fun onLoggedOut() {
        // 账号身份立即清空：历史/搜索历史马上切回空分区，不用等磁盘清理跑完
        CurrentAccount.update(null)
        // 资料与账号概要：首页顶部的关注/粉丝等数字都来自 _account，漏清会显示上一个账号
        _profile.value = null
        _account.value = null
        // 动态流与分页游标：不清会让下一个账号一进来就看到上一个账号的推荐流
        _posts.value = emptyList()
        nextPublishSN = ""
        _hasMore.value = true
        // 创作者数据与图表
        _sponsoring.value = emptyList()
        _markedCreators.value = emptyList()
        _creatorCards.value = emptyMap()
        _creatorCharts.value = emptyMap()
        _unconfirmedAmount.value = null
        // 结算期信号属于上一个账号的 dashboard，切号后必须清掉，
        // 否则新账号在 1 日会继承旧账号的「结算中」判定
        _isBalanceSettling.value = false
        _recentIncomeRecords.value = emptyList()
        // 搜索态：结果和错误提示都属于上一个账号的上下文
        searchJob?.cancel()
        searchQuery = ""
        searchPage = 0
        _searchResults.value = emptyList()
        _searchHasMore.value = false
        _searchError.value = null
        // 通用错误提示
        _error.value = null
        // 点赞/收藏是进程级单例，生命周期比会话长，必须显式清空
        interactions.reset()
        AppLog.d("Home", "登出：首页相关内存态已清空")
    }

    /** 将当前首页数据合并写入快照；任一独立模块刷新成功都能更新自己的缓存内容。 */
    private fun saveHomeCache() {
        // 旧会话的写盘请求直接丢弃：即使 HomeCache 有 ownerUserId 兜底，
        // 让上一代的数据覆盖磁盘也是没必要的风险
        if (!sessionAlive()) return
        val ownerUserId = _profile.value?.user?.user_id ?: return
        viewModelScope.launch {
            homeCacheSaveMutex.withLock {
                if (!sessionAlive()) return@withLock
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
                        // 未确认金额以纯文本落盘（BigDecimal 不参与序列化），供缓存帧的提现提醒使用
                        unconfirmedAmount = _unconfirmedAmount.value?.toPlainString(),
                        // 结算期信号（isBalanceSettling）刻意不落盘，原因见该字段的声明处注释
                    )
                )
            }
        }
    }

    /** 只在网络刷新开始前读取一次缓存，避免缓存回填与网络结果发生竞态。 */
    private suspend fun restoreHomeCache() {
        val cachedProfile = profileCache.load()
        // 资料缓存无效（没有缓存或属于其他账号）时不回填首页快照：
        // 账号不明的情况下展示旧数据，比让页面先空着再被网络结果填满更糟。
        if (cachedProfile == null) {
            AppLog.d("Home", "资料缓存无效，跳过首页快照回填")
            return
        }
        _profile.value = cachedProfile
        // 缓存里的资料属于当前 token，可以先拿它当账号身份，不必等网络把首屏等白
        bindAccount(cachedProfile.user.user_id)
        homeCache.load(cachedProfile.user.user_id)?.let { snapshot ->
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
            // 未确认金额文本回填为数值；旧快照无该字段或格式异常时保持 null，走实时帧兜底
            _unconfirmedAmount.value = snapshot.unconfirmedAmount?.toBigDecimalOrNull()
            // 结算期信号不回填（缓存里没有）：冷启动首帧的结算期判定交给本地时钟，
            // 服务端信号等 dashboard 回来后再纠正，避免拿上一天的旧信号误判
            // 标记「首屏已由缓存填好」：MainActivity 的门控据此放行，不必等网络
            _homeRestoredFromCache.value = true
            AppLog.d("Home", "已用缓存回填首页首屏（图表=${snapshot.creatorCharts.size}）")
        }
    }

    /**
     * 绑定当前账号身份。
     *
     * 浏览历史与搜索历史按 user_id 分区存储，必须先确定身份才谈得上读写。
     * 顺带执行一次升级迁移：旧版本只有一个无归属的键，把它接到当前账号名下，
     * 避免老用户升级后历史「凭空消失」。迁移是幂等的，没有旧数据时只是一次缓存读取。
     */
    private suspend fun bindAccount(userId: String) {
        if (userId.isBlank()) return
        // 旧会话不得改写当前账号身份：切号后它会把新账号的 CurrentAccount 覆盖回旧值
        if (!sessionAlive()) return
        CurrentAccount.update(userId)
        // 身份绑定本身不会失败；迁移只是「有旧数据则搬一次」的收尾动作，
        // 磁盘异常绝不能把已经拿到的首页数据一起丢掉（否则网络成功却整页报错）。
        runCatching {
            historyStore.migrateLegacyEntries(userId)
            searchHistoryStore.migrateLegacy(userId)
            // 首页布局、管理页布局、置顶会话等账号级设置同样需要从旧版全局键迁过来（幂等）
            settingsStore.migrateLegacyAccountSettings(userId)
        }.onFailure { AppLog.w("Home", "旧版账号级数据迁移失败，不影响本次刷新", it) }
    }

    fun loadMore() {
        AppLog.d("Discover", "loadMore called: isLoadingMore=${_isLoadingMore.value}, hasMore=${_hasMore.value}, nextSN=$nextPublishSN")
        if (_isLoadingMore.value || !_hasMore.value || nextPublishSN.isBlank()) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = withContext(Dispatchers.IO) { service.fetchRecommendedPosts(nextPublishSN) }
                AppLog.d("Discover", "loadMore result: newPosts=${page.posts.size}, nextSN=${page.nextPublishSN}, hasMore=${page.hasMore}")
                // 会话已切换：旧账号的下一页不能追加到新账号的列表
                if (!sessionAlive()) return@launch
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

    private companion object {
        /** 首页「近5天收入」模块展示的天数；接口按日期倒序返回，第 1 页取前 N 条即为最近 N 天。 */
        const val RECENT_INCOME_RECORD_COUNT = 5
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
