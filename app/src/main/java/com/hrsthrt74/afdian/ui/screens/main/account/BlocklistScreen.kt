package com.hrsthrt74.afdian.ui.screens.main.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.BlackedItem
import com.hrsthrt74.afdian.data.content.BlackedType
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
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
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.BlocklistViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 屏蔽 / 拒绝名单页：TabRow 切换「屏蔽列表」「拒绝列表」两个 tab，
 * 列表项复用 CreatorListItem；点击打开创作者主页，长按解除屏蔽 / 拒绝。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun BlocklistScreen(
    viewModel: BlocklistViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenCustomerService: () -> Unit,
    backStack: NavBackStack,
    onBack: () -> Unit,
) {
    // 数据：当前 tab 与两个名单的状态
    val kind by viewModel.kind.collectAsState()
    val blockUi by viewModel.blockUi.collectAsState()
    val rejectUi by viewModel.rejectUi.collectAsState()
    val removingId by viewModel.removingId.collectAsState()
    val removeError by viewModel.removeError.collectAsState()
    // 当前 tab 展示的名单状态（0 = 屏蔽，1 = 拒绝）
    val ui = if (kind == 0) blockUi else rejectUi

    // 屏幕
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    // 长按待解除的条目；弹窗关闭动画结束后清空，避免退场过程中标题闪变
    var pendingRemove by remember { mutableStateOf<BlackedItem?>(null) }
    val removeDialogState = rememberDialogState()
    // 解除失败提示弹窗：ViewModel 的 removeError 非空时弹出，关闭后消费掉
    val removeErrorDialogState = rememberDialogState()
    LaunchedEffect(removeError) {
        if (removeError != null) removeErrorDialogState.show()
    }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, false) {
                    Column {
                        TopAppBar(
                            title = "屏蔽与拒绝列表",
                            largeTitle = "屏蔽与拒绝列表",
                            color = blurBarColor(backdrop),
                            scrollBehavior = scrollBehavior,
                            navigationIcon = { BackButton(onBack = onBack, backStack = backStack) }
                        )
                        // 名单切换 tab；随顶栏一起放进 BlurredBar，模糊时才有底色
                        TabRow(
                            tabs = listOf("屏蔽", "拒绝"),
                            selectedTabIndex = kind,
                            onTabSelected = viewModel::select,
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = contentBottomPadding()
                    )
                ) {
                    // 说明卡片：按当前 tab 展示屏蔽 / 拒绝的效果说明（样式与优惠码页说明卡片一致）
                    item(key = "intro") {
                        BlocklistIntroCard(
                            kind = kind,
                            onOpenCustomerService = onOpenCustomerService,
                        )
                    }
                    when {
                        // 首屏加载中
                        ui.loading -> {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) { LoadingComponent() }
                            }
                        }
                        // 首屏失败：整页错误态 + 重试
                        ui.error != null -> {
                            item {
                                ErrorStateView(
                                    onRetry = viewModel::load,
                                    modifier = Modifier.fillParentMaxSize(),
                                    summary = ui.error,
                                )
                            }
                        }
                        // 空名单：按 tab 给出各自的说明文案
                        ui.items.isEmpty() -> {
                            item {
                                EmptyStateView(
                                    title = if (kind == 0) "还没有屏蔽的用户" else "还没有拒绝的用户",
                                    modifier = Modifier.fillParentMaxSize(),
                                )
                            }
                        }
                        // 名单条目：长条形创作者卡片
                        else -> items(ui.items, key = { it.id }) { item ->
                            // 捕获卡片边界，作为「主页展开」过渡动画的起点
                            val origin = rememberCreatorOrigin()
                            CreatorListItem(
                                avatarUrl = item.user.avatar,
                                creatorName = item.user.name.ifBlank { "爱发电用户" },
                                summary = joinLabel(item),
                                onClick = origin.onClick { onOpenCreator(item.asUser) },
                                // 长按弹出解除确认弹窗；正在解除中的条目忽略重复触发
                                onLongClick = if (removingId == null) {
                                    {
                                        pendingRemove = item
                                        removeDialogState.show()
                                    }
                                } else null,
                                modifier = origin.trackModifier().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                            )
                        }
                    }
                    // 底部加载指示器（翻页时出现在列表尾部）
                    if (ui.loadingMore) {
                        item { LoadingComponent(modifier = Modifier.padding(vertical = 16.dp)) }
                    }
                }
                // 右侧滚动条
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    trackPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    )
                )
            }

            // 滚动到底部附近自动加载下一页（放在 LazyColumn 外的 Composable 上下文）
            LoadMoreEffect(listState, ui.hasMore, ui.loadingMore) { viewModel.loadMore() }
        }
    }

    // 解除确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = removeDialogState,
        title = pendingRemove?.let { "解除${kindName(it.blackedType)}「${it.user.name}」？" }.orEmpty(),
        // 这里描述不准，干脆删掉得了
