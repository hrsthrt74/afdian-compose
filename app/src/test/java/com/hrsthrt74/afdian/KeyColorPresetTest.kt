package com.hrsthrt74.afdian

import androidx.compose.ui.graphics.Color
import com.hrsthrt74.afdian.data.settings.KeyColorPreset
import com.hrsthrt74.afdian.data.settings.resolveForSdk
import com.hrsthrt74.afdian.ui.theme.AfdianBrandColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Monet 种子色（Key Color）预设的回归测试。
 *
 * 这块最容易踩的两个坑：
 * 1. 持久化下标 —— 日后插入/重排档位会把用户已选颜色错位成另一个色，所以只存字符串；
 * 2. 低版本选到「跟随系统壁纸」—— 系统取色要 API 31+，低版本必须被过滤掉，
 *    否则会出现「下拉显示跟随系统、实际却用了别的色」的错位。
 */
class KeyColorPresetTest {

    /** 官方 demo 的 6 个预设色（紫色已被剔除）+ 跟随系统 + 爱发电主题色 = 8 项。 */
    @Test
    fun presetCountMatchesDesign() {
        assertEquals(8, KeyColorPreset.entries.size)
        assertEquals(8, KeyColorPreset.selectable(systemWallpaperSupported = true).size)
    }

    /** 低版本要藏掉「跟随系统壁纸」，其余 7 项照常可选（种子色是本地算的，不受系统版本限制）。 */
    @Test
    fun systemPresetIsHiddenWhenSystemWallpaperIsUnsupported() {
        val selectable = KeyColorPreset.selectable(systemWallpaperSupported = false)
        assertEquals(7, selectable.size)
        assertFalse("低版本下拉里不应出现「跟随系统壁纸」", KeyColorPreset.System in selectable)
    }

    /** 存储值必须唯一，否则 fromStorage 会随 entries 顺序漂移。 */
    @Test
    fun storageValuesAreUnique() {
        val values = KeyColorPreset.entries.map { it.storageValue }
        assertEquals("storageValue 必须唯一，实际 $values", values.distinct(), values)
    }

    /** 展示顺序：「跟随系统壁纸」在首位，「爱发电主题色」紧随其后，其余是官方 demo 的预设色。 */
    @Test
    fun afdianPresetComesRightAfterSystemInDropdown() {
        val selectable = KeyColorPreset.selectable(systemWallpaperSupported = true)
        assertEquals(KeyColorPreset.System, selectable[0])
        assertEquals(KeyColorPreset.Afdian, selectable[1])
        // 低版本过滤掉首项后，「爱发电主题色」自然落到第一位
        assertEquals(KeyColorPreset.Afdian, KeyColorPreset.selectable(systemWallpaperSupported = false).first())
    }

    @Test
    fun storageValuesRoundTripAndFallBackToDefault() {
        KeyColorPreset.entries.forEach { preset ->
            assertEquals(preset, KeyColorPreset.fromStorage(preset.storageValue))
        }
        // 老版本没有这个键（null）、或读到无法识别的值，都要回落默认档而不是崩
        assertEquals(KeyColorPreset.Default, KeyColorPreset.fromStorage(null))
        assertEquals(KeyColorPreset.Default, KeyColorPreset.fromStorage(""))
        assertEquals(KeyColorPreset.Default, KeyColorPreset.fromStorage("不存在的档位"))
        assertEquals(KeyColorPreset.System, KeyColorPreset.Default)
    }

    /** 低版本把「跟随系统壁纸」解析成真正生效的档位（蓝色），其余档位原样返回。 */
    @Test
    fun resolveForSdkFallsBackToBlueOnlyWhenNeeded() {
        assertEquals(KeyColorPreset.Blue, KeyColorPreset.System.resolveForSdk(systemWallpaperSupported = false))
        assertEquals(KeyColorPreset.System, KeyColorPreset.System.resolveForSdk(systemWallpaperSupported = true))
        KeyColorPreset.entries.filter { it != KeyColorPreset.System }.forEach { preset ->
            assertEquals(preset, preset.resolveForSdk(systemWallpaperSupported = false))
        }
    }

    /** 种子色取值：只有「跟随系统壁纸」不指定种子色，其余都必须有色值。 */
    @Test
    fun onlySystemPresetHasNoSeedColor() {
        assertNull(KeyColorPreset.System.color)
        KeyColorPreset.entries.filter { it != KeyColorPreset.System }.forEach { preset ->
            assertNotNull("${preset.storageValue} 必须指定种子色", preset.color)
        }
    }

    /** 爱发电主题色的种子色必须与品牌紫同源，防止日后改品牌色时这里忘记同步。 */
    @Test
    fun afdianPresetSharesTheBrandColor() {
        assertEquals(AfdianBrandColor, KeyColorPreset.Afdian.color)
    }

    /**
     * 收录的预设色要与 miuix 官方 demo 的 KeyColors 逐一对齐。
     *
     * 官方那 7 色里的「紫色」(#7C4DFF) 被**有意剔除**：它和爱发电品牌紫观感太接近，
     * 摆在一起下拉里分不清。所以这里只断言剩下 6 项。
     */
    @Test
    fun presetsMatchMiuixDemoPalette() {
        assertEquals(Color(0xFF3482FF), KeyColorPreset.Blue.color)
        assertEquals(Color(0xFF36D167), KeyColorPreset.Green.color)
        assertEquals(Color(0xFFFFB21D), KeyColorPreset.Yellow.color)
        assertEquals(Color(0xFFFF5722), KeyColorPreset.Orange.color)
        assertEquals(Color(0xFFE91E63), KeyColorPreset.Pink.color)
        assertEquals(Color(0xFF00BCD4), KeyColorPreset.Teal.color)
    }

    /** 「紫色」不该被重新加回来：它和爱发电主题色太像，是这份列表里唯一被主动剔除的一项。 */
    @Test
    fun purplePresetIsIntentionallyDropped() {
        assertFalse(
            "官方预设里的紫色与爱发电主题色过近，已被有意剔除",
            KeyColorPreset.entries.any { it.color == Color(0xFF7C4DFF) },
        )
    }
}
