package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_DEFAULT
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_MAX
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_MIN
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_SLIDER_STEPS
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_STEP
import com.hrsthrt74.afdian.data.settings.normalizeDataCardFontWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据卡数值字体（MiSans Condensed）宽度轴的回归测试。
 *
 * 锁住几件一旦被改就会静默劣化、却很难从界面看出来（「数字突然变形」或「拖到档位上不震动」）的事：
 * 1. **区间 / 档位间隔 / 默认值**：字体 fvar 的 wdth 轴是 10–200，产品只放开偏窄的 10–100，
 *    且**每 10 个单位一档**，默认 60。改动它们等于改产品行为，应当被测试拦下。
 * 2. **归一化**：这个值会经 DataStore 持久化并驱动字体轴，越界值必须夹回区间、
 *    非档位值必须吸附到最近的档位 —— 否则会出现「滑块停不住的档位」或非法轴值。
 * 3. **档位划分与 miuix 的 steps 换算一致**：`steps = 档位数 - 2`，算错会让档位数量不对。
 */
class DataCardFontWidthTest {

    /** 区间、档位间隔与默认值都与产品选定的一致。 */
    @Test
    fun rangeStepAndDefaultMatchDesign() {
        assertEquals(10, DATACARD_FONT_WIDTH_MIN)
        assertEquals(100, DATACARD_FONT_WIDTH_MAX)
        assertEquals(10, DATACARD_FONT_WIDTH_STEP)
        assertEquals(60, DATACARD_FONT_WIDTH_DEFAULT)
    }

    /** 区间必须能被档位间隔整除，且 miuix 的 steps = 档位数 - 2。 */
    @Test
    fun sliderStepsMatchRange() {
        assertEquals(
            "区间跨度必须能被档位间隔整除，否则末档对不齐",
            0,
            (DATACARD_FONT_WIDTH_MAX - DATACARD_FONT_WIDTH_MIN) % DATACARD_FONT_WIDTH_STEP,
        )
        // (100 - 10) / 10 = 9 段 → 10 个档位 → miuix steps = 10 - 2 = 8
        assertEquals(8, DATACARD_FONT_WIDTH_SLIDER_STEPS)
    }

    /** 默认值必须正好落在某个档位上，否则滑块初始位置与显示值会打架。 */
    @Test
    fun defaultLiesOnStepGrid() {
        assertEquals(
            "默认档位必须落在档位网格上",
            0,
            (DATACARD_FONT_WIDTH_DEFAULT - DATACARD_FONT_WIDTH_MIN) % DATACARD_FONT_WIDTH_STEP,
        )
    }

    /** 网格上的每个档位都必须原样透传（含两端点）。 */
    @Test
    fun gridValuesAreKept() {
        for (width in DATACARD_FONT_WIDTH_MIN..DATACARD_FONT_WIDTH_MAX step DATACARD_FONT_WIDTH_STEP) {
            assertEquals(width, normalizeDataCardFontWidth(width))
        }
    }

    /** 越界值向下/向上都夹到端点档位。 */
    @Test
    fun outOfRangeValuesAreClamped() {
        assertEquals(DATACARD_FONT_WIDTH_MIN, normalizeDataCardFontWidth(0))
        assertEquals(DATACARD_FONT_WIDTH_MIN, normalizeDataCardFontWidth(-100))
        assertEquals(DATACARD_FONT_WIDTH_MIN, normalizeDataCardFontWidth(DATACARD_FONT_WIDTH_MIN - 1))
        assertEquals(DATACARD_FONT_WIDTH_MAX, normalizeDataCardFontWidth(DATACARD_FONT_WIDTH_MAX + 1))
        // 字体轴本身支持到 200，但产品上限是 100：落在轴内、却在产品区间外的值也要被收住
        assertEquals(DATACARD_FONT_WIDTH_MAX, normalizeDataCardFontWidth(200))
        assertEquals(DATACARD_FONT_WIDTH_MAX, normalizeDataCardFontWidth(9999))
        assertEquals(DATACARD_FONT_WIDTH_MAX, normalizeDataCardFontWidth(Int.MAX_VALUE))
    }

    /**
     * 非档位值吸附到最近的档位。
     *
     * 这一条的直接动机：上一版滑块是 1 单位一档，用户可能已经把值拖到过 63 这类非档位值；
     * 若只夹取不吸附，界面会显示一个滑块根本停不住的 63，看着就像「每 10 单位一档」没生效。
     */
    @Test
    fun offGridValuesSnapToNearestStep() {
        assertEquals(60, normalizeDataCardFontWidth(61))
        assertEquals(60, normalizeDataCardFontWidth(63))
        assertEquals(60, normalizeDataCardFontWidth(64))
        // 正好卡在中间时向上取到更宽的档（Kotlin 的 roundToInt 半值向上）
        assertEquals(70, normalizeDataCardFontWidth(65))
        assertEquals(70, normalizeDataCardFontWidth(66))
        assertEquals(30, normalizeDataCardFontWidth(34))
        assertEquals(40, normalizeDataCardFontWidth(36))
        assertEquals(100, normalizeDataCardFontWidth(99))
    }

    /** 归一化结果永远落在档位网格上（穷举区间内所有整数，兜住任何取整边界错误）。 */
    @Test
    fun normalizedResultAlwaysOnGrid() {
        for (raw in -20..220) {
            val normalized = normalizeDataCardFontWidth(raw)
            assertTrue("归一化结果 $normalized 越界（输入 $raw）", normalized in DATACARD_FONT_WIDTH_MIN..DATACARD_FONT_WIDTH_MAX)
            assertEquals(
                "归一化结果 $normalized 不在档位网格上（输入 $raw）",
                0,
                (normalized - DATACARD_FONT_WIDTH_MIN) % DATACARD_FONT_WIDTH_STEP,
            )
        }
    }
}
