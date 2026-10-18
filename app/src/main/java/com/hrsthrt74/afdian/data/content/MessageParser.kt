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

/** 投诉通知的时间行，如 2026-05-18 14:34:14。 */
private val COMPLAINT_TIME_REGEX = Regex("""\d{4}-\d{1,2}-\d{1,2}[ T]\d{1,2}:\d{2}(:\d{2})?""")

/** 金额行：短数字或带小数点数字；服务器丢失小数点时形如 499。 */
private val COMPLAINT_AMOUNT_REGEX = Regex("""\d{1,10}(\.\d{1,2})?""")

/** 投诉单号/订单号的最短位数（实际约 26 位），远长于金额，用于区分超长纯数字行。 */
private const val COMPLAINT_LONG_DIGITS_MIN = 15

/** 详情链接提取：除空白与右括号外连续取字符，避免截断带参数的 URL。 */
private val COMPLAINT_URL_REGEX = Regex("https?://[^\\s\\])]+")

/** 补货通知的固定文案标记：库存告急时服务端下发的模板必带「库存仅剩」。 */
private const val RESTOCK_MARKER = "库存仅剩"

/** 补货通知里的方案名：形如 发电方案「X」，兼容全角/半角引号。 */
private val RESTOCK_PLAN_REGEX = Regex("""发电方案[「“"]([^」”"]+)[」”"]""")

/** 补货通知里的型号名：形如 型号「X」；方案未启用型号时整段缺失。 */
private val RESTOCK_SKU_REGEX = Regex("""型号[「“"]([^」”"]+)[」”"]""")

/** 补货通知里的剩余库存数：形如 库存仅剩5。 */
private val RESTOCK_STOCK_REGEX = Regex("""库存仅剩\s*(\d+)""")

/** 补货通知末尾的补货链接。 */
private val RESTOCK_URL_REGEX = Regex("https?://[^\\s]+")

/**
 * 将库存不足（补货）通知文本转换成结构化通知；未命中固定文案时返回 null。
 * 模板示例：你的发电方案「X」的型号「Y」库存仅剩5，点此前往补货：https://ifdian.net/setting/shop
 */
private fun tryParseRestockNotice(text: String): RestockNotice? {
    if (!text.contains(RESTOCK_MARKER)) return null

    val planName = RESTOCK_PLAN_REGEX.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    val skuName = RESTOCK_SKU_REGEX.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    val stock = RESTOCK_STOCK_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
    // 方案名与库存数都没取到，说明只是正好含「库存仅剩」的普通聊天，交回文本气泡
    if (planName == null && stock == null) return null

    return RestockNotice(
        planName = planName,
        skuName = skuName,
        stock = stock,
        detailUrl = RESTOCK_URL_REGEX.find(text)?.value,
    )
}

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
        ?: tryParseRestockNotice(text)?.let { MessageContent.Restock(it) }
        // 普通文本先做 HTML 清洗（客服自动回复带 <br>/<a> 标签），再挂自动消息标注
        ?: cleanMessageHtml(text).let { cleaned ->
            MessageContent.Text(
                cleaned.text,
                links = cleaned.links,
                autoKind = resolveAutoMessageKind(messageType),
            )
        }
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
    // 首行是固定标题，跳过；其余行按内容特征逐行分类
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.drop(1).toList()

    var complaintNo: String? = null
    var orderNo: String? = null
    var amount: String? = null
    var time: String? = null
    var detailUrl: String? = null
    var reason: String? = null
    var description: String? = null
    // 不匹配任何特征的自由文本行视为留言（仅普通投诉模板存在）
    val messageLines = mutableListOf<String>()
    // 投诉单号与订单号都是约 26 位的纯数字行，按出现顺序区分：第 1 个是投诉单号，第 2 个是订单号
    var longDigitCount = 0

    // 按行特征分类而非固定行号：升级投诉的「原因」行可能缺省，
    // 且详情链接行在两种模板中的位置也不同，固定索引会取错字段
    for (line in lines) {
        when {
            line.startsWith("用户升级投诉原因") ->
                reason = stripComplaintLabel(line, "用户升级投诉原因")
            line.startsWith("用户升级投诉描述") ->
                description = stripComplaintLabel(line, "用户升级投诉描述")
            COMPLAINT_TIME_REGEX.matches(line) -> time = line
            line.startsWith("http") ->
                detailUrl = COMPLAINT_URL_REGEX.find(line)?.value ?: line
            line.all { it.isDigit() } && line.length >= COMPLAINT_LONG_DIGITS_MIN -> {
                longDigitCount++
                if (longDigitCount == 1) complaintNo = line else orderNo = line
            }
            // 金额行必然出现在时间行之后，提前约束避免留言里的短数字被误判为金额
            time != null && COMPLAINT_AMOUNT_REGEX.matches(line) ->
                amount = normalizeComplaintAmount(line)
            else -> messageLines += line
        }
    }

    return ComplaintNotice(
        platform = platform,
        isUpgradeComplaint = isUpgradeComplaint,
        orderNo = orderNo,
        // 升级投诉模板没有留言，自由文本行仅在普通投诉时输出；多行留言按换行拼接
        message = messageLines
            .takeUnless { isUpgradeComplaint }
            ?.joinToString("\n")
            ?.takeIf { it.isNotEmpty() },
        reason = reason,
        description = description,
        time = time,
        amount = amount,
        complaintNo = complaintNo,
        detailUrl = detailUrl,
    )
}

