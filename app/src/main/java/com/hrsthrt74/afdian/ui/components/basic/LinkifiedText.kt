package com.hrsthrt74.afdian.ui.components.basic

// 纯文本 → 可点击链接 Spannable 的通用规则，从消息气泡里拆出来：
// 裸链接交给系统 Linkify 识别（与 TextView autoLink="web" 同一套规则），
// 再用自定义 ClickableSpan 接管点击，让它走应用内导航并使用主题色。
// 不直接开 TextView.autoLinkMask 的原因有两个：
// 1) autoLinkMask 生成的 URLSpan 点击走 ACTION_VIEW，会跳出 App 去浏览器；
// 2) Linkify 用的是 IRI 规则，紧贴链接的中文会被算进 URL（详见 trimCjkEdges），
//    必须拿到匹配区间后再收一次边界。
// 这套逻辑与具体界面无关，任何「纯文本 + 可点链接」的地方都可以直接复用。

import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import com.hrsthrt74.afdian.data.content.MessageTextLink

/** 裁剪后至少要保留的长度：最短的有效域名形如 `a.co`，再短就没有链接意义，不如保持原样 */
private const val MIN_LINK_TEXT_LENGTH = 4

/**
 * 把纯文本构建成 TextView 可点击、可选择原文的 Spannable，同时保留普通文本的原生选择能力。
 * 链接来源有两类：
 * - [links]：数据层 HTML 清洗记录的 <a> 标签区间（客服自动回复里的富文本链接），优先级最高；
 * - 裸链接：交给系统 [Linkify] 按 Android 自动链接规则识别，
 *   覆盖 `http(s)://`、`www.` 开头以及裸域名三种写法。
 * 长按菜单由 Android 系统负责，便于与设备当前的文本选择体验保持一致。
 *
 * @param text 已经清洗过的纯文本（不含 HTML 标签）
 * @param links 正文里 <a> 标签对应的区间与 href，普通消息传空列表
 * @param linkColor 链接文字颜色，一般传主题的 primary
 * @param onLinkClick 链接点击回调，参数是规整后的完整 URL
 */
internal fun buildLinkifiedText(
    text: String,
    links: List<MessageTextLink>,
    linkColor: Int,
    onLinkClick: (String) -> Unit,
): SpannableString {
    val spannable = SpannableString(text)

    // 裸链接识别交给系统 Linkify，不再手写正则：实测它能正确排除版本号、文件名、邮箱，
    // 能识别 www. 与裸域名，也懂括号配对，且会给缺 scheme 的地址补上 http://，
    // 交给应用内导航时是完整地址。
    // Linkify 没有公开的「指定默认 scheme」重载（只有 @hide 版本）；
    // API 29 起虽有可定制 Span 的 Function 版本，但下面还要按 <a> 区间去重、
    // 裁剪首尾中文，统一放在后处理里做更直观，所以这里用最基本的两参版本。
    Linkify.addLinks(spannable, Linkify.WEB_URLS)

    // Linkify 加的是系统 URLSpan，点击会走 ACTION_VIEW 拉起外部浏览器，
    // 这里统一换成主题色 + 应用内导航的自定义 Span。
    for (span in spannable.getSpans(0, spannable.length, URLSpan::class.java)) {
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val url = span.url
        spannable.removeSpan(span)
        if (start < 0 || end <= start) continue
        // 与数据层 <a> 区间重叠的跳过：<a> 的 href 比正文里的裸链接更权威
        if (links.any { start < it.end && end > it.start }) continue
        // Linkify 按 IRI 规则会把紧贴链接的中文一起圈进来（详见 trimCjkEdges），
        // 首尾各收一遍：「文档：example.com」不能把「文档：」也算成链接
        val (trimmedStart, trimmedEnd) = trimCjkEdges(text, start, end)
        // Linkify 只会给匹配文本补 scheme 前缀、不会改写正文，
        // 所以点击地址 = 原前缀 + 裁剪后的正文，避免点开时还带着一串中文
        val matchedText = text.substring(start, end)
        val clickUrl = if (url.endsWith(matchedText)) {
            url.dropLast(matchedText.length) + text.substring(trimmedStart, trimmedEnd)
        } else {
            url
        }
        spannable.setSpan(
            textLinkSpan(url = clickUrl, linkColor = linkColor, onLinkClick = onLinkClick),
            trimmedStart,
            trimmedEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    // <a> 标签还原出的链接最后挂上，优先级最高
    for (link in links) {
        spannable.setSpan(
            textLinkSpan(url = link.url, linkColor = linkColor, onLinkClick = onLinkClick),
            link.start,
            link.end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return spannable
}

/**
 * 判断点击坐标是否落在链接 Span 上：命中则本次点击留给 LinkMovementMethod
 * 打开链接，不再触发外层的其它点击行为（如气泡菜单）。
 * 坐标换算与 LinkMovementMethod 一致：事件坐标减 padding 加 scroll 转成文本
 * 布局坐标，再按行取字符 offset 查询 Span。
 */
internal fun hitLinkSpan(textView: TextView, x: Float, y: Float): Boolean {
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
 * 把 [start, end) 区间首尾的连续中日韩文字与全角标点剥掉，返回收缩后的区间。
 *
 * 背景：Linkify 用的是 IRI 规则，`Patterns.GOOD_IRI_CHAR` 覆盖了 `\u00A0-\uD7FF`
 * 与 `\uFDF0-\uFFEF` 等区段，中文和全角标点都算合法 URL 字符，于是：
 * - 头部：「文档：example.com」会把「文档：」一起圈进链接（全角冒号 U+FF1A 也合法）；
 * - 尾部：「https://xxx，很好用」会把逗号后的半句话一起圈进去。
 * 两端各收一遍即可，中间的中文路径保持不动。
 * 裁完若不剩一个像样的域名（太短，或以 `.` 之类开头），说明整条都是中文，
 * 放弃裁剪保持原样。
 */
private fun trimCjkEdges(text: CharSequence, start: Int, end: Int): Pair<Int, Int> {
    var trimmedStart = start
    var trimmedEnd = end
    while (trimmedStart < trimmedEnd && isCjkOrFullWidth(text[trimmedStart])) trimmedStart++
    while (trimmedEnd > trimmedStart && isCjkOrFullWidth(text[trimmedEnd - 1])) trimmedEnd--
    // 裁完必须以字母或数字开头，否则像「中文.com」会被裁成「.com」这种无效地址
    val startsLikeUrl = trimmedStart < trimmedEnd && text[trimmedStart].isLetterOrDigit()
    return if (startsLikeUrl && trimmedEnd - trimmedStart >= MIN_LINK_TEXT_LENGTH) {
        trimmedStart to trimmedEnd
    } else {
        start to end
    }
}

/** 判断字符是否属于中日韩文字或全角标点（这些字符紧贴链接时更像是正文而不是 URL） */
private fun isCjkOrFullWidth(c: Char): Boolean = when (c.code) {
    in 0x3000..0x303F, // 中文标点：、。〈〉《》「」【】
    in 0x3040..0x30FF, // 日文假名
    in 0x3400..0x4DBF, // CJK 扩展 A
    in 0x4E00..0x9FFF, // CJK 基本区
    in 0xAC00..0xD7AF, // 韩文音节
    in 0xF900..0xFAFF, // CJK 兼容
    in 0xFF00..0xFFEF  // 全角字母与标点：，！？：；（）等
    -> true
    else -> false
}

/** 构造一个正文内可点击链接的 Span：主题色 + 下划线，点击交给 [onLinkClick] 打开。 */
private fun textLinkSpan(
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
