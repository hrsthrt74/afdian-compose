package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 屏蔽 / 拒绝名单领域服务（对应官网 /setting/blocklist 与 /setting/rejectlist 两页）。
 *
 * 查询走 GET /api/my/blacked（type 参数区分名单），加入走 POST /api/user/black，
 * 解除走 POST /api/user/unblack；全部使用 ifdian.net 域名 + Cookie 认证。
 * 接口契约经 2026-10 浏览器实测：POST 请求体为 form-urlencoded（JSON 编码未经验证，勿随意替换）。
 */
internal class BlocklistService(
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
        const val TAG = "BlocklistService"
    }

    /** 名单类型对应的官网设置页地址，作为请求 referer。 */
    private fun refererOf(type: BlackedType): String = when (type) {
        BlackedType.Block -> "$HOST/setting/blocklist"
        BlackedType.Reject -> "$HOST/setting/rejectlist"
    }

    /** 分页拉取名单；page 从 1 开始，返回项与 has_more 标记。 */
    suspend fun fetchBlacked(type: BlackedType, page: Int): BlackedPage {
        // 与其他服务一致：auth_token 走 query + Cookie 双通道，服务端两种都认
        val url = (HOST + "/api/my/blacked").toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("type", type.wire)
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", refererOf(type))
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "my/blacked type=${type.wire} page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("名单加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, "my/blacked type=${type.wire} page=$page", "名单加载失败", requireEc = true)
            val data = root["data"]?.jsonObject
            // 逐条解析，个别条目字段异常时跳过而不是整页报错
            val items = data?.get("list")?.jsonArray.orEmpty().mapNotNull { element ->
                runCatching { json.decodeFromString(BlackedItem.serializer(), element.toString()) }.getOrNull()
            }
            // has_more 服务端返回 0/1，按字符串兼容解析（与 ec 的处理一致）
            val hasMore = data?.get("has_more")?.jsonPrimitive?.contentOrNull?.toIntOrNull() == 1
            BlackedPage(items, hasMore).also {
                AppLog.d(TAG, "my/blacked type=${type.wire} page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${it.items.size} 条")
            }
        }
    }

    /** 加入屏蔽 / 拒绝（POST /api/user/black），返回服务端提示文案（如「已加入屏蔽名单」）。 */
    suspend fun black(userId: String, type: BlackedType): String =
        mutateBlack("/api/user/black", userId, type, "加入失败")

    /** 解除屏蔽 / 拒绝（POST /api/user/unblack），返回服务端提示文案（如「已从屏蔽名单中移除」）。 */
    suspend fun unblack(userId: String, type: BlackedType): String =
        mutateBlack("/api/user/unblack", userId, type, "解除失败")

    /** black / unblack 共用请求体：form-urlencoded（浏览器实测契约），路径与兜底文案不同。 */
    private suspend fun mutateBlack(path: String, userId: String, type: BlackedType, fallbackMessage: String): String {
        // 网页端同款表单编码；OkHttp FormBody 会自动做 URL 编码
        val body = FormBody.Builder()
            .add("user_id", userId)
            .add("type", type.wire)
            .build()
        val request = Request.Builder()
            .url(HOST + path)
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", refererOf(type))
            .build()
        val start = System.currentTimeMillis()
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$path $userId type=${type.wire} -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("$fallbackMessage (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, "$path $userId type=${type.wire}", fallbackMessage, requireEc = true)
            val message = root["em"]?.jsonPrimitive?.contentOrNull.orEmpty()
            AppLog.d(TAG, "$path $userId type=${type.wire} -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），$message")
            return message
        }
    }
}
