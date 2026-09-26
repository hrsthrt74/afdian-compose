package com.hrsthrt74.afdian.data.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val messageJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

private const val SHIPMENT_PREFIX = "你购买的商品已发货"
private const val TYPE_REDEEM_CODE = 5
private const val TYPE_PURCHASE_REPLY = 4  // 购买成功后对方系统自动回复
private const val TYPE_AUTO_SENT = 7       // 系统自动发出的消息（FAQ 等场景）
private const val COMPLAINT_MARKER = "该笔订单会暂时冻结"

/** 将发货系统文本转换成结构化通知；未命中固定前缀时返回 null。 */
private fun tryParseShipmentNotice(text: String): ShipmentNotice? {
    if (!text.startsWith(SHIPMENT_PREFIX)) return null

    var productName: String? = null
    var orderNo: String? = null
    var trackingNo: String? = null
    var courier: String? = null
    val detailUrl = Regex("https?://[^\\s]+").find(text)?.value
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        when {
            line.startsWith("商品名称") -> productName = extractShipmentValue(line, "商品名称")
            line.startsWith("爱发电订单号") -> orderNo = extractShipmentValue(line, "爱发电订单号")
            line.startsWith("快递运单号") -> trackingNo = extractShipmentValue(line, "快递运单号")
            line.startsWith("快递公司") -> courier = extractShipmentValue(line, "快递公司")
        }
    }
    return ShipmentNotice(productName, orderNo, trackingNo, courier, detailUrl)
}

private fun extractShipmentValue(line: String, label: String): String? {
    var rest = line.removePrefix(label).trimStart()
    rest = rest.removePrefix("：").removePrefix(":").trim()
    return rest.takeIf { it.isNotEmpty() }
}

/** 统一解析文本消息，保留兑换码、系统通知和自动消息标注的既有映射。 */
internal fun resolveTextContent(text: String, messageType: Int): MessageContent = when {
    messageType == TYPE_REDEEM_CODE -> MessageContent.Code(text)
    else -> tryParseComplaintNotice(text)?.let { MessageContent.Complaint(it) }
        ?: tryParseShipmentNotice(text)?.let { MessageContent.Shipment(it) }
        ?: MessageContent.Text(text, autoKind = resolveAutoMessageKind(messageType))
}

/** 把接口消息类型映射为自动消息标注；type4（购买成功自动回复）与 type7（自动发出）以外的类型返回 null。 */
private fun resolveAutoMessageKind(messageType: Int): AutoMessageKind? = when (messageType) {
    TYPE_PURCHASE_REPLY -> AutoMessageKind.PURCHASE_REPLY
    TYPE_AUTO_SENT -> AutoMessageKind.AUTO_SENT
    else -> null
}

/** 解析普通投诉和升级投诉两种固定文本模板。 */
private fun tryParseComplaintNotice(text: String): ComplaintNotice? {
    if (!text.contains(COMPLAINT_MARKER)) return null

    val platform = when {
        text.contains("微信") -> "微信"
        text.contains("支付宝") -> "支付宝"
        else -> null
    }
    val isUpgradeComplaint = text.contains("升级投诉")
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.drop(1).toList()
    val timeIndex = if (isUpgradeComplaint) 3 else 2
    val amountIndex = if (isUpgradeComplaint) 4 else 3
    val complaintNoIndex = if (isUpgradeComplaint) 5 else 4
    val upgradeReason = lines.getOrNull(1)
        ?.removePrefix("用户升级投诉原因：")
        ?.removePrefix("用户升级投诉原因:")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val upgradeDescription = lines.getOrNull(2)
        ?.removePrefix("用户升级投诉描述：")
        ?.removePrefix("用户升级投诉描述:")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    return ComplaintNotice(
        platform = platform,
        isUpgradeComplaint = isUpgradeComplaint,
        orderNo = lines.getOrNull(0),
        message = lines.getOrNull(1).takeUnless { isUpgradeComplaint },
        reason = upgradeReason.takeIf { isUpgradeComplaint },
        description = upgradeDescription.takeIf { isUpgradeComplaint },
        time = lines.getOrNull(timeIndex),
        amount = lines.getOrNull(amountIndex),
        complaintNo = lines.getOrNull(complaintNoIndex),
        detailUrl = Regex("https?://[^\\s\\])]+").find(text)?.value,
    )
}

/** 订单 ext 中的兑换码、收货地址和优惠码信息。 */
internal data class OrderExt(
    val redeem: String? = null,
    val address: MessageOrderAddress? = null,
    val affiliateReduction: String? = null,
    val affiliateCode: String? = null,
)

