package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.SponsorFilterPlan
import com.hrsthrt74.afdian.data.content.SponsorFilterRow
import com.hrsthrt74.afdian.data.content.SponsorFilterType
import com.hrsthrt74.afdian.data.content.SponsorManageItem
import com.hrsthrt74.afdian.data.content.SponsorManageSort
import com.hrsthrt74.afdian.data.content.monthDisplayName
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.DataCard
import com.hrsthrt74.afdian.ui.components.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.EmptyStateView
import com.hrsthrt74.afdian.ui.components.FilterChip
import com.hrsthrt74.afdian.ui.components.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.relativeTime
import com.hrsthrt74.afdian.ui.components.YearMonthField
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.SponsorManageViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Favorites
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
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * 赞助者管理页（对应网页 ifdian.net/dashboard/sponsors）。
 *
 * 范围：核心列表 + 滚动加载 + 排序（累计金额/最近赞助/初次赞助）+ 「共 N 名赞助者」统计 + 行点击详情 Sheet。
 * 筛选档位 / 导出 / 群发私信不在本版范围内（群发私信按需求永久排除）。
 *
 * @param providedViewModel 可注入的 VM（路由 entry 用 AndroidViewModelFactory 构建后传入）
 * @param onOpenCreator 详情 Sheet 内「查看主页」跳转
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SponsorManageScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    providedViewModel: SponsorManageViewModel? = null,
    onOpenCreator: (AfadianUser) -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = providedViewModel ?: viewModel<SponsorManageViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val sponsors by viewModel.sponsors.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val filterPlans by viewModel.filterPlans.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val loadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    // 列表滚动状态：双击顶栏回顶 / 滚动加载 / 滚动条共用
    val listState = rememberLazyListState()
    // 滚动到底部附近自动加载下一页（与感谢页同款逻辑）
    LoadMoreEffect(listState, hasMore, loadingMore) { viewModel.loadMore() }

    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    // 详情 Sheet：数据直接取自列表项，无需额外请求
    var detailItem by remember { mutableStateOf<SponsorManageItem?>(null) }
    // 显示状态与数据分离，退出动画期间标题不闪空
    var sheetVisible by remember { mutableStateOf(false) }
    // 排序下拉弹窗：跨旋转/进程重建保留
    var showSortPopup by rememberSaveable { mutableStateOf(false) }
    // 筛选档位下拉弹窗
    var showFilterPopup by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层是模糊栏 + 可折叠大标题栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "赞助者管理",
                        largeTitle = "赞助者管理",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            BackButton(onBack = onBack, backStack = backStack)
                        },
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // 折叠大标题跟手：列表滚动喂给顶栏
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 滚到边界残留速度触发触觉反馈
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding(),
                ),
            ) {
                // 筛选行：排序 Chip + 筛选档位 Chip 并排（各占一半，交互参考收到发电页）
                item(key = "chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SponsorManageSortChip(  // 排序
                            sort = sort,
                            onClick = { showSortPopup = true },
                            showPopup = showSortPopup,
                            onDismissPopup = { showSortPopup = false },
                            onSortSelected = viewModel::setSort,
                            modifier = Modifier.weight(1f),
                        )
                        SponsorFilterChip(      // 筛选
                            summary = filterSummary(filters),
                            active = filters.isNotEmpty(),
                            applied = filters,
                            plans = filterPlans,
                            onClick = { showFilterPopup = true },
                            showPopup = showFilterPopup,
                            onDismissPopup = { showFilterPopup = false },
                            onApply = { rows ->
                                showFilterPopup = false
                                viewModel.setFilters(rows)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 统计行：「共 N 名赞助者」；首屏还没数据时不显示，避免闪「共 0 名」
                if (!loading || sponsors.isNotEmpty()) {
                    item(key = "count") {
                        Text(
                            text = "共 $totalCount 名赞助者",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp),
                        )
                    }
                }

                when {
                    // 首屏加载中：居中的统一加载组件撑满视口
                    loading && sponsors.isEmpty() -> item(key = "loading") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingComponent()
                        }
                    }

                    // 首屏加载失败：空状态 + 重试
                    error != null && sponsors.isEmpty() -> item(key = "error") {
                        EmptyStateView(
                            title = "加载失败",
                            summary = error!!,
                            modifier = Modifier.fillParentMaxSize(),
                            action = {
                                Button(
                                    onClick = viewModel::load,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("重试")
                                }
                            },
                        )
                    }

                    // 空列表（真的没有赞助者）
                    sponsors.isEmpty() -> item(key = "empty") {
                        EmptyStateView(
                            title = "暂无赞助者",
                            summary = "有人发电支持后会出现在这里",
                            icon = MiuixIcons.Light.Favorites,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }

                    else -> {
                        // 赞助者列表：稳定 key 用 user_id（一人一条）
                        items(sponsors, key = { it.id }) { item ->
                            SponsorRow(
                                item = item,
                                onClick = {
                                    detailItem = item
                                    sheetVisible = true
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }

                        // 滚动加载提示
                        if (loadingMore) {
                            item(key = "loading_more") {
                                Text(
                                    "加载更多…",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // 纵向滚动条：贴右侧，顶部让出折叠顶栏高度
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            )
        }
    }

    // 赞助者详情 Sheet
    SponsorDetailSheet(
        show = sheetVisible,
        item = detailItem,
        onDismiss = { sheetVisible = false },
        onOpenCreator = { user ->
            sheetVisible = false
            onOpenCreator(user)
        },
        // 私信：关 Sheet 后进入私信会话
        onOpenConversation = { target ->
            sheetVisible = false
            backStack.add(
                Route.MessageConversation(
                    userId = target.userId,
                    userName = target.name,
                    avatarUrl = target.avatar,
                ),
            )
        },
    )
}

/**
 * 排序下拉 Chip：显示当前排序，点击弹出选项列表（默认档 + 三字段 × 升降序成对）。
 * 交互与样式照搬收到发电页的排序 Chip（FilterChip + WindowListPopup）。
 * 默认档（不传排序字段）不高亮，与参考实现保持一致。
 */
@Composable
private fun SponsorManageSortChip(
    sort: SponsorManageSort,
    onClick: () -> Unit,
    showPopup: Boolean,
    onDismissPopup: () -> Unit,
    onSortSelected: (SponsorManageSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier) {
        // 排序 Chip：文案为当前排序，随 weight 撑满半行
        FilterChip(
            text = sort.title,
            // 默认档不显选中色，选定任一排序（含方向）后高亮为主色容器
            selected = sort != SponsorManageSort.Default,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        // 选项弹窗：贴 Chip 左下角弹出；列表按枚举声明顺序（默认档在最前）
        WindowListPopup(
            show = showPopup,
            alignment = PopupPositionProvider.Align.BottomStart,
            onDismissRequest = onDismissPopup,
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                SponsorManageSort.entries.forEachIndexed { index, option ->
                    DropdownImpl(
                        text = option.title,
                        optionSize = SponsorManageSort.entries.size,
                        isSelected = sort == option,
                        index = index,
                        onSelectedIndexChange = {
                            // 选中反馈：震动 + 切换排序（选默认档即取消排序）+ 收起弹窗
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

/** 筛选面板最多条件行数（防止误加过多条件）。 */
private const val FILTER_MAX_ROWS = 4

/**
 * 主页筛选面板的定位器：横向左缘固定为 [getMargins] 的 horizontalMargin（16dp），
 * 纵向沿用默认 dropdown 锚定（chip 下方优先、放不下翻上）。
 *
 * 默认 DropdownPositionProvider 的 x 直接取 anchor.left，与 popup 内部
 * `hostPositionInWindow` 换算存在坐标系错位（实测右缘顶满屏幕），
 * 这里绕开 anchor.x，用 margin 直接给出窗口坐标，配合内容 widthIn(屏-32)
 * 保证卡片左右各 16dp 对称。
 */
private class StartMarginPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: PopupPositionProvider.Align,
    ): IntOffset {
        val base = ListPopupDefaults.DropdownPositionProvider
            .calculatePosition(anchorBounds, windowBounds, layoutDirection, popupContentSize, popupMargin, alignment)
        // 横向固定左缘 = margin.left，并钳进窗口防极窄窗溢出；纵向保持锚定结果
        val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right)
            .coerceAtLeast(windowBounds.left)
        return IntOffset(x = popupMargin.left.coerceIn(windowBounds.left, maxX), y = base.y)
    }

    override fun getMargins(): PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
}

/** 全局单例：主页筛选面板定位器（无状态，无需 remember）。 */
private val startMarginPositionProvider = StartMarginPositionProvider()

/** 筛选 Chip 文案：无条件「筛选档位」；单条显示条件摘要；多条「筛选 (N)」。 */
private fun filterSummary(filters: List<SponsorFilterRow>): String = when {
    filters.isEmpty() -> "筛选档位"
    filters.size == 1 -> filters[0].summary
    else -> "筛选 (${filters.size})"
}

/** 筛选面板的视图状态：单 popup 内容状态机（主页与各级选择视图互相切换）。 */
private sealed interface SponsorFilterView {
    /** 条件行构建主页 */
    data object Builder : SponsorFilterView

    /** 编辑第 index 行的 type */
    data class TypePick(val index: Int) : SponsorFilterView

    /** 编辑第 index 行的 action */
    data class ActionPick(val index: Int) : SponsorFilterView

    /** 编辑第 index 行的方案 value */
    data class ValuePick(val index: Int) : SponsorFilterView

    /** 编辑第 index 行的累计金额（数字输入） */
    data class NumberInput(val index: Int) : SponsorFilterView

    /** 编辑第 index 行的赞助月份（年份 + 多月选择） */
    data class MonthPicker(val index: Int) : SponsorFilterView
}

/**
 * 筛选 Chip + 高级筛选面板：对齐网页「筛选档位」条件构建器（type/action/value 三段、
 * 多条件 AND、清除/确定），载体用 WindowListPopup 而非 dialog。
 *
 * 双层 popup 级联：第一层条件构建主页锚 Chip；点条件行任一段时，从**该段自身**叠弹
 * 第二层选择视图（子 popup 组合在主页内容的段 Box 内，主页与子层均为全屏透明 Dialog、
 * 坐标系对齐，故段级锚点正确），选完只关第二层回主页；点外先关第二层、再关主页。
 * 子层关闭蒙层（主页已有）避免叠加过暗。草稿/视图状态提升在主页之外，打开时从 [applied] 重置。
 *
 * @param summary Chip 展示文案（[filterSummary]）
 * @param active 是否有生效条件（Chip 高亮）
 * @param applied 已应用条件（打开时初始化草稿）
 * @param plans 方案选项（all-plans 拉取；失败为空时方案段只剩「全部」）
 * @param onApply 「确定/清除」提交（完整条件行列表；清除传空）
 */
@Composable
private fun SponsorFilterChip(
    summary: String,
    active: Boolean,
    applied: List<SponsorFilterRow>,
    plans: List<SponsorFilterPlan>,
    onClick: () -> Unit,
    showPopup: Boolean,
    onDismissPopup: () -> Unit,
    onApply: (List<SponsorFilterRow>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 草稿与子视图状态：提升在主页 popup 之外（段级子 popup 在主页内容内读取）
    var draft by remember { mutableStateOf(applied.ifEmpty { listOf(SponsorFilterRow()) }) }
    var view by remember { mutableStateOf<SponsorFilterView>(SponsorFilterView.Builder) }
    // 打开主页时重置：草稿取已应用条件，子视图回主页
    LaunchedEffect(showPopup) {
        if (showPopup) {
            draft = applied.ifEmpty { listOf(SponsorFilterRow()) }
            view = SponsorFilterView.Builder
        }
    }
    // 更新第 index 行草稿
    fun updateRow(index: Int, transform: (SponsorFilterRow) -> SponsorFilterRow) {
        draft = draft.mapIndexed { i, row -> if (i == index) transform(row) else row }
    }
    // 子视图内容：由各段的段级 popup 按当前 [view] 取用（选完/返回把 view 收回主页即收子层）
    val pickerContent: @Composable (SponsorFilterView) -> Unit = { current ->
        when (current) {
            is SponsorFilterView.TypePick -> FilterOptionPicker(
                title = "筛选类型",
                options = SponsorFilterType.entries.map { it.title },
                selectedIndex = SponsorFilterType.entries.indexOf(draft[current.index].type),
                onSelected = { i ->
                    val newType = SponsorFilterType.entries[i]
                    updateRow(current.index) { row ->
                        // 切类型后修正 action，保证落在新类型的可用集合内
                        val fixed = if (row.action in newType.actions) row.action else newType.actions.first()
                        row.copy(type = newType, action = fixed)
                    }
                    view = SponsorFilterView.Builder
                },
            )

            is SponsorFilterView.ActionPick -> {
                val row = draft[current.index]
                FilterOptionPicker(
                    title = "条件动作",
                    options = row.type.actions.map { it.titleFor(row.type) },
                    selectedIndex = row.type.actions.indexOf(row.action),
                    onSelected = { i ->
                        val newAction = row.type.actions[i]
                        updateRow(current.index) { it.copy(action = newAction) }
                        view = SponsorFilterView.Builder
                    },
                )
            }

            is SponsorFilterView.ValuePick -> FilterValuePicker(
                row = draft[current.index],
                plans = plans,
                onSelected = { plan ->
                    // plan = null 即「全部」（该行不生效）
                    updateRow(current.index) { it.copy(plan = plan) }
                    view = SponsorFilterView.Builder
                },
            )

            is SponsorFilterView.NumberInput -> FilterNumberInput(
                row = draft[current.index],
                onValueChange = { number -> updateRow(current.index) { it.copy(number = number) } },
                onDone = { view = SponsorFilterView.Builder },
            )

            is SponsorFilterView.MonthPicker -> FilterMonthPicker(
                row = draft[current.index],
                onMonthsChange = { months -> updateRow(current.index) { it.copy(months = months) } },
                onDone = { view = SponsorFilterView.Builder },
            )

            // 段级 popup 只在子视图激活时组合，此分支不可达
            is SponsorFilterView.Builder -> {}
        }
    }

    Box(modifier = modifier) {
        // 筛选 Chip：有生效条件时高亮为主色容器
        FilterChip(
            text = summary,
            selected = active,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )

        // 条件构建主页：锚 Chip 左下角；段级子 popup 见 FilterBuilderView
        // 注意：不要用 popupModifier 加 padding 当卡片边距——它作用于全屏定位容器，
        // 会与 anchor 的 window 坐标叠加导致左距双倍、右距被 coerce 顶满（左 32/右 0）。
        // 卡片边距 = 内容 widthIn 限宽（左右各 16）+ 固定左缘定位器（见 StartMarginPositionProvider）。
        WindowListPopup(
            show = showPopup,
            alignment = PopupPositionProvider.Align.BottomStart,
            onDismissRequest = onDismissPopup,
            // 条件行三段需要排布空间，固定最小宽度
            minWidth = 320.dp,
            // 横向左缘固定 16dp、纵向沿用 dropdown 锚定：脱离 anchor.x 换算的不确定性
            popupPositionProvider = startMarginPositionProvider,
        ) {
            val dismiss = LocalDismissState.current
            FilterBuilderView(
                draft = draft,
                view = view,
                onDismissPicker = { view = SponsorFilterView.Builder },
                pickerContent = pickerContent,
                onTypeClick = { view = SponsorFilterView.TypePick(it) },
                onActionClick = { view = SponsorFilterView.ActionPick(it) },
                onValueClick = { index ->
                    // 按类型分流：方案列表 / 金额数字输入 / 年月多选
                    view = when (val type = draft[index].type) {
                        SponsorFilterType.Sponsoring, SponsorFilterType.Sponsored ->
                            SponsorFilterView.ValuePick(index)
                        SponsorFilterType.Month -> SponsorFilterView.MonthPicker(index)
                        SponsorFilterType.Amount -> SponsorFilterView.NumberInput(index)
                    }
                },
                onDelete = { index ->
                    draft = draft.filterIndexed { i, _ -> i != index }
                },
                onAdd = { draft = draft + SponsorFilterRow() },
                onClear = {
                    onApply(emptyList())
                    dismiss?.invoke()
                },
                onConfirm = {
                    // 只提交完整条件；未完成行等价官方「全部/空值」不生效
                    onApply(draft.filter { it.isComplete })
                    dismiss?.invoke()
                },
            )
        }
    }
}

/**
 * 筛选面板主页：条件行列表（type/action/value 三段 + 删除）+ 新增 + 清除/确定。
 * 视觉对齐网页条件构建器，段用 FilterChip 承担点按反馈。
 * 每段自带一个段级 WindowListPopup（组合在段 Box 内，从该段下方弹出叠在主页上），
 * show 由 [view] 是否指向「当前段」驱动；点外/选完只收子层（[onDismissPicker]），
 * [pickerContent] 提供子层具体内容（由外层闭包草稿更新逻辑）。
 */
@Composable
private fun FilterBuilderView(
    draft: List<SponsorFilterRow>,
    view: SponsorFilterView,
    onDismissPicker: () -> Unit,
    pickerContent: @Composable (SponsorFilterView) -> Unit,
    onTypeClick: (Int) -> Unit,
    onActionClick: (Int) -> Unit,
    onValueClick: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // 卡片限宽 = 容器宽 - 32：内容不撑满屏幕，卡片自然左右各留 16 边距（锚 chip 不受干扰）
    val cardMaxWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } - 32.dp

    Column(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 面板标题
        Text(
            text = "筛选条件",
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(start = 8.dp)
        )

        draft.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // type 段 + 段级选择 popup（锚定本段）
                Box {
                    FilterChip(
                        text = row.type.title,
                        selected = false,
                        onClick = { onTypeClick(index) },
                    )
                    WindowListPopup(
                        show = (view as? SponsorFilterView.TypePick)?.index == index,
                        onDismissRequest = onDismissPicker,
                    ) {
                        pickerContent(view)
                    }
                }
                // action 段 + 段级选择 popup
                Box {
                    FilterChip(
                        text = row.action.titleFor(row.type),
                        selected = false,
                        onClick = { onActionClick(index) },
                    )
                    WindowListPopup(
                        show = (view as? SponsorFilterView.ActionPick)?.index == index,
                        onDismissRequest = onDismissPicker,
                    ) {
                        pickerContent(view)
                    }
                }
                // value 段：吃满剩余宽度，长方案名省略；方案/金额/月份三种子视图共用本段 popup
                Box(modifier = Modifier.weight(1f)) {
                    FilterChip(
                        text = row.valueLabel,
                        selected = false,
                        onClick = { onValueClick(index) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    WindowListPopup(
                        show = (view as? SponsorFilterView.ValuePick)?.index == index ||
                            (view as? SponsorFilterView.NumberInput)?.index == index ||
                            (view as? SponsorFilterView.MonthPicker)?.index == index,
                        onDismissRequest = onDismissPicker,
                    ) {
                        pickerContent(view)
                    }
                }
                // 删除该条件行；至少保留一行时不显示
                if (draft.size > 1) {
                    IconButton(
                        onClick = { onDelete(index) },
                        backgroundColor = MiuixTheme.colorScheme.secondary,
                    ) {
                        Icon(MiuixIcons.Close, "删除筛选条件")
                    }
                }
            }
        }

        // 新增筛选项（达到上限隐藏）
        if (draft.size < FILTER_MAX_ROWS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAdd()
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "＋ 新增筛选项",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }

        // 操作：清除（次要）+ 确定（主色）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "清除",
                onClick = onClear,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text("确定")
            }
        }
    }
}

/**
 * 通用单选选择视图（type/action 共用）：顶部 disabled 纯标题行 + 选项列表（DropdownImpl 打勾）。
 * 选中任一项即回主页（由调用方切换视图）；点外关闭同样回主页。
 */
@Composable
private fun FilterOptionPicker(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val optionCount = options.size + 1

    ListPopupColumn {
        Text(       // 顶部标题行：仅作分区说明，不可点击
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp)
        )
        HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp))
        options.forEachIndexed { index, label ->
            DropdownImpl(
                text = label,
                optionSize = optionCount,
                isSelected = index == selectedIndex,
                index = index + 1,
                onSelectedIndexChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSelected(index)
                },
            )
        }
    }
}

/** 方案 value 选择视图：顶部 disabled 纯标题 +「全部」（该行不生效）+ 各方案列表。 */
@Composable
private fun FilterValuePicker(
    row: SponsorFilterRow,
    plans: List<SponsorFilterPlan>,
    onSelected: (SponsorFilterPlan?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // 标题行 + 「全部」+ 各方案
    val optionCount = plans.size + 2

    ListPopupColumn {
        Text(       // 顶部标题行：仅作分区说明，不可点击
            text = "方案",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp)
        )
        HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp))
        DropdownImpl(
            text = "全部",
            optionSize = optionCount,
            isSelected = row.plan == null,
            index = 1,
            onSelectedIndexChange = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onSelected(null)
            },
        )
        plans.forEachIndexed { index, plan ->
            DropdownImpl(
                text = plan.display,
                optionSize = optionCount,
                isSelected = row.plan?.planId == plan.planId,
                index = index + 2,
                onSelectedIndexChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSelected(plan)
                },
            )
        }
    }
}