/** 去掉「用户升级投诉原因：」类标签前缀（兼容半角冒号）；标签后内容为空时返回 null。 */
private fun stripComplaintLabel(line: String, label: String): String? =
    line.removePrefix(label)
        .removePrefix("：").removePrefix(":")
        .trim()
        .takeIf { it.isNotEmpty() }

/**
 * 规范化投诉金额：爱发电服务器可能丢失小数点（如 4.99 返回成 499），
 * 无小数点时按「末两位为小数」还原（499 → 4.99、50 → 0.50、5 → 0.05）。
 * 先剥离可能混入的货币符号与千分位逗号；清洗后非纯数字的异常文本原样返回，避免误伤。
 */
private fun normalizeComplaintAmount(raw: String): String {
    // 去除首尾空白、货币符号（¥/￥）与千分位逗号，只留数字与小数点
    val cleaned = raw.trim()
        .removePrefix("¥").removePrefix("￥")
        .replace(",", "")
    if (cleaned.isEmpty()) return raw.trim()
    // 已带小数点说明金额格式正常，直接返回清洗结果
    if (cleaned.contains('.')) return cleaned
    // 清洗后不是纯数字的异常文本不做猜测，原样返回
    if (cleaned.any { !it.isDigit() }) return raw.trim()
    // 无小数点：按末两位还原小数；不足两位时前面补 0
    return when (cleaned.length) {
        1 -> "0.0$cleaned"
        2 -> "0.$cleaned"
        else -> "${cleaned.dropLast(2)}.${cleaned.takeLast(2)}"
    }
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
 * 解析「收到的通知」列表里的单条通知（/api/my/notice，like/comment 通用）。
 *
 * 两种通知的差异点：
 * - 点赞：动态摘要在顶层 `post` 字段，object_item 恒为空对象；
 * - 评论：动态摘要在 `object_item`，评论正文 / comment_id 在 action_item。
 * 未知 action 不丢弃，仅解析公共字段（subject / 游标 / 用户），保证列表不缺行。
 */
internal fun parseNoticeItem(obj: JsonObject): NoticeItem {
    val isComment = obj["action"]?.jsonPrimitive?.contentOrNull == NoticeAction.Comment.apiValue
    val actionObj = obj["action_item"]?.jsonObject
    val postSource = (if (isComment) obj["object_item"] else obj["post"])?.jsonObject
    return NoticeItem(
        subject = obj["subject"]?.jsonPrimitive?.contentOrNull ?: "",
        // publish_sn 是数字字面量，content 取出的是其十进制字符串形式
        publishSn = obj["publish_sn"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        user = actionObj?.get("user")?.jsonObject?.toAfadianUser() ?: AfadianUser(),
        time = actionObj?.get("publish_time")?.jsonPrimitive?.content?.toLongOrNull(),
        commentId = if (isComment) actionObj?.get("comment_id")?.jsonPrimitive?.contentOrNull else null,
        commentText = if (isComment) actionObj?.get("content")?.jsonPrimitive?.contentOrNull else null,
        post = postSource?.toNoticePost(),
    )
}

/** 解析通知里的用户对象，与会话用户同构但无 headimgurl 回退。 */
private fun JsonObject.toAfadianUser(): AfadianUser = AfadianUser(
    user_id = this["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
    name = this["name"]?.jsonPrimitive?.contentOrNull ?: "",
    avatar = this["avatar"]?.jsonPrimitive?.contentOrNull,
    url_slug = this["url_slug"]?.jsonPrimitive?.contentOrNull,
    cover = this["cover"]?.jsonPrimitive?.contentOrNull,
    is_verified = this["is_verified"]?.jsonPrimitive?.content?.toIntOrNull(),
)

/** 解析通知关联的动态摘要（点赞通知的 post / 评论通知的 object_item 同构）。 */
private fun JsonObject.toNoticePost(): NoticePost = NoticePost(
    postId = this["post_id"]?.jsonPrimitive?.contentOrNull ?: "",
    title = this["title"]?.jsonPrimitive?.contentOrNull ?: "",
    desc = this["desc"]?.jsonPrimitive?.contentOrNull,
    thumb = this["thumb"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
    picCount = this["pic_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
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
