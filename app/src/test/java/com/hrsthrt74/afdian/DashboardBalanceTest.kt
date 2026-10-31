package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.stats.parseDashboardBalance
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * dashboard.balance 字段解析的纯逻辑测试。
 *
 * 这个字段平时是金额，每月 1 日 0:00–10:00 结算期间变成「结算中，请耐心等待」文案，
 * 解析结果同时决定「可提现卡要不要写值」和「当前是否处于结算期」两件事，两边都不能误判。
 */
class DashboardBalanceTest {
    @Test
    fun moneyTextParsesAsAmount() {
        val balance = parseDashboardBalance("100.00")

        assertEquals(BigDecimal("100.00"), balance.amount)
        assertFalse(balance.isSettling)
    }

    @Test
    fun zeroAmountIsStillMoneyNotSettling() {
        val balance = parseDashboardBalance("0")

        assertEquals(BigDecimal.ZERO, balance.amount)
        assertFalse(balance.isSettling)
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        val balance = parseDashboardBalance("  100.00 ")

        assertEquals(BigDecimal("100.00"), balance.amount)
        assertFalse(balance.isSettling)
    }

    @Test
    fun fullSettlingSentenceIsSettlingSignal() {
        val balance = parseDashboardBalance("结算中，请耐心等待")

        assertNull(balance.amount)
        assertTrue(balance.isSettling)
    }

    @Test
    fun settlingKeywordVariantStillMatches() {
        // 只匹配「结算」两个字：平台改标点或后缀时不能静默失效
        val balance = parseDashboardBalance("结算中")

        assertNull(balance.amount)
        assertTrue(balance.isSettling)
    }

    @Test
    fun missingFieldIsNeitherMoneyNorSettling() {
        assertNull(parseDashboardBalance(null).amount)
        assertFalse(parseDashboardBalance(null).isSettling)
        assertNull(parseDashboardBalance("").amount)
        assertFalse(parseDashboardBalance("").isSettling)
        assertFalse(parseDashboardBalance("   ").isSettling)
    }

    @Test
    fun unknownTextIsNotTreatedAsSettling() {
        // 未知文案只让余额卡保持不可用，绝不能把平常日子误判成结算期
        val balance = parseDashboardBalance("系统繁忙")

        assertNull(balance.amount)
        assertFalse(balance.isSettling)
    }
}
