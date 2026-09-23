package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 长条形创作者卡片组件
 *
 * 自定义布局：左侧显示头像，中间显示创作者名称和副标题，
 * 右侧显示箭头指示器。整体由 Card 包裹并处理点击涟漪。
 *
 * @param avatarUrl 头像图片URL、Uri或Coil支持的任意数据源
 * @param creatorName 创作者名称
 * @param doing 创作内容描述，组件会自动拼成「正在创作 xxx」显示
 * @param summary 完全自定义的副标题文字，优先级高于 doing；为null时回退到 doing
 * @param onClick 点击回调，传null则不可点击
 * @param modifier 修饰符
 */
@Composable
fun CreatorListItem(
    avatarUrl: Any?,
    creatorName: String,
    doing: String? = null,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 副标题取值：自定义 summary 优先，否则用「正在创作 xxx」
    val displaySummary = when {
        !summary.isNullOrBlank() -> summary
        !doing.isNullOrBlank() -> "正在创作 $doing"
        else -> null
    }
    // Card 只负责容器样式，不处理点击
    Card(modifier = modifier) {
        // 点击事件和涟漪特效放在 Box 上
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧头像
                Avatar(
                    model = avatarUrl,
                    size = 48.dp,
                    contentDescription = "$creatorName 的头像"
                )

                // 中间文字区：名称 + 副标题
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(creatorName)

                    if (displaySummary != null) {
                        Text(
                            text = displaySummary,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // 右侧箭头指示器（参考 miuix ArrowPreference 的样式）
                Image(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(width = 10.dp, height = 16.dp),
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreatorListItemPreview() {
    // 默认「正在创作 xxx」样式
    CreatorListItem(
        avatarUrl = "https://example.com/avatar.jpg",
        creatorName = "创作者名称",
        doing = "每日绘画分享",
        onClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun CreatorListItemCustomSummaryPreview() {
    // 完全自定义副标题文字
    CreatorListItem(
        avatarUrl = "https://example.com/avatar.jpg",
        creatorName = "创作者名称",
        summary = "我发电了 超级会员；这里也可以自定义文字",
        onClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun CreatorListItemNoSummaryPreview() {
    CreatorListItem(
        avatarUrl = "https://example.com/avatar.jpg",
        creatorName = "创作者名称",
        onClick = {}
    )
}
