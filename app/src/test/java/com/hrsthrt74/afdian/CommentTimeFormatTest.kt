package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.formatCommentTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 评论时间展示的回归测试。
 *
 * 重点钉住两类容易出错的边界：
 * 1. 30 天内必须走相对时间（不能退化成绝对日期）；
 * 2. 跨年的绝对日期必须带年份——之前只输出「M月D日」，去年的评论会被误读成今年的。
 *
 * 时间戳统一用 [Calendar] 构造，与被测函数取年月日的方式一致，换时区跑也不会飘。
 */
class CommentTimeFormatTest {

    /** 构造某个本地时间点的秒级时间戳（默认中午 12 点，避开跨日边界）。 */
    private fun tsOf(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, hour, 0, 0)
        return cal.timeInMillis / 1000
    }

    /** 「现在」钉死在 2026-10-22 12:00，所有用例都相对它算。 */
    private val now = tsOf(2026, 10, 22)

    @Test
    fun blankTimestampRendersEmpty() {
        assertEquals("", formatCommentTime(null, now))
        assertEquals("", formatCommentTime(0, now))
        assertEquals("", formatCommentTime(-100, now))
    }

    @Test
    fun withinMinuteShowsJustNow() {
        assertEquals("刚刚", formatCommentTime(now - 30, now))
        // 服务端时间比本地快时 diff 为负，也要落进「刚刚」而不是「-1 分钟前」
        assertEquals("刚刚", formatCommentTime(now + 30, now))
    }

    @Test
    fun minutesAndHoursAreRelative() {
        assertEquals("5 分钟前", formatCommentTime(now - 5 * 60, now))
        assertEquals("3 小时前", formatCommentTime(now - 3 * 3600, now))
    }

    @Test
    fun withinThirtyDaysShowsDaysAgo() {
        assertEquals("5 天前", formatCommentTime(now - 5 * 86400, now))
    }

    @Test
    fun sameYearAbsoluteDateHasNoYear() {
        // 今年 3 月 5 日：超过 30 天，同年只写月日
        assertEquals("3月5日", formatCommentTime(tsOf(2026, 3, 5), now))
    }

    @Test
    fun lastYearAbsoluteDateKeepsYear() {
        // 核心修复点：去年的评论必须带年份，否则会被当成今年的 3 月 5 日
        assertEquals("2025年3月5日", formatCommentTime(tsOf(2025, 3, 5), now))
        // 更早的年份同样带年
        assertEquals("2024年12月31日", formatCommentTime(tsOf(2024, 12, 31), now))
    }

    @Test
    fun crossYearWithinThirtyDaysStaysRelative() {
        // 元旦前后看去年年底的评论：不足 30 天，仍然显示「N 天前」而不是日期
        val newYear = tsOf(2026, 1, 5)
        assertEquals("6 天前", formatCommentTime(tsOf(2025, 12, 30), newYear))
    }
}
