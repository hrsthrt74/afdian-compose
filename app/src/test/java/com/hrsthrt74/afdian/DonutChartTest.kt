package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.ui.components.data.enforceMinimumSweep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 环形图「最小扇区角度」换算的回归测试。
 *
 * 这段逻辑决定了图上扇区的视觉占比，一旦算错会出现两种后果：
 * 总和不是一整圈（扇区之间出现空档或重叠），或者小分类又变得看不见。
 * 因此把不变式（总和恒为 360、小项被抬到下限、大项占比在数据健康时不变）逐条钉死。
 */
class DonutChartTest {

    @Test
    fun tinySliceIsRaisedToTheMinimumSweep() {
        // 一个 1000 与一个 1：后者真实角度只有 0.36 度，必须被抬到下限
        val result = enforceMinimumSweep(listOf(1000f, 1f), minSweepDegrees = 5f)

        assertEquals(5f, result[1], 0.001f)
        // 让出的角度全部从大项扣，总和仍是一整圈
        assertEquals(355f, result[0], 0.001f)
        assertEquals(360f, result.sum(), 0.001f)
    }

    @Test
    fun healthyDataKeepsExactProportions() {
        // 两项都远超下限：这一步应退化成等比换算，占比一点不动
        val result = enforceMinimumSweep(listOf(3f, 1f), minSweepDegrees = 5f)

        assertEquals(270f, result[0], 0.001f)
        assertEquals(90f, result[1], 0.001f)
        assertEquals(360f, result.sum(), 0.001f)
    }

    @Test
    fun zeroValuesStayZero() {
        // 值为 0 的扇区不该被「抬」出一个角度来（那会凭空多出一条数据）
        val result = enforceMinimumSweep(listOf(10f, 0f, 10f), minSweepDegrees = 5f)

        assertEquals(0f, result[1], 0.001f)
        assertEquals(180f, result[0], 0.001f)
        assertEquals(180f, result[2], 0.001f)
    }

    @Test
    fun zeroMinimumDisablesTheFloor() {
        // minSweep = 0 应完全关闭下限处理：小项保持真实（不可见的）占比
        val result = enforceMinimumSweep(listOf(1000f, 1f), minSweepDegrees = 0f)

        assertEquals(360f, result.sum(), 0.001f)
        assertTrue("小项应保持真实占比（约 0.36 度），实际 ${result[1]}", result[1] < 1f)
    }

    @Test
    fun tooManySlicesFallsBackToEvenShareInsteadOfOverflowing() {
        // 4 个扇区要求每个至少 200 度，物理上不可能；此时放宽到均分，不能算出一圈以上
        val result = enforceMinimumSweep(listOf(1f, 1f, 1f, 100f), minSweepDegrees = 200f)

        assertEquals(360f, result.sum(), 0.001f)
        result.forEach { value ->
            assertTrue("均分后每项不应超过 90 度，实际 $value", value <= 90f + 0.001f)
        }
    }

    @Test
    fun singleSliceFillsTheWholeCircle() {
        val result = enforceMinimumSweep(listOf(5f), minSweepDegrees = 5f)

        assertEquals(1, result.size)
        assertEquals(360f, result[0], 0.001f)
    }

    @Test
    fun degenerateInputsAreReturnedAsIs() {
        assertEquals(emptyList<Float>(), enforceMinimumSweep(emptyList(), minSweepDegrees = 5f))
        assertEquals(listOf(0f, 0f), enforceMinimumSweep(listOf(0f, 0f), minSweepDegrees = 5f))
    }

    @Test
    fun sumIsAlwaysAFullCircle() {
        // 各种分布下总和都必须是一整圈，否则 Vico 画出来会出现空档或重叠
        val cases = listOf(
            listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f),
            listOf(1000000f, 3f, 7f, 0.5f),
            listOf(1f, 2f),
            listOf(50f, 50f, 1f, 1f, 1f),
        )
        cases.forEach { values ->
            val result = enforceMinimumSweep(values, minSweepDegrees = 5f)
            assertEquals("输入 $values 的总和不是一圈", 360f, result.sum(), 0.01f)
            assertEquals(values.size, result.size)
        }
    }
}
