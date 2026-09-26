package com.hrsthrt74.afdian.ui.util

import androidx.compose.runtime.Composable
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults.overlayColors
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 弹层（Dialog / BottomSheet / Popup）内 Card 的统一配色。
 *
 * 为什么必须单独指定颜色：这些弹层的容器背景取的是 Miuix 的 `background`，
 * 而 Card 的默认色是 `surfaceContainer`，两者在官方默认色板下数值完全相同
 * （浅色都是纯白 #FFFFFF、深色都是 #242424），所以默认 Card 放进弹层会与容器
 * 融为一体、直接「看不见」，必须显式给一个与弹层底有层次差异的颜色。
 *
 * 因此弹层内的 Card 一律改用 [overlayColors]：`secondaryContainer` 作底
 * （浅 #F0F0F0 / 深 #434343）、`onSurfaceContainerHighest` 作内容色
 * （浅 Black / 深 #E9E9E9），明暗两种模式下都能与弹层背景分离。
 *
 * 注意：本 util 只用于「弹层内部」的 Card。页面（Screen）内的 Card 背景本就
 * 与页面底不同，请保持 Miuix 默认色，不要全项目无脑替换。
 */
object AfdianCardDefaults {

    /**
     * 弹层内 Card 的统一颜色。
     *
     * 直接复用 Miuix 的 [CardDefaults.defaultColors]：它内部已按
     * color / contentColor 做 remember 缓存，切换主题色时会正确重建，
     * 因此这里无需自己再套一层 remember。
     */
    @Composable
    fun overlayColors(): CardColors = CardDefaults.defaultColors(
        color = MiuixTheme.colorScheme.secondaryContainer,
        contentColor = MiuixTheme.colorScheme.onSurfaceContainerHighest,
    )
}
