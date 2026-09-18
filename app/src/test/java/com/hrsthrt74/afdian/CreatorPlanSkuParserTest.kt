package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.parseCreatorPlanSkus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 方案 SKU 响应解析测试，覆盖嵌套方案、SKU 列表和缺省字段。 */
class CreatorPlanSkuParserTest {
    @Test
    fun parsesPlanAndSkuList() {
        val result = parseCreatorPlanSkus(
            Json.parseToJsonElement(
                """
                {
                  "data": {
                    "plan": {"plan_id": "p1", "name": "方案一", "price": "10.00", "product_type": 1},
                    "floor_price": "10.00",
                    "has_time_limit_price": 0,
                    "list": [{
                      "sku_id": "s1", "plan_id": "p1", "name": "商品一",
                      "price": "10.00", "show_stock": 1,
                      "plan": {"plan_id": "p1", "name": "方案一"}
                    }],
                    "vip_all_plans": [{"plan_id": "p2", "name": "会员方案"}],
                    "bought_skus": [{"sku_id": "s0", "name": "已购商品"}]
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        assertEquals("p1", result.plan?.plan_id)
        assertEquals("10.00", result.floor_price)
        assertEquals("s1", result.list.single().sku_id)
        assertEquals("方案一", result.list.single().plan?.name)
        assertEquals("p2", result.vip_all_plans.single().plan_id)
        assertEquals("s0", result.bought_skus.single().sku_id)
    }

    @Test
    fun missingDataReturnsEmptyResult() {
        val result = parseCreatorPlanSkus(Json.parseToJsonElement("{}").jsonObject)
        assertTrue(result.list.isEmpty())
        assertTrue(result.bought_skus.isEmpty())
    }
}
