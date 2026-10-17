package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.ui.components.basic.AppBlurRadius
import com.hrsthrt74.afdian.ui.components.basic.AppProgressiveBlurRadius
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.HtmlContent
import com.hrsthrt74.afdian.ui.components.basic.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.blurBarColors
import com.hrsthrt74.afdian.ui.components.basic.progressiveBlurBarColors
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.post.CommentCard
import com.hrsthrt74.afdian.ui.components.post.CommentSectionHeader
import com.hrsthrt74.afdian.ui.components.post.PostActionBar
import com.hrsthrt74.afdian.ui.components.post.PostHeader
import com.hrsthrt74.afdian.ui.components.post.PostPaidBlock
import com.hrsthrt74.afdian.ui.components.post.normalizeImageUrl
import com.hrsthrt74.afdian.ui.navigation.LocalAppLinkNavigator
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.PostDetailViewModel
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight.Companion.GlassStrokeSmallLight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 动态详情页。
 *
 * 结构：SmallTopAppBar（返回 + 分享）→ 创作者头部 → 封面图 → 标题 → 正文（HTML 原生渲染）→ 操作栏。
 * 路由携带列表页的 post 先秒显，[PostDetailViewModel] 后台拉 get-detail 全文覆盖刷新。
 *
 * @param post 路由传入的动态数据（可能只有预览内容）
 * @param scrollToComments 从列表页点评论按钮进来时为 true：评论区懒加载后自动滚到「评论 N 条」那一行
 * @param onBack 返回回调
 * @param onOpenCreator 点开创作者主页
 */
