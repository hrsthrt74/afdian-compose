package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.platform.LocalUriHandler
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.hrsthrt74.afdian.AppLog
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 轻量级 HTML 渲染组件（原生 Compose 实现，不用 WebView）。
 *
 * 用 Jsoup 把爱发电动态正文（HTML）解析成节点树，再映射为 Compose 组件：
 * - <p>/<br>                      → 段落 / 换行
 * - <strong>/<b>/<em>/<i>/<u>/<s> → AnnotatedString 内联样式
 * - <a href>                      → 可点击链接（系统浏览器打开）
 * - <img> / <figure>              → AsyncImage（coil 加载，宽度撑满、圆角）
 * - <blockquote>                  → 左侧竖线 + 缩进的引用块
 * - <h1>~<h6>                     → 不同字号字重的标题
 * - <ul>/<ol>/<li>                → 列表布局（圆点 / 数字前缀）
 * - <pre>                         → 等宽字体代码块
 * - <hr>                          → 分隔线
 *
 * 未识别的标签按「透明容器」处理，继续递归渲染其子节点，
 * 保证未知结构也能尽量显示文本内容。
 *
 * @param html 动态正文 HTML 字符串
 * @param modifier 外部修饰符
 */
@Composable
fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier,
    onUrlClick: ((String) -> Unit)? = null,
) {
    // 空白正文直接不占布局
    if (html.isBlank()) return
    val uriHandler = LocalUriHandler.current
    // 有自定义处理器时交给上层，否则保持原有行为：用系统方式打开链接。
    val handleUrl: (String) -> Unit = { url -> onUrlClick?.invoke(url) ?: uriHandler.openUri(url) }
    // parseBodyFragment：按片段解析并包一层 <body>，取其子节点即为顶层内容
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        RenderBlocks(Jsoup.parseBodyFragment(html).body().childNodes(), handleUrl)
    }
}

/** 块级元素集合：遇到它们时结束当前内联段落，单独成块渲染 */
private val BLOCK_TAGS = setOf(
    "p", "div", "br", "img", "blockquote", "ul", "ol", "li", "figure", "figcaption",
    "h1", "h2", "h3", "h4", "h5", "h6", "hr", "table", "pre", "section", "article",
)

/** 依次渲染一组节点；连续的内联节点会合并成一个段落，避免逐词断行 */
@Composable
private fun RenderBlocks(nodes: List<Node>, onUrlClick: (String) -> Unit) {
    // 按「块级元素」切段：段与段之间是独立的 Compose 子项，
    // 段内的连续内联节点（文本/加粗/链接…）合并进同一个 Text
    var inlineBuffer = mutableListOf<Node>()
    val flush: @Composable () -> Unit = {
        if (inlineBuffer.any { it.hasTextContent() }) {
            InlineParagraph(inlineBuffer.toList(), onUrlClick)
        }
    }
    for (node in nodes) {
        val isBlock = node is Element && node.tagName().lowercase() in BLOCK_TAGS
        if (isBlock) {
            flush()
            inlineBuffer = mutableListOf()
            RenderSingleBlock(node as Element, onUrlClick)
        } else {
            inlineBuffer += node
        }
    }
    flush()
}

/** 判断节点子树里是否含有非空白文本，用来跳过纯空白的内联段落 */
private fun Node.hasTextContent(): Boolean = when (this) {
    is TextNode -> !isBlank
    is Element -> childNodes().any { it.hasTextContent() }
    else -> false
}

/** 单个块级元素的分发渲染（标题/正文全部使用 MiuixTheme.textStyles 现成样式） */
@Composable
private fun RenderSingleBlock(element: Element, onUrlClick: (String) -> Unit) = when (val tag = element.tagName().lowercase()) {
    "br" -> Spacer(modifier = Modifier.height(20.dp))
    "img", "figure" -> HtmlImage(element)
    "blockquote" -> BlockQuote(element, onUrlClick)
    // 标题层级 → miuix 字阶：h1=title2(24sp) h2=title3(20sp) h3=title4(18sp) 其余=subtitle(14sp Bold)
    "h1" -> Heading(element, MiuixTheme.textStyles.title2)
    "h2" -> Heading(element, MiuixTheme.textStyles.title3)
    "h3" -> Heading(element, MiuixTheme.textStyles.title4)
    "h4", "h5", "h6" -> Heading(element, MiuixTheme.textStyles.subtitle)
    "ul" -> ListBlock(element, ordered = false, onUrlClick = onUrlClick)
    "ol" -> ListBlock(element, ordered = true, onUrlClick = onUrlClick)
    "hr" -> Divider()
    "pre" -> PreBlock(element)
    // figcaption 跟随 figure 已处理；p/div 等「文字容器」递归其子节点，
    // 其余未知标签也走这里当作透明容器，尽量把文本显示出来
    else -> if (tag == "figcaption") Caption(element) else RenderBlocks(element.childNodes(), onUrlClick)
}

