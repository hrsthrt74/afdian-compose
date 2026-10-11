package com.hrsthrt74.afdian.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** 主导航范围内所有网页链接共用的跳转入口。 */
val LocalAppLinkNavigator = staticCompositionLocalOf<AppLinkNavigator> {
    error("LocalAppLinkNavigator must be provided by MainScreen")
}
