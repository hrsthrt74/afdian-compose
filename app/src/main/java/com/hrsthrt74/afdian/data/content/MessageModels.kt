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

/**
 * 本地置顶的会话卡片快照。
 *
 * 为什么存整张卡片而不是只存 id：`/api/message/dialogs` 是**页码分页**，置顶的老会话
 * 可能落在第 N 页、首屏根本拿不到；只存 id 的话，这些置顶会「有配置但没入口」。
 * 存下置顶那一刻的展示字段，就能在服务端数据回来之前先把卡片画在列表最前，
 * 点进去仍然按 userId 正常打开会话。
 *
 * 数据新鲜度：服务端按最新消息时间倒序返回，所以置顶会话一旦有新消息就会跳到第一页，
 * 下次刷新时 [withServerData] 会把快照更新成服务端的最新值；长期没消息的会话，
 * 快照本身就是准的（最后一条消息停在置顶那天）。唯一可能过期的是未读数（在别的设备读过）。
 *
 * @param userId 对端用户 id（= [MessageDialog.id]）
 * @param name 会话标题（对方昵称）
 * @param avatar 头像 URL
 * @param desc 最后一条消息的预览文本
 * @param sendTime 最后一条消息的时间（秒级时间戳，与服务端一致）
 * @param unreadCount 未读数（置顶那一刻的值，之后由服务端数据刷新）
 */
@Serializable
data class PinnedDialogCard(
    val userId: String,
    val name: String,
    val avatar: String? = null,
    val desc: String? = null,
    val sendTime: Double? = null,
    val unreadCount: Int = 0,
)

/** 把一条服务端会话转成置顶快照（置顶动作发生的瞬间调用）。 */
internal fun MessageDialog.toPinnedCard(): PinnedDialogCard =
    PinnedDialogCard(
        userId = id,
        name = user.name,
        avatar = user.avatar,
        desc = desc,
        sendTime = send_time,
        unreadCount = unread_count,
    )

/**
 * 用服务端数据刷新快照的展示字段。
 *
 * 昵称/头像为空时保留旧值（服务端偶尔缺字段，不能把卡片刷成空标题）；
 * 其余字段以服务端为准（未读数、最后消息、时间都是服务端更可信）。
 */
internal fun PinnedDialogCard.withServerData(dialog: MessageDialog): PinnedDialogCard = copy(
    name = dialog.user.name.ifBlank { name },
    avatar = dialog.user.avatar ?: avatar,
    desc = dialog.desc,
    sendTime = dialog.send_time,
    unreadCount = dialog.unread_count,
)

/** 把置顶快照还原成一条列表可渲染的会话（服务端还没加载到这条会话时用它补位）。 */
internal fun PinnedDialogCard.toMessageDialog(): MessageDialog = MessageDialog(
    unread_count = unreadCount,
    user = AfadianUser(user_id = userId, name = name, avatar = avatar),
    desc = desc,
    send_time = sendTime,
)

/**
 * 把本地置顶快照并进服务端分页列表：置顶整块在最前，其余保持服务端顺序。
 *
 * 规则：
 * 1. 置顶组顺序 = [pinned] 的顺序（UI 侧按「新置顶在最前」维护），保证置顶后立刻出现在顶部；
 * 2. 置顶会话若已在服务端列表里，**用服务端那一行**（字段更新），不存在才用快照补位；
 * 3. 未置顶部分严格保持服务端顺序，`mergeDialogs` 的「按页追加」语义不受影响；
 * 4. 没有置顶时原样返回同一实例，避免每次流发射都造新列表。
 *
 * 纯函数、无副作用（不打日志）：单测直接调用；日志由 `MessagesViewModel` 负责。
 */
internal fun mergePinnedIntoDialogs(
    dialogs: List<MessageDialog>,
    pinned: List<PinnedDialogCard>,
): List<MessageDialog> {
    if (pinned.isEmpty()) return dialogs

    // 服务端已加载的会话索引：置顶命中时优先用这份较新的数据
    val serverById = HashMap<String, MessageDialog>(dialogs.size)
    for (dialog in dialogs) {
        if (dialog.id.isNotBlank()) serverById[dialog.id] = dialog
    }

    val pinnedIds = HashSet<String>(pinned.size)
    val result = ArrayList<MessageDialog>(dialogs.size + pinned.size)
    for (card in pinned) {
        if (card.userId.isBlank()) continue
        // 同一 id 配了两条（DataStore 被手工改坏）只保留第一条：
        // 重复 id 会让 LazyColumn 的 items(key) 直接抛 "Key ... was already used"
        if (!pinnedIds.add(card.userId)) continue
        // 已加载 → 服务端那一行；未加载（老会话在后面的分页）→ 快照补位
        result += serverById[card.userId] ?: card.toMessageDialog()
    }
    // 未置顶部分保持服务端顺序
    for (dialog in dialogs) {
        if (dialog.id in pinnedIds) continue
        result += dialog
    }
    return result
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