/** 把节点列表拆成若干「内联段」，供引用块/列表项等容器复用 */
private fun List<Node>.chunkedByInline() = listOf(this)

/** 段落内联内容：<strong>/<em>/<a> 等样式合并进一个 AnnotatedString */
@Composable
private fun InlineParagraph(nodes: List<Node>, onUrlClick: (String) -> Unit) {
    val linkColor = MiuixTheme.colorScheme.primary
    val textColor = MiuixTheme.colorScheme.onSurface

    val text = buildAnnotatedString {
        // 递归追加内联节点：bold/italic/underline/strike 是从父标签累积下来的样式状态
        fun appendInline(node: Node, bold: Boolean, italic: Boolean, underline: Boolean, strike: Boolean, code: Boolean, linkUrl: String?) {
            when (node) {
                is TextNode -> {
                    // HTML 正文里常见的「软换行 / 段落空行」用的是真正的 \n，
                    // 不能直接当成普通空白折叠掉，否则换行会全部变成空格、挤成一段。
                    // 处理策略：
                    //   1. 统一换行符为 \n（兼容 \r\n、\r）
                    //   2. 连续的「空格/制表符/不换行空格」折叠成一个空格（HTML 规范）
                    //   3. 去掉换行符紧邻的多余空格，避免行首/行尾多余留白
                    //   4. 连续 3 个以上换行折叠为 2 个，防止超长空行
                    val content = node.wholeText
                        .replace("\r\n", "\n")
                        .replace("\r", "\n")
                        .replace(Regex("[ \t\u00A0]+"), " ")
                        .replace(Regex(" *\n *"), "\n")
                        .replace(Regex("\n{3,}"), "\n\n")
                    if (content.isEmpty()) return
                    val span = SpanStyle(
                        fontWeight = if (bold) FontWeight.Bold else null,
                        fontStyle = if (italic) FontStyle.Italic else null,
                        textDecoration = when {
                            underline && strike -> TextDecoration.LineThrough + TextDecoration.Underline
                            underline -> TextDecoration.Underline
                            strike -> TextDecoration.LineThrough
                            else -> null
                        },
                        fontFamily = if (code) FontFamily.Monospace else null,
                    )
                    if (linkUrl != null) {
                        // 链接：用 LinkAnnotation.Url，Compose 会自动调起系统浏览器打开
                        // 自定义监听器让上层可以拦截创作者主页等特殊链接。
                        // 未被拦截的 URL 仍由上层回退到系统浏览器处理。
                        withLink(
                            LinkAnnotation.Url(
                                url = linkUrl,
                                linkInteractionListener = LinkInteractionListener { onUrlClick(linkUrl) },
                            )
                        ) {
                            withStyle(span.copy(color = linkColor, textDecoration = TextDecoration.Underline)) { append(content) }
                        }
                    } else {
                        withStyle(span) { append(content) }
                    }
                }
                is Element -> {
                    val tag = node.tagName().lowercase()
                    node.childNodes().forEach {
                        appendInline(
                            it,
                            bold = bold || tag == "b" || tag == "strong",
                            italic = italic || tag == "i" || tag == "em",
                            underline = underline || tag == "u" || tag == "ins",
                            strike = strike || tag == "s" || tag == "strike" || tag == "del",
                            code = code || tag == "code" || tag == "kbd",
                            linkUrl = node.attr("href").takeIf { tag == "a" && it.isNotBlank() } ?: linkUrl,
                        )
                    }
                }
                else -> {}
            }
        }
        nodes.forEach { appendInline(it, bold = false, italic = false, underline = false, strike = false, code = false, linkUrl = null) }
    }

    // 正文用 miuix 的 paragraph 样式（17sp）；miuix 默认行高只有 1.2em，
    // 长文阅读太挤，这里放宽到 1.6em（中文长文的舒适阅读区间约 1.5~1.7）
    Text(
        text = text,
        style = MiuixTheme.textStyles.paragraph.copy(lineHeight = 1.6f.em),
        color = textColor,
    )
}

