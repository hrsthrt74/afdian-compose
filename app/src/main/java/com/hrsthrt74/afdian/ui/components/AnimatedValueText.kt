package com.hrsthrt74.afdian.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 数字滚动动画总开关（组合局部）。
 *
 * 由 MainActivity 订阅设置页的「数字滚动动画」开关后，用 CompositionLocalProvider 向下提供。
 *
 * 这里刻意用 CompositionLocal 而不是「进程级可变 state」：后者需要手动往全局对象写一次，
 * 写入时机一旦没赶上（开关变化时恰好没有重组到写入方），卡片就会一直读着旧值继续播动画，
 * 表现为「开关关了动画还在播」。CompositionLocal 则把开关变成正常的组合输入，
 * 谁读谁订阅，开关一变必然重组，不依赖任何手动写入。
 *
 * 默认值为 true（未提供时按「开启动画」处理），与开关本身的默认值一致。
 * 关闭后数字回退为普通文本渲染（整串 Text + 自适应字号），也就是引入滚轮之前的显示逻辑。
 */
internal val LocalRollingNumberEnabled = compositionLocalOf { true }

// 数字滚轮的日志 tag 收归 AppLog.TAG_ODOMETER 统一定义：
// 日志页的「数字滚轮日志」开关按这个 tag 静音，两边必须共用同一常量。

/** 数字滚轮：主滚动时长（毫秒）。加上回弹落位后总时长约 460ms。 */
private const val OdometerRollDurationMillis = 340

/**
 * 数字滚轮：回弹落位时长（毫秒）。
 * 这段越短，同样的过冲量看起来越不像「弹」：回落快，就只是一次干脆的落位。
 */
private const val OdometerSettleDurationMillis = 120

/** 数字滚轮：相邻两位之间的错峰间隔（毫秒），形成从左往右依次停下的机械表盘感。 */
private const val OdometerRollStaggerMillis = 30

/**
 * 数字滚轮：滚过目标后回弹的过冲量（格）。
 * 0.06 格约十六分之一位数字：只剩「一点点点」，是收尾时几乎察觉不到的一个轻顿。
 */
private const val OdometerRollOvershootLines = 0.06f

/**
 * 数字滚轮：运动模糊的「曝光时长」（秒）。
 *
 * 模糊半径 = 滚动速度 × 这个时长（换算成实际位移），于是滚得越快糊得越厉害；
 * 减速时半径自己收小，落位那一刻就是清晰的。
 */
/**
 * 数字滚轮：运动模糊的「曝光比例」。
 *
 * 模糊半径 = 这一帧滚过的距离 × 这个比例。滚得快，一帧滚得远，就糊得厉害；
 * 减速时每帧位移变小，半径自己收小，落位那一刻就是清晰的。
 */
private const val OdometerSmearExposure = 0.25f

/**
 * 数字滚轮：横向模糊半径相对纵向的比例。
 *
 * 必须是**非零**：Compose 的 `Modifier.blur` 内部要求 x/y 半径都大于 0，否则直接把
 * renderEffect 置为 null（等于完全不糊）。所以哪怕只想要纵向模糊，也得给横向一个小的非零值。
 */
private const val OdometerSmearHorizontalRatio = 0.25f

/** 数字滚轮：模糊半径上限（dp）。再大就糊到看不清数字了。 */
private const val OdometerSmearMaxRadiusDp = 10f

/**
 * 数字滚轮：上下边界淡出区占格子高度的比例。
 *
 * 上下各 12% 正好落在行框的留白里（数字字形本身没占满整行），
 * 所以静止时看不出变化，只有滚动时数字进出边界会化开、不再是硬切。
 */
private const val OdometerEdgeFadeFraction = 0.12f

/**
 * 数字滚轮：主滚动缓动。
 * 起步带一点加速、末段大幅减速，收尾时还留着惯性，所以会冲过目标一点点。
 */
private val OdometerRollEasing = CubicBezierEasing(0.22f, 0.72f, 0.3f, 1f)

/** 数字滚轮：回弹落位缓动，从过冲位置平滑退回目标格。 */
private val OdometerSettleEasing = FastOutSlowInEasing

