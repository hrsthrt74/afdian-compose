package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 动态、收藏和互动领域服务。
 *
 * 这里集中维护推荐流、收藏列表、动态详情以及点赞/收藏/关注请求，
 * 避免这些接口继续和账户、账单、创作者聚合逻辑混在一个 Service 中。
 */
internal class FeedService(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient,
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
        const val TAG = "FeedService"
    }

    suspend fun fetchMarkedCreators(): List<FavoriteItem> =
        fetchFavoriteList("/api/my/marked") { it["user"]?.jsonObject ?: it }

    suspend fun fetchMarkedPosts(page: Int = 1): MarkedPostsPage {
        val url = "$AFDIAN_HOST/api/my/marked-post".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("page", page.toString())
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "/api/my/marked-post page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("收藏加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, "/api/my/marked-post", "收藏加载失败", requireEc = true)
            val data = root["data"]?.jsonObject
            val elements = data?.get("items")?.jsonArray ?: data?.get("list")?.jsonArray
            MarkedPostsPage(
                posts = elements.orEmpty().mapNotNull { element ->
                    val value = element.jsonObject["post"] as? JsonObject ?: element.jsonObject
                    runCatching { json.decodeFromJsonElement(RecommendedPost.serializer(), value) }
                        .getOrNull()
                        ?.takeIf { it.post_id.isNotBlank() }
                },
                hasMore = (data?.get("has_more")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0) == 1,
            ).also { pageResult ->
                if (pageResult.posts.isEmpty()) {
                    AppLog.w(TAG, "marked-post 解析为空，data 原始内容：$data")
                }
                AppLog.d(TAG, "/api/my/marked-post page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${pageResult.posts.size} 条，has_more=${pageResult.hasMore}")
            }
        }
    }

    suspend fun fetchMarkedCrowdfundings(): List<FavoriteItem> =
        fetchFavoriteList("/api/user/marked-crowdfunding") { it["crowdfunding"]?.jsonObject ?: it }

    suspend fun fetchRecommendedPosts(publishSN: String = ""): RecommendedPostsPage {
        val url = "$AFDIAN_HOST/api/post/get-rec-list".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("publish_sn", publishSN)
            .addQueryParameter("type", "old")
            .addQueryParameter("all", "1")
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "/api/post/get-rec-list publishSN=$publishSN -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("内容加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, "/api/post/get-rec-list", "内容加载失败")
            val payload = json.decodeFromJsonElement(FeedRecommendedPostsResponse.serializer(), root).data
            val posts = payload?.list.orEmpty()
            val nextSN = posts.lastOrNull()?.publish_sn?.toString().orEmpty()
            RecommendedPostsPage(
                posts = posts,
                nextPublishSN = nextSN,
                hasMore = (payload?.has_more ?: 0) == 1,
            ).also {
                AppLog.d(TAG, "/api/post/get-rec-list publishSN=$publishSN -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${posts.size} 条，has_more=${payload?.has_more ?: 0}")
            }
        }
    }

    suspend fun fetchPostDetail(postId: String, albumId: String = ""): RecommendedPost {
        val url = "$IFDIAN_HOST/api/post/get-detail".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("post_id", postId)
            .addQueryParameter("album_id", albumId)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("Referer", "$IFDIAN_HOST/p/$postId")
            .header("afd-fe-version", "1.20.10")
            .header("locale-lang", "zh-CN")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "/api/post/get-detail post_id=$postId -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("动态详情加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, "/api/post/get-detail", "动态详情加载失败")
            val data = root["data"]?.jsonObject
            val post = (data?.get("post") as? JsonObject)
                ?: (data?.get("item") as? JsonObject)
                ?: error("动态详情返回结构异常")
            val result = json.decodeFromJsonElement(RecommendedPost.serializer(), post)
            val oldNew = data?.get("old_new")?.let {
                runCatching { json.decodeFromJsonElement(PostOldNew.serializer(), it) }.getOrNull()
            }
            result.copy(old_new = oldNew).also {
                AppLog.d(TAG, "/api/post/get-detail post_id=$postId -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            }
        }
    }

    suspend fun markCreator(userID: String) = postUserAction("/api/user/mark", userID)
    suspend fun unmarkCreator(userID: String) = postUserAction("/api/user/unmark", userID)
    suspend fun likePost(postID: String) = postPostAction("/api/post/like", postID)
    suspend fun unlikePost(postID: String) = postPostAction("/api/post/unlike", postID)
    suspend fun markPost(postID: String) = postPostAction("/api/user/mark-post", postID)
    suspend fun unmarkPost(postID: String) = postPostAction("/api/user/unmark-post", postID)

    private fun postUserAction(path: String, userID: String) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("user_id", userID)
                put("auth_token", tokenStore.token)
            },
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(IFDIAN_HOST + path)
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.w(TAG, "$path user_id=$userID -> HTTP ${response.code}，操作失败")
                error("操作失败 (${response.code})")
            }
            val bodyText = response.body.string()
            if (bodyText.isNotBlank()) {
                runCatching { json.parseToJsonElement(bodyText).jsonObject }
                    .getOrNull()
                    ?.requireAfdianSuccess(TAG, path, "操作失败")
            }
            AppLog.d(TAG, "$path user_id=$userID -> HTTP ${response.code}")
        }
    }

    private fun postPostAction(path: String, postID: String) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("post_id", postID)
                put("auth_token", tokenStore.token)
            },
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(AFDIAN_HOST + path)
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", "$AFDIAN_HOST/")
            .build()
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.w(TAG, "$path post_id=$postID -> HTTP ${response.code}，操作失败")
                error("操作失败 (${response.code})")
            }
            val bodyText = response.body.string()
            if (bodyText.isNotBlank()) {
                runCatching { json.parseToJsonElement(bodyText).jsonObject }
                    .getOrNull()
                    ?.requireAfdianSuccess(TAG, path, "操作失败")
            }
            AppLog.d(TAG, "$path post_id=$postID -> HTTP ${response.code}")
        }
    }

    private fun fetchFavoriteList(path: String, unwrap: (JsonObject) -> JsonObject): List<FavoriteItem> {
        val url = (AFDIAN_HOST + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("page", "1")
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        var httpCode = 0
        return http.execute(request).use { response ->
            httpCode = response.code
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$path -> HTTP $httpCode（${System.currentTimeMillis() - start}ms）")
                error("收藏加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, "收藏加载失败")
            val data = root["data"]?.jsonObject ?: return@use emptyList()
            val items = data["items"]?.jsonArray ?: data["list"]?.jsonArray ?: return@use emptyList()
            items.mapNotNull { element ->
                val value = unwrap(element.jsonObject)
                FavoriteItem(
                    id = value.string("post_id") ?: value.string("user_id") ?: value.string("crowdfunding_id") ?: value.string("id") ?: return@mapNotNull null,
                    title = value.string("title") ?: value.string("name") ?: "未命名内容",
                    subtitle = value.string("preview") ?: value.string("preview_text") ?: value.string("desc") ?: value.string("doing") ?: "",
                    image = value.string("avatar") ?: value.string("cover"),
                    user = runCatching { json.decodeFromString<AfadianUser>(value.toString()) }.getOrNull(),
                )
            }
        }.also { list ->
            AppLog.d(TAG, "$path -> HTTP $httpCode（${System.currentTimeMillis() - start}ms），返回 ${list.size} 条")
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}

@Serializable
private data class FeedRecommendedPostsResponse(val data: FeedPostPayload? = null)

@Serializable
private data class FeedPostPayload(
    val list: List<RecommendedPost> = emptyList(),
    val has_more: Int? = null,
)
