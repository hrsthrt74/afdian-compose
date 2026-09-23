package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import android.content.Intent
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.ui.components.basic.AppBlurRadius
import com.hrsthrt74.afdian.ui.components.basic.AppProgressiveBlurRadius
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.HtmlContent
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.blurBarColors
import com.hrsthrt74.afdian.ui.components.basic.progressiveBlurBarColors
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.post.PostActionBar
import com.hrsthrt74.afdian.ui.components.post.PostHeader
import com.hrsthrt74.afdian.ui.components.post.PostPaidBlock
import com.hrsthrt74.afdian.ui.components.post.normalizeImageUrl
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

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val origin = rememberCreatorOrigin()
    // 与底部导航栏同款的「实时模糊」：backdrop 负责采样背后内容，
    // 操作栏通过 textureBlur 把采样结果糊化后垫在自己底下
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    // 列表滚动状态：双击顶栏回顶 + 滚动条共用（本页是固定小标题栏，无折叠行为）
    val listState = rememberLazyListState()

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
                            onUrlClick = onOpenUrl ?: uriHandler::openUri,
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
                        PostActionBar(post = detail)
                    }
                }
            }
        }
    }
}

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
