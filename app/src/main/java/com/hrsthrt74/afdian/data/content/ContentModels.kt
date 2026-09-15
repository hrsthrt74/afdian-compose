package com.hrsthrt74.afdian.data.content

import kotlinx.serialization.Serializable

/** 收藏列表中的统一条目。 */
@Serializable
data class FavoriteItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val image: String? = null,
    val user: AfadianUser? = null,
)

@Serializable
data class AfadianProfile(val user: AfadianUser = AfadianUser())

/** 推荐动态分页结果。 */
data class RecommendedPostsPage(
    val posts: List<RecommendedPost>,
    val nextPublishSN: String,
    val hasMore: Boolean,
)

/** 收藏动态分页结果。 */
data class MarkedPostsPage(
    val posts: List<RecommendedPost>,
    val hasMore: Boolean,
)

/** 上一篇/下一篇动态的精简信息。 */
@Serializable
data class PostOldNew(
    val old: PostOldNewInfo? = null,
    val new: PostOldNewInfo? = null,
)

@Serializable
data class PostOldNewInfo(
    val post_id: String = "",
    val title: String = "",
)

@Serializable
data class RecommendedPost(
    val post_id: String = "",
    val title: String = "",
    val preview: String? = null,
    val content: String? = null,
    val cover: String? = null,
    val like_count: Int? = null,
    val comment_count: Int? = null,
    val user: AfadianUser = AfadianUser(),
    val has_like: Int? = null,
    val pics: List<String>? = null,
    val publish_time: Long? = null,
    val publish_sn: Long? = null,
    val is_public: Int? = null,
    val min_price: String? = null,
    val albums: List<PostAlbum>? = null,
    val old_new: PostOldNew? = null,
) {
    val id: String get() = post_id
    val summary: String get() = (preview ?: content).orEmpty()
}

@Serializable
data class PostAlbum(
    val album_id: String = "",
    val title: String = "",
    val post_count: Int? = null,
)

@Serializable
data class AfadianUser(
    val user_id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val url_slug: String? = null,
    val cover: String? = null,
    val is_verified: Int? = null,
    val creator: AfadianCreator? = null,
)

@Serializable
data class AfadianCreator(
    val doing: String? = null,
    val detail: String? = null,
    val category: AfadianCategory? = null,
)

@Serializable
data class AfadianCategory(val name: String? = null)

@Serializable
data class AccountLogin(val email: String? = null, val phone: String? = null)

@Serializable
data class AccountOauth(val wect: AccountWechat? = null)

@Serializable
data class AccountWechat(val nickname: String? = null, val headimgurl: String? = null)

@Serializable
data class AfadianAccount(
    val login: AccountLogin = AccountLogin(),
    val oauth: AccountOauth = AccountOauth(),
    val user_private_id: String? = null,
) {
    val displayName: String get() = oauth.wect?.nickname ?: login.email ?: login.phone ?: "爱发电用户"
    val avatar: String? get() = oauth.wect?.headimgurl
}

@Serializable
data class SponsoringItem(
    val user: AfadianUser = AfadianUser(),
    val sale_list: List<SponsoringSale> = emptyList(),
    val new_post_count: Int? = null,
    val latest_post_update_time: Long? = null,
) {
    val id: String get() = user.user_id
    val planName: String get() = sale_list.firstOrNull()?.plan?.name ?: sale_list.firstOrNull()?.title ?: "发电计划"
}

@Serializable
data class SponsoringSale(val title: String? = null, val plan: SponsoringPlan? = null)

@Serializable
data class SponsoringPlan(val name: String? = null)

/** 感谢页中的单条赞助者。 */
@Serializable
data class ThankSponsor(
    val user_id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val url_slug: String? = null,
    val doing: String? = null,
) {
    val id: String get() = user_id

    /** 转成通用用户模型，用于跳转创作者主页。 */
    fun toAfadianUser() = AfadianUser(user_id = user_id, name = name, avatar = avatar, url_slug = url_slug)
}

data class ThankSponsorsPage(
    val list: List<ThankSponsor>,
    val hasMore: Boolean,
    val totalLabel: String,
)

/** 优惠码。 */
@Serializable
data class Affiliate(
    val id: Long = 0,
    val affiliate_code: String = "",
    val discount: String = "0",
    val has_time_limit: Int = 0,
    val begin_time: Long = 0,
    val end_time: Long = 0,
    val has_count_limit: Int = 0,
    val count_limit: Int = 0,
    val status: Int = 0,
    val plan_range: String = "",
    val switch: Int = 1,
    val in_valid_period: Int = 0,
    val discount_type: Int = 0,
    val amount_off: String = "0.00",
    val used_count: Int = 0,
    val use_limit_count: Int = 0,
    val count_available: Int = 0,
    val plans: List<AffiliatePlan> = emptyList(),
)