/**
 * 数值 value 输入视图：数字输入 + 完成。
 * InputField 不暴露 keyboardOptions，输入回调里过滤仅保留数字与单个小数点。
 */
@Composable
private fun FilterNumberInput(
    row: SponsorFilterRow,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    // 与主页同口径限宽，卡片同样左右各留 16 边距
    val cardMaxWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } - 32.dp

    Column(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 输入框：去掉默认搜索前导图标
        InputField(
            query = row.number,
            onQueryChange = { input ->
                val digits = input.filter { it.isDigit() || it == '.' }
                // 仅保留第一个小数点
                val filtered = digits.indexOf('.').let { dot ->
                    if (dot < 0) digits
                    else digits.substring(0, dot + 1) + digits.substring(dot + 1).replace(".", "")
                }
                onValueChange(filtered)
            },
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            label = "输入数值",
            leadingIcon = {},
        )
        // 完成：回条件构建主页（数值已实时写入草稿）
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("完成")
        }
    }
}

/**
 * 赞助月份选择视图：通用 [YearMonthField] 年/月滚轮（常显，无日与时分），
 * 点「添加」并入条件；已选项以「20xx年n月」chip 展示（尾随 Demibold Close，点按移除），
 * 「完成」回主页。值格式 YYYYMM 降序；同一月份重复添加会被忽略。
 */
