package com.hrsthrt74.afdian.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.settings.KeyColorPreset
import com.hrsthrt74.afdian.data.settings.ThemeRepository
import com.hrsthrt74.afdian.data.settings.ThemeSettings
import com.hrsthrt74.afdian.data.settings.resolveForSdk
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalThemeSettings = staticCompositionLocalOf<ThemeSettings> {
    error("LocalThemeSettings 尚未提供：请在 AfdianTheme 作用域内读取")
}

@Composable
fun AfdianTheme(
    primaryColor: Color? = null,
    // 配色模式：0=跟随系统 1=浅色 2=深色（与 SettingsStore 的约定一致）
    colorMode: Int = 0,
    // Monet 配色总开关：开启后上述三档改用 Monet 色板
    monetEnabled: Boolean = false,
    // Monet 种子色预设；[KeyColorPreset.System] 表示跟随系统壁纸
    keyColor: KeyColorPreset = KeyColorPreset.Default,
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

    // 把 colorMode 也作为 remember 键，切换模式时重建 controller 立即生效。
    // 影响色板的入参必须全部列进 key，漏掉任何一个都会让对应开关「点了没反应」
    val controller = remember(primaryColor, colorMode, monetEnabled, keyColor, systemWallpaperSupported) {
        if (monetEnabled) {
            // Monet 分支：保留原「跟随系统 / 浅色 / 深色」的明暗选择，
            // 只是把色板来源换成系统壁纸或指定种子色 —— 与 miuix 官方 demo 的 Monet 三档一致
            val monetMode = when (colorMode) {
                1 -> ColorSchemeMode.MonetLight
                2 -> ColorSchemeMode.MonetDark
                else -> ColorSchemeMode.MonetSystem
            }
            // 此处有意忽略 primaryColor（即「使用爱发电主题色」开关）：Monet 开启时由种子色接管，
            // 开关在设置页会被隐藏，其 DataStore 值原样保留，关闭 Monet 后自动恢复原行为。
            // keyColor 为 null 时（跟随系统壁纸）由 Miuix 自行读取系统配色
            ThemeController(
                colorSchemeMode = monetMode,
                keyColor = keyColor.resolveForSdk(systemWallpaperSupported).color,
            )
        } else {
            // 原有非 Monet 逻辑保持不变
            val mode = when (colorMode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
            if (primaryColor != null) {
                // 用 AfdianColors 的工厂函数生成整套品牌色系：
                // 除 primary 外，禁用态/容器/下拉选中项等一并跟随主题色
                ThemeController(
                    colorSchemeMode = mode,
                    lightColors = afdianLightColorScheme(primaryColor),
                    darkColors = afdianDarkColorScheme(primaryColor),
                )
            } else {
                ThemeController(mode)
            }
        }
    }
    // DataStore 首次读取完成后再组合内容，确保全局模糊状态不会出现首帧闪烁。
    CompositionLocalProvider(LocalThemeSettings provides currentSettings) {
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
