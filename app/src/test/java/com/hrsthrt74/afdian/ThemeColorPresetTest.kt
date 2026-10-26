package com.hrsthrt74.afdian

import androidx.compose.ui.graphics.Color
import com.hrsthrt74.afdian.data.settings.ThemeColorPreset
import com.hrsthrt74.afdian.data.settings.resolveForTheme
import com.hrsthrt74.afdian.ui.theme.AfdianBrandColor
import com.hrsthrt74.afdian.ui.theme.MiuixBrandBlue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题色预设的回归测试。
 *
 * 这块最容易踩的三个坑：
 * 1. 持久化下标 —— 日后插入/重排档位会把用户已选颜色错位成另一个色，所以只存字符串；
 * 2. 在 MIUIX 主题 / 低版本设备上选到「跟随壁纸」—— 前者是静态色板不取壁纸，
 *    后者系统取色要 API 31+，两种情况都必须被过滤/回退，否则会出现「显示跟随壁纸、实际用了别的色」；
 * 3. 旧版本键值的迁移 —— 旧的「蓝色」要并到 MIUIX 蓝，不能变成未知值回落成另一个档。
 */
class ThemeColorPresetTest {

    /** 自定义 + 跟随壁纸 + MIUIX 蓝 + 爱发电紫 + 官方 demo 的 5 个预设色（紫色被剔除）+ 彩虹 = 10 项。 */
    @Test
    fun presetCountMatchesDesign() {
        assertEquals(10, ThemeColorPreset.entries.size)
        assertEquals(10, ThemeColorPreset.selectable(systemWallpaperSupported = true).size)
    }

    /** 两种主题都提供「跟随壁纸」：MIUIX 下它只让主色随壁纸，不是禁用项。 */
    @Test
    fun systemPresetIsAvailableInBothThemes() {
        val selectable = ThemeColorPreset.selectable(systemWallpaperSupported = true)
        assertEquals(10, selectable.size)
        assertTrue("两种主题下都应能选「跟随壁纸」", ThemeColorPreset.System in selectable)
    }

    /** 低版本没有系统取色能力：藏掉「跟随壁纸」。 */
    @Test
    fun systemPresetIsHiddenWhenSystemWallpaperIsUnsupported() {
        val selectable = ThemeColorPreset.selectable(systemWallpaperSupported = false)
        assertEquals(9, selectable.size)
        assertFalse("低版本选择器里不应出现「跟随壁纸」", ThemeColorPreset.System in selectable)
    }

    /** 存储值必须唯一，否则 fromStorage 会随 entries 顺序漂移。 */
    @Test
    fun storageValuesAreUnique() {
        val values = ThemeColorPreset.entries.map { it.storageValue }
        assertEquals("storageValue 必须唯一，实际 $values", values.distinct(), values)
    }

    /** 展示顺序：自定义 → 跟随壁纸 → MIUIX 蓝 → 爱发电紫 → 其余预设 → 彩虹（彩虹固定放最后）。 */
    @Test
    fun presetsKeepDesignedOrder() {
        val selectable = ThemeColorPreset.selectable(systemWallpaperSupported = true)
        assertEquals(ThemeColorPreset.Custom, selectable[0])
        assertEquals(ThemeColorPreset.System, selectable[1])
        assertEquals(ThemeColorPreset.Miuix, selectable[2])
        assertEquals(ThemeColorPreset.Afdian, selectable[3])
        assertEquals(ThemeColorPreset.Rainbow, selectable.last())
        // 低版本过滤掉「跟随壁纸」后，自定义仍在首位
        assertEquals(
            ThemeColorPreset.Custom,
            ThemeColorPreset.selectable(systemWallpaperSupported = false).first(),
        )
    }

    @Test
    fun storageValuesRoundTripAndFallBackToDefault() {
        ThemeColorPreset.entries.forEach { preset ->
            assertEquals(preset, ThemeColorPreset.fromStorage(preset.storageValue))
        }
        // 老版本没有这个键（null）、或读到无法识别的值，都要回落默认档而不是崩
        assertEquals(ThemeColorPreset.Default, ThemeColorPreset.fromStorage(null))
        assertEquals(ThemeColorPreset.Default, ThemeColorPreset.fromStorage(""))
        assertEquals(ThemeColorPreset.Default, ThemeColorPreset.fromStorage("不存在的档位"))
        assertEquals(ThemeColorPreset.Miuix, ThemeColorPreset.Default)
    }

