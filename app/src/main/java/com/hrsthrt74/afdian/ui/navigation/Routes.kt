package com.hrsthrt74.afdian.ui.navigation

import com.hrsthrt74.afdian.data.content.AfadianUser
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {
    @Serializable data object MainTabs : Route

    @Serializable data object Home : Route
    @Serializable data object Discover : Route
    @Serializable data object Activity : Route
    @Serializable data object Messages : Route
    @Serializable data object Profile : Route

    @Serializable data class CreatorDetail(val user: AfadianUser) : Route

    @Serializable data object Favorites : Route
    @Serializable data object Sponsoring : Route
    @Serializable data object Orders : Route
    @Serializable data object Management : Route
    @Serializable data object Settings : Route
}
