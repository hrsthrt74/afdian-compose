package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.ui.components.account.AccountSwitchPopup
import com.hrsthrt74.afdian.ui.components.account.AccountSwitcherBadge
import com.hrsthrt74.afdian.ui.components.account.LocalAccountSwitcher
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.RefreshButton
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.data.toDataCardItem
import com.hrsthrt74.afdian.ui.components.home.CreatorDataEditPage
import com.hrsthrt74.afdian.ui.components.home.HomeCustomizationSheet
import com.hrsthrt74.afdian.ui.components.home.HomeModuleContent
import com.hrsthrt74.afdian.ui.components.home.HomeModuleContentContext
import com.hrsthrt74.afdian.ui.components.home.QuickEntryPickerSheet
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.HistoryViewModel
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 首页容器只负责页面骨架、数据订阅和模块顺序；具体模块 UI 放在 home 包内。 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenManagement: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSponsoring: () -> Unit,
    /** 打开「收入明细」页：首页「近5天收入」模块的标题点击入口。 */
    onOpenIncomeDetail: () -> Unit,
    historyViewModel: HistoryViewModel,
    onOpenHistory: () -> Unit,
    onOpenHistoryEntry: (com.hrsthrt74.afdian.data.history.HistoryEntry) -> Unit,
    /** 通用路由跳转：「快捷方式」模块点任意入口都走它。 */
    onNavigate: (Route) -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val listState = rememberLazyListState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }

    val sponsoring by viewModel.sponsoring.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val creatorCards by viewModel.creatorCards.collectAsState()
    // 卡片单位是否显示由设置决定，开关变化后立即重算展示项
    val showIncomeUnit by settingsStore.showIncomeUnit.collectAsState(initial = false)
    val showCountUnit by settingsStore.showCountUnit.collectAsState(initial = false)
    val creatorCardItems = remember(creatorCards, showIncomeUnit, showCountUnit) {
        creatorCards.mapValues { (_, card) -> card.toDataCardItem(showIncomeUnit, showCountUnit) }
    }
    val charts by viewModel.creatorCharts.collectAsState()
    // 「近5天收入」模块数据（随首页刷新一起拉取）
    val recentIncomeRecords by viewModel.recentIncomeRecords.collectAsState()
    // dashboard 的未确认金额：结算窗口内作为「上月收入」供提现提醒卡展示
    val unconfirmedAmount by viewModel.unconfirmedAmount.collectAsState()
    val markedCreators by viewModel.markedCreators.collectAsState()
    val historyEntries by historyViewModel.entries.collectAsState()
    // ── 首页模块形状：启用集合 / 顺序 / 快捷方式入口 ──
    // 三份配置都来自 DataStore，读取是异步的，这里一律等真值（初值 null）。
    //
    // 不能先用默认值铺一版：LazyColumn 会按 item key 把「首个可见项」钉在原处。
    // 首帧若按默认顺序渲染，快捷方式模块因为入口还没读到而只是个 0 高度的空壳；
    // 等真值把它的顺序排到后面时，LazyColumn 仍然把这个空壳的 key 留在视口顶部，
    // 于是列表自己往下滚了一大截 —— 这就是「快捷方式不在首位时启动会滚动」的成因。
    // 真值到齐前不铺任何模块，列表形状只定一次，就没有这一步跳动了。
    val enabledModulesState by settingsStore.homeEnabledModules.collectAsState(initial = null)
    val moduleOrderState by settingsStore.homeModuleOrder.collectAsState(initial = null)
    val homeQuickEntryKeys by settingsStore.homeQuickEntryKeys.collectAsState(initial = null)
    // 三者共同决定列表形状，缺一项都不能铺
    val homeModulesReady = enabledModulesState != null && moduleOrderState != null && homeQuickEntryKeys != null
    // 真正参与渲染的模块（顺序 × 启用集合）；未就绪时为空
    val visibleModules = remember(moduleOrderState, enabledModulesState) {
        val order = moduleOrderState
        val enabled = enabledModulesState
        if (order == null || enabled == null) emptyList() else order.filter { it in enabled }
    }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    // 「快捷方式」模块的挑选面板：同样挂在页面底部而非模块内部，
    // 否则模块滚出视口被 LazyColumn 回收时，正在显示的弹层会跟着消失
    var showQuickEntrySheet by remember { mutableStateOf(false) }
    // 创作者数据是否处于整页编辑：为 true 时隐藏其它模块，只渲染可拖拽的编辑页。
    var editingCreatorData by remember { mutableStateOf(false) }

    // ── 顶栏账号切换器 ──
    // 账号列表与切换动作由根层（MainActivity）注入，未登录 / 未注入时为 null
    val accountSwitcher = LocalAccountSwitcher.current
    // 当前账号资料：头像优先取它，拿不到再回退到账号记录里的头像
    val profile by viewModel.profile.collectAsState()
    // 「首页账号切换器」开关（设备级设置，默认关闭）；初值与存储默认值一致，不会先闪一帧头像
    val homeAccountSwitcherEnabled by settingsStore.homeAccountSwitcherEnabled.collectAsState(initial = false)
    // 账号切换菜单的显隐
    val showAccountPopup = remember { mutableStateOf(false) }
    // 三个条件同时满足才显示：非编辑态、开关打开、已保存账号多于一个（单账号没有可切换对象）
    val showHomeAccountSwitcher = !editingCreatorData &&
        homeAccountSwitcherEnabled &&
        (accountSwitcher?.accounts?.size ?: 0) > 1

    // 下拉刷新状态与指示器：isRefreshing 由「下拉」或「顶栏刷新按钮」共同驱动
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    val haptic = LocalHapticFeedback.current

    // 下拉触发刷新：isRefreshing 变 true 时调用 viewModel.refresh()（与顶栏刷新按钮同一路径）
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
        }
    }

    // 刷新完成（isLoading 从 true→false）时关闭下拉刷新指示器
    LaunchedEffect(loading) {
        if (!loading && isRefreshing) {
            isRefreshing = false
        }
    }

    // 编辑态拦截系统返回：编辑页只是页内状态而非导航栈页面，不拦截的话
    // 返回键会穿透到 NavDisplay 把根页面弹掉、直接退出应用。
    // 与发现页搜索层同款 NavigationBackHandler，预测性返回手势提交后才退出编辑。
    val editBackState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = editBackState,
        isBackEnabled = editingCreatorData,
        onBackCompleted = { editingCreatorData = false },
    )

    // 首页是根页面：首次进入时清掉 Compose 可能恢复的旧滚动位置，确保顶部模块可见。
    // 必须等模块配置到齐后再校准：配置没齐时列表还没铺出来，
    // 提前滚到 0 没有意义，反而会被随后定型的 key 锚点带偏。
    // 只执行一次，返回创作者详情页后不会因为重组而把用户滚动位置拉回顶部。
    LaunchedEffect(homeModulesReady) {
        if (!homeModulesReady) return@LaunchedEffect
        listState.scrollToItem(0)
        topAppBarState.heightOffset = 0f
        topAppBarState.contentOffset = 0f
    }

    val moduleContext = HomeModuleContentContext(
        sponsoring = sponsoring, loading = loading, creatorCards = creatorCardItems,
        creatorCharts = charts,
        unconfirmedAmount = unconfirmedAmount,
        markedCreators = markedCreators,
        settingsStore = settingsStore,
        onOpenCreator = onOpenCreator, onOpenManagement = onOpenManagement, onOpenFavorites = onOpenFavorites,
        onOpenSponsoring = onOpenSponsoring,
        historyEntries = historyEntries,
        onOpenHistory = onOpenHistory,
        onOpenHistoryEntry = onOpenHistoryEntry,
        onEditCreatorData = { editingCreatorData = true },
        recentIncomeRecords = recentIncomeRecords,
        onOpenIncomeDetail = onOpenIncomeDetail,
        onNavigate = onNavigate,
        onEditQuickEntries = { showQuickEntrySheet = true },
        // 快捷方式入口由容器统一订阅后下发：容器要用它判断整个列表能否铺，
        // 模块自己再订阅一份会出现「容器已就绪、模块还是空壳」的错位帧
        quickEntryKeys = homeQuickEntryKeys,
    )

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = if (editingCreatorData) "编辑创作者数据" else "首页",
                        color = blurBarColor(backdrop), scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            // 顶栏起始侧账号切换器：多账号 + 开关打开时才会渲染
                            if (showHomeAccountSwitcher && accountSwitcher != null) {
                                // 菜单容器以「调用处的父布局」为锚点，头像与弹层必须同处一个 Box
                                Box {
                                    // 头像 + 徽标单独包一层：徽标要压在头像圆边外侧，
                                    // 而弹层锚点必须仍是外层 Box，所以不能直接套在 Avatar 上
                                    Box(modifier = Modifier.padding(8.dp)) {
                                        // 当前账号头像（点击弹菜单，与「我的」页长按弹出的是同一个组件）
                                        Avatar(
                                            model = profile?.user?.avatar
                                                ?: accountSwitcher.accounts
                                                    .firstOrNull { it.userId == accountSwitcher.currentUserId }?.avatar,
                                            size = 32.dp,
                                            contentDescription = "切换账号",
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                // 打开前刷新一次：昵称/头像与最近使用顺序保持最新
                                                accountSwitcher.onRefresh()
                                                showAccountPopup.value = true
                                            },
                                        )
                                        // 右下角「可切换」徽标：贴着圆的右下角，往外压 2dp 才像角标
                                        AccountSwitcherBadge(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 4.dp, y = 2.dp),
                                        )
                                    }
                                    AccountSwitchPopup(
                                        visible = showAccountPopup,
                                        actions = accountSwitcher,
                                        onManage = { onNavigate(Route.Settings) },
                                    )
                                }
                            }
                        },
                        actions = {
                            if (!editingCreatorData) {
                                // 顶栏刷新按钮：转圈动画与最短转圈时长均内聚在 RefreshButton 内
                                RefreshButton(refreshing = loading, onClick = viewModel::refresh)
                            }
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            // 首页内容 ↔ 创作者数据整页编辑的切换：用 AnimatedContent 默认预设
            // （淡入 + 轻微缩放）过渡，与管理页编辑态切换的观感保持一致，不再硬切。
            AnimatedContent(
                targetState = editingCreatorData,
                modifier = Modifier.fillMaxSize(),
                label = "home_creator_data_edit_switch",
            ) { isEditing ->
                if (isEditing) {
                // 整页编辑：隐藏其它模块，编辑网格作为唯一滚动层，避免“双层滚动”。
                CreatorDataEditPage(
                    context = moduleContext,
                    modifier = Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
//                        .padding(bottom = contentBottomPadding())
                        ,
                    onExit = { editingCreatorData = false },
                )
            } else {
                // 下拉刷新包裹列表：只在普通首页态生效，整页编辑态不套（编辑网格无需刷新）
                PullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = { isRefreshing = true },
                    pullToRefreshState = pullToRefreshState,
                    refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新成功"),
                    // 只让刷新指示器避开模糊顶栏，列表自身的 contentPadding 仍由 LazyColumn 控制
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                    modifier = Modifier.fillMaxSize(),
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).scrollEndHaptic(),
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = contentBottomPadding()),
                ) {
                    // 配置未就绪时整段不铺：一旦先铺了「自定义」按钮或半个列表，
                    // 真值到达后的重排又会让 LazyColumn 按 key 拽走滚动位置
                    if (homeModulesReady) {
                        visibleModules.forEach { module ->
                            item(key = module.storageValue) { HomeModuleContent(module, moduleContext) }
                        }
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
                                        .clip(Capsule())
                                        .clickable { showCustomizeSheet = true }
                                        .background(MiuixTheme.colorScheme.surfaceContainer)
                                        .padding(16.dp, 8.dp)
                                ) {
                                    Text(
                                        "自定义",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
            }
        }
    }
    HomeCustomizationSheet(settingsStore, showCustomizeSheet) { showCustomizeSheet = false }
    QuickEntryPickerSheet(settingsStore, showQuickEntrySheet) { showQuickEntrySheet = false }
}
