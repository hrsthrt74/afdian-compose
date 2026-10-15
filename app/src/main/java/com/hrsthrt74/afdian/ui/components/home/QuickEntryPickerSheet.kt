package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.ui.components.basic.QuickEntry
import com.hrsthrt74.afdian.ui.navigation.QUICK_ENTRY_SLOTS
import com.hrsthrt74.afdian.ui.navigation.QuickEntryGroup
import com.hrsthrt74.afdian.ui.navigation.QuickEntryPreset
import com.hrsthrt74.afdian.ui.navigation.normalizeQuickEntries
import com.hrsthrt74.afdian.ui.navigation.presets
import com.hrsthrt74.afdian.ui.navigation.resolveIcon
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** 「可添加」网格的列数：与模块本身一行 4 个入口一致。 */
private const val GRID_COLUMNS = 4

/**
 * 「快捷方式」模块的自定义面板：挑最多 [QUICK_ENTRY_SLOTS] 个入口并排顺序。
 */
@Composable
fun QuickEntryPickerSheet(settingsStore: SettingsStore, show: Boolean, onDismiss: () -> Unit) {
    // 用可空状态等 DataStore 真值：collectAsState 的初值只能给默认的 4 个，
    // 拿它当配置会让面板开合时先闪一下默认入口再跳成用户配置（与创作者数据编辑页同一处理）。
    var savedKeys by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(settingsStore) {
        settingsStore.homeQuickEntryKeys.collect { savedKeys = it }
    }
    // 本地编辑态：只在首次拿到真值后初始化一次，之后不再随数据流回推，
    // 否则拖拽 / 增删产生的中间态会被自己的写入结果覆盖掉。
    var selected by remember { mutableStateOf<List<QuickEntryPreset>?>(null) }
    LaunchedEffect(savedKeys) {
        val keys = savedKeys
        if (keys != null && selected == null) selected = normalizeQuickEntries(keys)
    }
    val current = selected
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // 所有改动的统一出口：先更新本地编辑态（界面立即响应），再落盘
    val commit: (List<QuickEntryPreset>) -> Unit = { next ->
        selected = next
        scope.launch { settingsStore.setHomeQuickEntryKeys(next.map { it.storageValue }) }
    }
    // 槽位占满：网格里剩下的格子全部置灰，不再响应点击
    val hasRoom = (current?.size ?: 0) < QUICK_ENTRY_SLOTS

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val picked = current ?: return@rememberReorderableLazyListState
        // 面板里混着不可拖动的小标题与网格，LazyList 下标跟「已添加」的下标对不上，
        // 一律按 key（storageValue）反查真实位置。
        val fromIndex = picked.indexOfFirst { it.storageValue == from.key }
        val toIndex = picked.indexOfFirst { it.storageValue == to.key }
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            commit(picked.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
        }
    }

    WindowBottomSheet(
        show = show,
        title = "自定义快捷方式",
        onDismissRequest = onDismiss,
        insideMargin = DpSize(12.dp, 0.dp),
        endAction = {
            IconButton({
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onDismiss() })
            { Icon(MiuixIcons.Ok, null) }
        },
    ) {
        // 真值未到之前面板先空着：这一帧只可能出现在刚进首页的极短时间内
        val picked = current ?: return@WindowBottomSheet
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            // ===== 已添加：可拖拽列表 =====
            item(key = "picked_title") {
                SmallTitle("已添加" + "（${current?.size ?: 0}/$QUICK_ENTRY_SLOTS）", insideMargin = PaddingValues(20.dp, 12.dp, 8.dp, 4.dp))
            }
            itemsIndexed(picked, key = { _, preset -> preset.storageValue }) { _, preset ->
                ReorderableItem(reorderableState, key = preset.storageValue) { isDragging ->
                    // 拖拽中让整个条目轻微放大
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragging) 1.05f else 1f,
                        animationSpec = spring(),
                        label = "quick_entry_drag_scale",
                    )
                    // 外层预留不被缩放的水平空隙，避免放大后顶到抽屉/列表边界被裁剪
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        // 内层才做缩放，溢出量被外层空隙吸收，不会超出容器
                        Box(modifier = Modifier.graphicsLayer { scaleX = dragScale; scaleY = dragScale }) {
                            PickedEntryRow(
                                preset = preset,
                                // 只剩最后一个时不再允许移除，否则面板里会空掉一整段
                                removable = picked.size > 1,
                                onRemove = {
                                    if (picked.size > 1) commit(picked.filterNot { it == preset })
                                },
                                dragModifier = with(this@ReorderableItem) {
                                    Modifier.longPressDraggableHandle(
                                        // 长按进入拖拽时触发震动，给用户明确的抓取反馈
                                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // ===== 待添加网格 =====
            QuickEntryGroup.entries.forEach { group ->
                item(key = "group_${group.name}") {
                    SmallTitle(group.title, insideMargin = PaddingValues(20.dp, 12.dp, 8.dp, 12.dp))
                }
                item(key = "grid_${group.name}") {
                    PresetGrid(
                        presets = group.presets(),
                        picked = picked,
                        hasRoom = hasRoom,
                        onPick = { preset -> if (hasRoom) commit(picked + preset) },
                    )
                }
            }
        }
    }
}

/** 「已添加」里的一行：图标 + 标题 + 移除按钮 + 拖拽手柄。 */
@Composable
private fun PickedEntryRow(
    preset: QuickEntryPreset,
    removable: Boolean,
    onRemove: () -> Unit,
    dragModifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Card(
        colors = AfdianCardDefaults.overlayColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),    // 卡片内边距
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 入口图标
            Icon(
                imageVector = preset.resolveIcon(),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
            )
            // 已添加的项目标题
            Text( text = preset.title, modifier = Modifier.weight(1f) )
            Row{
                // 移除按钮
                if (removable){
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        onRemove()
                    }) { Icon(imageVector = MiuixIcons.Delete, contentDescription = "移除${preset.title}") }
                }
                // 拖拽手柄
                Text(
                    text = "☰",
                    fontSize = 20.sp,
                    modifier = dragModifier.padding(8.dp, 6.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

/**
 * 下半部分的 待添加网格。
 */
@Composable
private fun PresetGrid(
    presets: List<QuickEntryPreset>,
    picked: List<QuickEntryPreset>,
    hasRoom: Boolean,
    onPick: (QuickEntryPreset) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),  // 每个类别里两行之间的间距
    ) {
        presets.chunked(GRID_COLUMNS).forEach { rowPresets ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowPresets.forEach { preset ->
                    val added = preset in picked
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        QuickEntry(
                            title = preset.title,
                            icon = preset.resolveIcon(),
                            enabled = !added && hasRoom,
                            onClick = { onPick(preset) },
                            modifier = Modifier.alpha(
                                when {
                                    added -> 0.38f
                                    !hasRoom -> 0.30f
                                    else -> 1f
                                }
                            ),
                        )
                    }
                }
                // 末行不足 4 个时补空位，保持每行格子等宽
                repeat(GRID_COLUMNS - rowPresets.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))  // 每个分组间的间距
    }
}