@Composable
private fun FilterMonthPicker(
    row: SponsorFilterRow,
    onMonthsChange: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    // 与主页同口径限宽，卡片同样左右各留边距
    val cardMaxWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } - 32.dp
    val zone = remember { ZoneId.systemDefault() }
    // 待添加年月：默认取已选第一项（最新），否则今天
    val defaultYearMonth = row.months.firstOrNull()?.let { yyyymm ->
        val year = yyyymm.take(4).toIntOrNull()
        val month = yyyymm.drop(4).toIntOrNull()?.coerceIn(1, 12)
        if (year != null && month != null) year to month else null
    } ?: Instant.now().atZone(zone).toLocalDate().let { it.year to it.monthValue }
    var pendingYear by remember { mutableStateOf(defaultYearMonth.first) }
    var pendingMonth by remember { mutableStateOf(defaultYearMonth.second) }

    // 「添加」：当前年月拼 YYYYMM，去重后并入并保持降序（新月份在前）
    fun addPending() {
        val yyyymm = pendingYear.toString() + pendingMonth.toString().padStart(2, '0')
        if (yyyymm !in row.months) {
            onMonthsChange((row.months + yyyymm).sortedDescending())
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = cardMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 通用年月滚轮：常显、只选年与月
        YearMonthField(
            label = "选择月份",
            year = pendingYear,
            month = pendingMonth,
            onYearMonthChange = { year, month ->
                pendingYear = year
                pendingMonth = month
            },
        )
        // 已选月份 chips：FlowRow 自动换行；文本「20xx年n月」，尾随 Demibold Close，点按移除
        if (row.months.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.months.forEach { yyyymm ->
                    FilterChip(
                        text = monthDisplayName(yyyymm),
                        selected = true,
                        trailingIcon = MiuixIcons.Demibold.Close,
                        onClick = { onMonthsChange(row.months - yyyymm) },
                    )
                }
            }
        }
        // 底部操作：添加（并入当前年月）+ 完成（回主页），同行各占一半
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = ::addPending,
                modifier = Modifier.weight(1f),
            ) {
                Text("添加")
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f),
            ) {
                Text("完成")
            }
        }
    }
}