    /** 旧版本的「蓝色」与 MIUIX 蓝同色，迁移时必须并到同一档，不能回落成默认以外的东西。 */
    @Test
    fun legacyBlueValueMigratesToMiuix() {
        assertEquals(ThemeColorPreset.Miuix, ThemeColorPreset.fromStorage("blue"))
    }

    /** 旧「种子色」里的其余取值原样迁移。 */
    @Test
    fun legacyValuesMigrateAsIs() {
        assertEquals(ThemeColorPreset.System, ThemeColorPreset.fromStorage("system"))
        assertEquals(ThemeColorPreset.Afdian, ThemeColorPreset.fromStorage("afdian"))
        assertEquals(ThemeColorPreset.Green, ThemeColorPreset.fromStorage("green"))
        assertEquals(ThemeColorPreset.Teal, ThemeColorPreset.fromStorage("teal"))
    }

    /** 「跟随壁纸」只在设备不支持系统取色时回退成 MIUIX 蓝，其余情况原样保留。 */
    @Test
    fun resolveForThemeFallsBackToMiuixOnlyWhenNeeded() {
        assertEquals(
            ThemeColorPreset.System,
            ThemeColorPreset.System.resolveForTheme(systemWallpaperSupported = true),
        )
        assertEquals(
            ThemeColorPreset.Miuix,
            ThemeColorPreset.System.resolveForTheme(systemWallpaperSupported = false),
        )
        ThemeColorPreset.entries.filter { it != ThemeColorPreset.System }.forEach { preset ->
            assertEquals(preset, preset.resolveForTheme(systemWallpaperSupported = false))
            assertEquals(preset, preset.resolveForTheme(systemWallpaperSupported = true))
        }
    }

    /** 色值：自定义由用户自选、跟随壁纸取系统配色、彩虹由时间算，这三档没有固定色值；其余都必须有。 */
    @Test
    fun onlyCustomSystemAndRainbowPresetsHaveNoColor() {
        assertNull(ThemeColorPreset.System.color)
        assertNull(ThemeColorPreset.Custom.color)
        assertNull(ThemeColorPreset.Rainbow.color)
        ThemeColorPreset.entries
            .filter {
                it != ThemeColorPreset.System &&
                    it != ThemeColorPreset.Custom &&
                    it != ThemeColorPreset.Rainbow
            }
            .forEach { preset ->
                assertNotNull("${preset.storageValue} 必须指定色值", preset.color)
            }
    }

    /** 自定义档的默认色是 50% 灰：未调色时主题不该跳成某个预设色。 */
    @Test
    fun defaultCustomColorIsFiftyPercentGray() {
        assertEquals(Color(0xFF808080), ThemeColorPreset.DefaultCustomColor)
    }

    /** 爱发电紫必须与品牌紫同源，防止日后改品牌色时这里忘记同步。 */
    @Test
    fun afdianPresetSharesTheBrandColor() {
        assertEquals(AfdianBrandColor, ThemeColorPreset.Afdian.color)
    }

    /** MIUIX 蓝必须等于 Miuix 默认色板的主色，否则「MIUIX 蓝」就不是官方的那个蓝了。 */
    @Test
    fun miuixPresetSharesTheMiuixDefaultPrimary() {
        assertEquals(MiuixBrandBlue, ThemeColorPreset.Miuix.color)
        assertEquals(Color(0xFF3482FF), ThemeColorPreset.Miuix.color)
    }

    /**
     * 收录的预设色要与 miuix 官方 demo 的 KeyColors 逐一对齐。
     *
     * 官方那 7 色里的「紫色」(#7C4DFF) 被**有意剔除**：它和爱发电品牌紫观感太接近，
     * 摆在一起选择器里分不清。所以这里只断言剩下 5 项。
     */
    @Test
    fun presetsMatchMiuixDemoPalette() {
        assertEquals(Color(0xFF36D167), ThemeColorPreset.Green.color)
        assertEquals(Color(0xFFFFB21D), ThemeColorPreset.Yellow.color)
        assertEquals(Color(0xFFFF5722), ThemeColorPreset.Orange.color)
        assertEquals(Color(0xFFE91E63), ThemeColorPreset.Pink.color)
        assertEquals(Color(0xFF00BCD4), ThemeColorPreset.Teal.color)
    }

    /** 「紫色」不该被重新加回来：它和爱发电紫太像，是这份列表里唯一被主动剔除的一项。 */
    @Test
    fun purplePresetIsIntentionallyDropped() {
        assertFalse(
            "官方预设里的紫色与爱发电紫过近，已被有意剔除",
            ThemeColorPreset.entries.any { it.color == Color(0xFF7C4DFF) },
        )
    }
}
