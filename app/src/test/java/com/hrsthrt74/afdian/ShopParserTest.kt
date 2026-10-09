package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.SHOP_PLAN_STATUS_HIDDEN
import com.hrsthrt74.afdian.data.content.SHOP_PLAN_STATUS_NORMAL
import com.hrsthrt74.afdian.data.content.SHOP_PRODUCT_TYPE_BUNDLE
import com.hrsthrt74.afdian.data.content.parseShopPlanSkus
import com.hrsthrt74.afdian.data.content.parseShopProducts
import com.hrsthrt74.afdian.data.content.stockLabel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 售卖商品解析测试：sale_list → ShopProduct、get-plan-skus → ShopPlanSkus 与库存文案规则。 */
class ShopParserTest {

    @Test
    fun parsesSaleList() {
        // 模拟 all-plans 响应：一个普通商品（多型号限量）+ 一个捆绑包（无限库存）
        val root = Json.parseToJsonElement(
            """
            {
              "ec": 200, "em": "",
              "data": {
                "sale_list": [
                  {
                    "plan_id": "p1", "name": "测试商品", "pic": "",
                    "price": "99.99", "show_price": "99.99",
                    "status": 3, "product_type": 1,
                    "bundle_stock": 0, "sponsor_count": 22, "total_amount": "42.78",
                    "need_address": 0,
                    "sku": [
                      {"sku_id": "s1", "name": "9Pro", "price": "2.29", "stock": 0, "sponsor_count": "14"},
                      {"sku_id": "s2", "name": "Watch S4", "price": "2.29", "stock": "", "sponsor_count": 5}
                    ]
                  },
                  {
                    "plan_id": "p2", "name": "【捆绑包】测试", "pic": "https://example.com/a.png",
                    "price": "7.99", "show_price": "7.99",
                    "status": 1, "product_type": 2,
                    "bundle_stock": "", "sponsor_count": 7, "total_amount": "33.96",
                    "need_address": 1,
                    "sku": [
                      {"sku_id": "s3", "name": "子商品A", "price": "4.99", "stock": "", "sponsor_count": "6"}
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val products = parseShopProducts(root)
        assertEquals(2, products.size)

        // 普通商品：状态/销量/收入/多型号库存汇总
        val normal = products[0]
        assertEquals("p1", normal.planId)
        assertEquals(SHOP_PLAN_STATUS_HIDDEN, normal.status)
        assertFalse(normal.isBundle)
        assertEquals(22, normal.soldTotal)
        assertEquals("42.78", normal.incomeTotal)
        assertEquals(2, normal.skus.size)
        // 多型号无法用单一数字概括，库存显示 "-"
        assertEquals("-", normal.stockSummary)
        // 数字型 stock（0）转字符串、字符串型 sponsor_count 转 int
        assertEquals("0", normal.skus[0].stock)
        assertEquals(14, normal.skus[0].soldCount)
        // 空字符串库存 = 无限
        assertEquals("无限", normal.skus[1].stockLabel)
        // 商品封面为空时回退首个有图的型号（此处两型号都无图 → null）
        assertNull(normal.pic)

        // 捆绑包：类型、无限库存、实物标记
        val bundle = products[1]
        assertTrue(bundle.isBundle)
        assertEquals(SHOP_PLAN_STATUS_NORMAL, bundle.status)
        assertEquals("无限", bundle.stockSummary)
        assertTrue(bundle.needAddress)
        // 商品封面为空、SKU 有图 → 回退 SKU 图
        assertEquals("https://example.com/a.png", bundle.pic)
    }

    @Test
    fun singleSkuStockSummaryUsesSkuStock() {
        val root = Json.parseToJsonElement(
            """
            {
              "ec": 200, "em": "",
              "data": {
                "sale_list": [{
                  "plan_id": "p1", "name": "单型号", "price": "3.99",
                  "status": 1, "product_type": 1, "bundle_stock": 0,
                  "sponsor_count": 67, "total_amount": "245.33",
                  "sku": [{"sku_id": "s1", "name": "默认型号", "price": "3.99", "stock": 5, "sponsor_count": 70}]
                }]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val product = parseShopProducts(root).single()
        // 单型号商品：库存汇总直接读该型号，且没有「多型号/捆绑包」副标题
        assertEquals("5", product.stockSummary)
        assertNull(product.subtitle)
    }

    @Test
    fun dirtyRowsAreSkipped() {
        val root = Json.parseToJsonElement(
            """
            {
              "ec": 200, "em": "",
              "data": {
                "sale_list": [
                  {"name": "缺 plan_id 的脏数据", "sku": []},
                  {"plan_id": "p_ok", "name": "正常数据", "price": "1.00", "status": 1, "product_type": 1, "sku": []}
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val products = parseShopProducts(root)
        assertEquals(1, products.size)
        assertEquals("p_ok", products[0].planId)
    }

    @Test
    fun parsesPlanSkusForStockSheet() {
        val root = Json.parseToJsonElement(
            """
            {
              "ec": 200, "em": "",
              "data": {
                "plan": {"plan_id": "p1", "name": "测试商品", "product_type": 2, "bundle_stock": ""},
                "list": [
                  {"sku_id": "s1", "name": "型号A", "price": "2.29", "stock": "10", "sponsor_count": "3"}
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val result = parseShopPlanSkus(root)
        assertTrue(result.isBundle)
        assertEquals("p1", result.planId)
        // bundle_stock 为空串 = 无限
        assertEquals("", result.bundleStock)
        assertEquals(1, result.skus.size)
        assertEquals("10", result.skus[0].stock)
    }

    @Test
    fun missingDataReturnsEmptySkus() {
        val result = parseShopPlanSkus(Json.parseToJsonElement("{}").jsonObject)
        assertFalse(result.isBundle)
        assertTrue(result.skus.isEmpty())
    }

    @Test
    fun stockLabelRules() {
        // 空 / null = 无限；数字原样展示
        assertEquals("无限", stockLabel(null))
        assertEquals("无限", stockLabel(""))
        assertEquals("0", stockLabel("0"))
        assertEquals("5", stockLabel("5"))
    }
}
