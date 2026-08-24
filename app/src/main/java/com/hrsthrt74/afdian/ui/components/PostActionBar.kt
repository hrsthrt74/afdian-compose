package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.ui.viewmodel.PostInteractionController
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 动态操作栏：三列均分布局（点赞 / 评论 / 收藏），
 * 每个动作占一个等宽 Box，图标 + 文字居中排列。
 *
 * 从 PostCard 提取出来的公共组件，动态详情页底部也复用同一套操作栏。
 *
 * 点赞/收藏逻辑内置：组件直接读写全局的 [PostInteractionController] 单例，
 * 任何页面（发现页/收藏页/个人主页/详情页）不传回调也能直接点赞收藏，
 * 状态跨页面自动同步。外部参数仅作覆盖用（默认不需要传）。
 *
 * @param post 动态数据（点赞数 / 评论数 / 服务端点赞状态）
 * @param liked 覆盖「是否已点赞」显示；不传则读全局状态
 * @param marked 覆盖「是否已收藏」显示；不传则读全局状态
 * @param onLikeToggle 覆盖点赞点击动作；不传则走全局 toggle
 * @param onMarkToggle 覆盖收藏点击动作；不传则走全局 toggle
 */
@Composable
fun PostActionBar(
    post: RecommendedPost,
    liked: Boolean? = null,
    marked: Boolean? = null,
    onLikeToggle: (() -> Unit)? = null,
    onMarkToggle: (() -> Unit)? = null,
) {
    // 全局互动状态：所有页面的操作栏共享同一份「已赞/已收藏」集合
    val interactions = PostInteractionController.get(androidx.compose.ui.platform.LocalContext.current)
    val globalLiked by interactions.likedIds.collectAsState()
    val globalMarked by interactions.markedIds.collectAsState()

    // 外部没给覆盖值时，以全局状态为准
    val likedNow = liked ?: (post.id in globalLiked)
    val markedNow = marked ?: (post.id in globalMarked)

    // 服务端返回的 like_count 已包含「我已赞」的情况（has_like==1），
    // 所以只在本地状态与服务端初始状态不一致时做 ±1 的乐观显示
    val serverLiked = post.has_like == 1
    val likeCount = (post.like_count ?: 0) + when {
        likedNow && !serverLiked -> 1
        !likedNow && serverLiked -> -1
        else -> 0
    }
    val commentCount = post.comment_count ?: 0
    // 点击操作栏时的轻触觉反馈（TextHandleMove：短促轻微，适合点赞/收藏这类高频操作）；
    // 放在组件内部，列表卡片和详情页的底栏共用同一份手感
    val haptic = LocalHapticFeedback.current

    /**
     * 先震动再触发动作（外部覆盖回调优先，否则走全局 toggle）。
     * ⚠️ 注意：返回类型是 () -> Unit，表达式体的这层大括号【就是】返回的 lambda，
     * 里面不能再包一层 {} —— 多套一层会变成「返回一个被丢弃的内层 lambda」，
     * 整个函数退化成空操作（之前点击全部失效就是这个原因）。
     */
    fun hapticThen(action: () -> Unit): () -> Unit = {
        android.util.Log.d("PostActionBar", "操作栏点击: post=${post.id}")
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }
    val likeAction = onLikeToggle ?: { interactions.toggleLike(post.id) }
    val markAction = onMarkToggle ?: { interactions.toggleMark(post.id) }

    Row(modifier = Modifier.fillMaxWidth()) {
        // ── 第 1 列：点赞 ──
        val likeIcon = if (likedNow) MiuixIcons.FavoritesFill else MiuixIcons.Favorites
        val likeTint = if (likedNow) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurfaceVariantSummary
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = hapticThen(likeAction))
                // padding 放在 clickable 之后：内边距也算进点击热区
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = likeIcon,
                    contentDescription = if (likedNow) "取消点赞" else "点赞",
                    modifier = Modifier.size(18.dp),
                    tint = likeTint,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "$likeCount", style = MiuixTheme.textStyles.footnote1, color = likeTint)
            }
        }
        // ── 第 2 列：评论 ──
        Box(
            modifier = Modifier
                .weight(1f)
                // 与两侧保持同样的内边距，保证三列高度一致
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MiuixIcons.Community,
                    contentDescription = "评论",
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$commentCount",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        // ── 第 3 列：收藏（没有计数，直接显示「收藏」二字）──
        val markIcon = if (markedNow) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder
        val markTint = if (markedNow) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurfaceVariantSummary
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = hapticThen(markAction))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = markIcon,
                    contentDescription = if (markedNow) "取消收藏" else "收藏",
                    modifier = Modifier.size(18.dp),
                    tint = markTint,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "收藏",
                    style = MiuixTheme.textStyles.footnote1,
                    color = markTint,
                )
            }
        }
    }
}

