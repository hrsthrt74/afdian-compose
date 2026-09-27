package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.settings.KeyColorPreset
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.settings.ThemeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)

    // 直接把 DataStore 的 flow 转成 StateFlow，写入后自动回推 UI
    val startupPage: StateFlow<Int> = store.startupPage
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val useAfdianColor: StateFlow<Boolean> = store.useAfdianColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 配色模式：0=跟随系统 1=浅色 2=深色 */
    val colorMode: StateFlow<Int> = store.colorMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Monet 种子色预设，默认跟随系统壁纸 */
    val keyColor: StateFlow<KeyColorPreset> = store.keyColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeyColorPreset.Default)

    /** 高级过渡动画总开关，默认开启 */
    val advancedTransition: StateFlow<Boolean> = store.advancedTransition
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 页面内边缘滑动返回手势，默认开启 */
    val gestureBack: StateFlow<Boolean> = store.gestureBack
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 系统预测性返回开关，默认开启。 */
    val predictiveBack: StateFlow<Boolean> = store.predictiveBack
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 调试日志总开关，默认关闭（关闭时 AppLog 只输出警告/错误） */
    val debugLogging: StateFlow<Boolean> = store.debugLogging
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 浏览历史记录开关，默认开启。 */
    val historyEnabled: StateFlow<Boolean> = store.historyEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 金额类数据卡显示「元」单位，默认关闭。 */
    val showIncomeUnit: StateFlow<Boolean> = store.showIncomeUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 人数/次数类数据卡显示「人」「次」单位，默认关闭。 */
    val showCountUnit: StateFlow<Boolean> = store.showCountUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDebugLogging(enabled: Boolean) {
        viewModelScope.launch { store.setDebugLogging(enabled) }
    }

    fun setHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setHistoryEnabled(enabled) }
    }

    fun setShowIncomeUnit(enabled: Boolean) {
        viewModelScope.launch { store.setShowIncomeUnit(enabled) }
    }

    fun setShowCountUnit(enabled: Boolean) {
        viewModelScope.launch { store.setShowCountUnit(enabled) }
    }

    /** 重置管理页卡片布局为默认顺序；成功/失败都给 Toast 反馈，失败额外记警告日志。 */
    fun resetManagementLayout() {
        viewModelScope.launch {
            runCatching { store.resetManagementLayout() }
                .onSuccess { Toast.makeText(getApplication(), "已重置管理页卡片布局", Toast.LENGTH_SHORT).show() }
                .onFailure { error ->
                    AppLog.w("Settings", "重置管理页卡片布局失败", error)
                    Toast.makeText(getApplication(), "重置失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
        }
    }

    /** 重置首页创作者数据卡片布局为默认顺序；反馈逻辑同 [resetManagementLayout]。 */
    fun resetHomeCreatorDataLayout() {
        viewModelScope.launch {
            runCatching { store.resetHomeCreatorDataLayout() }
                .onSuccess { Toast.makeText(getApplication(), "已重置首页创作者数据卡片布局", Toast.LENGTH_SHORT).show() }
                .onFailure { error ->
                    AppLog.w("Settings", "重置首页创作者数据卡片布局失败", error)
                    Toast.makeText(getApplication(), "重置失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun setAdvancedTransition(enabled: Boolean) {
        viewModelScope.launch { store.setAdvancedTransition(enabled) }
    }

    fun setGestureBack(enabled: Boolean) {
        viewModelScope.launch { store.setGestureBack(enabled) }
    }

    fun setPredictiveBack(enabled: Boolean) {
        AppLog.d("Settings", "预测性返回已${if (enabled) "开启" else "关闭"}")
        viewModelScope.launch { store.setPredictiveBack(enabled) }
    }

    fun setColorMode(mode: Int) {
        viewModelScope.launch { store.setColorMode(mode) }
    }

    /**
     * 写入 Monet 配色开关。
     *
     * 这里刻意只提供写入方、不提供读取的 StateFlow：开关的勾选态由全局主题的
     * `MiuixTheme.isDynamicColor` 驱动。因为本 VM 是进入设置页时才创建的，
     * 读 StateFlow 的初值会先按「关闭」渲染一帧，导致「使用爱发电主题色」闪一下。
     */
    fun setMonetEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setMonetEnabled(enabled) }
    }

    fun setKeyColor(preset: KeyColorPreset) {
        viewModelScope.launch { store.setKeyColor(preset) }
    }

    fun setStartupPage(index: Int) {
        viewModelScope.launch { store.setStartupPage(index) }
    }

    fun setUseAfdianColor(enabled: Boolean) {
        viewModelScope.launch { store.setUseAfdianColor(enabled) }
    }

    fun setEnableBlur(enabled: Boolean) {
        viewModelScope.launch { ThemeRepository.setEnableBlur(getApplication(), enabled) }
    }

    fun setProgressiveBlur(enabled: Boolean) {
        viewModelScope.launch { ThemeRepository.setProgressiveBlur(getApplication(), enabled) }
    }
}
