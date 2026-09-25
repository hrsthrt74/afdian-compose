package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 顶栏和底栏统一使用的普通模糊半径，单位为 dp。 */
const val AppBlurRadius = 100f

/** 渐进模糊半径，单位为 dp。 */
const val AppProgressiveBlurRadius = 15f

/** 渐变色蒙层强端的 alpha：栏的模糊一侧（顶栏是上缘，底栏是下缘）。 */
const val AppBarTintStrongAlpha = 0.9f

/** 渐变色蒙层弱端的 alpha：0 表示完全透明，与未加蒙层的原图无缝衔接。 */
const val AppBarTintWeakAlpha = 0f

@Composable
fun rememberBlurBackdrop(enabled: Boolean): LayerBackdrop? {
    if (!enabled || !isRuntimeShaderSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

/** 模糊开启时必须让栏本身透明，否则不透明的默认背景会遮住模糊层。 */
@Composable
fun blurBarColor(backdrop: LayerBackdrop?): Color =
    if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

/**
 * 主页面底部内容的预留高度。
 * Pager 必须延伸到导航栏后方供 backdrop 采样，因此由页面内容自己避让底栏。
 */
@Composable
fun contentBottomPadding(): Dp {
    val systemNavigationPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    return NavigationBarDefaults.ItemHeight + systemNavigationPadding + 16.dp
}

@Composable
private fun blurColors(alpha: Float): BlurColors = BlurDefaults.blurColors(
    blendColors = listOf(BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = alpha)))
)

/** 底栏和顶栏普通模糊共用的颜色配置。 */
@Composable
fun blurBarColors(): BlurColors = blurColors(0.7f)

/**
 * 渐进模糊使用的空色配置：渐变模式下栏的颜色交给 [barTintBrush] 的独立渐变层，
 * 模糊只负责糊化本身，不再顺带上色。
 *
 * miuix 会把 blend 颜色折进渐进模糊自己的 ramp pass，颜色形状因此被模糊斜坡绑死；
 * 再加上 `startFraction = 0.5` 让栏的强端出现一整段恒定平台期，看起来就是一块纯色板。
 *
 * 用空列表而非 alpha = 0 的色值：`blendColors()` 见到空列表会直接返回，ramp 上色 pass 不会生成。
 */
@Composable
fun blurBarColorsNoTint(): BlurColors = BlurDefaults.blurColors()

/**
 * 栏的渐变色蒙层：强端 alpha 为 [strongAlpha]，弱端为 [weakAlpha]，中间连续过渡。
 *
 * [topHeavy] = true 时上浓下淡（顶栏），false 时下浓上淡（底栏）。
 * 两端统一用 `surface.copy(alpha = ...)` 而不是 `Color.Transparent`：后者的 RGB 是 0，
 * 渐变中段会混出一层灰调。
 *
 * 这一层与模糊斜坡完全解耦：模糊改方向、改 curve 都不会再改变颜色的分布。
 */
@Composable
fun barTintBrush(
    topHeavy: Boolean = true,
    strongAlpha: Float = AppBarTintStrongAlpha,
    weakAlpha: Float = AppBarTintWeakAlpha,
): Brush {
    val surface = MiuixTheme.colorScheme.surface
    val edges = listOf(surface.copy(alpha = strongAlpha), surface.copy(alpha = weakAlpha))
    return Brush.verticalGradient(if (topHeavy) edges else edges.reversed())
}

/**
 * 底栏仍在使用的颜色配置：`PostDetailScreen` / `MessageConversationScreen` 里手写的渐进模糊
 * 还在调用它（顶栏已改用 [barTintBrush] 的独立渐变层）。
 */
@Composable
fun progressiveBlurBarColors(): BlurColors = blurColors(0.5f)

/** 顶栏统一模糊实现：渐进模糊仅在 progressive=true 时启用。 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    progressive: Boolean,
    content: @Composable () -> Unit,
) {
    val active = blurEnabled && backdrop != null
    Box(
        modifier = when {
            // 普通模糊：整条栏交给 textureBlur。
            // 栏体自身（TopAppBar 用 blurBarColor 置透明）会透出这层模糊
            active && !progressive -> Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = AppBlurRadius,
                colors = blurBarColors(),
            )
            // 渐进模糊：由内部两层 matchParentSize 子 Box 负责，外层不加 modifier
            active -> Modifier
            // 模糊不可用（legacy 包体 / 用户关闭模糊效果 / 设备不支持 runtime shader）：
            // 整条栏铺一层不透明底色兜底。TopAppBar 只会给自己的节点上 surface，
            // 额外挂在它下面的内容（TabRow、表头等兄弟节点）无人上色，会纯透明、滚动内容从背后穿过。
            else -> Modifier.background(MiuixTheme.colorScheme.surface)
        },
    ) {
        if (active && progressive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = AppProgressiveBlurRadius,
                        gradient = ProgressiveBlur(angle = 90f, startFraction = 0f, endFraction = 1f, curve = 3f),
                        colors = blurBarColorsNoTint(),
                        noiseCoefficient = 0.1f,
                    ),
            )
            // 颜色层独立于模糊斜坡：全程连续的上浓下淡，消除平台期造成的“纯色板”观感
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(barTintBrush()),
            )
        }
        content()
    }
}
