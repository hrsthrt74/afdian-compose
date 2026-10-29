package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.ai.AiPromptBuilder
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import com.hrsthrt74.afdian.data.stats.IncomeStatType
import com.hrsthrt74.afdian.data.stats.MonthlyBillRecord
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI 提示词与数据表格构建器的回归测试：应用内「问 AI」与 MCP 工具共用这份输出口径。 */
class AiPromptBuilderTest {

    // ── 收入明细 ──

    @Test
    fun `收入表格的表头与行格式`() {
        val record = IncomeStatRecord(
            dateStr = "20261027",
            uv = 100,
            amount = BigDecimal("12.50"),
            orderCount = 3,
            userCount = 2,
            oldUserCount = 1,
        )
        val table = AiPromptBuilder.buildIncomeTable(listOf(record))
        // 表头六列 + 分隔行 + 一条数据行
        assertTrue(table.startsWith("| 日期 | 访问人数 | 发电金额(元) | 发电次数 | 发电人数 | 老客数 |"))
        assertTrue(table.contains("|---"))
        assertTrue(table.contains("| 2026/10/27 | 100 | 12.50 | 3 | 2 | 1 |"))
    }

    @Test
    fun `收入分析提示词包含指令与维度标题`() {
        val record = IncomeStatRecord("20261027", 1, BigDecimal.ONE, 1, 1, 0)
        val prompt = AiPromptBuilder.incomeAnalysisPrompt(IncomeStatType.Week, listOf(record))
        // 指令在前、数据在后，且带维度说明（周维度）
        assertTrue(prompt.indexOf("数据分析师") < prompt.indexOf("| 日期 |"))
        assertTrue(prompt.contains("「按周」维度"))
    }

    // ── 月度账单 ──

    @Test
    fun `月度账单表格月份补零`() {
        val bills = listOf(
            MonthlyBillRecord(LocalDate.of(2026, 3, 1), BigDecimal("12.00"), BigDecimal("11.00"), 5),
            MonthlyBillRecord(LocalDate.of(2026, 11, 1), BigDecimal("30.00"), BigDecimal("28.00"), 10),
        )
        val table = AiPromptBuilder.buildMonthlyBillsTable(bills)
        assertTrue(table.contains("| 2026-03 | 12.00 | 11.00 | 5 |"))
        assertTrue(table.contains("| 2026-11 | 30.00 | 28.00 | 10 |"))
    }

    // ── 金额展示辅助 ──

    @Test
    fun `金额去尾零且零值不出现科学计数法`() {
        // plainTrimmed 是 AiPromptBuilder 的成员扩展，测试里用 with 注入作用域
        with(AiPromptBuilder) {
            assertEquals("1", BigDecimal("1.00").plainTrimmed())
            assertEquals("1.5", BigDecimal("1.50").plainTrimmed())
            assertEquals("12.34", BigDecimal("12.34").plainTrimmed())
            assertEquals("0", BigDecimal("0.00").plainTrimmed())
        }
    }
}
