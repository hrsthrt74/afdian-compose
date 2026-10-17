package com.hrsthrt74.afdian.ui.components.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrsthrt74.afdian.data.content.PostComment
import com.hrsthrt74.afdian.data.content.SubReplyState
import com.hrsthrt74.afdian.data.content.displayLikeCount
import com.hrsthrt74.afdian.data.content.isPostAuthor
import com.hrsthrt74.afdian.data.content.replyTargetName
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 楼中楼「回复某人」前缀里的图标占位符。
 *
 * 用 U+FFFC（占位符字符）作为 inlineContent 的 key，保证不会和评论正文里的字符冲突。
 */
private const val REPLY_ICON_KEY = "\uFFFC"

/**
 * 楼中楼「回复某人」前缀图标的边长。
 *
 * 用 TextUnit（sp）而不是 dp：Compose 的 [Placeholder] 只接受 sp / em。
 * 这个值要跟正文 footnote1（13sp）接近，图标才和文字大小协调。
 */
private val REPLY_ICON_SIZE = 13.sp

/**
 * 「展开更多 / 加载更多」这一行取完后淡出的时长（毫秒）。
 *
 * 和楼中楼展开动画并行，取一个短一点的时长：慢吞吞地淡出会让人觉得点了没反应，
 * 又不能让它在展开过程中还占着原来那么高。
 */
private const val REPLY_ROW_EXIT_MS = 180

/**
 * 评论区标题行：「评论 N 条」。
 *
 * 单独抽出来是因为它同时充当详情页的**滚动锚点**：
 * 列表页点评论按钮进来时滚到这一行停住（而不是滚到评论区最底部）。
 */
@Composable
fun CommentSectionHeader(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row( modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp) ) {
        Text(
            text = "评论",
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline()
        )
        Spacer(Modifier.width(8.dp))
        // 条数用次要色小字，跟正文拉开层级
        Text(
            text = "$count 条",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.alignByBaseline()
        )
    }
}

/**
 * 一条根评论（含其楼中楼），整体包在一张 Card 里。
 *
 * 版式（自上而下、自左而右）：
 * - 左：头像（高度约两行正文）
 * - 中：昵称（+ 作者标签）→ 评论正文 → 时间
 * - 右：点赞竖排（上图标、下计数）
 * - 下方：楼中楼回复与「展开更多回复」，与母评论共用这张 Card
 *
 * 本轮只读：没有输入框与发送入口。点卡片是「回复」的预留入口
 * （[onReply]），发评论 / 回复接入后由上层打开输入弹层。
 *
 * @param comment 评论数据
 * @param postAuthorId 动态作者的 user_id（用于给作者本人的评论/楼中楼打「作者」标签）
 * @param likedIds 本地交互态判定的「已赞评论」集合（根评论与楼中楼共用一份）
 * @param replyState 该评论的「展开更多回复」状态；null 表示用户还没点过展开
 * @param onLikeToggle 点击点赞，参数为评论 id
 * @param onReply 点击卡片（回复入口）
 * @param onExpandReplies 点击「展开更多回复」（单向，展开后不再收起）
 * @param onLoadMoreReplies 回复翻到底时点击「加载更多回复」
 * @param onSubReply 点击某条楼中楼回复（回复到它本人）
 */
