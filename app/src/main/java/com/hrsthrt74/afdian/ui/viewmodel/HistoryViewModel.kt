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

    init {
        // 一次性回收旧版本预下载的历史头像缓存目录（新版本只存链接，不会再写这个目录）。
        // 放在这里而不是 HistoryStore 构造函数里：构造函数不该有磁盘副作用，
        // 而本 ViewModel 挂在 MainScreen 上、进程内只创建一次，正好是合适的迁移时机。
        viewModelScope.launch {
            runCatching { store.clearLegacyAvatarCache() }
                .onFailure { AppLog.w("History", "回收旧版历史头像缓存失败", it) }
        }
    }

    /** 清空所有浏览历史条目，并顺带回收旧版遗留的头像缓存目录。 */
    fun clearAll() {
        viewModelScope.launch {
            runCatching { store.clearAll() }
                .onSuccess { AppLog.d("History", "已清空浏览历史") }
                .onFailure { AppLog.w("History", "清空浏览历史失败", it) }
        }
    }
}
