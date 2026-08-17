package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class OrderService(private val tokenStore: AuthTokenStore, private val client: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(status: String, page: Int = 1): OrderPage {
        val url = "https://afdian.com/api/my/sponsored-bill-out-filter".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort_field", "update_time")
            .addQueryParameter("sort_value", "desc")
            .addQueryParameter("is_redeem", "0")
            .addQueryParameter("status", status)
            .build()
        val request = Request.Builder().url(url).header("Cookie", "auth_token=${tokenStore.token}").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("订单加载失败 (${response.code})")
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val data = root["data"]?.jsonObject ?: JsonObject(emptyMap())
            OrderPage(
                orders = data["list"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toOrder() },
                hasMore = data["has_more"]?.jsonPrimitive?.contentOrNull == "1",
                total = data["sponsored_count"]?.jsonPrimitive?.contentOrNull ?: "--",
                amount = data["payment_count"]?.jsonPrimitive?.contentOrNull ?: "--",
            )
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