/** 数字与单位之间的横向间距。 */
private val OdometerUnitSpacing = 4.dp

/**
 * 判断文本能否用滚轮渲染：至少含一个数字，且只由数字与 `.` `-` `,` 组成。
 *
 * "--"（不可用）、" "（编辑态占位）、含文字单位的历史缓存值都会落到普通文本路径。
 */
private fun String.isRollableValue(): Boolean =
    any { it.isDigit() } && all { it.isDigit() || it == '.' || it == '-' || it == ',' }

/**
 * 轮盘排版量算结果。
 *
 * @param fontSize 自适应后实际使用的字号
 * @param cellHeight 单个字符格的高度（等于该字号下的自然行高）
 * @param baseline 基线相对格子顶部的像素偏移（仅用于日志核对）
 */
private data class OdometerMetrics(
    val fontSize: TextUnit,
    val cellHeight: Dp,
    val baseline: Int,
    /**
     * 0-9 各自在最终字号下的自然宽度。
     *
     * 比例字体里各位并不等宽（「1」明显比「0」窄），逐位取用才能让整串数字
     * 看起来和普通文本一样自然；若统一按最宽的数字固定列宽，窄数字左右就会空一块。
     */
    val digitWidths: List<Dp>,
    /** 各非数字字符（小数点、负号等）的排版与定宽规格，按字符缓存 */
    val staticGlyphs: Map<Char, StaticGlyphSpec>,
    /** 单位需要下移多少才能让它的基线落到数字基线上；null 表示没有单位 */
    val unitBaselineOffset: Dp?,
)

/**
 * 一个「不滚动字符格」的排版与尺寸。
 *
 * @param layout 带上下文的排版结果
 * @param charIndex 目标字符在 [layout] 中的下标
 * @param width 这一格的宽度（见 [measureStaticGlyph] 的说明）
 */
private data class StaticGlyphSpec(
    val layout: TextLayoutResult,
    val charIndex: Int,
    val width: Dp,
)

/**
 * 量算一个不滚动的字符格。
 *
 * **上下文为什么是「0<char>0」而不是真实邻居**：字形选择依赖上下文（CJK 字体普遍为
 * 「两侧都是数字的句点」提供方点 → 居中圆点的替换），所以标点必须带着数字排版；
 * 注：DataCard 注入的 MiSans Condensed 这条替换是坏的（把小数点换成了冒号字形），
 * 已在那边用 `fontFeatureSettings` 关掉 `calt`（见 ui/theme/DataCardFont.kt），
 * 但「标点要带数字排版」这件事本身仍要保留 —— 换别的字体时它才是正确行为。
 * 但若直接用真实邻居，邻居的字形墨迹会画进这一格里 —— 比如某些字体的「9」会向左
 * 缩进（负的 left side bearing），墨迹就越过格子左边界，糊在小数点上。
 * 上下文字符只是用来让字体认出「这是数字之间」，取固定的 0 即可，
 * 既稳定又不会带来会越界的邻居。
 *
 * **宽度为什么取 max**：`getHorizontalPosition` 给的是**光标步进**，不含字形墨迹的
 * 左右溢出。若字体对「点 + 9」这一对做了负字距（kerning），步进会被压小，
 * 只按步进定宽就会把点切掉一块。所以再和「该字符单独排版时的宽度」取较大者兜底。
 */
private fun measureStaticGlyph(
    measurer: TextMeasurer,
    char: Char,
    style: TextStyle,
    density: Density,
): StaticGlyphSpec {
    val layout = measurer.measure(
        text = AnnotatedString("0${char}0"),
        style = style,
        maxLines = 1,
        softWrap = false,
    )
    val charIndex = 1
    val stepPx = layout.getHorizontalPosition(charIndex + 1, usePrimaryDirection = true) -
        layout.getHorizontalPosition(charIndex, usePrimaryDirection = true)
    val standalonePx = measurer.measure(AnnotatedString(char.toString()), style).size.width.toFloat()
    val widthPx = maxOf(stepPx, standalonePx)
    return StaticGlyphSpec(
        layout = layout,
        charIndex = charIndex,
        width = with(density) { widthPx.toDp() },
    )
}

