package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 自动回复菜单（FAQ）领域服务。
 *
 * 对应网页版「设置自动回复菜单」页（https://ifdian.net/setting/faq）：
 * 创作者在这里维护若干条「问题 → 答案」，粉丝在创作者主页会话页点一下即可快捷提问。
 *
 * 接口全部使用 ifdian.net 域名 + Cookie 认证，写操作 referer 指向设置页，
 * 这里集中保留请求头与业务错误码的接口契约，避免主内容服务继续膨胀。
 */
internal class FaqService(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient,
) {
    private val http = AfdianHttpClient(client)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private companion object {
        const val HOST = "https://ifdian.net"
        const val TAG = "FaqService"
        /** 网页端设置页地址：写接口的 referer 与之保持一致 */
        const val SETTING_REFERER = "$HOST/setting/faq"
    }

    /**
     * 拉取当前账号的全部 FAQ 条目。
     *
     * 接口一次返回全量、无分页，所以这里直接给 List；解析失败的条目跳过而非让整页失败。
     */
    suspend fun fetchFaqs(): List<FaqItem> {
        val url = (HOST + "/api/faq/list").toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", SETTING_REFERER)
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "list -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("自动回复加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            checkBusinessResult(root, "list", "自动回复加载失败")
            // data.list 是数组；逐条容错解析，个别脏数据不至于让整页空白
            val list = root["data"]?.jsonObject?.get("list")?.jsonArray.orEmpty().mapNotNull { element ->
                runCatching { json.decodeFromString(FaqItem.serializer(), element.toString()) }.getOrNull()
            }
            list.also {
                AppLog.d(TAG, "list -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${it.size} 条")
            }
        }
    }

    /**
     * 新增或更新一条 FAQ。
     *
     * 网页端用同一个 edit-faq 接口完成增删改：[id] 为空串即新增，传 id 则为编辑。
     *
     * @param id 条目 id，新增传空串
     */
    suspend fun editFaq(id: Int, title: String, content: String) {
        // id 为 0（未保存的新条目）时不传 id 字段，等价于网页端的新增
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                if (id > 0) put("id", id)
                put("title", title)
                put("content", content)
            },
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$HOST/api/faq/edit-faq")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", SETTING_REFERER)
            .build()
        executeMutation(request, "edit-faq id=$id title=$title", "保存失败")
    }

    /** 删除一条 FAQ。 */
    suspend fun deleteFaq(id: Int) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject { put("id", id) },
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$HOST/api/faq/delete-faq")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", SETTING_REFERER)
            .build()
        executeMutation(request, "delete-faq id=$id", "删除失败")
    }

    /** POST 类写操作的共同失败处理，保留接口返回的 em 作为上层提示文案。 */
    private fun executeMutation(request: Request, logPath: String, fallbackMessage: String) {
        val start = System.currentTimeMillis()
        http.execute(request, allowFallback = false).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$logPath -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("$fallbackMessage (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val message = root["em"]?.jsonPrimitive?.contentOrNull.orEmpty()
            checkBusinessResult(root, logPath, fallbackMessage)
            AppLog.d(TAG, "$logPath -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），$message")
        }
    }

    /** 业务码校验：ec 非 200 时抛出接口的 em，供 UI 直接展示。 */
    private fun checkBusinessResult(root: JsonObject, logPath: String, fallbackMessage: String) {
        val ec = root["ec"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        if (ec != 200) {
            val message = root["em"]?.jsonPrimitive?.contentOrNull.orEmpty()
            AppLog.w(TAG, "$logPath -> ec=$ec em=$message")
            error(message.ifBlank { fallbackMessage })
        }
    }
}
