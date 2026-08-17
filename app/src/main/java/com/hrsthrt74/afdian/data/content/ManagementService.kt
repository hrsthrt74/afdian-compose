package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class ManagementService(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(section: ManagementSection): ManagementResult {
        val payload = when (section) {
            ManagementSection.Overview -> get("/api/my/dashboard")
            ManagementSection.Income -> get("/api/my/stat?page=1&type=day")
            ManagementSection.Received -> get("/api/my/sponsored-bill-filter?page=1&sort_field=update_time&sort_value=desc")
            ManagementSection.Sponsors -> get("/api/my/who-sponsored-me")
            ManagementSection.Monthly -> get("/api/my/income")
        }
        val data = payload["data"]?.jsonObject ?: JsonObject(emptyMap())
        return when (section) {
            ManagementSection.Overview -> ManagementResult(section, listOf(
                "本月收入" to data.objectText("summary", "month_amount"),
                "累计收入" to data.objectText("summary", "all_sum_amount"),
                "近 31 天赞助者" to data.objectText("summary", "month_sponsor_count"),
                "历史赞助者" to data.objectText("summary", "all_sponsor_count"),
                // 访客 UV
                "主页访问人数" to data.objectText("summary", "uv"),
                // 访客 PV
                "主页访问次数" to data.objectText("summary", "pv"),
                "当前可提现" to data.text("balance"),
                "当前可提现（税后）" to data.text("balance_after_tax"),
            ))
            ManagementSection.Income -> ManagementResult(section, data.arrayTexts("list").take(30).mapIndexed { index, value -> "第 ${index + 1} 条" to value })
            ManagementSection.Received -> ManagementResult(section, listOf(
                "收入订单" to data.arraySize("list").toString(),
                "赞助人数" to data.text("sponsored_count"),
                "支付笔数" to data.text("payment_count"),
            ))
            ManagementSection.Sponsors -> ManagementResult(section, listOf(
                "赞助者总数" to data.text("total_count"),
                "总页数" to data.text("total_page"),
                "当前列表" to data.arraySize("list").toString(),
            ))
            ManagementSection.Monthly -> ManagementResult(section, listOf(
                "年度账单" to data.arraySize("monthly_bill").toString(),
                "数据状态" to if (data.arraySize("monthly_bill") > 0) "已加载" else "暂无数据",
            ))
        }
    }

    private fun get(path: String): JsonObject {
        val url = ("https://afdian.com" + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token).build()
        val request = Request.Builder().url(url).header("Cookie", "auth_token=${tokenStore.token}").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("请求失败 (${response.code})")
            json.parseToJsonElement(response.body.string()).jsonObject
        }
    }
}

enum class ManagementSection(val title: String) {
    Overview("整体概况"), Income("收入统计"), Received("收到发电"), Sponsors("赞助者管理"), Monthly("每月汇总")
}

data class ManagementResult(val section: ManagementSection, val rows: List<Pair<String, String>>)

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: "--"
private fun JsonObject.objectText(parent: String, key: String): String = this[parent]?.jsonObject?.text(key) ?: "--"
private fun JsonObject.arraySize(key: String): Int = this[key]?.jsonArray?.size ?: 0
private fun JsonObject.arrayTexts(key: String): List<String> = this[key]?.jsonArray?.map { it.toString() } ?: emptyList()