/**
 * 单条赞助者卡片：头像 + 昵称 / 当前方案 + 累计金额 / 最近赞助时间。
 * Card 只做容器（按压下沉），点击事件挂在内部行上。
 */
@Composable
private fun SponsorRow(
    item: SponsorManageItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.pressable(
            interactionSource = null,
            indication = SinkFeedback(),
            delay = null,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像
            Avatar(
                model = item.avatar,
                size = 44.dp,
                contentDescription = "${item.name} 的头像",
            )

            // 中间：昵称 + 当前方案（无生效方案显示「无」）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = item.name.ifBlank { "爱发电用户" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.currentPlanName ?: "无",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // 右侧：累计金额 + 最近赞助（相对时间，与网页口径一致）
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text(
                    text = "¥${item.allSumAmount}",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.primary,
                )
                if (item.lastPayTime > 0) {
                    Text(
                        text = relativeTime(item.lastPayTime * 1000),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 赞助者详情底部弹层：头部资料（含私信入口）+ 累计金额 + 两次赞助时间 + 赞助过的方案列表 + 查看主页。
 * 数据全部来自列表项，无额外网络请求。
 *
 * @param onOpenConversation 私信按钮回调（跳转私信会话由调用方组装）
 */
@Composable
private fun SponsorDetailSheet(
    show: Boolean,
    item: SponsorManageItem?,
    onDismiss: () -> Unit,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenConversation: (SponsorManageItem) -> Unit,
) {
    WindowBottomSheet(
        show = show,
        title = "赞助详情",
        onDismissRequest = onDismiss,
    ) {
        if (item == null) return@WindowBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 撑满 Sheet 可用高度，内容超高时 Sheet 内部滚动
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp)
                .scrollEndHaptic(),
        ) {
            // 头部：头像 + 昵称 + 当前方案
            Card(
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    // 头像
                    Avatar(
                        model = item.avatar,
                        size = 48.dp,
                        contentDescription = "${item.name} 的头像",
                    )
                // 昵称 + 当前方案：占满中间宽度，超长省略
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = item.name.ifBlank { "爱发电用户" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.currentPlanName?.let { "当前方案：$it" } ?: "当前方案：无",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 右侧：私信按钮
                IconButton(
                    onClick = { onOpenConversation(item) },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Community,
                        contentDescription = "给${item.name}发私信",
                    )
                }
                }
            }

            // 累计金额大卡
            DataCard(
                title = "累计金额",
                value = item.allSumAmount,
                unit = "元",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                // 卡片背景：surface
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
            )

            // 两次赞助时间：左标签右值
            Card(
                modifier = Modifier.fillMaxWidth(),
                // 卡片背景：surface
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    DetailTimeRow(label = "初次赞助", seconds = item.createTime)
                    HorizontalDivider()
                    DetailTimeRow(label = "最近赞助", seconds = item.lastPayTime)
                }
            }

            // 赞助过的方案列表（购买历史）
            if (item.plans.isNotEmpty()) {
                SmallTitle(
                    text = "赞助过的方案",
                    insideMargin = PaddingValues(16.dp, 16.dp, 8.dp, 8.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    // 卡片背景：surface
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        item.plans.forEachIndexed { index, plan ->
                            if (index > 0) HorizontalDivider()
                            PlanHistoryRow(planName = plan.name, price = plan.price, payMonth = plan.payMonth)
                        }
                    }
                }
            }

            // 查看主页：跳转赞助者个人页
            Button(
                onClick = { onOpenCreator(item.toAfadianUser()) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text("查看主页")
            }
        }
    }
}

/** 详情弹层里的时间行：左标签、右绝对时间。 */
@Composable
private fun DetailTimeRow(label: String, seconds: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = formatDetailTime(seconds),
            style = MiuixTheme.textStyles.body2,
        )
    }
}

/** 详情弹层里的单个方案行：左方案名、右价格。 */
@Composable
private fun PlanHistoryRow(planName: String, price: String, payMonth: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = planName.ifBlank { "发电方案" },
                style = MiuixTheme.textStyles.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 订阅类方案标注月数；售卖商品（通常 1 个月）不显示
            if (payMonth > 1) {
                Text(
                    text = "订阅 $payMonth 个月",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "¥$price",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary
        )
    }
}

/** 绝对时间展示格式：yyyy/MM/dd HH:mm（线程安全的 formatter 无状态复用）。 */
private val detailTimeFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

/** Unix 秒 → 展示文案；0（接口缺省）显示 “--”。 */
private fun formatDetailTime(seconds: Long): String =
    if (seconds <= 0L) "--" else detailTimeFormatter.format(Date(seconds * 1000))
