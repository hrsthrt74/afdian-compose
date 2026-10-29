package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.stats.IncomeAiFeedState
import com.hrsthrt74.afdian.data.stats.IncomeAiRange
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import com.hrsthrt74.afdian.data.stats.filterIncomeRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 「问 AI」时间窗口的回归测试。
 *
 * 锁住两件事：窗口起始日的口径（近 N 天含今天），以及「数据够不够覆盖所选窗口」的判定——
 * 后者决定了用户点发送时是立刻开跑还是继续等翻页，判错就是多等十几秒或数据缺一块。
 */
class IncomeAiRangeTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 28)

    // ── 窗口起始日 ──

    @Test
    fun `近 N 天的起始日按 N 减一天回推（含今天）`() {
        assertEquals(LocalDate.of(2026, 10, 22), IncomeAiRange.Day7.cutoff(today))
        assertEquals(LocalDate.of(2026, 9, 29), IncomeAiRange.Day30.cutoff(today))
        assertEquals(LocalDate.of(2026, 7, 31), IncomeAiRange.Quarter.cutoff(today))
        assertEquals(LocalDate.of(2026, 5, 2), IncomeAiRange.HalfYear.cutoff(today))
    }

    @Test
    fun `全部没有下界`() {
        assertNull(IncomeAiRange.All.cutoff(today))
    }

    // ── 覆盖判定 ──

    @Test
    fun `最旧日期不晚于窗口起始日就算覆盖`() {
        val state = IncomeAiFeedState(count = 50, oldestDate = LocalDate.of(2026, 9, 1))
        assertTrue(state.covers(IncomeAiRange.Day7, today))
        assertTrue(state.covers(IncomeAiRange.Day30, today))
        // 只到 9/1，覆盖不了 3 个月
        assertFalse(state.covers(IncomeAiRange.Quarter, today))
    }

    @Test
    fun `刚好落在起始日当天也算覆盖`() {
        val state = IncomeAiFeedState(count = 8, oldestDate = LocalDate.of(2026, 10, 22))
        assertTrue(state.covers(IncomeAiRange.Day7, today))
    }

    @Test
    fun `拉完就算覆盖（含数据比窗口还短的情况）`() {
        val state = IncomeAiFeedState(count = 3, oldestDate = LocalDate.of(2026, 10, 26), complete = true)
        assertTrue(state.covers(IncomeAiRange.All, today))
        assertTrue(state.covers(IncomeAiRange.HalfYear, today))
    }

    @Test
    fun `全部只有拉完才算覆盖`() {
        val partial = IncomeAiFeedState(count = 200, oldestDate = LocalDate.of(2024, 1, 1))
        assertFalse(partial.covers(IncomeAiRange.All, today))
        assertTrue(partial.copy(complete = true).covers(IncomeAiRange.All, today))
    }

    @Test
    fun `一条都没有且没拉完时不算覆盖`() {
        assertFalse(IncomeAiFeedState().covers(IncomeAiRange.Day7, today))
        assertFalse(IncomeAiFeedState().covers(IncomeAiRange.All, today))
    }

    @Test
    fun `拉取出错时不再空等`() {
        val failed = IncomeAiFeedState(count = 10, error = "网络异常")
        assertTrue(failed.covers(IncomeAiRange.All, today))
    }

    // ── 按窗口过滤 ──

    @Test
    fun `过滤保留窗口起始日当天与更新的一行`() {
        val records = listOf(record("20261028"), record("20261022"), record("20261021"))
        val filtered = filterIncomeRecords(records, IncomeAiRange.Day7, today)
        assertEquals(listOf("20261028", "20261022"), filtered.map { it.dateStr })
    }

    @Test
    fun `全部不过滤`() {
        val records = listOf(record("20261028"), record("20180101"))
        assertEquals(2, filterIncomeRecords(records, IncomeAiRange.All, today).size)
    }

    @Test
    fun `日期解析不了的记录保留，不悄悄丢行`() {
        val filtered = filterIncomeRecords(listOf(record("bad-date")), IncomeAiRange.Day7, today)
        assertEquals(1, filtered.size)
    }

    private fun record(date: String) = IncomeStatRecord(
        dateStr = date,
        uv = 1,
        amount = BigDecimal.ONE,
        orderCount = 1,
        userCount = 1,
        oldUserCount = 0,
    )
}
