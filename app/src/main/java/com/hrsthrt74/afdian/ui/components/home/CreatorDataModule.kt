package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.settings.ManagementLayoutType
import com.hrsthrt74.afdian.data.settings.defaultHomeCreatorDataLayout
import com.hrsthrt74.afdian.ui.components.data.ManagementAddCardSheet
import com.hrsthrt74.afdian.ui.components.data.ManagementDataLayout
import com.hrsthrt74.afdian.ui.components.data.ManagementEditableDataGrid
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 创作者数据展示模块。
 * 长按卡片进入整页编辑（[CreatorDataEditPage]），由 HomeScreen 在编辑时隐藏其它模块，
 * 避免把可滚动的编辑网格嵌在首页 LazyColumn 里造成“双层滚动”。
 */
@Composable
fun CreatorDataModule(context: HomeModuleContentContext) {
    val savedLayout by context.settingsStore.homeCreatorDataLayout.collectAsState(
        initial = defaultHomeCreatorDataLayout,
    )
    // 有实际数字卡、或有用户主动添加的图表卡时，才展示该模块。
    val hasData = savedLayout.any {
        it.type == ManagementLayoutType.CARD && context.creatorCards[it.value]?.value != "--"
    } || savedLayout.any { it.type == ManagementLayoutType.CHART }
    if (!hasData) return
    Column {
        ClickableSmallTitle(title = "创作者数据", onClick = context.onOpenManagement)
        ManagementDataLayout(
            cards = context.creatorCards,
            layout = savedLayout,
            charts = context.creatorCharts,
            onCardLongClick = { context.onEditCreatorData() },
            onChartLongClick = { context.onEditCreatorData() },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 首页创作者数据的整页编辑界面。
 * 作为首页唯一内容渲染（其它模块被隐藏），编辑网格自身独占剩余高度滚动，
 * 从而完全复用管理页稳定、支持跨格拖拽的编辑体验。
 *
 * @param onExit 结束编辑并返回首页。
 */
@Composable
fun CreatorDataEditPage(
    context: HomeModuleContentContext,
    modifier: Modifier = Modifier,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val savedLayout by context.settingsStore.homeCreatorDataLayout.collectAsState(
        initial = defaultHomeCreatorDataLayout,
    )
    var layout by remember(savedLayout) { mutableStateOf(savedLayout) }
    var showAddSheet by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        CreatorDataEditToolbar(
            onAdd = { showAddSheet = true },
            onDone = onExit,
        )
        ManagementEditableDataGrid(
            cards = context.creatorCards,
            layout = layout,
            charts = context.creatorCharts,
            onLayoutChanged = { layout = it },
            onLayoutCommitted = {
                scope.launch { context.settingsStore.setHomeCreatorDataLayout(it) }
            },
            modifier = Modifier.weight(1f),
        )
    }

    ManagementAddCardSheet(
        layout = layout,
        cards = context.creatorCards,
        charts = context.creatorCharts,
        show = showAddSheet,
        onAdd = { entry ->
            val alreadyExists = layout.any { it.type == entry.type && it.value == entry.value }
            if (!alreadyExists || entry.type == ManagementLayoutType.DIVIDER) {
                val nextLayout = layout + entry
                layout = nextLayout
                scope.launch { context.settingsStore.setHomeCreatorDataLayout(nextLayout) }
            }
            showAddSheet = false
        },
        onDismiss = { showAddSheet = false },
    )
}

/** 首页创作者数据卡编辑工具栏，动作图标与管理页 TopAppBar 保持一致。 */
@Composable
private fun CreatorDataEditToolbar(onAdd: () -> Unit, onDone: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "正在编辑",
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd) {
                Icon(imageVector = MiuixIcons.Add, contentDescription = "添加数据卡")
            }
            IconButton(onClick = onDone) {
                Icon(imageVector = MiuixIcons.Ok, contentDescription = "完成编辑")
            }
        }
    }
}