//        summary = pendingRemove?.let {
//            if (it.blackedType == BlackedType.Block) "解除后 TA 将可以重新评论你的动态。"
//            else "解除后 TA 将可以重新赞助你。"
//        }.orEmpty(),
        confirmText = "解除",
        destructive = true,
        onDismissed = { pendingRemove = null },
        onConfirm = { pendingRemove?.let(viewModel::remove) },
    )

    // 解除失败提示弹窗：确认 / 取消 / 点遮罩关闭时都消费掉错误，避免下次同错误不再弹出
    AppDialog(
        state = removeErrorDialogState,
        title = "解除失败",
        summary = removeError.orEmpty(),
        confirmText = "知道了",
        onConfirm = viewModel::consumeRemoveError,
        onDismissed = viewModel::consumeRemoveError,
    )
}

/** 条目副标题：加入名单的日期，如「屏蔽于 2026-10-04」。 */
private fun joinLabel(item: BlackedItem): String {
    val verb = if (item.blackedType == BlackedType.Block) "屏蔽" else "拒绝"
    val date = runCatching {
        Instant.ofEpochSecond(item.create_time)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrDefault("")
    return if (date.isBlank()) "已${verb}" else "${verb}于 $date"
}

/** 名单类型的展示名：block → 「屏蔽」，reject → 「拒绝」。 */
private fun kindName(type: BlackedType): String =
    if (type == BlackedType.Block) "屏蔽" else "拒绝"

// ==================== 屏蔽 / 拒绝说明文案 ====================
// internal：赞助详情弹窗（MessageConversationScreen）的确认文案复用同一份，保证两处不脱节。

/** 屏蔽名单的效果说明。 */
internal val BLOCK_INTRO_LINES = listOf(
    "双方将看不到对方的评论和电圈帖子",
)

/** 拒绝名单的效果说明（客服指引一条由 [customerServiceContactText] 单独渲染成可点链接）。 */
internal val REJECT_INTRO_LINES = listOf(
    "无法向你发电、点赞、评论、私信",
    "从你的电圈中移出该成员，并无法加入",
    "TA 在发电有效期内仍可以查看你的付费内容",
)

/**
 * 「如需取消权益，请联系管理员 @Lain音酱」富文本；「@Lain音酱」整体为可点链接（主色 + 下划线）。
 * 屏蔽页说明卡片与赞助详情确认弹窗共用，保证文案一致。
 *
 * @param linkColor 链接颜色（调用处传主题主色，避免本函数依赖 Composable 上下文）
 */
internal fun customerServiceContactText(
    linkColor: Color,
    onOpenCustomerService: () -> Unit,
): AnnotatedString = buildAnnotatedString {
    append("如需取消权益，请联系管理员 ")
    // 把「@Lain音酱」整体包成链接（LinkAnnotation.Clickable 携带样式，点击交给监听器）
    withLink(
        LinkAnnotation.Clickable(
            tag = "customer_service",
            styles = TextLinkStyles(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
            linkInteractionListener = LinkInteractionListener { _ -> onOpenCustomerService() },
        )
    ) { append("@Lain音酱") }
}

/**
 * 名单说明卡片：样式与优惠码页的说明卡片一致（Card + body2 次要色文字），
 * 内容按当前 tab 切换屏蔽 / 拒绝各自的效果说明。
 *
 * @param kind 当前 tab（0 = 屏蔽，1 = 拒绝）
 * @param onOpenCustomerService 点击「音酱」链接进入客服会话
 */
@Composable
private fun BlocklistIntroCard(
    kind: Int,
    onOpenCustomerService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        // 圆点列表：条目间 2dp 间距，视觉上与优惠码说明卡片的行距接近
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 效果说明：internal 常量，与赞助详情弹窗共用同一份数据
            if (kind == 0) {
                BLOCK_INTRO_LINES.forEach { IntroBullet(it) }
            } else {
                REJECT_INTRO_LINES.forEach { IntroBullet(it) }
                // 客服一行单独渲染：「@Lain音酱」是可点链接
                IntroCustomerServiceBullet(onOpenCustomerService)
            }
            // 名单页操作提示（加入弹窗里不展示这一条）
            IntroBullet("长按卡片可移除")
        }
    }
}

/** 单条说明文字：圆点前缀 + 次要色，字体样式与优惠码说明卡片相同。 */
@Composable
private fun IntroBullet(text: String) {
    Text(
        text = "• $text",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * 客服指引一行（「• 如需取消权益，请联系管理员 @Lain音酱」）。
 * 点击链接进入与爱发电客服（Lain音酱）的会话。
 */
@Composable
private fun IntroCustomerServiceBullet(onOpenCustomerService: () -> Unit) {
    Text(
        text = AnnotatedString("• ") + customerServiceContactText(
            linkColor = MiuixTheme.colorScheme.primary,
            onOpenCustomerService = onOpenCustomerService,
        ),
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
