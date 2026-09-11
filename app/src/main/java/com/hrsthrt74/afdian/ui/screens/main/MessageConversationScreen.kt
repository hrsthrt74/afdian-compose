package com.hrsthrt74.afdian.ui.screens.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.ComplaintNotice
import com.hrsthrt74.afdian.data.content.MessageContent
import com.hrsthrt74.afdian.data.content.MessageFAQ
import com.hrsthrt74.afdian.data.content.MessageGroup
import com.hrsthrt74.afdian.data.content.MessageItem
import com.hrsthrt74.afdian.data.content.MessageObjectContent
import com.hrsthrt74.afdian.data.content.ShipmentNotice
import com.hrsthrt74.afdian.data.content.SponsorAmount
import com.hrsthrt74.afdian.data.content.SponsorInfo
import com.hrsthrt74.afdian.data.settings.ThemeSettings
import com.hrsthrt74.afdian.ui.components.AppBlurRadius
import com.hrsthrt74.afdian.ui.components.AppProgressiveBlurRadius
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.DataCard
import com.hrsthrt74.afdian.ui.components.EmptyStateView
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.blurBarColors
import com.hrsthrt74.afdian.ui.components.progressiveBlurBarColors
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.MessagesViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults.textFieldColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageConversationScreen(
    userId: String,
    userName: String,
    avatarUrl: String?,
    myAvatarUrl: String? = null,
    viewModel: MessagesViewModel,
    onBack: () -> Unit,
    backStack: NavBackStack,
) {
    val messages = remember { mutableStateListOf<MessageItem>() }
    val faqs = remember { mutableStateListOf<MessageFAQ>() }
    var isLoading by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // 消息列表是否可见：加载完成后先滚动到底部，再设为 true 显示，
    // 避免 LazyColumn 先在顶部渲染再跳到底部的闪烁
    var messagesVisible by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    // 焦点管理器：点击输入框以外区域时用 clearFocus() 收起键盘
    val focusManager = LocalFocusManager.current
    // 触觉反馈：顶栏操作按钮点击时给轻量震动
    val haptic = LocalHapticFeedback.current

    // 赞助详情 Sheet 状态：数据首次打开时拉取并缓存，之后复用
    var showSponsorSheet by remember { mutableStateOf(false) }
    var sponsorInfo by remember { mutableStateOf<SponsorInfo?>(null) }
    var sponsorLoading by remember { mutableStateOf(false) }

    val themeSettings = LocalThemeSettings.current
    val snackbarHostState = remember { SnackbarHostState() }
    // 输入栏高度（px），Snackbar 需要显示在输入栏上方而不是被它挡住
    var inputBarHeightPx by remember { mutableStateOf(0) }
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val density = LocalDensity.current
    val imeBottomPadding = with(density) {
        WindowInsets.ime.getBottom(this).toDp()
    }
    // 输入栏高度换算成 dp，供 Snackbar 抬高使用
    val inputBarHeight = with(density) { inputBarHeightPx.toDp() }
    // 键盘历史最大高度：不同设备/输入法键盘高度不同，用作归一化基准。
    // 只增不减，收起时仍能以完整键盘高度为分母计算比例
    var maxImeHeight by remember { mutableStateOf(0.dp) }
    if (imeBottomPadding > maxImeHeight) maxImeHeight = imeBottomPadding
    // 强模糊起点连续跟随键盘高度：收起(0)→0.3，完全展开(1)→0.9，
    // 展开与收起两个方向都和键盘运动同步，不存在离散切换带来的突变
    val imeRatio = if (maxImeHeight > 0.dp) (imeBottomPadding / maxImeHeight).coerceIn(0f, 1f) else 0f
    val progressiveStartFraction = lerp(0.3f, 0.9f, imeRatio)

    suspend fun loadMessages() {
        isLoading = true
        errorMessage = null
        messagesVisible = false
        try {
            val fetchedMessages = viewModel.fetchMessages(userId)
            messages.clear()
            messages.addAll(fetchedMessages)
        } catch (e: CancellationException) {
            // 协程被正常取消（如快速切页/重组导致请求中断），不算错误，直接向上抛
            throw e
        } catch (e: Exception) {
            errorMessage = e.message ?: "会话加载失败"
        } finally {
            isLoading = false
        }
    }

    suspend fun loadFAQs() {
        try {
            val fetchedFAQs = viewModel.fetchFAQs(userId)
            faqs.clear()
            faqs.addAll(fetchedFAQs)
        } catch (e: CancellationException) {
            // 同上，取消不处理
            throw e
        } catch (_: Exception) {
        }
    }

    fun sendMessage() {
        val content = draft.trim()
        if (content.isBlank() || isSending) return

        isSending = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val sentMessage = viewModel.sendMessage(userId, content)
                draft = ""
                val existingIndex = messages.indexOfFirst { it.id == sentMessage.id }
                if (existingIndex >= 0) {
                    messages[existingIndex] = sentMessage
                } else {
                    messages.add(sentMessage)
                }
                listState.animateScrollToItem(messages.size - 1)
            } catch (e: CancellationException) {
                // 发送中途被取消不算错误，静默处理
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "消息发送失败"
            } finally {
                isSending = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // 进入会话页即写入浏览历史（与创作者/动态页「进入即记录」模式一致；
        // 开关关闭时 HistoryStore 内部忽略），不阻塞消息加载
        viewModel.recordConversationOpened(userId, userName, avatarUrl)
        loadMessages()
        loadFAQs()
    }

    // 消息加载完成后滚动到底部，再显示列表（避免先在顶部渲染再跳底的闪烁）
    LaunchedEffect(messages.size, messagesVisible) {
        if (messages.isNotEmpty() && !messagesVisible) {
            listState.scrollToItem(messages.size - 1)
            messagesVisible = true
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30000)
            if (!isLoading && !isSending) {
                try {
                    val newMessages = viewModel.fetchMessages(userId)
                    val existingIds = messages.map { it.id }.toSet()
                    val freshMessages = newMessages.filter { it.id !in existingIds }
                    if (freshMessages.isNotEmpty()) {
                        messages.addAll(freshMessages)
                        listState.animateScrollToItem(messages.size - 1)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    // 错误信息用 Snackbar 展示，不占布局空间；点击"重试"重新加载消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "重试",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                loadMessages()
                loadFAQs()
            }
        }
    }

    // 列表是否贴在底部（最后一项可见），用于输入栏高度变化时决定是否跟随滚动
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= layoutInfo.totalItemsCount - 1
        }
    }
    // 上一次「输入栏内容高度 + 键盘高度」，用于计算贴底跟随的滚动差值
    var previousBottomOffset by remember { mutableStateOf(0.dp) }
    // 列表需要抬升的总高度 = 输入栏可见内容高度 + max(键盘, 导航栏) 避让高度
    // （与输入栏内部 union 避让一致：键盘收起时让出导航栏，展开时让出键盘）
    val navigationBarsPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomOffset = inputBarHeight + maxOf(imeBottomPadding, navigationBarsPadding)
    // 底部偏移变化（键盘升降、FAQ 出现）时，若列表原本贴底则直接钉到最后一项：
    // scrollToItem 会精确落在最大滚动位置（最后一条完整露出），无累积误差，
    // 且逐帧调用不会被动画打断，视觉上平滑跟随
    LaunchedEffect(bottomOffset) {
        val isFirstMeasure = previousBottomOffset == 0.dp
        val changed = bottomOffset != previousBottomOffset
        previousBottomOffset = bottomOffset
        if (!isFirstMeasure && changed && isAtBottom && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        snackbarHost = {
            // 抬升一个「输入栏内容 + 键盘」的高度，保证显示在输入栏上方
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = bottomOffset),
            )
        },
        topBar = {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                SmallTopAppBar(
                title = userName,
                color = blurBarColor(backdrop),
                navigationIcon = {
                    BackButton(onBack = onBack, backStack = backStack)
                },
                actions = {
                    // 赞助详情入口：点击拉取 /api/user/sponsor-info 并弹出 Sheet
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        showSponsorSheet = true
                        // 数据只拉一次：已加载或正在加载则不重复请求
                        if (sponsorInfo == null && !sponsorLoading) {
                            sponsorLoading = true
                            coroutineScope.launch {
                                sponsorInfo = viewModel.fetchSponsorInfo(userId)
                                sponsorLoading = false
                            }
                        }
                    }) {
                        // 「赞助详情」入口图标
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = "赞助详情",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                // 不再 imePadding：列表铺满全屏，靠 contentPadding 跟随输入栏实际高度避让
                .fillMaxSize(),
            // 采样层铺满画布，输入区域固定贴在底部，避免被默认放到顶部。
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 采样层覆盖整个会话画布，输入栏作为外部 overlay 单独绘制，
            // 既能采样上方消息内容，也不会把自身模糊层纳入采样导致 RenderThread 崩溃。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (messagesVisible) 1f else 0f)
                        .scrollEndHaptic()
                        // 点击消息列表空白处取消输入框焦点；
                        // 子元素（链接、气泡点击）优先消费事件，不会被子节点拦截影响
                        .pointerInput(Unit) {
                            detectTapGestures { focusManager.clearFocus() }
                        },
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        // 动态跟随输入栏：可见内容高度 + 键盘/导航栏避让高度，键盘升起列表直接抬同等高度
                        bottom = bottomOffset + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                if (isLoading && messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingComponent()
                        }
                    }
                }

                items(messages, key = { it.id }) { item ->
                    MessageBubble(
                        item = item,
                        userName = userName,
                        avatarUrl = avatarUrl,
                        myAvatarUrl = myAvatarUrl
                    )
                }
            }

            }

            // 输入区域也使用与底部导航栏相同的普通模糊，消息内容可以透过输入栏显示。
            ConversationInputBar(
                backdrop = backdrop,
                themeSettings = themeSettings,
                progressiveStartFraction = progressiveStartFraction,
                onHeightChanged = { inputBarHeightPx = it },
                faqs = faqs,
                draft = draft,
                onDraftChange = { draft = it },
                isSending = isSending,
                focusRequester = focusRequester,
                onSend = { sendMessage() },
                // 点击 FAQ 调用 /api/faq/ask 发送快捷提问，
                // 服务器会自动回复答案，完成后重新拉取消息上屏
                onFaqClick = { faq ->
                    if (!isSending) {
                        isSending = true
                        coroutineScope.launch {
                            try {
                                viewModel.askFaq(userId, faq.id)
                                loadMessages()
                            } catch (_: Exception) {
                            } finally {
                                isSending = false
                            }
                        }
                    }
                },
            )
        }
}

