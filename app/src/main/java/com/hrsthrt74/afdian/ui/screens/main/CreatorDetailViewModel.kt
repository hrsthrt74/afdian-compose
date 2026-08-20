package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.CreatorDetail
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
            runCatching { withContext(Dispatchers.IO) { service.fetchCreatorDetail(user) } }
                .onSuccess { _detail.value = it }
                .onFailure { _error.value = it.message ?: "创作者主页加载失败" }
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
                _error.value = it.message ?: "收藏状态更新失败"
            }
            _isMarking.value = false
        }
    }
}