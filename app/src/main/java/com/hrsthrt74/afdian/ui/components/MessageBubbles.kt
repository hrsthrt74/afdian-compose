package com.hrsthrt74.afdian.ui.components

// 会话页普通消息气泡：头像行布局 + 文本气泡（TextView 可选中、链接可点），
// 以及 TextView 选择手柄染色、链接 Spannable、气泡时间格式化三个配套工具。
// 从 MessageConversationScreen 拆出，仅负责展示，不持有业务状态；
// 点击气泡通过 onBubbleTapped 回调（携带气泡窗口坐标）交给调用方弹操作菜单
// （弹层与挖洞压暗见同包 MessageBubbleActionPopup.kt）。
// 特殊消息卡片见同包 MessageCards.kt。

import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hrsthrt74.afdian.data.content.MessageTextLink
import com.hrsthrt74.afdian.data.content.AutoMessageKind
import com.hrsthrt74.afdian.data.content.MessageContent
import com.hrsthrt74.afdian.data.content.MessageItem
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.navigation.LocalAppLinkNavigator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.pressable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 单击判定的按住时长上限（毫秒）：超过视为长按（文本选择），不弹菜单 */
private const val TAP_MAX_DURATION_MS = 300L

/** 双击判定间隔（毫秒）：间隔内的第二次 tap 是双击选词操作，不弹菜单 */
private const val DOUBLE_TAP_INTERVAL_MS = 300L

