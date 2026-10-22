package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.basic.QuickEntry
import com.hrsthrt74.afdian.ui.navigation.normalizeQuickEntries
import com.hrsthrt74.afdian.ui.navigation.resolveIcon
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 首页「快捷方式」模块：一行最多 4 个自定义入口。
 *
 * 入口来自预设目录 [com.hrsthrt74.afdian.ui.navigation.QuickEntryPreset]，
 * 选哪几个、什么顺序存在 DataStore（由 [QuickEntryPickerSheet] 负责改），
 * 读取由首页容器统一做，再经 [HomeModuleContentContext.quickEntryKeys] 下发到这里。
 */
@Composable
fun QuickEntriesModule(context: HomeModuleContentContext) {
    // 入口配置由首页容器统一订阅后传进来：容器要等它到齐才铺模块，
    // 这里再用可空兜一层，保证任何情况下都不会渲染出一个 0 高度的空壳
    // （空壳会被 LazyColumn 记成「首个可见项」，等真值把模块排到后面时列表会自己滚过去）。
    val entries = context.quickEntryKeys?.let { normalizeQuickEntries(it) } ?: return

    Column {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle("快捷方式", modifier = Modifier.padding(start = 4.dp))
            // 编辑
            Row(
                modifier = Modifier
                    .padding(horizontal = 28.dp, vertical = 8.dp)
                    .clickable(
                        onClick = context.onEditQuickEntries,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "编辑",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Icon(   // 箭头
                    MiuixIcons.Demibold.ChevronForward,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.size(12.dp),
                )
            }

        }
        // 入口行：均分宽度
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            entries.forEach { preset ->
                QuickEntry(
                    title = preset.title,
                    modifier = Modifier.weight(1f),
                    icon = preset.resolveIcon(),
                    onClick = { context.onNavigate(preset.route) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))  // 因为上面是文字所以多留一点
    }
}
