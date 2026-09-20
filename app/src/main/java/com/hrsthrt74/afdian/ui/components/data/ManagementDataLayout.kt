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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.settings.ManagementLayoutEntry
import com.hrsthrt74.afdian.data.settings.ManagementLayoutType
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import com.hrsthrt74.afdian.data.stats.DataCardCatalog
import com.hrsthrt74.afdian.ui.components.DataCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 按用户保存的布局渲染管理页数据。
 * 包括：数据卡片网格、图表卡；小标题、分割线。
 */
@Composable
fun ManagementDataLayout(
    cards: Map<String, DataCardItem>,
    layout: List<ManagementLayoutEntry>,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    onCardLongClick: ((DataCardItem) -> Unit)? = null,
    onCardRemove: ((DataCardItem) -> Unit)? = null,
    charts: Map<String, ChartDataItem> = emptyMap(),
    /** 长按图表卡（如首页创作者数据模块）时进入编辑。 */
    onChartLongClick: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp), // 统一管理项目 vertical 间距
        modifier = modifier
    ) {
        var index = 0
        while (index < layout.size) {
            val entry = layout[index]
            when (entry.type) {

                // 数据卡片网格
                ManagementLayoutType.CARD -> {
                    val cardItems = buildList {
                        var cardIndex = index
                        while (cardIndex < layout.size && layout[cardIndex].type == ManagementLayoutType.CARD) {
                            cardItemFor(layout[cardIndex].value, cards)?.let(::add)
                            cardIndex++
                        }
                        index = cardIndex
                    }
                    if (cardItems.isNotEmpty()) {
                        DataCardGrid(
                            items = cardItems,
                            onItemLongClick = onCardLongClick,
                            showRemoveBadge = editing,
                            onItemRemove = onCardRemove,
                        )
                    }
                }

                // 小标题
                ManagementLayoutType.TITLE -> {
                    SmallTitle(text = entry.value, insideMargin = PaddingValues(start = 16.dp, top = 8.dp))
                    index++
                }

                // 图表卡，独占一行
                ManagementLayoutType.CHART -> {
                    ChartDataCard(
                        key = entry.value,
                        data = charts[entry.value],
                        onLongClick = onChartLongClick,
                    )
                    index++
                }

                // 分割线
                ManagementLayoutType.DIVIDER -> {
                    HorizontalDivider(Modifier.padding(horizontal = 4.dp))
                    index++
                }
            }
        }
    }
}

/** 编辑态管理页：真实卡片直接处于可拖拽网格中。
 *
 * @param topInset 顶部让位（如顶栏高度）：加进 contentPadding 而非外层 padding，
 * 网格仍从屏幕顶布局、滚动时穿过顶栏，保证模糊顶栏能采样到内容。
 */
@Composable
fun ManagementEditableDataGrid(
    cards: Map<String, DataCardItem>,
    layout: List<ManagementLayoutEntry>,
    onLayoutChanged: (List<ManagementLayoutEntry>) -> Unit,
    onLayoutCommitted: (List<ManagementLayoutEntry>) -> Unit,
    modifier: Modifier = Modifier,
    charts: Map<String, ChartDataItem> = emptyMap(),
    topInset: Dp = 0.dp,
) {
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
        // 注意：isAnyItemDragging 初始即为 false，若不记录“上一帧是否在拖拽”，
        // 首次进入编辑页（还没拖过任何卡片）就会误触发一次提交，把当前 layout
        // （此时可能还是 initial 默认值）覆盖写进 DataStore，导致用户保存的布局被重置。
        var wasDragging by remember { mutableStateOf(false) }
        LaunchedEffect(reorderableState.isAnyItemDragging) {
            val isDragging = reorderableState.isAnyItemDragging
            // 只有“上一帧在拖、这一帧松手”才算一次真实的拖拽结束，才允许提交。
            if (wasDragging && !isDragging) {
                lastMove = null
                onLayoutCommitted(layout)
            }
            wasDragging = isDragging
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            // topInset 并入 contentPadding：首项让出顶栏、布局仍从屏幕顶开始（可被顶栏采样）
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp + topInset, bottom = 8.dp),
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
                // 卡片占位符（实线框）
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
                                width = 1.5.dp,
                                color = MiuixTheme.colorScheme.outline,
                                cornerRadius = 16.dp,
                            ),
                    )
                } else {
                    val entry = (item as EditableGridItem.Entry).entry
            ReorderableItem(reorderableState, key = "${entry.type}:${entry.value}") { isDragging ->
                // 拖动时卡片抬起并放大，松手后通过弹簧动画自然恢复。
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
                EditableLayoutEntryContent(
                    entry = entry,
                    cards = cards,
                    charts = charts,
                    dragModifier = dragModifier.shadow(
                        elevation = dragElevation.value,
                        shape = RoundedCornerShape(16.dp),
                    ),
                    onRemove = {
                        val nextLayout = layout - entry
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLayoutChanged(nextLayout)
                        onLayoutCommitted(nextLayout)
                    },
                )
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

/** 布局里的卡片即使暂时没有数据，也要用目录元数据渲染出可编辑的占位卡。 */
internal fun cardItemFor(key: String, cards: Map<String, DataCardItem>): DataCardItem? {
    val definition = DataCardCatalog.find(key) ?: return null
    return cards[key] ?: DataCardItem(id = key, title = definition.title, value = "--")
}

/** 编辑态的 布局元素渲染 与删除 badge */
@Composable
internal fun EditableLayoutEntryContent(
    entry: ManagementLayoutEntry,
    cards: Map<String, DataCardItem>,
    dragModifier: Modifier,
    onRemove: () -> Unit,
    charts: Map<String, ChartDataItem> = emptyMap(),
) {
    // 删除 badge 按钮
    BadgedBox(
        badge = {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .size(22.dp)
                    .background(MiuixTheme.colorScheme.error)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "删除布局元素",
                    tint = MiuixTheme.colorScheme.onError,
                )
            }
        },
    ) {
        // 编辑态的各元素的特殊样式，
        // 如分割线显示边框，便于拖动/识别
        when (entry.type) {

            // 数据卡
            ManagementLayoutType.CARD -> cardItemFor(entry.value, cards)?.let { card ->
                DataCard(
                    title = card.title,
                    value = card.value,
                    unit = card.unit,
                    subtitle = card.subtitle,
                    modifier = dragModifier,
                )
            }

            // 图表卡
            ManagementLayoutType.CHART -> ChartDataCard(
                key = entry.value,
                data = charts[entry.value],
                modifier = dragModifier,
            )

            // 小标题
            ManagementLayoutType.TITLE -> SmallTitle(
                text = entry.value,
                insideMargin = PaddingValues(start = 12.dp, top = 8.dp, bottom = 8.dp),
                modifier = dragModifier
                    .background(MiuixTheme.colorScheme.surface)
                    .squircleBorder(width = 1.5.dp, color = MiuixTheme.colorScheme.outline, cornerRadius = 16.dp)
                    .padding(horizontal = 8.dp),
            )

            // 分割线
            ManagementLayoutType.DIVIDER -> Box(
                modifier = dragModifier
                    .background(MiuixTheme.colorScheme.surface)
                    .height(40.dp)
                    .squircleBorder(width = 1.5.dp, color = MiuixTheme.colorScheme.outline, cornerRadius = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text(
                        text = "分割线",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                }
            }
        }
    }
}
