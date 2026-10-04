package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.content.AutoMessageKind
import com.hrsthrt74.afdian.data.content.MessageContent
import com.hrsthrt74.afdian.data.content.MessageOrderAddress
import com.hrsthrt74.afdian.data.content.parseMessageDialog
import com.hrsthrt74.afdian.data.content.parseMessageItem
import com.hrsthrt74.afdian.data.content.parseNoticeItem
import com.hrsthrt74.afdian.data.content.parseOrderExt
import com.hrsthrt74.afdian.data.content.parseSendResponse
import com.hrsthrt74.afdian.data.content.resolveTextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun complaintTextParsesOrderAndComplaintNumbers() {
        // 普通投诉真实模板（脱敏）：标题 → 投诉单号 → 留言 → 时间 → 金额 → 订单号 → 详情链接
        val result = resolveTextContent(
            """
            用户在微信发起投诉，该笔订单会暂时冻结，用户不再投诉之后10天自动解冻，请在24小时内回复并联系用户撤诉，如超期可能会影响你正常收款：
            200000020261003140524969289
            我在爱发电曾经购买过应用，但是现在我不知道这个账号所对应的电话号码或是邮箱是什么了，现在只能看到微信付款记录
            2026-10-03 14:35:28
            4.99
            20260308092607102985354573
            https://ifdian.net/u/c37c94763a8d11f1bf6452540325c347
            """.trimIndent(),
            messageType = 1,
        )

        val notice = (result as MessageContent.Complaint).notice
        assertEquals("微信", notice.platform)
        assertFalse(notice.isUpgradeComplaint)
        // 此前订单号与投诉单号恰好取反，此处锁定正确映射
        assertEquals("200000020261003140524969289", notice.complaintNo)
        assertEquals("20260308092607102985354573", notice.orderNo)
        assertEquals(
            "我在爱发电曾经购买过应用，但是现在我不知道这个账号所对应的电话号码或是邮箱是什么了，现在只能看到微信付款记录",
            notice.message,
        )
        assertEquals("2026-10-03 14:35:28", notice.time)
        assertEquals("4.99", notice.amount)
        assertEquals("https://ifdian.net/u/c37c94763a8d11f1bf6452540325c347", notice.detailUrl)
    }

    @Test
    fun upgradeComplaintTextParsesReasonAndDescription() {
        // 升级投诉真实模板（脱敏）：详情链接夹在时间与金额之间，订单号在最后一行
        val result = resolveTextContent(
            """
            用户在微信发起投诉，该笔订单会暂时冻结，用户不再投诉之后10天自动解冻，请在24小时内回复并联系用户撤诉，如超期可能会影响你正常收款：
            200000020260518000574655634
            用户升级投诉原因：商家响应不及时
            用户升级投诉描述：不理我也根本没用我不行了
            2026-05-18 14:34:14
            https://afdian.com/u/8cdb4354ac5d11f199ea93450025c254
            9.99
            202605162331261014957937133
            """.trimIndent(),
            messageType = 1,
        )

        val notice = (result as MessageContent.Complaint).notice
        assertTrue(notice.isUpgradeComplaint)
        assertEquals("200000020260518000574655634", notice.complaintNo)
        assertEquals("商家响应不及时", notice.reason)
        assertEquals("不理我也根本没用我不行了", notice.description)
        assertEquals("2026-05-18 14:34:14", notice.time)
        assertEquals("https://afdian.com/u/8cdb4354ac5d11f199ea93450025c254", notice.detailUrl)
        assertEquals("9.99", notice.amount)
        assertEquals("202605162331261014957937133", notice.orderNo)
        // 升级投诉模板没有留言字段
        assertNull(notice.message)
    }

    @Test
    fun upgradeComplaintWithoutReasonLineStillParses() {
        // 「原因」行可能缺省（直接从描述行开始），逐行特征解析不受行号偏移影响
        val result = resolveTextContent(
            """
            用户在微信发起投诉，该笔订单会暂时冻结，用户不再投诉之后10天自动解冻，请在24小时内回复并联系用户撤诉，如超期可能会影响你正常收款：
            200000020260518000574655634
            用户升级投诉描述：不理我也根本没用我不行了
            2026-05-18 14:34:14
            https://afdian.com/u/8cdb4354ac5d11f199ea93450025c254
            9.99
            202605162331261014957937133
            """.trimIndent(),
            messageType = 1,
        )

        val notice = (result as MessageContent.Complaint).notice
        assertNull(notice.reason)
        assertEquals("不理我也根本没用我不行了", notice.description)
        assertEquals("2026-05-18 14:34:14", notice.time)
        assertEquals("9.99", notice.amount)
        assertEquals("202605162331261014957937133", notice.orderNo)
    }

    @Test
    fun complaintAmountRestoresMissingDecimalPoint() {
        fun amountOf(amount: String): String? =
            (resolveTextContent(complaintText(amount), messageType = 1) as MessageContent.Complaint).notice.amount

        // 丢失小数点：末两位还原为小数
        assertEquals("4.99", amountOf("499"))
        assertEquals("100.00", amountOf("10000"))
        // 不足两位时前面补 0
        assertEquals("0.50", amountOf("50"))
        assertEquals("0.05", amountOf("5"))
        // 已带小数点的金额原样保留
        assertEquals("4.99", amountOf("4.99"))
    }

    /** 构造普通投诉模板文本（脱敏），金额行可替换，便于金额规范化断言。 */
    private fun complaintText(amount: String): String =
        """
        用户在微信发起投诉，该笔订单会暂时冻结，用户不再投诉之后10天自动解冻，请在24小时内回复并联系用户撤诉，如超期可能会影响你正常收款：
        200000020261003140524969289
        我在爱发电曾经购买过应用
        2026-10-03 14:35:28
        $amount
        20260308092607102985354573
        https://ifdian.net/u/c37c94763a8d11f1bf6452540325c347
        """.trimIndent()

    @Test
    fun specialTextTypesKeepTheirOriginalMeaning() {
        assertTrue(resolveTextContent("code-1", messageType = 5) is MessageContent.Code)

        // type7 是系统自动发出的消息，type4 是购买成功后的自动回复，普通 type 不带标注
        assertEquals(
            AutoMessageKind.AUTO_SENT,
            (resolveTextContent("欢迎", messageType = 7) as MessageContent.Text).autoKind,
        )
        assertEquals(
            AutoMessageKind.PURCHASE_REPLY,
            (resolveTextContent("感谢支持", messageType = 4) as MessageContent.Text).autoKind,
        )
        assertNull((resolveTextContent("在吗", messageType = 1) as MessageContent.Text).autoKind)
    }

    @Test
    fun htmlMessageCleansBreaksAndAnchors() {
        // 客服自动回复模板：每个 <br> 都换一行（与网页端一致，连续 <br> 即空行），
        // <a> 标签还原为链接文字
        val raw = "你好<br>  <br>当月收入可在下月进行提现。<br>  <br>如需提现，" +
            "<a class=\"purple\" href=\"https://afdian.com/p/abc123\">点此看申请格式</a> 查看详情。"
        val content = resolveTextContent(raw, messageType = 7) as MessageContent.Text

        assertEquals("你好\n\n当月收入可在下月进行提现。\n\n如需提现，点此看申请格式 查看详情。", content.text)
        assertEquals(AutoMessageKind.AUTO_SENT, content.autoKind)
        // 链接区间必须正好落在还原出的链接文字上
        val link = content.links.single()
        assertEquals("https://afdian.com/p/abc123", link.url)
        assertEquals("点此看申请格式", content.text.substring(link.start, link.end))
    }

    @Test
    fun plainTextMessageSkipsHtmlCleaning() {
        // 纯文本消息原样保留，不产生链接区间（裸 URL 的点击 Span 由展示层负责）
        val content = resolveTextContent(
            "在吗？\n看下这个 https://afdian.com/a",
            messageType = 1,
        ) as MessageContent.Text

        assertEquals("在吗？\n看下这个 https://afdian.com/a", content.text)
        assertTrue(content.links.isEmpty())
    }

    @Test
    fun htmlMessageDecodesEntitiesAndStripsUnknownTags() {
        // HTML 实体解码（&nbsp;→不间断空格、&amp;→&），未知标签剥壳保留文本
        val content = resolveTextContent("A&nbsp;B<b>C</b>D&amp;E", messageType = 1) as MessageContent.Text

        assertEquals("A\u00A0BCD&E", content.text)
        assertTrue(content.links.isEmpty())
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
    fun textMessageBecomesAutoSentText() {
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
        assertEquals(AutoMessageKind.AUTO_SENT, text.autoKind)
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

    @Test
    fun likeNoticeParsesUserPostAndCursor() {
        // 真实接口样本（脱敏）：publish_sn 是数字游标，action_item 带点赞人与点赞时间
        val notice = parseNoticeItem(
            Json.parseToJsonElement(
                """
                {
                  "subject": "喜欢了动态",
                  "action": "like",
                  "object": "post",
                  "publish_sn": 17908172626149,
                  "action_item": {
                    "post_id": "1215e0e2739b11f0886552540025c377",
                    "publish_time": 1790817262,
                    "user": {
                      "user_id": "8afa7864b4af11f1aab15254001e7c00",
                      "name": "莫名其妙",
                      "avatar": "https://pic1.afdiancdn.com/user/avatar.jpeg",
                      "url_slug": "",
                      "is_verified": 0
                    }
                  },
                  "object_item": {},
                  "post": {
                    "post_id": "1215e0e2739b11f0886552540025c377",
                    "title": "表盘「PoP☆P」",
                    "desc": "",
                    "thumb": "https://pic1.afdiancdn.com/user/cover.png",
                    "pic_count": 1
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        assertEquals("喜欢了动态", notice.subject)
        assertEquals(17908172626149L, notice.publishSn)
        assertEquals("17908172626149", notice.id)
        assertEquals(1790817262L, notice.time)
        assertNull(notice.commentText)
        assertEquals("莫名其妙", notice.user.name)
        assertEquals("8afa7864b4af11f1aab15254001e7c00", notice.user.user_id)
        assertEquals("https://pic1.afdiancdn.com/user/avatar.jpeg", notice.user.avatar)
        // 点赞通知的动态摘要在顶层 post 字段
        assertEquals("表盘「PoP☆P」", notice.post?.title)
        assertEquals("https://pic1.afdiancdn.com/user/cover.png", notice.post?.thumb)
        assertEquals(1, notice.post?.picCount)
    }

    @Test
    fun commentNoticeParsesContentFromObjectItem() {
        // 真实接口样本（脱敏）：评论通知的动态摘要在 object_item，正文在 action_item.content
        val notice = parseNoticeItem(
            Json.parseToJsonElement(
                """
                {
                  "subject": "评论了动态",
                  "action": "comment",
                  "object": "post",
                  "publish_sn": 17908653612459,
                  "action_item": {
                    "comment_id": "6c6e7f90bda511f1aaf05254001e7c00",
                    "post_id": "5b881fa8e6ee11f0ae2752540025c377",
                    "content": "测试",
                    "like_count": 0,
                    "publish_time": 1790865361,
                    "has_like": 0,
                    "user": {
                      "user_id": "4c2c65089ed811f19f9052540025c377",
                      "name": "WASDimension",
                      "avatar": "https://pic1.afdiancdn.com/default/avatar/avatar-blue.png"
                    }
                  },
                  "object_item": {
                    "post_id": "5b881fa8e6ee11f0ae2752540025c377",
                    "title": "CAPTURE THE MOMENT",
                    "desc": "徕卡风格表盘",
                    "thumb": "https://pic1.afdiancdn.com/user/cover2.png",
                    "pic_count": 1
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        assertEquals("评论了动态", notice.subject)
        assertEquals(17908653612459L, notice.publishSn)
        assertEquals(1790865361L, notice.time)
        assertEquals("6c6e7f90bda511f1aaf05254001e7c00", notice.commentId)
        assertEquals("测试", notice.commentText)
        assertEquals("WASDimension", notice.user.name)
        // 评论通知的动态摘要在 object_item 字段
        assertEquals("CAPTURE THE MOMENT", notice.post?.title)
        assertEquals("徕卡风格表盘", notice.post?.desc)
        assertEquals("https://pic1.afdiancdn.com/user/cover2.png", notice.post?.thumb)
    }

    @Test
    fun noticeItemToleratesMissingFields() {
        // 动态被删除等场景下字段可能缺失，解析不应抛异常，空值用默认兜底
        val notice = parseNoticeItem(
            Json.parseToJsonElement(
                """
                {
                  "subject": "喜欢了动态",
                  "publish_sn": 17579452680600,
                  "action_item": {},
                  "object_item": {}
                }
                """.trimIndent(),
            ).jsonObject,
        )

        assertEquals(17579452680600L, notice.publishSn)
        assertNull(notice.time)
        assertNull(notice.post)
        assertNull(notice.commentText)
        assertEquals("", notice.user.name)
    }
}
