package com.hrsthrt74.afdian.data.probe

/**
 * 单个接口探针的定义。
 *
 * 这里只描述「怎么请求」与「摘要看哪些字段」，不含任何执行逻辑，
 * 因此可以当作静态清单使用（UI 直接用 [id] 作列表稳定 key）。
 *
 * @param id 稳定唯一键，用作列表 key 与状态索引，改动会让状态丢失
 * @param title 卡片标题（接口的中文语义名）
 * @param path 接口路径，含 query，例如 `/api/my/stat?page=1&type=day`
 * @param summary 一行说明「这个接口返回什么」，未运行时展示给用户
 * @param highlights 摘要要突出的字段路径列表。
 *   支持 `a.b` 与 `list[0].c` 两种写法；解析时先按响应根试，失败再把根当信封取 `data` 重试，
 *   所以 `summary.month_amount` 与 `data.` 开头的路径都能命中。
 *   留空表示不指定，由 [com.hrsthrt74.afdian.data.probe.buildHighlights] 自动取叶子字段前几条兜底。
 */
data class ApiProbeSpec(
    val id: String,
    val title: String,
    val path: String,
    val summary: String,
    val highlights: List<String> = emptyList(),
)

/**
 * 首版「无参核心接口」探针清单：全部是 GET，且只需 `auth_token` 即可调用。
 *
 * 刻意的取舍：
 * - 分页接口一律把 `page=1` 写死，只取**单页快照**。这里不走各 Repository 的翻页聚合
 *   （`/api/my/sponsored-bill-filter` 会翻几十页、`/api/my/stat` 会翻到 100 页上限），
 *   否则探针会长时间卡住，失去「快速核对接口返回值」的意义。
 * - 需要 `user_id` / `plan_id` / `post_id` 等参数的接口不收录，留待后续版本支持参数输入。
 */
object ApiProbeCatalog {

    /** 全部探针。顺序即页面上的展示顺序。 */
    val all: List<ApiProbeSpec> = listOf(
        ApiProbeSpec(
            id = "my_dashboard",
            title = "创作者看板",
            path = "/api/my/dashboard",
            summary = "历史/本月收入、发电人数、访问量、可提现余额、我的赞助",
            // 数据卡里的「本月收入」就来自 summary.month_amount，这里原样暴露出来便于核对。
            // 刻意不暴露：bill_amount / sum_bill_amount（与 confirmed / all_sum 恒等，冗余）、
            // transfer_fee_detail（提现手续费，仅提现场景有意义）、has_rn（技术标志位）、
            // monthly_bill 明细（已有专门的「月度账单」探针覆盖）。
            highlights = listOf(
                // —— 收入概览 ——
                "summary.month_amount",          // 本月收入
                "summary.next_month_amount",     // 下月待结算
                "summary.all_sum_amount",        // 累计收入
                "summary.confirmed_amount",      // 已确认金额
                "summary.unconfirmed_amount",    // 未确认金额
                // —— 发电与流量 ——
                "summary.all_sponsor_count",     // 累计发电人数
                "summary.month_sponsor_count",   // 本月发电人数
                "summary.uv",                    // 访问人数
                "summary.pv",                    // 访问次数
                // —— 可提现余额 ——
                "balance",                       // 可提现余额
                "balance_after_tax",             // 税后余额
                // —— 赞助规模 ——
                "sponsored_history_total_count", // 赞助记录总数（响应只带单页，此为全量计数）
                "sponsoring",                    // 我在赞助的创作者（数组，展示 [N 项]）
                "sponsoring_expired",            // 已过期的赞助（数组，展示 [N 项]）
            ),
        ),
        ApiProbeSpec(
            id = "my_stat_day",
            title = "按日统计（单页）",
            path = "/api/my/stat?page=1&type=day",
            summary = "按日的收入 / 发电次数 / 访问人数（只取第 1 页）",
            highlights = listOf(
                "list[0].date_str",
                "list[0].paid_order_real_amount",
                "list[0].paid_order_count",
                "list[0].uv",
                "list[0].pv",
                "has_more",
            ),
        ),
        ApiProbeSpec(
            id = "my_income",
            title = "月度账单",
            path = "/api/my/income",
            summary = "按月的抽成前 / 抽成后金额与发电人数",
            highlights = listOf(
                "monthly_bill[0].month",
                "monthly_bill[0].creator_amount",
                "monthly_bill[0].total_amount",
            ),
        ),
        ApiProbeSpec(
            id = "my_account",
            title = "账号信息",
            path = "/api/my/account",
            summary = "登录邮箱 / 手机号与微信资料",
        ),
        ApiProbeSpec(
            id = "my_profile",
            title = "个人资料",
            path = "/api/my/profile",
            summary = "当前登录用户的公开主页信息",
        ),
        ApiProbeSpec(
            id = "my_sponsoring",
            title = "我在赞助",
            path = "/api/my/sponsoring?need_sponsor_info=1",
            summary = "我支持的创作者列表",
        ),
        ApiProbeSpec(
            id = "my_check",
            title = "未读检查",
            path = "/api/my/check",
            summary = "消息 / 评论 / 点赞 / 私信未读数",
            highlights = listOf(
                "unread_message_num",
                "unread_count.comment",
                "unread_count.like",
                "unread_count.message",
            ),
        ),
        ApiProbeSpec(
            id = "my_marked",
            title = "收藏的创作者",
            path = "/api/my/marked",
            summary = "收藏夹里的创作者列表",
        ),
        ApiProbeSpec(
            id = "my_marked_post",
            title = "收藏的动态",
            path = "/api/my/marked-post?page=1",
            summary = "收藏的动态（只取第 1 页）",
        ),
        ApiProbeSpec(
            id = "user_marked_crowdfunding",
            title = "收藏的众筹",
            path = "/api/user/marked-crowdfunding",
            summary = "收藏的众筹项目",
        ),
        ApiProbeSpec(
            id = "post_rec_list",
            title = "推荐动态",
            path = "/api/post/get-rec-list?publish_sn=&type=old&all=1",
            summary = "推荐信息流（单页）",
        ),
        ApiProbeSpec(
            id = "message_dialogs",
            title = "消息会话",
            path = "/api/message/dialogs?page=1&unread=0",
            summary = "私信会话列表（只取第 1 页）",
        ),
        ApiProbeSpec(
            id = "creator_affiliate",
            title = "优惠码列表",
            path = "/api/creator/list-affiliate",
            summary = "创作者后台的优惠码",
        ),
        ApiProbeSpec(
            id = "creator_all_plans",
            title = "全部方案",
            path = "/api/creator/all-plans?post_only=&sale_only=&status=",
            summary = "订阅 / 售卖方案全量",
        ),
    )
}

