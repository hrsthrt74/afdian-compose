package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 「我的订单」领域服务：拉取当前账号在别人家买的东西（/api/my/sponsored-bill-out-filter）。
 *
 * 接口语义（2026-10 抓包验证，与网页端 /dashboard/order 完全一致）：
 * - status 参数必填，不传时接口返回空列表；网页只有四个筛选：2=已支付、1=待支付、8=待发货、9=已发货
 * - 订单体的 status 字段永远是支付状态数字（1=待支付、2=已支付），
 *   「待发货/已发货」由 order_attr（物流信息）是否存在区分，不能直接读 status 判断
 */
class OrderService(private val tokenStore: AuthTokenStore, private val client: OkHttpClient = sharedAfdianHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = AfdianHttpClient(client)

    fun load(status: String, page: Int = 1): OrderPage {
        // 请求前记录本次查询参数（status/page 不含凭据，可安全输出）
        AppLog.d("OrderService", "开始加载订单 status=$status page=$page")
        val url = "https://afdian.com/api/my/sponsored-bill-out-filter".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("page", page.toString())
            // 以下三个参数照抄网页端请求，缺了 is_redeem 有些账号会返回空列表
            .addQueryParameter("sort_field", "update_time")
            .addQueryParameter("sort_value", "desc")
            .addQueryParameter("is_redeem", "0")
            .addQueryParameter("plan_id", "")
            .addQueryParameter("sign_status", "")
            // status 必填：网页端不提供「全部」筛选，这里也必须始终带一个具体状态
            .addQueryParameter("status", status)
            .build()
        val request = Request.Builder().url(url).header("Cookie", "auth_token=${tokenStore.token}").build()
        // 记录起始时间，拿到响应后计算差值得到耗时
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) error("订单加载失败 (${response.code})")
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess("OrderService", "/api/my/sponsored-bill-out-filter", "订单加载失败", requireEc = true)
            val data = root["data"]?.jsonObject ?: JsonObject(emptyMap())
            val result = OrderPage(
                orders = data["list"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.toOrder() },
                hasMore = data.long("has_more") == 1L,
                // total_count 是当前筛选下的订单总数；sponsored_count 语义不明且恒为小值，不用它做摘要
                totalCount = data.long("total_count")?.toInt() ?: 0,
            )
            // 拿到响应并解析完成后，输出 HTTP 状态码、订单条数与整体耗时
            AppLog.d("OrderService", "订单加载完成 code=${response.code} count=${result.orders.size} hasMore=${result.hasMore} cost=${System.currentTimeMillis() - start}ms")
            result
        }
    }

    // ===== 安全取值 helper：接口字段类型不稳（数字/字符串混用），统一先判型再取 =====

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        string(key)?.toLongOrNull() ?: string(key)?.toDoubleOrNull()?.toLong()

    /**
     * 把接口返回的一条订单 JSON 转成本地展示模型。
     * 字段缺失/类型不对时降级为默认值而不是抛异常，避免一条脏数据吞掉整页订单。
     */
    private fun JsonObject.toOrder(): OrderItem? {
        // 订单号用 out_trade_no（网页端展示的「订单号」）；id 是内部数字主键，对外无意义
        val orderNo = string("out_trade_no")?.takeIf { it.isNotBlank() } ?: return null
        val plan = this["plan"] as? JsonObject
        val user = this["user"] as? JsonObject
        val ext = this["ext"] as? JsonObject
        val orderAttr = this["order_attr"] as? JsonObject
        val address = ext?.get("address") as? JsonObject
        // 支付时间：优先更新时间（支付/发货都会刷新），没有再用创建时间
        // 直接透传 Unix 秒，日期格式化交给 UI 层的 PostHeader（与动态卡片同一套 M/d 规则）
        val timeSeconds = long("update_time") ?: long("create_time")
        val expressNo = orderAttr?.string("express_no")?.trim()?.takeIf { it.isNotBlank() }
        val expressCompany = orderAttr?.string("company_name")?.trim()?.takeIf { it.isNotBlank() }
        // 兑换码：通过创作者兑换链接产生的订单才有（ext.redeem 与顶层 redeem_id 同值）
        val redeemCode = ext?.string("redeem")?.takeIf { it.isNotBlank() }
            ?: string("redeem_id")?.takeIf { it.isNotBlank() }
        // 期限：只有纯赞助类订单（product_type=0）才有「期限：N个月」的展示；
        // 商品订单的「永久」「3个月有效」属于 SKU 规格名，由 skuName 行负责显示
        val duration = if (long("product_type") == 0L) {
            val months = long("month") ?: 0
            if (months > 0) "${months}个月" else "永久"
        } else {
            null
        }
        // 方案/商品封面：优先 SKU 自带图，没有再用方案图（商品卡片展示用）
        val planPic = (skuArray()?.firstOrNull() as? JsonObject)
            ?.string("pic")?.takeIf { it.isNotBlank() }
            ?: plan?.string("pic")?.takeIf { it.isNotBlank() }
        return OrderItem(
            id = orderNo,
            planPic = planPic,
            // 方案 id 与创作者标识：点商品进方案详情、点头像进创作者主页用
            planId = string("plan_id")?.takeIf { it.isNotBlank() },
            userId = user?.string("user_id")?.takeIf { it.isNotBlank() }
                ?: string("remote_id")?.takeIf { it.isNotBlank() },
            userSlug = user?.string("url_slug")?.takeIf { it.isNotBlank() },
            redeemCode = redeemCode,
            duration = duration,
            title = plan?.string("name")?.takeIf { it.isNotBlank() }
                ?: string("title")?.takeIf { it.isNotBlank() }
                ?: "发电订单",
            // SKU 规格名：可能没有（纯赞助/捐赠订单），与方案名相同也不再重复显示
            skuName = skuName(),
            skuPrice = skuPrice(),
            skuCount = long("sku_count")?.toInt() ?: 0,
            creator = user?.string("name")?.takeIf { it.isNotBlank() } ?: "爱发电创作者",
            // 创作者头像：卡片头部用
            avatarUrl = user?.string("avatar")?.takeIf { it.isNotBlank() },
            // 实付金额用 total_amount：兑换码订单的 show_amount 是商品原价，不是实付
            amount = string("total_amount")?.takeIf { it.isNotBlank() }
                ?: string("show_amount")
                ?: "--",
            status = statusLabel(long("status"), expressNo),
            timeSeconds = timeSeconds,
            remark = string("remark")?.takeIf { it.isNotBlank() },
            // 收货地址：把「收件人 电话 详细地址」拼成一行，任何一段缺失都跳过
            address = listOfNotNull(
                address?.string("name")?.takeIf { it.isNotBlank() },
                address?.string("phone")?.takeIf { it.isNotBlank() },
                address?.string("address")?.takeIf { it.isNotBlank() }
                    ?: address?.string("exact_address")?.takeIf { it.isNotBlank() },
            ).takeIf { it.isNotEmpty() }?.joinToString(" "),
            expressCompany = expressCompany,
            expressNo = expressNo,
        )
    }

    /** 状态文案：status 是支付状态（1=待支付、2=已支付），有物流单号则视为已发货 */
    private fun statusLabel(status: Long?, expressNo: String?): String = when {
        status == 1L -> "待支付"
        status == 8L -> "待发货"
        status == 9L -> "已发货"
        expressNo != null -> "已发货"
        else -> "已支付"
    }

    /**
     * 兼容商品订单的两种 sku_detail 形态：本地数组或网页接口返回的 JSON 字符串。
     * 返回首个 SKU 的名称；纯赞助订单没有 SKU，返回 null。
     */
    private fun JsonObject.skuName(): String? = skuArray()?.firstOrNull()
        ?.let { (it as? JsonObject)?.string("name")?.takeIf { name -> name.isNotBlank() } }

    /** 首个 SKU 的单价（网页端显示为「￥xx.xx * n」里的单价） */
    private fun JsonObject.skuPrice(): String? = skuArray()?.firstOrNull()
        ?.let { (it as? JsonObject)?.string("price")?.takeIf { price -> price.isNotBlank() } }

    private fun JsonObject.skuArray(): JsonArray? {
        val value = this["sku_detail"] ?: return null
        return when (value) {
            is JsonArray -> value
            // 字符串形态：先按 JSON 解析再取数组，解析失败按无 SKU 处理
            is JsonPrimitive -> value.contentOrNull?.let {
                runCatching { json.parseToJsonElement(it) as? JsonArray }.getOrNull()
            }
            else -> null
        }
    }
}

