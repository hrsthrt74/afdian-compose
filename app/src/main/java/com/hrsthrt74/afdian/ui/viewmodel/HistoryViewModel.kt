package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.data.history.HistoryStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 浏览历史页面和「我的」页预览卡片共享的状态入口。 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val store = HistoryStore(application)

    val entries: StateFlow<List<HistoryEntry>> = store.entries
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun avatarModel(url: String?): Any? = store.avatarModel(url)

    fun clearAvatarCache() {
        viewModelScope.launch {
            runCatching { store.clearAvatarCache() }
                .onSuccess { AppLog.d("History", "设置页已清除历史记录头像缓存") }
                .onFailure { AppLog.w("History", "设置页清除历史记录头像缓存失败", it) }
        }
    }
}
