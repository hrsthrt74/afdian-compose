package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.ui.components.AppBlurRadius
import com.hrsthrt74.afdian.ui.components.blurBarColors
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.AppNavTransition
import com.hrsthrt74.afdian.ui.navigation.CreatorDetailTransition
import com.hrsthrt74.afdian.ui.navigation.CreatorExpandOrigin
import com.hrsthrt74.afdian.ui.navigation.CreatorExpandTransition
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.util.rememberMainPagerState
import com.hrsthrt74.afdian.ui.viewmodel.FavoritesViewModel
import com.hrsthrt74.afdian.ui.viewmodel.HistoryViewModel
import com.hrsthrt74.afdian.data.history.HistoryTarget
import com.hrsthrt74.afdian.data.history.toAfadianUser
import com.hrsthrt74.afdian.data.history.toRecommendedPost
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import com.hrsthrt74.afdian.ui.viewmodel.MessagesViewModel
import com.hrsthrt74.afdian.ui.viewmodel.MonthlySummaryViewModel
import com.hrsthrt74.afdian.ui.viewmodel.ReceivedSponsorsViewModel
import com.hrsthrt74.afdian.ui.viewmodel.SponsorManageViewModel
import com.hrsthrt74.afdian.ui.viewmodel.ThankSponsorsViewModel
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 底部导航 Tab 定义：标题 + 图标。
 */
private data class MainTab(val title: String, val icon: ImageVector)

private val mainTabs = listOf(
    MainTab("首页", MiuixIcons.Home),
    MainTab("发现", MiuixIcons.Search),
    MainTab("消息", MiuixIcons.Community),
    MainTab("我的", MiuixIcons.Contacts),
)

/**
 * 去重 push：miuix-nav 要求 back stack 内 contentKey 互不相等，
 * 相同路由被 push 两次会直接抛 "Duplicate contentKey" 异常闪退。
 * 全栈已有相等 route 时静默忽略，顺带挡掉过渡动画期间的快速连点。
 */
private fun NavBackStack.pushUnique(route: Route) {
    if (none { it == route }) add(route)
}

/**
 * 在当前导航 entry 内消费系统预测性返回事件。
 *
 * miuix-nav 会为每个 entry 创建父 dispatcher 的子 dispatcher，entry 内注册的 handler
 * 优先于 NavDisplay 自身的 handler。因此关闭设置后消费手势进度，避免 NavDisplay 驱动
 * 预测性动画；提交时仍执行原有 pop，让系统返回键和返回手势保持可用。
 */
@Composable
private fun PredictiveBackGuard(enabled: Boolean, onCommit: () -> Unit) {
    PredictiveBackHandler(
        enabled = enabled,
        onProgress = {},
        onCommit = onCommit,
        onCancel = {},
    )
}

/**
 * 主界面壳层：全局 [NavDisplay] 管理创作者主页等覆盖路由，
 * 根路由 [Route.MainTabs] 承载底部 [NavigationBar] + [HorizontalPager] 横滑切换。
 *
 * @param onReady 首帧布局完全就绪后回调一次；上层用它撤掉盖在主页上的过渡 UI
 *                （如登录页「登录中…」），避免切页瞬间露出空白背景闪一下。
 */
