package com.hrsthrt74.afdian.ui.components.post

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.ui.components.basic.CardActionBar
import com.hrsthrt74.afdian.ui.components.basic.CardActionBarItem
import com.hrsthrt74.afdian.ui.viewmodel.PostInteractionController
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 动态操作栏：三列均分（点赞 / 评论 / 收藏）。
 *
 * 视觉骨架（均分列、图标 + 文字、触觉、点击热区）统一由 basic 的 [CardActionBar] 渲染，
 * 本组件只保留动态互动的业务部分：读写全局 [PostInteractionController] 单例、点赞数乐观 ±1。
 *
 * 点赞/收藏逻辑内置：任何页面（发现页/收藏页/个人主页/详情页）不传回调也能直接点赞收藏，
 * 状态跨页面自动同步。外部参数仅作覆盖用（默认不需要传）。
 *
 * @param post 动态数据（点赞数 / 评论数 / 服务端点赞状态）
 * @param liked 覆盖「是否已点赞」显示；不传则读全局状态
 * @param marked 覆盖「是否已收藏」显示；不传则读全局状态
 * @param onLikeToggle 覆盖点赞点击动作；不传则走全局 toggle
 * @param onMarkToggle 覆盖收藏点击动作；不传则走全局 toggle
 * @param onCommentClick 评论按钮点击回调；不传则评论列纯展示不可点。
 *   一般由 [PostCard] 从卡片点击回调派生（落点为评论区），只有详情页会直接传「滚到评论区」。
 */
@Composable
fun PostActionBar(
    post: RecommendedPost,
    liked: Boolean? = null,
    marked: Boolean? = null,
    onLikeToggle: (() -> Unit)? = null,
    onMarkToggle: (() -> Unit)? = null,
    onCommentClick: (() -> Unit)? = null,
) {
    // 全局互动状态：所有页面的操作栏共享同一份「已赞/已收藏」集合
    val interactions = PostInteractionController.get(LocalContext.current)
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
    // 外部覆盖回调优先，否则走全局 toggle（触觉由 CardActionBar 内置，这里不用包）
    val likeAction = onLikeToggle ?: { interactions.toggleLike(post.id) }
    val markAction = onMarkToggle ?: { interactions.toggleMark(post.id) }

    // 视觉与交互全部委托通用操作栏：激活列亮主色，评论列纯展示不可点
    CardActionBar(
        items = listOf(
            // ── 点赞：图标随状态在描边/实心间切换，文字显示乐观计数 ──
            CardActionBarItem(
                icon = if (likedNow) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                label = "$likeCount",
                tint = if (likedNow) MiuixTheme.colorScheme.primary else null,
                contentDescription = if (likedNow) "取消点赞" else "点赞",
                onClick = likeAction,
            ),
            // ── 评论：展示计数；onCommentClick 为空时退化成纯展示（onClick = null）──
            CardActionBarItem(
                icon = MiuixIcons.Community,
                label = "$commentCount",
                contentDescription = "评论",
                onClick = onCommentClick,
            ),
            // ── 收藏：没有计数，直接显示「收藏」二字 ──
            CardActionBarItem(
                icon = if (markedNow) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                label = "收藏",
                tint = if (markedNow) MiuixTheme.colorScheme.primary else null,
                contentDescription = if (markedNow) "取消收藏" else "收藏",
                onClick = markAction,
            ),
        ),
    )
}
