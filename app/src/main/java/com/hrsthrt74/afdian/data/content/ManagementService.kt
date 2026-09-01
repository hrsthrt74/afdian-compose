package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
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
                "提现实际到手" to data.text("balance_after_tax"),
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
        }
    }

    private fun get(path: String): JsonObject {
        // 请求前仅记录接口路径，不输出 auth_token 等凭据信息
        AppLog.d("ManagementService", "开始请求管理接口 $path")
        val url = ("https://afdian.com" + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token).build()
        val request = Request.Builder().url(url).header("Cookie", "auth_token=${tokenStore.token}").build()
        // 记录起始时间，拿到响应后计算差值得到耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            // 非 2xx 走原有的 error() 错误处理，这里补一条 w 级告警便于定位
            if (!response.isSuccessful) {
                AppLog.w("ManagementService", "管理接口返回非 2xx $path code=${response.code}")
                error("请求失败 (${response.code})")
            }
            val result = json.parseToJsonElement(response.body.string()).jsonObject
            // 成功拿到响应后输出 HTTP 状态码与整体耗时
            AppLog.d("ManagementService", "管理接口响应 $path code=${response.code} cost=${System.currentTimeMillis() - start}ms")
            result
        }
    }
}

enum class ManagementSection(val title: String) {
    Overview("整体概况"), Income("收入统计"), Received("收到发电"), Sponsors("赞助者管理")
}

data class ManagementResult(val section: ManagementSection, val rows: List<Pair<String, String>>)

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: "--"
private fun JsonObject.objectText(parent: String, key: String): String = this[parent]?.jsonObject?.text(key) ?: "--"
private fun JsonObject.arraySize(key: String): Int = this[key]?.jsonArray?.size ?: 0
private fun JsonObject.arrayTexts(key: String): List<String> = this[key]?.jsonArray?.map { it.toString() } ?: emptyList()
