package com.hrsthrt74.afdian.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/** 选中态配色过渡时长：比尾部图标出场动画（220ms）更短，让变色先到位、图标随后落位。 */
private const val FilterChipColorTransitionDurationMs = 150

/** 通用筛选 Chip：统一消息页与列表页的选中颜色、触觉和按压反馈。 */
@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    onTrailingIconClick: (() -> Unit)? = null,
) {
    // 选中态配色：底色与内容色都用短过渡补间，切换选中态时不再「啪」地硬切；
    // 主题切换导致 colorScheme 变化时也会顺带平滑补间，不影响原有观感
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primaryContainer
        } else {
            MiuixTheme.colorScheme.secondary
        },
        animationSpec = tween(FilterChipColorTransitionDurationMs),
        label = "filterChipBackgroundColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.onPrimaryContainer
        } else {
            MiuixTheme.colorScheme.onSecondaryVariant
        },
        animationSpec = tween(FilterChipColorTransitionDurationMs),
        label = "filterChipContentColor",
    )
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
            .clip(Capsule())    // 平滑胶囊
            .background(backgroundColor)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                autoSize = TextAutoSize.StepBased(10.sp, 14.sp),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 尾部图标槽位：图标可能从「无」变成「有」（例如进入删除态才冒出关闭图标），
            // 用 AnimatedContent 默认预设（淡入 + 轻微放大 + 尺寸过渡）让出现/消失不再突兀；
            // trailingIcon 为 null 时槽位不渲染内容，尺寸收缩到 0，胶囊宽度随之平滑变化
            AnimatedContent(
                targetState = trailingIcon,
                contentAlignment = Alignment.Center,
                label = "filterChipTrailingIcon",
            ) { icon ->
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(start = 6.dp)
                            .then(
                                if (onTrailingIconClick != null) {
                                    Modifier.clickable(
                                        interactionSource = null,
                                        indication = null,
                                        onClick = onTrailingIconClick,
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
            }
        }
    }
}
