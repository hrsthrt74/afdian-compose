package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分类入口左侧的彩色图标块：32dp squircle 底 + 白色单色图标。
 *
 * 抽成共用组件的理由：「设置 → 总览」的分类卡与「我的 → 文档」的文档卡
 * 用的是同一套 HyperOS 风格彩色方块，分开各写一份迟早漂移（比如 Monet 适配只改了一边）。
 *
 * @param icon 白色单色图标
 * @param iconBackground 图标底色（沿用参考截图里的 MIUI 彩色方块）；
 *        Monet 配色开启时会被主题主色统一覆盖
 */
@Composable
internal fun CategoryIconTile(
    icon: ImageVector,
    iconBackground: Color,
    modifier: Modifier = Modifier,
) {
    // Monet 配色开启时，图标块统一使用主题主色，避免固定彩色和动态色板打架；
    // 关闭 Monet 时保留参考截图里的自定义配色。
    val monetActive = MiuixTheme.isDynamicColor
    // 方块底色：Monet 时取 primary，否则用调用方给的自定义色
    val tileColor = if (monetActive) MiuixTheme.colorScheme.primary else iconBackground
    // 图标颜色：主色底上改用 onPrimary 保证对比度；自定义色底继续用白色
    val iconTint = if (monetActive) MiuixTheme.colorScheme.onPrimary else Color.White

    Box(
        modifier = modifier
            .padding(end = 4.dp)    // 图标 <-> 文字
            .size(32.dp)
            .squircleClip(8.dp)
            .background(color = tileColor),
        contentAlignment = Alignment.Center,
    ) {
        // 分类图标：装饰性，标题已经说明了入口含义
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
    }
}
