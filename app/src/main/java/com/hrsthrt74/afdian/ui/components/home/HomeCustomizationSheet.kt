package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrsthrt74.afdian.data.settings.HomeModule
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** 首页模块配置抽屉：排序和显隐逻辑集中在这里，首页容器无需关心交互细节。 */
@Composable
fun HomeCustomizationSheet(settingsStore: SettingsStore, show: Boolean, onDismiss: () -> Unit) {
    val moduleOrder by settingsStore.homeModuleOrder.collectAsState(initial = HomeModule.entries)
    val enabledModules by settingsStore.homeEnabledModules.collectAsState(initial = HomeModule.entries.toSet())
    val scope = rememberCoroutineScope()

    WindowBottomSheet(
        show = show,
        title = "自定义首页",
        onDismissRequest = onDismiss,
        insideMargin = DpSize(12.dp, 0.dp))
    {
        val listState = rememberLazyListState()
        val hapticFeedback = LocalHapticFeedback.current
        var editableOrder by remember(moduleOrder) { mutableStateOf(moduleOrder) }
        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
            editableOrder = editableOrder.toMutableList().apply { add(to.index, removeAt(from.index)) }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { settingsStore.setHomeModuleOrder(editableOrder) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            items(editableOrder, key = { it.storageValue }) { module ->
                ReorderableItem(reorderableState, key = module.storageValue) { isDragging ->
                    // 拖拽中让整个条目轻微放大，突出“被抓起”的状态
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragging) 1.05f else 1f,
                        animationSpec = spring(),
                        label = "home_module_drag_scale",
                    )
                    // 外层预留不被缩放的水平空隙，避免放大后顶到抽屉/列表边界被裁剪
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        // 内层才做缩放，溢出量被外层空隙吸收，不会超出容器
                        Box(modifier = Modifier.graphicsLayer { scaleX = dragScale; scaleY = dragScale }) {
                            HomeModuleSettingRow(
                                module = module,
                                checked = module in enabledModules,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        val next = enabledModules.toMutableSet().apply {
                                            if (enabled) add(module) else remove(module)
                                        }
                                        settingsStore.setHomeEnabledModules(next)
                                    }
                                },
                                dragModifier = with(this@ReorderableItem) {
                                    Modifier.longPressDraggableHandle(
                                        // 长按进入拖拽时触发震动，给用户明确的抓取反馈
                                        onDragStarted = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeModuleSettingRow(module: HomeModule, checked: Boolean, onCheckedChange: (Boolean) -> Unit, dragModifier: Modifier) {
    Card(
        colors = CardColors(MiuixTheme.colorScheme.secondaryContainer, MiuixTheme.colorScheme.onSurfaceContainerHighest),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        CheckboxPreference(
            title = module.title,
            summary = module.summary,
            checked = checked,
            onCheckedChange = onCheckedChange,
            checkboxLocation = CheckboxLocation.End,
            insideMargin = PaddingValues(16.dp, 12.dp),
            startAction = {
                Text(
                    "☰",
                    fontSize = 20.sp,
                    modifier = dragModifier.padding(start = 4.dp, end = 4.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
        )
    }
}
