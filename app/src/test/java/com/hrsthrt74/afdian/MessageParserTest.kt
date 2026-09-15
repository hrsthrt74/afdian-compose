package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.MessageContent
import com.hrsthrt74.afdian.data.content.MessageOrderAddress
import com.hrsthrt74.afdian.data.content.parseMessageDialog
import com.hrsthrt74.afdian.data.content.parseMessageItem
import com.hrsthrt74.afdian.data.content.parseOrderExt
import com.hrsthrt74.afdian.data.content.parseSendResponse
import com.hrsthrt74.afdian.data.content.resolveTextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 消息解析回归测试，确保拆分服务和模型后仍保持原有字段映射。 */
class MessageParserTest {
    @Test
    fun shipmentTextBecomesStructuredNotice() {
        val result = resolveTextContent(
            "你购买的商品已发货\n商品名称：测试商品\n爱发电订单号: order-1\n快递运单号：track-1\n快递公司：顺丰\nhttps://afdian.com/order/1",
            messageType = 1,
        )

        val notice = (result as MessageContent.Shipment).notice
        assertEquals("测试商品", notice.productName)
        assertEquals("order-1", notice.orderNo)
        assertEquals("track-1", notice.trackingNo)
        assertEquals("顺丰", notice.courier)
        assertEquals("https://afdian.com/order/1", notice.detailUrl)
    }

    @Test
    fun specialTextTypesKeepTheirOriginalMeaning() {
        assertTrue(resolveTextContent("code-1", messageType = 5) is MessageContent.Code)

        val autoReply = resolveTextContent("欢迎", messageType = 7)
        assertTrue(autoReply is MessageContent.Text)
        assertTrue((autoReply as MessageContent.Text).isAutoReply)
    }

    @Test
    fun orderExtensionKeepsAddressAndAffiliateFields() {
        val content = Json.parseToJsonElement(
            """
            {
              "ext": {
                "redeem": "redeem-1",
                "affiliate_code": "SAVE10",
                "affiliate_reduction": "10.00",
                "address": {"name": "张三", "phone": "13800000000", "address": "上海"}
              }
            }
            """.trimIndent(),
        ).jsonObject

        val result = parseOrderExt(content)
        assertEquals("redeem-1", result.redeem)
        assertEquals("SAVE10", result.affiliateCode)
        assertEquals("10.00", result.affiliateReduction)
        assertEquals(MessageOrderAddress("张三", "13800000000", "上海"), result.address)
    }

    @Test
    fun dialogKeepsUserAndSponsorInfo() {
        val dialog = parseMessageDialog(
            Json.parseToJsonElement(
                """
                {
                  "latest_msg_id": 9,
                  "unread_count": 2,
                  "desc": "在吗",
                  "send_time": 1700000000.0,
                  "user": {"user_id": "u1", "name": "张三", "avatar": "https://a/1.png", "url_slug": "zhangsan"},
                  "sponsor_info": {
                    "out_detail": {"all_sum_amount": "100.00", "current_amount": "10.00"},
                    "in_detail": {"all_sum_amount": "50.00"}
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        assertEquals(9, dialog.latest_msg_id)
        assertEquals(2, dialog.unread_count)
        assertEquals("在吗", dialog.desc)
        assertEquals("u1", dialog.user.user_id)
        assertEquals("张三", dialog.user.name)
        assertEquals("https://a/1.png", dialog.user.avatar)
        assertEquals("100.00", dialog.sponsor_info?.out_detail?.all_sum_amount)
        assertEquals("50.00", dialog.sponsor_info?.in_detail?.all_sum_amount)
    }

    @Test
    fun textMessageBecomesAutoReplyText() {
        val item = parseMessageItem(
            Json.parseToJsonElement(
                """
                {
                  "type": "old",
                  "message": {
                    "msg_id": 12,
                    "id": 34,
                    "sender": "u1",
                    "r_status": 1,
                    "type": 7,
                    "content": "欢迎光临",
                    "send_time": 1700000000.0
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        assertEquals("old", item.type)
        assertEquals(12, item.message.msg_id)
        val text = item.message.content as MessageContent.Text
        assertEquals("欢迎光临", text.text)
        assertTrue(text.isAutoReply)
    }

    @Test
    fun orderMessageKeepsPlanSkuAndRedeemCode() {
        val item = parseMessageItem(
            Json.parseToJsonElement(
                """
                {
                  "type": "send",
                  "message": {
                    "msg_id": 1,
                    "id": 2,
                    "type": 1,
                    "content": {
                      "out_trade_no": "order-1",
                      "show_amount": "5.00",
                      "month": 1,
                      "plan": {"plan_id": "p1", "name": "月费方案"},
                      "ext": {"redeem": "redeem-1"},
                      "sku_detail": [{"sku_id": "s1", "count": 2, "name": "周边"}]
                    }
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        val content = (item.message.content as MessageContent.Object).content
        assertEquals("order-1", content.out_trade_no)
        assertEquals("5.00", content.show_amount)
        assertEquals(1, content.month)
        assertEquals("月费方案", content.plan?.name)
        assertEquals("redeem-1", content.redeem)
        assertEquals(1, content.sku_detail?.size)
        assertEquals("周边", content.sku_detail?.first()?.name)
    }

    @Test
    fun sendResponseParsesNewMessageAndBusinessCode() {
        val result = parseSendResponse(
            """
            {
              "ec": 200,
              "data": {
                "type": "send",
                "message": {
                  "msg_id": 5,
                  "id": 5,
                  "r_status": 1,
                  "type": 1,
                  "content": "你好",
                  "send_time": 1700000000.0
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(200, result.ec)
        assertEquals(5, result.item?.message?.msg_id)
        assertEquals("你好", (result.item?.message?.content as MessageContent.Text).text)
    }
}
