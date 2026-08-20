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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.MessageContent
import com.hrsthrt74.afdian.data.content.MessageFAQ
import com.hrsthrt74.afdian.data.content.MessageItem
import com.hrsthrt74.afdian.data.content.MessageObjectContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.pressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageConversationScreen(
    userId: String,
    userName: String,
    avatarUrl: String?,
    viewModel: MessagesViewModel,
    onBack: () -> Unit
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

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

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

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = userName,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp
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
                            Text("加载中...", style = MiuixTheme.textStyles.body2)
                        }
                    }
                }

                items(messages, key = { it.id }) { item ->
                    MessageBubble(
                        item = item,
                        userName = userName,
                        avatarUrl = avatarUrl
                    )
                }
            }

            if (faqs.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(faqs.take(6)) { faq ->
                        Card(
                            modifier = Modifier.clickable {
                                draft = faq.title.take(500)
                            }
                        ) {
                            Text(
                                text = faq.title,
                                style = MiuixTheme.textStyles.body2,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = draft,
                    onValueChange = { newValue ->
                        if (newValue.length <= 500) {
                            draft = newValue
                        }
                    },
                    label = "输入消息",
                    useLabelAsPlaceholder = true,
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
                    onClick = { sendMessage() },
                    enabled = isEnabled,
                    backgroundColor = animatedBgColor
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

            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "重试",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                coroutineScope.launch { loadMessages() }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    item: MessageItem,
    userName: String,
    avatarUrl: String?
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
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = userName,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.onSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.take(1),
                        style = MiuixTheme.textStyles.body1
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 气泡
        Column(
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

        // 自己头像，放这是因为在气泡后面
        if (isSentByMe) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "我",
                    style = MiuixTheme.textStyles.body1
                )
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
