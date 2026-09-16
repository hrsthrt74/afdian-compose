package com.hrsthrt74.afdian.ui.components.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import com.hrsthrt74.afdian.ui.components.DataCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 数据卡片网格。
 *
 * 根据可用宽度自适应列数：手机两列、平板竖屏三列、平板横屏四列。
 * 组件本身不产生滚动，适合嵌套在页面的 LazyColumn 中。
 * 最后一行不足时补充空位，确保卡片宽度与上一行保持一致。
 *
 * @param items 卡片数据列表
 * @param modifier 可选修饰符
 * @param onItemClick 卡片点击回调
 * @param onItemLongClick 卡片长按回调
 * @param showRemoveBadge 是否显示编辑态删除 badge
 * @param onItemRemove 删除卡片回调
 * @param cardColors 卡片颜色配置，可选
 */
@Composable
fun DataCardGrid(
    items: List<DataCardItem>,
    modifier: Modifier = Modifier,
    onItemClick: ((DataCardItem) -> Unit)? = null,
    onItemLongClick: ((DataCardItem) -> Unit)? = null,
    showRemoveBadge: Boolean = false,
    onItemRemove: ((DataCardItem) -> Unit)? = null,
    cardColors: CardColors = CardDefaults.defaultColors(),
) {
    BoxWithConstraints(modifier = modifier) {
        // 通过断点控制列数，避免平板上卡片被过度拉宽，同时不引入嵌套滚动容器。
        val columns = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            DataCard(
                                title = item.title,
                                value = item.value,
                                unit = item.unit,
                                subtitle = item.subtitle,
                                modifier = Modifier.fillMaxWidth(),
                                colors = cardColors,
                                onClick = onItemClick?.let { click -> { click(item) } },
                                onLongClick = onItemLongClick?.let { click -> { click(item) } },
                            )
                            if (showRemoveBadge && onItemRemove != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.error)
                                        .clickable { onItemRemove(item) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = "删除${item.title}",
                                        tint = MiuixTheme.colorScheme.onError,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                    repeat(columns - rowItems.size) {
                        // 普通展示态只补布局宽度，不显示任何虚影。
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
