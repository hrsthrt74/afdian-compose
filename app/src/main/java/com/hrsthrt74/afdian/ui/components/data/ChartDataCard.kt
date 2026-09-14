package com.hrsthrt74.afdian.ui.components.data

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.stats.ChartCatalog
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import com.hrsthrt74.afdian.data.stats.ChartType
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LayeredComponent
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.VicoTheme
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** 底轴标签列表存在图表 model 的 extraStore，供 valueFormatter 按下标取字符串。 */
private val ChartLabelKey = ExtraStore.Key<List<String>>()

/**
 * 底轴最多显示的标签数量（可自行调整）。
 * 实际会随数据点数量缩放：点少时全显示，点多时按此上限均匀取点并强制包含首尾。
 */
private const val BottomAxisLabelCount = 6

/**
 * 把图表横轴标签文本放进 model 供底轴 formatter 读取。
 */
private fun CartesianChartModelProducer.Transaction.storeLabels(
    categories: List<String>,
) {
    extras { it[ChartLabelKey] = categories }
}

/** Kotlin 泛型不变性：把 Double 列表转成 Vico series 需要的 Collection<Number>。 */
private fun List<Double>.asChartNumbers(): List<Number> = map { it }

/** 把数值转成卡片风格的简洁文本（去掉无意义的小数零）。 */
private fun plainNumber(value: Double): String {
    val rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
    return rounded.stripTrailingZeros().toPlainString()
}

/** 金额文本：固定两位小数并带人民币符号。 */
private fun moneyText(value: Double): String =
    "¥" + BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString()

/** 纵轴刻度数字：上千/上万时缩写，其余保留自然整数。 */
private fun compactAxisValue(value: Double): String {
    val absolute = abs(value)
    return when {
        absolute >= 10000 -> plainNumber(value / 10000) + "万"
        absolute >= 1000 -> plainNumber(value / 1000) + "k"
        else -> plainNumber(value)
    }
}

/**
 * 防御式生成底轴标签 _x_ 值。
 *
 * 关键点：库内 aligned/segmented 的标签生成是一个无上界 while(true)（直到 pv > maxX），
 * 一旦 ranges 出现 xStep==0/NaN 或 maxX==+Inf/NaN，就会永远循环并无限分配内存直至 ANR。
 * 这里改用**有界**逻辑：范围异常时退化为仅两端标签（并记日志），正常时按
 * [BottomAxisLabelCount] 上限均匀取点并强制包含首尾，绝不产生无界循环。
 */
private fun buildAxisLabelXs(minX: Double, maxX: Double, step: Double): List<Double> {
    val sane = minX.isFinite() && maxX.isFinite() && step.isFinite() && step > 0.0 && maxX >= minX
    if (!sane) {
        AppLog.w("ChartDataCard", "底轴范围异常，仅保留两端标签：minX=$minX maxX=$maxX step=$step")
        return buildList {
            if (minX.isFinite()) add(minX)
            if (maxX.isFinite() && maxX != minX) add(maxX)
        }
    }
    val intervals = floor((maxX - minX) / step).toLong()
    if (intervals <= 0) return listOf(minX)
    val maxPossible = (intervals + 1).coerceAtMost(Int.MAX_VALUE.toLong())
    val targetCount = BottomAxisLabelCount.toLong().coerceIn(2, maxPossible)
    val spacing = ceil(intervals.toDouble() / (targetCount - 1)).toLong().coerceAtLeast(1)
    val out = mutableListOf<Double>()
    var k = 0L
    // 有界循环：k 递增，且最多生成 100 个，物理上不可能无限。
    while (k * spacing <= intervals && out.size <= 100) {
        val x = minX + k * spacing * step
        if (x >= minX && x <= maxX) out += x
        k++
    }
    if (out.isEmpty()) out += minX
    if (out.last() < maxX) out += maxX
    return out.distinct()
}

/**
 * 安全的底轴 ItemPlacer：复刻 extremes 的结构与边距，但标签由 [buildAxisLabelXs]
 * 有界生成（多个），从而在保留多个日期刻度的同时，彻底避免库内的无界 while。
 */
private val SafeBottomAxisItemPlacer = object : HorizontalAxis.ItemPlacer {
    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = buildAxisLabelXs(context.ranges.minX, context.ranges.maxX, context.ranges.xStep)

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> = buildAxisLabelXs(context.ranges.minX, context.ranges.maxX, context.ranges.xStep)

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = buildAxisLabelXs(context.ranges.minX, context.ranges.maxX, context.ranges.xStep)

    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = tickThickness

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = tickThickness
    // 注意：不要开启 getShiftExtremeLabels=true。那会让最左/最右标签 End/Start 对齐到各自刻度，
    // 导致最右侧日期文本相对轴/柱向右偏出，看起来“对不齐”。
    // 保持默认 false 让所有标签都居中对齐于刻度；边缘留白由 CartesianLayerPadding 保证不裁剪。
}

