package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.stats.CardKey
import com.hrsthrt74.afdian.data.stats.CardMetric
import com.hrsthrt74.afdian.data.stats.CardPeriod
import com.hrsthrt74.afdian.data.stats.CardValue
import com.hrsthrt74.afdian.data.stats.DataCardModel
import com.hrsthrt74.afdian.data.stats.DailyStatRecord
import com.hrsthrt74.afdian.data.stats.PaidOrderRecord
import com.hrsthrt74.afdian.data.stats.mergeCardFrame
import com.hrsthrt74.afdian.data.stats.aggregateDailyStats
import com.hrsthrt74.afdian.data.stats.aggregatePaidOrders
import com.hrsthrt74.afdian.data.stats.toDateRange
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 数据卡时间范围、稳定键和统计聚合的纯逻辑测试。 */
class CardModelTest {
    private val today = LocalDate.of(2026, 9, 2)

    @Test
    fun periodsUseCalendarBoundaries() {
        assertEquals(LocalDate.of(2026, 9, 2), CardPeriod.Today.toDateRange(today).start)
        assertEquals(LocalDate.of(2026, 9, 3), CardPeriod.Today.toDateRange(today).endExclusive)
        assertEquals(LocalDate.of(2026, 9, 1), CardPeriod.Yesterday.toDateRange(today).start)
        assertEquals(LocalDate.of(2026, 8, 27), CardPeriod.LastDays(7).toDateRange(today).start)
        assertEquals(LocalDate.of(2026, 9, 1), CardPeriod.ThisMonth.toDateRange(today).start)
        assertEquals(LocalDate.of(2026, 8, 1), CardPeriod.LastMonth.toDateRange(today).start)
        assertNull(CardPeriod.History.toDateRange(today).start)
    }

    @Test
    fun cardKeyCanBeStoredAndRestored() {
        val key = CardKey(CardMetric.SponsorCount, CardPeriod.LastDays(31))

        assertEquals("sponsor_count:last_days_31", key.storageValue)
        assertEquals(key, CardKey.fromStorage(key.storageValue))
        assertEquals(CardKey(CardMetric.Balance), CardKey.fromStorage("balance"))
    }

    @Test
    fun paidOrdersUseExactMoneyAndDistinctSponsors() {
        val zone = ZoneOffset.UTC
        val range = CardPeriod.LastDays(2).toDateRange(today)
        val start = LocalDate.of(2026, 9, 1).atStartOfDay(zone).toEpochSecond()

        val result = aggregatePaidOrders(
            records = listOf(
                PaidOrderRecord("user-a", BigDecimal("1.10"), start + 60),
                PaidOrderRecord("user-a", BigDecimal("2.20"), start + 120),
                PaidOrderRecord("user-b", BigDecimal("3.30"), start + 180),
                PaidOrderRecord("user-c", BigDecimal("9.99"), start + 2 * 86_400),
            ),
            range = range,
            zoneId = zone,
        )

        assertEquals(BigDecimal("6.60"), result.amount)
        assertEquals(3L, result.orderCount)
        assertEquals(2L, result.sponsorCount)
    }

    @Test
    fun dailyStatsAreSummedOnlyInsideRange() {
        val result = aggregateDailyStats(
            records = listOf(
                DailyStatRecord(today.minusDays(1), BigDecimal("1.10"), 1, 2, 3),
                DailyStatRecord(today, BigDecimal("2.20"), 2, 4, 5),
                DailyStatRecord(today.plusDays(1), BigDecimal("9.99"), 9, 9, 9),
            ),
            range = CardPeriod.Today.toDateRange(today),
        )

        assertEquals(BigDecimal("2.20"), result.income)
        assertEquals(2L, result.orderCount)
        assertEquals(4L, result.uv)
        assertEquals(5L, result.pv)
    }

    /**
     * 流式中间帧里未完成的数据源是 Unavailable，合并时不能把已显示的缓存值抹掉，
     * 否则冷启动时数字会先消失一次、真值到达后动画还得从 0 重播。
     */
    @Test
    fun streamingFrameKeepsPreviousValueForPendingCards() {
        val cached = DataCardModel("income:today", "今日收入", CardValue.Text("1280.00"))
        val frame = mapOf(
            // 账单接口还没回来：仍是 Unavailable
            "income:today" to DataCardModel("income:today", "今日收入", CardValue.Unavailable),
            // dashboard 已经拿到：本帧的新值应当覆盖缓存
            "balance" to DataCardModel("balance", "可提现（抽成前）", CardValue.Money(BigDecimal("88.00"))),
        )

        val merged = mergeCardFrame(mapOf("income:today" to cached, "balance" to cached), frame)

        assertEquals(cached, merged["income:today"])
        assertEquals(CardValue.Money(BigDecimal("88.00")), merged["balance"]?.value)
    }

    /** 没有可保留的旧值时照常落到 Unavailable，首屏该显示 “--” 的仍然显示 “--”。 */
    @Test
    fun streamingFrameFallsBackToUnavailableWithoutPreviousValue() {
        val pending = DataCardModel("income:today", "今日收入", CardValue.Unavailable)

        val merged = mergeCardFrame(
            previous = mapOf("income:today" to pending),
            frame = mapOf("income:today" to pending),
        )

        assertEquals(CardValue.Unavailable, merged["income:today"]?.value)
        // 上一帧没有这张卡：直接用本帧值，不凭空造出旧卡
        val added = mergeCardFrame(
            previous = emptyMap(),
            frame = mapOf("income:today" to pending),
        )
        assertEquals(CardValue.Unavailable, added["income:today"]?.value)
    }
}