@Composable
fun MainScreen(onReady: () -> Unit = {}, onLogout: () -> Unit, viewModel: MainViewModel = viewModel()) {
    val messagesViewModel: MessagesViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
    val backStack = rememberNavBackStack<Route>(Route.MainTabs)

    // 高级过渡动画开关：订阅设置后写入全局状态，由 CreatorDetailTransition 运行时切换
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val contentService = remember {
        AfadianContentService(AuthTokenStore(context.applicationContext as Application))
    }

    // 拦截爱发电新旧两种公开创作者主页链接；其他 URL 保持原行为交给系统浏览器。
    val handleContentUrl: (String) -> Unit = urlHandler@{ url ->
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val host = uri?.host?.lowercase()
        val segments = uri?.pathSegments.orEmpty()

        // 动态链接：https://afdian.com/p/{post_id}，使用已有的按 ID 详情路由。
        val postId = segments.getOrNull(1)?.takeIf {
            segments.size == 2 && segments[0] == "p" && it.isNotBlank()
        }
        if (host in setOf("ifdian.net", "afdian.com") && postId != null) {
            backStack.pushUnique(Route.PostDetailById(postId))
            return@urlHandler
        }

        val slug = when {
            // 新格式：https://afdian.com/a/username
            segments.size == 2 && segments[0] == "a" -> segments[1].takeIf { it.isNotBlank() }
            // 旧格式：https://afdian.com/@username
            segments.size == 1 && segments[0].startsWith("@") ->
                segments[0].removePrefix("@").takeIf { it.isNotBlank() }
            else -> null
        }
        if (host !in setOf("ifdian.net", "afdian.com") || slug == null) {
            uriHandler.openUri(url)
        } else {
            coroutineScope.launch {
                AppLog.d("MainScreen", "拦截创作者链接并解析 slug=$slug")
                runCatching {
                    withContext(Dispatchers.IO) { contentService.fetchProfileBySlug(slug) }
                }.onSuccess { user ->
                    if (user.user_id.isBlank()) {
                        AppLog.w("MainScreen", "创作者链接解析成功但缺少 user_id，slug=$slug")
                    } else {
                        backStack.pushUnique(Route.CreatorDetail(user))
                    }
                }.onFailure { error ->
                    AppLog.e("MainScreen", "创作者链接解析失败，slug=$slug", error)
                }
            }
        }
    }
    val settingsStore = remember { SettingsStore(context) }
    val advancedTransition by settingsStore.advancedTransition.collectAsState(initial = true)
    val gestureBack by settingsStore.gestureBack.collectAsState(initial = true)
    val predictiveBack by settingsStore.predictiveBack.collectAsState(initial = true)
    CreatorExpandOrigin.enabled = advancedTransition
    CreatorExpandOrigin.gestureBack = gestureBack
    // 根 entry 没有上一级页面时不注册拦截器，避免关闭开关后系统返回无法退出应用。
    val blockPredictiveBack = !predictiveBack && backStack.size > 1

    // 读取设备屏幕圆角，供「主页展开」过渡在全屏停稳时对齐（直角屏为 0.dp）
    CreatorExpandOrigin.screenCorner = rememberNavSystemCornerRadius()

    // 包装登出回调：先把本地资料清掉（内存态 + 磁盘缓存），再执行真正的登出，
    // 避免 Activity 级 MainViewModel 的旧资料在换账号登录后短暂显示上一个账号的名字
    val handleLogout: () -> Unit = {
        viewModel.onLoggedOut()
        onLogout()
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // 全局默认过渡：AppNavTransition 在视觉上等同 MiuixDefault，
        // 但滑动关闭方向随「手势返回」开关动态声明（二级路由统一继承）
        transition = AppNavTransition,
        // 顶层 entry 过渡期间以设备屏幕圆角裁剪前缘（官方推荐做法）；
        // 平台报告圆角为 0 的直角屏上等于不裁剪，无需设置项控制
        effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
    ) {
        entry<Route.MainTabs> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            MainTabsContent(
                onReady = onReady,
                viewModel = viewModel,
                messagesViewModel = messagesViewModel,
                historyViewModel = historyViewModel,
                onOpenCreator = { user -> backStack.pushUnique(Route.CreatorDetail(user)) },
                // 动态卡片点击 → 动态详情页（携带列表页已有数据秒开）
                onOpenPost = { post -> backStack.pushUnique(Route.PostDetail(post)) },
                onOpenConversation = { userId, userName, avatarUrl -> backStack.pushUnique(Route.MessageConversation(userId, userName, avatarUrl)) },
                // 「我的」的二级路由（收藏/我发电的/订单/管理/设置）统一走外层栈全屏覆盖，
                // 不再在 Pager 页内嵌套 NavDisplay，避免过渡层平移溢出泄漏到相邻 tab
                onNavigate = { route -> backStack.pushUnique(route) },
            )
        }
        // 创作者主页：CreatorDetailTransition 内部按「高级过渡动画」开关
        // 在卡片展开与默认滑动之间动态切换；滑动返回方向由过渡动态声明，
        // 随「手势返回」开关即时生效（以下所有二级路由同理，不再逐个写死）
        entry<Route.CreatorDetail>(
            transition = CreatorDetailTransition,
        ) { route ->
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            CreatorDetailScreen(user = route.user, onBack = { backStack.removeLastOrNull() }, backStack = backStack, onOpenPost = { post -> backStack.pushUnique(Route.PostDetail(post)) })
        }
        // 动态详情：全文正文 HTML 渲染 + 分享/点赞/收藏
        entry<Route.PostDetail> { route ->
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            PostDetailScreen(
                post = route.post,
                onBack = { backStack.removeLastOrNull() },
                backStack = backStack,
                onOpenCreator = { user -> backStack.pushUnique(Route.CreatorDetail(user)) },
                onNavigateToPost = { post -> backStack.pushUnique(Route.PostDetail(post)) },
                onOpenUrl = handleContentUrl,
            )
        }
        // 按 id 打开动态详情：用只占 post_id 的占位 post 复用详情页，
        // PostDetailViewModel 后台按 id 拉取全文；加载中由 PostDetailScreen 显示 LoadingComponent
        entry<Route.PostDetailById> { route ->
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            PostDetailScreen(
                post = RecommendedPost(post_id = route.postId),
                onBack = { backStack.removeLastOrNull() },
                backStack = backStack,
                onOpenCreator = { user -> backStack.pushUnique(Route.CreatorDetail(user)) },
                onNavigateToPost = { post -> backStack.pushUnique(Route.PostDetail(post)) },
                onOpenUrl = handleContentUrl,
            )
        }
        entry<Route.MessageConversation> { route ->
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            // 从 MainViewModel 的资料缓存取自己的头像，替换聊天里「我」的占位头像
            val myProfile by viewModel.profile.collectAsState()
            MessageConversationScreen(
                userId = route.userId,
                userName = route.userName,
                avatarUrl = route.avatarUrl,
                myAvatarUrl = myProfile?.user?.avatar,
                viewModel = messagesViewModel,
                onBack = { backStack.removeLastOrNull() },
                backStack = backStack,
            )
        }

        // =====「我的」的二级屏幕：全部提升到外层栈，push 时盖住底部导航栏 =====

        entry<Route.Favorites> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            val vm: FavoritesViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
                    LocalContext.current.applicationContext as Application
                )
            )
            FavoritesScreen(vm, onOpenCreator = { user -> backStack.pushUnique(Route.CreatorDetail(user)) }, onOpenPost = { post -> backStack.pushUnique(Route.PostDetail(post)) }, backStack = backStack) {
                backStack.removeLastOrNull()
            }
        }
        entry<Route.Sponsoring> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            SponsoringScreen(viewModel, onOpenCreator = { user -> backStack.pushUnique(Route.CreatorDetail(user)) }, backStack = backStack) {
                backStack.removeLastOrNull()
            }
        }
        entry<Route.Orders> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            OrderScreen(onBack = { backStack.removeLastOrNull() }, backStack = backStack)
        }
        entry<Route.Management> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            ManagementScreen(onBack = { backStack.removeLastOrNull() }, backStack = backStack)
        }
        entry<Route.IncomeDetail> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            IncomeDetailScreen(onBack = { backStack.removeLastOrNull() }, backStack = backStack)
        }
        entry<Route.ReceivedSponsors> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            val app = LocalContext.current.applicationContext as Application
            val vm: ReceivedSponsorsViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app),
            )
            ReceivedSponsorsScreen(
                onBack = { backStack.removeLastOrNull() },
                backStack = backStack,
                providedViewModel = vm,
            )
        }
        entry<Route.SponsorManage> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            // 赞助者管理页（who-sponsored-me 列表）；入口在「我的」页创作者分组
            val app = LocalContext.current.applicationContext as Application
            val vm: SponsorManageViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app),
            )
            SponsorManageScreen(
                onBack = { backStack.removeLastOrNull() },
                backStack = backStack,
                providedViewModel = vm,
                onOpenCreator = { user -> backStack.pushUnique(Route.CreatorDetail(user)) },
            )
        }
        entry<Route.MonthlySummary> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            // 每月汇总（创作者后台 /api/my/income）；点某一行 → 该年月的感谢页
            val app = LocalContext.current.applicationContext as Application
            val vm: MonthlySummaryViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app),
            )
            MonthlySummaryScreen(
                onBack = { backStack.removeLastOrNull() },
                backStack = backStack,
                onOpenThankSponsors = { year, month -> backStack.pushUnique(Route.ThankSponsors(year, month)) },
                providedViewModel = vm,
            )
        }
        entry<Route.ThankSponsors> { route ->
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            // 顶部创作者信息直接用当前登录账号的资料（/api/my/profile）
            val myProfile by viewModel.profile.collectAsState()
            val userId = myProfile?.user?.user_id.orEmpty()
            val app = LocalContext.current.applicationContext as Application
            // 年月由「每月汇总」页带入；viewModelStore 按路由对象隔离，不同年月是不同 entry，不会串数据
            val vm: ThankSponsorsViewModel = viewModel(
                key = "thank-sponsors",
                factory = remember(userId, route.year, route.month) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            ThankSponsorsViewModel(
                                app,
                                userId = userId,
                                initialYear = route.year,
                                initialMonth = route.month,
                            ) as T
                    }
                }
            )
            ThankSponsorsScreen(
                viewModel = vm,
                creatorName = myProfile?.user?.name?.ifBlank { "爱发电用户" } ?: "爱发电用户",
                creatorAvatar = myProfile?.user?.avatar,
                creatorDoing = myProfile?.user?.creator?.doing,
                onBack = { backStack.removeLastOrNull() },
                backStack = backStack,
                onOpenCreator = { user -> backStack.pushUnique(Route.CreatorDetail(user)) }
            )
        }
        entry<Route.Affiliates> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            // 优惠码列表（创作者后台 creator/list-affiliate）；编辑 / 创建页后续再接
            AffiliateScreen(
                onBack = { backStack.removeLastOrNull() },
                backStack = backStack,
            )
        }
        entry<Route.Settings> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            SettingsScreen(onBack = { backStack.removeLastOrNull() }, backStack = backStack, onLogout = handleLogout)
        }
        entry<Route.History> {
            PredictiveBackGuard(blockPredictiveBack) { backStack.removeLastOrNull() }
            HistoryScreen(
                viewModel = historyViewModel,
                onBack = { backStack.removeLastOrNull() },
                backStack = backStack,
                onOpenCreator = { user -> backStack.pushUnique(Route.CreatorDetail(user)) },
                onOpenPost = { post -> backStack.pushUnique(Route.PostDetail(post)) },
                onOpenConversation = { userId, userName, avatarUrl ->
                    backStack.pushUnique(Route.MessageConversation(userId, userName, avatarUrl))
                },
            )
        }
    }
}

