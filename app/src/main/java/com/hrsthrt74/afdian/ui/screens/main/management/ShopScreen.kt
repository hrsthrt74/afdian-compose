package com.hrsthrt74.afdian.ui.screens.main.management

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.ShopProduct
import com.hrsthrt74.afdian.data.network.afdianWebUrl
import com.hrsthrt74.afdian.ui.components.PlanDetailSheet
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.RefreshButton
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.PlanDetailViewModel
import com.hrsthrt74.afdian.ui.viewmodel.ShopListUi
import com.hrsthrt74.afdian.ui.viewmodel.ShopViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.PullToRefreshState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 售卖商品筛选 Tab：下标与 [ShopViewModel.tabIndex] 一一对应（0=全部 / 1=上架中 / 2=已隐藏）。
 *
 * 提为顶层常量：TabRow 与「每个 Tab 一份滚动状态」的映射都依赖它。放进可组合函数里会每次
 * 重组重新分配列表，`remember` 里的 key 集合也就不稳定了。
 */
private val SHOP_TABS = listOf("全部", "上架中", "已隐藏")

/** 日志 TAG */
private const val ShopScreenLogTag = "Shop"

/**
 * 售卖商品管理页（创作者后台 /setting/shop）。
 *
 * 结构：TabRow 筛选（全部 / 上架中 / 已隐藏）+ 商品卡列表；顶栏提供刷新与「编辑排序」入口。
 * 编辑排序走独立的底部弹层 [ShopSortSheet]（长按把手拖拽，点确定才提交），不再占用页内状态。
 *
 * 切换筛选 tab 有「我的订单」同款转场（淡入淡出 + 1/8 幅度的横向位移），
 * 每个 tab 各持一份滚动状态、进入时统一回顶（详见 listStates 处的说明）。
 *
 * @param onOpenUrl 打开外部链接（查看商品页 / 网页端编辑器）
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    onOpenUrl: (String) -> Unit,
    initialPlanId: String? = null,
) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: ShopViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app),
    )

    // ── 页面状态 ──
    val tabIndex by viewModel.tabIndex.collectAsState()
    val allUi by viewModel.allUi.collectAsState()
    val onUi by viewModel.onUi.collectAsState()
    val hiddenUi by viewModel.hiddenUi.collectAsState()
    val busyPlanIds by viewModel.busyPlanIds.collectAsState()
    val stockSheet by viewModel.stockSheet.collectAsState()
    val sortSaving by viewModel.sortSaving.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    // 排序弹层是否显示：页内状态即可（草稿顺序由弹层自己持有）
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    // 三个 tab 的列表数据按 tab 下标索引。转场中每一页取「自己那一页的 tab 下标」的数据，
    // 旧页滑出期间冻结在自己原来的商品上，不会因为全局切到目标 tab 而「闪一下目标页」
    val uiByTab = mapOf(0 to allUi, 1 to onUi, 2 to hiddenUi)
    // 当前 tab 的列表状态：顶栏刷新按钮、下拉刷新收尾用
    val ui = uiByTab.getValue(tabIndex)

    // 每个筛选 tab 一份独立的滚动状态。
    //
    // 不能共用一个：切换 tab 用了 AnimatedContent，转场期间新旧两页会**同时组合**，
    // 两页的 LazyColumn 于是同时持有同一个 LazyListState。而 LazyListState 内部全是
    // 「列表独占」的单值件（scrollPosition / itemAnimator / beyondBoundsInfo，以及
    // LazyList.kt 直接 .then 进布局链的 remeasurementModifier、awaitLayoutModifier，
    // 后者只有一个 attachedNode 和一个 lock）——两条列表互相覆盖后会出现「scrollPosition
    // 已改、但 remeasurement 指向另一条甚至已退场的列表」，位置当帧不落位、下一帧才突变，
    // 也就是「切过去位置看着是对的、一滑动就跳到预期外」。
    //
    // 每个 tab 一份实例后，转场中旧页用旧 tab 的状态、新页用新 tab 的状态，互不干扰；
    // 旧页退场时也带着自己原来的位置淡出。
    val listStates = remember {
        SHOP_TABS.indices.associateWith { LazyListState() }
    }
    // 当前 tab 的滚动状态：顶栏「双击回顶」用这一份
    val listState = listStates.getValue(tabIndex)
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    // Tab 切换的轻震动：TextHandleMove 档位，轻量确认「切换发生了」（与「我的订单」页一致）
    val haptic = LocalHapticFeedback.current

    // ── 下拉刷新（与首页同款：isRefreshing 由下拉或顶栏刷新按钮共同驱动） ──
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) viewModel.refresh()
    }
    // 刷新完成（当前 tab 的 loading 从 true→false）时收起指示器
    LaunchedEffect(ui.loading) {
        if (!ui.loading && isRefreshing) isRefreshing = false
    }

    // 切换筛选 tab 时把该 tab 的列表拉回顶部。
    // 每个 tab 各有一份滚动状态，再次进入某个 tab 时会带着上次的位置回来，
    // 这里显式归零，保证「进哪个 tab 都从顶部开始」；只动进来的这一页，
    // 转场中正在退场的旧页保持原位置淡出。
    LaunchedEffect(tabIndex) {
        AppLog.d(ShopScreenLogTag, "tab switched -> $tabIndex, scroll list to top")
        listState.scrollToItem(0)
    }

    // ── 商品详情弹层（「查看」）：复用创作者主页 / 订单页同款 PlanDetailSheet ──
    val planDetailViewModel: PlanDetailViewModel = viewModel(
        key = "shop-plan-detail",
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlanDetailViewModel(app) as T
            }
        },
    )
    var selectedPlanId by rememberSaveable { mutableStateOf(initialPlanId) }
    PlanDetailSheet(
        show = selectedPlanId != null,
        planId = selectedPlanId,
        viewModel = planDetailViewModel,
        onDismiss = { selectedPlanId = null },
    )

    // 排序弹层：草稿顺序在弹层内，点确定才提交（弹层自己处理返回/下拉关闭）
    ShopSortSheet(
        show = showSortSheet,
        // 底稿始终取「全部」列表：接口按全量顺序保存，与当前筛选 tab 无关
        items = allUi.items,
        saving = sortSaving,
        onDismiss = { showSortSheet = false },
        onSubmit = { ordered -> viewModel.saveRank(ordered) { showSortSheet = false } },
    )

    Scaffold(
        topBar = {
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, false) {
                    Column {
                        TopAppBar(
                            title = "售卖商品",
                            largeTitle = "售卖商品",
                            color = blurBarColor(backdrop),
                            scrollBehavior = scrollBehavior,
                            navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                            actions = {
                                // 顶栏动作：刷新 + 打开排序弹层
                                RefreshButton(refreshing = ui.loading, onClick = viewModel::refresh)
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showSortSheet = true
                                    },
                                    enabled = !allUi.loading && allUi.items.isNotEmpty()
                                ) {
                                    // 编辑排序入口
                                    Icon(
                                        imageVector = MiuixIcons.Normal.Sort,
                                        contentDescription = "编辑排序",
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            },
                        )
                        // 筛选 tab；随顶栏一起放进 BlurredBar，模糊时才有底色
                        TabRow(
                            tabs = SHOP_TABS,
                            selectedTabIndex = tabIndex,
                            onTabSelected = { index ->
                                // 先震后切：点击的即时反馈不等转场动画
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectTab(index)
                            },
                            colors = TabRowDefaults.tabRowColors(Color.Transparent),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            // 筛选 tab 切换：「我的订单」同款转场——淡入淡出 + 1/8 幅度的横向位移。
            // 方向按 tab 顺序：切到更靠后的 tab 从右滑入、更靠前的从左滑入。
            // 之所以要这个位移，是因为「上架中 / 已隐藏」两页卡片长得很像，
            // 纯淡入淡出会让人以为没切成功
            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    (fadeIn(animationSpec = tween(220)) +
                        slideInHorizontally(animationSpec = tween(220)) { if (forward) it / 8 else -it / 8 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(150)) +
                                slideOutHorizontally(animationSpec = tween(150)) { if (forward) -it / 8 else it / 8 }
                        )
                },
                label = "shopTabList",
            ) { currentTab ->
                ShopListContent(
                    ui = uiByTab.getValue(currentTab),
                    busyPlanIds = busyPlanIds,
                    paddingValues = paddingValues,
                    // 每一页用自己的滚动状态（见上方 listStates 的说明）
                    listState = listStates.getValue(currentTab),
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    isRefreshing = isRefreshing,
                    pullToRefreshState = pullToRefreshState,
                    onRefresh = { isRefreshing = true },
                    onRetry = viewModel::retry,
                    onToggleVisible = viewModel::toggleVisible,
                    onEditStock = viewModel::openStockSheet,
                    // 查看打开 app 内商品详情弹层；编辑走网页端编辑器（App 内暂未实现）
                    onView = { planId -> selectedPlanId = planId },
                    // 编辑页在外站，域名跟随优先线路
                    onEdit = { planId -> onOpenUrl(afdianWebUrl("/edit/shop?plan_id=$planId")) },
                )
            }
        }
    }

    // ── 改库存弹层 ──
    ShopStockSheet(
        state = stockSheet,
        onDismiss = viewModel::dismissStockSheet,
        onRetry = viewModel::retryStockSheet,
        onSaveSkus = viewModel::saveSkuStock,
        onSaveBundle = viewModel::saveBundleStock,
    )

    // ── 操作失败提示弹窗：确认 / 取消 / 点遮罩关闭时都消费掉错误 ──
    val actionErrorDialogState = rememberDialogState()
    LaunchedEffect(actionError) {
        if (actionError != null) actionErrorDialogState.show()
    }
    AppDialog(
        state = actionErrorDialogState,
        title = "操作失败",
        summary = actionError.orEmpty(),
        confirmText = "知道了",
        onConfirm = viewModel::consumeActionError,
        onDismissed = viewModel::consumeActionError,
    )
}

/**
 * 商品列表主体：下拉刷新 + 商品卡 / 加载 / 空态 / 错误态 + 右侧滚动条。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun ShopListContent(
    ui: ShopListUi,
    busyPlanIds: Set<String>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    isRefreshing: Boolean,
    pullToRefreshState: PullToRefreshState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onToggleVisible: (ShopProduct) -> Unit,
    onEditStock: (ShopProduct) -> Unit,
    onView: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新成功"),
            contentPadding = PaddingValues(top = (paddingValues.calculateTopPadding() + 8.dp)),
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = contentBottomPadding(),
                ),
            ) {
                when {
                    // 首屏加载中
                    ui.loading -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                                contentAlignment = Alignment.Center,
                            ) { LoadingComponent() }
                        }
                    }
                    // 首屏失败：整页错误态 + 重试
                    ui.error != null -> {
                        item {
                            ErrorStateView(
                                onRetry = onRetry,
                                modifier = Modifier.fillParentMaxSize(),
                                summary = ui.error,
                            )
                        }
                    }
                    // 空列表
                    ui.items.isEmpty() -> {
                        item {
                            EmptyStateView(
                                title = "还没有售卖商品",
                                summary = "在网页端「上架新商品」后，这里就能管理它们了",
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }
                    // 商品卡片列表
                    else -> items(ui.items, key = { it.planId }) { product ->
                        ShopProductCard(
                            product = product,
                            busy = product.planId in busyPlanIds,
                            onToggleVisible = { onToggleVisible(product) },
                            onEditStock = { onEditStock(product) },
                            onView = { onView(product.planId) },
                            onEdit = { onEdit(product.planId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                        )
                    }
                }
            }
        }
        // 右侧滚动条
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding(),
            ),
        )
    }
}
