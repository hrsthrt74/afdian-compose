package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.ai.AiHistoryEntry
import com.hrsthrt74.afdian.data.ai.AiHistoryStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AI 记录页面与消息页入口卡片共享的状态入口。
 *
 * 只读为主：唯一的写操作是删除与清空。新增记录由 [com.hrsthrt74.afdian.ui.components.ai.AiResultSheet]
 * 在生成成功后直接写 [AiHistoryStore]，不经过这里 —— 那条路径没有 Compose 生命周期可依赖，
 * 走 ViewModel 反而会因为页面销毁而丢掉最后一次写入。
 */
class AiHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AiHistoryStore(application)

    // Eagerly + 真实首帧：消息页卡片要一进入就显示最近一条，不能先空一下再跳
    val entries: StateFlow<List<AiHistoryEntry>> = store.entries
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 删除单条记录（详情弹层里的「删除」）。 */
    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { store.delete(id) }
                .onSuccess { AppLog.d("AiHistory", "已删除 AI 记录: $id") }
                .onFailure { AppLog.w("AiHistory", "删除 AI 记录失败: $id", it) }
        }
    }

    /** 清空当前账号的全部 AI 记录（页面顶栏的「清空」）。 */
    fun clearAll() {
        viewModelScope.launch {
            runCatching { store.clearAll() }
                .onSuccess { AppLog.d("AiHistory", "已清空 AI 记录") }
                .onFailure { AppLog.w("AiHistory", "清空 AI 记录失败", it) }
        }
    }
}
