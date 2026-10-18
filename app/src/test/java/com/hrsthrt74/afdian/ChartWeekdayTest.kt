package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.stats.CHART_DAILY_WINDOW_DAYS
import com.hrsthrt74.afdian.data.stats.MonthlyBillRecord
import com.hrsthrt74.afdian.data.stats.buildDailyIncomeChart
import com.hrsthrt74.afdian.data.stats.buildMonthlyIncomeChart
import com.hrsthrt74.afdian.data.stats.buildUvChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 图表指示器「9/6 五 • ¥12.34」里星期数据的回归测试。
 *
 * 锁住三件事：
 * 1. 按日图表（近30天收入 / 访问人数趋势）的 `categoryWeekdays` 与 `categories` 等长且逐点对齐；
 * 2. 首尾点的日期与星期都正确（窗口是「今天往前推 29 天 → 今天」）；
 * 3. 月度图表没有星期（月份没有星期几），指示器据空列表自动只显示日期。
 */
class ChartWeekdayTest {

    /** 2026-10-16 是周五，作为「今天」便于手算窗口首尾的星期。 */
    private val now = LocalDate.of(2026, 10, 16)

    /** 按日图表：每一点都带星期，且与日期一一对齐。 */
    @Test
    fun dailyChartsCarryWeekdayForEveryPoint() {
        // 传空数据 → 全 0 → 回退成「到今天为止」的完整窗口，正好 30 个点，首尾可手算
        val uv = buildUvChart(emptyList(), now, CHART_DAILY_WINDOW_DAYS)
        assertEquals(CHART_DAILY_WINDOW_DAYS, uv.categories.size)
        assertEquals(uv.categories.size, uv.categoryWeekdays.size)
        // 末点 = 今天（2026-10-16 周五）
        assertEquals("10/16", uv.categories.last())
        assertEquals("五", uv.categoryWeekdays.last())
        // 首点 = 今天往前 29 天（2026-09-17 周四）
        assertEquals("9/17", uv.categories.first())
        assertEquals("四", uv.categoryWeekdays.first())

        // 收入柱状图走同一套日期与星期，两图窗口一致
        val income = buildDailyIncomeChart(emptyList(), now, CHART_DAILY_WINDOW_DAYS)
        assertEquals(uv.categories, income.categories)
        assertEquals(uv.categoryWeekdays, income.categoryWeekdays)
    }

    /** 月度图表：横轴是「年/月」，没有星期，指示器只会显示日期 + 值。 */
    @Test
    fun monthlyChartHasNoWeekday() {
        val monthly = buildMonthlyIncomeChart(
            listOf(
                MonthlyBillRecord(
                    month = now.withDayOfMonth(1),
                    totalAmount = BigDecimal("12.30"),
                    creatorAmount = BigDecimal("11.56"),
                    sponsorCount = 3,
                ),
            ),
        )
        assertEquals(listOf("26/10"), monthly.categories)
        assertTrue(monthly.categoryWeekdays.isEmpty())
    }
}
