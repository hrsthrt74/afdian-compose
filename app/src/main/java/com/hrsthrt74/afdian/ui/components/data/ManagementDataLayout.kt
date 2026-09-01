package com.hrsthrt74.afdian.ui.components.data

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.settings.ManagementCardId
import com.hrsthrt74.afdian.data.settings.ManagementLayoutEntry
import com.hrsthrt74.afdian.data.settings.ManagementLayoutType
import com.hrsthrt74.afdian.ui.components.DataCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 按用户保存的布局渲染管理页数据。
 * 连续的数据项会合并为一个自适应网格，标题和分割线则作为独立布局项插入。
 */
@Composable
fun ManagementDataLayout(
    rows: List<Pair<String, String>>,
    layout: List<ManagementLayoutEntry>,
    modifier: Modifier = Modifier,
) {
    val values = rows.toMap()
    Column(modifier = modifier) {
        var index = 0
        while (index < layout.size) {
            val entry = layout[index]
            when (entry.type) {
                ManagementLayoutType.CARD -> {
                    val cards = buildList {
                        var cardIndex = index
                        while (cardIndex < layout.size && layout[cardIndex].type == ManagementLayoutType.CARD) {
                            val cardId = ManagementCardId.fromStorage(layout[cardIndex].value)
                            val title = cardId?.title
                            val value = title?.let(values::get)
                            if (title != null && value != null) add(DataCardItem(title, value))
                            cardIndex++
                        }
                        index = cardIndex
                    }
                    if (cards.isNotEmpty()) DataCardGrid(cards)
                }
                ManagementLayoutType.TITLE -> {
                    SmallTitle(text = entry.value, insideMargin = PaddingValues(start = 16.dp, top = 16.dp, bottom = 8.dp))
                    index++
                }
                ManagementLayoutType.DIVIDER -> {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    index++
                }
            }
        }
    }
}