/**
 * 一条摘要：字段路径 + 展示值。
 *
 * [value] 为 null 表示**字段在该响应里不存在**（UI 显示灰色「（缺失）」）；
 * 字段存在但值是 JSON `null` 时，[value] 是字符串 `"null"`，两者可区分。
 */
data class Highlight(val path: String, val value: String?)

/** 失败归类，决定卡片徽章文案与给用户的解释。 */
enum class FailureKind(val label: String) {
    /** 本地没有 auth_token，请求根本没发出去 */
    NotLoggedIn("未登录"),

    /** HTTP 状态非 2xx */
    Http("HTTP 异常"),

    /** HTTP 通了但业务码 ec != 200 */
    Business("业务失败"),

    /** 响应体不是合法 JSON */
    Parse("解析失败"),

    /** 建立连接或读取响应时抛异常（断网、超时等） */
    Network("网络异常"),
}

/**
 * 单个探针的一次运行结果。
 *
 * 显式区分「未运行 / 请求中 / 成功 / 失败」四态，避免用一个 Boolean 含糊表达，
 * 也不需要额外的 loading 标志位。
 */
sealed interface ApiProbeResult {

    /** 尚未运行 */
    data object Idle : ApiProbeResult

    /** 请求进行中 */
    data object Running : ApiProbeResult

    /** 成功拿到响应（HTTP 2xx 且业务码为 200，或旧接口没有 ec） */
    data class Success(
        /** HTTP 状态码 */
        val httpCode: Int,
        /** 业务码 ec；旧接口可能不返回，此时为 null */
        val businessCode: Int?,
        /** 业务码对应的错误描述 em / msg / message */
        val businessMessage: String?,
        /** 总耗时（毫秒，含读取响应体） */
        val elapsedMs: Long,
        /** 响应体字节数 */
        val bodyBytes: Long,
        /** 实际命中的域名，便于确认是否走了 ifdian.net -> afdian.com 回退 */
        val resolvedHost: String,
        /** 展示用请求 URL，auth_token 已脱敏为 *** */
        val requestUrl: String,
        /** 已 pretty-print 并按上限截断的原始 JSON */
        val rawJson: String,
        /** 原始 JSON 是否被截断 */
        val rawTruncated: Boolean,
        /** 截断前的原始字符数 */
        val rawBytes: Long,
        /** 关键字段摘要 */
        val highlights: List<Highlight>,
    ) : ApiProbeResult

    /** 失败。失败时也可能拿到响应体（例如 ec != 200），尽量附上便于排查。 */
    data class Failure(
        val kind: FailureKind,
        val message: String,
        val httpCode: Int? = null,
        val businessCode: Int? = null,
        val elapsedMs: Long = 0,
        val resolvedHost: String? = null,
        val requestUrl: String? = null,
        val rawJson: String? = null,
        val rawTruncated: Boolean = false,
        val rawBytes: Long = 0,
        val highlights: List<Highlight> = emptyList(),
    ) : ApiProbeResult
}
