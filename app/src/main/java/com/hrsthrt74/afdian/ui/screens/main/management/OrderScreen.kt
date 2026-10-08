package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.OrderItem
import com.hrsthrt74.afdian.ui.components.PlanDetailSheet
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.CopyButton
import com.hrsthrt74.afdian.ui.components.basic.CopyButtonStyle
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.OrderViewModel
import com.hrsthrt74.afdian.ui.viewmodel.PlanDetailViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 订单筛选 Tab：与网页端四个状态一致（接口必填 status，无「全部」选项）。
 *
 * 提为顶层常量：TabRow、每个 Tab 的滚动状态映射、转场方向判断都依赖它。
 * 放在可组合函数里会每次重组重新分配列表，`remember` 里的 key 集合也就不稳定了。
 */
private val ORDER_FILTERS = listOf(
    OrderViewModel.STATUS_PAID to "已支付",
    OrderViewModel.STATUS_UNPAID to "待支付",
    OrderViewModel.STATUS_UNSHIPPED to "待发货",
    OrderViewModel.STATUS_SHIPPED to "已发货",
)

/** 日志 TAG */
private const val OrderScreenLogTag = "OrderScreen"

/**
 * 「我的订单」页：当前账号在别人家的购买记录。
 * 筛选与网页端一致（已支付/待支付/待发货/已发货），接口不支持「全部」，默认停在已支付。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun OrderScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    providedViewModel: OrderViewModel? = null,
    // 点击订单卡头部 → 跳转创作者主页（路由由外层注入，与其他页面同款）
    onOpenCreator: (AfadianUser) -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as Application
    val vm = providedViewModel ?: viewModel<OrderViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    )
    val status by vm.status.collectAsState()
    val snapshots by vm.snapshots.collectAsState()
    val summary by vm.summary.collectAsState()
    val loading by vm.loading.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val isLoadingMore by vm.isLoadingMore.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val error by vm.error.collectAsState()
    // 筛选 Tab 定义见文件顶部的 ORDER_FILTERS（与网页端四个状态一致，无「全部」选项）

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    // Tab 切换的轻震动：TextHandleMove 档位，轻量确认「切换发生了」
    val haptic = LocalHapticFeedback.current

    // 每个筛选 Tab 一份独立的滚动状态。
    //
    // 为什么不能「只用一个」或「按 status 临时重建」——这正是上一版修了却没用的原因：
    // AnimatedContent 转场期间新旧两页会**同时组合**，两页的 LazyColumn 于是同时持有同一个
    // LazyListState。而 LazyListState 内部全是「列表独占」的单值件：scrollPosition、
    // itemAnimator、beyondBoundsInfo，以及 remeasurementModifier / awaitLayoutModifier
    // （LazyList.kt 直接把状态自带的这两个实例 .then 进布局链，后者只有一个 attachedNode
    // 和一个 lock）。两条列表互相覆盖后，程序化滚动会出现「scrollPosition 已改、但
    // remeasurement 指向了另一个（甚至已退场的）列表」→ 位置当帧不落位、下一帧才突变。
    // 症状就是：切 Tab 后位置看着是对的，一滑动就跳到预期外的位置。
    //
    // 每个 Tab 各自一份实例后，转场中旧页用旧 Tab 的状态、新页用新 Tab 的状态，互不干扰；
    // 旧页退场时也是「带着自己原来的位置」淡出，不会闪跳。
    val listStates = remember {
        ORDER_FILTERS.associate { it.first to LazyListState() }
    }
    // 当前 Tab 的滚动状态：顶栏「双击回顶」与右侧滚动条读这一份
    val listState = listStates.getValue(status)

    // 切 Tab 回顶：进入某个 Tab 时把该 Tab 自己的滚动位置归零。
    // 只动「进来的这一页」，转场中正在退场的旧页保持原位置淡出。
    LaunchedEffect(status) {
        AppLog.d(OrderScreenLogTag, "filter switched -> status=$status, scroll list to top")
        listState.scrollToItem(0)
    }
    // 下拉刷新：pullRefreshing 只在用户拉过阈值后为 true，刷新完成（isRefreshing=false）时复位
    val pullToRefreshState = rememberPullToRefreshState()
    var pullRefreshing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pullRefreshing) {
        if (pullRefreshing) vm.refresh()
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullRefreshing = false
    }

    // 方案详情弹层：点订单卡里的方案卡中卡时打开（复用创作者主页同款组件）
    val planDetailViewModel: PlanDetailViewModel = viewModel(
        key = "order-plan-detail",
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlanDetailViewModel(app) as T
            }
        }
    )
    var selectedPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    PlanDetailSheet(
        show = selectedPlanId != null,
        planId = selectedPlanId,
        viewModel = planDetailViewModel,
        onDismiss = { selectedPlanId = null },
    )

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            // TabRow 放进 BlurredBar 内部（同 FavoritesScreen）：模糊开启时随顶栏一起参与采样，
            // 模糊关闭时也靠 BlurredBar 的 surface 底色兜底，避免露出下层滚动内容
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, false) {
            Column {
            TopAppBar(
                title = "我的订单",
                largeTitle = "我的订单",
                // 订单总数作为顶栏副标题（首帧空格占位，高度不突变）
                subtitle = summary,
                color = blurBarColor(backdrop),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton(onBack = onBack, backStack = backStack)
                }
            )
            TabRow(
                tabs = ORDER_FILTERS.map { it.second },
                selectedTabIndex = ORDER_FILTERS.indexOfFirst { it.first == status }.coerceAtLeast(0),
                onTabSelected = { index ->
                    // 先震后切：TextHandleMove 轻量档
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.load(ORDER_FILTERS[index].first)
                },
                // 背景透明：让顶栏模糊层透出来，TabRow 只负责指示器与文字
                colors = TabRowDefaults.tabRowColors(Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
            }
            }
            }
        }
    ) { paddingValues ->
        // 模糊采样层只包滚动内容（带 textureBlur 的栏不能进采样层，否则原生层崩溃）
        Column(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                // 切 Tab 时整个列表做淡入淡出 + 小幅平移：让「数据确实换了」看得见，
                // 避免已支付/已发货两页卡片长得像而被误以为没切换成功
                AnimatedContent(
                    targetState = status,
                    transitionSpec = {
                        // 按 ORDER_FILTERS 顺序决定平移方向：切到更靠后的 Tab 从右滑入，反之从左
                        val forward = ORDER_FILTERS.indexOfFirst { it.first == targetState } >
                            ORDER_FILTERS.indexOfFirst { it.first == initialState }
                        (fadeIn(animationSpec = tween(220)) +
                            slideInHorizontally(animationSpec = tween(220)) { if (forward) it / 8 else -it / 8 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(150)) +
                                    slideOutHorizontally(animationSpec = tween(150)) { if (forward) -it / 8 else it / 8 }
                            )
                    },
                    label = "orderList"
                ) { currentStatus ->
                    // 转场中的每一页只读自己 Tab 的快照：旧页滑出期间冻结在原数据上，
                    // 不会因为全局列表换成目标页数据而「闪一下目标页」
                    val tabSnapshot = snapshots[currentStatus]
                    // 加载中/错误/下拉刷新这些交互状态只对「正在展示的 Tab」生效，滑出的旧页不受影响
                    val isActiveTab = currentStatus == status
                    // 本页自己的滚动状态（见上方 listStates 的说明）：转场中两页各用各的，
                    // 旧页保持自己原来的位置淡出，新页从顶部进入
                    val pageListState = listStates.getValue(currentStatus)
                    // 滚动到底部附近自动加载下一页（只有活动 Tab 允许触发）
                    LoadMoreEffect(pageListState, if (isActiveTab) hasMore else false, isLoadingMore) { vm.loadMore() }
                    PullToRefresh(
                        isRefreshing = pullRefreshing,
                        onRefresh = { pullRefreshing = true },
                        pullToRefreshState = pullToRefreshState,
                        refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新成功"),
                        // 刷新指示器下移到顶栏（含 TabRow）以下；列表内容自己用 contentPadding 顶开
                        contentPadding = PaddingValues(top = paddingValues.calculateTopPadding() + 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    LazyColumn(
                        state = pageListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                            .scrollEndHaptic(),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = contentBottomPadding()
                        )
                    ) {
                    when {
                        // 该 Tab 还没有数据：活动 Tab 显示加载中/错误态，其余情况显示整页空态
                        tabSnapshot == null || tabSnapshot.orders.isEmpty() -> when {
                            isActiveTab && error != null -> item(key = "error") {
                                ErrorStateView(
                                    onRetry = { vm.load(currentStatus) },
                                    modifier = Modifier.fillParentMaxSize(),
                                    summary = error!!,
                                )
                            }
                            isActiveTab && (loading || tabSnapshot == null) -> item(key = "loading") {
                                LoadingComponent(modifier = Modifier.padding(horizontal = 12.dp))
                            }
                            else -> item(key = "empty") {
                                EmptyStateView(
                                    title = "暂无订单",
                                    summary = "这个分类下还没有订单记录",
                                    modifier = Modifier.fillParentMaxSize(),
                                )
                            }
                        }
                        // 正常列表
                        else -> items(tabSnapshot.orders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onOpenCreator = onOpenCreator,
                                onOpenPlan = { selectedPlanId = it },
                            )
                        }
                    }

                    // 加载更多提示
                    if (isActiveTab && isLoadingMore) {
                        item(key = "loading_more") {
                            Text(
                                "加载更多…",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                    }
                    }
                }
                // 右侧滚动条：trackPadding 让出顶栏高度，与 FavoritesScreen 同款
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    trackPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/** 单条订单卡片：官方网页布局——头像/昵称 + 状态/日期头部 + 商品信息 + 物流/订单号 */
@Composable
private fun OrderCard(
    order: OrderItem,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenPlan: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = 6.dp)
    ) {
        Column {
            // 订单卡专用头部：头像 + 昵称在左，状态和日期在右（各占一行不重叠）；点击进创作者主页
            OrderCardHeader(
                order = order,
                onClick = {
                    // 没拿到创作者标识（极少见的脏数据）就不跳，避免落在空主页
                    if (order.userId != null || order.userSlug != null) {
                        onOpenCreator(
                            AfadianUser(
                                user_id = order.userId.orEmpty(),
                                name = order.creator,
                                avatar = order.avatarUrl,
                                url_slug = order.userSlug,
                            )
                        )
                    }
                },
            )

            // 方案/商品：卡中卡（次级容器底色区分层次），左侧封面图 + 右侧名称/规格/单价；点击开方案详情
            OrderCardPlan(order, onOpenPlan)

            // 下方信息区：统一「标签 / 值 / 按钮」三列布局
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // 期限：纯赞助类订单显示（商品订单的时长在 SKU 规格名里，不重复）
                if (order.duration != null) {
                    InfoRow(label = "期限", value = order.duration)
                }
                // 金额：整单合计（区别于卡中卡里的单价）；多份时补充「单价 × 份数」
                InfoRow(
                    label = "金额",
                    value = buildString {
                        append("¥")
                        append(order.amount)
                        if (order.skuCount > 1 && order.skuPrice != null) {
                            append("（¥")
                            append(order.skuPrice)
                            append(" × ")
                            append(order.skuCount)
                            append("）")
                        }
                    },
                )
                // 买家留言
                if (order.remark != null) {
                    InfoRow(label = "留言", value = order.remark)
                }
                // 兑换码订单：外显只显示码本身，不带官方的链接前缀；可一键复制
                if (order.redeemCode != null) {
                    InfoRow(
                        label = "兑换自",
                        value = order.redeemCode,
                        copyText = order.redeemCode,
                        copyDescription = "复制兑换码",
                    )
                }
                // 已发货订单：收货地址 + 快递信息，快递单号可一键复制
                if (order.address != null) {
                    InfoRow(label = "收货地址", value = order.address)
                }
                if (order.expressNo != null) {
                    InfoRow(
                        label = "快递",
                        value = "${order.expressCompany ?: "--"} ${order.expressNo}",
                        copyText = order.expressNo,
                        copyDescription = "复制快递单号",
                    )
                }
                // 订单号可一键复制
                InfoRow(
                    label = "订单号",
                    value = order.id,
                    copyText = order.id,
                    copyDescription = "复制订单号",
                )
            }
        }
    }
}

