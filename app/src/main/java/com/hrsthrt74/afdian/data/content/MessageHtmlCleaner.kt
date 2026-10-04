package com.hrsthrt74.afdian.data.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 私信正文 HTML 清洗结果：
 * - text：清洗后的展示文本；
 * - links：文本里的 <a> 链接区间，展示层据此生成可点击 Span。
 */
internal data class CleanedMessageText(
    val text: String,
    val links: List<MessageTextLink>,
)

/**
 * 私信正文 HTML 清洗器。
 *
 * 客服/系统自动回复的正文是 HTML 片段（<br> 换行、<a> 超链接、&nbsp; 等实体），
 * 直接展示会把标签原样暴露给用户。这里只做「简单清洗」：
 * - <br>（含 <br/>、大小写变体）→ 换行，与网页端渲染一致，连续 <br> 即产生空行；
 * - <a href="...">文字</a> → 保留链接文字并记录区间；
 * - 其余标签剥壳保留文本，HTML 实体（&amp; 等）由 Jsoup 统一解码。
 *
 * 纯文本消息（绝大多数手打内容）不含 '<'，走快速路径原样返回，零解析开销。
 */
internal fun cleanMessageHtml(raw: String): CleanedMessageText {
    // 快速路径：无标签的正文不需要 Jsoup 解析
    if (!raw.contains('<')) return CleanedMessageText(raw, emptyList())

    val sb = StringBuilder()
    val links = mutableListOf<MessageTextLink>()

    // 行首需要剔除的空白字符：半角空格、制表符、&nbsp; 解码出的不间断空格、全角空格
    val leadingWhitespace = charArrayOf(' ', '\t', '\u00A0', '\u3000')

    // 追加文本；处于行首（文本开头或刚换行）时丢弃缩进空白，
    // 避免「<br>  <br>」里的夹缝空白在换行后顶出一行空格
    fun appendText(value: String) {
        val text = if (sb.isEmpty() || sb.last() == '\n') value.trimStart(*leadingWhitespace) else value
        sb.append(text)
    }

    // 追加换行；每个 <br> 都换一行，与网页端行为一致（两个 <br> 之间即空行，
    // 夹在中间的空白行由 appendText 的行首剔除逻辑收成空行，不会顶出多余空格）
    fun appendBreak() {
        sb.append('\n')
    }

    // 深度优先遍历节点树，只关心换行与链接，其余标签剥壳保留内部文本
    fun walk(node: Node) {
        when (node) {
            is TextNode -> appendText(node.wholeText)
            is Element -> when (node.tagName().lowercase()) {
                "br" -> appendBreak()
                "a" -> {
                    // 链接文字里可能嵌套其他标签，同样走递归；区间取遍历前后的游标
                    val start = sb.length
                    node.childNodes().forEach(::walk)
                    val end = sb.length
                    val href = node.attr("href").trim()
                    // 空文字或空链接不记录，避免生成无效区间
                    if (href.isNotEmpty() && end > start) {
                        links += MessageTextLink(start, end, href)
                    }
                }
                else -> node.childNodes().forEach(::walk)
            }
            else -> node.childNodes().forEach(::walk)
        }
    }

    Jsoup.parse(raw).body().childNodes().forEach(::walk)

    // 去掉首尾空白换行；链接区间同步平移，被裁剪波及的区间直接丢弃
    val text = sb.toString()
    val trimmed = text.trim(*leadingWhitespace, '\n')
    if (trimmed.isEmpty()) return CleanedMessageText("", emptyList())
    val headCut = text.indexOf(trimmed)
    val shiftedLinks = links.mapNotNull { link ->
        val start = link.start - headCut
        val end = link.end - headCut
        if (start in trimmed.indices && end <= trimmed.length && end > start) {
            MessageTextLink(start, end, link.url)
        } else {
            null
        }
    }
    return CleanedMessageText(trimmed, shiftedLinks)
}