/** 从订单 content.ext 解析可选字段，字段缺失时保持空值。 */
internal fun parseOrderExt(contentObject: JsonObject): OrderExt {
    val ext = contentObject["ext"] as? JsonObject ?: return OrderExt()
    val redeem = ext["redeem"]?.jsonPrimitive?.contentOrNull
    val address = ext["address"]?.let {
        runCatching { messageJson.decodeFromJsonElement(MessageOrderAddress.serializer(), it) }.getOrNull()
    }
    val affiliateCode = ext["affiliate_code"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    val affiliateReduction = ext["affiliate_reduction"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.toBigDecimalOrNull()?.signum() == 1 }
    return OrderExt(redeem, address, affiliateReduction, affiliateCode)
}

/** 解析会话列表里的单个会话对象；字段缺失时保持空值。 */
internal fun parseMessageDialog(obj: JsonObject): MessageDialog {
    val userObj = obj["user"]?.jsonObject
    val user = if (userObj != null) {
        val avatar = userObj["avatar"]?.jsonPrimitive?.contentOrNull
            ?: userObj["headimgurl"]?.jsonPrimitive?.contentOrNull
        AfadianUser(
            user_id = userObj["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
            name = userObj["name"]?.jsonPrimitive?.contentOrNull ?: "",
            avatar = avatar,
            url_slug = userObj["url_slug"]?.jsonPrimitive?.contentOrNull,
            cover = userObj["cover"]?.jsonPrimitive?.contentOrNull,
            is_verified = userObj["is_verified"]?.jsonPrimitive?.content?.toIntOrNull(),
        )
    } else {
        AfadianUser()
    }
    return MessageDialog(
        latest_msg_id = obj["latest_msg_id"]?.jsonPrimitive?.content?.toIntOrNull(),
        unread_count = obj["unread_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        total_count = obj["total_count"]?.jsonPrimitive?.content?.toIntOrNull(),
        status = obj["status"]?.jsonPrimitive?.content?.toIntOrNull(),
        user = user,
        desc = obj["desc"]?.jsonPrimitive?.contentOrNull,
        send_time = obj["send_time"]?.jsonPrimitive?.content?.toDoubleOrNull(),
        sponsor_info = runCatching {
            obj["sponsor_info"]?.jsonObject?.let { sponsorObject ->
                SponsorInfo(
                    out_detail = sponsorObject["out_detail"]?.jsonObject?.toSponsorAmount(),
                    in_detail = sponsorObject["in_detail"]?.jsonObject?.toSponsorAmount(),
                )
            }
        }.getOrNull(),
    )
}

private fun JsonObject.toSponsorAmount() = SponsorAmount(
    all_sum_amount = this["all_sum_amount"]?.jsonPrimitive?.contentOrNull,
    current_amount = this["current_amount"]?.jsonPrimitive?.contentOrNull,
    longest_expire_time = this["longest_expire_time"]?.jsonPrimitive?.content?.toLongOrNull(),
)

/**
 * 解析会话消息列表里的单条消息：
 * content 字段会随消息类型返回字符串、订单对象或群邀请对象，这里统一收敛为 MessageContent。
 */
internal fun parseMessageItem(obj: JsonObject): MessageItem {
    val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: ""
    val messageObject = obj["message"]?.jsonObject ?: return MessageItem(type = type)
    val contentElement = messageObject["content"]
    val skuDetailSource = if (contentElement is JsonObject) contentElement else messageObject

    val content = when {
        contentElement == null || contentElement is JsonNull -> MessageContent.Empty
        contentElement is JsonPrimitive && contentElement.isString -> resolveTextContent(
            contentElement.content,
            messageObject["type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        )
        contentElement is JsonObject -> {
            val skuDetail = skuDetailSource["sku_detail"]?.jsonArray?.mapNotNull { skuElement ->
                runCatching {
                    val skuObject = skuElement.jsonObject
                    MessageSKU(
                        sku_id = skuObject["sku_id"]?.jsonPrimitive?.contentOrNull,
                        count = skuObject["count"]?.jsonPrimitive?.content?.toIntOrNull(),
                        name = skuObject["name"]?.jsonPrimitive?.contentOrNull,
                        pic = skuObject["pic"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            }
            val planElement = contentElement["plan"]
            val plan = if (planElement is JsonObject) {
                CreatorPlan(
                    plan_id = planElement["plan_id"]?.jsonPrimitive?.contentOrNull,
                    name = planElement["name"]?.jsonPrimitive?.contentOrNull,
                    title = planElement["title"]?.jsonPrimitive?.contentOrNull,
                    pic = planElement["pic"]?.jsonPrimitive?.contentOrNull,
                    price = planElement["price"]?.jsonPrimitive?.contentOrNull,
                    show_price = planElement["show_price"]?.jsonPrimitive?.contentOrNull,
                )
            } else {
                null
            }
            val orderExt = parseOrderExt(contentElement)
            val group = (contentElement["group"] as? JsonObject)?.let {
                runCatching { messageJson.decodeFromJsonElement(MessageGroup.serializer(), it) }.getOrNull()
            }
            MessageContent.Object(
                MessageObjectContent(
                    out_trade_no = contentElement["out_trade_no"]?.jsonPrimitive?.contentOrNull,
                    show_amount = contentElement["show_amount"]?.jsonPrimitive?.contentOrNull,
                    total_amount = contentElement["total_amount"]?.jsonPrimitive?.contentOrNull,
                    per_month = contentElement["per_month"]?.jsonPrimitive?.contentOrNull,
                    month = contentElement["month"]?.jsonPrimitive?.content?.toIntOrNull(),
                    remark = contentElement["remark"]?.jsonPrimitive?.contentOrNull,
                    redeem = orderExt.redeem,
                    address = orderExt.address,
                    affiliateReduction = orderExt.affiliateReduction,
                    affiliateCode = orderExt.affiliateCode,
                    group = group,
                    plan = plan,
                    sku_detail = skuDetail,
                    product_type = contentElement["product_type"]?.jsonPrimitive?.content?.toIntOrNull(),
                ),
            )
        }
        else -> MessageContent.Empty
    }
    return MessageItem(
        type = type,
        message = MessageRecord(
            msg_id = messageObject["msg_id"]?.jsonPrimitive?.content?.toIntOrNull(),
            id = messageObject["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            sender = messageObject["sender"]?.jsonPrimitive?.contentOrNull,
            r_status = messageObject["r_status"]?.jsonPrimitive?.content?.toIntOrNull(),
            type = messageObject["type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            content = content,
            send_time = messageObject["send_time"]?.jsonPrimitive?.content?.toDoubleOrNull(),
        ),
    )
}

/** 发送/快捷提问响应的解码结果：业务码、错误文案与解析出的新消息。 */
internal data class MessageSendResult(
    val ec: Int? = null,
    val errorMessage: String? = null,
    val item: MessageItem? = null,
)

/** 解析消息发送、快捷提问的接口响应；data 缺失或结构异常时 item 为 null。 */
internal fun parseSendResponse(body: String): MessageSendResult {
    val response = messageJson.decodeFromString(MessageSendResponse.serializer(), body)
    return MessageSendResult(
        ec = response.ec,
        errorMessage = response.em ?: response.message,
        item = response.data?.toMessageItem(),
    )
}

/** 从响应顶层提取业务错误文案，供消息接口统一报错。 */
internal fun JsonObject.messageErrorText(): String? = sequenceOf("em", "msg", "message")
    .mapNotNull { key -> this[key]?.jsonPrimitive?.contentOrNull }
    .firstOrNull { it.isNotBlank() }

@Serializable
private data class MessageSendResponse(
    val ec: Int? = null,
    val em: String? = null,
    val message: String? = null,
    val data: MessageSendItemRaw? = null,
)

@Serializable
private data class MessageSendItemRaw(
    val type: String = "",
    val message: MessageSendRecordRaw = MessageSendRecordRaw(),
) {
    fun toMessageItem() = MessageItem(type = type, message = message.toMessageRecord())
}

@Serializable
private data class MessageSendRecordRaw(
    val msg_id: Int? = null,
    val id: Int = 0,
    val sender: String? = null,
    val r_status: Int? = null,
    val type: Int = 0,
    val content: JsonElement? = null,
    val send_time: Double? = null,
)

private fun MessageSendRecordRaw.toMessageRecord(): MessageRecord {
    val messageContent = when {
        content == null || content is JsonNull -> MessageContent.Empty
        content is JsonPrimitive && content.isString -> resolveTextContent(content.content, type)
        content is JsonObject -> {
            val objectContent = messageJson.decodeFromJsonElement(MessageObjectContent.serializer(), content)
            val orderExt = parseOrderExt(content)
            MessageContent.Object(
                objectContent.copy(
                    redeem = orderExt.redeem,
                    address = orderExt.address,
                    affiliateReduction = orderExt.affiliateReduction,
                    affiliateCode = orderExt.affiliateCode,
                ),
            )
        }
        else -> MessageContent.Empty
    }
    return MessageRecord(
        msg_id = msg_id,
        id = id,
        sender = sender,
        r_status = r_status,
        type = type,
        content = messageContent,
        send_time = send_time,
    )
}