/**
 * 整行宽图表卡：外层与数据卡一致的 Miuix Card，
 * 内部用 Vico 画柱状图或折线，横轴标签 + 点按 marker。
 *
 * @param key 布局条目的图表键（[ChartType.storageValue]）。
 * @param data 对应的图表数据；为 null 或没有序列时显示“暂无数据”。
 */
@Composable
fun ChartDataCard(
    key: String,
    data: ChartDataItem?,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.defaultColors(),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val chartType = ChartCatalog.find(key)
    val title = data?.title ?: ChartCatalog.titleFor(key)
    val isEmpty = data?.series.orEmpty().all { it.values.isEmpty() }

    // 有点击或长按需求时才套上手势，否则保持纯展示（图表自带 marker 手势不受影响）。
    val clickableModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
    } else {
        Modifier
    }

    Card(
        // 这是按压下沉
        modifier = modifier.fillMaxWidth().pressable(interactionSource = null, indication = SinkFeedback()),
        colors = colors,
    ) {
        Box(
            // 这是点击特效
            modifier = clickableModifier
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                )
                if (chartType == null || isEmpty) {
                    LoadingComponent(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp))
                } else {
                    ChartCanvas(
                        chartType = chartType,
                        data = data!!,
                        markerEnabled = onClick == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    var downOffset: Offset? = null
                                    var isHorizontalScrolling = false
                                    val scrollThreshold = 8.dp.toPx()
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressedChange = event.changes.firstOrNull { it.pressed }
                                        if (pressedChange != null) {
                                            // 记录首次按下位置
                                            if (downOffset == null) {
                                                downOffset = pressedChange.position
                                                isHorizontalScrolling = false
                                            } else if (!isHorizontalScrolling) {
                                                // 检测是否发生水平滑动：水平位移超过阈值且大于垂直位移
                                                val dx = abs(pressedChange.position.x - downOffset.x)
                                                val dy = abs(pressedChange.position.y - downOffset.y)
                                                if (dx > scrollThreshold && dx > dy) {
                                                    isHorizontalScrolling = true
                                                }
                                            }
                                            // 仅水平滑动时消费事件（防止背景页面跟着动）
                                            if (isHorizontalScrolling) {
                                                event.changes.fastForEach { it.consume() }
                                            }
                                        } else {
                                            // 手指抬起，重置状态
                                            downOffset = null
                                            isHorizontalScrolling = false
                                        }
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}

/** 真正绘制 Vico 图表的内部区域，按图表类型分发。 */
@Composable
private fun ChartCanvas(
    chartType: ChartType,
    data: ChartDataItem,
    modifier: Modifier = Modifier,
    markerEnabled: Boolean = true,
) {
    val colorScheme = MiuixTheme.colorScheme
    val primaryColor = colorScheme.primary
    // 折线图与柱状图都是单序列，统一使用主题主色。
    val seriesColors = listOf(primaryColor)
    val axisTextColor = colorScheme.onSurfaceVariantSummary
    val axisLineColor = colorScheme.outline

    // 用 Miuix 配色喂给 Vico 主题，轴组件默认颜色跟随主题。
    val theme = remember(colorScheme) {
        VicoTheme(
            columnCartesianLayerColors = seriesColors,
            lineCartesianLayerColors = seriesColors,
            pieChartColors = seriesColors,
            candlestickCartesianLayerColors = VicoTheme.CandlestickCartesianLayerColors(
                bullish = Color(0xff0ac285),
                neutral = Color(0xff9aa0a6),
                bearish = Color(0xffff5252),
            ),
            lineColor = axisLineColor,
            textColor = axisTextColor,
        )
    }
    val modelProducer = remember { CartesianChartModelProducer() }

    // 数据变更时整表重建；无数据分支在上层已挡掉。
    LaunchedEffect(data) {
        modelProducer.runTransaction {
            when (chartType) {
                ChartType.IncomeDaily30, ChartType.IncomeMonthly -> {
                    val values = data.series.firstOrNull()?.values.orEmpty().asChartNumbers()
                    columnModel { series(values) }
                    storeLabels(data.categories)
                }
                ChartType.UvDaily30 -> {
                    val values = data.series.firstOrNull()?.values.orEmpty().asChartNumbers()
                    lineModel { series(values) }
                    storeLabels(data.categories)
                }
            }
        }
    }

    // 柱状图（收入）按金额展示带 ¥，折线（访问人数）为整数访问量。
    val isMoney = chartType != ChartType.UvDaily30
    ProvideVicoTheme(theme) {
        BoxWithConstraints(modifier = modifier.height(170.dp)) {
            // 图表在测量到“有限且大于 0”的宽度前不挂载 Canvas：
            // 否则首帧宽度为 0 时库内 xSpacing=0，可见范围会算出 NaN，底轴 placer 的 while(true)
            // 因任何比较都返回 false 而永不退出，无限分配内存直到 ANR（底部弹窗打开时极易触发）。
            if (maxWidth.value.isFinite() && maxWidth > 0.dp) {
                // 点按 marker：竖线 + 「日期 · 数据」。在主题包裹内构建，让格线跟随主题色。
                val chartMarker = if (markerEnabled) ChartMarker(data.categories, isMoney) else null
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        if (chartType == ChartType.UvDaily30) {
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    seriesColors.mapIndexed { index, color ->
                                        LineCartesianLayer.rememberLine(
                                            fill = LineCartesianLayer.LineFill.single(Fill(color)),
                                            areaFill =
                                                LineCartesianLayer.AreaFill.single(
                                                    Fill(
                                                        Brush.verticalGradient(listOf(MiuixTheme.colorScheme.primary.copy(alpha = 0.4f), Color.Transparent))
                                                    )
                                                ),
                                            interpolator = LineCartesianLayer.Interpolator.catmullRom(),
                                        )
                                    },
                                ),
                            )
                        } else {
                            rememberColumnCartesianLayer(
                                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                    seriesColors.mapIndexed { index, color ->
                                        // 柱状图的柱子的参数
                                        rememberLineComponent(
                                            fill = Fill(color),
                                            thickness = 20.dp,
                                            shape = RoundedCornerShape(1.5.dp),
                                        )
                                    },
                                ),
                            )
                        },
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = CartesianValueFormatter { _, value, _ -> compactAxisValue(value) },
                            label = rememberAxisLabelComponent(
                                style = TextStyle(
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 10.sp,
                                ),
                            ),
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            // 自定义安全 placer：多个标签 + 永不进入库内无界 while（见 SafeBottomAxisItemPlacer）。
                            itemPlacer = SafeBottomAxisItemPlacer,
                            valueFormatter = remember(data.categories) {
                                CartesianValueFormatter { context, x, _ ->
                                    context.model.extraStore[ChartLabelKey].getOrElse(x.toInt()) { "" }
                                }
                            },
                            label = rememberAxisLabelComponent(
                                style = TextStyle(
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 10.sp,
                                ),
                            ),
                        ),
                        marker = chartMarker,
                        // 左右各留固定空白，让首尾标签能居中对齐且不被裁切。
                        layerPadding = {
                            CartesianLayerPadding(unscalableStart = 16.dp, unscalableEnd = 16.dp)
                        },
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier.fillMaxSize(),
                    scrollState = rememberVicoScrollState(scrollEnabled = false),
                    // 关闭动画：不播初次 reveal 动画、也不播数据差值动画。
                    // 否则 LazyColumn 把图表滚出可视区再滚回、或重新进入组合时，会重新播放
                    // Vico 默认初始化动画（表现为闪烁/卡顿）。
                    animationSpec = null,
                    initialAnimationSpec = null,
                )
            }
        }
    }
}