data class OrderPage(val orders: List<OrderItem>, val hasMore: Boolean, val totalCount: Int)

/**
 * 订单条目展示模型。
 * @param id 订单号（out_trade_no）
 * @param planPic 方案/商品封面图，可能为 null
 * @param planId 方案 id，打开方案详情用，纯赞助订单可能为 null
 * @param userId 创作者 user_id，跳转主页用
 * @param userSlug 创作者主页 slug，跳转主页用
 * @param redeemCode 兑换码（不含链接前缀），非兑换订单为 null
 * @param duration 期限文案（纯赞助订单「N个月」/「永久」），商品订单为 null
 * @param title 方案/商品名
 * @param skuName SKU 规格名，纯赞助订单为 null
 * @param skuPrice SKU 单价，无 SKU 时为 null
 * @param skuCount 购买份数
 * @param creator 创作者昵称
 * @param avatarUrl 创作者头像，可能为 null
 * @param amount 实付金额
 * @param status 状态文案（已支付/待支付/待发货/已发货）
 * @param timeSeconds 支付时间（Unix 秒），UI 层负责格式化
 * @param remark 买家留言，无留言为 null
 * @param address 收货地址（收件人 电话 地址），非实物订单为 null
 * @param expressCompany 快递公司，未发货为 null
 * @param expressNo 快递单号，未发货为 null
 */
data class OrderItem(
    val id: String,
    val planPic: String?,
    val planId: String?,
    val userId: String?,
    val userSlug: String?,
    val redeemCode: String?,
    val duration: String?,
    val title: String,
    val skuName: String?,
    val skuPrice: String?,
    val skuCount: Int,
    val creator: String,
    val avatarUrl: String?,
    val amount: String,
    val status: String,
    val timeSeconds: Long?,
    val remark: String?,
    val address: String?,
    val expressCompany: String?,
    val expressNo: String?,
)
