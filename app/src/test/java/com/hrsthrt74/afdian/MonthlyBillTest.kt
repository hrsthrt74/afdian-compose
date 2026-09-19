package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.stats.IncomeAmountMode
import com.hrsthrt74.afdian.data.stats.amountFor
import com.hrsthrt74.afdian.data.stats.moneyText
import com.hrsthrt74.afdian.data.stats.monthlyBillSections
import com.hrsthrt74.afdian.data.stats.parseMonthlyBills
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** 「每月汇总」月度账单解析与分组回归测试，样本取自 /api/my/income 的真实响应。 */
class MonthlyBillTest {

    /** 真实响应片段：2026 年 8 个月 + 2025 年 8 个月，其中 2025 年 6/5 月为 0 收入。 */
    private val realResponse = """
        {
          "ec": 200,
          "em": "",
          "data": {
            "monthly_bill": [
              {
                "year": 2026,
                "data": [
                  {"month": 8, "data": {"total_amount": "261.15", "creator_amount": "245.48", "sponsor_count": 81, "create_time": 1788208599}},
                  {"month": 7, "data": {"total_amount": "78.17", "creator_amount": "73.47", "sponsor_count": 25, "create_time": 1785529749}},
                  {"month": 6, "data": {"total_amount": "1.89", "creator_amount": "1.77", "sponsor_count": 1, "create_time": 1782851709}},
                  {"month": 5, "data": {"total_amount": "18.24", "creator_amount": "17.14", "sponsor_count": 7, "create_time": 1780259227}},
                  {"month": 4, "data": {"total_amount": "14.43", "creator_amount": "13.56", "sponsor_count": 7, "create_time": 1777580988}},
                  {"month": 3, "data": {"total_amount": "40.00", "creator_amount": "37.60", "sponsor_count": 11, "create_time": 1774988023}},
                  {"month": 2, "data": {"total_amount": "22.84", "creator_amount": "21.46", "sponsor_count": 9, "create_time": 1772309877}},
                  {"month": 1, "data": {"total_amount": "15.43", "creator_amount": "14.50", "sponsor_count": 7, "create_time": 1769890330}}
                ]
              },
              {
                "year": 2025,
                "data": [
                  {"month": 12, "data": {"total_amount": "20.31", "creator_amount": "19.09", "sponsor_count": 9, "create_time": 1767211703}},
                  {"month": 11, "data": {"total_amount": "15.43", "creator_amount": "14.50", "sponsor_count": 7, "create_time": 1764532775}},
                  {"month": 10, "data": {"total_amount": "28.18", "creator_amount": "26.48", "sponsor_count": 12, "create_time": 1761940434}},
                  {"month": 9, "data": {"total_amount": "50.69", "creator_amount": "47.64", "sponsor_count": 21, "create_time": 1759262036}},
                  {"month": 8, "data": {"total_amount": "24.57", "creator_amount": "23.09", "sponsor_count": 12, "create_time": 1756669590}},
                  {"month": 7, "data": {"total_amount": "35.91", "creator_amount": "33.75", "sponsor_count": 19, "create_time": 1753991810}},
                  {"month": 6, "data": {"total_amount": "0.00", "creator_amount": "0.00", "sponsor_count": 0, "create_time": 1751314498}},
                  {"month": 5, "data": {"total_amount": "0.00", "creator_amount": "0.00", "sponsor_count": 0, "create_time": 1748720713}}
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun monthlyBillElement() = Json.parseToJsonElement(realResponse)
        .jsonObject["data"]!!.jsonObject["monthly_bill"]!!

    @Test
    fun parsesEveryMonthIncludingZeroIncomeOnes() {
        val bills = parseMonthlyBills(monthlyBillElement())

        // 16 个月全部保留，0 收入的月份也不过滤
        assertEquals(16, bills.size)
        assertTrue(bills.any { it.month.year == 2025 && it.month.monthValue == 6 })
        assertTrue(bills.any { it.month.year == 2025 && it.month.monthValue == 5 })
    }

    @Test
    fun mapsAmountsAndSponsorCount() {
        val bills = parseMonthlyBills(monthlyBillElement())

        val august2026 = bills.single { it.month.year == 2026 && it.month.monthValue == 8 }
        assertEquals(BigDecimal("261.15"), august2026.totalAmount)
        assertEquals(BigDecimal("245.48"), august2026.creatorAmount)
        assertEquals(81L, august2026.sponsorCount)

        val may2025 = bills.single { it.month.year == 2025 && it.month.monthValue == 5 }
        assertEquals(BigDecimal("0.00"), may2025.totalAmount)
        assertEquals(BigDecimal("0.00"), may2025.creatorAmount)
        assertEquals(0L, may2025.sponsorCount)
    }

    @Test
    fun skipsBrokenRowsAndToleratesMissingElement() {
        // monthly_bill 缺失 → 空列表
        assertTrue(parseMonthlyBills(null).isEmpty())

        val broken = Json.parseToJsonElement(
            """
            [
              {"data": [{"month": 1, "data": {"creator_amount": "1.00"}}]},
              {"year": 2026, "data": [
                {"month": 2},
                {"month": 3, "data": {}},
                {"month": 4, "data": {"total_amount": "9.99"}},
                {"month": 5, "data": {"creator_amount": "5.00", "total_amount": "5.31"}}
              ]}
            ]
            """.trimIndent(),
        )

        val bills = parseMonthlyBills(broken)
        // 缺 year 的整年丢弃；缺 month / 缺 data / 缺 creator_amount 的月份丢弃；缺 total_amount 时回落 creator_amount
        assertEquals(1, bills.size)
        assertEquals(5, bills.single().month.monthValue)
        assertEquals(BigDecimal("5.31"), bills.single().totalAmount)
        assertEquals(BigDecimal("5.00"), bills.single().creatorAmount)
    }

    @Test
    fun sectionsGroupByYearDescending() {
        val sections = monthlyBillSections(parseMonthlyBills(monthlyBillElement()))

        assertEquals(listOf(2026, 2025), sections.map { it.year })
        // 组内月份倒序：8 月在最前
        assertEquals(listOf(8, 7, 6, 5, 4, 3, 2, 1), sections[0].bills.map { it.month.monthValue })
        assertEquals(listOf(12, 11, 10, 9, 8, 7, 6, 5), sections[1].bills.map { it.month.monthValue })
    }

    @Test
    fun amountModeSelectsCorrectColumn() {
        val august2026 = parseMonthlyBills(monthlyBillElement())
            .single { it.month.year == 2026 && it.month.monthValue == 8 }

        assertEquals(BigDecimal("261.15"), august2026.amountFor(IncomeAmountMode.BeforeFee))
        assertEquals(BigDecimal("245.48"), august2026.amountFor(IncomeAmountMode.AfterFee))
    }

    @Test
    fun amountModeFallsBackToBeforeFee() {
        assertEquals(IncomeAmountMode.BeforeFee, IncomeAmountMode.fromStorage(null))
        assertEquals(IncomeAmountMode.BeforeFee, IncomeAmountMode.fromStorage("unknown"))
        assertEquals(IncomeAmountMode.AfterFee, IncomeAmountMode.fromStorage("after_fee"))
    }

    @Test
    fun moneyTextKeepsTwoDecimalsWithSymbol() {
        assertEquals("¥261.15", moneyText(BigDecimal("261.15")))
        // 接口原样返回 40.00 / 0.00，展示时也要保留两位小数
        assertEquals("¥40.00", moneyText(BigDecimal("40.00")))
        assertEquals("¥0.00", moneyText(BigDecimal("0.00")))
        assertEquals("¥1.89", moneyText(BigDecimal("1.89")))
    }
}
