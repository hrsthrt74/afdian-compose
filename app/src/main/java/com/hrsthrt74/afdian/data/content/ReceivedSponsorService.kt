package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
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

/** 创作者收到发电账单领域服务，负责筛选、游标分页和订单扩展解析。 */
internal class ReceivedSponsorService(
    private val tokenStore: AuthTokenStore,
    client: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private val http = AfdianHttpClient(client)

    private companion object {
        const val HOST = "https://afdian.com"
        const val TAG = "ReceivedSponsorService"
    }

    suspend fun fetchReceivedSponsors(
        page: Int = 1,
        lastOrderId: String? = null,
        redeemOnly: Boolean = false,
        remarkOnly: Boolean = false,
        orderStatus: ReceivedOrderStatus = ReceivedOrderStatus.All,
        // 档位筛选：null = 全部档位，"-1" = 自选金额（网页端特殊 plan_id）
        planId: String? = null,
        shipmentStatus: ReceivedShipmentFilter = ReceivedShipmentFilter.All,
        sort: ReceivedSponsorsSort = ReceivedSponsorsSort.Newest,
        beginTime: Long? = null,
        endTime: Long? = null,
    ): ReceivedSponsorsPage {
        val builder = (HOST + "/api/my/sponsored-bill-filter").toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort_field", sort.field)
            .addQueryParameter("sort_value", sort.direction)
        if (redeemOnly) builder.addQueryParameter("is_redeem", "1")
        if (remarkOnly) builder.addQueryParameter("has_remark", "1")
        // 档位：普通方案传 plan_id；自选金额使用网页端约定的 plan_id=-1。
        planId?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("plan_id", it) }
        // 与网页端 post_data 保持一致：
        // - 订单状态走 status（空表示全部）；
        // - 已发货 / 待发货走 express_status；
        // - 「含收件信息」是特例：固定 status=10，此时不发送订单状态（二者互斥）。
        val expressCode = shipmentStatus.expressCode
        if (expressCode == "10") {
            builder.addQueryParameter("status", "10")
        } else {
            orderStatus.filterCode?.let { builder.addQueryParameter("status", it) }
            expressCode?.let { builder.addQueryParameter("express_status", it) }
        }
        // 时间边界允许单独留空，仍显式发送参数以匹配网页端请求格式。
        builder.addQueryParameter("begin_time", beginTime?.toString().orEmpty())
        builder.addQueryParameter("end_time", endTime?.toString().orEmpty())
        lastOrderId?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("last_order_id", it) }

        val path = "/api/my/sponsored-bill-filter?page=$page"
        val start = System.currentTimeMillis()
        val request = Request.Builder()
            .url(builder.addQueryParameter("auth_token", tokenStore.token).build())
            .get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("收到发电加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, "收到发电加载失败", requireEc = true)
            val data = root["data"]?.jsonObject ?: error("收到发电返回结构异常")
            val orders = data["list"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toReceivedSponsor() }
            ReceivedSponsorsPage(
                orders = orders,
                hasMore = data.long("has_more") == 1L,
                lastOrderId = data.string("last_order_id"),
            ).also {
                AppLog.d(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${it.orders.size} 条，has_more=${it.hasMore}")
            }
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        string(key)?.toLongOrNull() ?: string(key)?.toDoubleOrNull()?.toLong()

    private fun JsonObject.toReceivedSponsor(): ReceivedSponsor? {
        // 2 = 已支付；5 / 7 是网页端订单状态里的冻结 / 退款。
        // 待支付（1）等其它状态仍不进入列表。
        val orderStatusValue = long("status")
        if (orderStatusValue != null && orderStatusValue !in setOf(2L, 5L, 7L)) return null
        val orderNo = string("out_trade_no")?.takeIf { it.isNotBlank() } ?: return null
        val user = this["user"] as? JsonObject
        val plan = this["plan"] as? JsonObject
        val timeRange = this["time_range"] as? JsonObject
        val createTime = long("create_time") ?: long("update_time") ?: return null
        val skuName = skuName()
        val ext = this["ext"] as? JsonObject
        val bundleCount = ext?.string("bundle_count")?.toIntOrNull() ?: 0
        val bundlePriceDiff = ext?.string("bundle_price_diff")
            ?.takeIf { bundleCount > 0 && it.toBigDecimalOrNull()?.signum() == 1 }
        val redeemCode = ext?.string("redeem")?.takeIf { it.isNotBlank() }
        val affiliateCode = ext?.string("affiliate_code")?.takeIf { it.isNotBlank() }
        val affiliateReduction = ext?.string("affiliate_reduction")
            ?.takeIf { it.toBigDecimalOrNull()?.signum() == 1 }
        // 网页端新增的结算状态：state 决定「成功 / 冻结 / 退款」，refund_amount 用于退款金额
        val settlement = this["settlement_status"] as? JsonObject
        val settlementState = ReceivedOrderStatus.fromWire(settlement?.string("state"))
        val refundAmount = settlement?.string("refund_amount")?.takeIf { it.isNotBlank() }
        return ReceivedSponsor(
            orderNo = orderNo,
            userId = string("user_id") ?: user?.string("user_id").orEmpty(),
            userName = user?.string("name")?.ifBlank { null } ?: "匿名用户",
            avatarUrl = user?.string("avatar"),
            paidAtSeconds = createTime,
            planName = plan?.string("name")?.ifBlank { null }
                ?: string("title")?.ifBlank { null }
                ?: "发电方案",
            modelName = skuName ?: plan?.string("name")?.ifBlank { null } ?: "常规方案",
            amount = if (affiliateReduction != null) {
                string("total_amount").orEmpty()
            } else {
                string("show_amount")?.ifBlank { null } ?: string("total_amount").orEmpty()
            },
            remark = string("remark")?.takeIf { it.isNotBlank() },
            monthCount = long("month")?.toInt() ?: 0,
            totalAmountWithShipping = string("total_amount_g") ?: string("total_amount").orEmpty(),
            freight = string("freight") ?: string("shipping_fee") ?: "0.00",
            validFromSeconds = timeRange?.long("begin_time"),
            validUntilSeconds = timeRange?.long("end_time"),
            bundlePriceDiff = bundlePriceDiff,
            receiverInfo = listOfNotNull(
                string("address_person")?.takeIf { it.isNotBlank() },
                string("address_phone")?.takeIf { it.isNotBlank() },
                string("address_address")?.takeIf { it.isNotBlank() },
            ).takeIf { it.isNotEmpty() }?.joinToString(" "),
            expressNo = string("express_no")?.takeIf { it.isNotBlank() },
            orderRemark = string("order_remark")?.takeIf { it.isNotBlank() },
            redeemCode = redeemCode,
            affiliateReduction = affiliateReduction,
            affiliateCode = affiliateCode,
            orderStatus = settlementState,
            refundAmount = refundAmount,
        )
    }

    /** 兼容商品订单的数组 sku_detail 与网页接口返回的 JSON 字符串。 */
    private fun JsonObject.skuName(): String? {
        val value = this["sku_detail"] ?: return null
        val array = when (value) {
            is JsonArray -> value
            is JsonPrimitive -> value.contentOrNull?.let {
                runCatching { json.parseToJsonElement(it) as? JsonArray }.getOrNull()
            }
            else -> null
        } ?: return null
        return (array.firstOrNull() as? JsonObject)?.string("name")?.takeIf { it.isNotBlank() }
    }
}