/** 编辑态管理页：真实卡片直接处于可拖拽网格中。 */
@Composable
fun ManagementEditableDataGrid(
    rows: List<Pair<String, String>>,
    layout: List<ManagementLayoutEntry>,
    onLayoutChanged: (List<ManagementLayoutEntry>) -> Unit,
    onLayoutCommitted: (List<ManagementLayoutEntry>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = rows.toMap()
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    var lastMove by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 与普通 DataCardGrid 使用相同的列数断点，确保编辑态空位和展示态对齐。
        val columns = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        val renderItems = buildList<EditableGridItem> {
            var cardCount = 0
            var lastCardGroupCount = 0
            layout.forEachIndexed { index, entry ->
                add(EditableGridItem.Entry(entry))
                if (entry.type == ManagementLayoutType.CARD) cardCount++
                val isLastCard = entry.type == ManagementLayoutType.CARD &&
                    (index == layout.lastIndex || layout[index + 1].type != ManagementLayoutType.CARD)
                if (isLastCard) {
                    lastCardGroupCount = cardCount
                    repeat((columns - cardCount % columns) % columns) { slot ->
                        add(EditableGridItem.Placeholder("placeholder_${index}_$slot"))
                    }
                    cardCount = 0
                } else if (entry.type != ManagementLayoutType.CARD) {
                    cardCount = 0
                    lastCardGroupCount = 0
                }
            }
            // 末尾已经铺满，或最后是标题/分割线时，额外保留两个稳定的拖拽缓冲位。
            val lastEntry = layout.lastOrNull()
            if (lastEntry != null && (
                    lastEntry.type != ManagementLayoutType.CARD ||
                        lastCardGroupCount > 0 && lastCardGroupCount % columns == 0
                )
            ) {
                repeat(2) { slot ->
                    add(EditableGridItem.Placeholder("tail_placeholder_$slot"))
                }
            }
        }
        val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
            val fromItem = renderItems.getOrNull(from.index)
            val toItem = renderItems.getOrNull(to.index)
            // 虚影只展示空位，不参与排序，避免占位项改变真实布局索引。
            if (fromItem !is EditableGridItem.Entry || toItem !is EditableGridItem.Entry) {
                return@rememberReorderableLazyGridState
            }
            val fromIndex = layout.indexOf(fromItem.entry)
            val toIndex = layout.indexOf(toItem.entry)
            if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyGridState
            val move = fromIndex to toIndex
            if (move == lastMove || fromIndex == toIndex) return@rememberReorderableLazyGridState
            lastMove = move
            val nextLayout = layout.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            if (nextLayout == layout) return@rememberReorderableLazyGridState
            onLayoutChanged(nextLayout)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        // 拖动期间只更新 Compose 内存状态，松手后才写入 DataStore，避免外部流重组拖拽网格。
        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                lastMove = null
                onLayoutCommitted(layout)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = renderItems,
                key = { item ->
                    when (item) {
                        is EditableGridItem.Entry -> "${item.entry.type}:${item.entry.value}"
                        is EditableGridItem.Placeholder -> item.key
                    }
                },
                span = { item ->
                    when (item) {
                        is EditableGridItem.Entry -> if (item.entry.type == ManagementLayoutType.CARD) GridItemSpan(1) else GridItemSpan(maxLineSpan)
                        is EditableGridItem.Placeholder -> GridItemSpan(1)
                    }
                },
            ) { item ->
                if (item is EditableGridItem.Placeholder) {
                    DataCard(
                        title = " ",
                        value = " ",
                        colors = CardDefaults.defaultColors(
                            color = Color.Transparent,
                            contentColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .squircleBorder(
                                width = 2.dp,
                                color = MiuixTheme.colorScheme.outline,
                                cornerRadius = 16.dp,
                            ),
                    )
                } else {
                    val entry = (item as EditableGridItem.Entry).entry
            ReorderableItem(reorderableState, key = "${entry.type}:${entry.value}") { isDragging ->
                // 参考 exTile：拖动时卡片抬起并放大，松手后通过弹簧动画自然恢复。
                val dragScale = animateFloatAsState(
                    targetValue = if (isDragging) 1.1f else 1f,
                    animationSpec = spring(),
                    label = "management_card_drag_scale",
                )
                val dragElevation = animateDpAsState(
                    targetValue = if (isDragging) 12.dp else 0.dp,
                    animationSpec = spring(),
                    label = "management_card_drag_elevation",
                )
                val dragModifier = with(this@ReorderableItem) {
                    Modifier
                        .fillMaxWidth()
                        .longPressDraggableHandle(
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        )
                        .scale(dragScale.value)
                }
                BadgedBox(
                    badge = {
                        // Badge 自身不拦截锚点事件，点击减号只负责删除当前布局项。
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(20.dp)
                                .background(MiuixTheme.colorScheme.error),
                        ) {
                            Box(Modifier.clickable {
                                val nextLayout = layout - entry
                                onLayoutChanged(nextLayout)
                                onLayoutCommitted(nextLayout)
                            }){
                                Icon(Icons.Filled.Remove, contentDescription = null, tint = MiuixTheme.colorScheme.onError)
                            }
                        }
                    },
                ) {
                    when (entry.type) {
                        ManagementLayoutType.CARD -> {
                            val card = ManagementCardId.fromStorage(entry.value)
                            if (card != null) {
                                DataCard(
                                    title = card.title,
                                    value = values[card.title] ?: "--",
                                    modifier = dragModifier.shadow(
                                        elevation = dragElevation.value,
                                        shape = RoundedCornerShape(16.dp),
                                    ),
                                )
                            }
                        }
                        ManagementLayoutType.TITLE -> SmallTitle(
                            text = entry.value,
                            insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            modifier = dragModifier
                                .squircleBorder(width = 2.dp, color = MiuixTheme.colorScheme.outline, cornerRadius = 16.dp)
                                .padding(horizontal = 8.dp),
                        )
                        ManagementLayoutType.DIVIDER -> Box(
                            modifier = dragModifier
                                .height(40.dp)
                                .squircleBorder(width = 2.dp, color = MiuixTheme.colorScheme.outline, cornerRadius = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 编辑态用左右线条和文字明确标识分割线，同时保留整行拖拽热区。
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text(
                                    text = "分割线",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                }
            }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

private sealed interface EditableGridItem {
    data class Entry(val entry: ManagementLayoutEntry) : EditableGridItem
    data class Placeholder(val key: String) : EditableGridItem
}
