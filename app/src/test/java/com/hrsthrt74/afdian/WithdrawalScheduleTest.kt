package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.stats.WithdrawalSchedule
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提现周期计算的纯逻辑测试。 */
class WithdrawalScheduleTest {
    @Test
    fun lastSevenDaysShowReminderAndPointToNextMonth() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 9, 25, 10, 0))

        assertTrue(status.shouldShowReminder)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), status.withdrawalTime)
        assertEquals(6L, status.remainingDays)
    }

    @Test
    fun normalDaysDoNotShowReminder() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 9, 10, 10, 0))

        assertFalse(status.shouldShowReminder)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), status.withdrawalTime)
    }

    @Test
    fun firstDayBeforeTenPointsToToday() {
        val status = WithdrawalSchedule.calculate(LocalDateTime.of(2026, 10, 1, 9, 0))

        assertTrue(status.shouldShowReminder)
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), status.withdrawalTime)
        assertEquals(0L, status.remainingDays)
    }
}
