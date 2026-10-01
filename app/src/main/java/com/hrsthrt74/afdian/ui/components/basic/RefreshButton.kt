package com.hrsthrt74.afdian.ui.components.basic

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hrsthrt74.afdian.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** 日志 TAG */
private const val RefreshButtonLogTag = "RefreshButton"

/** 每转一圈的时长（毫秒）：线性匀速，与原首页手写刷新动画保持一致 */
private const val RefreshButtonSpinDurationMs = 900

/**
 * 停止滑行的时长倍数：必须与 [RefreshButtonStopEasing] 的初始斜率（0.25 / 0.2 = 1.25）联动。
 *
 * 推导：滑行起步的角速度 = 剩余角度 × 曲线初始斜率 ÷ 时长。要让起步角速度恰好等于
 * 匀速转圈的角速度（360° ÷ [RefreshButtonSpinDurationMs]），时长就得是「匀速折算」的 1.25 倍。
 * 两者一个改动另一个必须跟着改，否则停止瞬间会出现速度突变（生硬感）。
 *
 * 倍数越小停得越利落（1.0 = 匀速折算时长，但尾段减速会很急）；1.25 时平均滑行约 560ms、
 * 视觉上约 400ms 就基本停稳，兼顾利落与平滑。
 */
private const val RefreshButtonStopDurationScale = 1.25f

/**
 * 停止归位的减速曲线：前快后慢，角速度平滑衰减到 0。
 *
 * 初始斜率 1.25（与 [RefreshButtonStopDurationScale] 联动，保证起步速度与匀速转圈无缝衔接）、
 * 结尾斜率 0（缓缓停住）。相比角速度线性衰减（初始斜率 2、平均滑行 900ms），这条曲线把
 * 大部分角度放在前半程滑完，停止明显更利落，尾段仍保留一小段减速缓冲不显急刹。
 */
private val RefreshButtonStopEasing = CubicBezierEasing(0.2f, 0.25f, 0.75f, 1f)

/** 「刷新完成」后转圈图标的最短停留时长（毫秒）：即使请求瞬间完成，也至少转满一圈，避免一闪而过 */
private const val RefreshButtonMinSpinDurationMs = 1000L

/** 可用/禁用两态之间图标着色的渐变过渡时长（毫秒），与 CopyButton 保持一致 */
private const val RefreshButtonTintTransitionDurationMs = 200

/** 按字重取「刷新」图标：注意 MiuixIcons.Refresh 本身即 Regular 档 */
private val MiuixIconWeight.refreshIcon: ImageVector
    get() = when (this) {
        MiuixIconWeight.Light -> MiuixIcons.Light.Refresh
        MiuixIconWeight.Normal -> MiuixIcons.Normal.Refresh
        MiuixIconWeight.Regular -> MiuixIcons.Regular.Refresh
        MiuixIconWeight.Medium -> MiuixIcons.Medium.Refresh
        MiuixIconWeight.Demibold -> MiuixIcons.Demibold.Refresh
    }

/**
 * 刷新按钮：点击触发 [onClick]，[refreshing] 为 true 时图标匀速转圈。
 *
 * 交互链路（点击 → 震动 → 回调 → 转圈）：
 * 1. 点击立刻触发一次触觉反馈（与 CopyButton 手感一致）；
 * 2. 回调 [onClick] 交给调用方发起刷新，组件不感知业务细节；
 * 3. [refreshing] 变 true 后图标开始转圈；变 false 后「至少转满 [RefreshButtonMinSpinDurationMs]」
 *    才停——请求瞬间完成时图标不会一闪而过；
 * 4. 停止时以与转圈相同的角速度起步、角速度平滑衰减到 0，减速滑行到整圈归位：
 *    视觉上是「利落但柔和地停住」而非急刹，也不会从任意角度硬跳回 0°。
 *
 * 状态归属：是否在刷新由调用方传入（一般接 ViewModel 的 loading/running 状态），
 * 组件内部只维护「转圈动画的开与停」。
 *
 * 使用示例：
 * ```kotlin
 * RefreshButton(refreshing = loading, onClick = viewModel::refresh)
 * RefreshButton(refreshing = running, onClick = onRerun, iconWeight = MiuixIconWeight.Demibold)
 * ```
 *
 * @param refreshing 是否正在刷新；true 时图标转圈并吞掉重复点击（不置灰，保持转圈观感）
 * @param onClick 点击回调，触发刷新动作
 * @param modifier 外部修饰符
 * @param enabled 是否可点击；false 时图标置灰且不可点，与 [refreshing] 互不影响
 * @param iconWeight 图标字重，默认 [MiuixIconWeight.Regular]（等同不带字重的 MiuixIcons.Refresh）
 * @param contentDescription 无障碍描述（转圈时会自动附加状态后缀）
 */