@Serializable
data class AffiliatePlan(
    val plan_id: String = "",
    val name: String = "",
    val pic: String? = null,
    val price: String = "0",
)

data class AffiliateForm(
    val id: String = "",
    val affiliateCode: String,
    val discount: Double,
    val hasTimeLimit: Boolean,
    val beginTime: Long,
    val endTime: Long,
    val hasCountLimit: Boolean,
    val countLimit: String,
    val useLimitCount: String,
    val planRange: String,
    val planIds: String,
)

@Serializable
data class AffiliateSelectablePlan(
    val plan_id: String = "",
    val name: String = "",
    val pic: String? = null,
    val price: String = "0",
    val product_type: Int = 0,
)

/** 收到发电卡片所需的最小订单模型。 */
data class ReceivedSponsor(
    val orderNo: String,
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val paidAtSeconds: Long,
    val planName: String,
    val modelName: String,
    val amount: String,
    val remark: String?,
    val monthCount: Int,
    val totalAmountWithShipping: String,
    val freight: String,
    val validFromSeconds: Long?,
    val validUntilSeconds: Long?,
    val bundlePriceDiff: String?,
    val receiverInfo: String?,
    val expressNo: String?,
    val orderRemark: String?,
    val redeemCode: String?,
    val affiliateReduction: String?,
    val affiliateCode: String?,
)

data class ReceivedSponsorsPage(
    val orders: List<ReceivedSponsor>,
    val hasMore: Boolean,
    val lastOrderId: String?,
)

enum class ReceivedSponsorsSort(val field: String, val direction: String, val title: String) {
    Newest("update_time", "desc", "新订单在前"),
    Oldest("update_time", "asc", "新订单在后"),
    AmountHigh("total_amount", "desc", "金额从高到低"),
    AmountLow("total_amount", "asc", "金额从低到高"),
}

enum class ReceivedShipmentFilter(val title: String, val statusCode: String?) {
    All("不筛选发货", null),
    WithReceiver("含收件信息", "10"),
    Shipped("已发货", "9"),
    Pending("待发货", "8"),
}

/** 收到发电列表的时间范围，null 表示对应边界不限制。 */
data class ReceivedTimeRange(
    val beginMillis: Long? = null,
    val endMillis: Long? = null,
) {
    val beginTime: Long? get() = beginMillis?.div(1000)
    val endTime: Long? get() = endMillis?.div(1000)?.plus(59)
}

@Serializable
data class CreatorDetail(
    val profile: AfadianUser = AfadianUser(),
    val goals: List<CreatorGoal> = emptyList(),
    val topSponsors: List<TopSponsor> = emptyList(),
    val plans: List<CreatorPlan> = emptyList(),
    val albums: List<CreatorAlbum> = emptyList(),
    val posts: List<RecommendedPost> = emptyList(),
    val products: List<CreatorProduct> = emptyList(),
    val isMarked: Boolean = false,
)

@Serializable
data class CreatorGoal(
    val goal_id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val current_amount: String? = null,
    val target_amount: String? = null,
) {
    val displayTitle: String get() = title ?: name ?: "未命名目标"
    val amountText: String get() = when {
        current_amount.isNullOrBlank() && target_amount.isNullOrBlank() -> ""
        else -> "¥${current_amount ?: "--"} / ¥${target_amount ?: "--"}"
    }
}

@Serializable
data class TopSponsor(
    val user: AfadianUser? = null,
    val name: String? = null,
    val avatar: String? = null,
    val total_amount: String? = null,
) {
    val displayName: String get() = user?.name ?: name ?: "匿名赞助者"
    val avatarUrl: String? get() = user?.avatar ?: avatar
    val totalAmountText: String get() = total_amount ?: "0"
}

@Serializable
data class CreatorPlan(
    val plan_id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val pic: String? = null,
    val price: String? = null,
    val show_price: String? = null,
) {
    val displayName: String get() = name ?: title ?: "发电方案"
    val priceText: String get() = show_price ?: price ?: "--"
}

@Serializable
data class CreatorAlbum(
    val album_id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val cover: String? = null,
    val content: String? = null,
    val post_count: Int? = null,
) {
    val displayTitle: String get() = title ?: name ?: "合集"
    val postCountText: String get() = post_count?.let { "$it 篇" } ?: ""
}

@Serializable
data class CreatorProduct(
    val plan_id: String? = null,
    val name: String? = null,
    val pic: String? = null,
    val price: String? = null,
    val show_price: String? = null,
    val floor_price: String? = null,
    val description: String? = null,
) {
    val displayName: String get() = name ?: "商品"
    val priceText: String get() = show_price ?: floor_price ?: price ?: "--"
    val summary: String get() = description.orEmpty()
}
