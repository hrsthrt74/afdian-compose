package com.hrsthrt74.afdian.data.stats

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 「问 AI」的时间尺度：决定喂给模型的数据窗口。
 *
 * 统一用「起始日下界」表达而不是给每种粒度单独定规则：统计接口按日/周/月三种粒度返回，
 * 按周/按月时 `date_str` 是周期起始日，只要起始日落在窗口内就算命中
 * （跨窗口边界的周期不拆开，也不按比例折算）。
 */
enum class IncomeAiRange(val label: String, val days: Int?) {
    Day7("7天", 7),
    Day30("30天", 30),
    Quarter("3个月", 90),
    HalfYear("6个月", 180),
    All("全部", null);

    /**
     * 窗口的自然语言说法：「近7天」「近3个月」。
     *
     * [All] 没有下界，套「近」会变成「近全部」，所以它直接用 label。
     * 摘要文案（底栏气泡 / AI 记录里的问题摘要）统一走这里，避免各处各拼一遍。
     */
    val windowText: String
        get() = if (days == null) label else "近$label"

    /**
     * 窗口起始日（含当天）。「近 7 天」= 今天往前数 7 天，所以是 [days] - 1 天前。
     * [All] 没有下界，返回 null。
     */
    fun cutoff(today: LocalDate = LocalDate.now()): LocalDate? =
        days?.let { today.minusDays((it - 1).toLong()) }

    companion object {
        /** 打开结果底栏时的默认窗口。 */
        val Default = Day30
    }
}

/**
 * 「问 AI」的预取进度快照（UI 只读这几个数，不持有记录本体）。
 *
 * 之所以单独成一个不可变对象：发送时要判断「手上的数据够不够覆盖所选窗口」，
 * 而这个判断必须能在数据还没拉完时随时做——接口按日期倒序返回，
 * 所以「已拉到的最旧日期」就是当前覆盖下界。
 */
data class IncomeAiFeedState(
    /** 已拉到的记录条数。 */
    val count: Int = 0,
    /** 已拉到的最旧一条的日期；一条都没有时为 null。 */
    val oldestDate: LocalDate? = null,
    /** 是否已经拉到数据尽头（空页或 has_more=false）。 */
    val complete: Boolean = false,
    /** 拉取失败原因；失败后不再空等，用手上已拿到的部分继续分析。 */
    val error: String? = null,
) {
    /**
     * 现有数据是否已经覆盖 [range]。
     *
     * - 已经拉到尽头（含一条都没有的情况）→ 覆盖；
     * - 拉到的最旧日期不晚于窗口起始日 → 覆盖；
     * - 「全部」只有拉完才算覆盖；
     * - 拉取出错 → 返回 true：让调用方别再空等，用已有的部分先分析。
     */
    fun covers(range: IncomeAiRange, today: LocalDate = LocalDate.now()): Boolean {
        if (error != null) return true
        if (complete) return true
        val cutoff = range.cutoff(today) ?: return false
        val oldest = oldestDate ?: return false
        return !oldest.isAfter(cutoff)
    }
}

/** 解析统计接口的 YYYYMMDD 日期；非法格式返回 null。 */
fun parseIncomeStatDate(dateStr: String): LocalDate? =
    runCatching { LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()

/**
 * 按时间窗口筛选记录。
 *
 * 解析不了日期的记录**保留**：宁可多给一条脏行让模型忽略，也不要悄悄少喂数据
 * （接口的 date_str 一直是 YYYYMMDD，真正的解析失败只可能是上游异常）。
 */
fun filterIncomeRecords(
    records: List<IncomeStatRecord>,
    range: IncomeAiRange,
    today: LocalDate = LocalDate.now(),
): List<IncomeStatRecord> {
    val cutoff = range.cutoff(today) ?: return records
    return records.filter { record ->
        val date = parseIncomeStatDate(record.dateStr)
        date == null || !date.isBefore(cutoff)
    }
}
