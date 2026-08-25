package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 大号创作者卡片组件（矩形）
 *
 * 上半部分显示创作者封面图，下半部分显示头像和创作者名称。
 * 适合单列布局的大卡片展示场景。
 *
 * @param avatarUrl 头像图片URL、Uri或Coil支持的任意数据源
 * @param coverUrl 封面/头图图片URL、Uri或Coil支持的任意数据源
 * @param creatorName 创作者名称
 * @param onClick 点击回调，传null则不可点击
 * @param modifier 修饰符
 */
@Composable
fun CreatorLargeCard(
    avatarUrl: Any?,
    coverUrl: Any?,
    creatorName: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
    ) {
        Column {
            // 上半部分：封面图（16:9 比例）
            AsyncImage(
                model = coverUrl,
                contentDescription = "$creatorName 的封面",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    // 图片加载前/失败时的占位背景色
                    .background(MiuixTheme.colorScheme.disabledSecondary),
                contentScale = ContentScale.Crop
            )

            // 下半部分：头像 + 创作者名称
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    model = avatarUrl,
                    size = 40.dp,
                    contentDescription = "$creatorName 的头像"
                )
                Text(
                    text = creatorName,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MiuixTheme.textStyles.title3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 小号创作者卡片组件（矩形）
 *
 * 结构与大卡一致（上封面图、下头像+名字），但整体尺寸更紧凑，
 * 适合双列网格布局展示场景。
 *
 * @param avatarUrl 头像图片URL、Uri或Coil支持的任意数据源
 * @param coverUrl 封面/头图图片URL、Uri或Coil支持的任意数据源
 * @param creatorName 创作者名称
 * @param onClick 点击回调，传null则不可点击
 * @param modifier 修饰符
 */
@Composable
fun CreatorSmallCard(
    avatarUrl: Any?,
    coverUrl: Any?,
    creatorName: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
    ) {
        Column {
            // 上半部分：封面图（扁长方形，适配窄小的双列卡片）
            AsyncImage(
                model = coverUrl,
                contentDescription = "$creatorName 的封面",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    // 图片加载前/失败时的占位背景色
                    .background(MiuixTheme.colorScheme.disabledSecondary),
                contentScale = ContentScale.Crop
            )

            // 下半部分：小头像 + 创作者名称（更紧凑的内边距）
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    model = avatarUrl,
                    size = 28.dp,
                    contentDescription = "$creatorName 的头像"
                )
                Text(
                    text = creatorName,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MiuixTheme.textStyles.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreatorLargeCardPreview() {
    CreatorLargeCard(
        avatarUrl = "https://example.com/avatar.jpg",
        coverUrl = "https://example.com/cover.jpg",
        creatorName = "创作者名称",
        onClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun CreatorSmallCardPreview() {
    CreatorSmallCard(
        avatarUrl = "https://example.com/avatar.jpg",
        coverUrl = "https://example.com/cover.jpg",
        creatorName = "创作者名称"
    )
}

// 预览：模拟大卡 + 双列小卡的实际排版效果
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun CreatorCardsLayoutPreview() {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        // 单列大卡
        CreatorLargeCard(
            avatarUrl = "https://example.com/avatar.jpg",
            coverUrl = "https://example.com/cover.jpg",
            creatorName = "创作者名称",
            modifier = Modifier.fillMaxWidth()
        )

        // 双列小卡
        Row(modifier = Modifier.padding(top = 12.dp)) {
            CreatorSmallCard(
                avatarUrl = "https://example.com/avatar.jpg",
                coverUrl = "https://example.com/cover.jpg",
                creatorName = "创作者A",
                modifier = Modifier.weight(1f)
            )
            CreatorSmallCard(
                avatarUrl = "https://example.com/avatar.jpg",
                coverUrl = "https://example.com/cover.jpg",
                creatorName = "创作者B",
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
        }
    }
}
