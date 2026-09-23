package com.hrsthrt74.afdian.ui.components

// 会话页普通消息气泡：头像行布局 + 文本气泡（TextView 可选中、链接可点），
// 以及 TextView 选择手柄染色、链接 Spannable、气泡时间格式化三个配套工具。
// 从 MessageConversationScreen 拆出，仅负责展示，不持有业务状态。
// 特殊消息卡片见同包 MessageCards.kt。

import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hrsthrt74.afdian.data.content.MessageContent
import com.hrsthrt74.afdian.data.content.MessageItem
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.pressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MessageBubble(
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
fun MessageContentBubble(
    content: MessageContent,
    isSentByMe: Boolean,
    userName: String,
) {
    // TextView 颜色
    val textColor = MiuixTheme.colorScheme.onSurface                        // 消息正文
    val linkColor = MiuixTheme.colorScheme.primary                          // 链接
    val selectionColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.35f) // 高亮
    val selectionHandleColor = MiuixTheme.colorScheme.primary               // 手柄

    val uriHandler = LocalUriHandler.current

    when (content) {
        is MessageContent.Text -> {
            // 气泡
            Card(modifier = Modifier
                .widthIn(max = 270.dp)
                .pressable(
                    interactionSource = null,
                )
            ) {
                Column {
                    AndroidView(
                        factory = { context ->
                            TextView(context).apply {
                                setTextIsSelectable(true)
                                movementMethod = LinkMovementMethod.getInstance()
                                // TextView 默认包含字体上下留白；关闭后再用较小倍率控制行间距，
                                // 使视觉效果更接近原先 Compose 文本的紧凑排版。
                                includeFontPadding = false
                                val paddingHorizontal = (12 * context.resources.displayMetrics.density).roundToInt()
                                val paddingVertical = (10 * context.resources.displayMetrics.density).roundToInt()
                                setPadding(
                                    paddingHorizontal,
                                    paddingVertical,
                                    paddingHorizontal,
                                    paddingVertical
                                )
                            }
                        },
                        update = { textView ->
                            textView.setTextColor(textColor.toArgb())
                            textView.textSize = 16f
                            textView.setLineSpacing(0f, 1.1f)
                            // Android TextView 的选择高亮颜色独立于文字颜色设置，
                            // 每次重组都更新，确保切换深浅主题后立即生效。
                            textView.highlightColor = selectionColor.toArgb()
                            tintTextSelectionHandles(textView, selectionHandleColor.toArgb())
                            textView.setText(buildSelectableMessageText(
                                text = content.text,
                                linkColor = linkColor.toArgb(),
                                onLinkClick = uriHandler::openUri,
                            ), TextView.BufferType.SPANNABLE)
                        },
                        modifier = Modifier.widthIn(max = 270.dp)
                    )
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

/** 给 TextView 的三个原生选择手柄同步应用当前主题色。 */
private fun tintTextSelectionHandles(textView: TextView, color: Int) {
    textView.textSelectHandle?.mutate()?.apply {
        setTint(color)
        textView.setTextSelectHandle(this)
    }
    textView.textSelectHandleLeft?.mutate()?.apply {
        setTint(color)
        textView.setTextSelectHandleLeft(this)
    }
    textView.textSelectHandleRight?.mutate()?.apply {
        setTint(color)
        textView.setTextSelectHandleRight(this)
    }
}

/**
 * 把消息中的 URL 转成 TextView 可点击的 Spannable，同时保留普通文本的原生选择能力。
 * TextView 的长按菜单由 Android 系统负责，便于与设备当前的文本选择体验保持一致。
 */
private fun buildSelectableMessageText(
    text: String,
    linkColor: Int,
    onLinkClick: (String) -> Unit,
): SpannableString {
    val spannable = SpannableString(text)
    val urlPattern = Regex("https?://[^\\s]+")
    for (match in urlPattern.findAll(text)) {
        val url = match.value
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onLinkClick(url)
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = linkColor
                    ds.isUnderlineText = true
                }
            },
            match.range.first,
            match.range.last + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return spannable
}

private fun formatBubbleTime(timestamp: Double): String {
    val date = Date((timestamp * 1000).toLong())
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(date)
}
