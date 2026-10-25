package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 开发者中心领域服务（对应官网 /dashboard/dev 页面）。
 *
 * 覆盖页面上的四件事：读取 user_id、读取/保存 Webhook URL、发送 Webhook 测试、生成/读取 API Token。
 * 全部使用 ifdian.net 域名 + Cookie 认证，请求头与 [BlocklistService] 保持一致。
 *
 * 接口契约经 2026-10 浏览器实测 + 前端 chunk（dashboardDev.bbdd1b22.js）核对：
 * - 四个只读/写入接口均返回 { ec, em, data }
 * - 三个写接口的请求体为 JSON：{ "url": "..." }，create-open-token 则不带任何参数
 * - 写接口的提示文案直接取 em 字段（前端组件就是这么用的），这里保持一致
 */
internal class DeveloperService(
    private val tokenStore: AuthTokenStore,
    client: OkHttpClient,
) {
    private val http = AfdianHttpClient(client)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private companion object {
        const val HOST = "https://ifdian.net"
        const val TAG = "DeveloperService"

        /** 开发者页面的 referer，服务端据此判断请求来源 */
        const val REFERER = "$HOST/dashboard/dev"

        val JSON_MEDIA = "application/json".toMediaType()
    }

    /**
     * 读取当前登录用户的 user_id。
     *
     * 开发者页面顶部展示的就是这个值（前端组件直接取 session_user.user_id），
     * 它与 /api/my/account 返回的 user_private_id 不是同一个东西，不要混用。
     */
    suspend fun fetchUserId(): String {
        val request = baseGet("/api/my/profile").build()
        return parse(request, "/api/my/profile", "获取 user_id 失败", logValue = { it }) { root ->
            root["data"]?.jsonObject
                ?.get("user")?.jsonObject
                ?.get("user_id")?.jsonPrimitive?.content
                .orEmpty()
        }
    }

    /** 读取已配置的 Webhook URL；未配置过时服务端返回空串。 */
    suspend fun fetchWebhookUrl(): String {
        val request = baseGet("/api/creator/list-open-webhook").build()
        return parse(request, "/api/creator/list-open-webhook", "读取 Webhook 失败", logValue = { it }) { root ->
            root["data"]?.jsonObject?.get("url")?.jsonPrimitive?.content.orEmpty()
        }
    }

    /** 保存 Webhook URL，返回服务端提示文案（em）。 */
    suspend fun updateWebhookUrl(url: String): String =
        postWithUrl("/api/creator/update-open-webhook", url, "保存失败")

    /** 让服务端向指定 URL 发一条测试通知，返回服务端提示文案（em）。 */
    suspend fun testWebhookUrl(url: String): String =
        postWithUrl("/api/creator/test-open-webhook", url, "发送测试失败")

    /**
     * 生成新的 API Token。
     *
     * 注意：重新生成会让旧的 Token 立即失效，调用方必须先向用户确认。
     * 返回服务端提示文案（em）。
     */
    suspend fun createToken(): String {
        // create-open-token 不带任何业务参数，这里发空 JSON 对象 "{}"（与前端空 data 的 POST 一致）
        val request = basePost("/api/creator/create-open-token", "{}", "生成 Token 失败").build()
        return parse(
            request,
            "/api/creator/create-open-token",
            "生成 Token 失败",
            logValue = { "em=$it" },
        ) { root ->
            root["em"]?.jsonPrimitive?.content.orEmpty()
        }
    }

    /** 读取当前 API Token；未生成过时服务端返回空串。 */
    suspend fun fetchToken(): String {
        val request = baseGet("/api/creator/list-open-tokens").build()
        // Token 是凭据，日志只记长度不记明文
        return parse(
            request,
            "/api/creator/list-open-tokens",
            "读取 Token 失败",
            logValue = { "len=${it.length}" },
        ) { root ->
            root["data"]?.jsonObject?.get("token")?.jsonPrimitive?.content.orEmpty()
        }
    }

    // ==================== 请求构造 ====================

    /** GET 请求：auth_token 走 query + Cookie 双通道（与服务层其它接口一致，服务端两种都认）。 */
    private fun baseGet(path: String): Request.Builder =
        Request.Builder()
            .url("$HOST$path")
            .get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", REFERER)

    /** POST 请求：JSON 请求体 + Cookie 认证。 */
    private fun basePost(path: String, body: String, @Suppress("UNUSED_PARAMETER") fallback: String): Request.Builder =
        Request.Builder()
            .url("$HOST$path")
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", REFERER)

    /** update-open-webhook 与 test-open-webhook 共用请求体（前端同样是 { url }）。 */
    private suspend fun postWithUrl(path: String, url: String, fallbackMessage: String): String {
        val body = buildJsonObject { put("url", url) }.toString()
        val request = basePost(path, body, fallbackMessage).build()
        return parse(request, path, fallbackMessage, logValue = { "em=$it" }) { root ->
            root["em"]?.jsonPrimitive?.content.orEmpty()
        }
    }

    /**
     * 统一执行并解析：校验 HTTP 状态与业务 ec，再把 data 交给 [extract]。
     *
     * 换线策略由请求方法决定（见 `AfdianHttpClient`）：GET 失败即换下一条线路，
     * 写请求只在连接没建立时换，避免服务端已成功但客户端超时后重复提交。
     *
     * @param logValue 日志里要打印的结果摘要。Token 属于凭据，调用方传长度而非明文。
     */
    private inline fun <T> parse(
        request: Request,
        label: String,
        fallbackMessage: String,
        logValue: (T) -> String,
        extract: (JsonObject) -> T,
    ): T {
        val start = System.currentTimeMillis()
        // GET 允许失败换线；写操作只在连接没建立时换线，由 AfdianHttpClient 按方法判定
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$label -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("$fallbackMessage (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, label, fallbackMessage, requireEc = true)
            val result = extract(root)
            AppLog.d(TAG, "$label -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），${logValue(result)}")
            return result
        }
    }
}