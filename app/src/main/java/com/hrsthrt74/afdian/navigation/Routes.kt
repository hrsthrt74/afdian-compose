package com.hrsthrt74.afdian.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {
    @Serializable data object Home : Route
    @Serializable data object Discover : Route
    @Serializable data object Activity : Route
    @Serializable data object Messages : Route
    @Serializable data object Profile : Route
}
