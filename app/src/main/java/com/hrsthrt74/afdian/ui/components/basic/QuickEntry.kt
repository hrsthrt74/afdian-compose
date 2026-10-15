package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 圆形快捷入口：上面圆形底 + 图标，下面标题。
 *
 * 「我的」页的快捷入口区、首页「快捷方式」模块与它的自定义面板共用这一个组件，
 * 三处的视觉（圆形底色、图标尺寸、标题排版）因此不会走样。
 * 走预设目录的入口，标题/图标/跳转目标统一来自
 * [com.hrsthrt74.afdian.ui.navigation.QuickEntryPreset]；需要自己干点别的动作
 * （刷新、开外链……）的入口不进目录，在调用点直接手写 [onClick] 即可。
 *
 * @param icon 图标；不传时用同尺寸 Spacer 占位，保证一行入口的标题仍然对齐。
 * @param enabled 置 false 时不可点（面板里「已添加」的格子靠它挡住重复添加）。
 * @param containerSize 圆形底直径；[iconSize] 圆内图标边长。
 *        面板里的紧凑格子把这两个调小就是另一档尺寸，不必再写一个组件。
 */
@Composable
fun QuickEntry(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    iconTint: Color = MiuixTheme.colorScheme.primary,
    containerSize: Dp = 72.dp,
    iconSize: Dp = 32.dp,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 圆形裁切的 Box：图标坐里面居中；点击范围限定在圆形内（clip 在前，水波纹也被裁成圆形）
        Box(
            modifier = Modifier
                .size(containerSize)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(
                    enabled = enabled,
                    onClick = {
                        // 触感反馈内聚在组件里，调用方只管传动作
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                // 圆形底里的图标
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize),
                )
            } else {
                Spacer(modifier = Modifier.size(iconSize))
            }
        }
        // 圆形底下面的标题
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
    }
}
