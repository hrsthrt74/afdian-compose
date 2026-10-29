package com.hrsthrt74.afdian.data.ai

import com.hrsthrt74.afdian.data.stats.IncomeAiRange
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import com.hrsthrt74.afdian.data.stats.IncomeStatType
import com.hrsthrt74.afdian.data.stats.MonthlyBillRecord
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 各 AI 场景的提示词与数据表格构建器。
 *
 * 职责边界：**纯函数、不发请求、不碰网络**——数据由调用方（各 ViewModel / MCP 工具）拉好，
 * 这里只负责「结构化数据 → AI 可读文本」。全部做成顶层纯函数，单测可以直接喂样例数据。
 *
 * 两个用途共用同一份数据表格：
 * - 应用内「问 AI」= 分析指令 + 数据表格；
 * - MCP 工具 = 纯数据表格（分析交给外部 AI 客户端）。
 */
object AiPromptBuilder {

    /**
     * 收入分析的系统指令（与旧版「问 AI」复制方案共用同一份文案，见
     * IncomeStatRepository.fetchAllMarkdown——那边现在也委托到这里，保证口径一致）。
     */
    const val INCOME_ANALYSIS_PROMPT =
        "你是一名专业的数据分析师，请分析以下爱发电每日数据。只基于数据，不要编造。\n" +
            "要求：计算付费转化率、人均发电金额、单次金额、老客占比、访问人均价值。\n" +
            "输出：整体概况（总量、日均、转化指标）、趋势与峰值/低谷、转化与付费质量、" +
            "老客与复购、异常点（如高访问低转化、高金额少人数、零收入日）、结论与3-5条可执行建议"

    /** 收入明细 Markdown 表格的表头与分隔行（列含义写在表头里，AI 直接可读）。 */
    private const val INCOME_TABLE_HEADER =
        "| 日期 | 访问人数 | 发电金额(元) | 发电次数 | 发电人数 | 老客数 |\n" +
            "|---|---|---|---|---|---|\n"

    /**
     * 生成「问 AI」收入分析的完整提示词：内置指令 + 数据表格。
     * 提示词在前、数据在后（旧版复制方案就是如此，保持口径一致）。
     *
     * @param range 用户选的时间窗口；null 表示不设下界（复制提示词走的是全量口径）。
     *              除了说明窗口，还会把实际日期跨度写进去——模型据此才知道数据到哪天为止，
     *              不会把「还没来到的日子」当成零收入。
     */
    fun incomeAnalysisPrompt(
        type: IncomeStatType,
        records: List<IncomeStatRecord>,
        range: IncomeAiRange? = null,
    ): String {
        val scopeText = range?.let { "近 ${it.label}" } ?: "全部"
        // 接口按日期倒序返回：首条最新、末条最旧
        val spanText = if (records.isEmpty()) {
            ""
        } else {
            "（${records.last().displayDate} ~ ${records.first().displayDate}，共 ${records.size} 条）"
        }
        return INCOME_ANALYSIS_PROMPT + "\n\n" +
            "以下是「${type.title}」维度的${scopeText}收入统计数据$spanText：\n\n" +
            buildIncomeTable(records)
    }

    /** 收入明细 → Markdown 表格。 */
    fun buildIncomeTable(records: List<IncomeStatRecord>): String = buildString {
        append(INCOME_TABLE_HEADER)
        for (record in records) {
            append("| ${record.displayDate} | ${record.uv} | ${record.amount.toPlainString()} " +
                "| ${record.orderCount} | ${record.userCount} | ${record.oldUserCount} |\n")
        }
    }

    /** 月度账单 → Markdown 表格（MCP 工具用）。 */
    fun buildMonthlyBillsTable(bills: List<MonthlyBillRecord>): String = buildString {
        append("| 月份 | 总金额(元) | 抽成后(元) | 发电人数 |\n|---|---|---|---|\n")
        for (bill in bills) {
            append("| ${bill.month.year}-${"%02d".format(bill.month.monthValue)} ")
            append("| ${bill.totalAmount.toPlainString()} | ${bill.creatorAmount.toPlainString()} ")
            append("| ${bill.sponsorCount} |\n")
        }
    }

    /** 金额的展示辅助：去除多余的尾零（1.00 → 1、1.50 → 1.5），AI 阅读更干净。 */
    fun BigDecimal.plainTrimmed(): String {
        val stripped = stripTrailingZeros()
        // stripTrailingZeros 对 0 会给出 0E-8 这类科学计数法形态；BigInteger 永远不会用科学计数法，直接 toString
        return if (stripped.scale() < 0) stripped.toBigInteger().toString() else stripped.toPlainString()
    }

}
