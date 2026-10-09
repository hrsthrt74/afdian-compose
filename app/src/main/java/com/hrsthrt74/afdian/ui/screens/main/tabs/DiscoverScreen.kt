package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.ui.components.AppChip
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.CreatorLargeCard
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.post.PostCard
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

@Composable
fun DiscoverScreen(
    viewModel: MainViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenPost: (RecommendedPost) -> Unit = {},
    // 本页是否为底部导航选中的页：用户点 navbar 切走时立即变 false，
    // 据此在切走瞬间关闭搜索面板（搜索激活时 pager 已被外壳禁滑，navbar 是唯一离开途径）
    isPageSelected: Boolean = true,
    // 搜索面板激活状态（展开中/展开/收起中）上报给外壳；外壳用它禁用 pager 横滑
    onSearchActiveChange: (Boolean) -> Unit = {},
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val posts by viewModel.posts.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val searchLoadingMore by viewModel.searchLoadingMore.collectAsState()
    val searchHasMore by viewModel.searchHasMore.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val searchHistoryEnabled by viewModel.searchHistoryEnabled.collectAsState()
    val listState = rememberLazyListState()
    var searchStatus by remember { mutableStateOf(DiscoverSearchStatus()) }
    var searchOffsetY by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val dynamicTopPadding = { 1.dp * (1f - scrollBehavior.state.collapsedFraction) }

    // 搜索栏独立收缩进度：0 = 完整展示，1 = 高度收归 0。
    // 分三段串行，参考 TopAppBar 的折叠写法（各自连接吃各自的滚动预算）：
    //   ① 先收搜索框（本连接，约 52dp 行程）→ ② 再收大标题（scrollBehavior，约 40dp）
    //   → ③ 最后才真正滚动列表内容（前两者吃完才轮到 child）。
    // 若共用 collapsedFraction，52dp 的搜索栏高度会被挤进 40dp 的标题行程，
    // contentPadding 收缩速率超过 1:1，内容会比手指更快地飞出去（滚动变快的根源）。
    // 必须 rememberSaveable：miuix 的 rememberTopAppBarState（内部 saveable）和
    // rememberLazyListState 在组合销毁重建（跳走再回来、Activity 重建）后都会恢复，
    // 本进度若用普通 remember 会归零，出现「顶栏仍折叠、搜索栏却弹回未折叠」的脱节。
    var searchFraction by rememberSaveable { mutableStateOf(0f) }
    // 搜索栏收缩的滚动预算 = 自然高度（45 胶囊 + 6 底距 + 1 顶距）：滚动多少收多少，手感 1:1
    val searchCollapseRangePx = with(density) { 52.dp.toPx() }
    // 分段收拢连接（nestedScroll 链序：pre 外→内、post 内→外，本连接放外层/最前）：
    // - 收拢 pre：本连接先收搜索框，剩余才轮到 scrollBehavior 收大标题，最后才到列表 → 顺序①②③；
    // - 展开 post：内层先展开大标题，剩余再到本连接长回搜索框 → 收拢的严格逆序（栈式）；
    // - postFling：按松手动量方向吸附到 0/1，避免半截卡住或反向回弹。
    val searchCollapseConnection = remember(searchCollapseRangePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 只处理向上收拢；展开方向由 onPostScroll 接手
                if (available.y >= 0f) return Offset.Zero
                val remaining = (1f - searchFraction) * searchCollapseRangePx
                val used = (-available.y).coerceAtMost(remaining)
                if (used > 0f) searchFraction += used / searchCollapseRangePx
                // 关键：consumed 必须与 available 同号（向上为负）。
                // 父链扣减是 available - parentConsumed：若这里返回正数，
                // 外层 scrollBehavior 会拿到 -δ-(+δ)=-2δ 的双倍增量，标题跟着双倍收，
                // contentPadding 单帧缩 3 倍 → 折叠时「列表滚太快」的真凶。
                return Offset(0f, -used)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0f) {
                    // 向下滚动的剩余量（列表已在顶部、标题已由内层展开完）：把搜索框长回去
                    val remaining = searchFraction * searchCollapseRangePx
                    val used = available.y.coerceAtMost(remaining)
                    if (used > 0f) searchFraction -= used / searchCollapseRangePx
                    return Offset(0f, used)
                }
                if (available.y < 0f) {
                    // 漏到 post 的向上余量（如列表滚到尽头的边界），继续喂给搜索框收拢
                    val remaining = (1f - searchFraction) * searchCollapseRangePx
                    val used = (-available.y).coerceAtMost(remaining)
                    if (used > 0f) searchFraction += used / searchCollapseRangePx
                    // 同 onPreScroll：consumed 与 available 同号（负），否则父链扣减会翻倍
                    return Offset(0f, -used)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val fraction = searchFraction
                // 没停在半路不用吸；本段收拢时大标题尚未动（offset = 0），不会打扰标题自己的 settle
                if (fraction <= 0.001f || fraction >= 0.999f) return Velocity.Zero
                // 按松手动量方向吸附：上甩顺势收完、下拉顺势展开、慢放就近吸附 → 不会反向回弹
                val target = when {
                    available.y < -1f -> 1f
                    available.y > 1f -> 0f
                    else -> if (fraction < 0.5f) 0f else 1f
                }
                animate(
                    initialValue = fraction,
                    targetValue = target,
                    animationSpec = spring(stiffness = 2500f),
                ) { value, _ ->
                    searchFraction = value
                }
                return Velocity(0f, available.y)
            }
        }
    }

    LoadMoreEffect(listState, hasMore, isLoadingMore) { viewModel.loadMore() }

    // 搜索激活状态上报：任一非 COLLAPSED 相位都算激活，外壳据此禁用 pager 横滑。
    // 相位每次变化都会触发；重复写同值布尔对外壳无重组开销。组合重建后本 effect
    // 以重置的 COLLAPSED 初值自愈上报 false，不会让外壳的禁滑残留。
    LaunchedEffect(searchStatus.current) {
        onSearchActiveChange(!searchStatus.isCollapsed())
    }

    // 底部导航切到其他 tab（本页不再选中）：面板已不可见，无需播放收起动画，
    // 直接归位关闭；并清空在途搜索（与「取消」按钮同一组清理），回来时是干净列表。
    // 搜索激活时 pager 已禁滑，点 navbar 是唯一切页途径，这里必定能拦到。
    LaunchedEffect(isPageSelected) {
        if (!isPageSelected && !searchStatus.isCollapsed()) {
            AppLog.d("DiscoverScreen", "页面被切走，取消搜索状态")
            viewModel.searchCreators("")
            searchStatus = DiscoverSearchStatus()
        }
    }

    Scaffold(
        topBar = {
            DoubleTapToTop(listState, scrollBehavior, enabled = searchStatus.isCollapsed()) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "发现",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            SearchFake(
                                label = "搜索创作者",
                                topPadding = dynamicTopPadding,
                                // 生产者 lambda：进度在布局/绘制阶段读取，滚动时本组件不重组（同 miuix 大标题写法）
                                fraction = { searchFraction },
                                visible = searchStatus.isCollapsed(),
                                onClick = { searchStatus = searchStatus.copy(current = SearchPhase.EXPANDING) },
                                onPositioned = { coordinates ->
                                    with(density) { searchOffsetY = coordinates.positionInWindow().y.toDp() }
                                },
                            )
                        },
                    )
                }
            }
        },
        popupHost = {
            SearchPager(
                status = searchStatus,
                offsetY = searchOffsetY,
                onStatusChange = { searchStatus = it },
                viewModel = viewModel,
                onOpenCreator = onOpenCreator,
                searchResults = searchResults,
                searchLoading = searchLoading,
                searchLoadingMore = searchLoadingMore,
                searchHasMore = searchHasMore,
                searchError = searchError,
                searchHistory = searchHistory,
                searchHistoryEnabled = searchHistoryEnabled,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
            ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    // 链序决定分段顺序（pre 外→内）：搜索框连接在最外 → 先收搜索框，
                    // scrollBehavior 在内 → 再收大标题，最后剩的才滚动列表内容
                    .nestedScroll(searchCollapseConnection)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding(),
                ),
            ) {
                item { SmallTitle(text = "全站精选", modifier = Modifier.padding(start = 4.dp)) }
                if (error != null) {
                    item {
                        ErrorStateView(
                            onRetry = viewModel::refresh,
                            modifier = Modifier.fillParentMaxSize(),
                            summary = error!!,
                        )
                    }
                } else if (posts.isEmpty()) {
                    item {
                        if (loading) LoadingComponent(modifier = Modifier.padding(horizontal = 12.dp))
                        else Text("暂无精选内容", modifier = Modifier.padding(horizontal = 12.dp))
                    }
                } else {
                    items(posts, key = { it.id }) { post ->
                        val origin = rememberCreatorOrigin()
                        PostCard(
                            post = post,
                            profile = post.user,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            onClick = { onOpenPost(post) },
                            onCreatorClick = origin.onClick { onOpenCreator(post.user) },
                            showDoing = true,
                            creatorOrigin = origin,
                        )
                    }
                    if (isLoadingMore) item { LoadingComponent(modifier = Modifier.padding(vertical = 16.dp)) }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
    }
}
}

