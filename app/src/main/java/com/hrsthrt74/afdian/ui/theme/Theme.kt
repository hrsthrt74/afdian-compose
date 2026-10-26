package com.hrsthrt74.afdian.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.settings.ThemeColorPreset
import com.hrsthrt74.afdian.data.settings.ThemeRepository
import com.hrsthrt74.afdian.data.settings.ThemeSettings
import com.hrsthrt74.afdian.data.settings.resolveForTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.platformDynamicColors

val LocalThemeSettings = staticCompositionLocalOf<ThemeSettings> {
    error("LocalThemeSettings 尚未提供：请在 AfdianTheme 作用域内读取")
}

/**
 * 全局**真正生效**的主题色档位（「跟随壁纸」在 MIUIX 主题或低版本设备上已解析成 MIUIX 蓝）。
 *
 * 设置页的主题色选择器读它来决定高亮哪一颗色球。不要改读 SettingsViewModel 的流：
 * VM 是进入设置页时才创建的，初值固定是默认档位，会先按默认值高亮一帧再跳到真实档位。
 */
val LocalThemeColor = compositionLocalOf<ThemeColorPreset> {
    error("LocalThemeColor 尚未提供：请在 AfdianTheme 作用域内读取")
}

/**
 * 自定义主题色的具体色值（用户在外观页调色盘里挑的颜色）。
 *
 * 选择器里「自定义」那颗色球读它上色；只有在 [LocalThemeColor] 为
 * [ThemeColorPreset.Custom] 时才参与主题。
 */
val LocalCustomThemeColor = compositionLocalOf<Color> {
    error("LocalCustomThemeColor 尚未提供：请在 AfdianTheme 作用域内读取")
}

/**
 * 彩虹主题色的轮换节奏。
 *
 * 每 [RainbowStepMillis] 毫秒把色相往前推 [RainbowHueStep] 度。
 * 步长保持很小：除外观页外的其他页面都没有 `ThemeColorTransition`，步子一大就能看到明显的「跳色」；
 * 节奏按「看得见在流动」调得比较快 —— 代价是整包重组更频繁，这一点在选中彩虹档时已在警告弹窗里说明。
 *
 * 按 100 毫秒 5 度计算，转完一整圈约 7 秒。
 */
private const val RainbowStepMillis = 100L
private const val RainbowHueStep = 5f
/** 彩虹色的饱和度：够鲜艳但不刺眼，深色模式下也不会糊成一片。 */
private const val RainbowSaturation = 0.65f
/** 彩虹色的明度：接近满亮，保证作为主题色时对比度足够。 */
private const val RainbowValue = 0.95f

/**
 * 按固定节奏轮换色相的「彩虹」主题色。
 *
 * 只在主题色真正选中 [ThemeColorPreset.Rainbow] 时被调用：其余档位不启动计时循环，
 * 不会白白让整个 App 每秒重组一次。色相从红色（0 度）起步，进程存活期间一直往前走。
 */
@Composable
private fun rememberRainbowColor(): Color {
    var hue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(RainbowStepMillis)
            hue = (hue + RainbowHueStep) % 360f
        }
    }
    return Color.hsv(hue, RainbowSaturation, RainbowValue)
}

