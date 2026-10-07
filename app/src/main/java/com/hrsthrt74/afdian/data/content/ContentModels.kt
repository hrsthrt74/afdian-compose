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
    // 众筹 tab 专属：官方众筹详情（状态/进度/支持人数等），创作者/动态 tab 为 null
    val crowdfunding: FavoriteCrowdfunding? = null,
)

/**
 * 收藏众筹条目的详情，来自 /api/user/marked-crowdfunding 返回的 crowdfunding 对象。
 * 只解析卡片渲染所需的字段，商品列表/富文本详情等不收藏的数据一律忽略。
 */
@Serializable
data class FavoriteCrowdfunding(
    val crowdfunding_id: String = "",
    // 封面图
    val pic: String? = null,
    // 众筹状态：1=众筹即将开始 2=众筹中 3=众筹已结束（与官方前端 tag 判断一致）
    val process: Int = 0,
    // 开始/结束时间戳（秒），用于倒计时
    val begin_time: Long = 0,
    val end_time: Long = 0,
    // 支持人数
    val sponsor_count: Long = 0,
    // 目标列表，官方卡片只展示第一个
    val goal_list: List<FavoriteCrowdfundingGoal> = emptyList(),
)

/** 众筹目标：官方 percent 为「已筹/目标」的倍数（如 55.7 表示 5570%）。 */
@Serializable
data class FavoriteCrowdfundingGoal(
    // 目标金额，如 "30000.00"
    val amount: String = "",
    // 已筹金额，如 "1670918.00"
    val sum_income: String = "",
    // 进度倍数，如 "55.697"；展示时 ×100 保留 1 位小数
    val percent: String = "",
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

/** 方案详情接口返回的计划信息；字段保持字符串金额，避免精度丢失。 */
@Serializable
data class CreatorPlanSkuPlan(
    val plan_id: String = "",
    val user_id: String = "",
    val name: String = "",
    val pic: String? = null,
    val desc: String? = null,
    val price: String? = null,
    val show_price: String? = null,
    val pay_month: Int? = null,
    val product_type: Int? = null,
    val can_buy_count: Int? = null,
    val permanent: Int? = null,
    val need_address: Int? = null,
)

/** /api/creator/get-plan-skus 返回的单个商品 SKU。 */
@Serializable
data class CreatorPlanSku(
    val sku_id: String = "",
    val plan_id: String = "",
    val user_id: String = "",
    val status: Int? = null,
    val name: String = "",
    val stock: String? = null,
    val price: String? = null,
    val album_id: String? = null,
    val post_id: String? = null,
    val pic: String? = null,
    val desc: String? = null,
    val sponsor_count: String? = null,
    val purchased_rich_text: String? = null,
    val show_stock: Int? = null,
    val plan: CreatorPlanSkuPlan? = null,
    val sku_limitation_label_switch: Int? = null,
    val has_sku_purchase_limit: Int? = null,
    val can_buy_count: Int? = null,
)

/** 方案 SKU 查询结果，包含方案信息、可购买 SKU 及已购 SKU。 */
@Serializable
data class CreatorPlanSkus(
    val plan: CreatorPlanSkuPlan? = null,
    val floor_price: String? = null,
    val has_time_limit_price: Int? = null,
    val list: List<CreatorPlanSku> = emptyList(),
    val vip_all_plans: List<CreatorPlanSkuPlan> = emptyList(),
    val bought_skus: List<CreatorPlanSku> = emptyList(),
)

/**
 * 赞助者管理列表项（who-sponsored-me 精简解析）。
 * 只保留展示与详情所需字段；接口里 plan 的巨型 HTML desc 等一律不映射。
 */
data class SponsorManageItem(
    /** 赞助者用户 ID，列表稳定 key */
    val userId: String,
    val name: String,
    val avatar: String?,
    val urlSlug: String?,
    /** 当前生效方案名；接口未返回 current_plan（如无生效计划）时为 null，展示层显示「无」 */
    val currentPlanName: String?,
    /** 累计赞助金额（接口为字符串，只展示不参与计算） */
    val allSumAmount: String,
    /** 最近赞助时间（Unix 秒） */
    val lastPayTime: Long,
    /** 初次赞助时间（Unix 秒） */
    val createTime: Long,
    /** 该赞助者购买过的方案（精简列表，用于详情 Sheet） */
    val plans: List<SponsorPlanBrief>,
) {
    val id: String get() = userId

    /** 转成通用用户模型，用于跳转赞助者主页。 */
    fun toAfadianUser() = AfadianUser(user_id = userId, name = name, avatar = avatar, url_slug = urlSlug)
}

/** 赞助者购买过的单个方案（精简）。 */
data class SponsorPlanBrief(
    val planId: String,
    val name: String,
    val price: String,
    /** 订阅月数；售卖商品通常为 1 */
    val payMonth: Int,
    /** 0=订阅 1=售卖 2=捆绑包 */
    val productType: Int?,
)

/** 赞助者管理分页结果。 */
data class SponsorManagePage(
    val list: List<SponsorManageItem>,
    /** 赞助者总人数 */
    val totalCount: Int,
    /** 总页数 */
    val totalPages: Int,
)

/**
 * 赞助者管理排序（对应网页 dashboard/sponsors 表头点击，抓包验证）。
 *
 * 字段名与网页一致：all_sum_amount / last_pay_time / create_time；
 * 每个字段 asc/desc 成对，另带 Default 档（sort_field/sort_value 传空串，即网页初始默认顺序）。
 * 筛选（filter）暂不实现。
 */
enum class SponsorManageSort(val field: String, val direction: String, val title: String) {
    /** 默认排序：不传排序字段，接口返回原生顺序（与网页初始加载一致） */
    Default("", "", "默认排序"),
    /** 累计金额从高到低（网页首点 all_sum_amount/desc） */
    AmountDesc("all_sum_amount", "desc", "金额从高到低"),
    /** 累计金额从低到高 */
    AmountAsc("all_sum_amount", "asc", "金额从低到高"),
    /** 最近赞助时间：最近在前（网页默认列表即此顺序） */
    RecentDesc("last_pay_time", "desc", "最近赞助在前"),
    /** 最近赞助时间：最早在前（网页首点 last_pay_time/asc） */
    RecentAsc("last_pay_time", "asc", "最早赞助在前"),
    /** 初次赞助时间：新赞助者在前（网页首点 create_time/desc） */
    FirstDesc("create_time", "desc", "新赞助者在前"),
    /** 初次赞助时间：老赞助者在前 */
    FirstAsc("create_time", "asc", "老赞助者在前"),
}

/**
 * 筛选档位 popup 的单个方案选项（来自 GET /api/creator/all-plans，
 * 合并 list + sale_list 去重并排除 0 元方案，与网页「筛选档位」下拉一致）。
 */
data class SponsorFilterPlan(
    val planId: String,
    val name: String,
    /** 展示价格（show_price 优先），字符串原样展示不参与计算 */
    val price: String,
) {
    /** Chip / popup 展示文案：「￥3.99 方案名」 */
    val display: String get() = "￥$price $name"
}

/**
 * 筛选条件类型（对应网页条件构建器的 type 段，抓包验证的四种）。
 */
enum class SponsorFilterType(val title: String, val field: String) {
    /** 正在赞助该方案（当前生效计划） */
    Sponsoring("正在赞助", "sponsoring"),
    /** 历史赞助过该方案 */
    Sponsored("赞助过", "sponsored"),
    /** 累计赞助金额比较 */
    Amount("累计金额", "all_sum_amount"),
    /** 赞助月数比较 */
    Month("赞助月份", "month"),
    ;

    /** 该类型是否按方案取值（false = 数值/月份比较） */
    val isPlanType: Boolean get() = this == Sponsoring || this == Sponsored

    /** 该类型可用的动作集合与顺序（对齐网页下拉） */
    val actions: List<SponsorFilterAction>
        get() = when (this) {
            // 方案型：包含 / 不包含
            Sponsoring, Sponsored -> listOf(SponsorFilterAction.Eq, SponsorFilterAction.Ne)
            // 累计金额：网页顺序 大于等于 / 小于等于 / 不等于 / 大于 / 等于 / 小于
            Amount -> listOf(
                SponsorFilterAction.Ge,
                SponsorFilterAction.Le,
                SponsorFilterAction.Ne,
                SponsorFilterAction.Gt,
                SponsorFilterAction.Eq,
                SponsorFilterAction.Lt,
            )
            // 赞助月份：多月份匹配语义固定为「等于（属于其中之一）」
            Month -> listOf(SponsorFilterAction.Eq)
        }
}

/** 筛选条件动作（符号与网页 filter.action 一致）。 */
enum class SponsorFilterAction(val symbol: String) {
    Eq("="),
    Ne("!="),
    Ge(">="),
    Le("<="),
    Gt(">"),
    Lt("<"),
    ;

    /**
     * 展示标题：方案型「包含/不包含」；数值型用比较符号，
     * 其中 `!=`/`>=`/`<=` 不直观，显示为「≠」「≥」「≤」。
     * 请求体里的 action 值始终用 [symbol]，不受展示文案影响。
     */
    fun titleFor(type: SponsorFilterType): String = when {
        // 数值型（累计金额/赞助月份）：符号紧凑，多字符比较符换成 Unicode 单字形
        !type.isPlanType -> when (this) {
            Ne -> "≠"
            Ge -> "≥"
            Le -> "≤"
            else -> symbol
        }
        this == Eq -> "包含"
        this == Ne -> "不包含"
        else -> symbol
    }
}

/**
 * 一条筛选条件（popup 草稿与已应用状态共用）。
 *
 * 完整性规则与提交口径：
 * - 方案型未选方案（plan = null 即「全部」）不生效；
 * - 累计金额未输入合法数字不生效；
 * - 赞助月份未选任何年月不生效。
 * 不完整的行构建 filter 时跳过，等价于官方的「全部/空值」条件。
 */
data class SponsorFilterRow(
    val type: SponsorFilterType = SponsorFilterType.Sponsoring,
    val action: SponsorFilterAction = SponsorFilterAction.Eq,
    /** 方案型选中的方案；null = 「全部」（不限定方案） */
    val plan: SponsorFilterPlan? = null,
    /** 累计金额输入的数字字符串（原样进 filter.value；仅 Amount 使用） */
    val number: String = "",
    /** 赞助月份选中的年月（YYYYMM 如「202610」，降序；仅 Month 使用） */
    val months: List<String> = emptyList(),
) {
    /** 是否可提交进 filter（见类注释） */
    val isComplete: Boolean
        get() = when {
            type.isPlanType -> plan != null
            type == SponsorFilterType.Month -> months.isNotEmpty()
            else -> number.toBigDecimalOrNull() != null
        }

    /** value 段展示：方案名 / 数字 / 月份摘要 / 「全部」/ 占位提示 */
    val valueLabel: String
        get() = when {
            type.isPlanType -> plan?.name ?: "全部"
            type == SponsorFilterType.Month -> when {
                months.isEmpty() -> "选择月份"
                months.size == 1 -> monthDisplayName(months.first())
                else -> "${monthDisplayName(months.first())} 等${months.size}个"
            }
            number.isNotBlank() -> number
            else -> "输入数值"
        }

    /** 条件摘要（Chip 单条筛选时展示）：「正在赞助 = 宇宙航线」「累计金额 >= 10」 */
    val summary: String
        get() = "${type.title} ${action.titleFor(type)} $valueLabel"
}

/** YYYYMM（如「202610」）→「2026年10月」；非法格式原样返回。 */
fun monthDisplayName(yyyymm: String): String {
    if (yyyymm.length != 6) return yyyymm
    val month = yyyymm.substring(4).toIntOrNull() ?: return yyyymm
    if (month !in 1..12) return yyyymm
    return "${yyyymm.substring(0, 4)}年${month}月"
}

// ==================== 屏蔽 / 拒绝名单 ====================

/** 名单类型：block = 屏蔽（限制评论互动），reject = 拒绝（限制赞助发电）。 */
enum class BlackedType(val wire: String) {
    Block("block"),
    Reject("reject");

    companion object {
        /** 由接口返回的 type 字符串还原枚举；未知值回退为屏蔽。 */
        fun fromWire(wire: String?): BlackedType =
            entries.firstOrNull { it.wire == wire } ?: Block
    }
}

/**
 * 名单条目里的用户资料（接口 user 对象）。
 *
 * 刻意不复用 [AfadianUser]：本接口把非创作者的 creator 字段序列化成空数组 []，
 * 而 AfadianUser.creator 是对象类型，直接解析会抛异常。
 */
@Serializable
data class BlackedUserInfo(
    val user_id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val url_slug: String? = null,
    val cover: String? = null,
    val is_verified: Int? = null,
)

/** 名单条目：谁、在什么时候被屏蔽 / 拒绝。 */
@Serializable
data class BlackedItem(
    val id: Long = 0,
    val remote_id: String = "",
    val type: String = "",
    val create_time: Long = 0,
    val update_time: Long = 0,
    val user: BlackedUserInfo = BlackedUserInfo(),
) {
    /** 条目所属名单类型；未知值回退为屏蔽。 */
    val blackedType: BlackedType get() = BlackedType.fromWire(type)

    /** 映射成通用用户模型，供「打开创作者主页」等既有导航复用。 */
    val asUser: AfadianUser
        get() = AfadianUser(
            user_id = user.user_id.ifBlank { remote_id },
            name = user.name,
            avatar = user.avatar,
            url_slug = user.url_slug,
            cover = user.cover,
            is_verified = user.is_verified,
        )
}

/** 名单分页结果。 */
data class BlackedPage(
    val items: List<BlackedItem>,
    val hasMore: Boolean,
)