private enum class SearchPhase { COLLAPSED, EXPANDING, EXPANDED, COLLAPSING }

/**
 * 假搜索栏仍允许点击的折叠进度上限（0 = 完整展示，1 = 高度收归 0）。
 *
 * 取 0.05f 而不是 0f 的原因：
 * 1. `searchFraction` 由 nestedScroll 里 `+=` / `-=` 累加，完全展开时可能残留 1e-7 级浮点尾数，
 *    用 `== 0f` 判定会出现「明明完整展开却点不动」；
 * 2. 0.05 对应的实测高度约 49dp，仍 ≥ 48dp 最小触摸目标，天然不落在会外扩 24dp 的区间里，
 *    所以这个阈值内的点击都是「真实落在可见胶囊上」的，不会误放行。
 */
private const val SearchFakeTapFractionThreshold = 0.05f

private data class DiscoverSearchStatus(
    val text: String = "",
    val current: SearchPhase = SearchPhase.COLLAPSED,
) {
    fun isCollapsed() = current == SearchPhase.COLLAPSED
    fun shouldExpand() = current == SearchPhase.EXPANDED || current == SearchPhase.EXPANDING
    fun onAnimationComplete(): DiscoverSearchStatus = when (current) {
        SearchPhase.EXPANDING -> copy(current = SearchPhase.EXPANDED)
        SearchPhase.COLLAPSING -> copy(text = "", current = SearchPhase.COLLAPSED)
        else -> this
    }
}

