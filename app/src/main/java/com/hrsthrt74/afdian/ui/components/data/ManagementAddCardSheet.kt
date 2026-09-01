package com.hrsthrt74.afdian.ui.components.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import com.hrsthrt74.afdian.data.settings.ManagementCardId
import com.hrsthrt74.afdian.data.settings.ManagementLayoutEntry
import com.hrsthrt74.afdian.data.settings.ManagementLayoutType
import com.hrsthrt74.afdian.ui.components.DataCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 编辑态的添加面板：按分组展示可添加的数据卡片，每个卡片显示真实数据值。
 * 标题和分割线作为独立的布局元素放在顶部。
 */
@Composable
fun ManagementAddCardSheet(
    layout: List<ManagementLayoutEntry>,
    rows: List<Pair<String, String>>,
    show: Boolean,
    onAdd: (ManagementLayoutEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    // 将 rows 转为 Map 以便快速查找
    val values = remember(rows) { rows.toMap() }

    // 过滤掉已添加的卡片
    val availableCards = remember(layout) {
        ManagementCardId.entries.filterNot { card ->
            layout.any { it.type == ManagementLayoutType.CARD && it.value == card.storageValue }
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
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ===== 布局元素区域 =====
            // 标题添加按钮组
            item(key = "add_titles") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallTitle("小标题", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 0.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        listOf("收入", "访问数据", "提现").forEach { title ->
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
                    }
                }
            }

            // 分割线添加按钮
            item(key = "add_divider") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallTitle("分割线", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 0.dp))
                    Card(
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
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

            // ===== 数据卡片区域 =====
            if (availableCards.isNotEmpty()) {
                // 按分组渲染卡片
                groupedCards.forEach { (group, cards) ->
                    item(key = "group_$group") {
                        SmallTitle(group, insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 0.dp))
                    }

                    // 使用 DataCardGrid 网格布局展示卡片
                    item(key = "cards_$group") {
                        DataCardGrid(
                            items = cards.map { card ->
                                DataCardItem(card.title, values[card.title] ?: "--")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            onItemClick = { item ->
                                cards.firstOrNull { it.title == item.title }?.let { card ->
                                    onAdd(ManagementLayoutEntry(ManagementLayoutType.CARD, card.storageValue))
                                }
                            },
                            cardColors = CardColors(
                                color = MiuixTheme.colorScheme.surface,
                                contentColor = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                            ),
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
