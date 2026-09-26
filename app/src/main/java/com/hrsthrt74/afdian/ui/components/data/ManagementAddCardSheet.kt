package com.hrsthrt74.afdian.ui.components.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.settings.ManagementLayoutEntry
import com.hrsthrt74.afdian.data.settings.ManagementLayoutType
import com.hrsthrt74.afdian.data.stats.ChartCatalog
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import com.hrsthrt74.afdian.data.stats.DataCardCatalog
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 编辑态的添加面板：按分组展示可添加的数据卡片，每个卡片显示真实数据值。
 * 标题和分割线作为独立的布局元素放在顶部。
 */
@Composable
fun ManagementAddCardSheet(
    layout: List<ManagementLayoutEntry>,
    cards: Map<String, DataCardItem>,
    charts: Map<String, ChartDataItem> = emptyMap(),
    show: Boolean,
    onAdd: (ManagementLayoutEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    // 过滤掉已添加的卡片
    val availableCards = remember(layout) {
        DataCardCatalog.all.filterNot { card ->
            layout.any { it.type == ManagementLayoutType.CARD && it.value == card.key.storageValue }
        }
    }

    // 过滤掉已添加的图表卡
    val availableCharts = remember(layout) {
        ChartCatalog.all.filterNot { chart ->
            layout.any { it.type == ManagementLayoutType.CHART && it.value == chart.storageValue }
        }
    }

    // 按分组组织卡片
    val groupedCards = remember(availableCards) {
        availableCards.groupBy { it.group }
    }

    // 连点计数器：每次点击立即自增，不依赖 recomposition，避免重复 key
    var dividerClickCount by remember { mutableIntStateOf(0) }

    WindowBottomSheet(
        show = show,
        title = "添加卡片",
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().scrollEndHaptic(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ===== 布局元素区域 =====
            // 标题添加按钮组
            item(key = "add_titles") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallTitle("小标题", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 0.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DataCardCatalog.groups.chunked(3).forEach { titles ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                titles.forEach { title ->
                                    val alreadyAdded = layout.any {
                                        it.type == ManagementLayoutType.TITLE && it.value == title
                                    }
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surface),
                                        onClick = { onAdd(ManagementLayoutEntry(ManagementLayoutType.TITLE, title)) },
                                        enabled = !alreadyAdded,
                                    ) { Text(title) }
                                }
                                repeat(3 - titles.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 分割线添加按钮
            item(key = "add_divider") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallTitle("分割线", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 0.dp))
                    Card(
                        // 弹层内 Card 统一配色：与 Sheet 背景拉开层次
                        colors = AfdianCardDefaults.overlayColors(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(modifier = Modifier
                            .pressable(interactionSource = null)
                            .clickable {
                                dividerClickCount++
                                onAdd(ManagementLayoutEntry(ManagementLayoutType.DIVIDER, "divider_${layout.size}_$dividerClickCount"))
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp, 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text(
                                    text = "添加分割线",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ===== 图表卡区域 =====
            // 与数据卡一致：真实渲染图表本身（带真实数据），点击整卡加入布局。
            // 每张图一个独立 LazyColumn item + 稳定 key：添加/移除某张图时，
            // 其它图表的 item 因 key 不变不会重组重绘，避免整组一起重建造成的卡顿。
            if (availableCharts.isNotEmpty()) {
                item(key = "charts_section_title") {
                    SmallTitle("图表", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 0.dp))
                }
                items(
                    items = availableCharts,
                    key = { it.storageValue },
                ) { chart ->
                    ChartDataCard(
                        key = chart.storageValue,
                        data = charts[chart.storageValue],
                        onClick = {
                            onAdd(ManagementLayoutEntry(ManagementLayoutType.CHART, chart.storageValue))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        // 弹层内 Card 统一配色：与 Sheet 背景拉开层次
                        colors = AfdianCardDefaults.overlayColors(),
                    )
                }
            }

            // ===== 数据卡片区域 =====
            if (availableCards.isNotEmpty()) {
                // 按分组渲染卡片
                groupedCards.forEach { (group, definitions) ->
                    item(key = "group_$group") {
                        SmallTitle(group, insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 0.dp))
                    }

                    // 使用 DataCardGrid 网格布局展示卡片
                    item(key = "cards_$group") {
                        DataCardGrid(
                            items = definitions.map { card ->
                                cards[card.key.storageValue]
                                    ?: DataCardItem(id = card.key.storageValue, title = card.title, value = "--")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            onItemClick = { item ->
                                DataCardCatalog.find(item.id)?.let { card ->
                                    onAdd(ManagementLayoutEntry(ManagementLayoutType.CARD, card.key.storageValue))
                                }
                            },
                            cardColors = AfdianCardDefaults.overlayColors(),
                        )
                    }
                }
            }

            // 底部留白
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
