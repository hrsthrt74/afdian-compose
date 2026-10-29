package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.ai.AiHistoryEntry
import com.hrsthrt74.afdian.ui.util.aiAnswerSummary
import com.hrsthrt74.afdian.ui.util.aiHistoryGroups
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * AI 记录的纯逻辑回归测试。
 *
 * 覆盖三块：快照构造时的字段兜底（id / 时间戳 / 思考截断）、JSON 的宽容解析（前向兼容）、
 * 以及列表页的时间分组与摘要清洗。仓库本身依赖 Context + DataStore，不在此测试范围内。
 */
class AiHistoryTest {

    // 与 AiHistoryStore 中保持一致的宽容解析配置
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Test
    fun createFillsIdAndTimestamp() {
        val entry = AiHistoryEntry.create(
            scene = AiHistoryEntry.SCENE_INCOME,
            title = "AI 收入分析",
            query = "按日 · 近 30 天",
            answer = "## 结论\n本月收入环比上升。",
        )

        assertTrue("id 不应为空", entry.id.isNotBlank())
        assertTrue("createdAt 应被填充", entry.createdAt > 0L)
        assertEquals(AiHistoryEntry.SCENE_INCOME, entry.scene)
        assertEquals("按日 · 近 30 天", entry.query)
    }

    @Test
    fun createKeepsAnswerVerbatimButTruncatesReasoning() {
        // 正文是用户唯一的产物，含标题 / 表格 / 换行都必须原样保留
        val answer = "# 标题\n\n| 月份 | 收入 |\n| --- | --- |\n| 10 | 128 |\n"
        val longReasoning = "想".repeat(AiHistoryEntry.MAX_REASONING_CHARS + 500)

        val entry = AiHistoryEntry.create(
            scene = AiHistoryEntry.SCENE_INCOME,
            title = "AI 收入分析",
            query = "按月 · 全部",
            answer = answer,
            reasoning = longReasoning,
            thinkingSeconds = 12,
            model = "deepseek-chat",
            provider = "deepseek",
        )

        assertEquals(answer, entry.answer)
        assertEquals(AiHistoryEntry.MAX_REASONING_CHARS, entry.reasoning.length)
        assertEquals(12, entry.thinkingSeconds)
        assertEquals("deepseek-chat", entry.model)
        assertEquals("deepseek", entry.provider)
    }

    @Test
    fun createLeavesShortReasoningUntouched() {
        val entry = AiHistoryEntry.create(
            scene = AiHistoryEntry.SCENE_INCOME,
            title = "AI 收入分析",
            query = "按周 · 近 90 天",
            answer = "分析正文",
            reasoning = "简短思考",
        )

        assertEquals("简短思考", entry.reasoning)
    }

    @Test
    fun entrySurvivesJsonRoundTrip() {
        val original = listOf(
            AiHistoryEntry.create(
                scene = AiHistoryEntry.SCENE_INCOME,
                title = "AI 收入分析",
                query = "按日 · 近 7 天",
                answer = "第一行\n第二行",
                reasoning = "思考",
                thinkingSeconds = 3,
                model = "glm-5.3-flash",
                provider = "zhipu",
                id = "fixed-id",
                now = 1_700_000_000_000L,
            ),
        )

        val encoded = json.encodeToString(ListSerializer(AiHistoryEntry.serializer()), original)
        val decoded = json.decodeFromString(ListSerializer(AiHistoryEntry.serializer()), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun unknownFieldsAndNewSceneDoNotBreakDecoding() {
        // 将来加字段 / 加场景后，旧版本客户端读新数据不能整批丢光
        val raw = """
            [{"id":"a1","scene":"future_scene","title":"标题","query":"摘要",
              "answer":"正文","createdAt":1700000000000,"futureField":123}]
        """.trimIndent()

        val decoded = json.decodeFromString(ListSerializer(AiHistoryEntry.serializer()), raw)

        assertEquals(1, decoded.size)
        assertEquals("future_scene", decoded.single().scene)
        assertEquals("", decoded.single().reasoning)
        assertEquals(0, decoded.single().thinkingSeconds)
    }

    @Test
    fun groupsEntriesByRecencyBucket() {
        // 固定一个本地时间的正午作为「现在」，避免测试在凌晨跑时把「今天」算成「昨天」
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 29, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val entries = listOf(
            entryAt(now - 60_000L),                 // 1 分钟前 → 刚刚
            entryAt(now - 3 * 3_600_000L),          // 今天 09:00 → 今天
            entryAt(now - 24 * 3_600_000L),         // 昨天 12:00 → 昨天
            entryAt(now - 3 * 86_400_000L),         // 10 月 26 日 12:00 → x月d日
        )

        val groups = aiHistoryGroups(entries, now = now)

        assertEquals(listOf("刚刚", "今天", "昨天", "10月26日"), groups.map { it.title })
        assertEquals(listOf(1, 1, 1, 1), groups.map { it.entries.size })
    }

    @Test
    fun groupsKeepSameBucketTogether() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 29, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 两条「今天」应合成一组，且组内保持输入顺序（列表数据本身已按时间倒序）
        val entries = listOf(entryAt(now - 3_600_000L), entryAt(now - 6 * 3_600_000L))

        val groups = aiHistoryGroups(entries, now = now)

        assertEquals(1, groups.size)
        assertEquals("今天", groups.single().title)
        assertEquals(entries, groups.single().entries)
    }

    @Test
    fun emptyInputProducesNoGroups() {
        assertTrue(aiHistoryGroups(emptyList()).isEmpty())
    }

    @Test
    fun answerSummaryStripsMarkdownAndCollapsesWhitespace() {
        val answer = """
            ## 本月结论

            - **收入** 环比上升 12%
            - `退款` 略有增加
        """.trimIndent()

        val summary = aiAnswerSummary(answer)

        assertTrue("不应残留 Markdown 标记：$summary", !summary.contains("#") && !summary.contains("*") && !summary.contains("`"))
        assertTrue("不应包含换行：$summary", !summary.contains("\n"))
        assertTrue(summary.startsWith("本月结论"))
    }

    @Test
    fun answerSummaryTruncatesAndMarksEllipsis() {
        val summary = aiAnswerSummary("收".repeat(200), limit = 60)

        // 60 个正文字符 + 一个省略号
        assertEquals(61, summary.length)
        assertTrue(summary.endsWith("…"))
    }

    /** 构造一条只有时间有意义的记录，其余字段给能过长度校验的最小值。 */
    private fun entryAt(createdAt: Long) = AiHistoryEntry.create(
        scene = AiHistoryEntry.SCENE_INCOME,
        title = "AI 收入分析",
        query = "按日 · 近 30 天",
        answer = "正文",
        now = createdAt,
    )
}
