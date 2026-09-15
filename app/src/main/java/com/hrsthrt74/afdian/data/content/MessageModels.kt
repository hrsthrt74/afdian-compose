package com.hrsthrt74.afdian.data.content

import kotlinx.serialization.Serializable

/** 会话列表分页结果。 */
@Serializable
data class MessageDialogPage(
    val dialogs: List<MessageDialog> = emptyList(),
    val totalCount: Int = 0,
    val totalPage: Int = 1,
    val hasMore: Boolean = false,
)

/** 会话消息分页结果。 */
data class MessageConversation(
    val messages: List<MessageItem> = emptyList(),
    val hasMore: Boolean = false,
    val canSendPictureMessage: Boolean = false,
)

@Serializable
data class MessageDialog(
    val latest_msg_id: Int? = null,
    val unread_count: Int = 0,
    val total_count: Int? = null,
    val status: Int? = null,
    val user: AfadianUser = AfadianUser(),
    val desc: String? = null,
    val send_time: Double? = null,
    val sponsor_info: SponsorInfo? = null,
) {
    val id: String get() = user.user_id
}

/** 发电明细：out_detail 是我对他发电，in_detail 是他对我发电。 */
@Serializable
data class SponsorInfo(
    val out_detail: SponsorAmount? = null,
    val in_detail: SponsorAmount? = null,
)

@Serializable
data class SponsorAmount(
    val all_sum_amount: String? = null,
    val current_amount: String? = null,
    val longest_expire_time: Long? = null,
    val list: List<SponsorRecord> = emptyList(),
)

/** 一笔赞助对应的方案、金额与留言。 */
@Serializable
data class SponsorRecord(
    val plan: CreatorPlan? = null,
    val remark: String? = null,
    val monthly_amount: String? = null,
    val show_amount: String? = null,
    val expire_time: Long? = null,
)

/** 单条会话消息。 */
data class MessageItem(
    val type: String = "",
    val message: MessageRecord = MessageRecord(),
) {
    val id: Int get() = message.msg_id ?: message.id
    val isSentByMe: Boolean get() = type == "send"
}

data class MessageRecord(
    val msg_id: Int? = null,
    val id: Int = 0,
    val sender: String? = null,
    val r_status: Int? = null,
    val type: Int = 0,
    val content: MessageContent = MessageContent.Empty,
    val send_time: Double? = null,
)

/** 私信正文的领域模型，屏蔽接口 content 字段的多种 JSON 形态。 */
sealed class MessageContent {
    data class Text(val text: String, val isAutoReply: Boolean = false) : MessageContent()
    data class Object(val content: MessageObjectContent) : MessageContent()
    data class Shipment(val notice: ShipmentNotice) : MessageContent()
    data class Complaint(val notice: ComplaintNotice) : MessageContent()
    data class Code(val text: String) : MessageContent()
    data object Empty : MessageContent()
}

@Serializable
data class MessageObjectContent(
    val out_trade_no: String? = null,
    val show_amount: String? = null,
    val total_amount: String? = null,
    val per_month: String? = null,
    val month: Int? = null,
    val remark: String? = null,
    val redeem: String? = null,
    val address: MessageOrderAddress? = null,
    val affiliateReduction: String? = null,
    val affiliateCode: String? = null,
    val plan: CreatorPlan? = null,
    val sku_detail: List<MessageSKU>? = null,
    val group: MessageGroup? = null,
    val product_type: Int? = null,
)

/** 订单收货地址。 */
@Serializable
data class MessageOrderAddress(
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null,
)

/** 文本消息识别出的发货通知。 */
data class ShipmentNotice(
    val productName: String? = null,
    val orderNo: String? = null,
    val trackingNo: String? = null,
    val courier: String? = null,
    val detailUrl: String? = null,
)

/** 文本消息识别出的投诉通知。 */
data class ComplaintNotice(
    val platform: String? = null,
    val isUpgradeComplaint: Boolean = false,
    val orderNo: String? = null,
    val message: String? = null,
    val reason: String? = null,
    val description: String? = null,
    val time: String? = null,
    val amount: String? = null,
    val complaintNo: String? = null,
    val detailUrl: String? = null,
)

@Serializable
data class MessageSKU(
    val sku_id: String? = null,
    val count: Int? = null,
    val name: String? = null,
    val pic: String? = null,
) {
    val id: String get() = sku_id ?: listOfNotNull(name, pic).joinToString("-")
}

@Serializable
data class MessageGroup(
    val group_id: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val content: String? = null,
    val member_count: Int? = null,
    val join_group_type: Int? = null,
)

@Serializable
data class MessageFAQ(
    val id: Int = 0,
    val title: String = "",
)

@Serializable
data class MessageSendRequest(
    val user_id: String,
    val type: String,
    val content: String,
    val auth_token: String?,
)

/** /api/my/check 返回的轻量未读计数。 */
data class MyCheckData(
    val unreadMessageNum: Int = 0,
    val unreadComment: Int = 0,
    val unreadLike: Int = 0,
    val unreadPrivateMsg: Int = 0,
)
