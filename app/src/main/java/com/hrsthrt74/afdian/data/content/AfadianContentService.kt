package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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

private val json = Json { 
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

class AfadianContentService(private val tokenStore: AuthTokenStore, private val client: OkHttpClient = OkHttpClient()) {

    companion object {
        private const val AFDIAN_HOST = "https://afdian.com"
        private const val IFDIAN_HOST = "https://ifdian.net"
        // 统一日志 Tag：本类所有网络埋点共用，便于 Logcat 过滤
        private const val TAG = "AfadianContentService"
    }

    suspend fun fetchAccount(): AfadianAccount = get("/api/my/account", AfadianAccountResponse.serializer()).data
        ?: error("账号信息加载失败")

    suspend fun fetchProfile(): AfadianProfile = get("/api/my/profile", AfadianProfileResponse.serializer()).data
        ?: error("个人信息加载失败")

    suspend fun fetchSponsoring(): List<SponsoringItem> = get("/api/my/sponsoring?need_sponsor_info=1", SponsoringResponse.serializer()).data?.sponsoring.orEmpty()

    /**
     * 赞助者感谢页（创作者后台 get-thank-sponsors）：按年月分页拉取「该感谢谁」的用户资料。
     * 响应里只有用户资料（无金额/计划）；分页只看 has_more。
     */
    suspend fun fetchThankSponsors(userId: String, year: Int, month: Int, page: Int = 1): ThankSponsorsPage {
        val url = "https://afdian.com/api/creator/get-thank-sponsors".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("user_id", userId)
            .addQueryParameter("year", year.toString())
            .addQueryParameter("month", month.toString())
            .addQueryParameter("page", page.toString())
            .build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        // 埋点：请求前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 非 2xx 属于异常路径，E 级日志始终输出
                AppLog.e(TAG, "get-thank-sponsors year=$year month=$month page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("感谢页加载失败 (${response.code})")
            }
            val data = json.parseToJsonElement(response.body.string()).jsonObject["data"]?.jsonObject
            val sponsors = data?.get("list")?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = element.jsonObject
                // creator 字段类型不稳定：普通用户是空数组 []，创作者是对象，取 doing 前必须安全转型
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
                // ⚠️ total_count 不是数字，是给前端 tab 的展示文案（如「所有」），只用于标题展示
                totalLabel = data?.get("total_count")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "所有",
            ).also {
                AppLog.d(TAG, "get-thank-sponsors year=$year month=$month page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${it.list.size} 条，has_more=${it.hasMore}")
            }
        }
    }

    /**
     * 读取感谢语（感谢页顶部创作者自定义文案，与年月无关，接口带年月参数但内容全局一份）
     */
    suspend fun fetchThankWord(userId: String, year: Int, month: Int): String =
        // user_id 是 32 位 hex、year/month 是数字，直接拼 query 不会有转义问题
        get("/api/creator/get-thank-word?user_id=$userId&year=$year&month=$month", ThankWordResponse.serializer(), host = IFDIAN_HOST)
            .data?.content.orEmpty()

    /**
     * 保存感谢语：POST JSON {content, pic, year, month}（服务端会校验年月，缺失时报 year\month error），
     * 成功时 ec=200、em=「感谢语保存成功」
     */
    suspend fun editThankWord(content: String, year: Int, month: Int) {
        val url = (IFDIAN_HOST + "/api/creator/edit-thank-word").toHttpUrl()
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("content", content)
                put("pic", "")
                put("year", year)
                put("month", month)
            }
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", IFDIAN_HOST + "/").build()
        val start = System.currentTimeMillis()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "edit-thank-word -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("感谢语保存失败 (${response.code})")
            }
            val result = json.decodeFromString(ThankWordResponse.serializer(), response.body.string())
            // ec != 200 属于业务失败（如未登录），抛错让上层提示
            if ((result.ec ?: 0) != 200) {
                AppLog.w(TAG, "edit-thank-word -> ec=${result.ec} em=${result.em}")
                error(result.em ?: "感谢语保存失败")
            }
            AppLog.d(TAG, "edit-thank-word -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），${result.em}")
        }
    }

    suspend fun fetchMarkedCreators(): List<FavoriteItem> = fetchFavoriteList("/api/my/marked") { it["user"]?.jsonObject ?: it }
    // 收藏动态：每项里的 post 对象结构与精选接口（rec-list）完全一致，
    // 直接反序列化成 RecommendedPost，供 PostCard 组件渲染完整卡片
    suspend fun fetchMarkedPosts(page: Int = 1): MarkedPostsPage {
        val url = "https://afdian.com/api/my/marked-post".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("page", page.toString())
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}").build()
        // 埋点：请求前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 属于异常路径，E 级日志始终输出（原有 error 抛错行为不变）
                AppLog.e(TAG, "/api/my/marked-post page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("收藏加载失败 (${response.code})")
            }
            val data = json.parseToJsonElement(response.body.string()).jsonObject["data"]?.jsonObject
            // 兼容 items / list 两种字段名（与通用收藏列表逻辑保持一致）
            val elements = data?.get("items")?.jsonArray ?: data?.get("list")?.jsonArray
            MarkedPostsPage(
                posts = elements.orEmpty().mapNotNull { element ->
                    // 兼容两种返回结构：项本身是 post 对象，或包了一层 "post" 字段
                    val value = element.jsonObject["post"] as? JsonObject ?: element.jsonObject
                    runCatching { json.decodeFromJsonElement(RecommendedPost.serializer(), value) }
                        .getOrNull()
                        // 完全解析失败时退化为最小可用数据，保证卡片至少能显示标题/作者
                        ?.takeIf { it.post_id.isNotBlank() }
                },
                hasMore = (data?.get("has_more")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0) == 1,
            ).also { page ->
                // 诊断日志：解析结果为空时打印原始返回，方便排查字段结构
                if (page.posts.isEmpty()) {
                    AppLog.w(TAG, "marked-post 解析为空，data 原始内容：$data")
                }
            }.also { result ->
                // 埋点：D 级调试日志，记录分页参数、HTTP 状态码、耗时与解析出的动态条数
                AppLog.d(TAG, "/api/my/marked-post page=$page -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${result.posts.size} 条，has_more=${result.hasMore}")
            }
        }
    }
    suspend fun fetchMarkedCrowdfundings(): List<FavoriteItem> = fetchFavoriteList("/api/user/marked-crowdfunding") { it["crowdfunding"]?.jsonObject ?: it }
    suspend fun fetchRecommendedPosts(publishSN: String = ""): RecommendedPostsPage {
        val url = "https://afdian.com/api/post/get-rec-list".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token).addQueryParameter("publish_sn", publishSN)
            .addQueryParameter("type", "old").addQueryParameter("all", "1").build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        // 埋点：请求前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 属于异常路径，E 级日志始终输出（原有 error 抛错行为不变）
                AppLog.e(TAG, "/api/post/get-rec-list publishSN=$publishSN -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("内容加载失败 (${response.code})")
            }
            val payload = json.decodeFromString<RecommendedPostsResponse>(response.body.string()).data
            val posts = payload?.list.orEmpty()
            val nextSN = posts.lastOrNull()?.publish_sn?.toString().orEmpty()
            RecommendedPostsPage(
                posts = posts,
                nextPublishSN = nextSN,
                hasMore = (payload?.has_more ?: 0) == 1,
            ).also {
                // 埋点：D 级调试日志，记录分页游标、HTTP 状态码、耗时与返回动态条数
                AppLog.d(TAG, "/api/post/get-rec-list publishSN=$publishSN -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${posts.size} 条，has_more=${payload?.has_more ?: 0}")
            }
        }
    }

    /** 动态详情：GET /api/post/get-detail，正文 HTML 在 content 字段 */
    suspend fun fetchPostDetail(postId: String, albumId: String = ""): RecommendedPost {
        val url = "https://ifdian.net/api/post/get-detail".toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("post_id", postId)
            .addQueryParameter("album_id", albumId)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("Referer", "https://ifdian.net/p/$postId")
            .header("afd-fe-version", "1.20.10")
            .header("locale-lang", "zh-CN")
            .build()
        // 埋点：请求前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 属于异常路径，E 级日志始终输出（原有 error 抛错行为不变）
                AppLog.e(TAG, "/api/post/get-detail post_id=$postId -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("动态详情加载失败 (${response.code})")
            }
            val body = response.body.string()
            val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            // 兼容 data.post / data.item 两种包裹字段
            val post = (data?.get("post") as? JsonObject)
                ?: (data?.get("item") as? JsonObject)
                ?: run {
                    // 埋点：返回结构异常属于错误路径，W 级日志始终输出（原有 error 抛错行为不变）
                    AppLog.w(TAG, "/api/post/get-detail post_id=$postId 返回结构异常，缺少 data.post/data.item")
                    error("动态详情返回结构异常")
                }
            // old_new 与 post 同级（在 data 下），需要单独解析后合并
            val result = json.decodeFromJsonElement(RecommendedPost.serializer(), post)
            val oldNew = data?.get("old_new")?.let { runCatching { json.decodeFromJsonElement(PostOldNew.serializer(), it) }.getOrNull() }
            result.copy(old_new = oldNew).also {
                // 埋点：D 级调试日志，记录动态 ID、HTTP 状态码与总耗时
                AppLog.d(TAG, "/api/post/get-detail post_id=$postId -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            }
        }
    }

    suspend fun fetchCreatorDetail(user: AfadianUser): CreatorDetail {
        val userID = user.user_id
        return coroutineScope {
            val profileDeferred = async {
                if (user.url_slug.isNullOrBlank()) user
                else runCatching { fetchProfileBySlug(user.url_slug) }.getOrDefault(user)
            }
            val goalsDeferred = async { runCatching { fetchGoals(userID) }.getOrDefault(emptyList()) }
            val sponsorsDeferred = async { runCatching { fetchTopSponsors(userID) }.getOrDefault(emptyList()) }
            val plansDeferred = async { runCatching { fetchPlans(userID) }.getOrDefault(emptyList()) }
            val albumsDeferred = async { runCatching { fetchAlbums(userID) }.getOrDefault(emptyList()) }
            val postsDeferred = async { runCatching { fetchUserPosts(userID) }.getOrDefault(emptyList()) }
            val productsDeferred = async { runCatching { fetchCreatorProducts(userID) }.getOrDefault(emptyList()) }
            val markedDeferred = async { runCatching { fetchMarkedCreators() }.getOrDefault(emptyList()) }
            CreatorDetail(
                profile = profileDeferred.await(),
                goals = goalsDeferred.await(),
                topSponsors = sponsorsDeferred.await(),
                plans = plansDeferred.await(),
                albums = albumsDeferred.await(),
                posts = postsDeferred.await(),
                products = productsDeferred.await(),
                isMarked = markedDeferred.await().any { it.id == userID },
            )
        }
    }

    suspend fun markCreator(userID: String) = postUserAction("/api/user/mark", userID)

    // ── 动态互动：点赞 / 收藏（参数均为 post_id，端点来自 afdian.com 前端 JS）──
    suspend fun likePost(postID: String) = postPostAction("/api/post/like", postID)
    suspend fun unlikePost(postID: String) = postPostAction("/api/post/unlike", postID)
    suspend fun markPost(postID: String) = postPostAction("/api/user/mark-post", postID)
    suspend fun unmarkPost(postID: String) = postPostAction("/api/user/unmark-post", postID)
    suspend fun unmarkCreator(userID: String) = postUserAction("/api/user/unmark", userID)

    suspend fun fetchMessageDialogs(page: Int = 1, unread: Int = 0): MessageDialogPage {
        val url = (AFDIAN_HOST + "/api/message/dialogs").toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("unread", unread.toString())
            .build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        // 埋点：请求前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 属于异常路径，E 级日志始终输出（原有 error 抛错行为不变）
                AppLog.e(TAG, "/api/message/dialogs page=$page unread=$unread -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("消息列表加载失败 (${response.code})")
            }
            val body = response.body.string()
            val root = try { json.parseToJsonElement(body).jsonObject } catch (_: Exception) {
                // 埋点：响应体解析失败属于错误路径，W 级日志始终输出
                AppLog.w(TAG, "/api/message/dialogs page=$page 响应体解析失败")
                error("消息列表加载失败")
            }
            val ec = root["ec"]?.jsonPrimitive?.content?.toIntOrNull()
            if (ec != 200) {
                // 埋点：业务错误码（HTTP 200 但 ec!=200）走 W 级日志，只记错误码不透出消息内容
                AppLog.w(TAG, "/api/message/dialogs page=$page 业务失败 ec=$ec")
                error(root["message"]?.jsonPrimitive?.contentOrNull ?: "消息列表加载失败")
            }
            val data = root["data"]?.jsonObject ?: error("消息列表加载失败")
            val list = data["list"]?.jsonArray?.mapNotNull { element ->
                runCatching { parseMessageDialog(element.jsonObject) }.getOrNull()
            }.orEmpty()
            MessageDialogPage(
                dialogs = list,
                totalCount = data["total_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: list.size,
                totalPage = data["total_page"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                hasMore = data["has_more"]?.jsonPrimitive?.content?.toIntOrNull() == 1
            ).also {
                // 埋点：D 级调试日志，只记分页参数与会话数量，绝不输出私信/会话内容
                AppLog.d(TAG, "/api/message/dialogs page=$page unread=$unread -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${list.size} 条会话，total_page=${it.totalPage}，has_more=${it.hasMore}")
            }
        }
    }

    private fun parseMessageDialog(obj: JsonObject): MessageDialog {
        val userObj = obj["user"]?.jsonObject
        val user = if (userObj != null) {
            val avatar = userObj["avatar"]?.jsonPrimitive?.contentOrNull
                ?: userObj["headimgurl"]?.jsonPrimitive?.contentOrNull
            AfadianUser(
                user_id = userObj["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                name = userObj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                avatar = avatar,
                url_slug = userObj["url_slug"]?.jsonPrimitive?.contentOrNull,
                cover = userObj["cover"]?.jsonPrimitive?.contentOrNull,
                is_verified = userObj["is_verified"]?.jsonPrimitive?.content?.toIntOrNull()
            )
        } else {
            AfadianUser()
        }
        return MessageDialog(
            latest_msg_id = obj["latest_msg_id"]?.jsonPrimitive?.content?.toIntOrNull(),
            unread_count = obj["unread_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            total_count = obj["total_count"]?.jsonPrimitive?.content?.toIntOrNull(),
            status = obj["status"]?.jsonPrimitive?.content?.toIntOrNull(),
            user = user,
            desc = obj["desc"]?.jsonPrimitive?.contentOrNull,
            send_time = obj["send_time"]?.jsonPrimitive?.content?.toDoubleOrNull(),
            sponsor_info = runCatching {
                obj["sponsor_info"]?.jsonObject?.let { si ->
                    SponsorInfo(
                        out_detail = si["out_detail"]?.jsonObject?.let { od ->
                            SponsorAmount(
                                all_sum_amount = od["all_sum_amount"]?.jsonPrimitive?.contentOrNull,
                                current_amount = od["current_amount"]?.jsonPrimitive?.contentOrNull,
                                longest_expire_time = od["longest_expire_time"]?.jsonPrimitive?.content?.toLongOrNull()
                            )
                        },
                        in_detail = si["in_detail"]?.jsonObject?.let { id ->
                            SponsorAmount(
                                all_sum_amount = id["all_sum_amount"]?.jsonPrimitive?.contentOrNull,
                                current_amount = id["current_amount"]?.jsonPrimitive?.contentOrNull,
                                longest_expire_time = id["longest_expire_time"]?.jsonPrimitive?.content?.toLongOrNull()
                            )
                        }
                    )
                }
            }.getOrNull()
        )
    }

    suspend fun fetchMessages(userID: String, type: String = "old", messageID: String = ""): MessageConversation {
        val url = (AFDIAN_HOST + "/api/message/messages").toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("user_id", userID)
            .addQueryParameter("type", type)
            .addQueryParameter("message_id", messageID)
            .build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        // 埋点：请求前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 属于异常路径，E 级日志始终输出（原有 error 抛错行为不变）
                AppLog.e(TAG, "/api/message/messages user_id=$userID type=$type message_id=$messageID -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("会话加载失败 (${response.code})")
            }
            val body = response.body.string()
            val root = try { json.parseToJsonElement(body).jsonObject } catch (_: Exception) {
                // 埋点：响应体解析失败属于错误路径，W 级日志始终输出
                AppLog.w(TAG, "/api/message/messages user_id=$userID type=$type 响应体解析失败")
                error("会话加载失败")
            }
            val ec = root["ec"]?.jsonPrimitive?.content?.toIntOrNull()
            if (ec != 200) {
                // 埋点：业务错误码（HTTP 200 但 ec!=200）走 W 级日志，只记错误码不透出消息内容
                AppLog.w(TAG, "/api/message/messages user_id=$userID type=$type 业务失败 ec=$ec")
                error(root["message"]?.jsonPrimitive?.contentOrNull ?: "会话加载失败")
            }
            val data = root["data"]?.jsonObject ?: error("会话加载失败")
            val list = data["list"]?.jsonArray?.mapNotNull { element ->
                runCatching { parseMessageItem(element.jsonObject) }.getOrNull()
            }.orEmpty()
            MessageConversation(
                messages = list,
                hasMore = data["has_more"]?.jsonPrimitive?.content?.toIntOrNull() == 1,
                canSendPictureMessage = data["can_picture_message"]?.jsonPrimitive?.content?.toIntOrNull() == 1
            ).also {
                // 埋点：D 级调试日志，只记拉取参数与消息条数，绝不输出任何私信内容
                AppLog.d(TAG, "/api/message/messages user_id=$userID type=$type message_id=$messageID -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${list.size} 条消息，has_more=${it.hasMore}")
            }
        }
    }

    private fun parseMessageItem(obj: JsonObject): MessageItem {
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: ""
        val messageObj = obj["message"]?.jsonObject ?: return MessageItem(type = type)
        val contentElement = messageObj["content"]
        
        // 尝试从 content 对象或 message 对象获取 sku_detail
        val skuDetailSource = if (contentElement is JsonObject) contentElement else messageObj
        
        val content = when {
            contentElement == null || contentElement is JsonNull -> MessageContent.Empty
            contentElement is JsonPrimitive && contentElement.isString -> MessageContent.Text(contentElement.content)
            contentElement is JsonObject -> {
                val skuDetail = skuDetailSource["sku_detail"]?.jsonArray?.mapNotNull { skuElement ->
                    runCatching {
                        val skuObj = skuElement.jsonObject
                        MessageSKU(
                            sku_id = skuObj["sku_id"]?.jsonPrimitive?.contentOrNull,
                            count = skuObj["count"]?.jsonPrimitive?.content?.toIntOrNull(),
                            name = skuObj["name"]?.jsonPrimitive?.contentOrNull,
                            pic = skuObj["pic"]?.jsonPrimitive?.contentOrNull
                        )
                    }.getOrNull()
                }
                
                // 解析 plan 对象
                val planElement = contentElement["plan"]
                val plan = if (planElement is JsonObject) {
                    CreatorPlan(
                        plan_id = planElement["plan_id"]?.jsonPrimitive?.contentOrNull,
                        name = planElement["name"]?.jsonPrimitive?.contentOrNull,
                        title = planElement["title"]?.jsonPrimitive?.contentOrNull,
                        pic = planElement["pic"]?.jsonPrimitive?.contentOrNull,
                        price = planElement["price"]?.jsonPrimitive?.contentOrNull,
                        show_price = planElement["show_price"]?.jsonPrimitive?.contentOrNull
                    )
                } else null
                
                val objContent = MessageObjectContent(
                    out_trade_no = contentElement["out_trade_no"]?.jsonPrimitive?.contentOrNull,
                    show_amount = contentElement["show_amount"]?.jsonPrimitive?.contentOrNull,
                    total_amount = contentElement["total_amount"]?.jsonPrimitive?.contentOrNull,
                    per_month = contentElement["per_month"]?.jsonPrimitive?.contentOrNull,
                    month = contentElement["month"]?.jsonPrimitive?.content?.toIntOrNull(),
                    remark = contentElement["remark"]?.jsonPrimitive?.contentOrNull,
                    plan = plan,
                    sku_detail = skuDetail,
                    product_type = contentElement["product_type"]?.jsonPrimitive?.content?.toIntOrNull()
                )
                MessageContent.Object(objContent)
            }
            else -> MessageContent.Empty
        }
        val record = MessageRecord(
            msg_id = messageObj["msg_id"]?.jsonPrimitive?.content?.toIntOrNull(),
            id = messageObj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            sender = messageObj["sender"]?.jsonPrimitive?.contentOrNull,
            r_status = messageObj["r_status"]?.jsonPrimitive?.content?.toIntOrNull(),
            type = messageObj["type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            content = content,
            send_time = messageObj["send_time"]?.jsonPrimitive?.content?.toDoubleOrNull()
        )
        return MessageItem(type = type, message = record)
    }

    suspend fun fetchFAQs(userID: String): List<MessageFAQ> {
        val response = get("/api/faq/list-faq?user_id=$userID", MessageFAQResponse.serializer())
        if (response.ec != 200) error(response.message ?: "快捷回复加载失败")
        return response.data?.other_list.orEmpty()
    }

    suspend fun sendMessage(userID: String, content: String): MessageItem {
        val url = "$IFDIAN_HOST/api/message/send".toHttpUrl()
        val body = json.encodeToString(
            MessageSendRequest.serializer(),
            MessageSendRequest(user_id = userID, type = "1", content = content, auth_token = tokenStore.token)
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).header("Cookie", "auth_token=${tokenStore.token}").build()
        // 埋点：请求前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 属于异常路径，E 级日志始终输出（只记 user_id 与内容长度，绝不输出私信内容）
                AppLog.e(TAG, "/api/message/send user_id=$userID len=${content.length} -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("消息发送失败 (${response.code})")
            }
            val result = json.decodeFromString<MessageSendResponse>(response.body.string())
            if (result.ec != 200) {
                // 埋点：业务失败路径走 W 级日志，只记错误码，不透出消息内容与服务器 message 全文
                AppLog.w(TAG, "/api/message/send user_id=$userID len=${content.length} 业务失败 ec=${result.ec}")
                error(result.message ?: "消息发送失败")
            }
            // 埋点：D 级调试日志，发送成功只记 user_id、内容长度与耗时，绝不输出私信内容
            AppLog.d(TAG, "/api/message/send user_id=$userID len=${content.length} -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            result.toMessageItem() ?: run {
                val id = (System.currentTimeMillis() / 1000).toInt()
                MessageItem(
                    type = "send",
                    message = MessageRecord(msg_id = id, id = id, r_status = 1, type = 1, content = MessageContent.Text(content), send_time = System.currentTimeMillis() / 1000.0)
                )
            }
        }
    }

    /**
     * 发送 FAQ 快捷提问：POST /api/faq/ask（表单参数 id=FAQ条目ID, user_id=对方用户ID）。
     * 服务器会以 type=7 的系统消息形式返回自动回复。
     */
    suspend fun askFaq(userID: String, faqId: Int): MessageItem {
        val body = "id=$faqId&user_id=$userID"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder()
            .url("$IFDIAN_HOST/api/faq/ask")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        // 埋点：请求前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 属于异常路径，E 级日志始终输出（原有 error 抛错行为不变）
                AppLog.e(TAG, "/api/faq/ask user_id=$userID faqId=$faqId -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("快捷提问发送失败 (${response.code})")
            }
            val result = json.decodeFromString<MessageSendResponse>(response.body.string())
            if (result.ec != 200) {
                // 埋点：业务失败路径走 W 级日志，只记错误码不透出服务器 message 全文
                AppLog.w(TAG, "/api/faq/ask user_id=$userID faqId=$faqId 业务失败 ec=${result.ec}")
                error(result.message ?: "快捷提问发送失败")
            }
            // 埋点：D 级调试日志，记录目标用户、FAQ 编号、HTTP 状态码与耗时
            AppLog.d(TAG, "/api/faq/ask user_id=$userID faqId=$faqId -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            // 接口本身不回传消息内容，本地构造提问消息用于即时上屏
            MessageItem(
                type = "send",
                message = MessageRecord(
                    msg_id = (System.currentTimeMillis() / 1000).toInt(),
                    id = (System.currentTimeMillis() / 1000).toInt(),
                    r_status = 1,
                    type = 1,
                    content = MessageContent.Text(""),
                    send_time = System.currentTimeMillis() / 1000.0
                )
            )
        }
    }

    /** 根据公开主页 slug 获取完整用户资料，供正文创作者链接跳转使用。 */
    fun fetchProfileBySlug(slug: String): AfadianUser =
        get("/api/user/get-profile-by-slug?url_slug=$slug", UserProfileResponse.serializer()).data?.user
            ?: error("用户信息加载失败")

    private fun fetchGoals(userID: String): List<CreatorGoal> {
        val payload = get("/api/creator/get-goals?user_id=$userID", CreatorGoalsResponse.serializer()).data
            ?: return emptyList()
        return payload.list ?: payload.goals ?: emptyList()
    }

    private fun fetchTopSponsors(userID: String): List<TopSponsor> =
        get("/api/creator/get-top-sponsors?user_id=$userID", TopSponsorsResponse.serializer()).data?.list.orEmpty()

    private fun fetchPlans(userID: String): List<CreatorPlan> {
        val payload = get(
            "/api/creator/get-plans?user_id=$userID&album_id=&unlock_plan_ids=&diy=&affiliate_code=",
            CreatorPlansResponse.serializer(), host = IFDIAN_HOST
        ).data ?: return emptyList()
        return payload.list ?: payload.plans ?: emptyList()
    }

    private fun fetchAlbums(userID: String): List<CreatorAlbum> {
        val payload = get(
            "/api/user/get-album-list?user_id=$userID&page=1&per_page=10", CreatorAlbumsResponse.serializer()
        ).data ?: return emptyList()
        return payload.list ?: payload.albums ?: emptyList()
    }

    /** 获取用户动态：先获取相册列表，再遍历每个相册拉取帖子，合并去重后按时间倒序排列 */
    private suspend fun fetchUserPosts(userID: String): List<RecommendedPost> {
        // 1. 获取该用户的所有相册（每个相册就是一组动态）
        val albums = fetchAlbums(userID)
        if (albums.isEmpty()) return emptyList()
        // 2. 并发获取每个相册下的帖子
        return coroutineScope {
            albums.mapNotNull { it.album_id }.map { albumID ->
                async {
                    runCatching {
                        get(
                            "/api/user/get-album-post?album_id=$albumID",
                            AlbumPostsResponse.serializer()
                        ).data?.list.orEmpty()
                    }.getOrDefault(emptyList())
                }
            }.flatMap { it.await() }
              .distinctBy { it.post_id }
              .sortedByDescending { it.publish_time ?: 0L }
        }
    }

    private fun fetchCreatorProducts(userID: String): List<CreatorProduct> =
        get(
            "/api/creator/get-products?user_id=$userID&page=1&tag_id=", CreatorProductsResponse.serializer(),
            host = IFDIAN_HOST
        ).data?.list.orEmpty()

    private fun postUserAction(path: String, userID: String) {
        val url = (IFDIAN_HOST + path).toHttpUrl()
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("user_id", userID)
                put("auth_token", tokenStore.token)
            }
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).header("Cookie", "auth_token=${tokenStore.token}").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 属于异常路径，W 级日志始终输出便于排查（只记路径与目标 ID，不含任何凭据）
                AppLog.w(TAG, "$path user_id=$userID -> HTTP ${response.code}，操作失败")
                error("操作失败 (${response.code})")
            }
            // 埋点：D 级调试日志，记录关注/取关操作的路径、目标用户与响应码
            AppLog.d(TAG, "$path user_id=$userID -> HTTP ${response.code}")
        }
    }

    /** 动态互动类 POST（点赞/收藏），JSON body 携带 post_id + auth_token */
    private fun postPostAction(path: String, postID: String) {
        val url = (AFDIAN_HOST + path).toHttpUrl()
        val body = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("post_id", postID)
                put("auth_token", tokenStore.token)
            }
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", "https://afdian.com/").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 时 W 级日志始终输出，提示点赞/收藏互动可能未生效
                AppLog.w(TAG, "$path post_id=$postID -> HTTP ${response.code}，操作失败")
                error("操作失败 (${response.code})")
            }
            // 埋点：D 级调试日志，记录动态互动的路径、目标动态与响应码
            AppLog.d(TAG, "$path post_id=$postID -> HTTP ${response.code}")
        }
    }

    private fun <T> get(
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        host: String = AFDIAN_HOST,
    ): T {
        val url = (host + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token).build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        // 埋点：请求发出前记录起始时间，用于统计接口耗时
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 埋点：非 2xx 属于异常路径，E 级日志始终输出（原有 error 抛错行为不变）
                AppLog.e(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("请求失败 (${response.code})")
            }
            json.decodeFromString(serializer, response.body.string()).also {
                // 埋点：D 级调试日志，记录请求路径、HTTP 状态码与总耗时（含读取响应体与解析）
                AppLog.d(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            }
        }
    }

    private fun fetchFavoriteList(path: String, unwrap: (JsonObject) -> JsonObject): List<FavoriteItem> {
        val url = ("https://afdian.com" + path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token).addQueryParameter("page", "1").build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        // 埋点：请求前记录起始时间；httpCode 暂存响应码，供 use 块结束后统一打点
        val start = System.currentTimeMillis()
        var httpCode = 0
        return client.newCall(request).execute().use { response ->
            httpCode = response.code
            if (!response.isSuccessful) {
                // 非 2xx 属于异常路径，E 级日志始终输出（原有 error 抛错行为不变）
                AppLog.e(TAG, "$path -> HTTP $httpCode（${System.currentTimeMillis() - start}ms）")
                error("收藏加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val data = root["data"]?.jsonObject ?: return@use emptyList()
            val items = data["items"]?.jsonArray ?: data["list"]?.jsonArray ?: return@use emptyList()
            items.mapNotNull { element ->
                val value = unwrap(element.jsonObject)
                FavoriteItem(
                    id = value.string("post_id") ?: value.string("user_id") ?: value.string("crowdfunding_id") ?: value.string("id") ?: return@mapNotNull null,
                    title = value.string("title") ?: value.string("name") ?: "未命名内容",
                    subtitle = value.string("preview") ?: value.string("preview_text") ?: value.string("desc") ?: value.string("doing") ?: "",
                    image = value.string("avatar") ?: value.string("cover"),
                    user = runCatching { json.decodeFromString<AfadianUser>(value.toString()) }.getOrNull()
                )
            }
        }.also { list ->
            // 埋点：D 级调试日志，记录路径、HTTP 状态码、耗时与返回条数（不含任何凭据字段）
            AppLog.d(TAG, "$path -> HTTP $httpCode（${System.currentTimeMillis() - start}ms），返回 ${list.size} 条")
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    // ── /api/my/check：轻量未读检查（无参数，返回全部未读分类计数）──
    suspend fun fetchMyCheck(): MyCheckData {
        val url = (AFDIAN_HOST + "/api/my/check").toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 非 2xx 属于异常路径，E 级日志始终输出
                AppLog.e(TAG, "/api/my/check -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("未读检查失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val ec = root["ec"]?.jsonPrimitive?.content?.toIntOrNull()
            if (ec != 200) {
                // 业务错误码走 W 级日志，便于排查 Cookie 失效等情况
                AppLog.w(TAG, "/api/my/check -> ec=$ec")
                error("未读检查失败 (ec=$ec)")
            }
            val data = root["data"]?.jsonObject ?: error("未读检查返回结构异常")
            val countObj = data["unread_count"]?.jsonObject
            MyCheckData(
                unreadMessageNum = data["unread_message_num"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                unreadComment = countObj?.get("comment")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                unreadLike = countObj?.get("like")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                unreadPrivateMsg = countObj?.get("message")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            ).also {
                // 埋点：D 级调试日志，记录各分类未读数
                AppLog.d(TAG, "/api/my/check -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），message=${it.unreadMessageNum} comment=${it.unreadComment} like=${it.unreadLike}")
            }
        }
    }
}

@Serializable data class FavoriteItem(
    val id: String, val title: String, val subtitle: String, val image: String? = null,
    val user: AfadianUser? = null,
)

@Serializable private data class AfadianAccountResponse(val ec: Int? = null, val data: AfadianAccount? = null)
@Serializable private data class AfadianProfileResponse(val ec: Int? = null, val data: AfadianProfile? = null)
@Serializable data class AfadianProfile(val user: AfadianUser = AfadianUser())
@Serializable private data class SponsoringResponse(val ec: Int? = null, val data: SponsoringPayload? = null)
@Serializable private data class SponsoringPayload(val sponsoring: List<SponsoringItem> = emptyList())
@Serializable private data class RecommendedPostsResponse(val data: PostPayload? = null)
@Serializable private data class PostPayload(
    val list: List<RecommendedPost> = emptyList(),
    val has_more: Int? = null,
)
@Serializable private data class AlbumPostsResponse(val data: AlbumPostsPayload? = null)
@Serializable private data class AlbumPostsPayload(val list: List<RecommendedPost> = emptyList(), val has_more: Int? = null)

/** 推荐帖子分页结果 */
data class RecommendedPostsPage(
    val posts: List<RecommendedPost>,
    val nextPublishSN: String,
    val hasMore: Boolean,
)

/** 收藏动态分页结果（page 游标分页） */
data class MarkedPostsPage(
    val posts: List<RecommendedPost>,
    val hasMore: Boolean,
)
/** 上一篇/下一篇动态的精简信息 */
@Serializable data class PostOldNew(
    val old: PostOldNewInfo? = null,
    val new: PostOldNewInfo? = null,
)

/** 上一篇/下一篇动态的精简信息 */
@Serializable data class PostOldNewInfo(
    val post_id: String = "",
    val title: String = "",
)

@Serializable data class RecommendedPost(
    val post_id: String = "", val title: String = "", val preview: String? = null,
    val content: String? = null, val cover: String? = null, val like_count: Int? = null,
    val comment_count: Int? = null, val user: AfadianUser = AfadianUser(),
    // 当前登录用户是否已点赞（0/1，匿名请求时不返回）
    val has_like: Int? = null,
    val pics: List<String>? = null,           // 动态图片列表
    val publish_time: Long? = null,           // 发布时间（Unix 秒）
    val publish_sn: Long? = null,             // 发布序号（用于分页游标）
    val is_public: Int? = null,               // 是否公开
    val min_price: String? = null,            // 最低价格（付费内容）
    val albums: List<PostAlbum>? = null,      // 所属相册列表
    val old_new: PostOldNew? = null,          // 上一篇/下一篇动态
) {
    val id: String get() = post_id
    val summary: String get() = (preview ?: content).orEmpty()
}

/** 动态所属相册的精简信息 */
@Serializable data class PostAlbum(
    val album_id: String = "", val title: String = "", val post_count: Int? = null,
)

@Serializable data class AfadianUser(
    val user_id: String = "", val name: String = "", val avatar: String? = null, val url_slug: String? = null,
    val cover: String? = null, val is_verified: Int? = null, val creator: AfadianCreator? = null,
)

@Serializable data class AfadianCreator(
    val doing: String? = null, val detail: String? = null, val category: AfadianCategory? = null,
)

@Serializable data class AfadianCategory(val name: String? = null)

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

/** 赞助者感谢页（get-thank-sponsors）的单条赞助者：接口只给用户资料，不含金额/计划信息 */
@Serializable data class ThankSponsor(
    val user_id: String = "", val name: String = "", val avatar: String? = null, val url_slug: String? = null,
    // 赞助者同时是创作者时的「正在创作」内容，普通用户为 null
    val doing: String? = null,
) {
    val id: String get() = user_id

    /** 转成通用用户模型，用于跳转创作者主页 */
    fun toAfadianUser() = AfadianUser(user_id = user_id, name = name, avatar = avatar, url_slug = url_slug)
}

/** 感谢页单页数据：has_more 控制翻页；totalLabel 是接口给的展示文案（如「所有」），只用于标题 */
data class ThankSponsorsPage(
    val list: List<ThankSponsor>,
    val hasMore: Boolean,
    val totalLabel: String,
)

/** get-thank-word / edit-thank-word 的响应：data 里是感谢语内容与配图（配图暂未使用） */
@Serializable private data class ThankWordResponse(
    val ec: Int? = null,
    val em: String? = null,
    val data: ThankWord? = null,
)

@Serializable private data class ThankWord(val content: String? = null, val pic: String? = null)

@Serializable data class CreatorDetail(
    val profile: AfadianUser = AfadianUser(),
    val goals: List<CreatorGoal> = emptyList(),
    val topSponsors: List<TopSponsor> = emptyList(),
    val plans: List<CreatorPlan> = emptyList(),
    val albums: List<CreatorAlbum> = emptyList(),
    val posts: List<RecommendedPost> = emptyList(),
    val products: List<CreatorProduct> = emptyList(),
    val isMarked: Boolean = false,
)

@Serializable data class CreatorGoal(
    val goal_id: String? = null, val title: String? = null, val name: String? = null,
    val current_amount: String? = null, val target_amount: String? = null,
) {
    val displayTitle: String get() = title ?: name ?: "未命名目标"
    val amountText: String get() = when {
        current_amount.isNullOrBlank() && target_amount.isNullOrBlank() -> ""
        else -> "¥${current_amount ?: "--"} / ¥${target_amount ?: "--"}"
    }
}

@Serializable data class TopSponsor(
    val user: AfadianUser? = null, val name: String? = null, val avatar: String? = null,
    val total_amount: String? = null,
) {
    val displayName: String get() = user?.name ?: name ?: "匿名赞助者"
    val avatarUrl: String? get() = user?.avatar ?: avatar
    val totalAmountText: String get() = total_amount ?: "0"
}

@Serializable data class CreatorPlan(
    val plan_id: String? = null, val name: String? = null, val title: String? = null,
    val pic: String? = null, val price: String? = null, val show_price: String? = null,
) {
    val displayName: String get() = name ?: title ?: "发电方案"
    val priceText: String get() = show_price ?: price ?: "--"
}

@Serializable data class CreatorAlbum(
    val album_id: String? = null, val title: String? = null, val name: String? = null,
    val cover: String? = null, val content: String? = null, val post_count: Int? = null,
) {
    val displayTitle: String get() = title ?: name ?: "合集"
    val postCountText: String get() = post_count?.let { "$it 篇" } ?: ""
}

@Serializable data class CreatorProduct(
    val plan_id: String? = null, val name: String? = null, val pic: String? = null,
    val price: String? = null, val show_price: String? = null, val floor_price: String? = null,
    val description: String? = null,
) {
    val displayName: String get() = name ?: "商品"
    val priceText: String get() = show_price ?: floor_price ?: price ?: "--"
    val summary: String get() = description.orEmpty()
}

@Serializable private data class UserProfileResponse(val ec: Int? = null, val data: UserProfilePayload? = null)
@Serializable private data class UserProfilePayload(val user: AfadianUser? = null)
@Serializable private data class CreatorGoalsResponse(val ec: Int? = null, val data: CreatorGoalsPayload? = null)
@Serializable private data class CreatorGoalsPayload(val list: List<CreatorGoal>? = null, val goals: List<CreatorGoal>? = null)
@Serializable private data class TopSponsorsResponse(val ec: Int? = null, val data: TopSponsorsPayload? = null)
@Serializable private data class TopSponsorsPayload(val list: List<TopSponsor>? = null)
@Serializable private data class CreatorPlansResponse(val ec: Int? = null, val data: CreatorPlansPayload? = null)
@Serializable private data class CreatorPlansPayload(val list: List<CreatorPlan>? = null, val plans: List<CreatorPlan>? = null)
@Serializable private data class CreatorAlbumsResponse(val ec: Int? = null, val data: CreatorAlbumsPayload? = null)
@Serializable private data class CreatorAlbumsPayload(val list: List<CreatorAlbum>? = null, val albums: List<CreatorAlbum>? = null)
@Serializable private data class CreatorProductsResponse(val ec: Int? = null, val data: CreatorProductsPayload? = null)
@Serializable private data class CreatorProductsPayload(val list: List<CreatorProduct>? = null)

@Serializable data class MessageDialogPage(
    val dialogs: List<MessageDialog> = emptyList(),
    val totalCount: Int = 0,
    val totalPage: Int = 1,
    val hasMore: Boolean = false
)

data class MessageConversation(
    val messages: List<MessageItem> = emptyList(),
    val hasMore: Boolean = false,
    val canSendPictureMessage: Boolean = false
)

@Serializable data class MessageDialog(
    val latest_msg_id: Int? = null,
    val unread_count: Int = 0,
    val total_count: Int? = null,
    val status: Int? = null,
    val user: AfadianUser = AfadianUser(),
    val desc: String? = null,
    val send_time: Double? = null,
    val sponsor_info: SponsorInfo? = null
) {
    val id: String get() = user.user_id
}

/** 发电明细：out_detail=我对他发电，in_detail=他对我发电 */
@Serializable data class SponsorInfo(
    val out_detail: SponsorAmount? = null,
    val in_detail: SponsorAmount? = null
)

@Serializable data class SponsorAmount(
    val all_sum_amount: String? = null,
    val current_amount: String? = null,
    val longest_expire_time: Long? = null
)

data class MessageItem(
    val type: String = "",
    val message: MessageRecord = MessageRecord()
) {
    val id: Int get() = message.msg_id ?: message.id
    val isSentByMe: Boolean get() = type == "send"
}

@Serializable
private data class MessageItemRaw(
    val type: String = "",
    val message: MessageRecordRaw = MessageRecordRaw()
) {
    fun toMessageItem() = MessageItem(
        type = type,
        message = message.toMessageRecord()
    )
}

data class MessageRecord(
    val msg_id: Int? = null,
    val id: Int = 0,
    val sender: String? = null,
    val r_status: Int? = null,
    val type: Int = 0,
    val content: MessageContent = MessageContent.Empty,
    val send_time: Double? = null
)

sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class Object(val content: MessageObjectContent) : MessageContent()
    data object Empty : MessageContent()
}

@Serializable
private data class MessageRecordRaw(
    val msg_id: Int? = null,
    val id: Int = 0,
    val sender: String? = null,
    val r_status: Int? = null,
    val type: Int = 0,
    val content: JsonElement? = null,
    val send_time: Double? = null
)

private fun MessageRecordRaw.toMessageRecord(): MessageRecord {
    val messageContent = when {
        content == null || content is JsonNull -> MessageContent.Empty
        content is JsonPrimitive && content.isString -> MessageContent.Text(content.content)
        content is JsonObject -> {
            val obj = json.decodeFromJsonElement(MessageObjectContent.serializer(), content)
            MessageContent.Object(obj)
        }
        else -> MessageContent.Empty
    }
    return MessageRecord(
        msg_id = msg_id,
        id = id,
        sender = sender,
        r_status = r_status,
        type = type,
        content = messageContent,
        send_time = send_time
    )
}

@Serializable data class MessageObjectContent(
    val out_trade_no: String? = null,
    val show_amount: String? = null,
    val total_amount: String? = null,
    val per_month: String? = null,
    val month: Int? = null,
    val remark: String? = null,
    val plan: CreatorPlan? = null,
    val sku_detail: List<MessageSKU>? = null,
    val group: MessageGroup? = null,
    val product_type: Int? = null
)

@Serializable data class MessageSKU(
    val sku_id: String? = null,
    val count: Int? = null,
    val name: String? = null,
    val pic: String? = null
) {
    val id: String get() = sku_id ?: listOfNotNull(name, pic).joinToString("-")
}

@Serializable data class MessageGroup(
    val group_id: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val content: String? = null,
    val member_count: Int? = null
)

@Serializable data class MessageFAQ(
    val id: Int = 0,
    val title: String = ""
)

@Serializable data class MessageSendRequest(
    val user_id: String,
    val type: String,
    val content: String,
    val auth_token: String?
)

@Serializable private data class MessageDialogsResponse(val ec: Int? = null, val message: String? = null, val data: MessageDialogsData? = null)
@Serializable private data class MessageDialogsData(
    val list: List<MessageDialog> = emptyList(),
    val total_count: Int? = null,
    val total_page: Int? = null,
    val has_more: Int? = null
)

@Serializable private data class MessagesResponse(val ec: Int? = null, val message: String? = null, val data: MessagesData? = null)
@Serializable private data class MessagesData(
    val list: List<MessageItemRaw> = emptyList(),
    val has_more: Int? = null,
    val can_picture_message: Int? = null
) {
    fun toMessages() = list.map { it.toMessageItem() }
}

@Serializable private data class MessageFAQResponse(val ec: Int? = null, val message: String? = null, val data: MessageFAQData? = null)
// 服务端返回的是驼峰 otherList，需要用 SerialName 映射
@Serializable private data class MessageFAQData(@SerialName("otherList") val other_list: List<MessageFAQ>? = null)

@Serializable private data class MessageSendResponse(val ec: Int? = null, val message: String? = null, val data: MessageItemRaw? = null) {
    fun toMessageItem() = data?.toMessageItem()
}

/** /api/my/check 响应：轻量级未读计数，包含评论、点赞、私信三类 */
data class MyCheckData(
    val unreadMessageNum: Int = 0,   // 未读消息总数（评论+点赞+私信）
    val unreadComment: Int = 0,      // 未读评论数
    val unreadLike: Int = 0,         // 未读点赞数
    val unreadPrivateMsg: Int = 0,   // 未读私信数
)
