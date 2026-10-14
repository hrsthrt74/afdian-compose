package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 可点击的 SmallTitle 组件
 * 左侧显示标题，右侧显示"更多"文本和箭头图标，整个区域可点击
 *
 * @param title 标题文本
 * @param onClick 点击回调
 * @param modifier Modifier 修饰符
 * @param actionText 右侧操作文本，默认为"更多"
 */
@Composable
fun ClickableSmallTitle(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionText: String = "更多",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧标题
        SmallTitle(title, modifier = Modifier.padding(start = 4.dp))

        // 右侧操作区域
        Row(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 操作文本
            Text(
                actionText,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            // 箭头图标
            Icon(
                MiuixIcons.Demibold.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}