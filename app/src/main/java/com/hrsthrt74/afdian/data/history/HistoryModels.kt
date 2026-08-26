package com.hrsthrt74.afdian.data.history

import com.hrsthrt74.afdian.data.content.AfadianCreator
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 历史记录支持的目标类型。
 *
 * 使用密封接口而不是把路由 JSON 直接存盘，数据层不会依赖 UI 导航实现；
 * 以后新增页面时只需增加一个目标类型和对应的路由转换即可。
 */
@Serializable
sealed interface HistoryTarget {
    val targetId: String
    val avatarUrl: String?

    @Serializable
    @SerialName("creator_detail")
    data class CreatorDetailTarget(
        val userId: String,
        val name: String = "",
        override val avatarUrl: String? = null,
        val urlSlug: String? = null,
        val coverUrl: String? = null,
        val doing: String? = null,
    ) : HistoryTarget {
        override val targetId: String get() = userId
    }

    @Serializable
    @SerialName("post_detail")
    data class PostDetailTarget(
        val postId: String,
        val title: String = "",
        val preview: String? = null,
        val creator: CreatorSnapshot = CreatorSnapshot(),
    ) : HistoryTarget {
        override val targetId: String get() = postId
        override val avatarUrl: String? get() = creator.avatarUrl
    }
}

/** 动态历史记录里需要保存的最小创作者快照，避免把整篇正文写入 DataStore。 */
@Serializable
data class CreatorSnapshot(
    val userId: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val urlSlug: String? = null,
    val coverUrl: String? = null,
    val doing: String? = null,
) {
    fun toAfadianUser(): AfadianUser = AfadianUser(
        user_id = userId,
        name = name,
        avatar = avatarUrl,
        url_slug = urlSlug,
        cover = coverUrl,
        creator = doing?.let { AfadianCreator(doing = it) },
    )

    companion object {
        fun from(user: AfadianUser) = CreatorSnapshot(
            userId = user.user_id,
            name = user.name,
            avatarUrl = user.avatar,
            urlSlug = user.url_slug,
            coverUrl = user.cover,
            doing = user.creator?.doing,
        )
    }
}

/** 一条本地浏览记录；key 用类型 + ID，保证不同创作者不会互相覆盖。 */
@Serializable
data class HistoryEntry(
    val target: HistoryTarget,
    val visitedAt: Long,
) {
    val key: String get() = when (target) {
        is HistoryTarget.CreatorDetailTarget -> "creator:${target.userId}"
        is HistoryTarget.PostDetailTarget -> "post:${target.postId}"
    }

    val title: String
        get() = when (val value = target) {
            is HistoryTarget.CreatorDetailTarget -> value.name.ifBlank { "创作者主页" }
            is HistoryTarget.PostDetailTarget -> value.creator.name
                .takeIf { it.isNotBlank() }
                ?.let { "$it 的动态" }
                ?: "动态详情"
        }

    val summary: String
        get() = when (val value = target) {
            is HistoryTarget.CreatorDetailTarget -> value.doing?.takeIf { it.isNotBlank() } ?: "创作者主页"
            is HistoryTarget.PostDetailTarget -> value.title.ifBlank { value.preview.orEmpty() }.ifBlank { "动态详情" }
        }

    companion object {
        fun from(user: AfadianUser) = HistoryEntry(
            target = HistoryTarget.CreatorDetailTarget(
                userId = user.user_id,
                name = user.name,
                avatarUrl = user.avatar,
                urlSlug = user.url_slug,
                coverUrl = user.cover,
                doing = user.creator?.doing,
            ),
            visitedAt = 0L,
        )

        fun from(post: RecommendedPost) = HistoryEntry(
            target = HistoryTarget.PostDetailTarget(
                postId = post.post_id,
                title = post.title,
                preview = post.preview ?: post.content,
                creator = CreatorSnapshot.from(post.user),
            ),
            visitedAt = 0L,
        )
    }
}

/** 便于历史条目重新构造现有页面路由所需的数据。 */
fun HistoryTarget.CreatorDetailTarget.toAfadianUser() = AfadianUser(
    user_id = userId,
    name = name,
    avatar = avatarUrl,
    url_slug = urlSlug,
    cover = coverUrl,
    creator = doing?.let { AfadianCreator(doing = it) },
)

fun HistoryTarget.PostDetailTarget.toRecommendedPost() = RecommendedPost(
    post_id = postId,
    title = title,
    preview = preview,
    user = creator.toAfadianUser(),
)
