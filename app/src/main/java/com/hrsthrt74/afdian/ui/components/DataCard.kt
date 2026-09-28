package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
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
 * @param unit 数值的后导单位（如「元」「次」「%」），显示在数值右侧；留空则不渲染
 * @param unitStyle 单位文字样式，默认 footnote1，弱化层级
 * @param unitColor 单位颜色，默认次级文本色，避免和数值抢视觉
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
    unit: String? = null,
    unitStyle: TextStyle = MiuixTheme.textStyles.footnote1,
    unitColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
    // autoSize 缩的是整个文本行：字号从 32sp 掉到 14sp 时，数值行的高度也一起塌下去，
    // 同一行的卡片就会一高一矮。这里按「最大字号」把数值行锁成固定高度：
    // 字号变小只是上方留白变多，卡片总高不变、数字底边保持贴底。
    // 行高优先沿用样式自带的 sp 值；样式没指定时按 1.4 倍最大字号兜底（略大于自然行高，避免挤到字形）。
    val valueLineHeight =
        if (valueStyle.lineHeight.type == TextUnitType.Sp) valueStyle.lineHeight
        else maxFontSize * 1.4f
    val valueRowHeight = with(LocalDensity.current) { valueLineHeight.toDp() }
    // 数值文本自身改用自然行高：行高一事交给外层 Box 钉死，
    // 文本才可能在行内往下靠，否则会被 lineHeight 顶在行框中间。
    val valueTextStyle = if (minFontSize != null) {
        valueStyle.copy(lineHeight = TextUnit.Unspecified)
    } else {
        valueStyle
    }
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
                // 外层再兜一层固定高度：数值行处在基线对齐模式下，行高由 Row 重新计算，
                // 单靠 TextStyle.lineHeight 未必压得住，这里直接把这一行的高度钉死。
                // 靠下对齐：字号被缩小时多出来的留白全留给上方，
                // 数字底边（连同右侧单位的基线）保持贴在这一行的底部，不会悬在中间。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (minFontSize != null) Modifier.height(valueRowHeight) else Modifier
                        ),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Row(
                        // 单位需要和数值同底线，因此这一行整体按基线对齐排版
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            value,
                            // weight 让数值吃掉单位以外的全部宽度去做自适应；fill=false 保证数值短时仍靠左，
                            // 不被拉满整行，和没有单位时的观感保持一致。
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .alignByBaseline(),
                            style = valueTextStyle,
                            maxLines = 1,
                            autoSize = if (minFontSize != null) {
                                TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = maxFontSize)
                            } else {
                                null
                            }
                        )
                        // 单位不带 weight，会先于数值测量，任何情况下都完整显示、不会被压缩或截断。
                        if (!unit.isNullOrBlank()) {
                            Text(
                                unit,
                                modifier = Modifier.alignByBaseline(),
                                style = unitStyle,
                                color = unitColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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