@Composable
private fun SearchFake(
    label: String,
    topPadding: () -> Dp,
    fraction: () -> Float,
    visible: Boolean,
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    Column(
        modifier = Modifier
            .alpha(if (visible) 1f else 0f)
            .fillMaxWidth()
            .onGloballyPositioned(onPositioned)
            // 注意：能否点击只能由这里的 key 决定，而 key 只含搜索面板相位（COLLAPSED/…），
            // 不含滚动折叠进度——所以「折叠到 0 高度后仍可点」的问题不能靠 key 解决：
            // 折叠是用下面的 .layout{} 把高度压成 0 实现的，而高度为 0 时 Compose 的
            // 最小触摸目标仍会把命中区向上/下各外扩 (48dp - 0) / 2 = 24dp 并记入命中路径，
            // 于是这个 pointerInput 照样收得到事件。因此把「折叠进度」判定搬进手势回调内。
            .pointerInput(visible) {
                if (visible) detectTapGestures {
                    // fraction 是生产者 lambda（读的 MutableFloatState.value），
                    // 在事件里调用即取最新进度，滚动过程不重组也拿得到准数。
                    if (fraction() > SearchFakeTapFractionThreshold) {
                        // 胶囊已被滚动压小（含完全折叠）：拦掉这次点击。
                        // 否则折叠后点「发现」小标题附近也会误弹搜索面板
                        AppLog.d("DiscoverScreen", "SearchFake 折叠中，忽略点击 fraction=${fraction()}")
                    } else {
                        onClick()
                    }
                }
            }
            .padding(horizontal = 16.dp)
            // 对齐 miuix 大标题 offset{} 的写法：布局阶段读收缩进度，直接把组件
            // 高度量成「自然高度 × (1 - 进度)」——最矮精确到 0、改的是真实布局高度，
            // 不 scaleY 压缩、不需要 clip；滚动只触发布局测量，不触发本组件重组。
            .layout { measurable, constraints ->
                val naturalPx = SearchBarDefaults.InputFieldMinHeight.toPx() +
                    6.dp.toPx() + topPadding().toPx()
                val heightPx = (naturalPx * (1f - fraction().coerceIn(0f, 1f))).roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = heightPx, maxHeight = heightPx),
                )
                layout(placeable.width, heightPx) { placeable.placeRelative(0, 0) }
            },
    ) {
        // 假搜索胶囊：撑满 SearchFake 剩余高度的圆角底，随高度收缩自然变成细条再归零。
        // 用自绘假栏而非 InputField：后者内部写死 heightIn(min = 45.dp)，强压会溢出。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = topPadding(), bottom = 6.dp)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh, Capsule()),
            contentAlignment = Alignment.Center,
        ) {
            // 放大镜 + 占位文案行（假搜索栏内容）
            Row(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    // 绘制阶段读进度：胶囊高度低于 30dp 前内容整体淡出，保证不越界、无需裁切
                    val naturalPx = SearchBarDefaults.InputFieldMinHeight.toPx() +
                        6.dp.toPx() + topPadding().toPx()
                    val capsulePx = naturalPx * (1f - fraction().coerceIn(0f, 1f)) -
                        6.dp.toPx() - topPadding().toPx()
                    alpha = ((capsulePx - 30.dp.toPx()) / 15.dp.toPx()).coerceIn(0f, 1f)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 放大镜图标
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                )
                // 占位文案
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = SearchBarDefaults.InputFieldFontSize,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                )
            }
        }
    }
}

