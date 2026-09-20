package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.SponsorFilterAction
import com.hrsthrt74.afdian.data.content.SponsorFilterPlan
import com.hrsthrt74.afdian.data.content.SponsorFilterRow
import com.hrsthrt74.afdian.data.content.SponsorFilterType
import com.hrsthrt74.afdian.data.content.buildSponsorFilterArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


/** 筛选条件 filter 构建回归测试：字段映射、不完整行跳过、多条件 AND。 */
class SponsorFilterBuildTest {
    private val plan = SponsorFilterPlan(planId = "plan-1", name = "宇宙航线", price = "3.99")

    @Test
    fun buildsPlanCondition() {
        val array = buildSponsorFilterArray(
            listOf(
                SponsorFilterRow(
                    type = SponsorFilterType.Sponsoring,
                    action = SponsorFilterAction.Eq,
                    plan = plan,
                ),
            ),
        )

        assertEquals(1, array.size)
        val obj = array[0].jsonObject
        assertEquals("sponsoring", obj["type"]!!.jsonPrimitive.content)
        assertEquals("=", obj["action"]!!.jsonPrimitive.content)
        assertEquals("plan-1", obj["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildsNumericConditionWithSymbol() {
        val array = buildSponsorFilterArray(
            listOf(
                SponsorFilterRow(
                    type = SponsorFilterType.Amount,
                    action = SponsorFilterAction.Ge,
                    number = "10",
                ),
            ),
        )

        assertEquals(1, array.size)
        val obj = array[0].jsonObject
        assertEquals("all_sum_amount", obj["type"]!!.jsonPrimitive.content)
        assertEquals(">=", obj["action"]!!.jsonPrimitive.content)
        assertEquals("10", obj["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun incompleteRowsAreSkippedAndCompleteOnesKept() {
        val array = buildSponsorFilterArray(
            listOf(
                // 方案型未选方案（「全部」）→ 跳过
                SponsorFilterRow(type = SponsorFilterType.Sponsored, action = SponsorFilterAction.Ne),
                // 月份型未选年月 → 跳过
                SponsorFilterRow(type = SponsorFilterType.Month, action = SponsorFilterAction.Eq),
                // 金额空输入 → 跳过
                SponsorFilterRow(type = SponsorFilterType.Amount, action = SponsorFilterAction.Ge, number = ""),
                // 金额非数字 → 跳过
                SponsorFilterRow(type = SponsorFilterType.Amount, action = SponsorFilterAction.Ge, number = "abc"),
                // 完整行保留
                SponsorFilterRow(type = SponsorFilterType.Sponsored, action = SponsorFilterAction.Eq, plan = plan),
            ),
        )

        assertEquals(1, array.size)
        val obj = array[0].jsonObject
        assertEquals("sponsored", obj["type"]!!.jsonPrimitive.content)
        assertEquals("plan-1", obj["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildsMonthConditionWithJoinedValueAndMonthValueArray() {
        // 抓包协议：value = 逗号分隔 YYYYMM（降序），month_value = 名值数组
        val array = buildSponsorFilterArray(
            listOf(
                SponsorFilterRow(
                    type = SponsorFilterType.Month,
                    action = SponsorFilterAction.Eq,
                    months = listOf("202610", "202609"),
                ),
            ),
        )

        assertEquals(1, array.size)
        val obj = array[0].jsonObject
        assertEquals("month", obj["type"]!!.jsonPrimitive.content)
        assertEquals("=", obj["action"]!!.jsonPrimitive.content)
        assertEquals("202610,202609", obj["value"]!!.jsonPrimitive.content)

        val monthValue = obj["month_value"]!!.jsonArray
        assertEquals(2, monthValue.size)
        val first = monthValue[0].jsonObject
        assertEquals("2026年10月", first["name"]!!.jsonPrimitive.content)
        assertEquals("202610", first["value"]!!.jsonPrimitive.content)
        val second = monthValue[1].jsonObject
        assertEquals("2026年9月", second["name"]!!.jsonPrimitive.content)
        assertEquals("202609", second["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun emptyFiltersYieldEmptyArray() {
        assertTrue(buildSponsorFilterArray(emptyList()).jsonArray.isEmpty())
    }
}
