package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.ai.aiAnswerPreview
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [aiAnswerPreview] 的纯函数单测：Markdown → 一行纯文本。
 *
 * 这个函数只为「消息页卡片的一行摘要」服务，所以测的是「记号有没有被剥干净」，
 * 不测排版还原（没人指望预览保留缩进和层级）。
 */
class AiAnswerPreviewTest {

    @Test
    fun `标题与列表前缀和加粗记号都被剥掉`() {
        val answer = "## 收入概览\n- **总计**：¥100"
        assertEquals("收入概览 总计：¥100", aiAnswerPreview(answer))
    }

    @Test
    fun `链接只留文字，图片整个丢掉，行内代码去反引号`() {
        val answer = "见[帮助文档](https://example.com) ![图](https://example.com/a.png) 与 `in_detail` 字段"
        // 图片被替换成空格后与相邻空格合并，结果里不会留下空洞
        assertEquals("见帮助文档 与 in_detail 字段", aiAnswerPreview(answer))
    }

    @Test
    fun `表格分隔行整行丢弃，竖线换成空格`() {
        val answer = "| 日期 | 金额 |\n|---|---|\n| 10-01 | ¥5 |"
        assertEquals("日期 金额 10-01 ¥5", aiAnswerPreview(answer))
    }

    @Test
    fun `代码块整段丢弃，只留上下文`() {
        val answer = "结论如下：\n```\nval a = 1\n```\n建议保持"
        assertEquals("结论如下： 建议保持", aiAnswerPreview(answer))
    }

    @Test
    fun `空正文返回空串，由调用方兜底`() {
        assertEquals("", aiAnswerPreview(""))
        assertEquals("", aiAnswerPreview("   \n  "))
    }

    @Test
    fun `超出上限直接截断`() {
        assertEquals("abc", aiAnswerPreview("abcdef", maxChars = 3))
    }

    @Test
    fun `斜体记号不会误伤下划线标识符`() {
        // 只处理 *x* / **x** 这类成对星号，`in_detail` 的下划线必须原样保留
        val answer = "看 *in_detail* 字段"
        assertEquals("看 in_detail 字段", aiAnswerPreview(answer))
    }
}