/**
 * 构建点按 marker：圆角标签框，内容为「日期 · 数据」的**单行**文本。
 * 柱状图（收入）显示带 ¥ 的金额；折线图（访问人数）显示整数访问量。
 */
@Composable
private fun ChartMarker(
    categories: List<String>,
    isMoney: Boolean,
): DefaultCartesianMarker {
    val colorScheme = MiuixTheme.colorScheme
    // 指示器气泡
    val labelBg = rememberShapeComponent(
        fill = Fill(colorScheme.surfaceContainer),
        shape = MarkerCornerBasedShape(RoundedCornerShape(20.dp)),
        strokeFill = Fill(colorScheme.outline),
        strokeThickness = 1.dp,
    )
    // 指示器文本
    val labelText = rememberTextComponent(
        style = TextStyle(color = colorScheme.onSurface, fontSize = 10.sp, letterSpacing = 0.3.sp),
        padding = Insets(8.dp, 4.dp),
        background = labelBg,
    )
    return rememberDefaultCartesianMarker(
        label = labelText,
        valueFormatter = remember(categories, isMoney) {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                val target = targets.firstOrNull()
                buildAnnotatedString {
                    val index = target?.x?.toInt() ?: -1
                    val category = categories.getOrNull(index)
                    if (!category.isNullOrBlank()) {
                        append(category)
                        append(" · ")
                    }
                    // 所有图表当前都是单序列：仅取命中的值即可，多值时用点分隔保持单行。
                    val values = when (target) {
                        is ColumnCartesianLayerMarkerTarget -> target.columns.map { it.color to it.entry.y }
                        is LineCartesianLayerMarkerTarget -> target.points.map { it.color to it.entry.y }
                        else -> emptyList()
                    }
                    values.forEachIndexed { valueIndex, (color, value) ->
                        if (valueIndex > 0) append(" · ")
                        withStyle(SpanStyle(color)) {
                            append(if (isMoney) moneyText(value) else plainNumber(value))
                        }
                    }
                }
            }
        },
        // 按住后显示的圆球
        indicator = { color ->
            LayeredComponent(
                back = ShapeComponent(Fill(colorScheme.primaryVariant.copy(alpha = 0.1f)), CircleShape),
                front = ShapeComponent(Fill(colorScheme.primaryVariant), CircleShape),
                padding = Insets(12.dp),
            )
        },
        // 圆球大小（外圈）
        indicatorSize = 36.dp,
        guideline = rememberAxisGuidelineComponent(),
    )
}
