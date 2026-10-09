package com.hrsthrt74.afdian.ui.screens.main.account

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.CreatorListItem
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.crowdfunding.CrowdfundingCard
import com.hrsthrt74.afdian.ui.components.post.PostCard
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.FavoritesTabState
import com.hrsthrt74.afdian.ui.viewmodel.FavoritesViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 收藏页 Tab 标题：下标与 [FavoritesViewModel.kind] 一一对应（0=创作者 / 1=动态 / 2=众筹）。
 *
 * 提为顶层常量：TabRow 与「每个 Tab 一份滚动状态」的映射都依赖它，
 * 放进可组合函数里每次重组都会重新分配列表，`remember` 里的 key 集合也就不稳定了。
 */
private val FAVORITES_TABS = listOf("创作者", "动态", "众筹")

/** 日志 TAG */
private const val FavoritesScreenLogTag = "Favorites"

/**
 * 我的收藏页：TabRow 三个分区（创作者 / 动态 / 众筹）。
 *
 * 切 tab 有「售卖商品 / 我的订单」同款转场（淡入淡出 + 1/8 幅度的横向位移）+ 点击轻震动，
 * 每个 tab 各持一份滚动状态、进入时统一回顶（详见 listStates 处的说明）。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenPost: (RecommendedPost) -> Unit = {},
    backStack: NavBackStack,
    onBack: () -> Unit,
) {
    // 数据：每个 tab 一份列表状态，转场中新旧两页各读各的
    val kind by viewModel.kind.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    // 屏幕
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    // Tab 切换的轻震动：TextHandleMove 档位，轻量确认「切换发生了」
    val haptic = LocalHapticFeedback.current

    // 每个 tab 一份独立的滚动状态。
    //
    // 不能共用一个：切 tab 用了 AnimatedContent，转场期间新旧两页会**同时组合**，
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
        FAVORITES_TABS.indices.associateWith { LazyListState() }
    }
    // 当前 tab 的滚动状态：顶栏「双击回顶」与右侧滚动条读这一份
    val listState = listStates.getValue(kind)

    // 切 tab 回顶：进入某个 tab 时把该 tab 自己的滚动位置归零。
    // 只动「进来的这一页」，转场中正在退场的旧页保持原位置淡出。
    LaunchedEffect(kind) {
        AppLog.d(FavoritesScreenLogTag, "tab switched -> $kind, scroll list to top")
        listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, false) {
                    Column{
                        TopAppBar(
                            title = "我的收藏",
                            largeTitle = "我的收藏",
                            color = blurBarColor(backdrop),
                            scrollBehavior = scrollBehavior,
                            navigationIcon = { BackButton(onBack = onBack, backStack = backStack) }
                        )
                        TabRow(
                            tabs = FAVORITES_TABS,
                            selectedTabIndex = kind,
                            onTabSelected = { index ->
                                // 先震后切：点击的即时反馈不等转场动画
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.select(index)
                            },
                            colors = TabRowDefaults.tabRowColors(Color.Transparent),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                // 切 tab 时整个列表做淡入淡出 + 小幅平移：三个分区的卡片长得像，
                // 纯切换会让人以为没切成功
                AnimatedContent(
                    targetState = kind,
                    transitionSpec = {
                        // 按 FAVORITES_TABS 顺序决定平移方向：切到更靠后的 tab 从右滑入，反之从左
                        val forward = targetState > initialState
                        (fadeIn(animationSpec = tween(220)) +
                            slideInHorizontally(animationSpec = tween(220)) { if (forward) it / 8 else -it / 8 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(150)) +
                                    slideOutHorizontally(animationSpec = tween(150)) { if (forward) -it / 8 else it / 8 }
                            )
                    },
                    label = "favoritesTabList",
                ) { currentKind ->
                    // 转场中的每一页只读自己 tab 的数据：旧页滑出期间冻结在原列表上，
                    // 不会因为全局切到目标 tab 而「闪一下目标页」
                    FavoritesTabList(
                        kind = currentKind,
                        tabState = tabs[currentKind],
                        isActiveTab = currentKind == kind,
                        isLoadingMore = isLoadingMore,
                        // 每一页用自己的滚动状态（见上方 listStates 的说明）
                        listState = listStates.getValue(currentKind),
                        paddingValues = paddingValues,
                        scrollBehavior = scrollBehavior,
                        onRetry = { viewModel.load(currentKind) },
                        onLoadMore = viewModel::loadMore,
                        onOpenCreator = onOpenCreator,
                        onOpenPost = onOpenPost,
                    )
                }
                // 右侧滚动条：只跟随当前 tab（转场期间不叠两条），与转场内容同处一个 Box 才能居右对齐
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    trackPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    )
                )
            }
        }
    }
}

/**
 * 单个 tab 的列表内容：加载 / 错误 / 空态 / 列表 + 右侧区域留白。
 *
 * 由 [AnimatedContent] 在转场期间对「新旧两页」各组合一次，因此入参全部显式传入，
 * 不读任何全局的当前 tab 状态。
 */