@Composable
fun PostDetailScreen(
    post: RecommendedPost,
    onBack: () -> Unit,
    backStack: NavBackStack,
    onOpenCreator: (AfadianUser) -> Unit,
    onNavigateToPost: (RecommendedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)? = null,
    scrollToComments: Boolean = false,
) {
    val app = LocalContext.current.applicationContext as Application
    // key 用 post_id：同一动态复用同一个 VM，避免重复请求全文
    val vm: PostDetailViewModel = viewModel(
        key = post.id,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PostDetailViewModel(app, post) as T
        },
    )
    val detail by vm.post.collectAsState()
    val error by vm.error.collectAsState()
    val loading by vm.loading.collectAsState()
    // 完整详情是否已经有结果：滚动定位要等它，避免按「预览结构」算出来的旧锚点落位
    val detailSettled by vm.detailSettled.collectAsState()

    // ──────── 评论状态（本轮只读 + 点赞）────────
    val comments by vm.comments.collectAsState()
    val commentsLoading by vm.commentsLoading.collectAsState()
    val commentsLoadingMore by vm.commentsLoadingMore.collectAsState()
    val commentsHasMore by vm.commentsHasMore.collectAsState()
    val commentsError by vm.commentsError.collectAsState()
    val subReplies by vm.subReplies.collectAsState()
    val likedCommentIds by vm.likedCommentIds.collectAsState()

    val context = LocalContext.current
    val appLinkNavigator = LocalAppLinkNavigator.current
    val origin = rememberCreatorOrigin()
    // 与底部导航栏同款的「实时模糊」：backdrop 负责采样背后内容，
    // 操作栏通过 textureBlur 把采样结果糊化后垫在自己底下
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    // 列表滚动状态：双击顶栏回顶 + 滚动条共用（本页是固定小标题栏，无折叠行为）
    val listState = rememberLazyListState()

    // ──────── 评论区定位 ────────
    //
    // 评论区固定挂在页面最底部，标题行的下标完全由它前面那几个「条件项」的数量决定。
    // ⚠️ 这里必须与下方 LazyColumn 的 item 顺序严格对齐：改动正文区结构时要同步改这里，
    //    否则点评论按钮会滚到错误的位置（表现为落在上一篇/下一篇或文章末尾卡片上）。
    val commentsAnchorIndex = remember(detail) {
        var index = 0
        index++                                        // header
        if (!detail.cover.isNullOrBlank()) index++      // cover
        if (detail.title.isNotBlank()) index++          // title
        if ((detail.min_price?.toDoubleOrNull() ?: 0.0) > 0.0) index++   // paid
        index++                                        // content
        if (!detail.pics.isNullOrEmpty() && !detail.content.orEmpty().contains("<img")) index++ // pics
        detail.old_new?.let { if (it.old != null || it.new != null) index++ } // post_navigation
        index++                                        // footer_header
        index                                          // ← comments_header 自身
    }

    // 锚点会随 detail 变化；用 rememberUpdatedState 让挂起中的滚动逻辑始终读到最新值
    val anchor by rememberUpdatedState(commentsAnchorIndex)
    val scope = rememberCoroutineScope()

    /**
     * 评论区末尾补位空白的高度（px）。
     *
     * 评论区可能很短（没评论 / 只加载了一点点），如果「评论标题 → 内容末尾」不足一屏，
     * 列表就滚不到「标题贴顶」的位置——从列表页点评论按钮跳进来会停在半屏，看着像动画没走完。
     * 这块空白保证评论区至少有一屏高，标题永远可以顶到屏幕顶部。
     *
     * 精确值由下方 `LaunchedEffect` 测量；滚动失败时也会先累加一个近似值兜底。
     */
    var commentsFillPx by remember { mutableIntStateOf(0) }

    /**
     * 等「评论区标题行」具备稳定可滚动的条件，返回应滚到的下标。
     *
     * 两个前提缺一不可：
     * 1. 完整详情已落定（[detailSettled]）：从列表页进来时会先渲染路由里的预览 post，
     *    全文到位后 `cover / title / pics / 上一篇` 这些条件 item 会增减，锚点随之后移。
     *    提前滚就会停在正文中间——正文里图片多时结构差异最大，最容易复现。
     * 2. 列表已按当前结构完成布局（totalItemsCount 覆盖到锚点）。
     */
    suspend fun awaitCommentsAnchor(): Int {
        snapshotFlow { Triple(detailSettled, listState.layoutInfo.totalItemsCount, anchor) }
            .first { (settled, count, index) -> settled && count > index }
        return anchor
    }

    /**
     * 滚到评论区标题行，所有入口（路由标记 / 页内评论按钮）共用。
     *
     * 近距离（≤2 个 item）走动画；远距离直接跳：
     * [androidx.compose.foundation.lazy.LazyListState.animateScrollToItem] 在高低不一的
     * 卡片之间做长距离滚动时，每帧都要布局途中整屏的高卡片（楼中楼盖得多时尤甚），
     * 掉帧非常明显（这就是「一卡一卡」的来源），而且动画途中结构变化还会偏。
     * 直接跳更稳、更跟手，也符合「跳到评论区」这种导航动作的预期。
     *
     * 落位后会校验「第一可见项是不是评论标题」：详情刚落定那一帧，快照里的 layoutInfo
     * 可能还是上一版结构；另外评论区比一屏短时，末尾补位空白要到下一帧才长出来。
     * 这两种情况都会多试几轮（最多 4 轮）。
     */
    suspend fun scrollToCommentsAnchor() {
        // 最多试 4 轮：第一轮落位；评论区比一屏短时会先补末尾空白，通常第 2~3 轮就能贴顶
        repeat(4) {
            // 先等一帧：让 detailSettled 触发的重组/布局、或刚补上的空白真正生效
            withFrameNanos { }
            val target = awaitCommentsAnchor()
            val distance = kotlin.math.abs(target - listState.firstVisibleItemIndex)
            if (distance <= 2) {
                listState.animateScrollToItem(target)
            } else {
                listState.scrollToItem(target)
            }
            // 校验落点：评论标题就是第一可见项才算成功
            withFrameNanos { }
            val firstKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key
            if (firstKey == COMMENTS_HEADER_KEY) return
            // 没顶到顶通常是因为「评论区比一屏还短」：上面的测量 effect 会在这一帧
            // 给末尾补上空白，等下一轮重试即可（repeat 里已经先 withFrameNanos）
        }
    }

    // 详情页内点评论按钮：已经在本页了，只滚动不跳转；同时确保评论已请求
    val scrollToCommentSection: () -> Unit = {
        vm.loadComments()
        scope.launch { scrollToCommentsAnchor() }
    }

    // 懒加载：评论区第一次滚进视口才请求评论（进页面不预取，用户看不到就不花流量）
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index >= anchor } }
            .filter { it }
            .first()
        vm.loadComments()
    }

    // 从列表页点评论按钮进来：等详情结构与列表布局都稳定后，再跳到评论区标题行
    val pendingScrollToComments = remember { mutableStateOf(scrollToComments) }
    LaunchedEffect(listState) {
        if (!pendingScrollToComments.value) return@LaunchedEffect
        scrollToCommentsAnchor()
        // 动画/跳转结束后再消费标记，避免重组导致重复滚
        pendingScrollToComments.value = false
    }

    // ──────── 评论区最短占满一屏（测量）────────
    //
    // 原理：只要「评论标题」和「最后一条内容」同时可见，两者顶/底的差值就是
    // 「标题 → 内容末尾」的高度，跟当前滚到哪儿无关；用它和一屏比，差多少就补多少。
    // 因为末尾恒为我们加的补位空白，「最后一条真实内容」是倒数第二项。
    // 其他位置量不出来，就保持原值，避免来回抖。
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            if (info.totalItemsCount < 2) return@snapshotFlow null
            val header = info.visibleItemsInfo.firstOrNull { it.index == anchor } ?: return@snapshotFlow null
            val lastContent = info.visibleItemsInfo.firstOrNull { it.index == info.totalItemsCount - 2 }
            when {
                // 1) 末尾内容也可见：精确量出评论区高度，按一屏补齐
                lastContent != null -> {
                    val viewportPx = info.viewportEndOffset - info.viewportStartOffset
                    val contentBelow = lastContent.offset + lastContent.size - header.offset
                    (viewportPx - contentBelow).coerceAtLeast(0)
                }
                // 2) 标题已经贴顶、末尾内容还在屏幕外：评论区本身超过一屏，不需要补位
                info.visibleItemsInfo.firstOrNull()?.index == anchor -> 0
                // 3) 其他位置（标题在中段、末尾在屏幕外）量不出来，保持原值
                else -> null
            }
        }.filterNotNull().collect { commentsFillPx = it }
    }
    // px → dp：末尾空白项用
    val commentsFillHeight = with(LocalDensity.current) { commentsFillPx.toDp() }

    // 评论列表滚动加载：沿用通用 LoadMoreEffect，评论排在页面末尾，触底即加载下一页
    LoadMoreEffect(
        listState = listState,
        hasMore = commentsHasMore && comments.isNotEmpty(),
        isLoadingMore = commentsLoadingMore,
        onLoadMore = vm::loadMoreComments,
    )

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 小标题栏（固定栏无折叠态，scrollBehavior 传 null）
            DoubleTapToTop(listState, scrollBehavior = null) {
            // 顶栏实时模糊：与其他页面（消息会话/订单页）同款写法——
            // BlurredBar 负责糊化 backdrop 采样到的滚动内容，底色用 blurBarColor 半透明
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                SmallTopAppBar(
                    title = "",
                    color = blurBarColor(backdrop),
                    navigationIcon = {
                        BackButton(onBack = onBack, backStack = backStack)
                    },
                    actions = {
                        // 分享：调起系统分享面板，分享该动态的网页链接
                        IconButton(onClick = { sharePost(context, detail.id, detail.title) }) {
                            Icon(imageVector = MiuixIcons.Share, contentDescription = "分享")
                        }
                    },
                )
            }
            }
        },
    ) { paddingValues ->
        // 悬浮容器：操作栏常驻屏幕底部，LazyColumn 从其后方穿过（底部留出避让空间）
        //
        // ⚠️ 层级结构有讲究（照抄 MessageConversationScreen 的踩坑结论）：
        // layerBackdrop 采样层只能包住「被模糊的内容」（LazyColumn），
        // 千万不能把带 textureBlur 的操作栏也套进采样子树——
        // 自身模糊层参与采样会导致 RenderThread 原生层崩溃（SIGSEGV）。
        Box(modifier = Modifier.fillMaxSize()) {
            // 采样层：只包列表内容，供操作栏的 textureBlur 糊化背后滚动的正文
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
                // 仅持有 id 打开（如「创作者指南」入口）时，先用占位 post 拉详情，
                // 加载完成前显示统一加载组件，避免空白闪烁；已有完整内容则直接秒显
                if (loading && error == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingComponent(text = "正在加载动态...")
                    }
                } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        // 底部额外留出悬浮操作栏的高度 + 边距，保证正文最后一段不被遮挡
                        bottom = paddingValues.calculateBottomPadding() + 92.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── 创作者头部（与卡片同款，可点进主页）──
                item(key = "header") {
                    PostHeader(
                        post = detail,
                        profile = detail.user,
                        onCreatorClick = origin.onClick { onOpenCreator(detail.user) },
                        showDoing = true,
                        origin = origin,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // ── 封面图 ──
                if (!detail.cover.isNullOrBlank()) {
                    item(key = "cover") {
                        AsyncImage(
                            model = detail.cover,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
                // ── 标题 ──
                if (detail.title.isNotBlank()) {
                    item(key = "title") {
                        Text(
                            text = detail.title,
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                // ── 付费动态：未解锁显示「发电解锁」按钮，已解锁显示可查看提示 ──
                //    与 PostCard 共用 PostPaidBlock，两处表现一致；按钮跳转创作者主页去发电
                val minPrice = detail.min_price?.toDoubleOrNull() ?: 0.0
                if (minPrice > 0.0) {
                    item(key = "paid") {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PostPaidBlock(
                                post = detail,
                                onUnlockClick = { onOpenCreator(detail.user) },
                            )
                        }
                    }
                }
                // ── 正文（HTML 原生渲染）；get-detail 失败时降级显示列表页摘要并提示错误 ──
                item(key = "content") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HtmlContent(
                            html = detail.content.orEmpty().ifBlank { detail.summary },
                            onUrlClick = onOpenUrl ?: appLinkNavigator::open,
                        )
                        error?.let {
                            Text(
                                text = it,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
                // ── pics 数组里的图：有些动态的图不在正文 HTML 里，
                //    列表卡片能看到、详情页却消失，就是这种。正文里已有 <img> 时不再重复展示。
                //    详情页不用卡片那种网格，逐张全宽铺开展示（阅读场景更像文章配图）──
                if (!detail.pics.isNullOrEmpty() && !detail.content.orEmpty().contains("<img")) {
                    item(key = "pics") {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            detail.pics.orEmpty().forEach { url ->
                                AsyncImage(
                                    model = normalizeImageUrl(url),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .squircleClip(cornerRadius = 16.dp)
                                )
                            }
                        }
                    }
                }

                // ── 上一篇/下一篇导航栏：两列卡片，没有内容的列也占位 ──
                detail.old_new?.let { oldNew ->
                    if (oldNew.old != null || oldNew.new != null) {
                        item(key = "post_navigation") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // 上一篇卡片
                                CardNavItem(
                                    label = "上一篇",
                                    title = oldNew.old?.title,
                                    enabled = oldNew.old != null,
                                    modifier = Modifier.weight(1f),
                                    onClick = oldNew.old?.let { old -> {
                                        onNavigateToPost(
                                            RecommendedPost(
                                                post_id = old.post_id,
                                                title = old.title,
                                                user = detail.user,
                                            )
                                        )
                                    } },
                                )
                                // 下一篇卡片（没有也占位）
                                CardNavItem(
                                    label = "下一篇",
                                    title = oldNew.new?.title,
                                    enabled = oldNew.new != null,
                                    modifier = Modifier.weight(1f),
                                    onClick = oldNew.new?.let { new -> {
                                        onNavigateToPost(
                                            RecommendedPost(
                                                post_id = new.post_id,
                                                title = new.title,
                                                user = detail.user,
                                            )
                                        )
                                    } },
                                )
                            }
                        }
                    }
                }
                // ── 文章末尾创作者头部：再次展示创作者信息，方便长文阅读后快速关注 ──
                item(key = "footer_header") {
                    Card(Modifier.pressable(interactionSource = null, indication = SinkFeedback(), delay = null).padding(horizontal = 16.dp)) {
                        PostHeader(
                            post = detail,
                            profile = detail.user,
                            onCreatorClick = origin.onClick { onOpenCreator(detail.user) },
                            showDoing = true,
                            origin = origin,
                        )

                    }
                }

                // ── 评论区（详情页最底部）──
                // 标题行恒存在，作为滚动锚点：即便评论还没加载/为空也在，
                // 这样「点评论按钮进来」总能落到这一行，而不是空集或底部。
                item(key = COMMENTS_HEADER_KEY) {
                    CommentSectionHeader(
                        count = detail.comment_count ?: comments.size,
                    )
                }
                // 加载中且一条都没有：只转圈；有数据就静默（避免列表跳动）
                if (commentsLoading && comments.isEmpty()) {
                    item(key = "comments_loading") {
                        LoadingComponent(
                            text = "正在加载评论...",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                    }
                } else if (commentsError != null && comments.isEmpty()) {
                    item(key = "comments_error") {
                        ErrorStateView(
                            onRetry = vm::retryComments,
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            summary = commentsError,
                        )
                    }
                } else if (comments.isEmpty()) {
                    // 空态用统一的 EmptyStateView；这里在滚动列表里，禁用浮现动画，
                    // 避免反复滚进滚出视口时重复播
                    item(key = "comments_empty") {
                        EmptyStateView(
                            title = "还没有评论",
                            summary = "来说两句吧",
                            icon = MiuixIcons.Light.Community,
                            animated = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                        )
                    }
                } else {
                    // 评论行：用根评论 id 作稳定 key（评论不可变，分页只追加）
                    items(comments, key = { it.comment_id }) { comment ->
                        CommentCard(
                            comment = comment,
                            postAuthorId = detail.user.user_id,
                            likedIds = likedCommentIds,
                            replyState = subReplies[comment.comment_id],
                            onLikeToggle = vm::toggleCommentLike,
                            // 点卡片回复：本轮只读，回复输入尚未接入，先给明确反馈而不是静默无响应
                            onReply = { Toast.makeText(context, "回复功能暂未开放", Toast.LENGTH_SHORT).show() },
                            onExpandReplies = { vm.expandSubReplies(comment.comment_id) },
                            onLoadMoreReplies = { vm.loadMoreReplies(comment.comment_id) },
                            // 评论者头像点进其创作者主页；匿名评论没有 user_id 就不给入口
                            onCreatorClick = comment.user.user_id
                                .takeIf { it.isNotBlank() }
                                ?.let { uid -> origin.onClick { onOpenCreator(comment.user.copy(user_id = uid)) } },
                        )
                    }
                    // 底部加载指示器
                    if (commentsLoadingMore) {
                        item(key = "comments_loading_more") {
                            LoadingComponent(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                        }
                    } else if (!commentsHasMore) {
                        item(key = "comments_end") {
                            Text(
                                text = "— 没有更多评论了 —",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            )
                        }
                    }
                }

                // 评论区最短占一屏：末尾补一块空白，保证「评论标题」永远能滚到屏幕顶部。
                // 高度由上面的测量逻辑算出；内容够长时自动归零，不会多出空白。
                item(key = "comments_fill") {
                    Spacer(Modifier.height(commentsFillHeight))
                }
            }
                } // else 分支结束（LazyColumn）
                } // 采样层 Box 结束（操作栏必须在其外部，见上方注释）

            // ── 悬浮操作栏背景区（照抄 ConversationInputBar 的分层结构）：
            //   一个全宽、贴屏幕底缘的容器，渐变色底 + 渐进模糊铺满整个区域
            //   （含导航栏高度的空间），操作栏浮在它上面 ──
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                val bgShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                // 始终存在的渐变色底：顶部透明 → 底部 surface，无模糊时也保证正文可读
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))
                            ),
                            bgShape,
                        ),
                )
                // 仅在渐进模糊开启（且采样层可用）时叠一层底部→顶部的渐进糊化
                if (backdrop != null && themeSettings.progressiveBlur) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .progressiveTextureBlur(
                                backdrop = backdrop,
                                shape = bgShape,
                                blurRadius = AppProgressiveBlurRadius,
                                gradient = ProgressiveBlur(angle = 270f, startFraction = 0.5f, endFraction = 1f, curve = 0.8f),
                                colors = progressiveBlurBarColors(),
                                noiseCoefficient = 0.1f,
                            ),
                    )
                }
                // 操作栏本体：一张悬浮的圆角卡片——半透明底色（透出后面的模糊）+ 细描边；
                // 卡片下方到屏幕底缘的空间留给外层的模糊渐变背景继续延伸
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = paddingValues.calculateBottomPadding() + 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(Capsule())
                            .fillMaxWidth()
                            // 圆角卡片外观：半透明底色让渐进模糊透出来，细描边勾出轮廓；
                            // 普通模糊开启时（非渐进），在卡片上叠一层实时糊化
                            .background(
                                MiuixTheme.colorScheme.surface.copy(alpha = 0.6f),
                                RoundedCornerShape(24.dp),
                            )
                            .then(
                                if (backdrop != null && !themeSettings.progressiveBlur) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(24.dp),
                                        blurRadius = AppBlurRadius,
                                        colors = blurBarColors(),
                                        enabled = themeSettings.enableBlur,
                                        highlight = GlassStrokeSmallLight
                                    )
                                } else Modifier
                            )
                            .border(
                                width = 0.75.dp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                shape = Capsule(),
                            )
                        ,
                    ) {
                        // 评论按钮：已在详情页内，点了直接滚到页面底部的评论区
                        PostActionBar(post = detail, onCommentClick = scrollToCommentSection)
                    }
                }
            }
        }
    }
}

/**
 * 评论标题行在 LazyColumn 里的 item key。
 *
 * 与下方 `item(key = ...)` 共用同一个常量：滚动落位后用第一可见项的 key 校验
 * 「到底是不是评论区」，避免结构刚变化那一帧落错位置。
 */
private const val COMMENTS_HEADER_KEY = "comments_header"

/** 上一篇/下一篇导航卡片 */
@Composable
private fun CardNavItem(
    label: String,
    title: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (!enabled) {
        Spacer(modifier = modifier)
        return
    }
    Card(modifier = modifier.pressable(interactionSource = null, indication = SinkFeedback(), delay = null).then(modifier)) {
        Column(
            modifier = Modifier
                .clickable { onClick?.invoke() }
                .padding(12.dp),
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title ?: "—",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 系统分享面板：分享动态链接 https://afdian.com/p/{post_id} */
private fun sharePost(context: android.content.Context, postId: String, title: String) {
    val url = "https://afdian.com/p/$postId"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, if (title.isBlank()) url else "$title\n$url")
    }
    context.startActivity(Intent.createChooser(intent, "分享动态"))
}