@Composable
fun CommentCard(
    comment: PostComment,
    postAuthorId: String,
    likedIds: Set<String>,
    replyState: SubReplyState?,
    onLikeToggle: (String) -> Unit,
    onReply: () -> Unit,
    onExpandReplies: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    modifier: Modifier = Modifier,
    onCreatorClick: (() -> Unit)? = null,
    onSubReply: ((String) -> Unit)? = null,
) {
    // ⚠️ 没点过「展开」时 replyState 为 null，但只要 sub_comment_has_more == 1
    //    展开行就必须画出来，否则用户没有入口去展开（这是可展开的唯一触发点）。
    //    展开之后 attempted 置位且不再回退，楼中楼就一直显示。
    val showSubs = comment.sub_comments.isNotEmpty() || replyState?.attempted == true

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                // ── 母评论主体：头像 / 昵称 / 正文 / 时间 / 点赞 ──
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 评论者头像，高度约两行正文（正文 body2 = 14sp），点击进其主页
                    Avatar(
                        model = comment.user.avatar,
                        size = 36.dp,
                        contentDescription = comment.user.name,
                        onClick = onCreatorClick,
                    )
                    Spacer(Modifier.width(8.dp))    // 头像 <-> 正文
                    Column(modifier = Modifier.weight(1f)) {
                        // 昵称 + 「作者」标签同一行。与楼中楼同理：Tag 不压缩，
                        // 昵称超长时省略，避免长昵称把标签挤掉。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = comment.user.name.ifBlank { "匿名用户" },
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isPostAuthor(postAuthorId, comment)) {
                                Spacer(Modifier.width(4.dp))
                                PostAuthorTag()
                            }
                        }
                        // 评论正文
                        if (comment.content.isNotBlank()) {
                            Text(
                                text = comment.content,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 点卡片 = 回复入口（发评论接入后由上层开输入弹层）
                                    .clickable(onClick = onReply)
                                    .padding(top = 4.dp),
                            )
                        }
                        // 时间放在正文下面单独一行
                        Text(
                            text = formatCommentTime(comment.publish_time),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // ── 点赞：右对齐竖排，上图标（默认尺寸）下计数 ──
                    LikeButton(
                        likeCount = displayLikeCount(comment.like_count, comment.comment_id in likedIds, comment.has_like == 1),
                        liked = comment.comment_id in likedIds,
                        onClick = { onLikeToggle(comment.comment_id) },
                    )
                }

                // ── 楼中楼：与母评论共用这张 Card，靠左边框 + 缩进区分层级 ──
                // 展开是单向的，所以 AnimatedVisibility 的 visible 只会在第一次点「展开」时
                // 由 false 变 true，之后永远为真——只需要处理进入动画即可。
                // animateContentSize 让新追加的回复也带着卡片一起平滑长高，而不是突然顶下去。
                AnimatedVisibility(
                    visible = showSubs,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    modifier = Modifier.animateContentSize(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        comment.sub_comments.forEach { sub ->
                            SubCommentRow(
                                comment = sub,
                                replyTarget = replyTargetName(comment, sub),
                                liked = sub.comment_id in likedIds,
                                isPostAuthor = isPostAuthor(postAuthorId, sub),
                                onLikeClick = { onLikeToggle(sub.comment_id) },
                                onClick = onSubReply?.let { cb -> { cb(sub.comment_id) } },
                            )
                        }
                        replyState?.extra?.forEach { sub ->
                            SubCommentRow(
                                comment = sub,
                                replyTarget = replyTargetName(comment, sub),
                                liked = sub.comment_id in likedIds,
                                isPostAuthor = isPostAuthor(postAuthorId, sub),
                                onLikeClick = { onLikeToggle(sub.comment_id) },
                                onClick = onSubReply?.let { cb -> { cb(sub.comment_id) } },
                            )
                        }
                    }
                }

                // 「展开更多回复」与「加载更多回复」；拉完了让这一行淡出退场。
                // ⚠️ 不能直接 if 掉：请求完成那一帧 hasMore 变 false，行会瞬间消失，
                //    卡片高度先突变变矮，然后楼中楼才开始展开，看起来像抽了一下。
                //    这里用 fadeOut + shrinkVertically 退场，高度收拢和楼中楼展开并行推进。
                if (comment.sub_comment_has_more == 1) {
                    val showReplyRow = replyState?.let {
                        // 已请求过且没有更多、也不在加载中 → 收起来（走退场动画）
                        !(it.attempted && !it.hasMore && !it.loading)
                    } ?: true
                    AnimatedVisibility(
                        visible = showReplyRow,
                        enter = fadeIn(),
                        exit = fadeOut(animationSpec = tween(REPLY_ROW_EXIT_MS)) +
                            shrinkVertically(animationSpec = tween(REPLY_ROW_EXIT_MS)),
                    ) {
                        ReplyExpandRow(
                            attempted = replyState?.attempted == true,
                            loading = replyState?.loading == true,
                            onExpand = onExpandReplies,
                            onLoadMore = onLoadMoreReplies,
                        )
                    }
                }
            }
        }
    }
}

