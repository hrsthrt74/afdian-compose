package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.HistoryPreviewCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 首页浏览历史模块：标题入口与预览卡片均复用现有历史记录组件。 */
@Composable
fun HistoryModule(context: HomeModuleContentContext) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle("浏览历史", modifier = Modifier.padding(start = 4.dp))
            Row(
                modifier = Modifier
                    .clickable(
                        onClick = context.onOpenHistory,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    )
                    .padding(horizontal = 28.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("更多", style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.onBackgroundVariant)
                Icon(MiuixIcons.ChevronForward, contentDescription = null, tint = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.size(12.dp))
            }
        }
        HistoryPreviewCard(
            entries = context.historyEntries,
            avatarModel = context.historyAvatarModel,
            onOpenHistory = context.onOpenHistory,
            onOpenEntry = context.onOpenHistoryEntry,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
