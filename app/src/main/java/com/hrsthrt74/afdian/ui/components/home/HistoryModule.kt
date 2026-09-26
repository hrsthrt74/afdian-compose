package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.HistoryPreviewCard

/** 首页浏览历史模块：标题入口与预览卡片均复用现有历史记录组件。 */
@Composable
fun HistoryModule(context: HomeModuleContentContext) {
    Column {
        ClickableSmallTitle(
            title = "浏览历史",
            onClick = context.onOpenHistory,
        )
        HistoryPreviewCard(
            entries = context.historyEntries,
            onOpenHistory = context.onOpenHistory,
            onOpenEntry = context.onOpenHistoryEntry,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
