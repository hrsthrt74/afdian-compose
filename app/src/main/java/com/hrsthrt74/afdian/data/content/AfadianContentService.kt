package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    }

    suspend fun fetchAccount(): AfadianAccount = get("/api/my/account", AfadianAccountResponse.serializer()).data
        ?: error("账号信息加载失败")

    suspend fun fetchProfile(): AfadianProfile = get("/api/my/profile", AfadianProfileResponse.serializer()).data
        ?: error("个人信息加载失败")

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

    suspend fun unmarkCreator(userID: String) = postUserAction("/api/user/unmark", userID)

    suspend fun fetchMessageDialogs(page: Int = 1, unread: Int = 0): MessageDialogPage {
        val url = (AFDIAN_HOST + "/api/message/dialogs").toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("unread", unread.toString())
            .build()
        val request = Request.Builder().url(url).get().header("Cookie", "auth_token=${tokenStore.token}").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("消息列表加载失败 (${response.code})")
            val body = response.body.string()
            val root = try { json.parseToJsonElement(body).jsonObject } catch (_: Exception) { error("消息列表加载失败") }
            val ec = root["ec"]?.jsonPrimitive?.content?.toIntOrNull()
            if (ec != 200) error(root["message"]?.jsonPrimitive?.contentOrNull ?: "消息列表加载失败")
            val data = root["data"]?.jsonObject ?: error("消息列表加载失败")
            val list = data["list"]?.jsonArray?.mapNotNull { element ->
                runCatching { parseMessageDialog(element.jsonObject) }.getOrNull()
            }.orEmpty()
            MessageDialogPage(
                dialogs = list,
                totalCount = data["total_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: list.size,
                totalPage = data["total_page"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                hasMore = data["has_more"]?.jsonPrimitive?.content?.toIntOrNull() == 1
            )
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
            send_time = obj["send_time"]?.jsonPrimitive?.content?.toDoubleOrNull()
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
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("会话加载失败 (${response.code})")
            val body = response.body.string()
            val root = try { json.parseToJsonElement(body).jsonObject } catch (_: Exception) { error("会话加载失败") }
            val ec = root["ec"]?.jsonPrimitive?.content?.toIntOrNull()
            if (ec != 200) error(root["message"]?.jsonPrimitive?.contentOrNull ?: "会话加载失败")
            val data = root["data"]?.jsonObject ?: error("会话加载失败")
            val list = data["list"]?.jsonArray?.mapNotNull { element ->
                runCatching { parseMessageItem(element.jsonObject) }.getOrNull()
            }.orEmpty()
            MessageConversation(
                messages = list,
                hasMore = data["has_more"]?.jsonPrimitive?.content?.toIntOrNull() == 1,
                canSendPictureMessage = data["can_picture_message"]?.jsonPrimitive?.content?.toIntOrNull() == 1
            )
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
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("消息发送失败 (${response.code})")
            val result = json.decodeFromString<MessageSendResponse>(response.body.string())
            if (result.ec != 200) error(result.message ?: "消息发送失败")
            result.toMessageItem() ?: run {
                val id = (System.currentTimeMillis() / 1000).toInt()
                MessageItem(
                    type = "send",
                    message = MessageRecord(msg_id = id, id = id, r_status = 1, type = 1, content = MessageContent.Text(content), send_time = System.currentTimeMillis() / 1000.0)
                )
            }
        }
    }

    private fun fetchProfileBySlug(slug: String): AfadianUser =
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

    private fun fetchUserPosts(userID: String): List<RecommendedPost> =
        get(
            "/api/post/get-list?user_id=$userID&type=old&publish_sn=&per_page=10&group_id=&all=1&is_public=&plan_id=&title=&name=",
            RecommendedPostsResponse.serializer()
        ).data?.list.orEmpty()

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
            if (!response.isSuccessful) error("操作失败 (${response.code})")
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
                    image = value.string("avatar") ?: value.string("cover"),
                    user = runCatching { json.decodeFromString<AfadianUser>(value.toString()) }.getOrNull()
                )
            }
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
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
    val send_time: Double? = null
) {
    val id: String get() = user.user_id
}

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
@Serializable private data class MessageFAQData(val other_list: List<MessageFAQ>? = null)

@Serializable private data class MessageSendResponse(val ec: Int? = null, val message: String? = null, val data: MessageItemRaw? = null) {
    fun toMessageItem() = data?.toMessageItem()
}
