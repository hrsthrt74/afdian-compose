package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 感谢页领域服务：感谢者分页、感谢语读取和保存。 */
internal class ThankSponsorsService(
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
        const val IFDIAN_HOST = "https://ifdian.net"
        const val TAG = "ThankSponsorsService"
    }

    suspend fun fetchThankSponsors(userId: String, year: Int, month: Int, page: Int = 1): ThankSponsorsPage {
        val url = "$AFDIAN_HOST/api/creator/get-thank-sponsors".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("user_id", userId)
            .addQueryParameter("year", year.toString())
            .addQueryParameter("month", month.toString())
            .addQueryParameter("page", page.toString())
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "get-thank-sponsors year=$year month=$month page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("感谢页加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, "get-thank-sponsors", "感谢页加载失败", requireEc = true)
            val data = root["data"]?.jsonObject
            val sponsors = data?.get("list")?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = element.jsonObject
                val doing = (obj["creator"] as? JsonObject)?.string("doing")
                ThankSponsor(
                    user_id = obj.string("user_id") ?: return@mapNotNull null,
                    name = obj.string("name").orEmpty(),
                    avatar = obj.string("avatar"),
                    url_slug = obj.string("url_slug"),
                    doing = doing?.takeIf { it.isNotBlank() },
                )
            }
            ThankSponsorsPage(
                list = sponsors,
                hasMore = (data?.get("has_more")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0) == 1,
                totalLabel = data?.get("total_count")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "所有",
            ).also {
                AppLog.d(TAG, "get-thank-sponsors year=$year month=$month page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${it.list.size} 条，has_more=${it.hasMore}")
            }
        }
    }

    suspend fun fetchThankWord(userId: String, year: Int, month: Int): String =
        get(
            "/api/creator/get-thank-word?user_id=$userId&year=$year&month=$month",
            ThankWordResponse.serializer(),
            host = IFDIAN_HOST,
        ).data?.content.orEmpty()

    suspend fun editThankWord(content: String, year: Int, month: Int) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("content", content)
                put("pic", "")
                put("year", year)
                put("month", month)
            },
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$IFDIAN_HOST/api/creator/edit-thank-word")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", "$IFDIAN_HOST/")
            .build()
        val start = System.currentTimeMillis()
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "edit-thank-word -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("感谢语保存失败 (${response.code})")
            }
            val result = json.decodeFromString(ThankWordResponse.serializer(), response.body.string())
            if (result.ec != 200) {
                AppLog.w(TAG, "edit-thank-word -> ec=${result.ec} em=${result.em}")
                error(result.em ?: "感谢语保存失败")
            }
            AppLog.d(TAG, "edit-thank-word -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），${result.em}")
        }
    }

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
        return http.execute(request).use { response ->
            if (!response.isSuccessful) error("请求失败 (${response.code})")
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, "请求失败", requireEc = true)
            json.decodeFromJsonElement(serializer, root)
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
}

@Serializable
private data class ThankWordResponse(
    val ec: Int? = null,
    val em: String? = null,
    val data: ThankWord? = null,
)

@Serializable
private data class ThankWord(val content: String? = null, val pic: String? = null)