@Composable
private fun MainTabsContent(
    onReady: () -> Unit,
    viewModel: MainViewModel,
    messagesViewModel: MessagesViewModel,
    historyViewModel: HistoryViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenPost: (com.hrsthrt74.afdian.data.content.RecommendedPost) -> Unit,
    onOpenConversation: (String, String, String?) -> Unit,
    onNavigate: (Route) -> Unit,
) {
    val context = LocalContext.current
    // 启动页订阅 DataStore；加载完成（null -> 值）之前不创建 pager，
    // 否则 rememberPagerState 会用占位值定格在第 0 页。
    val settingsStore = remember { SettingsStore(context) }
    val startupPage by settingsStore.startupPage.collectAsState(initial = null)
    val initialPage = startupPage?.coerceIn(0, mainTabs.lastIndex) ?: return
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { mainTabs.size }
    )
    val mainPagerState = rememberMainPagerState(pagerState)

    // 横滑翻页后把当前页回写到导航栏高亮
    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val haptic = LocalHapticFeedback.current
    val hasUnreadMessages by messagesViewModel.hasUnreadMessages.collectAsState()
    val unreadCount by messagesViewModel.unreadCount.collectAsState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    var layoutReady by remember { mutableStateOf(false) }

    // Miuix Scaffold 的 PaddingValues 会在首轮测量后更新；等待两帧后再显示，
    // 避免用户看到顶栏/底栏边距从旧值跳到最终值的过程。
    // 两帧后同时回调 onReady：上层据此撤掉盖在主页上的过渡 UI（登录页转圈）。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        layoutReady = true
        onReady()
    }

    Scaffold(
        modifier = Modifier.alpha(if (layoutReady) 1f else 0f),
        bottomBar = {
            NavigationBar(
                modifier = if (backdrop != null) Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                    blurRadius = AppBlurRadius,
                    colors = blurBarColors(),
                    enabled = themeSettings.enableBlur,
                ) else Modifier,
                color = if (themeSettings.enableBlur) MiuixTheme.colorScheme.surface.copy(alpha = 0.1f)
                else MiuixTheme.colorScheme.surface,
            ) {
                mainTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = mainPagerState.selectedPage == index,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mainPagerState.animateToPage(index)
                        },
                        icon = tab.icon,
                        label = tab.title,
                        badge = if (index == 2 && hasUnreadMessages) {
                            { Badge( containerColor = MiuixTheme.colorScheme.primary ) { Text("$unreadCount") } }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    ) { _ ->
        HorizontalPager(
            state = pagerState,
            // 四个 tab 页全部常驻组合：切换时零组合成本，nav 点击的翻页动画才能
            // 流畅滑过中间页（按需加载时中间页要在 350ms 动画中即时组合，掉帧严重
            // 视觉上像瞬移）。代价是启动首帧一次性初始化四份模糊层，可接受。
            beyondViewportPageCount = mainTabs.lastIndex,
            modifier = Modifier
                .fillMaxSize()
                // Pager 延伸到导航栏后方，底栏才能捕获真实滚动内容；页面自身再预留遮挡空间。
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
        ) { page ->
            // 页面容器：全宽背景铺满，与各页面 Scaffold 背景同色
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                when (page) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        onOpenCreator = onOpenCreator,
                        onOpenManagement = { onNavigate(Route.Management) },
                        onOpenFavorites = { onNavigate(Route.Favorites) },
                        onOpenSponsoring = { onNavigate(Route.Sponsoring) },
                        historyViewModel = historyViewModel,
                        onOpenHistory = { onNavigate(Route.History) },
                        onOpenHistoryEntry = { entry ->
                            when (val target = entry.target) {
                                is HistoryTarget.CreatorDetailTarget -> onOpenCreator(target.toAfadianUser())
                                is HistoryTarget.PostDetailTarget -> onOpenPost(target.toRecommendedPost())
                                // 私信会话：复用现有的会话页跳转
                                is HistoryTarget.MessageConversationTarget ->
                                    onOpenConversation(target.userId, target.name, target.avatarUrl)
                            }
                        },
                    )
                    1 -> DiscoverScreen(
                        viewModel = viewModel,
                        onOpenCreator = onOpenCreator,
                        onOpenPost = onOpenPost,
                    )
                    2 -> MessagesScreen(messagesViewModel) { route ->
                        when (route) {
                            is Route.MessageConversation -> onOpenConversation(route.userId, route.userName, route.avatarUrl)
                            else -> {}
                        }
                    }
                    else -> ProfileScreen(viewModel, onOpenCreator, onNavigate)
                }
            }
        }
    }
}
