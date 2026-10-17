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
        fetchFavoriteList("/api/my/marked", { it["user"]?.jsonObject ?: it })

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
        fetchFavoriteList("/api/user/marked-crowdfunding", { it["crowdfunding"]?.jsonObject ?: it }, ::decorateCrowdfunding)

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

    // ──────── 评论（只读 + 点赞，不含发布/删除）────────

    /**
     * 拉取动态的评论列表（`GET /api/comment/get-list`）。
     *
     * 游标分页：首屏传空串，之后传上一页 [CommentListPage.nextPublishSn]，
     * 服务端返回严格早于该 publish_sn 的评论。
     *
     * ⚠️ 热门评论（`data.hot_list`）**必须显式传 `hot=1` 才会下发**，
     * 不传这个参数时服务端连 key 都不返回（实测）。而且只有首屏（游标为空）才返回，
     * 翻页时 hot_list 是空数组——所以这里只在首屏拼 `hot=1`，后续页天然不会重复。
     *
     * 该接口匿名也能读，未登录时 `has_like` 为 null（按未赞处理）。
     */
    suspend fun fetchCommentList(postId: String, publishSn: String = ""): CommentListPage {
        val url = "$IFDIAN_HOST/api/comment/get-list".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("post_id", postId)
            .addQueryParameter("publish_sn", publishSn)
            .addQueryParameter("type", "old")
            .apply { if (publishSn.isBlank()) addQueryParameter("hot", "1") }
            .build()
        val start = System.currentTimeMillis()
        return http.execute(commentGetRequest(url, postId)).use { response ->
            val root = response.requireJson(TAG, "/api/comment/get-list", postId, start)
            root.requireAfdianSuccess(TAG, "/api/comment/get-list", "评论加载失败")
            parseCommentPage(root).also { page ->
                AppLog.d(
                    TAG,
                    "/api/comment/get-list post_id=$postId sn=$publishSn -> " +
                        "HTTP ${response.code}（${System.currentTimeMillis() - start}ms），" +
                        "返回 ${page.comments.size} 条，has_more=${page.hasMore}"
                )
            }
        }
    }

    /**
     * 拉取某条根评论的完整回复列表（`GET /api/comment/get-sub-list`）。
     *
     * 主列表接口只内嵌前几条楼中楼，`sub_comment_has_more == 1` 时用它补齐；
     * 回复同样按 publish_sn 游标翻页。
     */
    suspend fun fetchSubComments(
        postId: String,
        rootCommentId: String,
        publishSn: String = "",
    ): CommentListPage {
        val url = "$IFDIAN_HOST/api/comment/get-sub-list".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("post_id", postId)
            .addQueryParameter("root_comment_id", rootCommentId)
            .addQueryParameter("publish_sn", publishSn)
            .build()
        val start = System.currentTimeMillis()
        return http.execute(commentGetRequest(url, postId)).use { response ->
            val root = response.requireJson(TAG, "/api/comment/get-sub-list", postId, start)
            root.requireAfdianSuccess(TAG, "/api/comment/get-sub-list", "回复加载失败")
            parseCommentPage(root).also { page ->
                AppLog.d(
                    TAG,
                    "/api/comment/get-sub-list root=$rootCommentId sn=$publishSn -> " +
                        "HTTP ${response.code}（${System.currentTimeMillis() - start}ms），" +
                        "返回 ${page.comments.size} 条，has_more=${page.hasMore}"
                )
            }
        }
    }

    /** 评论点赞（`POST /api/comment/like`），需要登录 */
    suspend fun likeComment(commentId: String) = postCommentAction("/api/comment/like", commentId)

    /** 取消评论点赞（`POST /api/comment/unlike`），需要登录 */
    suspend fun unlikeComment(commentId: String) = postCommentAction("/api/comment/unlike", commentId)

    /** 评论类 GET 请求：与 get-detail 一样补 Referer / 版本 / 语言头，否则服务端会返回降级内容 */
    private fun commentGetRequest(url: okhttp3.HttpUrl, postId: String): Request =
        Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("Referer", "$IFDIAN_HOST/p/$postId")
            .header("afd-fe-version", "1.20.10")
            .header("locale-lang", "zh-CN")
            .build()

    /** 统一取出响应体并解析成 JSON 对象，HTTP 非 2xx 直接抛带上下文的异常 */
    private fun okhttp3.Response.requireJson(
        tag: String,
        path: String,
        postId: String,
        start: Long,
    ): JsonObject {
        if (!isSuccessful) {
            AppLog.e(tag, "$path post_id=$postId -> HTTP $code（${System.currentTimeMillis() - start}ms）")
            error("评论加载失败 ($code)")
        }
        return json.parseToJsonElement(body.string()).jsonObject
    }

    /** 评论点赞/取消：POST JSON `{comment_id}`，与动态点赞同一套写法（写请求不自动重试） */
    private fun postCommentAction(path: String, commentId: String) {
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("comment_id", commentId)
                put("auth_token", tokenStore.token)
            },
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(AFDIAN_HOST + path)
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", "$AFDIAN_HOST/")
            .build()
        val start = System.currentTimeMillis()
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.w(TAG, "$path comment_id=$commentId -> HTTP ${response.code}，操作失败")
                error("操作失败 (${response.code})")
            }
            val bodyText = response.body.string()
            if (bodyText.isNotBlank()) {
                runCatching { json.parseToJsonElement(bodyText).jsonObject }
                    .getOrNull()
                    ?.requireAfdianSuccess(TAG, path, "操作失败")
            }
            AppLog.d(TAG, "$path comment_id=$commentId -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
        }
    }

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

    /**
     * 收藏列表通用拉取：unwrap 负责从条目里取出主体对象（创作者/众筹），
     * decorate 可选地在基础字段之上追加 tab 专属解析（如众筹详情）。
     */
    private fun fetchFavoriteList(
        path: String,
        unwrap: (JsonObject) -> JsonObject,
        decorate: (FavoriteItem, JsonObject) -> FavoriteItem = { item, _ -> item },
    ): List<FavoriteItem> {
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
                val item = FavoriteItem(
                    id = value.string("post_id") ?: value.string("user_id") ?: value.string("crowdfunding_id") ?: value.string("id") ?: return@mapNotNull null,
                    title = value.string("title") ?: value.string("name") ?: "未命名内容",
                    subtitle = value.string("preview") ?: value.string("preview_text") ?: value.string("desc") ?: value.string("doing") ?: "",
                    image = value.string("avatar") ?: value.string("cover") ?: value.string("pic"),
                    user = runCatching { json.decodeFromString<AfadianUser>(value.toString()) }.getOrNull(),
                )
                decorate(item, value)
            }
        }.also { list ->
            AppLog.d(TAG, "$path -> HTTP $httpCode（${System.currentTimeMillis() - start}ms），返回 ${list.size} 条")
        }
    }

    /** 众筹 tab 专属：把官方众筹详情整体解析进 FavoriteItem，卡片渲染进度/倒计时要用。 */
    private fun decorateCrowdfunding(item: FavoriteItem, value: JsonObject): FavoriteItem =
        item.copy(
            crowdfunding = runCatching {
                json.decodeFromJsonElement(FavoriteCrowdfunding.serializer(), value)
            }.onFailure {
                AppLog.w(TAG, "众筹详情解析失败 id=${item.id}", it)
            }.getOrNull(),
        )

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