// 赞助详情 Sheet：展示「我发电了 TA」与「TA 发电了我」两组数据
SponsorDetailSheet(
    show = showSponsorSheet,
    userName = userName,
    sponsorInfo = sponsorInfo,
    isLoading = sponsorLoading,
    onDismiss = { showSponsorSheet = false },
)
}

/**
 * 赞助详情底部弹层：布局参考爱发电官方「xx 的赞助详情」弹窗。
 * 顶部三张 DataCard（当前发电/总共发电/到期时间）+ 下方逐笔赞助明细，
 * 「我发电了 TA」与「TA 发电了我」两组数据上下分区展示。
 */
@Composable
private fun SponsorDetailSheet(
    show: Boolean,
    userName: String,
    sponsorInfo: SponsorInfo?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    WindowBottomSheet(show = show, title = "${userName} 的赞助详情", onDismissRequest = onDismiss) {
        when {
            // 首次拉取中：显示加载指示
            isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingComponent()
            }

            // 加载失败（数据为 null）：提示用户
            sponsorInfo == null -> Text(
                text = "赞助详情加载失败，请稍后重试",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxSize(),
                textAlign = TextAlign.Center,
            )

            // 正常展示：out=我发电了 TA，in=TA 发电了我；
            // 某个方向没有记录时整个分区（含标题/卡片）都不显示
            else -> {
                // 两个方向都没有任何记录：显示空态插画
                val hasOut = !sponsorInfo.out_detail?.list.isNullOrEmpty()
                val hasIn = !sponsorInfo.in_detail?.list.isNullOrEmpty()
                if (!hasOut && !hasIn) {
                    // 空态：通用空状态视图，撑满 Sheet 居中
                    EmptyStateView(
                        title = "暂无发电记录",
                        summary = "喜欢 TA 的话，去 TA 的主页发电支持一下吧～",
                        icon = MiuixIcons.Light.Favorites,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    )
                } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 撑满 Sheet 可用高度：Sheet 外层是 wrapContentHeight，
                        // 内容不自撑满时整个弹层就只有内容那么高
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    // 「我发电了 TA」分区：out_detail 有记录才显示
                    if (hasOut) {
                        item {
                            SponsorSection(
                                title = "我发电了 TA",
                                amount = sponsorInfo.out_detail,
                                // 与官方一致：我发电用主色（purple）
                                amountColor = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }

                    // 两个分区都有内容时才画中间的分割线
                    if (hasOut && hasIn) {
                        item {
                            HorizontalDivider(Modifier.padding(horizontal = 4.dp).padding(top = 16.dp))
                        }
                    }

                    // 「TA 发电了我」分区：in_detail 有记录才显示
                    if (hasIn) {
                        item {
                            SponsorSection(
                                title = "TA 发电了我",
                                amount = sponsorInfo.in_detail,
                                // 与官方一致：TA 发电我用橙色
                                amountColor = Color(0xFFFCB239),
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

/**
 * 赞助详情单个分区：三张 DataCard 概览 + 逐笔明细列表。
 * @param amountColor 金额文字颜色，区分发电方向
 */
@Composable
private fun SponsorSection(
    title: String,
    amount: SponsorAmount?,
    amountColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 分区标题
        SmallTitle(text = title, insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 8.dp))
        // 顶部三栏概览：当前发电（元/月）、总共发电（元）、到期时间，布局对齐 DataCard 网格
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 当前发电：月度总额
            DataCard(
                title = "当前发电",
                value = "${amount?.current_amount ?: "0.00"} 元/月",
                maxFontSize = 24.sp,
                modifier = Modifier.weight(1f),
                colors = CardColors(
                    color = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                )
            )
            // 总共发电：累计总额
            DataCard(
                title = "总共发电",
                value = "${amount?.all_sum_amount ?: "0.00"} 元",
                maxFontSize = 24.sp,
                modifier = Modifier.weight(1f),
                colors = CardColors(
                    color = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                )
            )
            // 到期时间：最长到期日
//            DataCard(
//                title = "到期时间",
//                value = formatExpireTime(amount?.longest_expire_time),
//                modifier = Modifier.weight(1f),
//                colors = CardColors(
//                    color = MiuixTheme.colorScheme.surface,
//                    contentColor = MiuixTheme.colorScheme.onSurfaceContainerHighest,
//                )
//            )
        }

        // 逐笔明细：整体用 Card 包裹，条目之间以分割线分隔。
        // （空列表时整个分区已在 Sheet 层隐藏，不会走到这里）
        if (amount?.list.isNullOrEmpty()) return
        Card(
                colors = CardColors(
                    color = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column {
                    amount.list.forEachIndexed { index, record ->
                        // 条目之间加分割线，首条上方不画
                        if (index > 0) {
                            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                        }
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 左列：方案名 + 留言；右侧：大金额（￥ 与 /月 用小字）
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 左列：发电方案名（加 weight 占据剩余宽度），第二行是留言
                                Column(modifier = Modifier.weight(1f)) {
                                    // 发电方案名
                                    Text(
                                        text = record.plan?.displayName ?: "发电方案",
                                        style = MiuixTheme.textStyles.body2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    // 留言（有内容才显示）
                                    if (!record.remark.isNullOrBlank()) {
                                        Text(
                                            text = "留言：${record.remark}",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))

                                // 右侧金额
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "¥",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = amountColor,
                                        modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                                    )
                                    Text(
                                        text = record.show_amount ?: record.monthly_amount ?: "--",
                                        style = MiuixTheme.textStyles.title2,
                                        color = amountColor,
                                    )
                                    Text(
                                        text = "/月",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}

/** 秒级时间戳 → yyyy-MM-dd；空值显示占位符 */
private fun formatExpireTime(timestamp: Long?): String =
    timestamp?.let {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it * 1000))
    } ?: "--"

/**
 * 会话页底部输入栏：包含模糊背景、FAQ 快捷回复和输入框/发送按钮。
 * 模糊容器跟随键盘扩展，输入内容通过内部布局避让 IME。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversationInputBar(
    backdrop: LayerBackdrop?,
    themeSettings: ThemeSettings,
    progressiveStartFraction: Float,
    onHeightChanged: (Int) -> Unit,
    faqs: List<MessageFAQ>,
    draft: String,
    onDraftChange: (String) -> Unit,
    isSending: Boolean,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
    onFaqClick: (MessageFAQ) -> Unit,
) {
    // FAQ 快捷回复是否展开：默认收起，由输入框尾部的 Help 图标点击切换
    var showFaqs by remember { mutableStateOf(false) }
    // 输入框一旦有字就自动收起 FAQ（只收起，不自动展开）
    LaunchedEffect(draft) {
        if (draft.isNotBlank()) showFaqs = false
    }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(blurBarColor(backdrop))
            .then(
                if (backdrop != null && !themeSettings.progressiveBlur) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = AppBlurRadius,
                        colors = blurBarColors(),
                    )
                } else {
                    Modifier
                }
            )
            // 不再对容器整体 imePadding：键盘避让由内层 Column 用
            // navigationBars.union(ime) 统一处理，insets 连续变化，收起时不会突变。
            // 模糊背景会延伸到导航栏/键盘后面（视觉上被遮挡，无影响）。
        ,
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (backdrop != null && themeSettings.progressiveBlur) {
                // 输入区域位于底部，渐进方向必须从底部向顶部，
                // 与顶栏使用的 angle=90f 相反。
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .progressiveTextureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = AppProgressiveBlurRadius,
                            gradient = ProgressiveBlur(
                                angle = 270f,
                                startFraction = progressiveStartFraction,
                                endFraction = 1f,
                                curve = 1f,
                            ),
                            colors = progressiveBlurBarColors(),
                            noiseCoefficient = 0.1f,
                        ),
                )
        }

        // 导航栏与键盘统一避让：union 每边取最大值，
        // 键盘收起时=导航栏高度，展开时=ime，动画全程连续无跳变
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                // 注意：不要在整个 Column 上加 animateContentSize，
                // 否则键盘避让的尺寸变化也会被动画化，输入栏会跟着键盘拖影
                // 只测避让后的可见内容高度（不含键盘区域），供列表/Snackbar 精确抬升
                .onSizeChanged { onHeightChanged(it.height) }
        ) {
        // 渐进模糊需要更大顶部留白
        Spacer(modifier = Modifier.height(if (themeSettings.progressiveBlur) 20.dp else 0.dp))

        // FAQ：不再常驻，点击输入框尾部的 Help 图标后出现/消失。
        // 用 0→1 的进度值同时驱动「高度裁切 + 淡入淡出」：
        // 收起时内容保持组合并随高度一起淡出，避免内容先闪消、背景再缩的割裂感
        val faqProgress by animateFloatAsState(
            targetValue = if (showFaqs && faqs.isNotEmpty()) 1f else 0f,
            animationSpec = tween(250),
            label = "faqProgress",
        )
        if (faqs.isNotEmpty() && faqProgress > 0f) {

            LazyRow(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        // 先按完整尺寸测量内容，再按进度裁掉底部高度（内容贴顶放置）
                        val placeable = measurable.measure(constraints)
                        val animatedHeight = (placeable.height * faqProgress).roundToInt()
                        layout(placeable.width, animatedHeight) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .graphicsLayer {
                        // 同步淡入淡出 + 按布局尺寸裁切，防止超出部分画到输入栏外
                        alpha = faqProgress
                        clip = true
                        shape = RectangleShape
                    },
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 「常见问题」标签
                item {
                    Text(
                        text = "常见问题",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(faqs.take(6)) { faq ->
                    Card {
                        Text(
                            text = faq.title,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                // 先 clickable 后 padding
                                .clickable(enabled = showFaqs) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onFaqClick(faq)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 主操作区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 输入框
            TextField(
                value = draft,
                onValueChange = { newValue ->
                    if (newValue.length <= 500) {
                        onDraftChange(newValue)
                    }
                },
                label = "输入消息",
                useLabelAsPlaceholder = true,
                maxLines = 8,
                insideMargin = DpSize(16.dp, 12.dp),
                cornerRadius = 20.dp,
                colors = textFieldColors(MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)),

                // 尾部 Help 图标：点击展开/收起 FAQ 快捷回复；
                // 没有 FAQ 数据时不渲染，避免出现点了没反应的空按钮
                trailingIcon = if (faqs.isNotEmpty()) { {
                    // 常驻 12dp 左侧留白：图标隐藏时输入框右缘也不至于贴死
                    Spacer(modifier = Modifier.width(8.dp))
                    // 有文本时按钮隐藏：淡出 + 缩小 + 模糊；清空后恢复。
                    // 布局尺寸保持不变（仅视觉缩放），避免输入框宽度随输入抖动
                    val iconProgress by animateFloatAsState(
                        targetValue = if (draft.isBlank()) 1f else 0f,
                        animationSpec = tween(200),
                        label = "helpIconProgress",
                    )
                    if (iconProgress > 0f) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                showFaqs = !showFaqs
                           },
                            modifier = Modifier.graphicsLayer {
                                // 同步淡入淡出
                                alpha = iconProgress
                                // 消失时变小、出现时变大（以自身中心为基准缩放）
                                scaleX = iconProgress
                                scaleY = iconProgress
                                // 模糊开关开启时附加模糊过渡：消失清晰→8dp，出现模糊→清晰
                                if (themeSettings.enableBlur) {
                                    val radius = 8.dp.toPx() * (1f - iconProgress)
                                    renderEffect = if (radius > 0.5f)
                                        BlurEffect(radius, radius, TileMode.Decal)
                                    else
                                        null
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Help,
                                // 图标颜色与占位文字保持一致，展开时切主色提示当前状态
                                tint = if (showFaqs)
                                    MiuixTheme.colorScheme.primary
                                else
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                contentDescription = "FAQ 快捷回复",
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } } else null,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )

            val isEnabled = draft.isNotBlank() && !isSending
            val animatedBgColor by animateColorAsState(
                targetValue = if (isEnabled)
                    MiuixTheme.colorScheme.primary
                else
                    MiuixTheme.colorScheme.disabledPrimary,
                animationSpec = tween(200),
                label = "bgColor"
            )
            val animatedIconColor by animateColorAsState(
            targetValue = if (isEnabled) 
                MiuixTheme.colorScheme.onPrimary 
            else 
                MiuixTheme.colorScheme.disabledOnPrimary,
            animationSpec = tween(200),
            label = "iconColor"
        )

            // 按钮
            IconButton(
                onClick = onSend,
                enabled = isEnabled,
                backgroundColor = animatedBgColor,
            ) {
                Icon(
                    imageVector = MiuixIcons.Demibold.Back,
                    modifier = Modifier
                        .padding(12.dp)
                        .rotate(90f),
                    tint = animatedIconColor,
                    contentDescription = "Send"
                )
            }
        }
        }
    }
}
@Composable
private fun MessageBubble(
    item: MessageItem,
    userName: String,
    avatarUrl: String?,
    myAvatarUrl: String?
) {
    val isSentByMe = item.isSentByMe

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSentByMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // 对方的头像
        if (!isSentByMe) {
            Avatar(
                model = avatarUrl,
                size = 36.dp,
                contentDescription = userName,
                modifier = Modifier.padding(bottom = 2.dp, end = 8.dp)
            )
        } else {
            Spacer(Modifier.size(36.dp + 8.dp))
        }

        // 气泡区域
        Column(
            // weight(fill=false)：气泡最多占剩余宽度，超长内容（如订单卡片）会换行/收窄，
            // 不会把两侧固定尺寸的头像水平挤扁
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
        ) {
            // 时间
            item.message.send_time?.let { timestamp ->
                Text(
                    text = formatBubbleTime(timestamp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp)
                )
            }
            // 气泡
            MessageContentBubble(
                content = item.message.content,
                isSentByMe = isSentByMe,
                userName = userName
            )
        }

        // 自己头像
        if (isSentByMe) {
            Avatar(
                model = myAvatarUrl,
                size = 36.dp,
                contentDescription = "我",
                modifier = Modifier.padding(bottom = 2.dp, start = 8.dp)
            )
        } else {
            Spacer(Modifier.size(36.dp + 8.dp))
        }
    }
}

@Composable
private fun MessageContentBubble(
    content: MessageContent,
    isSentByMe: Boolean,
    userName: String,
) {
    val backgroundColor = if (isSentByMe) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSentByMe) {
        Color.White
    } else {
        MiuixTheme.colorScheme.onSurface
    }

    val linkColor = MiuixTheme.colorScheme.primary

    when (content) {
        is MessageContent.Text -> {
            val annotatedText = buildAnnotatedString {
                val urlPattern = Regex("https?://[^\\s]+")
                var lastIndex = 0
                val text = content.text

                for (match in urlPattern.findAll(text)) {
                    // 添加链接前的普通文本
                    append(text.substring(lastIndex, match.range.first))

                    // 添加链接
                    val url = match.value
                    withLink(LinkAnnotation.Url(url)) {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(url)
                        }
                    }

                    lastIndex = match.range.last + 1
                }

                // 添加剩余的普通文本
                if (lastIndex < text.length) {
                    append(text.substring(lastIndex))
                }
            }

            // 气泡
            Card(modifier = Modifier
                .widthIn(max = 270.dp)
                .pressable(
                    interactionSource = null,
                )
            ) {
                Column {
                    SelectionContainer {
                        // 文本
                        Text(
                            text = annotatedText,
                            style = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 1.4.em
                            ),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    // type4(购买成功自动回复)/type7(FAQ 自动回复) 在正文下方加一条说明；
                    // 自己发出去的 type7 不算自动回复，不显示该标注
                    if (content.isAutoReply && !isSentByMe) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        Text(
                            text = "此消息为自动回复",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        is MessageContent.Code -> {
            RedeemCodeCard(
                code = content.text,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }

        is MessageContent.Object -> {
            val objContent = content.content
            if (objContent.group != null) {
                GroupInviteCard(
                    group = objContent.group,
                    userName = userName,
                    modifier = Modifier.widthIn(max = 300.dp)
                )
            } else {
                OrderCard(
                    content = objContent,
                    modifier = Modifier.widthIn(max = 300.dp)
                )
            }
        }

        is MessageContent.Shipment -> {
            ShipmentNoticeCard(
                notice = content.notice,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }

        is MessageContent.Complaint -> {
            ComplaintCard(
                complaint = content.notice,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }

        is MessageContent.Empty -> {
            Text(
                text = "暂不支持的消息",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 电圈卡片：上下两部分。上半是动作文案（邀请/已加入），
 * 下半是「方形封面 + 圈子标题 + 右箭头」。
 */
@Composable
private fun GroupInviteCard(
    group: MessageGroup,
    userName: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column {
            // 上半区：动作文案；join_group_type=1 表示已加入，其余表示对方邀请
            val headline = if (group.join_group_type == 1) {
                "您已加入"
            } else {
                "$userName 邀请您加入"
            }
            Text(
                text = headline,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // 下半区：方形封面 + 标题 + 右箭头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable{ } // 写完「电圈」页再绑定这里
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 圈子封面（方形，裁切为直角圆角小块）
                group.cover?.let { coverUrl ->
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                // 右侧：标题 + 电圈描述（content 字段）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = group.title ?: "电圈",
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 电圈描述：content 非空时在标题下方展示，最多两行
                    group.content?.takeIf { it.isNotBlank() }?.let { desc ->
                        Text(
                            text = desc,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                // 右箭头
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

/**
 * 发电订单卡片：外层 Card 自带圆角裁切，内含金额头 + plan/型号 + 备注 + 订单号。
 */
@Composable
private fun OrderCard(
    content: MessageObjectContent,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column{

            // 优先 show_amount → total_amount → per_month
            val amount = content.show_amount ?: content.total_amount ?: content.per_month
            val month = content.month
            // ext.redeem 有值表示该发电订单使用了兑换码
            val usedRedeem = !content.redeem.isNullOrBlank()

            if (amount != null) {

                // 上半金额区
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.primary)
                        .padding(16.dp, 12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {

                    // 金额：￥
                    Text(
                        text = "¥",
                        style = MiuixTheme.textStyles.body1,
                        color = if (usedRedeem) { MiuixTheme.colorScheme.onPrimary.copy(0.8f) }
                                else            { MiuixTheme.colorScheme.onPrimary },
                        modifier = Modifier.padding(end = 2.dp, bottom = 4.dp)
                    )
                    // 金额：数字
                    Text(
                        text = amount,
                        color = if (usedRedeem) { MiuixTheme.colorScheme.onPrimary.copy(0.8f) }
                                else            { MiuixTheme.colorScheme.onPrimary },
                        // 使用兑换码时对发电金额加删除线，表示非实付
                        style = if (usedRedeem) { MiuixTheme.textStyles.title1.copy(textDecoration = TextDecoration.LineThrough) }
                                else            { MiuixTheme.textStyles.title1 }
                    )

                    // n个月
                    if (month != null && month > 0 && content.product_type != 1) {
                        Text(
                            text = "/ $month 个月",
                            style = MiuixTheme.textStyles.body2,
                            color = if (usedRedeem) { MiuixTheme.colorScheme.onPrimary.copy(0.8f) }
                                    else            { MiuixTheme.colorScheme.onPrimary },
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }

                    // 「使用兑换码」标注
                    if (usedRedeem) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "使用兑换码",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            // 下半信息部分
            Column(
                modifier = Modifier.padding(16.dp, 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 发电了…
                val planName = content.plan?.name ?: content.plan?.title
                if (planName != null && planName.isNotBlank()) {
                    Text(
                        text = "发电了 $planName",
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 型号
                val skuName = content.sku_detail?.firstOrNull()?.name?.takeIf { it != "默认型号" }
                if (skuName != null && skuName.isNotBlank()) {
                    Text(
                        text = "型号 $skuName",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        letterSpacing = 0.5.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 留言
                content.remark?.let { remark ->
                    if (remark.isNotBlank()) {
                        HorizontalDivider()
                        Text(
                            text = "留言：$remark",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 收货信息：商品需要填写收货地址时，ext.address 会带出收货人信息
                content.address?.let { addressInfo ->
                    if (!addressInfo.address.isNullOrBlank()) {
                        HorizontalDivider()
                        // 收货人 + 联系电话
                        val recipient = listOfNotNull(addressInfo.name, addressInfo.phone)
                            .joinToString("  ")
                        if (recipient.isNotBlank()) {
                            Text(
                                text = recipient,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 完整收货地址
                        Text(
                            text = addressInfo.address,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 订单号
                content.out_trade_no?.let { orderNo ->
                    if (orderNo.isNotBlank()) {
                        HorizontalDivider()
                        Text(
                            text = orderNo,
                            style = MiuixTheme.textStyles.footnote2,
                            letterSpacing = 0.5.sp, // 防止过挤
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 兑换码消息。
 * 上半展示兑换码，下半点击「复制兑换码」。
 */
@Composable
private fun RedeemCodeCard(
    code: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Card(modifier = modifier) {
        Column {
            // 上半区：兑换码，等宽放大便于查看/核对
            SelectionContainer {
                Text(
                    text = code,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 12.dp))

            // 下半区：整块可点的复制区，浅容器底色区分，主色图标+文案居中
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // 点击触发轻震动反馈
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // 兑换码写入系统剪贴板
                        val clipboardManager = context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("兑换码", code))
                        // 轻提示反馈
                        Toast.makeText(context, "已复制兑换码", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 复制图标
                Icon(
                    imageVector = MiuixIcons.Demibold.Copy,
                    contentDescription = "复制兑换码",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "复制兑换码",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 发货通知卡片：外层 Miuix Card 只做圆角裁切（squircleSurface），
 * 内部不套泡沫背景。上半为主色填充头部，下半素色区放物流字段与详情入口。
 */
@Composable
private fun ShipmentNoticeCard(
    notice: ShipmentNotice,
    modifier: Modifier = Modifier,
) {
    // 「查看订单详情」用系统浏览器打开
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current

    Card(modifier = modifier) {
        Column {
            // 头部主色填充区：白字标题，顶部两角圆角由 Card 自身裁切
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.primary)
            ) {
                Text(
                    text = "你购买的商品已发货",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // 下半部分
            Column(
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                notice.productName?.takeIf { it.isNotBlank() }?.let { name ->
                    // 商品名称
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider()

                // 详细信息
                notice.orderNo?.let { ShipmentFieldRow("爱发电订单号", it) }
                notice.trackingNo?.let { ShipmentFieldRow("快递运单号", it) }
                notice.courier?.let { ShipmentFieldRow("快递公司", it) }

                HorizontalDivider()
            }

            notice.detailUrl?.let { url ->
                // 查看订单详情
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            uriHandler.openUri(url)
                        }
                        .padding(16.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Demibold.Notes,
                        contentDescription = "查看订单详情",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "查看订单详情",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 发货卡片中的「标签：值」行，标签固定宽左侧灰色，值为正文可换行。
 */
@Composable
private fun ShipmentFieldRow(label: String, value: String, valueColor: Color? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = valueColor ?: MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 投诉通知卡片：顶栏用 error 红底 + onError 文字标明「微信投诉/支付宝投诉」，
 * 标题下紧跟金额（样式参考 OrderCard），下半素色区展示其余字段与详情链接。
 */
@Composable
private fun ComplaintCard(
    complaint: ComplaintNotice,
    modifier: Modifier = Modifier,
) {
    // 平台识别失败时回退为「投诉」
    val complaintSuffix = if (complaint.isUpgradeComplaint) "升级投诉" else "投诉"
    val title = complaint.platform?.let { "${it}${complaintSuffix}" } ?: complaintSuffix
    val amount = complaint.amount?.takeIf { it.isNotBlank() }
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    val upgradeTextColor = if (complaint.isUpgradeComplaint) {
        MiuixTheme.colorScheme.error
    } else {
        null
    }

    Card(modifier = modifier) {
        Column {
            // 顶栏（红色强调）：标题 + 金额
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.error)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // 顶栏标题
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onError
                    )
                    // 金额（参考 OrderCard 金额区排版：¥ + 大字数字）
                    if (amount != null) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "¥",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onError,
                                modifier = Modifier.padding(end = 2.dp, bottom = 4.dp)
                            )
                            Text(
                                text = amount,
                                style = MiuixTheme.textStyles.title1,
                                color = MiuixTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }

            // 下半素色区：字段逐行展示
            Column(
                modifier = Modifier.padding(16.dp, 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                complaint.message?.takeIf { it.isNotBlank() }?.let { ShipmentFieldRow("留言", it) }
                complaint.reason?.takeIf { it.isNotBlank() }?.let {
                    ShipmentFieldRow("升级投诉原因", it, upgradeTextColor)
                }
                complaint.description?.takeIf { it.isNotBlank() }?.let {
                    ShipmentFieldRow("升级投诉描述", it, upgradeTextColor)
                }
                complaint.time?.takeIf { it.isNotBlank() }?.let { ShipmentFieldRow("投诉时间", it) }
                complaint.orderNo?.takeIf { it.isNotBlank() }?.let { ShipmentFieldRow("订单号", it) }
                complaint.complaintNo?.takeIf { it.isNotBlank() }?.let { ShipmentFieldRow("投诉单号", it) }
            }

            Column{

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                complaint.detailUrl?.let { url ->
                    // 查看用户详情：居中操作行（图标 + 文案），样式与其他带操作卡片一致
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                uriHandler.openUri(url)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 用户图标
                        Icon(
                            imageVector = MiuixIcons.Demibold.Contacts,
                            contentDescription = "查看用户详情",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "查看用户详情",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }

            }
        }
    }
}

private fun formatBubbleTime(timestamp: Double): String {
    val date = Date((timestamp * 1000).toLong())
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(date)
}