/**
 * 量算滚轮需要的排版参数。
 *
 * 字号自适应沿用原 TextAutoSize 的效果：先按最大字号量出整串的自然宽度，
 * 再按可用宽度等比缩小，并夹在 [minFontSize, maxFontSize] 之间。
 * 数字列按「这一位目标数字」的自然宽度定宽，避免比例字体下 1 和 8 占位不同、各位错位。
 *
 * @param availableWidthPx 可用宽度（像素），0 或 [Constraints.Infinity] 表示不受限
 */
private fun measureOdometer(
    measurer: TextMeasurer,
    value: String,
    unit: String?,
    unitStyle: TextStyle,
    style: TextStyle,
    availableWidthPx: Int,
    minFontSize: TextUnit?,
    maxFontSize: TextUnit,
    density: Density,
): OdometerMetrics {
    fun styleAt(fontSize: TextUnit) = style.copy(fontSize = fontSize, lineHeight = TextUnit.Unspecified)

    // 非数字字符（小数点、负号、千分位等）的排版与定宽；同一字符在串里可能出现多次，按字符缓存
    fun staticSpecs(fontSize: TextUnit): Map<Char, StaticGlyphSpec> {
        val s = styleAt(fontSize)
        return value.toSet()
            .filterNot { it.isDigit() }
            .associateWith { char -> measureStaticGlyph(measurer, char, s, density) }
    }

    // 0-9 各自的自然宽度：比例字体里各位并不等宽，逐位取用才自然
    fun digitWidthsPx(fontSize: TextUnit): FloatArray {
        val s = styleAt(fontSize)
        return FloatArray(10) { digit ->
            measurer.measure(AnnotatedString(digit.toString()), s).size.width.toFloat()
        }
    }

    // 0) 先把单位量出来：数字的可用宽度要先把单位和间距让出去
    val unitLayout = unit?.takeIf { it.isNotBlank() }
        ?.let { measurer.measure(AnnotatedString(it), unitStyle) }
    val unitWidthPx = unitLayout?.let { it.size.width.toFloat() } ?: 0f
    val unitSpacingPx = if (unitLayout != null) with(density) { OdometerUnitSpacing.toPx() } else 0f
    val digitBudgetPx = availableWidthPx - unitWidthPx - unitSpacingPx

    // 1) 按最大字号估自然宽度：数字取各自宽度，非数字取各字符格的宽度（与最终布局口径一致）
    val maxDigitWidths = digitWidthsPx(maxFontSize)
    val maxStaticSpecs = staticSpecs(maxFontSize)
    var naturalWidth = 0f
    value.forEach { char ->
        naturalWidth += if (char.isDigit()) {
            maxDigitWidths[char - '0']
        } else {
            with(density) { maxStaticSpecs.getValue(char).width.toPx() }
        }
    }

    // 2) 等比缩放字号以放进可用宽度；minFontSize 为 null 表示调用方显式关闭自适应
    val scale = if (minFontSize == null || digitBudgetPx <= 0 || naturalWidth <= 0f) {
        1f
    } else {
        // 留 2% 余量：Row 会按「已用宽度」逐个子项扣减可用宽度，
        // 只要总宽擦着上限，靠后的格子就会被压成 0 宽，整串数字会一起消失。
        (digitBudgetPx / naturalWidth * 0.98f).coerceAtMost(1f)
    }
    val maxFontSizePx = with(density) { maxFontSize.toPx() }
    val minFontSizePx = with(density) { (minFontSize ?: maxFontSize).toPx() }
    // 用 minOf/maxOf 兜住区间：万一调用方传了反着的字号，coerceIn 会直接抛异常
    val fontSizeFloor = minOf(minFontSizePx, maxFontSizePx)
    val fontSizeCeil = maxOf(minFontSizePx, maxFontSizePx)
    val fontSizePx = (maxFontSizePx * scale).coerceIn(fontSizeFloor, fontSizeCeil)
    val fontSize = with(density) { fontSizePx.toSp() }

    // 3) 用最终字号量出格子高度、基线与 0-9 的列宽；量算异常时给非零兜底，
    //    否则一旦拿到 0，整串数字会连同布局一起“凭空消失”。
    val digitLayout = measurer.measure(AnnotatedString("0"), styleAt(fontSize))
    val measuredCellHeight = with(density) { digitLayout.size.height.toDp() }
    val fallbackCellHeight = with(density) { maxFontSizePx.toDp() }
    val cellHeight = measuredCellHeight.takeIf { it.value > 0f } ?: fallbackCellHeight
    val digitWidths = digitWidthsPx(fontSize).map { widthPx ->
        with(density) { widthPx.toDp() }.takeIf { it.value > 0f } ?: (cellHeight * 0.6f)
    }
    val baseline = digitLayout.firstBaseline.toInt().takeIf { it > 0 }
        ?: with(density) { cellHeight.toPx() * 0.8f }.toInt()

    // 4) 单位下移量 = 数字基线 - 单位自身基线（两者都以各自行框顶部为原点）
    val unitBaselineOffset = unitLayout?.let {
        with(density) { (digitLayout.firstBaseline - it.firstBaseline).toDp() }
    }

    // 5) 最终字号下各非数字字符格的排版与宽度（字号变了要重量，否则字形与格子会不匹配）
    val staticGlyphs = staticSpecs(fontSize)

    return OdometerMetrics(
        fontSize = fontSize,
        cellHeight = cellHeight,
        baseline = baseline,
        digitWidths = digitWidths,
        staticGlyphs = staticGlyphs,
        unitBaselineOffset = unitBaselineOffset,
    )
}

