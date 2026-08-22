package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/** 渐进模糊使用的颜色配置。 */
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
        modifier = if (active && !progressive) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = AppBlurRadius,
                 colors = blurBarColors(),
            )
        } else Modifier,
    ) {
        if (active && progressive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = AppProgressiveBlurRadius,
                        gradient = ProgressiveBlur(angle = 90f, startFraction = 0.5f, endFraction = 1f, curve = 0.8f),
                         colors = progressiveBlurBarColors(),
                        noiseCoefficient = 0.1f,
                    ),
            )
        }
        content()
    }
}
