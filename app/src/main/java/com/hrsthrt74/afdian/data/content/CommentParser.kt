package com.hrsthrt74.afdian.data.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * 评论接口解析。做成顶层 `internal` 函数便于单元测试（对齐 `MessageParser.kt` 的做法）。
 *
 * 这里刻意不打任何日志：`AppLog` 最终会落到 `android.util.Log`，
 * 单元测试里未 mock 会直接抛异常（与 MessageParser 保持同一约定）。
 * 请求侧的耗时/条数日志由 `FeedService` 负责。
 *
 * 官方网页端的合并规则（`get_comment` 实现）：`hot_list` 拼接在 `list` 之前，
 * 下一页游标取整个 `comment_list` 的最后一条 `publish_sn`（即最新一页 `list` 的末条）。
 */
private val parserJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/**
 * 解析 `/api/comment/get-list` 与 `/api/comment/get-sub-list` 的响应。
 * 两个接口返回结构一致（`data.list` + `data.has_more`），只有主列表会带 `hot_list`。
 *
 * 坏条目（缺 comment_id / 结构不对）直接跳过并记 W 级日志，不让单条脏数据打断整页。
 */
internal fun parseCommentPage(root: JsonObject): CommentListPage {
    val data = root["data"]?.jsonObject ?: return CommentListPage()

    // 热门评论：只在首屏（游标为空、服务层拼了 hot=1）才下发，不带分页游标语义。
    // 照官方网页端做法前置合并；解析层对「key 缺失 / 重复下发」都要能兜住。
    val hotComments = data["hot_list"]?.let { parseComments(it) }.orEmpty()
    val list = data["list"]?.let { parseComments(it) }.orEmpty()

    // 去重合并：热门置前。理论上两者不重叠，个别情况下服务端可能重复下发，这里兜一层
    val seen = mutableSetOf<String>()
    val merged = (hotComments + list).filter { seen.add(it.comment_id) }

    // list 为空说明已经翻到底（此时只剩热门），不能再拿游标去翻，否则会死循环
    val hasMore = list.isNotEmpty() && data.boolOf("has_more")

    // 游标取合并后的末条，与官方 oldest_publish_sn 口径一致
    val nextPublishSn = merged.lastOrNull()?.publish_sn?.toString().orEmpty()

    return CommentListPage(
        comments = merged,
        hasMore = hasMore,
        nextPublishSn = nextPublishSn,
    )
}

/** 把一个 JSON 数组解析成评论列表，跳过解析失败的条目 */
private fun parseComments(element: JsonElement?): List<PostComment> =
    (element as? JsonArray).orEmpty().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val comment = runCatching {
            parserJson.decodeFromJsonElement(PostComment.serializer(), obj)
        }.getOrNull()
        if (comment == null || comment.comment_id.isBlank()) null else comment
    }

/** 宽松取布尔：官方 has_more 是 int（0/1），也兼容 true/false */
private fun JsonObject.boolOf(key: String): Boolean =
    when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull?.let { it == "1" || it == "true" } ?: false
        else -> false
    }

/**
 * 评论点赞数的显示值：服务端返回的 like_count 已经包含「我已赞」的情况，
 * 所以只有本地交互态与服务端初始态不一致时才做 ±1 的乐观补偿
 * （与 `PostActionBar` 对动态点赞数的处理完全同一套口径）。
 *
 * @param serverCount 服务端原始 like_count
 * @param likedNow 本地交互态判定的「当前是否已赞」
 * @param serverLiked 服务端返回的 has_like == 1
 */
internal fun displayLikeCount(serverCount: Int?, likedNow: Boolean, serverLiked: Boolean): Int =
    (serverCount ?: 0) + when {
        likedNow && !serverLiked -> 1
        !likedNow && serverLiked -> -1
        else -> 0
    }
