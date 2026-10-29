package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.LocalContentColor

/**
 * 设置项目左侧的图标：原生 [Icon] + 尾部 8dp 间距。
 *
 * 抽成共用组件的理由：「设置 → 总览」的分类卡、账号列表、各设置子页的
 * `BasicComponent` 起手槽都在重复写 `Icon(..., Modifier.padding(end = 8.dp))`，
 * 间距值一散落各处就会漂移。
 *
 * 与 [CategoryIconTile] 的区别：这里**不带**彩色方块背景，图标直接用原生
 * [Icon] 绘制（默认跟随 [LocalContentColor]），只在尾部补 8dp 与标题拉开距离。
 *
 * 用法（放进 `BasicComponent` 的 `startAction` 槽）：
 * ```
 * startAction = {
 *     SettingItemIcon(imageVector = MiuixIcons.Settings)
 * }
 * ```
 * 不需要图标但想和同级项的左对齐保持一致时，可以什么都不传：
 * ```
 * startAction = { SettingItemIcon() }
 * ```
 *
 * @param imageVector 要绘制的矢量图标；为 null 时用一个 24dp 的空占位替代
 *        （保持与同级设置项相同的缩进，尾部 8dp 间距照常保留）
 * @param modifier 作用于图标（或占位）自身的 Modifier；传入的尺寸/点击态会先生效，
 *        8dp 尾部间距始终由本组件追加
 * @param tint 图标着色，默认取 Miuix 的 [LocalContentColor]，与原生 [Icon] 一致；
 *        传 [Color.Unspecified] 表示不着色（多色图标）。无图标时该参数不生效
 */
@Composable
internal fun SettingItemIcon(
    imageVector: ImageVector? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    // 尾部 8dp 间距先算好，图标分支和占位分支共用，保证两种形态的缩进完全一致
    val iconModifier = modifier.padding(end = 8.dp)

    if (imageVector == null) {
        // 无图标：用 24dp 空白占位（与 Miuix 图标默认视觉尺寸一致），
        // 位置和间距都留着，避免同行其他设置项的标题左右跳动
        Spacer(iconModifier.width(24.dp))
    } else {
        Icon(
            imageVector = imageVector,
            // 纯装饰性图标：入口含义由旁边的标题文字说明，不额外朗读
            contentDescription = null,
            tint = tint,
            modifier = iconModifier,
        )
    }
}