@Composable
private fun SearchPager(
    status: DiscoverSearchStatus,
    offsetY: Dp,
    onStatusChange: (DiscoverSearchStatus) -> Unit,
    viewModel: MainViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    searchResults: List<AfadianUser>,
    searchLoading: Boolean,
    searchLoadingMore: Boolean,
    searchHasMore: Boolean,
    searchError: String?,
    searchHistory: List<String>,
    searchHistoryEnabled: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val resultsListState = rememberLazyListState()
    var deletingHistory by remember { mutableStateOf<String?>(null) }
    val showResultsFade by remember {
        derivedStateOf {
            resultsListState.firstVisibleItemIndex > 0 ||
                resultsListState.firstVisibleItemScrollOffset > 0
        }
    }
    val resultsFadeAlpha by animateFloatAsState(
        targetValue = if (showResultsFade) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "discoverResultsFadeAlpha",
    )
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val topInset by animateDpAsState(
        targetValue = if (status.shouldExpand()) systemBarsPadding + 12.dp else maxOf(offsetY, 0.dp),
        animationSpec = tween(300, easing = LinearOutSlowInEasing),
        label = "discoverSearchTopInset",
        finishedListener = {
            onStatusChange(status.onAnimationComplete())
        },
    )
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (status.shouldExpand()) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "discoverSearchSurfaceAlpha",
    )
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(status.current) {
        if (status.current == SearchPhase.EXPANDED) focusRequester.requestFocus()
    }

    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = !status.isCollapsed(),
        onBackCompleted = {
            onStatusChange(status.copy(current = SearchPhase.COLLAPSING))
        },
    )

    if (!status.isCollapsed()) {
        Box(
            modifier = Modifier.fillMaxSize().zIndex(5f)
                .background(MiuixTheme.colorScheme.surface.copy(alpha = surfaceAlpha))
                .clickable(interactionSource = null, indication = null) { }, // 吃掉点击透传
        ) {
            Column(Modifier.fillMaxWidth().padding(top = topInset)) {
                Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InputField(
                            query = status.text,
                            onQueryChange = {
                                onStatusChange(status.copy(text = it))
                                deletingHistory = null
                                viewModel.searchCreators(it)
                            },
                            onSearch = {},
                            label = "搜索创作者",
                            expanded = status.shouldExpand(),
                            onExpandedChange = {},
                            modifier = Modifier
                                .weight(1f)
                                .clip(Capsule())
                                .padding(horizontal = 16.dp)
                                .focusRequester(focusRequester),
                        )
                        AnimatedVisibility(
                            // 官方 SearchPager 在 EXPANDING 阶段就显示取消按钮，避免等待顶部位移动画结束。
                            visible = status.current == SearchPhase.EXPANDED ||
                                status.current == SearchPhase.EXPANDING,
                            enter = expandHorizontally() + slideInHorizontally { it },
                            exit = shrinkHorizontally() + slideOutHorizontally { it },
                        ) {
                            Text(
                                text = "取消",
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 16.dp).clickable(
                                    interactionSource = null,
                                    indication = null,
                                    enabled = status.current == SearchPhase.EXPANDED,
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    deletingHistory = null
                                    viewModel.searchCreators("")
                                    onStatusChange(status.copy(text = "", current = SearchPhase.COLLAPSING))
                                },
                            )
                    }
                }
                AnimatedVisibility(
                    visible = status.current == SearchPhase.EXPANDED,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    when {
                        status.text.isBlank() -> {
                            if (!searchHistoryEnabled) {
                                Text(
                                    text = "搜索历史已关闭",
                                    textAlign = TextAlign.Center,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SmallTitle(
                                            text = "最近搜索",
                                            modifier = Modifier.weight(1f),
                                        )
                                        Row(
                                            modifier = Modifier
                                                .then(
                                                    if (searchHistory.isNotEmpty()) {
                                                        Modifier.clickable(
                                                            interactionSource = null,
                                                            indication = null,
                                                        ) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                                            viewModel.clearSearchHistory()
                                                        }
                                                    } else {
                                                        Modifier
                                                    },
                                                )
                                                .padding(horizontal = 28.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Demibold.Delete,
                                                contentDescription = "删除搜索记录",
                                                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = "删除记录",
                                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                                style = MiuixTheme.textStyles.subtitle,
                                                modifier = Modifier.padding(start = 4.dp),
                                            )
                                        }
                                    }
                                    if (searchHistory.isEmpty()) {
                                        Text(
                                            text = "还没有记录",
                                            textAlign = TextAlign.Center,
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                                        )
                                    } else {
                                        FlowRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            searchHistory.forEach { query ->
                                                AppChip(
                                                    text = query,
                                                    selected = deletingHistory == query,
                                                    trailingIcon = if (deletingHistory == query) {
                                                        MiuixIcons.Demibold.Close
                                                    } else {
                                                        null
                                                    },
                                                    onClick = {
                                                        onStatusChange(status.copy(text = query))
                                                        viewModel.searchCreators(query)
                                                        deletingHistory = null
                                                    },
                                                    onLongClick = { deletingHistory = query },
                                                    onTrailingIconClick = {
                                                        viewModel.removeSearchHistory(query)
                                                        deletingHistory = null
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        searchLoading -> {
                            LoadingComponent(
                                text = "正在搜索…",
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        searchError != null -> {
                            Text(
                                text = searchError,
                                textAlign = TextAlign.Center,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        searchResults.isEmpty() -> {
                            Text(
                                text = "没有找到匹配的创作者",
                                textAlign = TextAlign.Center,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                    if (
                        status.text.isNotBlank() &&
                        !searchLoading &&
                        searchError == null &&
                        searchResults.isNotEmpty()
                    ) {
                        LoadMoreEffect(
                            listState = resultsListState,
                            hasMore = searchHasMore,
                            isLoadingMore = searchLoadingMore,
                        ) {
                            viewModel.loadMoreSearchCreators(status.text)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(
                                state = resultsListState,
                                modifier = Modifier.fillMaxSize().scrollEndHaptic(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = 16.dp,
                                    end = 16.dp,
                                    bottom = contentBottomPadding(),
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(searchResults, key = { it.user_id }) { creator ->
                                    var itemVisible by remember { mutableStateOf(false) }
                                    val itemAlpha by animateFloatAsState(
                                        targetValue = if (itemVisible) 1f else 0f,
                                        animationSpec = tween(
                                            durationMillis = 260,
                                            easing = FastOutSlowInEasing,
                                        ),
                                        label = "searchResultCardAlpha",
                                    )
                                    LaunchedEffect(Unit) { itemVisible = true }
                                    CreatorLargeCard(
                                        avatarUrl = creator.avatar,
                                        coverUrl = creator.cover,
                                        creatorName = creator.name,
                                        categoryName = creator.creator?.category?.name,
                                        doing = creator.creator?.doing,
                                        onClick = {
                                            onOpenCreator(creator)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(itemAlpha),
                                    )
                                }
                            if (searchLoadingMore) {
                                item {
                                    LoadingComponent(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                    )
                                }
                            }
                            if (!searchLoadingMore && !searchHasMore) {
                                item(key = "search-no-more") {
                                    Text(
                                        text = "没有更多了",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                    )
                                }
                            }
                            if (!searchLoadingMore) {
                                item(key = "search-auth-tip") {
                                    Text(
                                        text = "创作者本人认证后才可被搜索",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .align(Alignment.TopCenter)
                                .alpha(resultsFadeAlpha)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MiuixTheme.colorScheme.surface,
                                            MiuixTheme.colorScheme.surface.copy(alpha = 0f),
                                        ),
                                    ),
                                ),
                        )
                        }
                    }
                }
            }
        }
    }
}
