package com.hrsthrt74.afdian.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.hrsthrt74.afdian.data.settings.ThemeRepository
import com.hrsthrt74.afdian.data.settings.ThemeSettings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

val LocalThemeSettings = staticCompositionLocalOf<ThemeSettings> {
    error("LocalThemeSettings 尚未提供：请在 AfdianTheme 作用域内读取")
}

@Composable
fun AfdianTheme(
    primaryColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeSettings by ThemeRepository.settings(context).collectAsState(initial = null)
    val currentSettings = themeSettings ?: return
    val controller = remember(primaryColor) {
        if (primaryColor != null) {
            ThemeController(
                colorSchemeMode = ColorSchemeMode.System,
                lightColors = lightColorScheme(primary = primaryColor),
                darkColors = darkColorScheme(primary = primaryColor),
            )
        } else {
            ThemeController(ColorSchemeMode.System)
        }
    }
    // DataStore 首次读取完成后再组合内容，确保全局模糊状态不会出现首帧闪烁。
    CompositionLocalProvider(LocalThemeSettings provides currentSettings) {
        MiuixTheme(controller = controller, content = content)
    }
}
