package com.hrsthrt74.afdian.ui.util

import com.hrsthrt74.afdian.data.ai.AiHistoryEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** AI 记录页的分组：一个时间标题 + 该组内的记录。 */
internal data class AiHistoryGroup(val title: String, val entries: List<AiHistoryEntry>)

/**
 * 把 AI 记录按时间切成「刚刚 / 今天 / 昨天 / x月d日」四档。
 *
 * 逻辑与 `HistoryScreen.historyGroups` 完全同口径（同一套零点边界算法），刻意各写一份而不是抽公共泛型：
 * 两者条目类型不同，泛型化要额外传一个 `timestampOf` lambda，反而让两处都更难读。
 * 输入已按时间倒序时，`groupBy` 会保留首次出现的顺序，输出天然就是从新到旧。
 *
 * 放在非 Compose 文件里是为了能被纯 JVM 单测直接覆盖（项目没有 Robolectric）。
 */
internal fun aiHistoryGroups(
    entries: List<AiHistoryEntry>,
    now: Long = System.currentTimeMillis(),
): List<AiHistoryGroup> {
    // 先用 Calendar 算出「今天」和「昨天」的零点时间戳，作为分组边界
    val todayStart = Calendar.getInstance().apply { timeInMillis = now }.let { cal ->
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val yesterdayStart = todayStart - 86_400_000L
    val formatter = SimpleDateFormat("M月d日", Locale.getDefault())

    return entries.groupBy { entry ->
        val ts = entry.createdAt
        when {
            now - ts < 300_000L -> "刚刚"              // 5 分钟内
            ts >= todayStart -> "今天"                  // 今天、但不是刚刚
            ts >= yesterdayStart -> "昨天"
            else -> formatter.format(Date(ts))          // 更早：x月x日
        }
    }.entries.map { (title, grouped) -> AiHistoryGroup(title, grouped) }
}

/**
 * 把 Markdown 正文压成一行纯文本摘要，供列表条目使用。
 *
 * 只做最轻量的字符清洗（去掉标题井号、加粗星号、引用符、列表符号），不引 Markdown 解析器：
 * 列表里只显示一行 60 字，正则足够，解析整篇反而增加滚动时的开销。
 */
internal fun aiAnswerSummary(answer: String, limit: Int = 60): String {
    val flattened = answer
        .replace(Regex("[#*`>_~]"), "")
        .replace(Regex("^\\s*[-+]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (flattened.length <= limit) flattened else flattened.take(limit) + "…"
}
