package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.millisUntilNextLocalDay
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨天自动刷新的时长计算测试。
 *
 * 规则：等到下一个本地自然日 0 点再刷新，且额外留 1 秒缓冲，
 * 保证触发时 `LocalDate.now()` 已经翻篇（数据卡的今日/昨天/本月口径才会跟着换）。
 */
class DailyRefreshTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun afternoonWaitsUntilNextMidnight() {
        val now = ZonedDateTime.of(2026, 11, 1, 15, 30, 0, 0, zone)

        // 15:30 → 次日 0:00 共 8 小时 30 分，再加 1 秒缓冲
        assertEquals((8 * 60 + 30) * 60_000L + 1_000L, millisUntilNextLocalDay(now))
    }

    @Test
    fun lastSecondOfDayWaitsAboutOneSecond() {
        val now = ZonedDateTime.of(2026, 11, 1, 23, 59, 59, 0, zone)

        // 23:59:59 → 0:00 剩 1 秒，加缓冲共 2 秒，确保落点已是新的一天
        assertEquals(2_000L, millisUntilNextLocalDay(now))
    }

    @Test
    fun exactMidnightAlwaysWaitsAtLeastOneSecond() {
        val now = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)

        // 正好整点：直接算会得到 24 小时，同样是正数，不需要特殊处理
        assertEquals(24 * 60 * 60 * 1000L + 1_000L, millisUntilNextLocalDay(now))

        // 贴着 0 点前 1 毫秒时，加缓冲后也不能出现 0 或负数（否则会连触）
        val justBefore = ZonedDateTime.of(2026, 11, 1, 23, 59, 59, 999_000_000, zone)
        assertTrue(millisUntilNextLocalDay(justBefore) >= 1_000L)
    }

    @Test
    fun monthAndYearBoundaryAlsoPointsToNextMidnight() {
        val newYearEve = ZonedDateTime.of(2026, 12, 31, 22, 0, 0, 0, zone)

        // 跨年同样只是「下一个自然日 0 点」，不针对某一天
        assertEquals(2 * 60 * 60 * 1000L + 1_000L, millisUntilNextLocalDay(newYearEve))
    }
}
