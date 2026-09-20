package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.parseSponsorManageItem
import com.hrsthrt74.afdian.data.content.parseSponsorManagePage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 赞助者管理列表解析回归测试：字段映射、缺省容错、未知字段忽略。 */
class SponsorManageParserTest {
    @Test
    fun parsesFullSponsorItem() {
        val root = Json.parseToJsonElement(
            """
            {
              "ec": 200,
              "em": "",
              "data": {
                "total_count": 306,
                "total_page": 31,
                "list": [
                  {
                    "sponsor_plans": [
                      {
                        "plan_id": "plan-1",
                        "name": "相册表盘 DIY",
                        "price": "3.99",
                        "pay_month": 1,
                        "product_type": 1,
                        "desc": "<h1>巨型 HTML 不应被映射</h1>",
                        "shipping_fee_info": {"id": 11}
                      }
                    ],
                    "current_plan": {"plan_id": "plan-1", "name": "相册表盘 DIY"},
                    "all_sum_amount": "3.99",
                    "create_time": 1789127819,
                    "last_pay_time": 1789891359,
                    "user": {
                      "user_id": "u-1",
                      "name": "gsr",
                      "avatar": "https://a/1.png",
                      "url_slug": ""
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val page = parseSponsorManagePage(root)
        assertEquals(306, page.totalCount)
        assertEquals(31, page.totalPages)
        assertEquals(1, page.list.size)

        val item = page.list.first()
        assertEquals("u-1", item.userId)
        assertEquals("gsr", item.name)
        assertEquals("https://a/1.png", item.avatar)
        assertEquals("相册表盘 DIY", item.currentPlanName)
        assertEquals("3.99", item.allSumAmount)
        assertEquals(1789891359L, item.lastPayTime)
        assertEquals(1789127819L, item.createTime)

        assertEquals(1, item.plans.size)
        val plan = item.plans.first()
        assertEquals("plan-1", plan.planId)
        assertEquals("相册表盘 DIY", plan.name)
        assertEquals("3.99", plan.price)
        assertEquals(1, plan.payMonth)
        assertEquals(1, plan.productType)
    }

    @Test
    fun missingCurrentPlanBecomesNullNotCrash() {
        val item = parseSponsorManageItem(
            Json.parseToJsonElement(
                """
                {
                  "sponsor_plans": [],
                  "all_sum_amount": "0.00",
                  "create_time": 100,
                  "last_pay_time": 200,
                  "user": {"user_id": "u-2", "name": "无计划用户"}
                }
                """.trimIndent(),
            ).jsonObject,
        )!!

        assertNull(item.currentPlanName)
        assertNull(item.avatar)
        assertTrue(item.plans.isEmpty())
        assertEquals(0, item.userId.compareTo("u-2"))
    }

    @Test
    fun itemWithoutUserIdIsDropped() {
        val page = parseSponsorManagePage(
            Json.parseToJsonElement(
                """
                {
                  "data": {
                    "total_count": 1,
                    "total_page": 1,
                    "list": [
                      {"user": {"name": "缺 id 的脏数据"}},
                      {"user": {"user_id": "u-3", "name": "正常"}}
                    ]
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        assertEquals(1, page.list.size)
        assertEquals("u-3", page.list[0].userId)
    }

    @Test
    fun emptyDataFallsBackToZeroes() {
        val page = parseSponsorManagePage(Json.parseToJsonElement("{}").jsonObject)
        assertEquals(0, page.totalCount)
        assertEquals(0, page.totalPages)
        assertTrue(page.list.isEmpty())
    }
}