@Composable
fun RefreshButton(
    refreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconWeight: MiuixIconWeight = MiuixIconWeight.Regular,
    contentDescription: String = "刷新",
) {
    val haptic = LocalHapticFeedback.current

    // 组件内部的实际转圈开关：比 refreshing 多活一段「最短转圈时长」，见下方 LaunchedEffect
    var spinning by remember { mutableStateOf(false) }
    // 本次刷新的开始时刻（elapsedRealtime 不受用户改系统时间影响）
    var startedAt by remember { mutableLongStateOf(0L) }

    // 旋转角度：用 Animatable 而非 InfiniteTransition，为了停止时能拿到当前角度、就近补齐整圈
    val rotation = remember { Animatable(0f) }

    // spinning 的开与停：
    // - refreshing false→true：立刻记录开始时刻并开转；
    // - refreshing true→false：若本次刷新不足最短时长，delay 补齐剩余时间再停（协程被取消时自动跳过）
    LaunchedEffect(refreshing) {
        if (refreshing) {
            startedAt = SystemClock.elapsedRealtime()
            spinning = true
        } else if (spinning) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            delay((RefreshButtonMinSpinDurationMs - elapsed).coerceAtLeast(0L).milliseconds)
            spinning = false
        }
    }

    // 转圈与归位共用一个 effect：spinning 变化时旧协程被取消、新协程接管，天然无并发竞争
    LaunchedEffect(spinning) {
        if (spinning) {
            // 转圈：按固定角速度一圈接一圈地转；每圈从剩余角度起算时长，保证全程匀速无跳变
            while (isActive) {
                val start = rotation.value
                val durationMs = ((360f - start) / 360f * RefreshButtonSpinDurationMs).roundToInt()
                rotation.animateTo(360f, tween(durationMs.coerceAtLeast(1), easing = LinearEasing))
                // 360° 与 0° 同相位，归零不产生视觉跳变
                rotation.snapTo(0f)
            }
        } else {
            // 减速滑行归位：起步角速度与匀速转圈严格一致（速度无突变），随后角速度线性
            // 衰减到 0，最终停在整圈上（图标正立，snapTo 归零不产生视觉跳变）。
            // 时长按「匀速折算 × RefreshButtonStopDurationScale」取，正是初速衔接的条件。
            val current = rotation.value
            val target = ceil(current / 360f) * 360f
            if (target > current) {
                val durationMs = (target - current) / 360f *
                    RefreshButtonSpinDurationMs * RefreshButtonStopDurationScale
                rotation.animateTo(
                    target,
                    tween(durationMs.roundToInt().coerceAtLeast(1), easing = RefreshButtonStopEasing),
                )
            }
            rotation.snapTo(target % 360f)
        }
    }

    // 图标着色：可用态用 onSurface（等同 Miuix Icon 默认色），禁用态置灰；
    // 渐变过渡时长与 CopyButton 一致。转圈中不算禁用，保持正常色
    val iconTint by animateColorAsState(
        targetValue = if (enabled) {
            MiuixTheme.colorScheme.onSurface
        } else {
            MiuixTheme.colorScheme.disabledOnSecondaryVariant
        },
        animationSpec = tween(RefreshButtonTintTransitionDurationMs),
        label = "refreshButtonIconTint",
    )

    IconButton(
        onClick = {
            // 刷新进行中吞掉重复点击，避免重入；不置灰，保持转圈观感
            if (refreshing) return@IconButton
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            AppLog.d(RefreshButtonLogTag, "触发刷新")
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            imageVector = iconWeight.refreshIcon,
            contentDescription = if (spinning) "$contentDescription（进行中）" else contentDescription,
            tint = iconTint,
            // 在 graphicsLayer 里读角度：每帧只走绘制阶段，不触发重组
            modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
        )
    }
}
