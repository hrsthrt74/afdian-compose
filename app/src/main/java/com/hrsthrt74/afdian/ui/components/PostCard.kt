package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.AfadianCreator
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import java.text.SimpleDateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.hrsthrt74.afdian.ui.navigation.CreatorOrigin

/**
 * 单条动态卡片，参考爱发电官方 feed 布局
 *
 * @param post 动态数据
 * @param profile 创作者信息（用于显示头像和用户名）
 * @param modifier 外部修饰符，调用方可自行设置边距
 * @param onCreatorClick 点击头像/用户名的回调，传 null 则不可点击
 * @param showDoing 是否显示「正在创作……」，默认不显示
 * @param creatorOrigin 创作者主页展开动画的起点捕获器；
 *   传入时会把头部头像行的边界记录为动画起点（配合 CreatorOrigin.onClick 包装回调使用）
 */
@Composable
fun PostCard(
    post: RecommendedPost,
    profile: AfadianUser,
    modifier: Modifier = Modifier,
    onCreatorClick: (() -> Unit)? = null,
    showDoing: Boolean = false,
    creatorOrigin: CreatorOrigin? = null,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PostHeader(post = post, profile = profile, onCreatorClick = onCreatorClick, showDoing = showDoing, origin = creatorOrigin)
            PostContent(post = post, onCreatorClick = onCreatorClick)
            PostFooter(post = post)
        }
    }
}

