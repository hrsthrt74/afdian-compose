package com.hrsthrt74.afdian.data.content

import kotlinx.serialization.Serializable

/**
 * 单条评论，来自 `/api/comment/get-list` 与 `/api/comment/get-sub-list`。
 *
 * 字段名与官方网页端结构保持一致（服务端就是这套 snake_case），
 * 只映射评论区渲染需要的字段，其余（status / can_edit / block 等）一律忽略。
 *
 * @param has_like 服务端真实点赞态；未点赞时返回 JSON `null`，所以是 Int? 而不是 Boolean。
 *                展示计数时用它做基准（见 PostActionBar 同款口径）。
 * @param publish_sn 游标字段：分页时传「当前最旧一条」的 publish_sn，服务端返回严格早于它的数据。
 * @param sub_comments 服务端内嵌的少量楼中楼回复（通常前几条）。
 * @param sub_comment_has_more 1 = 还有更多回复未内嵌，需要再打 `/api/comment/get-sub-list`。
 */
@Serializable
data class PostComment(
    val comment_id: String = "",
    val post_id: String = "",
    val user_id: String = "",
    // 评论正文是纯文本，不含 HTML，页面直接用 Text 渲染
    val content: String = "",
    val like_count: Int? = null,
    val has_like: Int? = null,
    val publish_time: Long? = null,
    val publish_sn: Long? = null,
    val can_edit: Int? = null,
    val sub_comment_has_more: Int? = null,
    val user: AfadianUser = AfadianUser(),
    // 楼中楼专属：这条回复是回给谁的。服务端只在 sub_comments 里下发（根评论没有该字段，
    // 这时是空对象）。页面据此在正文前渲染「↩ 被回复者」，让楼中楼的对话关系一眼可辨。
    val reply_user: AfadianUser = AfadianUser(),
    val sub_comments: List<PostComment> = emptyList(),
) {
    val id: String get() = comment_id
}

/**
 * 楼中楼正文前要标出的「回复对象」昵称，返回 null 表示这一条不标。
 *
 * 三种情况不标：
 * - 服务端没下发 `reply_user`；
 * - `reply_user` 缺 id 或昵称，标出来也没信息量；
 * - **回的是楼主**——楼中楼天然就挂在母评论下，再标一次纯属冗余，
 *   楼主昵称在卡片顶部已经有了。
 *
 * 其余情况才在正文前渲染「↩ 昵称」。放在数据层是为了能脱离 Compose 单测这几条分支
 * （单测源码集不该为了测一个纯函数去 import UI 包）。
 */
internal fun replyTargetName(root: PostComment, sub: PostComment): String? {
    val targetId = sub.reply_user.user_id
    val targetName = sub.reply_user.name
    if (targetId.isBlank() || targetName.isBlank()) return null
    // 比对 root.user 而不是 root 顶层那个 user_id：页面上「楼主」渲染的就是 root.user，
    // 顶层 user_id 在部分响应（收藏接口等）里可能缺失，拿它比会漏判。
    if (targetId == root.user.user_id) return null
    return targetName
}

/**
 * 评论者是不是动态作者本人（页面据此打「作者」标签）。
 *
 * 两边 `user_id` 都非空且相等才算——空串 == 空串 会把「都没有 user_id」的匿名评论
 * 全部误判成作者。
 */
internal fun isPostAuthor(authorId: String, comment: PostComment): Boolean {
    if (authorId.isBlank()) return false
    return comment.user.user_id == authorId
}

/**
 * 评论分页结果（主列表与楼中楼回复共用同一套结构，官方两个接口返回字段一致）。
 *
 * @param comments 已把 `hot_list` 前置合并的评论列表，热门在最前（热门本身不打标签，
 *   只是排序靠前）。
 * @param nextPublishSn 下一页游标，取自合并后最旧一条的 publish_sn。
 * @param hasMore 是否还有下一页（官方 has_more == 1）。
 */
data class CommentListPage(
    val comments: List<PostComment> = emptyList(),
    val hasMore: Boolean = false,
    val nextPublishSn: String = "",
)

/**
 * 某条根评论的「展开更多回复」状态。
 *
 * 展开是**单向**的：点开之后就一直显示，没有「收起」——收起一条已经读过的楼中楼没有意义，
 * 反而让人以为内容被藏起来了。
 *
 * @param expanded 是否处于展开态（收起时不渲染 extra 里的条目）。
 * @param extra 通过 get-sub-list 追加拉到的回复，按追加顺序排列。
 * @param hasMore 这些回复是否还有更早的（官方按 publish_sn 游标往后翻）。
 * @param loading 正在拉取，避免连点重复请求。
 * @param attempted 是否已经成功请求过至少一次（服务端返回空也算）：为 true 后这一行不再显示。
 *   请求失败**不**置位，这样用户还能再点一次重试。
 */
data class SubReplyState(
    val extra: List<PostComment> = emptyList(),
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val attempted: Boolean = false,
)