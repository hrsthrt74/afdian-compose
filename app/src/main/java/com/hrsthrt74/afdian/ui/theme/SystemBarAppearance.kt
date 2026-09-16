package com.hrsthrt74.afdian.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.hrsthrt74.afdian.AppLog
import top.yukonga.miuix.kmp.theme.Colors

/**
 * 判断一份色板是不是「深色」：只看页面底色的相对亮度。
 *
 * 用亮度而不是「记住用户选了哪个模式」来判断，好处是谁手里有当前显示的色板，
 * 谁就能直接判定，不需要把「用户设置」「跟随系统」「过渡中的插值色」三种来源
 * 再对齐一遍。
 */
fun isDarkColorScheme(colors: Colors): Boolean = colors.background.luminance() < 0.5f

/**
 * 把状态栏 / 导航栏的图标颜色同步成当前页面上能看清的那一档。
 *
 * App 开了 edge-to-edge，系统栏是透明的，图标颜色由系统按「系统的深浅色」决定；
 * 于是 App 手动切成深色、而系统还是浅色时，系统仍旧给黑色图标，压在深色背景上
 * 就看不见了。这里按 App 自己实际生效的色板覆盖一次：深色页面用浅色（白）图标。
 *
 * 注意参数语义容易看反：`isAppearanceLightStatusBars = true` 指的是「图标画成深色」，
 * 用在浅色背景上；所以深色页面要传 false。
 *
 * @param isDark 当前页面是不是深色（底深、字浅）。
 */
@Composable
fun ApplySystemBarAppearance(isDark: Boolean) {
    val view = LocalView.current
    // 记录上一次真正写进窗口的值：SideEffect 每次重组都会跑一次，靠它在值没变时
    // 直接跳过，免得反复向系统写同一个 appearance。故意用普通对象而不是 state，
    // 写它不会引发额外重组。
    val lastApplied = remember { LastAppliedIsDark() }
    SideEffect {
        if (lastApplied.value == isDark) return@SideEffect
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        lastApplied.value = isDark
        AppLog.d("SystemBars", "系统栏图标切换为${if (isDark) "浅色" else "深色"}")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            // 导航栏（手势提示条 / 三键）同样压在 App 背景上，一起跟随
            isAppearanceLightNavigationBars = !isDark
        }
    }
}

/** [ApplySystemBarAppearance] 里存上次写入值的盒子，不参与快照。 */
private class LastAppliedIsDark {
    var value: Boolean? = null
}
