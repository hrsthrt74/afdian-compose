package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.settings.ThemeRepository
import androidx.lifecycle.viewModelScope
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 配色模式：0=跟随系统 1=浅色 2=深色 */
    val colorMode: StateFlow<Int> = store.colorMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 高级过渡动画总开关，默认开启 */
    val advancedTransition: StateFlow<Boolean> = store.advancedTransition
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 页面内边缘滑动返回手势，默认开启 */
    val gestureBack: StateFlow<Boolean> = store.gestureBack
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 调试日志总开关，默认关闭（关闭时 AppLog 只输出警告/错误） */
    val debugLogging: StateFlow<Boolean> = store.debugLogging
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDebugLogging(enabled: Boolean) {
        viewModelScope.launch { store.setDebugLogging(enabled) }
    }

    fun setAdvancedTransition(enabled: Boolean) {
        viewModelScope.launch { store.setAdvancedTransition(enabled) }
    }

    fun setGestureBack(enabled: Boolean) {
        viewModelScope.launch { store.setGestureBack(enabled) }
    }

    fun setColorMode(mode: Int) {
        viewModelScope.launch { store.setColorMode(mode) }
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
