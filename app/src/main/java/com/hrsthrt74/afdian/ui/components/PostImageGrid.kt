package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 动态图片网格：根据图片数量自适应布局（1张4:3 / 2~3张并排 / 4张以上3列网格）。
 *
 * 从 PostCard 提取出来的公共组件，动态详情页也复用——
 * 有些动态的图不在正文 HTML 里，而是单独存在 post.pics 数组中，
 * 详情页正文渲染完 HTML 后需要用它把 pics 也展示出来。
 *
 * @param images 图片 URL 列表（会逐个做归一化，兼容协议相对/明文 http/含空白等写法）
 */
@Composable
fun PostImageGrid(images: List<String>) {
    if (images.isEmpty()) return

    val cornerRadius = 8.dp
    val gap = 4.dp
    val bgShape = RoundedCornerShape(cornerRadius)

    when (images.size) {
        1 -> AsyncImage(
            model = normalizeImageUrl(images[0]),
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
                    model = normalizeImageUrl(url),
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
                                model = normalizeImageUrl(url),
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

/** 把动态图片地址归一化成 Coil 能直接加载的 https 绝对地址（HTML 正文与 pics 网格共用） */
internal fun normalizeImageUrl(raw: String): String {
    val src = raw.trim()
    return when {
        src.isBlank() -> ""
        // 协议相对地址（//pic1.afdiancdn.com/...）：编辑器常见写法
        src.startsWith("//") -> "https:$src"
        // 站内相对路径
        src.startsWith("/") -> "https://afdian.com$src"
        // http 明文：Android 默认禁止明文流量，而爱发电 CDN 的 https 实测可用，
        // 统一升级成 https，避免「有的图能载入有的不行」的玄学问题
        src.startsWith("http://") -> "https://" + src.removePrefix("http://")
        // 无协议且非相对路径的裸域名（cdn.example.com/a.png）
        !src.contains("://") -> "https://$src"
        else -> src
    }
}
