package com.hrsthrt74.afdian.feature.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.ManagementResult
import com.hrsthrt74.afdian.data.content.ManagementSection
import com.hrsthrt74.afdian.data.content.ManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val service = ManagementService(AuthTokenStore(application))
    private val _section = MutableStateFlow(ManagementSection.Overview)
    val section = _section.asStateFlow()
    private val _result = MutableStateFlow<ManagementResult?>(null)
    val result = _result.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { load(ManagementSection.Overview) }

    fun load(section: ManagementSection) {
        _section.value = section
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching { withContext(Dispatchers.IO) { service.load(section) } }
                .onSuccess { _result.value = it }
                .onFailure { _error.value = it.message ?: "管理数据加载失败" }
            _loading.value = false
        }
    }
}
