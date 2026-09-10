package com.hrsthrt74.afdian.ui.components

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * @param summary 可选引导语（如「喜欢 TA 的话，去 TA 的主页支持一下吧～」）
 * @param animated 是否播放「从中心放大 + 淡入」的浮现动画，默认开启
 * @param action 可选操作区（如重试按钮），渲染在引导语下方，占满可用宽度（两侧留白）
 */
@Composable
fun EmptyStateView(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = MiuixIcons.Light.Info,
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
            val scale = 0.9f + 0.2f * progress.value
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 空态插画图标
            Icon(icon, null, Modifier.size(96.dp), MiuixTheme.colorScheme.primaryContainer)
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
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    action()
                }
            }
            Spacer(Modifier.height(36.dp)) // 视觉补偿
        }
    }
}
