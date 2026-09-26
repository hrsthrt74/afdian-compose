package com.hrsthrt74.afdian.ui.components.data

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieModel

/**
 * 环形图的一个扇区。
 *
 * [color] 由调用方按业务含义固定给出（而不是按扇区下标轮转取色），
 * 这样即便某类数据清零、排序变化，同一个分类的颜色也不会跳变。
 */
@Immutable
data class DonutSlice(val value: Float, val color: Color)

/**
 * 默认的最小扇区角度（度）。
 *
 * 为什么是 5 而不是更小的 2~3：扇区之间还有 [DonutChart] 的缝隙，
 * 库会把每段圆弧两端的 `outerTrim` / `innerTrim` 各削掉约 1 度（半径越大、缝隙越宽，削得越多）。
 * 一个 3 度的扇区再被啃掉两头，实际只剩一丝，等于没解决「看不见」的问题，
 * 所以下限要明显大于缝隙占用的角度才有效。
 */
private const val DefaultMinSweepDegrees = 5f

/**
 * 把一组权重换算成角度值，保证每个非零扇区至少占 [minSweepDegrees] 度，总和为 [totalDegrees]。
 *
 * 动机：真实数据里常有一两个极小的分类，按真实占比画出来只有零点几度，屏幕上几乎等于不存在，
 * 用户会误以为这个分类压根没有数据。
 *
 * 做法（一趟解出，不做循环逼近）：
 * 1. 按**真实占比**找出角度低于下限的扇区，把它们抬到下限，占用固定预算；
 * 2. 剩余预算按原始权重比例分给其余扇区。
 *
 * 数据本来就健康（没有任何扇区低于下限）时，这一步退化成等比缩放，不改变任何占比，
 * 因此可以无条件调用、不需要调用方先判断。
 *
 * 纯函数，便于单测。返回列表与入参一一对应，零值项保持为零。
 */
internal fun enforceMinimumSweep(
    values: List<Float>,
    minSweepDegrees: Float,
    totalDegrees: Float = 360f,
): List<Float> {
    if (values.isEmpty()) return values
    val positiveCount = values.count { it > 0f }
    if (positiveCount == 0) return values
    val total = values.sum()
    if (total <= 0f) return values

    // 扇区过多时下限不可行（全抬到下限会超过一圈），此时放宽到「均分」这个理论上限
    val sweep = minSweepDegrees.coerceIn(0f, totalDegrees / positiveCount)

    // 只抬真实角度就低于下限的扇区
    val promoted = values.indices.filter { index ->
        values[index] > 0f && values[index] / total * totalDegrees < sweep
    }
    if (promoted.isEmpty()) {
        // 无需求：仅等比换算成角度，占比完全不变
        return values.map { it / total * totalDegrees }
    }

    val restBudget = (totalDegrees - promoted.size * sweep).coerceAtLeast(0f)
    val restTotal = values.indices
        .filterNot { it in promoted }
        .sumOf { values[it].toDouble() }
        .toFloat()

    return values.mapIndexed { index, value ->
        when {
            index in promoted -> sweep
            restTotal > 0f -> value / restTotal * restBudget
            // 所有扇区都被抬到下限（整体数据量极小）：剩下的项没有预算可分
            else -> 0f
        }
    }
}

/**
 * 通用环形图，基于 Vico 的 `PieChart` + `PieSize.Inner`（挖内孔）实现。
 *
 * 设计取舍：
 * - **扇区不画任何标签、也不带图例**。环形图的职责只是把「谁占得多」一眼表达出来，
 *   具体名称与数值由调用方在列表里给出；调用方把颜色复用到列表项上，两边自然对应。
 * - **小扇区有角度下限**（[minSweepDegrees]），避免占比极低的分类在图上彻底消失。
 *   代价是这一项的视觉占比会略大于真实占比 —— 这是「看得见」换来的，不需要在图上标注；
 *   真实数值始终由调用方在列表里如实展示。
 * - **`animateIn = false`**：首次拿到数据直接到位、不播进场动画。图表放在 `LazyColumn`
 *   的 item 里，滚出可视区再滚回来会重新进入组合，若播进场动画就会看到一次莫名其妙的
 *   重播闪烁（`ChartDataCard` 关掉 Vico 初始化动画是同一个原因）。
 *   注意这里**没有**把 `animationSpec` 一起关掉：数据更新（例如清理完某个分类）时仍会
 *   平滑过渡到新占比，这是想要的效果。
 *
 * @param slices 扇区列表，值为任意同单位的正值（内部会自行归一化）。值为 0 或负的扇区不会被绘制。
 * @param modifier 外部修饰符，作用在承载图表与圆心的 [Box] 上。
 * @param diameter 圆环外径。
 * @param thickness 圆环厚度（外径与内径之差的一半），决定中间留出的圆孔大小。
 * @param gap 相邻扇区之间的缝隙宽度。
 * @param minSweepDegrees 单个扇区的最小角度，传 0 表示按真实占比绘制、不做任何下限处理。
 * @param center 圆心内容插槽，居中绘制在圆孔里。
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp,
    thickness: Dp = 20.dp,
    gap: Dp = 3.dp,
    minSweepDegrees: Float = DefaultMinSweepDegrees,
    center: @Composable BoxScope.() -> Unit = {},
) {
    // 稳定化入参：调用方若在每次重组时内联构造 listOf(...)，内容相等但实例不同，
    // rememberPieChart 的 remember 键会随之变化 → 重建 PieChart 并重启图表副作用。
    // 这里按内容做一次记忆，内容不变就复用同一实例。
    val stableSlices = remember(slices) { slices.toList() }

    // 真实权重 → 带最小角度下限的角度值。这一步只影响绘制，不影响调用方展示的数值。
    val sweepDegrees = remember(stableSlices, minSweepDegrees) {
        enforceMinimumSweep(stableSlices.map { it.value }, minSweepDegrees)
    }
    val hasData = sweepDegrees.any { it > 0f }

    if (!hasData) {
        // 无有效数据时不构造图表：SliceProvider 要求至少一个扇区，
        // 且总和为 0 会让库内出现 0/0（NaN 角度）。只保留圆心内容。
        Box(modifier = modifier, contentAlignment = Alignment.Center) { center() }
    } else {
        val pieChart = rememberPieChart(
            sliceProvider = PieChart.SliceProvider.series(
                stableSlices.map { slice -> PieChart.Slice(fill = Fill(slice.color)) }
            ),
            // 扇区间隙：环形图下库会把它做成等宽的径向细缝（createParallelSlicePath）
            spacing = gap,
            outerSize = PieSize.Outer.fixed(diameter),
            // 内径 = 外径 - 2 × 环厚，即挖出圆孔变成环形；至少 0（等于不挖孔）
            innerSize = PieSize.Inner.fixed((diameter - thickness * 2).coerceAtLeast(0.dp)),
            startAngle = -90f,
        )
        val modelProducer = remember { PieChartModelProducer() }

        LaunchedEffect(sweepDegrees) {
            modelProducer.runTransaction {
                // List<Float> 因泛型不变性不能直接当 Iterable<Number> 用，逐项上转为 Number
                pieModel { series(sweepDegrees.map { it as Number }) }
            }
        }

        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            PieChartHost(
                chart = pieChart,
                modelProducer = modelProducer,
                // 由 chartAreaHeight 决定高度（= 外径），宽度仍撑满可用空间、圆环水平居中
                chartAreaHeight = diameter,
            )
            // 圆心内容：叠在图表之上居中
            center()
        }
    }
}
