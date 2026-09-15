package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class OrderService(private val tokenStore: AuthTokenStore, private val client: OkHttpClient = sharedAfdianHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = AfdianHttpClient(client)

    fun load(status: String, page: Int = 1): OrderPage {
        // 请求前记录本次查询参数（status/page 不含凭据，可安全输出）
        AppLog.d("OrderService", "开始加载订单 status=$status page=$page")
        val url = "https://afdian.com/api/my/sponsored-bill-out-filter".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort_field", "update_time")
            .addQueryParameter("sort_value", "desc")
            .addQueryParameter("is_redeem", "0")
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
                orders = data["list"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toOrder() },
                hasMore = data["has_more"]?.jsonPrimitive?.contentOrNull == "1",
                total = data["sponsored_count"]?.jsonPrimitive?.contentOrNull ?: "--",
                amount = data["payment_count"]?.jsonPrimitive?.contentOrNull ?: "--",
            )
            // 拿到响应并解析完成后，输出 HTTP 状态码、订单条数与整体耗时
            AppLog.d("OrderService", "订单加载完成 code=${response.code} count=${result.orders.size} cost=${System.currentTimeMillis() - start}ms")
            result
        }
    }
}

data class OrderPage(val orders: List<OrderItem>, val hasMore: Boolean, val total: String, val amount: String)
data class OrderItem(val id: String, val title: String, val creator: String, val amount: String, val status: String, val time: String)

private fun JsonObject.toOrder(): OrderItem? {
    val id = this["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val plan = this["plan"]?.jsonObject
    val user = this["user"]?.jsonObject
    val statusCode = this["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return OrderItem(
        id = id,
        title = plan?.get("name")?.jsonPrimitive?.contentOrNull ?: this["title"]?.jsonPrimitive?.contentOrNull ?: "发电订单",
        creator = user?.get("name")?.jsonPrimitive?.contentOrNull ?: "爱发电创作者",
        amount = this["show_amount"]?.jsonPrimitive?.contentOrNull ?: this["total_amount"]?.jsonPrimitive?.contentOrNull ?: "--",
        status = when (statusCode) { "1" -> "待支付"; "2" -> "已支付"; "8" -> "待发货"; "9" -> "已收货"; else -> "状态未知" },
        time = this["update_time"]?.jsonPrimitive?.contentOrNull ?: this["create_time"]?.jsonPrimitive?.contentOrNull ?: "--",
    )
}
