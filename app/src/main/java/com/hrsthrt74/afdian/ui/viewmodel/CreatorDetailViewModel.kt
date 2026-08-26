package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.CreatorDetail
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.data.history.HistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreatorDetailViewModel(
    application: Application,
    private val user: AfadianUser,
) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    private val historyStore = HistoryStore(application)
    private val _detail = MutableStateFlow<CreatorDetail?>(null)
    val detail = _detail.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isMarking = MutableStateFlow(false)
    val isMarking = _isMarking.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            // 详情页生命周期只记录一次入口；历史开关关闭时仓库会直接忽略写入。
            withContext(Dispatchers.IO) {
                historyStore.recordOpened(HistoryEntry.from(user).target)
            }
            // 调试日志：记录加载起点（创作者 user_id / slug）
            AppLog.d("CreatorDetail", "load: userId=${user.user_id}, slug=${user.url_slug}")
            runCatching { withContext(Dispatchers.IO) { service.fetchCreatorDetail(user) } }
                .onSuccess {
                    _detail.value = it
                    // 网络资料更完整时只更新快照，不改变用户真正进入页面的时间。
                    viewModelScope.launch(Dispatchers.IO) {
                        historyStore.updateSnapshot(HistoryEntry.from(it.profile).target)
                    }
                    // 调试日志：各板块条数统计，便于确认数据完整性
                    AppLog.d(
                        "CreatorDetail",
                        "load ok: userId=${user.user_id}, goals=${it.goals.size}, plans=${it.plans.size}, " +
                            "albums=${it.albums.size}, posts=${it.posts.size}, products=${it.products.size}, marked=${it.isMarked}"
                    )
                }
                .onFailure {
                    // 错误日志：用户模式下也能看到创作者主页加载失败
                    AppLog.e("CreatorDetail", "load failed: userId=${user.user_id}", it)
                    _error.value = it.message ?: "创作者主页加载失败"
                }
            _loading.value = false
        }
    }

    fun toggleMark() {
        val current = _detail.value ?: return
        viewModelScope.launch {
            _isMarking.value = true
            _error.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    if (current.isMarked) service.unmarkCreator(current.profile.user_id)
                    else service.markCreator(current.profile.user_id)
                }
            }.onSuccess {
                _detail.value = current.copy(isMarked = !current.isMarked)
            }.onFailure {
                // 错误日志：收藏/取消收藏请求失败时用户模式下也可追踪
                AppLog.e("CreatorDetail", "toggleMark failed: userId=${current.profile.user_id}", it)
                _error.value = it.message ?: "收藏状态更新失败"
            }
            _isMarking.value = false
        }
    }
}
