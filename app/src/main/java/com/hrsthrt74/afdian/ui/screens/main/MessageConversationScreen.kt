package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.MessageContent
import com.hrsthrt74.afdian.data.content.MessageFAQ
import com.hrsthrt74.afdian.data.content.MessageItem
import com.hrsthrt74.afdian.data.content.MessageObjectContent
import com.hrsthrt74.afdian.data.settings.ThemeSettings
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import com.hrsthrt74.afdian.ui.components.AppBlurRadius
import com.hrsthrt74.afdian.ui.components.AppProgressiveBlurRadius
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.blurBarColors
import com.hrsthrt74.afdian.ui.components.progressiveBlurBarColors
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.MessagesViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults.textFieldColors
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.pressable
// Miuix 提供的「滚动到边界触觉反馈」修饰符：列表快速滑动到顶/底边界时震动一下
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val themeSettings = LocalThemeSettings.current
    val snackbarHostState = remember { SnackbarHostState() }
    // 输入栏高度（px），Snackbar 需要显示在输入栏上方而不是被它挡住
    var inputBarHeightPx by remember { mutableStateOf(0) }
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val density = LocalDensity.current
    val imeBottomPadding = with(density) {
        WindowInsets.ime.getBottom(this).toDp()
    }
    val isImeVisible = imeBottomPadding > 0.dp
    // 输入栏高度换算成 dp，供 Snackbar 抬高使用
    val inputBarHeight = with(density) { inputBarHeightPx.toDp() }
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val progressiveStartFraction = if (isImeVisible && windowHeight > 0.dp) {
        // 键盘越高，模糊渐变的强模糊起点越靠下，避免输入区域被大面积重模糊。
        (0.5f + (imeBottomPadding / windowHeight)).coerceIn(0.5f, 0.6f)
    } else {
        0.5f
    }

    suspend fun loadMessages() {
        isLoading = true
        errorMessage = null
        try {
            val fetchedMessages = viewModel.fetchMessages(userId)
            messages.clear()
            messages.addAll(fetchedMessages)
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
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
        loadMessages()
        loadFAQs()
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
                    // 消息列表快速滑到顶部/底部边界时触发触觉反馈（本列表无 nestedScroll，直接追加即可）
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        // 动态跟随输入栏：可见内容高度 + 键盘/导航栏避让高度，键盘升起列表直接抬同等高度
                        bottom = bottomOffset + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
}

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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(blurBarColor(backdrop))
            .then(
                if (backdrop != null && !themeSettings.progressiveBlur) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = androidx.compose.ui.graphics.RectangleShape,
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
                            shape = androidx.compose.ui.graphics.RectangleShape,
                            blurRadius = AppProgressiveBlurRadius,
                            gradient = ProgressiveBlur(
                                angle = 270f,
                                startFraction = progressiveStartFraction,
                                endFraction = 1f,
                                curve = 0.8f,
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
                // 只测避让后的可见内容高度（不含键盘区域），供列表/Snackbar 精确抬升
                .onSizeChanged { onHeightChanged(it.height) }
        ) {
        // 顶部留白：渐进模糊需要更大留白(20dp)，普通模糊给 8dp 即可
        Spacer(modifier = Modifier.height(if (themeSettings.progressiveBlur) 20.dp else 8.dp))

        // FAQ
        if (faqs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(faqs.take(6)) { faq ->
                    Card {
                        // 点击绑定到内部 Box，让点击特效在卡片内容上正常显示，
                        // 避免直接绑在 Card 上导致的裁切问题
                        Box(
                            modifier = Modifier.clickable { onFaqClick(faq) }
                        ) {
                            Text(
                                text = faq.title,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
        // 头像
        if (!isSentByMe) {
            if (avatarUrl != null) {
                Avatar(
                    model = avatarUrl,
                    size = 42.dp,
                    contentDescription = userName,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.take(1),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 气泡
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
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp)
                )
            }
            // 气泡
            MessageContentBubble(
                content = item.message.content,
                isSentByMe = isSentByMe
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        
        // 自己头像，放这是因为在气泡后面；有缓存的真实头像就用真实头像
        if (isSentByMe) {
            if (myAvatarUrl != null) {
                Avatar(
                    model = myAvatarUrl,
                    size = 42.dp,
                    contentDescription = "我",
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "我",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageContentBubble(
    content: MessageContent,
    isSentByMe: Boolean
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
                SelectionContainer {
                    // 文本
                    Text(
                        text = annotatedText,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        is MessageContent.Object -> {
            MessageObjectCard(
                content = content.content,
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

@Composable
private fun MessageObjectCard(
    content: MessageObjectContent,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content.group?.let { group ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    group.cover?.let { coverUrl ->
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column {
                        Text(
                            text = "圈子邀请",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = group.title ?: "圈子",
                            style = MiuixTheme.textStyles.body2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                group.content?.let { groupContent ->
                    if (groupContent.isNotBlank()) {
                        Text(
                            text = groupContent,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                group.member_count?.let { count ->
                    Text(
                        text = "$count 位成员",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } ?: run {
                Text(
                    text = "发电订单",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )

                val amount = content.show_amount ?: content.total_amount ?: content.per_month
                val month = content.month
                if (amount != null) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "¥",
                            style = MiuixTheme.textStyles.body1,
                            modifier = Modifier.padding(end = 2.dp, bottom = 4.dp)
                        )
                        Text(
                            text = amount,
                            style = MiuixTheme.textStyles.title1
                        )
                        if (month != null && month > 0 && content.product_type != 1) {
                            Text(
                                text = "/ $month 个月",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }

                // 优先显示 plan 的 name，然后是 sku 的 name
                val planName = content.plan?.name ?: content.plan?.title
                val skuName = content.sku_detail?.firstOrNull()?.name?.takeIf { it != "默认型号" }
                val displayName = when {
                    planName != null && skuName != null -> "$planName · $skuName"
                    planName != null -> planName
                    skuName != null -> skuName
                    else -> null
                }
                if (displayName != null && displayName.isNotBlank()) {
                    Text(
                        text = displayName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider()

                content.remark?.let { remark ->
                    if (remark.isNotBlank()) {
                        Text(
                            text = remark,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        HorizontalDivider()
                    }
                }

                content.out_trade_no?.let { orderNo ->
                    if (orderNo.isNotBlank()) {
                        Text(
                            text = orderNo,
                            style = MiuixTheme.textStyles.footnote2,
                            letterSpacing = 0.5.sp,
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

private fun formatBubbleTime(timestamp: Double): String {
    val date = Date((timestamp * 1000).toLong())
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(date)
}
