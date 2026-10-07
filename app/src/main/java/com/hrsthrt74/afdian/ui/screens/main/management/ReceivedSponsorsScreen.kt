package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.content.ReceivedShipmentFilter
import com.hrsthrt74.afdian.data.content.ReceivedSponsor
import com.hrsthrt74.afdian.data.content.ReceivedSponsorsSort
import com.hrsthrt74.afdian.data.content.ReceivedTimeRange
import com.hrsthrt74.afdian.ui.components.DataCard
import com.hrsthrt74.afdian.ui.components.DateTimeField
import com.hrsthrt74.afdian.ui.components.FilterChip
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.CopyButton
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import com.hrsthrt74.afdian.ui.viewmodel.ReceivedSponsorsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowListPopup
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * 独立的收到发电订单列表。
 *
 * 本阶段只通过 [onOrderClick] 暴露点击边界，不打开弹窗或实现订单操作。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun ReceivedSponsorsScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    onOrderClick: (ReceivedSponsor) -> Unit = {},
    providedViewModel: ReceivedSponsorsViewModel? = null,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = providedViewModel ?: viewModel<ReceivedSponsorsViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val redeemOnly by viewModel.redeemOnly.collectAsState()
    val remarkOnly by viewModel.remarkOnly.collectAsState()
    val shipmentFilter by viewModel.shipmentFilter.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val timeRange by viewModel.timeRange.collectAsState()
    var pullRefreshing by rememberSaveable { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<ReceivedSponsor?>(null) }
    var showMoreFilters by rememberSaveable { mutableStateOf(false) }
    var showSortPopup by rememberSaveable { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val visibleOrders = remember(orders, searchQuery) {
        val keyword = searchQuery.trim()
        if (keyword.isEmpty()) {
            orders
        } else {
            orders.filter { order ->
                listOf(
                    order.orderNo,
                    order.userName,
                    order.planName,
                    order.modelName,
                    order.remark,
                    order.receiverInfo,
                    order.expressNo,
                    order.orderRemark,
                ).any { value -> value?.contains(keyword, ignoreCase = true) == true }
            }
        }
    }
    // 只有「新订单在前 + 没有任何筛选与搜索」时列表才是完整时间线，
    // 此时才插入「今天 / 昨天 / 更早」标题；一旦筛选过，标题会误导阅读。
    val showDateSections = sort == ReceivedSponsorsSort.Newest &&
        !redeemOnly &&
        !remarkOnly &&
        shipmentFilter == ReceivedShipmentFilter.All &&
        timeRange == ReceivedTimeRange() &&
        searchQuery.isBlank()
    val sections = remember(visibleOrders, showDateSections) {
        receivedSponsorSections(visibleOrders, showDateSections)
    }
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(pullRefreshing) {
        if (pullRefreshing) viewModel.refresh()
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullRefreshing = false
    }
    LoadMoreEffect(listState, hasMore, isLoadingMore) { viewModel.loadMore() }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：仅普通顶栏形态启用；
            // 搜索形态（输入框）下整段不挂手势，避免双击回顶干扰输入框的点按/选词操作
            DoubleTapToTop(listState, scrollBehavior, enabled = !searchActive) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                AnimatedContent(
                    targetState = searchActive,
                    label = "收到发电搜索栏切换",
                ) { active ->
                    if (active) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp, 4.dp)
                                .windowInsetsPadding(WindowInsets.statusBars),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchBar(
                                modifier = Modifier.weight(1f),
                                // 外层已按 TopAppBar 规范处理水平间距，避免叠加 SearchBar 默认的 12.dp。
                                insideMargin = DpSize(0.dp, 0.dp),
                                expanded = true,
                                onExpandedChange = { expanded ->
                                    if (!expanded) {
                                        searchActive = false
                                        searchQuery = ""
                                    }
                                },
                                outsideEndAction = {
                                    Text(
                                        text = "取消",
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(start = 16.dp, end = 4.dp)
                                            .clickable(
                                                interactionSource = null,
                                                indication = null,
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                searchActive = false
                                                searchQuery = ""
                                            },
                                    )
                                },
                                inputField = {
                                    InputField(
                                        query = searchQuery,
                                        onQueryChange = { searchQuery = it },
                                        onSearch = { searchQuery = it },
                                        expanded = true,
                                        onExpandedChange = {},
                                        label = "搜索订单",
                                        leadingIcon = {
                                            Icon(
                                                modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                                                imageVector = MiuixIcons.Basic.Search,
                                                contentDescription = null,
                                            )
                                        },
                                    )
                                },
                            ) {}
                        }
                    } else {
                        TopAppBar(
                            title = "收到发电",
                            largeTitle = "收到发电",
                            color = blurBarColor(backdrop),
                            scrollBehavior = scrollBehavior,
                            navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                            actions = {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        searchActive = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Search,
                                        contentDescription = "搜索订单",
                                    )
                                }
                            },
                        )
                    }
                }
                }
            }
        },
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = pullRefreshing,
            onRefresh = { pullRefreshing = true },
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新成功"),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            Box{
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .scrollEndHaptic(),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = contentBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 过滤
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 发货筛选收进「更多」，常驻 Chip 保持稳定布局，避免筛选切换时产生跳动。
                            ReceivedSponsorSortChip(
                                sort = sort,
                                onClick = { showSortPopup = true },
                                showPopup = showSortPopup,
                                onDismissPopup = { showSortPopup = false },
                                onSortSelected = viewModel::setSort,
                                modifier = Modifier.weight(1.5f),
                            )
                            FilterChip(
                                text = "兑换码",
                                selected = redeemOnly,
                                onClick = viewModel::toggleRedeemOnly,
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                text = "有留言",
                                selected = remarkOnly,
                                onClick = viewModel::toggleRemarkOnly,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMoreFilters = true
                                },
                                backgroundColor = if (shipmentFilter != ReceivedShipmentFilter.All || timeRange != ReceivedTimeRange()) {
                                    MiuixTheme.colorScheme.primaryContainer
                                } else {
                                    MiuixTheme.colorScheme.secondary
                                },
                                modifier = Modifier.pressable(
                                    interactionSource = null,
                                    indication = SinkFeedback(),
                                    delay = null,
                                ),
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Filter,
                                    contentDescription = "更多筛选与排序",
                                    tint = if (shipmentFilter != ReceivedShipmentFilter.All || timeRange != ReceivedTimeRange()) {
                                        MiuixTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MiuixTheme.colorScheme.onSecondaryVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    when {
                        // 全屏加载中
                        isLoading -> item { LoadingComponent(modifier = Modifier.fillMaxWidth()) }

                        // 加载失败
                        error != null -> item { ErrorStateView(
                            onRetry = viewModel::load,
                            modifier = Modifier.fillParentMaxSize().padding(horizontal = 16.dp),
                            summary = error!!,
                        ) }

                        // 暂无发电记录
                        visibleOrders.isEmpty() -> item { EmptyStateView(
                            title = "暂无发电记录",
                            summary = if (searchQuery.isBlank()) {
                                "点击已选中的筛选项可取消过滤"
                            } else {
                                "没有找到匹配的订单"
                            },
                            icon = MiuixIcons.Light.Create,
                            modifier = Modifier.fillParentMaxSize(),
                        ) }
                    }

                    // 发电记录卡片
                    if (!isLoading && error == null) {
                        sections.forEach { section ->
                            // 标题为 null 表示当前不按日期分组，整段直接平铺。
                            section.title?.let { title ->
                                item(key = "section-$title") {
                                    Text(
                                        text = title,
                                        style = MiuixTheme.textStyles.subtitle,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    )
                                }
                            }
                            items(section.orders, key = { it.orderNo }) { order ->
                                ReceivedSponsorCard(
                                    order,
                                    onOpenConversation = {
                                        backStack.add(
                                            Route.MessageConversation(
                                                userId = order.userId,
                                                userName = order.userName,
                                                avatarUrl = order.avatarUrl,
                                            )
                                        )
                                    },
                                    onOpenDetails = {
                                        selectedOrder = order
                                        onOrderClick(order)
                                    },
                                )
                            }
                        }
                    }

                    // 末尾加载中
                    if (isLoadingMore) {
                        item { LoadingComponent(modifier = Modifier.fillMaxWidth()) }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
                // 滚动条
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    trackPadding = PaddingValues(top = paddingValues.calculateTopPadding())
                )
            }
        }
        ReceivedSponsorDetailSheet(
            order = selectedOrder,
            onDismiss = { selectedOrder = null },
            onOpenConversation = { order ->
                selectedOrder = null
                backStack.add(
                    Route.MessageConversation(
                        userId = order.userId,
                        userName = order.userName,
                        avatarUrl = order.avatarUrl,
                    )
                )
            },
        )
        ReceivedSponsorFilterSheet(
            show = showMoreFilters,
            shipmentFilter = shipmentFilter,
            sort = sort,
            timeRange = timeRange,
            onShipmentFilterChanged = viewModel::setShipmentFilter,
            onSortChanged = viewModel::setSort,
            onTimeRangeChanged = viewModel::setTimeRange,
            onDismiss = { showMoreFilters = false },
        )
    }
}

