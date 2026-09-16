package com.hrsthrt74.afdian.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import com.hrsthrt74.afdian.AppLog
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 配色过渡时长（毫秒）。
 * 比页面切换动画略短：点完「深色模式」要马上看到结果，拖太久反而显得迟钝。
 */
private const val ColorTransitionDurationMillis = 500

/**
 * 让包在里面的内容在「配色变化」时平滑过渡，而不是瞬间换色。
 *
 * 原理：Miuix 的 [Colors] 里每个色值都是独立的 state，[MiuixTheme] 在色板整体替换时
 * 直接写入新值，所以切深浅模式会「跳」一下。这里在本页范围内额外提供一份色板：
 * 侦测到外层主题换色后，用一条 0→1 的进度把「当前显示中的色板」逐帧插值到新色板，
 * 视觉上就是整页渐变。
 *
 * 只在设置页使用（这是唯一能改配色、且切换时用户看得见的页面）。相比放到全局主题里，
 * 这样过渡期间只重算本页，不会连带着把被设置页盖住的首页等四个 Tab 页面也每帧重组。
 *
 * @param content 需要平滑换色的页面内容。
 */
@Composable
fun ThemeColorTransition(content: @Composable () -> Unit) {
    val target = rememberTargetColors()
    val colors = rememberTransitionColors(target)
    MiuixTheme(colors = colors, content = content)
}

/**
 * 取外层全局主题当前色板的一份快照，作为过渡的终点。
 *
 * 终点不直接用页面里拿到的 colorMode / useAfdianColor：这两个流在 DataStore 首帧
 * 到达前还是初始值（例如 0=浅色），刚打开设置页时会先「假切换」一次，若跟着过渡
 * 就会在页面刚打开时看到一次多余的明暗渐变。全局主题已经在 AfdianTheme 里按真值
 * 建好，直接抄它的结果最稳，也能顺带覆盖「跟随系统」时系统深浅色翻转的情况。
 */
@Composable
private fun rememberTargetColors(): Colors {
    val outer = MiuixTheme.colorScheme
    // copy() 会读遍全部色值，于是订阅它们：全局主题一换色这里就产出一份新快照；
    // 没变化时 derivedStateOf 复用同一个实例，不会白白触发过渡。
    val snapshot = remember { derivedStateOf { outer.copy() } }
    return snapshot.value
}

/**
 * 把「目标色板」的变化摊平成一段渐变，返回当前这一帧应该显示的那份色板。
 *
 * current 始终等于屏幕上正在显示的样子：稳定态就是目标色板本身，过渡期间每帧
 * 被写进插值结果。因此过渡被中途打断（刚切深色又马上切回浅色）时，新一段过渡会
 * 从当前中间色起算，不会出现跳回旧色再重跑的跳变。
 */
@Composable
private fun rememberTransitionColors(target: Colors): Colors {
    var current by remember { mutableStateOf(target) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(target) {
        val start = current
        // 首次组合时 current 就是 target，没有「旧色板」可过渡，直接跳过
        if (start === target) return@LaunchedEffect
        AppLog.d("Theme", "配色过渡开始，时长 ${ColorTransitionDurationMillis}ms")
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(ColorTransitionDurationMillis, easing = FastOutSlowInEasing),
        ) {
            current = lerpColors(start, target, value)
        }
        // 收尾落位到目标实例本身，别停留在插值出来的等价对象上
        current = target
    }
    return current
}

/**
 * 逐色值插值两份色板。[fraction] 为 0 时完全是 [from]，为 1 时完全是 [to]。
 *
 * 例外：主色家族（primary 及其派生色 —— 禁用态、强调容器、下拉选中项、Slider 刻度点）
 * 直接取 [to] 的值一步到位，不参与渐变。强调色跟着背景慢慢变色观感很脏，
 * 换配色时开关 / 按钮这类元素直接跳到新色更干净。
 *
 * Miuix 的 [Colors] 没有提供批量插值，只能逐个字段算；字段列表与 Colors 构造参数
 * 一一对应，Miuix 升级新增色值时这里需要同步补上（漏了会编译报错，不会静默出错）。
 */
