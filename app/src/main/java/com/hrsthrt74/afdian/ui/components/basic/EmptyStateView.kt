package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通用空状态视图：居中的大图标 + 标题 + 可选引导语。
 * 样式约定与私信页空态一致：96dp 图标（primaryContainer 着色）、title2 标题、body2 摘要。
 * 被组合时自带「从中心放大 + 淡入」的浮现动画（0.8 倍缩放 → 1 倍，透明度 0 → 1）。
 *
 * @param title 空态标题（如「暂无发电记录」）
 * @param modifier 铺满方式由调用方决定（fillMaxSize / fillMaxWidth + fillMaxHeight 等）
 * @param icon 空态图标，默认 Info（问号圆圈）
 * @param iconModifier 作用于图标本身的额外变换（如旋转），默认无
 * @param summary 可选引导语（如「喜欢 TA 的话，去 TA 的主页支持一下吧～」）
 * @param animated 是否播放「从中心放大 + 淡入」的浮现动画，默认开启
 * @param action 可选操作区（如重试按钮），渲染在引导语下方，占满可用宽度（两侧留白）
 */
@Composable
fun EmptyStateView(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = MiuixIcons.Light.Info,
    iconModifier: Modifier = Modifier,
    summary: String? = null,
    animated: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    // 浮现动画进度：0 → 1，用 Animatable 而不是 animateFloatAsState，
    // 因为后者首次组合时直接落在目标值、不会播放进场动画。
    // animated=false 时直接从 1 开始，不播动画
    val progress = remember { Animatable(if (animated) 0f else 1f) }
    if (animated) {
        LaunchedEffect(Unit) {
            progress.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            // 淡入：透明度跟随进度
            alpha = progress.value
            // 从中心放大：0.8 倍 → 1 倍（默认 transformOrigin 即自身中心）
            val scale = 0.9f + 0.1f * progress.value
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 空态插画图标（iconModifier 供调用方做旋转等额外变换）
            Icon(icon, null, Modifier
                .size(96.dp)
                .then(iconModifier), MiuixTheme.colorScheme.primaryContainer)
            Spacer(modifier = Modifier.height(16.dp))
            // 空态标题
            Text(title, style = MiuixTheme.textStyles.title2)
            // 引导语（有内容才显示）
            if (summary != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
            }
            // 操作区（如重试按钮），占满宽度、两侧留白
            if (action != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)) {
                    action()
                }
            }
            Spacer(Modifier.height(36.dp)) // 视觉补偿
        }
    }
}

/**
 * 预设错误态视图：[EmptyStateView] 的「加载失败」预设版本。
 * 标题固定为「加载失败」，并内置一个占满宽度的「重试」按钮，
 * 免去各页面重复拼装 `action = { Button(...) { Text("重试") } }`。
 *
 * 图标约定：miuix-icons 里没有 Warning / Alert / Error 语义的图标，
 * 因此默认沿用 Info 图标并旋转 180° 作为错误态图标。
 * 若自行传入其它图标且不希望被旋转，把 [iconRotation] 传 0f 即可。
 *
 * 与 [EmptyStateView] 共用同一套排版（96dp 图标 + title2 标题 + body2 摘要）与浮现动画。
 *
 * 用法：
 * ```kotlin
 * ErrorStateView(
 *     onRetry = viewModel::load,
 *     modifier = Modifier.fillParentMaxSize(), // LazyColumn item 内撑满视口；普通容器用 fillMaxSize
 *     summary = error,                          // 可选，一般直接透传当前错误文案
 * )
 * ```
 *
 * @param onRetry 点击「重试」按钮的回调（通常直接接 ViewModel 的加载方法）
 * @param modifier 铺满方式由调用方决定（fillParentMaxSize / fillMaxSize 等）
 * @param summary 可选错误摘要（如网络异常文案），展示在标题下方
 * @param title 错误态标题，默认「加载失败」
 * @param icon 错误态图标，默认 Info（配合 [iconRotation] 旋转 180° 充作错误图标）
 * @param iconRotation 图标旋转角度，默认 180f；传入自定义图标时可设为 0f 关闭旋转
 * @param retryText 重试按钮文案，默认「重试」
 * @param animated 是否播放「从中心放大 + 淡入」的浮现动画，默认开启
 */
@Composable
fun ErrorStateView(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    title: String = "加载失败",
    icon: ImageVector = MiuixIcons.Light.Info,
    iconRotation: Float = 180f,
    retryText: String = "重试",
    animated: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    EmptyStateView(
        title = title,
        modifier = modifier,
        icon = icon,
        // 错误图标：Info 原地旋转 180°（ ! ）
        iconModifier = Modifier.graphicsLayer { rotationZ = iconRotation },
        summary = summary,
        animated = animated,
        // 预设操作区：占满宽度的重试按钮
        action = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onRetry()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(retryText)
            }
        },
    )
}
