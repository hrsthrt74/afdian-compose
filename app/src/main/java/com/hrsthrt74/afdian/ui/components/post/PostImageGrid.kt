package com.hrsthrt74.afdian.ui.components.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import com.hrsthrt74.afdian.data.network.AfdianRouteState
import com.hrsthrt74.afdian.ui.components.image.LocalImageViewer
import top.yukonga.miuix.kmp.basic.Text
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
        1 -> ViewerThumb(
            url = images[0],
            urls = images,
            index = 0,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MiuixTheme.colorScheme.disabledSecondary, bgShape)
                .clip(bgShape),
        )
        2, 3 -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            images.forEachIndexed { index, url ->
                ViewerThumb(
                    url = url,
                    urls = images,
                    index = index,
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
            // 9 张及以上：只展示前 9 张（3x3），第 9 张上叠一个「+n」表示还有多少张没显示
            val showAll = images.size < 9
            val display = if (showAll) images else images.take(9)
            val remaining = if (showAll) 0 else images.size - 9
            val rows = display.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEachIndexed { colIndex, url ->
                            // 9 张及以上时，第 3 行第 3 列（索引 2,2）即第 9 张需要叠层
                            val isNinth = !showAll && rowIndex == 2 && colIndex == 2
                            // 把 aspectRatio 放到外层 Box 上，保证格子是正方形，
                            // 叠层用 fillMaxSize 才能铺满整张图；剩余为 0（正好 9 张）则不显示
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            ) {
                                ViewerThumb(
                                    url = url,
                                    urls = images,
                                    // 按 3 列换算成原图列表中的真实下标
                                    index = rowIndex * 3 + colIndex,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MiuixTheme.colorScheme.disabledSecondary, bgShape)
                                        .clip(bgShape),
                                )
                                // 叠层：固定半透明黑底（不随主题变化，深浅色下始终压在图片上）+ 居中「+n」文字
                                if (isNinth && remaining > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Color.Black.copy(alpha = 0.5f),
                                                bgShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "+$remaining",
                                            color = Color.White,
                                            style = MiuixTheme.textStyles.title2,
                                        )
                                    }
                                }
                            }
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

/**
 * 可点击的缩略图：点击后交给全局图片查看器，从当前格子的位置展开。
 *
 * 用 `boundsInRoot()` 采集位置而不是传坐标参数：
 * 查看器宿主与页面在同一个根布局里，坐标系一致，放大/收起动画才能严丝合缝地对上。
 *
 * 除图片网格外，动态封面图（PostCard / 详情页）也复用它——
 * 单图场景传 `urls = listOf(url)`、`index = 0` 即可。
 */
@Composable
internal fun ViewerThumb(
    url: String,
    urls: List<String>,
    index: Int,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val viewer = LocalImageViewer.current
    // 布局完成前是 Rect.Zero，此时点击也能打开，只是动画起点在左上角
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // 缩略图加载成功后的内存缓存 key：交给查看器做占位图，避免打开时重新解码导致闪一下
    var cacheKey by remember { mutableStateOf<MemoryCache.Key?>(null) }
    AsyncImage(
        model = normalizeImageUrl(url),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clickable { viewer.open(urls, index, bounds, cacheKey) },
        onSuccess = { cacheKey = it.result.memoryCacheKey },
    )
}

/**
 * 把动态图片地址归一化成 Coil 能直接加载的 https 绝对地址（HTML 正文与 pics 网格共用）
 *
 * @param host 站内相对路径（以 `/` 开头）要补的域名。默认取当前优先线路，
 *   与接口请求走同一条线路；显式传参供单测固定域名。
 *   注意这是组合期读全局值，不是 Compose 可观察状态：切线路后已渲染过的图片不会重新解析，
 *   新滚到的内容才用新域名（图片有磁盘缓存，实际影响可忽略）。
 */
internal fun normalizeImageUrl(
    raw: String,
    host: String = AfdianRouteState.preferred.host,
): String {
    val src = raw.trim()
    return when {
        src.isBlank() -> ""
        // 协议相对地址（//pic1.afdiancdn.com/...）：编辑器常见写法
        src.startsWith("//") -> "https:$src"
        // 站内相对路径：域名跟随优先线路
        src.startsWith("/") -> "https://$host$src"
        // http 明文：Android 默认禁止明文流量，而爱发电 CDN 的 https 实测可用，
        // 统一升级成 https，避免「有的图能载入有的不行」的玄学问题
        src.startsWith("http://") -> "https://" + src.removePrefix("http://")
        // 无协议且非相对路径的裸域名（cdn.example.com/a.png）
        !src.contains("://") -> "https://$src"
        else -> src
    }
}