@Composable
private fun OrderCardPlan(
    order: OrderItem,
    onOpenPlan: (String) -> Unit
) {
    Card(
        colors = CardColors(
            color = MiuixTheme.colorScheme.secondaryContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainerHighest,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // 纯赞助订单没有方案 id，点击无效
                .then(
                    if (order.planId != null) {
                        Modifier.clickable(onClick = { onOpenPlan(order.planId) })
                    } else {
                        Modifier
                    }
                )
                .padding(8.dp)
        ) {
            // 封面图：始终显示 56dp 占位块，
            // 有图时占位块内叠加 AsyncImage——加载前/失败自然露出占位底色
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .squircleClip(8.dp)
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                if (order.planPic != null) {
                    AsyncImage(
                        model = order.planPic,
                        contentDescription = order.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 方案名 + SKU 规格 + 单价
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    order.title,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // SKU 规格：纯赞助订单没有，规格名与方案名相同时不重复显示
                if (order.skuName != null && order.skuName != order.title) {
                    Text(
                        order.skuName,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            // 右侧单价 × 份数（网页端的「￥xx.xx * n」）；纯赞助订单没有 SKU 就不显示
            if (order.skuPrice != null) {
                Text(
                    text = if (order.skuCount > 1) "¥${order.skuPrice}\n× ${order.skuCount}" else "¥${order.skuPrice}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * 信息行：三列布局——左列固定样式的标签，中列自适应的值（长文本自动换行），右列操作按钮。
 * 值列占满剩余宽度，标签与各行的按钮都不挤压值的显示。
 *
 * @param label 左列标签（金额 / 留言 / 收货地址 / 快递 / 订单号）
 * @param value 中列的值
 * @param copyText 传了就在右列放一个复制按钮
 * @param copyDescription 复制按钮的无障碍描述
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    copyText: String? = null,
    copyDescription: String = "复制",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 左列：标签，固定宽度让各行的值列纵向对齐（最长的「收货地址」4 字也不换行）
        Text(
            label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.width(64.dp)
        )
        // 中列：值，占满剩余宽度；金额行用主色强调，其余用默认色
        Text(
            value,
            style = TextStyle(
                fontSize = 13.sp,
                fontFeatureSettings = "tnum"
            ),
            color = if (label == "金额") {
                MiuixTheme.colorScheme.onSurface
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            modifier = Modifier
                .weight(1f)
//                .padding(start = 4.dp, end = if (copyText != null) 0.dp else 0.dp)
        )
        // 右列：复制按钮（没有就整列留空）
        if (copyText != null) {
            CopyButton(
                text = copyText,
                contentDescription = copyDescription,
                style = CopyButtonStyle.Small,
            )
        } else {
            Spacer(Modifier.height(28.dp))  // 16+12, [com.hrsthrt74.afdian.ui.components.basic.CopyButtonStyle.Small]
        }
    }
}

/**
 * 订单卡专用头部：左侧头像 + 创作者昵称，右侧自上而下是状态标签和日期。
 * 不复用 PostHeader——它的右侧只有一个日期槽位，塞进状态文字会和日期挤在一起。
 */
@Composable
private fun OrderCardHeader(order: OrderItem, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // 整行可点：进创作者主页（与动态卡片头部行为一致）
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 创作者头像
        Avatar(
            model = order.avatarUrl,
            size = 42.dp,
            contentDescription = order.creator,
        )
        // 昵称：状态放右侧独立列，不再挤昵称这一行
        Text(
            text = order.creator.ifBlank { "创作者" },
            style = MiuixTheme.textStyles.subtitle,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
        // 右侧列：状态在上、日期在下，右对齐
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = order.status,
                style = MiuixTheme.textStyles.footnote1,
                color = if (order.status == "待支付" || order.status == "待发货") {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.primary
                }
            )
            // 日期：当年内只显示「M/d」，跨年才带上年份，与官方网页端习惯一致
            if (order.timeSeconds != null) {
                val dateStr = remember(order.timeSeconds) {
                    val date = Date(order.timeSeconds * 1000)
                    val cal = Calendar.getInstance()
                    val orderYear = cal.apply { time = date }.get(Calendar.YEAR)
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    val pattern = if (orderYear == currentYear) "M/d" else "yyyy/M/d"
                    SimpleDateFormat(pattern, Locale.getDefault()).format(date)
                }
                Text(
                    text = dateStr,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