/**
 * 计算下一个停靠格：不小于「当前格 + 10」且与目标数字同余的最小格数。
 *
 * 这样每轮至少滚满一整圈，滚轮才有「转起来」的机械感；
 * 停在同余位置意味着露出的数字仍是目标数字，回卷时不会闪。
 */
private fun nextOdometerStop(from: Float, digit: Int): Float {
    val k = ceil((from + 10f - digit) / 10f)
    return k * 10f + digit
}

/**
 * 数值文本（本文件的对外入口）：数值与单位一起排版，并按需播放数字滚轮动画。
 *
 * 两条路径：
 *   - 可解析的纯数字 → [OdometerValueText]，渲染成「一位一个轮盘」，每位独立滚到目标数字；
 *   - 其它文本（"--"、空格占位、含前导零等）→ 普通整串 [Text]，数值与单位按基线对齐。
 *
 * 是否走滚轮由两个开关共同决定：调用方的 [enabled]（这张卡要不要动画）
 * 与全局的 [LocalRollingNumberEnabled]（用户在设置页是否启用了滚动动画）。
 *
 * 单独抽一层 composable，是为了把「逐帧变化的 state 读取」限制在这个小节点里——
 * 若直接在 DataCard 体内读取动画值，动画期间整张卡片（背景、标题、单位、布局）都会每帧重组。
 *
 * @param value 原始展示文本（已格式化）
 * @param unit 数值的后导单位，null 表示不显示
 * @param enabled 这张卡是否参与动画（false 直接按普通文本渲染）
 * @param autoPlay 首次组合是否自动播放滚动；false 表示直接落位（如设置页预览卡，等用户点重播）
 * @param minFontSize 数值自适应最小字号，null 表示禁用自适应
 * @param maxFontSize 数值自适应最大字号
 */
