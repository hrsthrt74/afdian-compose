package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** 账号领域服务：账号信息、个人资料、支持列表和轻量未读检查。 */
internal class AccountService(
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
        const val AFDIAN_HOST = "https://afdian.com"
        const val TAG = "AccountService"
    }

    /** 账号基础信息：登录邮箱/手机号与微信昵称头像。 */
    suspend fun fetchAccount(): AfadianAccount =
        get("/api/my/account", AfadianAccountResponse.serializer()).data
            ?: error("账号信息加载失败")

    /** 个人资料：当前登录用户的公开主页信息。 */
    suspend fun fetchProfile(): AfadianProfile =
        get("/api/my/profile", AfadianProfileResponse.serializer()).data
            ?: error("个人信息加载失败")

    /** 我支持的创作者列表（含最新动态提示）。 */
    suspend fun fetchSponsoring(): List<SponsoringItem> =
        get("/api/my/sponsoring?need_sponsor_info=1", SponsoringResponse.serializer()).data?.sponsoring.orEmpty()

    /** 轻量未读检查：无参数，返回全部未读分类计数，供底部导航栏使用。 */
    suspend fun fetchMyCheck(): MyCheckData {
        val path = "/api/my/check"
        val url = (AFDIAN_HOST + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        // 埋点：请求发出前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                // 非 2xx 属于异常路径，E 级日志始终输出
                AppLog.e(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("未读检查失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            // 统一业务码校验：ec 缺失或非 200 都视为失败，并优先展示服务端 em
            root.requireAfdianSuccess(TAG, path, "未读检查失败", requireEc = true)
            val data = root["data"]?.jsonObject ?: error("未读检查返回结构异常")
            val countObj = data["unread_count"]?.jsonObject
            MyCheckData(
                unreadMessageNum = data["unread_message_num"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                unreadComment = countObj?.get("comment")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                unreadLike = countObj?.get("like")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                unreadPrivateMsg = countObj?.get("message")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            ).also {
                // 埋点：D 级调试日志，记录各分类未读数
                AppLog.d(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），message=${it.unreadMessageNum} comment=${it.unreadComment} like=${it.unreadLike}")
            }
        }
    }

    /** 账号域共用的 GET 请求：统一拼 auth_token、校验 HTTP 与业务码。 */
    private fun <T> get(
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        host: String = AFDIAN_HOST,
    ): T {
        val url = (host + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        // 埋点：请求发出前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                // 非 2xx 属于异常路径，E 级日志始终输出
                AppLog.e(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("请求失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, "请求失败", requireEc = true)
            json.decodeFromJsonElement(serializer, root).also {
                // 埋点：D 级调试日志，记录请求路径、HTTP 状态码与总耗时（含读取响应体与解析）
                AppLog.d(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            }
        }
    }
}

@Serializable private data class AfadianAccountResponse(val ec: Int? = null, val data: AfadianAccount? = null)
@Serializable private data class AfadianProfileResponse(val ec: Int? = null, val data: AfadianProfile? = null)
@Serializable private data class SponsoringResponse(val ec: Int? = null, val data: SponsoringPayload? = null)
@Serializable private data class SponsoringPayload(val sponsoring: List<SponsoringItem> = emptyList())
