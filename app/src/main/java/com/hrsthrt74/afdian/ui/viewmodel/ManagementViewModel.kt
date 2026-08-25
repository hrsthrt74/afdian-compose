package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
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
            // 调试日志：记录请求的管理板块
            AppLog.d("Management", "load: section=${section.name}")
            runCatching { withContext(Dispatchers.IO) { service.load(section) } }
                .onSuccess {
                    _result.value = it
                    // 调试日志：板块与数据行数统计
                    AppLog.d("Management", "load ok: section=${section.name}, rows=${it.rows.size}")
                }
                .onFailure {
                    // 错误日志：用户模式下也能看到管理数据加载失败
                    AppLog.e("Management", "load failed: section=${section.name}", it)
                    _error.value = it.message ?: "管理数据加载失败"
                }
            _loading.value = false
        }
    }
}