@Composable
fun AfdianTheme(
    // 配色模式：0=跟随系统 1=浅色 2=深色（与 SettingsStore 的约定一致）
    colorMode: Int = 0,
    // 主题样式：false=MIUIX（Miuix 静态色板） true=Monet（按壁纸 / 主题色生成色板）
    monetEnabled: Boolean = false,
    // 主题色；[ThemeColorPreset.System] 表示跟随系统壁纸（仅 Monet + Android 12+ 生效）
    themeColor: ThemeColorPreset = ThemeColorPreset.Default,
    // 自定义主题色的具体色值（themeColor == Custom 时生效；默认 50% 灰）
    customColor: Color = ThemeColorPreset.DefaultCustomColor,
    // 是否等主题 DataStore 首值到达后再组合内容。
    // 必须无条件展示的阻断页（装错 edition）传 false：
    // 这类页面的存在意义就是「无论如何都要让用户看见」，不能被一个偏好文件的读取挡在启动画面后面。
    // 其余调用点保持默认 true，行为与之前完全一致。
    waitForThemeSettings: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeSettings by ThemeRepository.settings(context).collectAsState(initial = null)
    val currentSettings = themeSettings
        ?: if (waitForThemeSettings) {
            // 主题还没读出来：不组合内容，等下一次发射（DataStore 首次读取通常只有几毫秒）
            return
        } else {
            // 阻断页兜底：用默认值先渲染。模糊开关仍跟随构建能力（legacy 恒为 false），
            // 保证兜底状态与数据源折叠后的语义一致
            ThemeSettings(
                enableBlur = BuildConfig.SUPPORT_BLUR,
                progressiveBlur = BuildConfig.SUPPORT_BLUR,
            )
        }

    // 系统壁纸取色（Miuix 内部读系统 Material You 配置）要求 Android 12 / API 31 以上；
    // 这是进程内不变的常量，remember 一次即可
    val systemWallpaperSupported = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    // 「真正生效」的主题色：跟随壁纸在 MIUIX 主题 / 低版本设备上回退成 MIUIX 蓝。
    // 既用于下面构建色板，也下发给设置页高亮色球，保证「显示的那颗」和「实际用的那颗」一致
    val resolvedColor = themeColor.resolveForTheme(systemWallpaperSupported = systemWallpaperSupported)
    // 彩虹档的色值由时间算：只有真正选中它时才启动轮换循环，别的档位不付出重组代价
    val rainbowColor = if (resolvedColor == ThemeColorPreset.Rainbow) rememberRainbowColor() else null
    // MIUIX + 跟随壁纸：只让**主色**跟随壁纸。
    // 从系统动态色里取主色，再按 Miuix 的派生规律染主色家族；背景 / 卡片 / 文字这些中性色
    // 仍然是 Miuix 静态色板 —— 这就是「MIUIX 主题、主色随壁纸」与 Monet（整套都随壁纸）的区别。
    val wallpaperAccent = if (resolvedColor == ThemeColorPreset.System && !monetEnabled) {
        val dark = when (colorMode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }
        platformDynamicColors(dark = dark).primary
    } else {
        null
    }
    // 实际用来染色 / 做种子的色值：
    // 自定义档取用户挑的颜色，彩虹档取当前这一拍的色相；
    // MIUIX+跟随壁纸取壁纸主色，Monet+跟随壁纸为 null（整套都交给系统）
    val accentColor = when (resolvedColor) {
        ThemeColorPreset.Custom -> customColor
        ThemeColorPreset.Rainbow -> rainbowColor
        ThemeColorPreset.System -> wallpaperAccent
        else -> resolvedColor.color
    }

    // 把 colorMode 也作为 remember 键，切换模式时重建 controller 立即生效。
    // 影响色板的入参必须全部列进 key，漏掉任何一个都会让对应开关「点了没反应」
    val controller = remember(colorMode, monetEnabled, resolvedColor, accentColor) {
        if (monetEnabled) {
            // Monet 分支：保留原「跟随系统 / 浅色 / 深色」的明暗选择，
            // 只是把色板来源换成系统壁纸或指定种子色 —— 与 miuix 官方 demo 的 Monet 三档一致
            val monetMode = when (colorMode) {
                1 -> ColorSchemeMode.MonetLight
                2 -> ColorSchemeMode.MonetDark
                else -> ColorSchemeMode.MonetSystem
            }
            // 种子色用解析后的 accentColor，不能读 ThemeColorPreset.color：
            // 「自定义」「彩虹」两档的 color 恒为 null（色值另外存 / 由时间算），
            // 传 null 会让 Miuix 退回「读系统壁纸色」，表现就是选什么颜色都是壁纸蓝。
            // 「跟随壁纸」在 Monet 下的 accentColor 同样是 null，正好交给 Miuix 读系统配色
            ThemeController(
                colorSchemeMode = monetMode,
                keyColor = accentColor,
            )
        } else {
            // MIUIX 分支：Miuix 静态色板，主色由主题色决定
            val mode = when (colorMode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
            if (resolvedColor == ThemeColorPreset.Miuix || accentColor == null) {
                // MIUIX 蓝：原样使用官方默认色板（连禁用态、强调容器等都是官方的蓝）
                ThemeController(mode)
            } else {
                // 其余档位（含自定义、跟随壁纸）：用 AfdianColors 的工厂函数按主色生成整套品牌色系，
                // 除 primary 外，禁用态/容器/下拉选中项等一并跟随主题色
                ThemeController(
                    colorSchemeMode = mode,
                    lightColors = afdianLightColorScheme(accentColor),
                    darkColors = afdianDarkColorScheme(accentColor),
                )
            }
        }
    }
    // DataStore 首次读取完成后再组合内容，确保全局模糊状态不会出现首帧闪烁。
    CompositionLocalProvider(
        LocalThemeSettings provides currentSettings,
        LocalThemeColor provides resolvedColor,
        LocalCustomThemeColor provides customColor,
    ) {
        MiuixTheme(controller = controller) {
            // 把窗口（decorView）背景同步成主题 background 色：
            // Compose 只画自己的内容层，页面切换/键盘收起等瞬间露出的窗口底
            // 默认是系统白色，深色模式下会「闪一下白」；这里让它跟随主题即消失
            val view = LocalView.current
            val windowBackground = MiuixTheme.colorScheme.background
            SideEffect {
                (view.context as? Activity)?.window?.decorView?.background =
                    ColorDrawable(windowBackground.toArgb())
            }
            // 系统栏（状态栏 / 导航栏）图标颜色跟随 App 实际生效的深浅：
            // 手动切深色而系统仍是浅色时，系统默认给的还是黑图标，会看不见
            ApplySystemBarAppearance(isDark = isDarkColorScheme(MiuixTheme.colorScheme))
            content()
        }
    }
}