@Composable
fun MessageBubble(
    item: MessageItem,
    userName: String,
    avatarUrl: String?,
    myAvatarUrl: String?,
    // 点击气泡回调（携带气泡的窗口坐标快照）：null = 保持旧行为，不挂点击手势
    onBubbleTapped: ((item: MessageItem, bubbleBounds: Rect) -> Unit)? = null,
    // 气泡实时边界上报（每次布局回调）：弹层打开期间挖洞遮罩与菜单跟随气泡的
    // 最新窗口坐标，防御任何布局变化（滚动/转场/键盘）造成的遮罩错位
    onLiveBounds: ((item: MessageItem, bubbleBounds: Rect) -> Unit)? = null,
) {
    val isSentByMe = item.isSentByMe

    // 气泡的窗口坐标：随每次布局（滚动/键盘收放）实时更新，
    // 点击时取到的即「打开菜单瞬间」的最新位置，由调用方负责冻结成快照
    var bubbleBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    // 回调经 rememberUpdatedState 包装：pointerInput 闭包常驻，读取最新回调避免重组后过期
    val latestOnBubbleTapped by rememberUpdatedState(onBubbleTapped)
    val latestOnLiveBounds by rememberUpdatedState(onLiveBounds)

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
            // 气泡触发器包装层：捕获气泡窗口坐标 + 检测点击。
            // 文本气泡内部 TextView 会消费触摸，其点击走 TextView 的 onClickListener
            // （见 MessageContentBubble）；本层手势主要覆盖卡片类消息（兑换码/订单/物流等）
            // 的非交互区域——卡片自身的 clickable（复制区/详情入口等）消费事件时本层不触发，
            // 原有行为不受影响
            Box(
                modifier = Modifier
                    // 记录气泡在窗口中的最新坐标（供菜单挖洞对位），并实时上报给调用方
                    .onGloballyPositioned {
                        val bounds = it.boundsInWindow()
                        bubbleBoundsInWindow = bounds
                        latestOnLiveBounds?.invoke(item, bounds)
                    }
                    .then(
                        if (onBubbleTapped != null) {
                            Modifier.pointerInput(item.id) {
                                detectTapGestures {
                                    bubbleBoundsInWindow?.let { bounds ->
                                        latestOnBubbleTapped?.invoke(item, bounds)
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                MessageContentBubble(
                    content = item.message.content,
                    isSentByMe = isSentByMe,
                    userName = userName,
                    // 文本气泡点击 → 同样走 onBubbleTapped（携带最新坐标）
                    onTextBubbleTapped = onBubbleTapped?.let { callback ->
                        {
                            bubbleBoundsInWindow?.let { bounds -> callback(item, bounds) }
                        }
                    },
                )
            }
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
    // 文本气泡的普通点击回调（链接点击不触发，见下方时间戳 guard）：null = 不响应
    onTextBubbleTapped: (() -> Unit)? = null,
) {
    // TextView 颜色
    val textColor = MiuixTheme.colorScheme.onSurface                        // 消息正文
    val linkColor = MiuixTheme.colorScheme.primary                          // 链接
    val selectionColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.35f) // 高亮
    val selectionHandleColor = MiuixTheme.colorScheme.primary               // 手柄

    val appLinkNavigator = LocalAppLinkNavigator.current

    // 上次 tap 抬起时间：间隔内的第二次 tap 是双击（选词操作），不弹菜单
    var lastTapUpAt by remember { mutableStateOf(0L) }
    // factory 闭包只执行一次，经 rememberUpdatedState 读取最新回调
    val latestOnTextBubbleTapped by rememberUpdatedState(onTextBubbleTapped)

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
                                // 触摸监听：在 View 层直接识别「单击」来触发气泡菜单。
                                // 不用 setOnClickListener：selectable 模式下 TextView 内部
                                // Editor 的双击检测状态机会吞掉部分 tap 的 performClick
                                // （实测：关闭菜单后第一次点击丢失，日志见 Editor
                                // "Double tap detection is fail"），OnTouchListener 在
                                // UP 时自行判定 tap，稳定可靠。返回 false 不消费事件，
                                // TextView 的文本选择 / 链接点击照常工作。
                                setOnTouchListener { view, event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_UP -> {
                                            val now = event.eventTime
                                            // 单击判定：按住时长短（长按 = 文本选择，不弹）
                                            val isTap = now - event.downTime < TAP_MAX_DURATION_MS
                                            // 双击（间隔很短）视为选词操作，第二次不弹菜单
                                            val isDoubleTap = now - lastTapUpAt < DOUBLE_TAP_INTERVAL_MS
                                            lastTapUpAt = now
                                            if (isTap && !isDoubleTap &&
                                                view is TextView && !hitLinkSpan(view, event.x, event.y)
                                            ) {
                                                latestOnTextBubbleTapped?.invoke()
                                            }
                                        }
                                    }
                                    false
                                }
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
                                links = content.links,
                                linkColor = linkColor.toArgb(),
                                onLinkClick = appLinkNavigator::open,
                            ), TextView.BufferType.SPANNABLE)
                        },
                        modifier = Modifier.widthIn(max = 270.dp)
                    )
                    // 自动消息在正文下方追加一条来源说明：
                    // type7（系统自动发出）双方都显示「此消息为自动发出」——我方点 FAQ 时由服务端代发的也是 type7；
                    // type4（购买成功自动回复）只会由对方发出，自己发出去的不显示该标注
                    val autoMessageLabel = when (content.autoKind) {
                        AutoMessageKind.AUTO_SENT -> "此消息为自动发出"
                        AutoMessageKind.PURCHASE_REPLY -> if (isSentByMe) null else "此消息为自动回复"
                        null -> null
                    }
                    if (autoMessageLabel != null) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        Text(
                            text = autoMessageLabel,
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

        is MessageContent.Restock -> {
            RestockNoticeCard(
                notice = content.notice,
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
 * 判断点击坐标是否落在链接 Span 上：命中则本次点击交给 LinkMovementMethod
 * 打开链接，不弹气泡菜单。
 * 坐标换算与 LinkMovementMethod 一致：事件坐标减 padding 加 scroll 转成文本
 * 布局坐标，再按行取字符 offset 查询 Span。
 */
private fun hitLinkSpan(textView: TextView, x: Float, y: Float): Boolean {
    val layout = textView.layout ?: return false
    val spanned = textView.text as? Spanned ?: return false
    val textX = x - textView.totalPaddingLeft + textView.scrollX
    val textY = y - textView.totalPaddingTop + textView.scrollY
    if (textY < 0f || textY >= layout.height) return false
    val line = layout.getLineForVertical(textY.toInt())
    val offset = layout.getOffsetForHorizontal(line, textX)
    return spanned.getSpans(offset, offset, ClickableSpan::class.java).isNotEmpty()
}

/**
 * 把消息构建成 TextView 可点击、可选择原文的 Spannable，同时保留普通文本的原生选择能力。
 * 链接来源有两类：
 * - [links]：数据层 HTML 清洗记录的 <a> 标签区间（客服自动回复里的富文本链接）；
 * - 裸 URL：用户手打的 http(s) 链接，按正则识别。
 * TextView 的长按菜单由 Android 系统负责，便于与设备当前的文本选择体验保持一致。
 */
private fun buildSelectableMessageText(
    text: String,
    links: List<MessageTextLink>,
    linkColor: Int,
    onLinkClick: (String) -> Unit,
): SpannableString {
    val spannable = SpannableString(text)
    // 先挂 <a> 标签还原出的链接
    for (link in links) {
        spannable.setSpan(
            messageLinkSpan(url = link.url, linkColor = linkColor, onLinkClick = onLinkClick),
            link.start,
            link.end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    // 再识别裸 URL；与已有链接区间重叠的跳过，避免同一 URL 叠加两个点击 Span
    val urlPattern = Regex("https?://[^\\s]+")
    for (match in urlPattern.findAll(text)) {
        val range = match.range
        if (links.any { range.first < it.end && range.last + 1 > it.start }) continue
        spannable.setSpan(
            messageLinkSpan(url = match.value, linkColor = linkColor, onLinkClick = onLinkClick),
            range.first,
            range.last + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return spannable
}

/** 构造一个消息内可点击链接的 Span：主题色 + 下划线，点击交给 [onLinkClick] 打开。 */
private fun messageLinkSpan(
    url: String,
    linkColor: Int,
    onLinkClick: (String) -> Unit,
) = object : ClickableSpan() {
    override fun onClick(widget: View) {
        onLinkClick(url)
    }

    override fun updateDrawState(ds: TextPaint) {
        ds.color = linkColor
        ds.isUnderlineText = true
    }
}

private fun formatBubbleTime(timestamp: Double): String {
    val date = Date((timestamp * 1000).toLong())
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(date)
}

/**
 * 消息的具体发送时间（完整日期 + 时分），用于气泡操作菜单底部的时间行，
 * 区别于气泡上的短时间戳。非 private：由 MessageConversationScreen 组装菜单时调用。
 */
fun formatExactMessageTime(timestamp: Double): String {
    val date = Date((timestamp * 1000).toLong())
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(date)
}
