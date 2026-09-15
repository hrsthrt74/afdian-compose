package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 私信领域的数据服务。
 *
 * 这里集中处理会话列表、会话消息、FAQ 和赞助关系。消息接口的 content
 * 字段会根据消息类型返回字符串、订单对象或群邀请对象，因此不再让主内容服务
 * 同时承担请求编排和消息 JSON 解析两类职责。
 */
internal class MessageService(
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
        const val AFDIAN_HOST = "https://afdian.com"
        const val IFDIAN_HOST = "https://ifdian.net"
        const val TAG = "MessageService"
    }

    suspend fun fetchMessageDialogs(page: Int = 1, unread: Int = 0): MessageDialogPage {
        val url = (AFDIAN_HOST + "/api/message/dialogs").toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("unread", unread.toString())
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "/api/message/dialogs page=$page unread=$unread -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("消息列表加载失败 (${response.code})")
            }
            val root = try {
                json.parseToJsonElement(response.body.string()).jsonObject
            } catch (_: Exception) {
                AppLog.w(TAG, "/api/message/dialogs page=$page 响应体解析失败")
                error("消息列表加载失败")
            }
            val ec = root["ec"]?.jsonPrimitive?.content?.toIntOrNull()
            if (ec != 200) {
                AppLog.w(TAG, "/api/message/dialogs page=$page 业务失败 ec=$ec")
                error(root.messageErrorText() ?: "消息列表加载失败")
            }
            val data = root["data"]?.jsonObject ?: error("消息列表加载失败")
            val list = data["list"]?.jsonArray?.mapNotNull { element ->
                runCatching { parseMessageDialog(element.jsonObject) }.getOrNull()
            }.orEmpty()
            MessageDialogPage(
                dialogs = list,
                totalCount = data["total_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: list.size,
                totalPage = data["total_page"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                hasMore = data["has_more"]?.jsonPrimitive?.content?.toIntOrNull() == 1,
            ).also {
                AppLog.d(TAG, "/api/message/dialogs page=$page unread=$unread -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${list.size} 条会话，total_page=${it.totalPage}，has_more=${it.hasMore}")
            }
        }
    }

    suspend fun fetchMessages(userID: String, type: String = "old", messageID: String = ""): MessageConversation {
        val url = (AFDIAN_HOST + "/api/message/messages").toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .addQueryParameter("user_id", userID)
            .addQueryParameter("type", type)
            .addQueryParameter("message_id", messageID)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "/api/message/messages user_id=$userID type=$type message_id=$messageID -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("会话加载失败 (${response.code})")
            }
            val root = try {
                json.parseToJsonElement(response.body.string()).jsonObject
            } catch (_: Exception) {
                AppLog.w(TAG, "/api/message/messages user_id=$userID type=$type 响应体解析失败")
                error("会话加载失败")
            }
            val ec = root["ec"]?.jsonPrimitive?.content?.toIntOrNull()
            if (ec != 200) {
                AppLog.w(TAG, "/api/message/messages user_id=$userID type=$type 业务失败 ec=$ec")
                error(root.messageErrorText() ?: "会话加载失败")
            }
            val data = root["data"]?.jsonObject ?: error("会话加载失败")
            val list = data["list"]?.jsonArray?.mapNotNull { element ->
                runCatching { parseMessageItem(element.jsonObject) }.getOrNull()
            }.orEmpty()
            MessageConversation(
                messages = list,
                hasMore = data["has_more"]?.jsonPrimitive?.content?.toIntOrNull() == 1,
                canSendPictureMessage = data["can_picture_message"]?.jsonPrimitive?.content?.toIntOrNull() == 1,
            ).also {
                AppLog.d(TAG, "/api/message/messages user_id=$userID type=$type message_id=$messageID -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），返回 ${list.size} 条消息，has_more=${it.hasMore}")
            }
        }
    }

    suspend fun fetchSponsorInfo(userID: String): SponsorInfo {
        val response = get(
            "/api/user/sponsor-info?user_id=$userID",
            MessageServiceSponsorInfoResponse.serializer(),
            host = IFDIAN_HOST,
        )
        if (response.ec != 200) error(response.em ?: response.message ?: "赞助详情加载失败")
        return response.data?.sponsor_info ?: SponsorInfo()
    }

    suspend fun fetchFAQs(userID: String): List<MessageFAQ> {
        val response = get("/api/faq/list-faq?user_id=$userID", MessageServiceFaqResponse.serializer())
        if (response.ec != 200) error(response.em ?: response.message ?: "快捷回复加载失败")
        return response.data?.other_list.orEmpty()
    }

    suspend fun sendMessage(userID: String, content: String): MessageItem {
        val body = json.encodeToString(
            MessageSendRequest.serializer(),
            MessageSendRequest(user_id = userID, type = "1", content = content, auth_token = tokenStore.token),
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$IFDIAN_HOST/api/message/send")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request, allowFallback = false).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "/api/message/send user_id=$userID len=${content.length} -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("消息发送失败 (${response.code})")
            }
            val result = parseSendResponse(response.body.string())
            if (result.ec != 200) {
                AppLog.w(TAG, "/api/message/send user_id=$userID len=${content.length} 业务失败 ec=${result.ec}")
                error(result.errorMessage ?: "消息发送失败")
            }
            AppLog.d(TAG, "/api/message/send user_id=$userID len=${content.length} -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            result.item ?: localTextMessage(content)
        }
    }

    suspend fun askFaq(userID: String, faqId: Int): MessageItem {
        val body = "id=$faqId&user_id=$userID"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder()
            .url("$IFDIAN_HOST/api/faq/ask")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        return http.execute(request, allowFallback = false).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "/api/faq/ask user_id=$userID faqId=$faqId -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("快捷提问发送失败 (${response.code})")
            }
            val result = parseSendResponse(response.body.string())
            if (result.ec != 200) {
                AppLog.w(TAG, "/api/faq/ask user_id=$userID faqId=$faqId 业务失败 ec=${result.ec}")
                error(result.errorMessage ?: "快捷提问发送失败")
            }
            AppLog.d(TAG, "/api/faq/ask user_id=$userID faqId=$faqId -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            val now = (System.currentTimeMillis() / 1000.0)
            MessageItem(
                type = "send",
                message = MessageRecord(
                    msg_id = now.toInt(),
                    id = now.toInt(),
                    r_status = 1,
                    type = 1,
                    content = MessageContent.Text(""),
                    send_time = now,
                ),
            )
        }
    }

    private fun localTextMessage(content: String): MessageItem {
        val now = (System.currentTimeMillis() / 1000.0)
        return MessageItem(
            type = "send",
            message = MessageRecord(
                msg_id = now.toInt(),
                id = now.toInt(),
                r_status = 1,
                type = 1,
                content = MessageContent.Text(content),
                send_time = now,
            ),
        )
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
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("请求失败 (${response.code})")
            }
            json.decodeFromString(serializer, response.body.string()).also {
                AppLog.d(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            }
        }
    }

}

@Serializable
private data class MessageServiceFaqResponse(
    val ec: Int? = null,
    val em: String? = null,
    val message: String? = null,
    val data: MessageServiceFaqData? = null,
)

@Serializable
private data class MessageServiceFaqData(
    @SerialName("otherList") val other_list: List<MessageFAQ>? = null,
)

@Serializable
private data class MessageServiceSponsorInfoResponse(
    val ec: Int? = null,
    val em: String? = null,
    val message: String? = null,
    val data: MessageServiceSponsorInfoData? = null,
)

@Serializable
private data class MessageServiceSponsorInfoData(val sponsor_info: SponsorInfo? = null)
