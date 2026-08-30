package com.hrsthrt74.afdian

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 时间炸弹边界测试。 */
class TimeBombTest {
    private val buildTime = 1_000_000L

    @Test
    fun before120HoursIsAvailable() {
        assertFalse(TimeBomb.isExpired(buildTime, buildTime + TimeBomb.VALIDITY_MS - 1))
    }

    @Test
    fun exactly120HoursIsExpired() {
        assertTrue(TimeBomb.isExpired(buildTime, buildTime + TimeBomb.VALIDITY_MS))
    }

    @Test
    fun after120HoursIsExpired() {
        assertTrue(TimeBomb.isExpired(buildTime, buildTime + TimeBomb.VALIDITY_MS + 1))
    }
}