@Composable
private fun FavoritesTabList(
    kind: Int,
    tabState: FavoritesTabState?,
    isActiveTab: Boolean,
    isLoadingMore: Boolean,
    listState: LazyListState,
    paddingValues: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenPost: (RecommendedPost) -> Unit,
) {
    // 滚动到底部附近自动加载下一页（动态 tab 专属）。
    // 只让「正在展示的 tab」触发，转场中正在退场的旧页不接着翻页。
    if (kind == 1 && isActiveTab) {
        LoadMoreEffect(listState, tabState?.hasMore == true, isLoadingMore) { onLoadMore() }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .scrollEndHaptic(),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            bottom = contentBottomPadding()
        )
    ) {
        when {
            // 该 tab 从未访问过：还没有任何数据可显示，进入整页加载态
            tabState == null -> item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) { LoadingComponent() }
            }
            // 首屏失败：整页错误态 + 重试
            tabState.error != null -> item(key = "error") {
                ErrorStateView(
                    onRetry = onRetry,
                    modifier = Modifier.fillParentMaxSize(),
                    summary = tabState.error,
                )
            }
            // 动态 tab：完整动态卡片
            kind == 1 -> {
                if (tabState.posts.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "还没有收藏内容",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    items(tabState.posts, key = { it.id }) { post ->
                        // 捕获动态卡片头部行的边界，作为「主页展开」过渡动画的起点
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
                    // 底部加载指示器
                    if (isLoadingMore) {
                        item(key = "loading_more") { LoadingComponent(modifier = Modifier.padding(vertical = 16.dp)) }
                    }
                }
            }
            // 创作者 / 众筹 tab 的通用收藏条目
            tabState.items.isEmpty() -> item(key = "empty") {
                EmptyStateView(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = "还没有收藏内容",
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
            else -> items(tabState.items, key = { it.id }) { item ->
                // 创作者 tab 使用长条形卡片组件，众筹 tab 用官方同款众筹卡片
                if (kind == 0) {
                    // 创作内容：subtitle 为空时回退到创作者简介（doing）和分区名，
                    // 由组件自动拼成「正在创作 xxx」
                    val doing = item.subtitle.ifBlank {
                        listOfNotNull(
                            item.user?.creator?.doing,
                            item.user?.creator?.category?.name
                        ).joinToString(" · ").ifBlank { null }
                    }
                    // 捕获卡片边界，作为「主页展开」过渡动画的起点
                    val origin = rememberCreatorOrigin()
                    CreatorListItem(
                        avatarUrl = item.image,
                        creatorName = item.title,
                        doing = doing,
                        onClick = origin.onClick {
                            onOpenCreator(item.user ?: AfadianUser(user_id = item.id, name = item.title, avatar = item.image))
                        },
                        modifier = origin.trackModifier().padding(16.dp, 6.dp)
                    )
                } else {
                    // 众筹 tab：官方同款众筹卡片（封面 + 状态徽标 + 金额进度条 + 支持人数/倒计时）
                    CrowdfundingCard(
                        item = item,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}