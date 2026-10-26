package com.hrsthrt74.afdian

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.hrsthrt74.afdian.ui.theme.AfdianBrandColor
import com.hrsthrt74.afdian.ui.theme.MiuixBrandBlue
import com.hrsthrt74.afdian.ui.theme.afdianDarkColorScheme
import com.hrsthrt74.afdian.ui.theme.afdianLightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 色板生成的自检，重点是主色变体 [top.yukonga.miuix.kmp.theme.Colors.primaryVariant]。
 *
 * Miuix 官方浅色默认色板把变体写得和主色一模一样（`#3482FF`），我们若照抄，
 * 调色盘顶部的三段预览就会出现两段同色、也看不出层次。这里把「变体 = 主色向黑 15%」
 * 这条规律钉住，防止以后又退化成同色。
 */
class AfdianColorsTest {

    /** 覆盖各档预设里会真正传给色板生成函数的强调色 */
    private val accents = listOf(
        MiuixBrandBlue,          // MIUIX 蓝
        AfdianBrandColor,        // 爱发电紫
        Color(0xFF34C759),       // 绿
        Color(0xFFF5A623),       // 橙
        Color(0xFF808080),       // 自定义档默认的 50% 灰
    )

    @Test
    fun `主色变体不等于主色`() {
        accents.forEach { accent ->
            assertNotEquals("浅色：$accent", accent, afdianLightColorScheme(accent).primaryVariant)
            assertNotEquals("深色：$accent", accent, afdianDarkColorScheme(accent).primaryVariant)
        }
    }

    @Test
    fun `主色变体更深`() {
        accents.forEach { accent ->
            assertTrue(
                "浅色：$accent",
                afdianLightColorScheme(accent).primaryVariant.luminance() < accent.luminance(),
            )
            assertTrue(
                "深色：$accent",
                afdianDarkColorScheme(accent).primaryVariant.luminance() < accent.luminance(),
            )
        }
    }

    @Test
    fun `主色变体是同色系`() {
        // 「更深一档」而不是「换了个颜色」：主色最亮的那个通道，变体里仍然最亮
        accents.forEach { accent ->
            val primaryMax = maxChannel(accent)
            listOf(afdianLightColorScheme(accent), afdianDarkColorScheme(accent)).forEach { scheme ->
                assertEquals("$accent", primaryMax, maxChannel(scheme.primaryVariant))
            }
        }
    }

    @Test
    fun `主色变体按约定向黑插值`() {
        // 与实现一一对应：改系数（0.15）时这里会一起失败，提醒同步设计说明
        accents.forEach { accent ->
            val expected = lerp(accent, Color.Black, 0.15f)
            assertEquals(expected, afdianLightColorScheme(accent).primaryVariant)
            assertEquals(expected, afdianDarkColorScheme(accent).primaryVariant)
        }
    }

    @Test
    fun `主色本身与传入的强调色一致`() {
        // 防止以后「变体」的改动不小心把主色也一起改了
        accents.forEach { accent ->
            assertEquals(accent, afdianLightColorScheme(accent).primary)
            assertEquals(accent, afdianDarkColorScheme(accent).primary)
        }
    }

    /** 返回最亮通道的序号：0=R、1=G、2=B */
    private fun maxChannel(color: Color): Int {
        val channels = listOf(color.red, color.green, color.blue)
        return channels.indexOf(channels.max())
    }
}
