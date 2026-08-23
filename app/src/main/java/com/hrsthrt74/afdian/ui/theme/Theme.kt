package com.hrsthrt74.afdian.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
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
import com.hrsthrt74.afdian.data.settings.ThemeRepository
import com.hrsthrt74.afdian.data.settings.ThemeSettings
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
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeSettings by ThemeRepository.settings(context).collectAsState(initial = null)
    val currentSettings = themeSettings ?: return
    // 把 colorMode 也作为 remember 键，切换模式时重建 controller 立即生效
    val controller = remember(primaryColor, colorMode) {
        // 自定义主题色时需要同时定制明暗两套色板，否则用默认色板即可
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
            content()
        }
    }
}
