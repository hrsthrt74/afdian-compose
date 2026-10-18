package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * 「收入明细」按日表格的星期后缀回归测试。
 *
 * 锁住三件事：
 * 1. YYYYMMDD → 星期汉字的映射（周一=一 … 周日=日），跨周边界不错位；
 * 2. 输出格式固定为 `26/09/06 日`（短年份 + 一个半角空格 + 单个汉字），列宽估算依赖这个长度；
 * 3. 非法日期不崩、不拼星期，退回原有的短年份日期，且其余日期格式完全不受影响。
 */
class IncomeStatRecordTest {

    /** 构造一条只关心日期的记录，统计字段一律填 0。 */
    private fun record(dateStr: String) = IncomeStatRecord(
        dateStr = dateStr,
        uv = 0L,
        amount = BigDecimal.ZERO,
        orderCount = 0L,
        userCount = 0L,
        oldUserCount = 0L,
    )

    /** 2026-09-07（周一）到 2026-09-13（周日）连续七天，覆盖 DayOfWeek 1..7 的全部映射。 */
    @Test
    fun weekdayCoversWholeWeek() {
        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
        for ((offset, label) in labels.withIndex()) {
            val day = (7 + offset).toString().padStart(2, '0')
            assertEquals("26/09/$day $label", record("202609$day").displayShortYearDateWithWeekday)
        }
    }

    /** 抽查两个已知日期与真实日历一致：2026-09-06 是周日，2026-10-16 是周五。 */
    @Test
    fun knownDatesMatchCalendar() {
        assertEquals("26/09/06 日", record("20260906").displayShortYearDateWithWeekday)
        assertEquals("26/10/16 五", record("20261016").displayShortYearDateWithWeekday)
    }

    /** 首页用的紧凑格式：月/日 + 星期，不带年份，格式与短年份版只差年份。 */
    @Test
    fun compactWeekdayFormat() {
        assertEquals("10/16 五", record("20261016").displayMonthDayWithWeekday)
        assertEquals("09/06 日", record("20260906").displayMonthDayWithWeekday)
        // 非法日期退回不带星期的紧凑日期
        assertEquals("13/32", record("20261332").displayMonthDayWithWeekday)
        assertEquals("2026-09-06", record("2026-09-06").displayMonthDayWithWeekday)
    }

    /** 非法日期不抛异常、也不拼星期：8 位越界退回短年份拼接，长度不对则原样返回。 */
    @Test
    fun invalidDateFallsBackToShortDate() {
        // 月份 13、日期 32 都越界，LocalDate 解析失败
        assertEquals("26/13/32", record("20261332").displayShortYearDateWithWeekday)
        // 2026 不是闰年，2 月 29 日同样非法
        assertEquals("26/02/29", record("20260229").displayShortYearDateWithWeekday)
        // 空串与带分隔符的日期不满足 8 位，displayShortYearDate 原样返回
        assertEquals("", record("").displayShortYearDateWithWeekday)
        assertEquals("2026-09-06", record("2026-09-06").displayShortYearDateWithWeekday)
    }

    /** 原有三种日期格式不受影响（导出用完整年份、首页用月/日、表格用短年份）。 */
    @Test
    fun existingFormatsStayUnchanged() {
        val record = record("20260906")
        assertEquals("2026/09/06", record.displayDate)
        assertEquals("26/09/06", record.displayShortYearDate)
        assertEquals("09/06", record.displayMonthDay)
    }
}
