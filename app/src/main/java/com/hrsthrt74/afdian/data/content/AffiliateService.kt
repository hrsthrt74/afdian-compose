package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import kotlinx.serialization.encodeToString
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
 * 创作者优惠码领域服务。
 *
 * 优惠码接口全部使用创作者后台的 ifdian.net 域名和 Cookie 认证，
 * 这里集中保留请求头、业务错误码和表单字段的接口契约，避免主内容服务继续膨胀。
 */
internal class AffiliateService(
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
        const val TAG = "AffiliateService"
        const val SETTING_REFERER = "$HOST/setting/aff"
        const val EDIT_REFERER = "$HOST/edit/aff"
    }

    suspend fun fetchAffiliates(): List<Affiliate> {
        val url = (HOST + "/api/creator/list-affiliate").toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", SETTING_REFERER)
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "list-affiliate -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("优惠码加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            checkBusinessResult(root, "list-affiliate", "优惠码加载失败")
            val list = root["data"]?.jsonObject?.get("list")?.jsonArray.orEmpty().mapNotNull { element ->
                runCatching { json.decodeFromString(Affiliate.serializer(), element.toString()) }.getOrNull()
            }
            list.also {
                AppLog.d(TAG, "list-affiliate -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${it.size} 条")
            }
        }
    }

    suspend fun setAffiliateSwitch(affiliateCode: String, enable: Boolean) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("affiliate_code", affiliateCode)
                put("switch", if (enable) 1 else 0)
            },
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$HOST/api/creator/affiliate-switch")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", SETTING_REFERER)
            .build()
        executeMutation(request, "affiliate-switch code=$affiliateCode", "设置失败")
    }

    suspend fun generateAffiliateCode(): String {
        val url = (HOST + "/api/creator/generate-affiliate-code").toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", "$EDIT_REFERER")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "generate-affiliate-code -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("生成优惠码失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            checkBusinessResult(root, "generate-affiliate-code", "生成优惠码失败")
            val code = root["data"]?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull.orEmpty()
            AppLog.d(TAG, "generate-affiliate-code -> $code（${System.currentTimeMillis() - start}ms）")
            code
        }
    }

    suspend fun fetchAffiliateSelectablePlans(): List<AffiliateSelectablePlan> {
        val url = (HOST + "/api/creator/simple-plan-list").toHttpUrl().newBuilder()
            .addQueryParameter("guild_id", "")
            .addQueryParameter("platform", "")
            .addQueryParameter("show_hide", "0")
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", EDIT_REFERER)
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "simple-plan-list -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("方案列表加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            checkBusinessResult(root, "simple-plan-list", "方案列表加载失败")
            val data = root["data"]?.jsonObject
            val raw = data?.get("list")?.jsonArray.orEmpty() + data?.get("sale_list")?.jsonArray.orEmpty()
            val plans = raw.mapNotNull { element ->
                runCatching { json.decodeFromString(AffiliateSelectablePlan.serializer(), element.toString()) }.getOrNull()
            }
            plans.also {
                AppLog.d(TAG, "simple-plan-list -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${it.size} 条")
            }
        }
    }

    suspend fun editAffiliate(form: AffiliateForm) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                form.id.toLongOrNull()?.let { put("id", it) } ?: put("id", form.id)
                put("affiliate_code", form.affiliateCode)
                put("discount", form.discount)
                put("has_time_limit", if (form.hasTimeLimit) 1 else 0)
                put("begin_time", form.beginTime)
                put("end_time", form.endTime)
                put("has_count_limit", if (form.hasCountLimit) 1 else 0)
                put("count_limit", form.countLimit)
                put("plan_range", form.planRange)
                put("plan_ids", form.planIds)
                put("use_limit_count", form.useLimitCount)
            },
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$HOST/api/creator/edit-affiliate")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", EDIT_REFERER)
            .build()
        executeMutation(request, "edit-affiliate code=${form.affiliateCode}", "保存失败")
    }

    suspend fun fetchAffiliateDetail(affiliateCode: String): Affiliate {
        val url = (HOST + "/api/creator/affiliate-detail").toHttpUrl().newBuilder()
            .addQueryParameter("affiliate_code", affiliateCode)
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", "$EDIT_REFERER?affiliate_code=$affiliateCode")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "affiliate-detail code=$affiliateCode -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("优惠码详情加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            checkBusinessResult(root, "affiliate-detail code=$affiliateCode", "优惠码详情加载失败")
            val data = root["data"]?.jsonObject ?: error("优惠码详情返回结构异常")
            json.decodeFromString(Affiliate.serializer(), data.toString()).also {
                AppLog.d(TAG, "affiliate-detail code=$affiliateCode -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            }
        }
    }

    suspend fun deleteAffiliate(id: Long) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject { put("id", id) },
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$HOST/api/creator/delete-affiliate")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", EDIT_REFERER)
            .build()
        executeMutation(request, "delete-affiliate id=$id", "删除失败")
    }

    /** POST 类优惠码操作的共同失败处理，保留接口返回的 em 作为上层提示。 */
    private fun executeMutation(request: Request, logPath: String, fallbackMessage: String) {
        val start = System.currentTimeMillis()
        http.execute(request).use { response ->
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

    private fun checkBusinessResult(root: JsonObject, logPath: String, fallbackMessage: String) {
        val ec = root["ec"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        if (ec != 200) {
            val message = root["em"]?.jsonPrimitive?.contentOrNull.orEmpty()
            AppLog.w(TAG, "$logPath -> ec=$ec em=$message")
            error(message.ifBlank { fallbackMessage })
        }
    }
}
