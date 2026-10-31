package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.stats.WithdrawalSchedule
import com.hrsthrt74.afdian.data.stats.estimatedAfterFee
import com.hrsthrt74.afdian.data.stats.formatCountdown
import com.hrsthrt74.afdian.data.stats.settlementAmountTexts
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提现周期计算与结算期金额口径的纯逻辑测试。 */
class WithdrawalScheduleTest {
    @Test
    fun lastSevenDaysShowReminderAndPointToNextMonth() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 9, 25, 10, 0))

        assertTrue(status.shouldShowReminder)
        assertFalse(status.isSettlementWindow)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), status.withdrawalTime)
        assertEquals(6L, status.remainingDays)
    }

    @Test
    fun normalDaysDoNotShowReminder() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 9, 10, 10, 0))

        assertFalse(status.shouldShowReminder)
        assertFalse(status.isSettlementWindow)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), status.withdrawalTime)
    }

    @Test
    fun firstDayBeforeTenPointsToToday() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 1, 9, 0))

        assertTrue(status.shouldShowReminder)
        assertTrue(status.isSettlementWindow)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), status.withdrawalTime)
        assertEquals(0L, status.remainingDays)
    }

    @Test
    fun firstDayAtTenSettlementWindowEnds() {
        // 1 日 10:00 整结算窗口结束：不再提醒，倒计时指向下月 1 日
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 1, 10, 0))

        assertFalse(status.shouldShowReminder)
        assertFalse(status.isSettlementWindow)
        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 0), status.withdrawalTime)
    }

    // ---- 跨月边界：结算窗口的进入与退出 ----

    @Test
    fun lastMinuteOfMonthStaysOutsideSettlementWindow() {
        // 月末最后一分钟还属于上个月：不该提前切「上月」口径
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 9, 30, 23, 59))

        assertTrue(status.shouldShowReminder)
        assertFalse(status.isSettlementWindow)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), status.withdrawalTime)
        // 23:59 → 10:00 是 10 小时 1 分：不足一天时改按「小时 + 分」显示，不再出现「0 天」
        assertEquals("10 小时 1 分", status.remainingText)
        assertFalse(status.isWithdrawalTimeReached)
    }

    @Test
    fun firstSecondOfMonthEntersSettlementWindow() {
        // 自然月翻篇的第一秒就进入结算期：提醒卡必须立刻切「上月」口径
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 1, 0, 0))

        assertTrue(status.shouldShowReminder)
        assertTrue(status.isSettlementWindow)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), status.withdrawalTime)
        assertEquals("10 小时 0 分", status.remainingText)
        assertFalse(status.isWithdrawalTimeReached)
    }

    @Test
    fun lastMinuteBeforeTenStillSettling() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 1, 9, 59))

        assertTrue(status.isSettlementWindow)
        // 不足一小时只报分钟，不再出现「0 小时 1 分」
        assertEquals("1 分", status.remainingText)
        assertFalse(status.isWithdrawalTimeReached)
    }

    @Test
    fun hoursAndMinutesShowTogetherWithinLastDay() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 1, 8, 30))

        // 1 小时 30 分：小时与分钟都要露出来，用户才能看出在倒数
        assertEquals("1 小时 30 分", status.remainingText)
    }

    @Test
    fun lastMinuteShowsLessThanOneMinute() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 1, 9, 59, 30))

        // 最后 30 秒：拆到秒只会让文案每秒跳动，统一说「不足一分钟」
        assertEquals("不足一分钟", status.remainingText)
        assertFalse(status.isWithdrawalTimeReached)
    }

    // ---- 每月最后一天的「距离结算」倒计时 ----

    @Test
    fun lastDayShowsSettlementCountdown() {
        // 10/31 20:00：下月 1 日 0:00 开始结算，还有 4 小时；离提现（1 日 10:00）还有 14 小时
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 31, 20, 0))

        assertEquals("4 小时 0 分", status.settlementRemainingText)
        assertEquals("14 小时 0 分", status.remainingText)
        assertTrue(status.shouldShowReminder)
        assertFalse(status.isSettlementWindow)
    }

    @Test
    fun settlementCountdownUsesMonthLengthNotFixedDay() {
        // 9 月只有 30 天：最后一天是 9/30，不能写死 31 号
        assertEquals(
            "12 小时 0 分",
            WithdrawalSchedule.calculate(LocalDateTime.of(2026, 9, 30, 12, 0)).settlementRemainingText,
        )
        // 30 号当天已经不是最后一天（9 月里 30 号才是）；10 月的 30 号不是最后一天
        assertNull(WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 30, 12, 0)).settlementRemainingText)
    }

    @Test
    fun settlementCountdownAbsentOutsideLastDay() {
        // 月末提醒期内但不是最后一天：只说提现，不说结算
        assertNull(WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 28, 9, 0)).settlementRemainingText)
        // 1 日结算期同样不展示
        assertNull(WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 1, 5, 0)).settlementRemainingText)
    }

    @Test
    fun lastDayLastMinuteSettlementCountdown() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 31, 23, 59))

        assertEquals("1 分", status.settlementRemainingText)
    }

    @Test
    fun countdownFormatSharedByBothCountdowns() {
        // 两个倒计时共用一套格式：≥1 天回到「天 + 小时」，其余按「小时 + 分」递进
        assertEquals("2 天 3 小时", formatCountdown(Duration.ofDays(2).plusHours(3)))
        assertEquals("5 小时 30 分", formatCountdown(Duration.ofHours(5).plusMinutes(30)))
        assertEquals("45 分", formatCountdown(Duration.ofMinutes(45)))
        assertEquals("不足一分钟", formatCountdown(Duration.ofSeconds(30)))
    }

    // ---- 服务端结算信号 ----

    @Test
    fun serverSettlingKeepsReminderAfterTen() {
        // 服务端 10:00 后仍报结算中：提醒继续显示，倒计时说「已到提现日」而不是病句
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 1, 10, 30), isBalanceSettling = true)

        assertTrue(status.shouldShowReminder)
        assertTrue(status.isSettlementWindow)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), status.withdrawalTime)
        assertEquals(0L, status.remainingDays)
        assertEquals("已到提现日", status.remainingText)
        assertTrue(status.isWithdrawalTimeReached)
    }

    @Test
    fun staleServerSignalIsIgnoredOutsideFirstDay() {
        // 信号没有时间戳：进程从 1 日活到 5 日且一直没刷新成功时，不能把 5 日误判成结算期
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 5, 10, 0), isBalanceSettling = true)

        assertFalse(status.shouldShowReminder)
        assertFalse(status.isSettlementWindow)
        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 0), status.withdrawalTime)
    }

    // ---- 结算期金额口径 ----

    @Test
    fun settlementAmountsUseUnconfirmedAndEstimatedAfterFee() {
        val texts = settlementAmountTexts(BigDecimal("3021.68"))

        assertEquals("3021.68", texts.income)
        // 3021.68 × 0.94 = 2840.3792，向下取整到分
        assertEquals("2840.37", texts.afterFee)
    }

    @Test
    fun afterFeeRoundsDownInsteadOfHalfUp() {
        // 100.01 × 0.94 = 94.0094：四舍五入还是 94.01，这里必须是 94.00（宁可少算一分）
        assertEquals(BigDecimal("94.00"), estimatedAfterFee(BigDecimal("100.01")))
        assertEquals("94.00", settlementAmountTexts(BigDecimal("100.01")).afterFee)
    }

    @Test
    fun zeroUnconfirmedShowsZeroInsteadOfPlaceholder() {
        // 0 是有效业务值（上月没人发电，或结算已完成转入余额），不能当成「取不到」
        val texts = settlementAmountTexts(BigDecimal.ZERO)

        assertEquals("0.00", texts.income)
        assertEquals("0.00", texts.afterFee)
    }

    @Test
    fun tinyAmountFloorsToZero() {
        assertEquals("0.00", settlementAmountTexts(BigDecimal("0.01")).afterFee)
    }

    @Test
    fun missingUnconfirmedFallsBackToPlaceholder() {
        val texts = settlementAmountTexts(null)

        assertEquals("---", texts.income)
        assertEquals("---", texts.afterFee)
    }
}
