package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

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
    val backgroundColor = if (selected) {
        MiuixTheme.colorScheme.primaryContainer
    } else {
        MiuixTheme.colorScheme.secondary
    }
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MiuixTheme.colorScheme.onSecondaryVariant
    }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
            .clip(RoundedCornerShape(48.dp))
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
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(20.dp)
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
