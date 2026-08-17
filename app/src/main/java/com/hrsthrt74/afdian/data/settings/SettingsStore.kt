package com.hrsthrt74.afdian.data.settings

import android.content.Context

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var startupPage: Int
        get() = preferences.getInt(KEY_STARTUP_PAGE, 0)
        set(value) = preferences.edit().putInt(KEY_STARTUP_PAGE, value).apply()

    private companion object {
        const val KEY_STARTUP_PAGE = "startup_page"
    }
}