/** 「作者」小标签：主色淡底 + 主色文字，标出这条评论是动态作者本人回复的 */
@Composable
private fun PostAuthorTag() {
    Text(
        text = "作者",
        style = MiuixTheme.textStyles.footnote2,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * 点赞按钮：右对齐竖排，上图标（默认尺寸）下计数。
 */
@Composable
private fun LikeButton(
    likeCount: Int,
    liked: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // 图标缩放：点赞/取消时先「按瘪」到 0.75 再弹回 1，带一点回弹。
    // 用 Animatable 而不是 animateFloatAsState：这里要的是一次性的弹跳，
    // 而不是随 liked 持续保持某个放大比例。
    val iconScale = remember { Animatable(1f) }
    // 记录上一次的点赞态：LaunchedEffect 首帧也会执行，靠它跳过
    // 「滚动复用 / 首次进入页面」时的误触发，只在真正切换时播动画。
    var lastLiked by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked == lastLiked) return@LaunchedEffect
        lastLiked = liked
        iconScale.snapTo(0.75f)
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    Column(
        modifier = Modifier.clickable(
            interactionSource = null,
            indication = null,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (liked) MiuixIcons.FavoritesFill else MiuixIcons.Demibold.Favorites,
            contentDescription = if (liked) "取消点赞" else "点赞",
            tint = if (liked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions,
            // 缩放只作用于图标本身，下面的计数文字不跟着抖
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$likeCount",
            style = MiuixTheme.textStyles.footnote2,
            color = if (liked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/**
 * 楼中楼回复行：左缩进 + 小头像 + 右侧竖排点赞，字号比母评论轻一号。
 *
 * @param isPostAuthor 这条回复是动态作者本人发的（昵称旁打「作者」标签，与母评论一致）
 */
@Composable
private fun SubCommentRow(
    comment: PostComment,
    replyTarget: String?,
    liked: Boolean,
    isPostAuthor: Boolean,
    onLikeClick: () -> Unit,
    onClick: (() -> Unit)?,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Avatar(
            model = comment.user.avatar,
            size = 24.dp,
            contentDescription = comment.user.name,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 昵称 +「作者」标签 + 时间挤在同一行（为了紧凑）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user.name.ifBlank { "匿名用户" },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isPostAuthor) {
                    Spacer(Modifier.width(4.dp))
                    PostAuthorTag()
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatCommentTime(comment.publish_time),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    maxLines = 1,
                )
            }
            if (comment.content.isNotBlank()) {
                // 楼中楼
                val bodyStyle = MiuixTheme.textStyles.footnote1
                if (replyTarget != null) {
                    Text(
                        text = buildAnnotatedString {
                            appendInlineContent(REPLY_ICON_KEY, "$REPLY_ICON_KEY ") // 图标 ↳
                            withStyle(SpanStyle(color = MiuixTheme.colorScheme.onSurfaceVariantActions)) {
                                append(" ") // 手动加padding
                                append(replyTarget)
                                append("：")
                            }
                            append(comment.content) // 内容
                        },
                        inlineContent = mapOf(
                            REPLY_ICON_KEY to InlineTextContent(
                                placeholder = Placeholder(
                                    width = REPLY_ICON_SIZE,
                                    height = REPLY_ICON_SIZE,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                                ),
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Demibold.Undo,
                                    contentDescription = "回复",
                                    modifier = Modifier.rotate(180f),   // Undo 旋转 180° 变成 ↳
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                        ),
                        style = bodyStyle,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                            .padding(top = 4.dp),
                    )
                } else {
                    Text(
                        text = comment.content,
                        style = bodyStyle,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                            .padding(top = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        LikeButton(
            likeCount = displayLikeCount(comment.like_count, liked, comment.has_like == 1),
            liked = liked,
            onClick = onLikeClick,
        )
    }
}

/**
 * 「展开/加载更多回复」两态（显示与否由外层 [AnimatedVisibility] 管，这里只管文案）：
 * - 没展开过：显示「展开更多」，点它把整条楼中楼展开（**不可再收起**）
 * - 展开后还有更多：显示「加载更多」，点击继续翻页
 * - 取完了：外层把它淡出。这里**不做**「取完就 return」——否则退场动画播放时内容已经为空，
 *   看起来还是硬删除，卡片高度照样会跳。
 *
 * @param attempted 是否已经请求过一次（服务端返回空或失败也算），用来区分「还没点」和「点完了」
 */
@Composable
private fun ReplyExpandRow(
    attempted: Boolean,
    loading: Boolean,
    onExpand: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val label = when {
        loading -> "加载中…"
        !attempted -> "展开更多"
        else -> "加载更多"
    }
    Text(
        text = label,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        modifier = Modifier
            .clickable(
                enabled = !loading,
                interactionSource = null,
                indication = null,
                onClick = if (attempted) onLoadMore else onExpand
            )
            .padding(start = 44.dp, top = 12.dp)
            .fillMaxWidth(),    // 整行可点
    )
}

/**
 * 相对时间格式化：刚刚 / N 分钟前 / N 小时前 / N 天前 / M 月 D 日。
 *
 * 评论流时间跨度很大（实测有几个月前的），纯时间戳可读性差；
 * 超过 30 天回落到绝对日期，避免「180 天前」这种无信息量的表达。
 */
internal fun formatCommentTime(epochSeconds: Long?): String {
    val ts = epochSeconds?.takeIf { it > 0 } ?: return ""
    val diff = System.currentTimeMillis() / 1000 - ts
    val minute = 60L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 30 * day -> "${diff / day} 天前"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts * 1000 }
            "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
        }
    }
}
