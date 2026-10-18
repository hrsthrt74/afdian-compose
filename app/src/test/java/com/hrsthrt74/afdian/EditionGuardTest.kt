package com.hrsthrt74.afdian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本拦截边界测试。
 *
 * 只测纯函数 [EditionGuard.isWrongEdition]，不测 [EditionGuard.isBlocked] ——
 * 后者的结果取决于当前测试变体（legacy 变体在 API 34+ 的 JVM 上恒为 true）。
 */
class EditionGuardTest {

    /**
     * 阈值来自 Gradle 注入的 `BuildConfig.STANDARD_MIN_SDK`，必须等于 standard 的 minSdk。
     * 这条断言是刻意的「绊线」：以后调 minSdk 时它会失败，提醒同步确认拦截范围。
     */
    @Test
    fun thresholdMatchesStandardMinSdk() {
        assertEquals(
            "STANDARD_MIN_SDK 必须与 standard flavor 的 minSdk 一致",
            34,
            EditionGuard.STANDARD_MIN_SDK,
        )
    }

    // ---- legacy 包：API 33 及以下放行 ----

    @Test
    fun legacyOnApi29IsAllowed() {
        assertFalse(EditionGuard.isWrongEdition(legacyEdition = true, sdkInt = 29))
    }

    @Test
    fun legacyOnApi33IsAllowed() {
        assertFalse(EditionGuard.isWrongEdition(legacyEdition = true, sdkInt = 33))
    }

    // ---- legacy 包：API 34 起拦截（34 是边界，取等号） ----

    @Test
    fun legacyOnApi34IsBlocked() {
        assertTrue(EditionGuard.isWrongEdition(legacyEdition = true, sdkInt = 34))
    }

    @Test
    fun legacyOnApi36IsBlocked() {
        assertTrue(EditionGuard.isWrongEdition(legacyEdition = true, sdkInt = 36))
    }

    // ---- standard 包：任何系统版本都不该被拦 ----
    // 34 是最容易写错的边界：standard 自己的 minSdk 就是 34

    @Test
    fun standardOnApi34IsAllowed() {
        assertFalse(EditionGuard.isWrongEdition(legacyEdition = false, sdkInt = 34))
    }

    @Test
    fun standardOnApi36IsAllowed() {
        assertFalse(EditionGuard.isWrongEdition(legacyEdition = false, sdkInt = 36))
    }
}
