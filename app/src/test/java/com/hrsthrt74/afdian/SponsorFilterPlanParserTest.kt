package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.parseFilterPlans
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 筛选档位方案解析回归测试：合并去重、0 元排除、缺字段容错。 */
class SponsorFilterPlanParserTest {
    @Test
    fun mergesListAndSaleListAndDedupes() {
        val root = Json.parseToJsonElement(
            """
            {
              "ec": 200,
              "data": {
                "list": [
                  {"plan_id": "p-independent", "name": "一大瓶快乐水", "price": "5.00", "show_price": "5.00"},
                  {"plan_id": "p-dup", "name": "重复方案", "price": "12.34", "show_price": "12.34"}
                ],
                "sale_list": [
                  {"plan_id": "p-dup", "name": "重复方案", "price": "12.34", "show_price": "12.34"},
                  {"plan_id": "p-sale", "name": "原创表盘", "price": "3.99", "show_price": "3.99", "product_type": 1}
                ],
                "paid_range": [{}, {}]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val plans = parseFilterPlans(root)

        // list 在前、sale_list 去重追加：p-dup 只保留第一次出现
        assertEquals(3, plans.size)
        assertEquals("p-independent", plans[0].planId)
        assertEquals("p-dup", plans[1].planId)
        assertEquals("p-sale", plans[2].planId)
        assertEquals("￥3.99 原创表盘", plans[2].display)
    }

    @Test
    fun dropsZeroPriceAndMissingId() {
        val root = Json.parseToJsonElement(
            """
            {
              "data": {
                "list": [
                  {"plan_id": "p-free", "name": "0 元方案", "price": "0.00"},
                  {"plan_id": "p-noprice", "name": "缺价格"},
                  {"name": "缺 id", "price": "1.00"},
                  {"plan_id": "p-ok", "name": "正常", "price": "1.89"}
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val plans = parseFilterPlans(root)

        assertEquals(1, plans.size)
        assertEquals("p-ok", plans[0].planId)
    }

    @Test
    fun emptyRootYieldsEmptyList() {
        assertTrue(parseFilterPlans(Json.parseToJsonElement("{}").jsonObject).isEmpty())
    }
}