/** 头部：用户头像、名称、发布时间 */
@Composable
private fun PostHeader(post: RecommendedPost, profile: AfadianUser, onCreatorClick: (() -> Unit)?, showDoing: Boolean, origin: CreatorOrigin?) {
    val clickableMod = if (onCreatorClick != null) Modifier.clickable(onClick = onCreatorClick) else Modifier
    Row(
        modifier = Modifier
            // 头像行是「点开创作者主页」的真实起点：持续记录它的边界供展开动画使用
            .then(origin?.trackModifier() ?: Modifier)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            model = profile.avatar,
            size = 42.dp,
            contentDescription = profile.name,
            onClick = onCreatorClick,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f).then(clickableMod)) {
            Text(
                text = profile.name.ifBlank { "创作者" },
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val doing = profile.creator?.doing
            if (showDoing && !doing.isNullOrBlank()) {
                Text(
                    text = "正在创作 $doing",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (post.publish_time != null) {
            val dateStr = remember(post.publish_time) {
                val date = Date(post.publish_time * 1000)
                val cal = java.util.Calendar.getInstance()
                val postYear = cal.apply { time = date }.get(java.util.Calendar.YEAR)
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val pattern = if (postYear == currentYear) "M/d" else "yyyy/M/d"
                SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(date)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = dateStr,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 内容区：封面图 → 标题 → 文本预览 → 图片网格 */
@Composable
private fun PostContent(post: RecommendedPost, onCreatorClick: (() -> Unit)?) {
    Column {
        // 有封面图时显示在文字之前，不再显示其他图片
        if (!post.cover.isNullOrBlank()) {
            AsyncImage(
                model = post.cover,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .background(MiuixTheme.colorScheme.disabledSecondary, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 标题
            if (post.title.isNotBlank()) {
                Text(
                    text = post.title,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            // 正文
            val summary = post.summary
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 付费内容
            val minPrice = post.min_price?.toDoubleOrNull() ?: 0.0
            if (minPrice > 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                if (summary.isBlank()) {
                    // 未解锁：显示发电按钮
                    Button(
                        onClick = { onCreatorClick?.invoke() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("发电解锁 ¥${post.min_price}")
                    }
                } else {
                    // 已解锁：提示可查看内容
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MiuixIcons.Demibold.Unlock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "你可以查看该动态的内容",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                }
            }
            // 图片，无封面图时显示 pics 网格
            if (post.cover.isNullOrBlank()) {
                val images = post.pics.orEmpty()
                if (images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PostImageGrid(images = images)
                }
            }
        }
    }
}

/** 图片网格：根据图片数量自适应布局（1张4:3 / 2~3张并排 / 4~9张3列网格） */
@Composable
private fun PostImageGrid(images: List<String>) {
    if (images.isEmpty()) return

    val cornerRadius = 8.dp
    val gap = 4.dp
    val bgShape = RoundedCornerShape(cornerRadius)

    when (images.size) {
        1 -> AsyncImage(
            model = images[0],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MiuixTheme.colorScheme.disabledSecondary, bgShape)
                .clip(bgShape),
        )
        2, 3 -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            images.forEach { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .background(MiuixTheme.colorScheme.disabledSecondary, bgShape)
                        .clip(bgShape),
                )
            }
        }
        else -> {
            val rows = images.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(MiuixTheme.colorScheme.disabledSecondary, bgShape)
                                    .clip(bgShape),
                            )
                        }
                        // 补齐不满 3 个的行，用空 Spacer 占位
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 底部：点赞数、评论数 */
@Composable
private fun PostFooter(post: RecommendedPost) {
    val likeCount = post.like_count ?: 0
    val commentCount = post.comment_count ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Favorites,
            contentDescription = "点赞",
            modifier = Modifier.size(16.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$likeCount",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = MiuixIcons.Community,
            contentDescription = "评论",
            modifier = Modifier.size(16.dp),
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

// ──────── 预览 ────────

private val previewProfile = AfadianUser(
    user_id = "preview_user",
    name = "示例创作者",
    avatar = null,
    creator = AfadianCreator(doing = "独立开发 · 设计"),
)
private val previewProfileLongName = AfadianUser(
    user_id = "preview_user2",
    name = "这是一个非常非常长的创作者名称用来测试省略号效果",
    avatar = null,
)

private fun previewMod() = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)

/** 基础：标题 + 正文 */
@Preview(showBackground = true)
@Composable
private fun PostCardBasicPreview() {
    PostCard(
        post = RecommendedPost(
            post_id = "1", title = "这是一条动态标题",
            content = "这里是动态的正文内容，可以展示多行文本预览效果。支持多行省略，第四行之后会被截断。",
            like_count = 42, comment_count = 7, publish_time = 1704067200L,
        ),
        profile = previewProfile,
        modifier = previewMod(),
    )
}

/** 封面图 + 标题 + 正文 */
@Preview(showBackground = true)
@Composable
private fun PostCardWithCoverPreview() {
    PostCard(
        post = RecommendedPost(
            post_id = "2", title = "带封面图的动态",
            content = "封面图显示在文字上方，撑满宽度。",
            cover = "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/0928a6ab8b30250dafb1e21513aef4d0_w1000_h750_s520.png",
            like_count = 5, comment_count = 1, publish_time = 1756111841L,
        ),
        profile = previewProfile,
        modifier = previewMod(),
    )
}

/** 图片网格（3张） */
@Preview(showBackground = true)
@Composable
private fun PostCardThreeImagesPreview() {
    PostCard(
        post = RecommendedPost(
            post_id = "3", title = "三张图片并排",
            content = "三张图在同一行等宽排列。",
            pics = listOf(
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/923b2cdb48939ace6a85f03b62340308_w900_h900_s210.png",
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/6f62b5f0a9029a9654a2e29f273bc26b_w900_h900_s313.png",
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/c032f97efdfa6fadd824dac2e9f30bf1_w900_h900_s205.png",
            ),
            like_count = 128, comment_count = 23, publish_time = 1704153600L,
        ),
        profile = previewProfile,
        modifier = previewMod(),
    )
}

/** 图片网格（6张，FlowRow 换行） */
@Preview(showBackground = true)
@Composable
private fun PostCardSixImagesPreview() {
    PostCard(
        post = RecommendedPost(
            post_id = "4", title = "六张图片网格",
            pics = listOf(
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/923b2cdb48939ace6a85f03b62340308_w900_h900_s210.png",
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/6f62b5f0a9029a9654a2e29f273bc26b_w900_h900_s313.png",
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/c032f97efdfa6fadd824dac2e9f30bf1_w900_h900_s205.png",
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/923b2cdb48939ace6a85f03b62340308_w900_h900_s210.png",
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/6f62b5f0a9029a9654a2e29f273bc26b_w900_h900_s313.png",
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/c032f97efdfa6fadd824dac2e9f30bf1_w900_h900_s205.png",
            ),
            like_count = 300, comment_count = 50, publish_time = 1756111841L,
        ),
        profile = previewProfile,
        modifier = previewMod(),
    )
}

/** 付费未解锁（按钮） */
@Preview(showBackground = true)
@Composable
private fun PostCardLockedPreview() {
    PostCard(
        post = RecommendedPost(
            post_id = "5", title = "付费内容 — 未解锁",
            content = "", min_price = "6.00",
            like_count = 10, comment_count = 2, publish_time = 1756111841L,
        ),
        profile = previewProfile,
        modifier = previewMod(),
        onCreatorClick = {},
    )
}

/** 付费已解锁（小字提示） */
@Preview(showBackground = true)
@Composable
private fun PostCardUnlockedPreview() {
    PostCard(
        post = RecommendedPost(
            post_id = "6", title = "付费内容 — 已解锁",
            content = "这是解锁后可以看到的正文内容。", min_price = "6.00",
            like_count = 10, comment_count = 2, publish_time = 1756111841L,
        ),
        profile = previewProfile,
        modifier = previewMod(),
    )
}

/** 显示「正在创作」 */
@Preview(showBackground = true)
@Composable
private fun PostCardShowDoingPreview() {
    PostCard(
        post = RecommendedPost(
            post_id = "7", title = "发现页会显示正在创作",
            content = "showDoing = true 时展示。", publish_time = 1756111841L,
        ),
        profile = previewProfile,
        modifier = previewMod(),
        showDoing = true,
    )
}

/** 超长用户名 + 今年日期 */
@Preview(showBackground = true)
@Composable
private fun PostCardLongNamePreview() {
    PostCard(
        post = RecommendedPost(
            post_id = "8", title = "测试长用户名省略",
            content = "用户名过长时应省略，日期不会被挤掉。", publish_time = System.currentTimeMillis() / 1000,
            like_count = 999, comment_count = 88,
        ),
        profile = previewProfileLongName,
        modifier = previewMod(),
    )
}

/** 仅图片，无标题无正文 */
@Preview(showBackground = true)
@Composable
private fun PostCardImageOnlyPreview() {
    PostCard(
        post = RecommendedPost(
            post_id = "9",
            pics = listOf(
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/923b2cdb48939ace6a85f03b62340308_w900_h900_s210.png",
                "https://pic1.afdiancdn.com/user/ba19a0909c5311eb8ebb52540025c377/common/6f62b5f0a9029a9654a2e29f273bc26b_w900_h900_s313.png",
            ),
            like_count = 3, comment_count = 0, publish_time = 1756111841L,
        ),
        profile = previewProfile,
        modifier = previewMod(),
    )
}
