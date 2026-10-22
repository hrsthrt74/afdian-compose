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
 * 按 `comment_id` 合并两批评论：保留先出现的一条，丢弃后出现的重复项。
 *
 * 两个场景共用同一套口径，避免规则各写一份后漂移：
 * - **单页内**：`hot_list` 作 existing、`list` 作 incoming —— 热门置顶，普通评论里
 *   重复下发的同一条（服务端确实会重复下发）直接丢掉；
 * - **跨页**：屏幕上已有的列表作 existing、新一页作 incoming —— 热门评论本质是
 *   「历史高赞的老评论」，翻到它所在的年代时会再次出现在 `list` 里，这里同样丢掉，
 *   让它稳定留在顶部，而不是又冒出来一遍。
 *
 * 为什么保留先出现的那条：existing 是已经在屏幕上的内容，位置和当前展示态都以它为准；
 * 同 id 时既不重复上屏，也不把条目挪到列表尾部造成滚动跳动。
 *
 * `comment_id` 为空白直接丢弃：多个空串会在 `LazyColumn` 里变成重复 key 而闪退
 * （解析层已过滤一次，这里再兜一层，防别的调用方忘记过滤）。
 *
 * 纯函数、不打日志：单测里 `AppLog` 最终落到 `android.util.Log`，未 mock 会抛异常；
 * 丢弃了多少条由调用方按需记录。
 */
internal fun mergeComments(
    existing: List<PostComment>,
    incoming: List<PostComment>,
): List<PostComment> {
    val merged = ArrayList<PostComment>(existing.size + incoming.size)
    val seen = HashSet<String>(existing.size + incoming.size)
    // 先收已有列表：既给它自身去重，也占住位置，让后面同 id 的旧页数据被丢弃
    for (comment in existing) {
        if (comment.comment_id.isBlank()) continue
        if (seen.add(comment.comment_id)) merged += comment
    }
    // 再追加新页里没见过的，保持服务端顺序
    for (comment in incoming) {
        if (comment.comment_id.isBlank()) continue
        if (seen.add(comment.comment_id)) merged += comment
    }
    return merged
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

    // 去重合并：热门置前。热门里已经有了的评论，普通评论里再下发就丢掉（保留热评那一条）
    val merged = mergeComments(hotComments, list)

    // list 为空说明已经翻到底（此时只剩热门），不能再拿游标去翻，否则会死循环
    val hasMore = list.isNotEmpty() && data.boolOf("has_more")

    // 游标取**原始 list** 的末条，与官方 oldest_publish_sn 口径一致。
    // 不能用 merged 的末条：若 list 末条恰好是热评的重复项，去重后末条会退到倒数第二条，
    // 游标变新会漏拉下一段数据。
    val nextPublishSn = list.lastOrNull()?.publish_sn?.toString().orEmpty()

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
