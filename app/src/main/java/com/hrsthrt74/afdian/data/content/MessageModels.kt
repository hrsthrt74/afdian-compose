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
    /**
     * LazyColumn 的稳定 key：会话由对端用户唯一确定，服务端按 user_id 归一。
     * 空 user_id 的行（响应缺 user 对象）已由 [mergeDialogs] 丢弃，不会带空 key 进列表。
     */
    val id: String get() = user.user_id
}

/**
 * 合并会话分页，保证返回值满足「id 唯一且非空」——这是消息列表 LazyColumn 的 key 不变式。
 *
 * 为什么必须合并而不能直接 `+`：`/api/message/dialogs` 是**页码分页**，服务端按最新消息时间
 * 倒序返回。翻页期间只要来一条新消息，尾部会话就会下沉到下一页，同一会话在 page N 与
 * page N+1 各出现一次；刷新与在飞翻页交错时也会把旧页追加到新列表上。重复 id 会让
 * `items(key = { it.id })` 直接抛 "Key ... was already used" 闪退。
 *
 * 去重方向：**保留先出现的一条**。existing 是已经在屏幕上的更新快照，incoming 是更旧的页，
 * 同 id 时旧数据不应覆盖新数据，也不该把条目挪到列表尾部造成跳动。
 *
 * user_id 为空/空白的会话直接丢弃：跳转会话页（`Route.MessageConversation`）与
 * `markConversationRead` 都只认 `user.user_id`，这种行既打不开也标不了已读，留着只会误导。
 *
 * 纯函数、保持无副作用（不打日志）：单测里 `AppLog` 最终落到 `android.util.Log`，未 mock 会抛异常；
 * 丢弃条数由调用方 `MessagesViewModel` 记 W 日志。
 */
internal fun mergeDialogs(
    existing: List<MessageDialog>,
    incoming: List<MessageDialog>,
): List<MessageDialog> {
    val merged = ArrayList<MessageDialog>(existing.size + incoming.size)
    val seen = HashSet<String>(existing.size + incoming.size)
    // 先收已有列表：既去重自身，也占住位置，让后面同 id 的旧页数据被丢弃
    for (dialog in existing) {
        // 防御性过滤：既有列表理论上已归一化，避免某次漏走 mergeDialogs 的空行混进来
        if (dialog.user.user_id.isBlank()) continue
        if (seen.add(dialog.id)) merged += dialog
    }
    // 再追加新页里没见过的会话，保持服务端顺序
    for (dialog in incoming) {
        if (dialog.user.user_id.isBlank()) continue
        if (seen.add(dialog.id)) merged += dialog
    }
    return merged
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

/**
 * 自动消息的来源类型，对应接口 message.type 的两个特殊取值，
 * 仅用于对方气泡底部的来源标注；普通消息为 null。
 */
enum class AutoMessageKind {
    /** type4：对方购买成功后系统自动回复的消息 → 标注「此消息为自动回复」。 */
    PURCHASE_REPLY,

    /** type7：系统自动发出的消息（如 FAQ 相关内容）→ 标注「此消息为自动发出」。 */
    AUTO_SENT,
}

/** 清洗后正文里的超链接区间；start/end 为 [MessageContent.Text.text] 的字符索引（含头不含尾）。 */
data class MessageTextLink(
    val start: Int,
    val end: Int,
    val url: String,
)

/** 私信正文的领域模型，屏蔽接口 content 字段的多种 JSON 形态。 */
sealed class MessageContent {
    /** links：正文里的 HTML <a> 链接区间（经 HTML 清洗得到），普通纯文本消息为空列表。 */
    data class Text(
        val text: String,
        val links: List<MessageTextLink> = emptyList(),
        val autoKind: AutoMessageKind? = null,
    ) : MessageContent()
    data class Object(val content: MessageObjectContent) : MessageContent()
    data class Shipment(val notice: ShipmentNotice) : MessageContent()
    data class Complaint(val notice: ComplaintNotice) : MessageContent()
    data class Restock(val notice: RestockNotice) : MessageContent()
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

/** 文本消息识别出的库存不足（补货）通知。 */
data class RestockNotice(
    /** 发电方案名称 */
    val planName: String? = null,
    /** 型号名称；方案没有型号时为空 */
    val skuName: String? = null,
    /** 剩余库存数量 */
    val stock: Int? = null,
    /** 补货设置页链接；当前卡片只做通知，不提供跳转 */
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

/** 通知类型，对应 /api/my/notice 的 action 参数。 */
enum class NoticeAction(val apiValue: String) {
    /** 「收到的喜欢」：XX 喜欢了动态。 */
    Like("like"),

    /** 「收到的评论」：XX 评论了动态。 */
    Comment("comment"),
}

/** 「收到的通知」分页结果（/api/my/notice，like/comment 通用）。 */
data class NoticePage(
    val notices: List<NoticeItem> = emptyList(),
    val hasMore: Boolean = false,
)

/**
 * 一条社区通知（喜欢/评论动态）。
 *
 * publish_sn 是服务端下发的通知游标：既是列表排序键（降序），也是加载
 * 下一页时回传的 publish_sn 参数，必须用 Long 承接（约 1.8e13）。
 * 拉取列表的同时服务端会把对应类型的未读清零（网页端同款行为）。
 */
data class NoticeItem(
    val subject: String = "",
    val publishSn: Long = 0L,
    val user: AfadianUser = AfadianUser(),
    /** 动作时间（秒级时间戳）：点赞时间 / 评论时间。 */
    val time: Long? = null,
    /** 评论 id（仅评论通知有）。 */
    val commentId: String? = null,
    /** 评论正文（仅评论通知有；点赞通知为 null）。 */
    val commentText: String? = null,
    /** 被赞 / 被评论的动态摘要。 */
    val post: NoticePost? = null,
) {
    /** 列表 key：publish_sn 由服务端保证唯一。 */
    val id: String get() = publishSn.toString()
}

/** 通知关联的动态摘要（仅用于展示，不含正文）。 */
data class NoticePost(
    val postId: String = "",
    val title: String = "",
    val desc: String? = null,
    val thumb: String? = null,
    val picCount: Int = 0,
)