@Composable
private fun ReceivedSponsorSortChip(
    sort: ReceivedSponsorsSort,
    onClick: () -> Unit,
    showPopup: Boolean,
    onDismissPopup: () -> Unit,
    onSortSelected: (ReceivedSponsorsSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier) {
        FilterChip(
            text = sort.title,
            selected = sort != ReceivedSponsorsSort.Newest,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        WindowListPopup(
            show = showPopup,
            alignment = PopupPositionProvider.Align.BottomStart,
            onDismissRequest = onDismissPopup,
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                ReceivedSponsorsSort.values().forEachIndexed { index, option ->
                    DropdownImpl(
                        text = option.title,
                        optionSize = ReceivedSponsorsSort.values().size,
                        isSelected = sort == option,
                        index = index,
                        onSelectedIndexChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onSortSelected(option)
                            dismiss?.invoke()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivedSponsorFilterSheet(
    show: Boolean,
    shipmentFilter: ReceivedShipmentFilter,
    sort: ReceivedSponsorsSort,
    timeRange: ReceivedTimeRange,
    onShipmentFilterChanged: (ReceivedShipmentFilter) -> Unit,
    onSortChanged: (ReceivedSponsorsSort) -> Unit,
    onTimeRangeChanged: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 时间草稿（毫秒）：null 表示该边界不限制。
    // DateTimeField 本身没有「未设置」状态，因此未设置时用「今天」作为展示值，
    // 只有用户真正拨动滚轮才会写入草稿，未动过的边界仍然保持「不限」。
    var draftBeginMillis by remember(show, timeRange.beginMillis) { mutableStateOf(timeRange.beginMillis) }
    var draftEndMillis by remember(show, timeRange.endMillis) { mutableStateOf(timeRange.endMillis) }
    val zone = remember { ZoneId.systemDefault() }
    // 未设置边界时的展示值：起始取今天 00:00，结束取今天 23:59
    val fallbackBeginMillis = remember { LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli() }
    val fallbackEndMillis = remember {
        LocalDate.now().atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
    }
    val hasTimeFilter = draftBeginMillis != null || draftEndMillis != null

    WindowBottomSheet(
        show = show,
        title = "筛选与排序",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallTitle(text = "排序", insideMargin = PaddingValues(start = 16.dp))
            ReceivedSponsorsSort.entries.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowOptions.forEach { option ->
                        FilterChip(
                            text = option.title,
                            selected = sort == option,
                            onClick = {
                                onSortChanged(option)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SmallTitle(text = "发货状态", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 0.dp))
            ReceivedShipmentFilter.entries.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowOptions.forEach { option ->
                        FilterChip(
                            text = option.title,
                            selected = shipmentFilter == option,
                            onClick = {
                                onShipmentFilterChanged(option)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

            }

            SmallTitle(text = "时间", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 0.dp))
            Card(
                // 弹层内 Card 统一配色：与 Sheet 背景拉开层次
                colors = AfdianCardDefaults.overlayColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 起始时间：日期 / 时间双滚轮
                    DateTimeField(
                        label = "起始时间",
                        millis = draftBeginMillis ?: fallbackBeginMillis,
                        onChanged = { draftBeginMillis = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    // 结束时间：日期 / 时间双滚轮
                    DateTimeField(
                        label = "结束时间",
                        millis = draftEndMillis ?: fallbackEndMillis,
                        onChanged = { draftEndMillis = it },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasTimeFilter) {
                    FilterChip(
                        text = "清除",
                        selected = false,
                        onClick = {
                            draftBeginMillis = null
                            draftEndMillis = null
                            onTimeRangeChanged(null, null)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                FilterChip(
                    text = "应用",
                    selected = false,
                    onClick = {
                        onTimeRangeChanged(draftBeginMillis, draftEndMillis)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

}

@Composable
private fun ReceivedSponsorCard(
    order: ReceivedSponsor,
    onOpenConversation: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 用户信息和金额区域点击后直接进入私信。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenConversation)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 头像：统一使用项目 Avatar 组件与失败占位背景。
                Avatar(
                    model = order.avatarUrl,
                    size = 48.dp,
                    contentDescription = "${order.userName}的头像",
                )
                // 用户名 & 时间
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = order.userName,
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatPaidTime(order.paidAtSeconds),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                // 金额
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "¥",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = order.amount.ifBlank { "--" },
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.primary,
                            textDecoration = if (order.redeemCode != null) {
                                TextDecoration.LineThrough
                            } else {
                                null
                            },
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                    order.bundlePriceDiff?.let { discount ->
                        Text(
                            text = "优惠了￥$discount",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    // 使用优惠码时大金额已是实付价格，这里补一行减免金额说明
                    order.affiliateReduction?.let { reduction ->
                        Text(
                            text = "优惠了￥$reduction",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    if (order.redeemCode != null) {
                        Text(
                            text = "使用兑换码",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp))

            // 订单信息区域点击后打开详情 Sheet，右侧箭头提示可进入。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDetails)
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SponsorField("方案", order.planName)
                    // 型号为「默认型号」时视为无有效型号，整行不展示（精确四字匹配）
                    if (order.modelName != "默认型号") {
                        SponsorField("型号", order.modelName)
                    }
                    // 使用优惠码的订单展示优惠码本身
                    order.affiliateCode?.let { SponsorField("优惠码", it) }
                    order.redeemCode?.let { SponsorField("兑换自", it) }
                    order.remark?.let { SponsorField("留言", it) }
//                    SponsorField("单号", order.orderNo)
                }
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    contentDescription = "查看订单详情",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SponsorField(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier
                .width(56.dp) // 头像48+padding12-4
                .padding(start = 4.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body2,
        )
    }
}

/** 订单详情 Sheet：不额外增加横向页边距，直接使用 Sheet 自带的内容宽度。 */
@Composable
private fun ReceivedSponsorDetailSheet(
    order: ReceivedSponsor?,
    onDismiss: () -> Unit,
    onOpenConversation: (ReceivedSponsor) -> Unit,
) {
    WindowBottomSheet(
        show = order != null,
        title = "订单详情",
        onDismissRequest = onDismiss,
    ) {
        order?.let { current ->
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    // 用户信息
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        // 弹层内 Card 统一配色：与 Sheet 背景拉开层次
                        colors = AfdianCardDefaults.overlayColors(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(
                                model = current.avatarUrl,
                                size = 48.dp,
                                contentDescription = "${current.userName}的头像",
                            )
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(current.userName)
                                Text(
                                    text = formatPaidTime(current.paidAtSeconds),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            IconButton(
                                onClick = { onOpenConversation(current) },
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Community,
                                    contentDescription = "给${current.userName}发私信",
                                )
                            }
                        }
                    }
                }
                item {
                    // 仅金额和月份使用数据卡，保持其他订单信息的阅读层级。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DataCard(
                            title = "发电金额",
                            value = current.totalAmountWithShipping.ifBlank { "--" },
                            // 金额为占位符时不挂单位，避免出现「-- 元」
                            unit = current.totalAmountWithShipping.takeIf { it.isNotBlank() }?.let { "元" },
                            modifier = Modifier.weight(1f),
                            // 弹层内 Card 统一配色：与 Sheet 背景拉开层次
                            colors = AfdianCardDefaults.overlayColors(),
                        )
                        DataCard(
                            title = "月份",
                            value = if (current.monthCount > 0) current.monthCount.toString() else "--",
                            // 同上，无有效月数时不显示「个月」
                            unit = if (current.monthCount > 0) "个月" else null,
                            modifier = Modifier.weight(1f),
                            // 弹层内 Card 统一配色：与 Sheet 背景拉开层次
                            colors = AfdianCardDefaults.overlayColors(),
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        // 弹层内 Card 统一配色：与 Sheet 背景拉开层次
                        colors = AfdianCardDefaults.overlayColors(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SponsorDetailField("方案", current.planName)
                            SponsorDetailField("型号", current.modelName)
                            // 使用优惠码的订单：减免金额与优惠码本身
                            current.affiliateReduction?.let {
                                SponsorDetailField("已优惠", "￥$it")
                            }
                            current.affiliateCode?.let { SponsorDetailCopyField("优惠码", it) }
                            // 使用兑换码的订单：展示兑换来源
                            current.redeemCode?.let { SponsorDetailCopyField("兑换自", it) }
                            // 运费为 0、0.00 或无数据时不展示该行
                            if (current.freight.toBigDecimalOrNull()?.signum() == 1) {
                                SponsorDetailField("运费", "¥${current.freight}")
                            }
                            current.remark?.let { SponsorDetailCopyField("留言", it) }
                            SponsorDetailOrderNoField(current.orderNo)
                            formatValidity(current.validFromSeconds, current.validUntilSeconds)
                                ?.let { SponsorDetailField("有效期", it) }
                            current.receiverInfo?.let { SponsorDetailField("收件信息", it) }
                            current.expressNo?.let { SponsorDetailField("快递单号", it) }
                            current.orderRemark?.let { SponsorDetailField("备注", it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsorDetailField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(text = value, style = MiuixTheme.textStyles.body2)
    }
}

/**
 * 带复制按钮的详情行：点击复制按钮把该行内容写入剪贴板，
 * 不改变 Sheet 的关闭状态。留言等长文本占满剩余宽度并换行。
 *
 * 复制动作（震动、写剪贴板、图标反馈、Toast）统一交给 [CopyButton] 处理。
 */
@Composable
private fun SponsorDetailCopyField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // weight(1f)：长内容（如留言）在按钮左侧换行，不会把按钮挤出屏幕
        SponsorDetailField(label, value, modifier = Modifier.weight(1f))

        // 复制该行内容：Toast 沿用「$label已复制」的既有文案，
        // 预设样式现在与字重绑定，此处跟随默认档（Regular 字重）
        CopyButton(
            text = value,
            toastText = "${label}已复制",
            contentDescription = "复制$label",
        )
    }
}

/** 单号详情行：复用通用复制行，复制按钮只复制订单号，不改变 Sheet 的关闭状态。 */
@Composable
private fun SponsorDetailOrderNoField(orderNo: String) {
    SponsorDetailCopyField("单号", orderNo)
}

/** 收到发电列表的一段：[title] 为 null 表示该段不显示标题（即不按日期分组）。 */
private data class ReceivedSponsorSection(
    val title: String?,
    val orders: List<ReceivedSponsor>,
)

/**
 * 按支付时间把订单分到「今天」「昨天」「更早」。
 *
 * - 只在 [groupByDate] 为 true 时分组，否则返回单段无标题列表；
 * - 空分组不产出，因此「今天没有订单」时不会显示「今天」标题，「昨天」同理。
 */
private fun receivedSponsorSections(
    orders: List<ReceivedSponsor>,
    groupByDate: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ReceivedSponsorSection> {
    if (orders.isEmpty()) return emptyList()
    if (!groupByDate) return listOf(ReceivedSponsorSection(null, orders))

    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val todayOrders = ArrayList<ReceivedSponsor>()
    val yesterdayOrders = ArrayList<ReceivedSponsor>()
    val earlierOrders = ArrayList<ReceivedSponsor>()
    orders.forEach { order ->
        val paidDate = Instant.ofEpochSecond(order.paidAtSeconds).atZone(zone).toLocalDate()
        when (paidDate) {
            today -> todayOrders.add(order)
            yesterday -> yesterdayOrders.add(order)
            else -> earlierOrders.add(order)
        }
    }
    return buildList {
        if (todayOrders.isNotEmpty()) add(ReceivedSponsorSection("今天", todayOrders))
        if (yesterdayOrders.isNotEmpty()) add(ReceivedSponsorSection("昨天", yesterdayOrders))
        if (earlierOrders.isNotEmpty()) add(ReceivedSponsorSection("更早", earlierOrders))
    }
}

private fun formatValidity(fromSeconds: Long?, untilSeconds: Long?): String? {
    if (fromSeconds == null && untilSeconds == null) return null
    val formatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val from = fromSeconds?.let { formatter.format(Date(it * 1000)) } ?: "未知"
    val until = untilSeconds?.let { formatter.format(Date(it * 1000)) } ?: "未知"
    return "$from 至 $until"
}

private fun formatPaidTime(seconds: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000))
