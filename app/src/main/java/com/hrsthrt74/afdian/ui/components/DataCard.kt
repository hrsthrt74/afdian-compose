package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 公共数据卡片组件：显示标题和数值。
 * 样式对齐 ManagementScreen Overview 统计卡：
 *   - primary 色标题 + subtitle 样式
 *   - title1 大号数值（支持自适应字号）
 *   - 内边距 horizontal=14dp / vertical=12dp
 *
 * @param title 标题文本
 * @param value 数值文本
 * @param modifier 可选修饰符（如 weight(1f) 等）
 * @param insideMargin 卡片内部边距，默认 14dp 水平 / 12dp 垂直
 * @param titleColor 标题颜色，默认为主题 primary 色
 * @param titleStyle 标题文字样式，默认为 subtitle
 * @param valueStyle 数值文字样式，默认为 title1
 * @param minFontSize 数值自适应最小字号（px），默认 14sp，设为 null 则禁用自适应
 * @param maxFontSize 数值自适应最大字号（px），默认 32sp，仅当 minFontSize 非 null 时生效
 * @param colors 卡片颜色，虚影等特殊状态可以传入透明背景
 * @param subtitle 可选的补充说明，显示在数值下方
 * @param onLongClick 长按回调，适合进入卡片编辑模式
 */
@Composable
fun DataCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    titleColor: Color = MiuixTheme.colorScheme.primary,
    titleStyle: TextStyle = MiuixTheme.textStyles.subtitle,
    valueStyle: TextStyle = MiuixTheme.textStyles.title1,
    minFontSize: TextUnit? = 14.sp,
    maxFontSize: TextUnit = 32.sp,
    colors: CardColors = CardDefaults.defaultColors(),
    onClick: (() -> Unit)? = null,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        // 这是按压下沉
        // 所有数据卡片统一使用按压下沉反馈，包含不可点击的编辑态虚影。
        modifier = modifier.pressable(interactionSource = null, indication = SinkFeedback(), delay = null),
        colors = colors,
    ) {
        Box(
            // 这是点击特效
            modifier = if (onClick != null || onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = onClick ?: {},
                    onLongClick = onLongClick,
                )
            } else {
                Modifier
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    title,
                    color = titleColor,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    value,
                    style = valueStyle,
                    maxLines = 1,
                    autoSize = if (minFontSize != null) {
                        TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = maxFontSize)
                    } else {
                        null
                    }
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
