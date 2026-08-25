package com.hrsthrt74.afdian.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 只保存模糊相关设置。 */
data class ThemeSettings(
    val enableBlur: Boolean = true,
    val progressiveBlur: Boolean = true,
)

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

object ThemeRepository {
    private val enableBlurKey = booleanPreferencesKey("enable_blur")
    private val progressiveBlurKey = booleanPreferencesKey("progressive_blur")

    /** 主题层唯一订阅入口，页面通过 LocalThemeSettings 消费结果。 */
    fun settings(context: Context): Flow<ThemeSettings> = context.themeDataStore.data.map { preferences ->
        ThemeSettings(
            enableBlur = preferences[enableBlurKey] ?: true,
            progressiveBlur = preferences[progressiveBlurKey] ?: true,
        )
    }

    suspend fun setEnableBlur(context: Context, enabled: Boolean) {
        context.themeDataStore.edit { it[enableBlurKey] = enabled }
    }

    suspend fun setProgressiveBlur(context: Context, enabled: Boolean) {
        context.themeDataStore.edit { it[progressiveBlurKey] = enabled }
    }
}
