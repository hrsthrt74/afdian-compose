package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.RecommendedPost
import com.hrsthrt74.afdian.ui.navigation.CreatorOrigin
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 动态卡片头部：创作者头像、名称、「正在创作」、发布日期。
 *
 * 从 PostCard 中提取出来的公共组件，动态详情页也复用同一套头部样式。
 *
 * @param post 动态数据（用于显示发布时间）
 * @param profile 创作者信息（头像 + 用户名 + 简介）
 * @param onCreatorClick 点击头像/名称的回调，传 null 则不可点击
 * @param showDoing 是否显示「正在创作……」一行，默认不显示
 * @param origin 「主页展开」过渡动画的起点捕获器；
 *   传入时会持续记录头像行的边界作为动画起点
 */
@Composable
fun PostHeader(
    post: RecommendedPost,
    profile: AfadianUser,
    modifier: Modifier = Modifier,
    onCreatorClick: (() -> Unit)? = null,
    showDoing: Boolean = false,
    origin: CreatorOrigin? = null,
) {
    // 点击特效挂在整行上：头像、名称、日期区域任意位置点击都进入创作者主页
    val clickableMod = if (onCreatorClick != null) Modifier.clickable(onClick = onCreatorClick) else Modifier
    Row(
        modifier = modifier
            // 头像行是「点开创作者主页」的真实起点：持续记录它的边界供展开动画使用
            .then(origin?.trackModifier() ?: Modifier)
            .then(clickableMod)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            model = profile.avatar,
            size = 42.dp,
            contentDescription = profile.name,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (post.publish_time != null) {
            // 当年内只显示「M/d」，跨年才带上年份，与官方网页端习惯一致
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