/** 把正文里五花八门的图片地址归一化成 Coil 能直接加载的 https 绝对地址（实现见 PostImageGrid.kt） */

/** 正文图片：<img> 或 <figure> 里包的图片（可多张），宽度撑满、8dp 圆角 */@Composable
private fun HtmlImage(element: Element) {
    val scope = element.select("img").ifEmpty { element.getElementsByTag("img") }
    val images = if (element.tagName() == "img") listOf(element) else scope
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEach { img ->
            // 一次取多个候选属性：部分编辑器/懒加载方案把真实地址放在 data-* 里
            val raw = listOf("src", "data-src", "data-original-src", "data-lazy-src")
                .firstNotNullOfOrNull { img.attr(it).trim().takeIf(String::isNotBlank) }
            if (raw.isNullOrBlank()) {
                // 四个属性都拿不到地址：打日志留痕，别再无声跳过
                AppLog.w("HtmlContent", "<img> 无可用地址，已跳过: ${img.outerHtml().take(200)}")
                return@forEach
            }
            val model = ImageRequest.Builder(context)
                .data(normalizeImageUrl(raw))
                .listener(
                    // 失败落日志：URL 形态五花八门，排查「图挂了」时先看这里
                    onError = { _, result ->
                        AppLog.w("HtmlContent", "图片加载失败: $raw -> ${result.throwable.message}")
                    },
                )
                .build()
            AsyncImage(
                model = model,
                contentDescription = img.attr("alt").ifBlank { null },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
            )
        }
        // 图注：footnote1 辅助色文本居中显示
        element.selectFirst("figcaption")?.let { caption ->
            val desc = caption.wholeText().trim()
            if (desc.isNotBlank()) {
                Text(
                    text = desc,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** 引用块：左侧主题色竖线 + 内容缩进，内部仍支持内联样式与嵌套段落 */
@Composable
private fun BlockQuote(element: Element, onUrlClick: (String) -> Unit) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.primary),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            element.childNodes().chunkedByInline().forEach { chunk ->
                if (chunk.any { it.hasTextContent() }) InlineParagraph(chunk, onUrlClick)
            }
        }
    }
}

/** 标题：直接使用传入的 miuix 字阶样式（title2/title3/title4/subtitle），不自造字号字重 */
@Composable
private fun Heading(element: Element, style: androidx.compose.ui.text.TextStyle) {
    Text(
        text = element.wholeText().trim(),
        style = style,
        color = MiuixTheme.colorScheme.onSurface,
    )
}

/** 图注 */
@Composable
private fun Caption(element: Element) {
    val desc = element.wholeText().trim()
    if (desc.isNotBlank()) {
        Text(
            text = desc,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** 列表：<ol> 数字前缀，<ul> 圆点前缀；<li> 内容仍支持完整内联样式递归 */
@Composable
private fun ListBlock(list: Element, ordered: Boolean, onUrlClick: (String) -> Unit) {
    val items = list.children().filter { it.tagName().lowercase() == "li" }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, li ->
            Row {
                Text(
                    text = if (ordered) "${index + 1}." else "•",
                    style = MiuixTheme.textStyles.paragraph,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.width(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    // 文本部分按内联段落渲染（支持加粗/链接等样式）
                    li.childNodes().chunkedByInline().forEach { chunk ->
                        if (chunk.any { it.hasTextContent() }) InlineParagraph(chunk, onUrlClick)
                    }
                    // 嵌套列表单独渲染一次，避免放进内联循环里重复输出
                    li.children().filter { it.tagName().lowercase() in setOf("ul", "ol") }
                        .forEach {
                            nested -> ListBlock(nested, ordered = nested.tagName().lowercase() == "ol", onUrlClick = onUrlClick)
                        }
                }
            }
        }
    }
}

/** 分隔线 */
@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.25f)),
    )
}

/** 代码块：miuix 没有等宽字阶，仅覆盖 fontFamily 为等宽（字号沿用 body2），浅色圆角背景 */
@Composable
private fun PreBlock(element: Element) {
    Text(
        text = element.wholeText(),
        style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.disabledSecondary)
            .padding(10.dp),
    )
}
