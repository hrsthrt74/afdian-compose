package com.hrsthrt74.afdian.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun AfdianTheme(
    primaryColor: Color? = null,
    content: @Composable () -> Unit,
) {
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
    MiuixTheme(controller = controller, content = content)
}