private fun lerpColors(from: Colors, to: Colors, fraction: Float): Colors = Colors(
    // —— 主色家族：瞬间切换，不参与渐变 ——
    // 强调色（开关 / 按钮 / Slider / 下拉选中项）跟着背景一起慢慢变色观感很脏，
    // 直接取目标值让它一步到位；只有中性色（背景、文字、卡片、分割线、遮罩）才渐变。
    primary = to.primary,
    onPrimary = to.onPrimary,
    primaryVariant = to.primaryVariant,
    onPrimaryVariant = to.onPrimaryVariant,
    error = lerp(from.error, to.error, fraction),
    onError = lerp(from.onError, to.onError, fraction),
    errorContainer = lerp(from.errorContainer, to.errorContainer, fraction),
    onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, fraction),
    // 主色家族：主操作色的禁用态与强调容器
    disabledPrimary = to.disabledPrimary,
    disabledOnPrimary = to.disabledOnPrimary,
    disabledPrimaryButton = to.disabledPrimaryButton,
    disabledOnPrimaryButton = to.disabledOnPrimaryButton,
    disabledPrimarySlider = to.disabledPrimarySlider,
    primaryContainer = to.primaryContainer,
    onPrimaryContainer = to.onPrimaryContainer,
    secondary = lerp(from.secondary, to.secondary, fraction),
    onSecondary = lerp(from.onSecondary, to.onSecondary, fraction),
    secondaryVariant = lerp(from.secondaryVariant, to.secondaryVariant, fraction),
    onSecondaryVariant = lerp(from.onSecondaryVariant, to.onSecondaryVariant, fraction),
    disabledSecondary = lerp(from.disabledSecondary, to.disabledSecondary, fraction),
    disabledOnSecondary = lerp(from.disabledOnSecondary, to.disabledOnSecondary, fraction),
    disabledSecondaryVariant = lerp(from.disabledSecondaryVariant, to.disabledSecondaryVariant, fraction),
    disabledOnSecondaryVariant = lerp(from.disabledOnSecondaryVariant, to.disabledOnSecondaryVariant, fraction),
    secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, fraction),
    onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, fraction),
    secondaryContainerVariant = lerp(from.secondaryContainerVariant, to.secondaryContainerVariant, fraction),
    onSecondaryContainerVariant = lerp(from.onSecondaryContainerVariant, to.onSecondaryContainerVariant, fraction),
    // 主色家族：下拉菜单选中项的底色与文字
    tertiaryContainer = to.tertiaryContainer,
    onTertiaryContainer = to.onTertiaryContainer,
    tertiaryContainerVariant = to.tertiaryContainerVariant,
    background = lerp(from.background, to.background, fraction),
    onBackground = lerp(from.onBackground, to.onBackground, fraction),
    onBackgroundVariant = lerp(from.onBackgroundVariant, to.onBackgroundVariant, fraction),
    surface = lerp(from.surface, to.surface, fraction),
    onSurface = lerp(from.onSurface, to.onSurface, fraction),
    surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, fraction),
    onSurfaceSecondary = lerp(from.onSurfaceSecondary, to.onSurfaceSecondary, fraction),
    onSurfaceVariantSummary = lerp(from.onSurfaceVariantSummary, to.onSurfaceVariantSummary, fraction),
    onSurfaceVariantActions = lerp(from.onSurfaceVariantActions, to.onSurfaceVariantActions, fraction),
    disabledOnSurface = lerp(from.disabledOnSurface, to.disabledOnSurface, fraction),
    surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, fraction),
    onSurfaceContainer = lerp(from.onSurfaceContainer, to.onSurfaceContainer, fraction),
    onSurfaceContainerVariant = lerp(from.onSurfaceContainerVariant, to.onSurfaceContainerVariant, fraction),
    surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, fraction),
    onSurfaceContainerHigh = lerp(from.onSurfaceContainerHigh, to.onSurfaceContainerHigh, fraction),
    surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, fraction),
    onSurfaceContainerHighest = lerp(from.onSurfaceContainerHighest, to.onSurfaceContainerHighest, fraction),
    outline = lerp(from.outline, to.outline, fraction),
    dividerLine = lerp(from.dividerLine, to.dividerLine, fraction),
    windowDimming = lerp(from.windowDimming, to.windowDimming, fraction),
    // 主色家族：Slider 刻度点（sliderBackground 是中性色，仍参与渐变）
    sliderKeyPoint = to.sliderKeyPoint,
    sliderKeyPointForeground = to.sliderKeyPointForeground,
    sliderBackground = lerp(from.sliderBackground, to.sliderBackground, fraction),
)
