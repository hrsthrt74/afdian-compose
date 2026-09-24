package com.hrsthrt74.afdian.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.BuildConfig
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
        // legacy 低配版（SUPPORT_BLUR=false）在数据源层强制关闭全部模糊：
        // 所有模糊调用点的 backdrop 都来自 themeSettings.enableBlur，这里置 false 后
        // 顶栏/底栏/输入栏自动回退为不透明背景，且能覆盖未来新增的模糊调用点。
        val blurSupported = BuildConfig.SUPPORT_BLUR
        ThemeSettings(
            enableBlur = (preferences[enableBlurKey] ?: true) && blurSupported,
            progressiveBlur = (preferences[progressiveBlurKey] ?: true) && blurSupported,
        )
    }

    suspend fun setEnableBlur(context: Context, enabled: Boolean) {
        context.themeDataStore.edit { it[enableBlurKey] = enabled }
    }

    suspend fun setProgressiveBlur(context: Context, enabled: Boolean) {
        context.themeDataStore.edit { it[progressiveBlurKey] = enabled }
    }
}
