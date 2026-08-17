package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class AfadianContentService(private val tokenStore: AuthTokenStore, private val client: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAccount(): AfadianAccount = get("/api/my/account", AfadianAccountResponse.serializer()).data
        ?: error("账号信息加载失败")

    suspend fun fetchSponsoring(): List<SponsoringItem> = get("/api/my/sponsoring?need_sponsor_info=1", SponsoringResponse.serializer()).data?.sponsoring.orEmpty()
    suspend fun fetchMarkedCreators(): List<FavoriteItem> = fetchFavoriteList("/api/my/marked") { it["user"]?.jsonObject ?: it }
    suspend fun fetchMarkedPosts(): List<FavoriteItem> = fetchFavoriteList("/api/my/marked-post") { it["post"]?.jsonObject ?: it }
    suspend fun fetchMarkedCrowdfundings(): List<FavoriteItem> = fetchFavoriteList("/api/user/marked-crowdfunding") { it["crowdfunding"]?.jsonObject ?: it }
    suspend fun fetchRecommendedPosts(publishSN: String = ""): List<RecommendedPost> {
        val url = "https://afdian.com/api/post/get-rec-list".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token).addQueryParameter("publish_sn", publishSN)
            .addQueryParameter("type", "old").addQueryParameter("all", "1").build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("内容加载失败 (${response.code})")
            json.decodeFromString<RecommendedPostsResponse>(response.body.string()).data?.list.orEmpty()
        }
    }

    private fun <T> get(path: String, serializer: kotlinx.serialization.KSerializer<T>): T {
        val url = ("https://afdian.com" + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token).build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("请求失败 (${response.code})")
            json.decodeFromString(serializer, response.body.string())
        }
    }

    private fun fetchFavoriteList(path: String, unwrap: (JsonObject) -> JsonObject): List<FavoriteItem> {
        val url = ("https://afdian.com" + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token).addQueryParameter("page", "1").build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("收藏加载失败 (${response.code})")
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val data = root["data"]?.jsonObject ?: return@use emptyList()
            val items = data["items"]?.jsonArray ?: data["list"]?.jsonArray ?: return@use emptyList()
            items.mapNotNull { element ->
                val value = unwrap(element.jsonObject)
                FavoriteItem(
                    id = value.string("post_id") ?: value.string("user_id") ?: value.string("crowdfunding_id") ?: value.string("id") ?: return@mapNotNull null,
                    title = value.string("title") ?: value.string("name") ?: "未命名内容",
                    subtitle = value.string("preview") ?: value.string("preview_text") ?: value.string("desc") ?: value.string("doing") ?: "",
                    image = value.string("cover") ?: value.string("avatar")
                )
            }
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

@Serializable data class FavoriteItem(val id: String, val title: String, val subtitle: String, val image: String? = null)

@Serializable private data class AfadianAccountResponse(val ec: Int? = null, val data: AfadianAccount? = null)
@Serializable private data class SponsoringResponse(val ec: Int? = null, val data: SponsoringPayload? = null)
@Serializable private data class SponsoringPayload(val sponsoring: List<SponsoringItem> = emptyList())
@Serializable private data class RecommendedPostsResponse(val data: PostPayload? = null)
@Serializable private data class PostPayload(val list: List<RecommendedPost> = emptyList())
@Serializable data class RecommendedPost(
    val post_id: String = "", val title: String = "", val preview: String? = null,
    val content: String? = null, val cover: String? = null, val like_count: Int? = null,
    val comment_count: Int? = null, val user: AfadianUser = AfadianUser(),
) {
    val id: String get() = post_id
    val summary: String get() = (preview ?: content).orEmpty()
}

@Serializable data class AfadianUser(
    val user_id: String = "", val name: String = "", val avatar: String? = null, val url_slug: String? = null,
)

@Serializable data class AfadianAccount(
    val login: AccountLogin = AccountLogin(),
    val oauth: AccountOauth = AccountOauth(),
    val user_private_id: String? = null,
) {
    val displayName: String get() = oauth.wect?.nickname ?: login.email ?: login.phone ?: "爱发电用户"
    val avatar: String? get() = oauth.wect?.headimgurl
}

@Serializable data class AccountLogin(val email: String? = null, val phone: String? = null)
@Serializable data class AccountOauth(val wect: AccountWechat? = null)
@Serializable data class AccountWechat(val nickname: String? = null, val headimgurl: String? = null)

@Serializable data class SponsoringItem(
    val user: AfadianUser = AfadianUser(),
    val sale_list: List<SponsoringSale> = emptyList(),
    val new_post_count: Int? = null,
    val latest_post_update_time: Long? = null,
) {
    val id: String get() = user.user_id
    val planName: String get() = sale_list.firstOrNull()?.plan?.name ?: sale_list.firstOrNull()?.title ?: "发电计划"
}

@Serializable data class SponsoringSale(val title: String? = null, val plan: SponsoringPlan? = null)
@Serializable data class SponsoringPlan(val name: String? = null)