@Composable
internal fun AnimatedValueText(
    value: String,
    unit: String?,
    enabled: Boolean,
    autoPlay: Boolean = true,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    unitStyle: TextStyle = MiuixTheme.textStyles.footnote1,
    unitColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    minFontSize: TextUnit?,
    maxFontSize: TextUnit,
) {
    if (enabled && LocalRollingNumberEnabled.current && value.isRollableValue()) {
        OdometerValueText(
            value = value,
            unit = unit,
            autoPlay = autoPlay,
            modifier = modifier,
            style = style,
            unitStyle = unitStyle,
            unitColor = unitColor,
            minFontSize = minFontSize,
            maxFontSize = maxFontSize,
        )
    } else {
        // 三种情况都走这里：文本不可滚动（"--"、" " 等）、调用方显式关闭动画、
        // 或用户在设置页关掉了「数字滚动动画」。
        // 这就是滚轮之前的原始显示逻辑：整串文本 + 自适应字号，数值与单位按基线对齐。
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                value,
                // fill=false：数值短时仍靠左，不被拉满整行
                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                style = style,
                maxLines = 1,
                autoSize = if (minFontSize != null) {
                    TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = maxFontSize)
                } else {
                    null
                },
            )
            if (!unit.isNullOrBlank()) {
                Text(
                    unit,
                    modifier = Modifier.alignByBaseline(),
                    style = unitStyle,
                    color = unitColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 滚轮式数值文本：每一位数字各自垂直滚动到目标数字，`.` `-` `,` 等字符保持静止。
 *
 * 布局上是一行「字符格」，全部走画布绘制（窗口高度与行距都来自同一份 TextMeasurer 量算结果）：
 *   - 数字格内画轮盘 [DigitReel]，只露出当前停靠的那一行；
 *   - 非数字格画该字符 [StaticGlyph]，格子高度与数字一致，因此基线天然对齐；
 *   - 单位跟在最后，按量算出的偏移下移，让它的基线与数字基线重合。
 *
 * 首次组合时所有轮盘从数字 0 出发向上滚到目标位（首位先停、往右依次后停）；
 * 之后数值变化时再从当前位置滚到新位。被 LazyColumn 回收后重新组合时不再重播。
 *
 * @param autoPlay 首次组合是否自动播放；false 时直接落位（设置页预览卡靠它实现「默认不播」）
 */
@Composable
private fun OdometerValueText(
    value: String,
    unit: String?,
    autoPlay: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle,
    unitStyle: TextStyle,
    unitColor: Color,
    minFontSize: TextUnit?,
    maxFontSize: TextUnit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // 全局「模糊效果」总开关（设置页 → 外观 → 模糊效果）。
    // 关闭后数字滚动不再叠加速度模糊，而滚动的时长/曲线/回弹/边界淡出都不受影响。
    // 低配版（SUPPORT_BLUR=false）在数据源层已被强制为 false，这里于是自动降级。
    val smearEnabled = LocalThemeSettings.current.enableBlur
    // 「是否已经播过一次」：
    //   - autoPlay=true（普通卡片）→ 初始 false，首次组合从 0 开始滚；
    //   - autoPlay=false（设置页预览卡）→ 初始 true，直接落位到目标数字，等用户点「重播」；
    //   - 滚出视口被 LazyColumn 回收后重新组合 → 读回保存的 true，不重播。
    var seenBefore by rememberSaveable { mutableStateOf(!autoPlay) }
    val playOnFirstFrame = remember { !seenBefore }
    LaunchedEffect(Unit) {
        if (!seenBefore) {
            seenBefore = true
            AppLog.d(AppLog.TAG_ODOMETER, "数字滚轮动画开始：$value")
        }
    }

    // 这里刻意不用 weight：BoxWithConstraints 是 SubcomposeLayout，被 Row 当加权子项测量时
    // 时序很微妙。直接占满整行、自己内部排布，是最稳的写法。
    BoxWithConstraints(modifier = modifier) {
        // 可用宽度；不受限（Infinity）时视为不做自适应缩放
        val availableWidth = constraints.maxWidth
        val metrics = remember(measurer, style, unit, unitStyle, value, availableWidth, minFontSize, maxFontSize) {
            measureOdometer(
                measurer = measurer,
                value = value,
                unit = unit,
                unitStyle = unitStyle,
                style = style,
                availableWidthPx = if (availableWidth == Constraints.Infinity) 0 else availableWidth,
                minFontSize = minFontSize,
                maxFontSize = maxFontSize,
                density = density,
            )
        }
        // 把量算结果打一条日志：数字还是不显示时，这条能直接看出字号/格子尺寸是否算歪了
        LaunchedEffect(metrics) {
            AppLog.d(
                AppLog.TAG_ODOMETER,
                "滚轮量算：可用宽=${availableWidth}px 字号=${metrics.fontSize} " +
                    "格高=${metrics.cellHeight} 数字宽=${
                        metrics.digitWidths.joinToString("/") { it.value.toInt().toString() }
                    } 基线=${metrics.baseline}",
            )
        }
        val reelStyle = style.copy(fontSize = metrics.fontSize, lineHeight = TextUnit.Unspecified)
        // 颜色得自己解析：TextMeasurer 只负责排版，画布绘制时要显式给出颜色。
        // 口径对齐 Miuix 的 Text：style 里的颜色 → LocalContentColor 兜底。
        val reelColor = reelStyle.color.takeOrElse { LocalContentColor.current }
        val lastIndex = value.lastIndex
        // 数字位序号：首位（最左边第一位数字）为 0，往右递增。
        // 错峰按它算，才是「从左到右依次落位」；若直接用 rightIndex 反推，
        // 小数点/负号也会白占一拍，小数部分会莫名多停一下。
        var digitOrdinal = 0

        Row {
            value.forEachIndexed { index, char ->
                // 从右往左的位数：0 为个位，作为列的身份，值变长时同一位次不会串位
                val rightIndex = lastIndex - index
                if (char.isDigit()) {
                    val staggerSteps = digitOrdinal++
                    // 列宽取「这一位目标数字」的自然宽度：比例字体下各位宽度不同，
                    // 统一按最宽数字定宽会让窄数字左右空出一块。
                    val digit = char - '0'
                    val columnWidth = metrics.digitWidths[digit]
                    key(rightIndex) {
                        DigitReel(
                            valueText = value,
                            digit = digit,
                            place = rightIndex,
                            staggerSteps = staggerSteps,
                            reelStyle = reelStyle,
                            reelColor = reelColor,
                            cellHeight = metrics.cellHeight,
                            columnWidth = columnWidth,
                            columnWidthPx = with(density) { columnWidth.toPx() },
                            measurer = measurer,
                            playOnFirstFrame = playOnFirstFrame,
                            smearEnabled = smearEnabled,
                        )
                    }
                } else {
                    // 这是小数点/负号/千分位等分隔符：不滚动，只占一个字符格。
                    // 排版与定宽的讲究见 measureStaticGlyph —— 关键是既要拿到字体的上下文替换
                    //（否则小数点恒为方点），又不能把邻居的溢出墨迹画进来（否则点会被糊掉一块）
                    StaticGlyph(
                        spec = metrics.staticGlyphs.getValue(char),
                        cellHeight = metrics.cellHeight,
                        glyphColor = reelColor,
                    )
                }
            }
            // 单位：按量算出来的偏移下移，让它自己的基线和数字基线重合
            if (!unit.isNullOrBlank()) {
                Text(
                    text = unit,
                    style = unitStyle,
                    color = unitColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .then(
                            metrics.unitBaselineOffset
                                ?.let { offsetY -> Modifier.offset(y = offsetY) }
                                ?: Modifier
                        ),
                )
            }
        }
    }
}

/**
 * 单个数字滚轮。
 *
 * 位置上不放进 rememberSaveable：轮盘位置在被回收重排后重算的代价很低，
 * 「是否重播」这件事由上层 [OdometerValueText] 用一次性标记统一控制。
 *
 * @param valueText 整串数值，作为动画触发键：任何一次数值变化都会让所有轮盘重新转起来
 * @param digit 这一位要停在的目标数字
 * @param place 从右往左的位数（0 为个位），仅用于日志标识
 * @param staggerSteps 错峰步数：0 表示这一位最先滚（首位），越往右步数越大、越晚滚
 * @param playOnFirstFrame 首次组合是否播放滚动
 * @param smearEnabled 是否叠加速度模糊（跟随设置页的「模糊效果」总开关）
 */
@Composable
private fun DigitReel(
    valueText: String,
    digit: Int,
    place: Int,
    staggerSteps: Int,
    reelStyle: TextStyle,
    reelColor: Color,
    cellHeight: Dp,
    columnWidth: Dp,
    columnWidthPx: Float,
    measurer: TextMeasurer,
    playOnFirstFrame: Boolean,
    smearEnabled: Boolean,
) {
    // 当前停在轮盘的第几格。首次组合从第 0 格（数字 0）出发，恢复组合直接停在目标格
    var stop by remember { mutableStateOf(if (playOnFirstFrame) 0f else digit.toFloat()) }
    // 恢复组合时不需要补播一次动画，这个标记只用来跳过第一轮
    var skipFirstRun by remember { mutableStateOf(!playOnFirstFrame) }
    val progress = remember { Animatable(0f) }
    // 运动模糊半径（dp）。用 state 存、写在动画回调里：和 stop 同一个机制，
    // 写进去就一定会驱动界面更新（这正是之前用 graphicsLayer 读 velocity 失败的地方）。
    var blurRadius by remember { mutableStateOf(0.dp) }
    // 0-9 各量一次排版结果并记住，避免逐帧重组时反复排一整轮文字
    val digitLayouts = remember(measurer, reelStyle) {
        (0..9).map { digitValue ->
            measurer.measure(AnnotatedString(digitValue.toString()), reelStyle)
        }
    }
    val lineHeightPx = digitLayouts[0].size.height.toFloat()

    // 把「这一帧滚过的距离」换算成模糊半径。用逐帧位移而不是 Animatable.velocity：
    // velocity 在组合期/图层期读都不可靠，而逐帧位移是拿现成的、还天然按实际速度变化。
    // 设置页关掉模糊时恒为 0——半径 0 会让 Compose 直接跳过整个特效（renderEffect = null），
    // 于是不仅是「看不见模糊」，连离屏模糊的开销也一并没有了。
    fun blurRadiusFor(previousStop: Float, nextStop: Float): Dp =
        if (!smearEnabled) {
            0.dp
        } else {
            (abs(nextStop - previousStop) * cellHeight.value * OdometerSmearExposure)
                .coerceAtMost(OdometerSmearMaxRadiusDp)
                .dp
        }

    LaunchedEffect(valueText) {
        if (skipFirstRun) {
            skipFirstRun = false
            return@LaunchedEffect
        }
        val from = stop
        val target = nextOdometerStop(from, digit)
        // 错峰：首位（最左边第一位）先动、往右依次跟上，收尾时也是首位先落位。
        // 用 delay 而不是 tween 的 delayMillis，因为下面要跑两段动画，错峰只该作用在开头一次。
        if (staggerSteps > 0) delay((staggerSteps * OdometerRollStaggerMillis).milliseconds)
        AppLog.d(AppLog.TAG_ODOMETER, "数字滚轮：第 $place 位从第 $from 格滚到第 $target 格")
        val overshoot = OdometerRollOvershootLines

        // 第一段：带着惯性滚过目标一点点
        var previousStop = from
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(OdometerRollDurationMillis, easing = OdometerRollEasing),
        ) {
            val nextStop = from + (target + overshoot - from) * value
            blurRadius = blurRadiusFor(previousStop, nextStop)
            previousStop = nextStop
            stop = nextStop
        }
        // 第二段：从过冲位置平滑退回目标格，就是那一下回弹。
        // 这一段每帧位移极小，半径自然接近 0，不需要额外判断。
        var previousSettleStop = stop
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(OdometerSettleDurationMillis, easing = OdometerSettleEasing),
        ) {
            val nextStop = target + overshoot * (1f - value)
            blurRadius = blurRadiusFor(previousSettleStop, nextStop)
            previousSettleStop = nextStop
            stop = nextStop
        }
        // 收尾回卷到第 digit 格：与目标格同余，露出的数字不变，但轮盘回到起始位置
        blurRadius = 0.dp
        stop = digit.toFloat()
    }

    Box(
        modifier = Modifier
            // requiredWidth/requiredHeight：不受父级剩余空间影响，避免被压成 0 宽 0 高
            .requiredWidth(columnWidth)
            .requiredHeight(cellHeight)
            // 这是数字格的可视窗口：只露出轮盘上的一行。
            //
            // compositingStrategy = Offscreen 是必须的：下面用 DstIn 做渐变蒙版，
            // 只有本节点先渲染进独立的离屏图层，蒙版才只作用于自己画的内容。
            // 否则它会连卡片背景一起抹掉、抠出一块透明区（看起来就像多了个背景色遮罩）。
            // 滚动时有模糊特效、Compose 本来就会建离屏层，所以那时看不出问题；
            // 一停下来特效被移除、图层不再隔离，问题就露出来了。
            .graphicsLayer {
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
            }
            // 运动模糊：主要糊纵向（数字是上下滚的）。
            // 横向不能给 0：Compose 要求 x/y 半径都 > 0，否则直接把 renderEffect 置为 null、
            // 完全不出模糊。这里取纵向的 1/4，观感上仍以纵向为主，又满足条件。
            .blur(
                radiusX = blurRadius * OdometerSmearHorizontalRatio,
                radiusY = blurRadius,
            )
            .drawWithContent {
                // 这里没有子内容，drawContent() 是空操作，只为拿到一块可继续绘制的画布
                drawContent()
                // 位移在 draw 阶段读取，滚动期间只重绘、不重组
                val firstLine = floor(stop).toInt()
                val offsetInLine = stop - firstLine
                // 窗口只可能露出两行
                for (row in firstLine until firstLine + 2) {
                    val layout = digitLayouts[row.mod(10)]
                    drawText(
                        textLayoutResult = layout,
                        color = reelColor,
                        topLeft = Offset(
                            // 横向居中：滚动途中经过的数字宽度可能不等于列宽（列宽按最终那一位定），
                            // 居中能把溢出量左右平分，不至于只缺一边
                            x = (columnWidthPx - layout.size.width) / 2f,
                            y = (row - firstLine - offsetInLine) * lineHeightPx,
                        ),
                    )
                }
                // 上下边界淡出。用纵向渐变做 alpha 蒙版（DstIn 只取渐变的 alpha）：
                // 这样是把数字自己的 alpha 化掉，而不是拿背景色去盖——
                // 卡片底色是可配置的（弹层卡是 secondaryContainer、编辑态虚影甚至是透明的），
                // 用颜色盖在那些情况下会露出可见色带。
                // 蒙版落在本节点自己的图层里，不会影响到卡片背景——这个隔离必须由上面
                // graphicsLayer 的 CompositingStrategy.Offscreen 保证，不能指望 blur 恰好建了图层：
                // 关掉模糊（或半径恰好为 0）时它根本不会建，那时蒙版就会把卡片背景一起抠掉。
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        OdometerEdgeFadeFraction to Color.Black,
                        1f - OdometerEdgeFadeFraction to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/**
 * 不滚动的字符格：小数点、负号、千分位等。
 *
 * 它不单独排版自己那一个字符，而是把 **带上下文**的排版结果平移后裁出自己这一段 ——
 * 字形选择依赖上下文：CJK 字体普遍为「两侧都是数字的句点」提供了替换字形（方点 → 居中圆点），
 * 标点一旦脱离数字单独排版，替换规则不生效，小数点就恒为方点。
 *
 * 上下文由 [measureStaticGlyph] 构造（`0<char>0`），不是真实的邻居 —— 这是为了避免
 * 邻居的字形墨迹画进这一格：格子边界是一道**硬裁剪**（[clipToBounds]），与背景是否透明无关，
 * 凡是落在格子外的墨迹都会被切掉，落在格子内则会被画出来（比如负 left side bearing 的「9」
 * 就会向左边溢出、糊在小数点上）。
 *
 * 纵向不需要额外偏移：上下文与数字格用的是同一 style、同一字号排版出的同一行，行高与基线天然一致。
 */
@Composable
private fun StaticGlyph(
    spec: StaticGlyphSpec,
    cellHeight: Dp,
    glyphColor: Color,
) {
    Box(
        modifier = Modifier
            .requiredWidth(spec.width)
            .requiredHeight(cellHeight)
            .clipToBounds()
            .drawBehind {
                // 把目标字符的左边缘平移到格子左边缘，左右两侧（上下文用的那两个 0）由 clip 裁掉
                val startX =
                    spec.layout.getHorizontalPosition(spec.charIndex, usePrimaryDirection = true)
                translate(left = -startX) {
                    drawText(textLayoutResult = spec.layout, color = glyphColor, topLeft = Offset.Zero)
                }
            },
    )
}
