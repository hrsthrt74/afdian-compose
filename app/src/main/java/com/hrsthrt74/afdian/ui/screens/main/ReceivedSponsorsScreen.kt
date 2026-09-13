package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.ReceivedShipmentFilter
import com.hrsthrt74.afdian.data.content.ReceivedSponsor
import com.hrsthrt74.afdian.data.content.ReceivedSponsorsSort
import com.hrsthrt74.afdian.data.content.ReceivedTimeRange
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.DataCard
import com.hrsthrt74.afdian.ui.components.EmptyStateView
import com.hrsthrt74.afdian.ui.components.FilterChip
import com.hrsthrt74.afdian.ui.components.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.ReceivedSponsorsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
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
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.More
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
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth

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
                        error != null -> item { EmptyStateView(
                            title = "加载失败",
                            summary = error!!,
                            modifier = Modifier.fillParentMaxSize().padding(horizontal = 16.dp),
                            action = {
                                Button(onClick = viewModel::load, modifier = Modifier.fillMaxWidth()) {
                                    Text("重试")
                                }
                            },
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
                        items(visibleOrders, key = { it.orderNo }) { order ->
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
    onTimeRangeChanged: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingBoundary by remember(show) { mutableStateOf<TimeBoundary?>(null) }
    var draftBeginDate by remember(show, timeRange.beginDate) { mutableStateOf(timeRange.beginDate) }
    var draftEndDate by remember(show, timeRange.endDate) { mutableStateOf(timeRange.endDate) }
    val hasTimeFilter = draftBeginDate != null || draftEndDate != null

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
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
            ) {
                Column{
                    TimeBoundaryRow(
                        title = "起始时间",
                        date = draftBeginDate,
                        expanded = editingBoundary == TimeBoundary.Begin,
                        onClick = {
                            editingBoundary = if (editingBoundary == TimeBoundary.Begin) {
                                null
                            } else {
                                TimeBoundary.Begin
                            }
                        },
                    )
                    AnimatedVisibility(
                        visible = editingBoundary == TimeBoundary.Begin,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        ReceivedDatePicker(
                            date = draftBeginDate ?: LocalDate.now(),
                            onDateChanged = { draftBeginDate = it },
                        )
                    }
                    TimeBoundaryRow(
                        title = "结束时间",
                        date = draftEndDate,
                        expanded = editingBoundary == TimeBoundary.End,
                        onClick = {
                            editingBoundary = if (editingBoundary == TimeBoundary.End) {
                                null
                            } else {
                                TimeBoundary.End
                            }
                        },
                    )
                    AnimatedVisibility(
                        visible = editingBoundary == TimeBoundary.End,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        ReceivedDatePicker(
                            date = draftEndDate ?: LocalDate.now(),
                            onDateChanged = { draftEndDate = it },
                        )
                    }
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
                            draftBeginDate = null
                            draftEndDate = null
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
                        onTimeRangeChanged(draftBeginDate, draftEndDate)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

}

/** 当前正在编辑的时间边界。 */
private enum class TimeBoundary {
    Begin,
    End,
}

@Composable
private fun TimeBoundaryRow(
    title: String,
    date: LocalDate?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val chipBackground = if (expanded) {
        MiuixTheme.colorScheme.primaryContainer
    } else {
        MiuixTheme.colorScheme.secondary
    }
    val chipContent = if (expanded) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MiuixTheme.colorScheme.onSecondaryVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(48.dp))
                .background(chipBackground)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date?.toString() ?: "不限",
                style = MiuixTheme.textStyles.body2,
                color = chipContent,
            )
        }
    }
}

/**
 * 日期选择器由年、月、日三个滚轮组成；月份变化时会把日期限制在目标月份的有效天数内。
 */
@Composable
private fun ReceivedDatePicker(
    date: LocalDate,
    onDateChanged: (LocalDate) -> Unit,
) {
    val dayRange = 1..YearMonth.of(date.year, date.monthValue).lengthOfMonth()
    val currentYear = LocalDate.now().year
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NumberPicker(
            value = date.year,
            onValueChange = { year ->
                val day = minOf(date.dayOfMonth, YearMonth.of(year, date.monthValue).lengthOfMonth())
                onDateChanged(LocalDate.of(year, date.monthValue, day))
            },
            range = 2018..currentYear,
            modifier = Modifier.weight(1.5f),
        )
        Text("年")
        NumberPicker(
            value = date.monthValue,
            onValueChange = { month ->
                val day = minOf(date.dayOfMonth, YearMonth.of(date.year, month).lengthOfMonth())
                onDateChanged(LocalDate.of(date.year, month, day))
            },
            range = 1..12,
            modifier = Modifier.weight(1f),
        )
        Text("月")
        NumberPicker(
            value = date.dayOfMonth,
            onValueChange = { day ->
                onDateChanged(LocalDate.of(date.year, date.monthValue, day))
            },
            range = dayRange,
            modifier = Modifier.weight(1f),
        )
        Text("日")
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
                    SponsorField("型号", order.modelName)
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
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
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
                            value = "¥${current.totalAmountWithShipping.ifBlank { "--" }}",
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surface,
                            ),
                        )
                        DataCard(
                            title = "月份",
                            value = if (current.monthCount > 0) "${current.monthCount} 个月" else "--",
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surface,
                            ),
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
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
                            current.affiliateCode?.let { SponsorDetailField("优惠码", it) }
                            SponsorDetailField("运费", "¥${current.freight}")
                            current.remark?.let { SponsorDetailField("留言", it) }
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
private fun SponsorDetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(text = value, style = MiuixTheme.textStyles.body2)
    }
}

/** 单号详情行：复制按钮只复制订单号，不改变 Sheet 的关闭状态。 */
@Composable
private fun SponsorDetailOrderNoField(orderNo: String) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SponsorDetailField("单号", orderNo)

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("订单号", orderNo))
                AppLog.d("ReceivedSponsors", "订单号已复制")
                Toast.makeText(context, "订单号已复制", Toast.LENGTH_SHORT).show()
            },
        ) {
            Icon(
                imageVector = MiuixIcons.Demibold.Copy,
                contentDescription = "复制订单号",
            )
        }
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
