package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.OrderItem
import com.hrsthrt74.afdian.data.content.OrderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderViewModel(application: Application) : AndroidViewModel(application) {
    private val service = OrderService(AuthTokenStore(application))
    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()
    private val _orders = MutableStateFlow<List<OrderItem>>(emptyList())
    val orders = _orders.asStateFlow()
    private val _summary = MutableStateFlow("暂无订单统计")
    val summary = _summary.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { load("") }

    fun load(status: String) {
        _status.value = status
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            // 调试日志：记录筛选条件（status 为空串表示全部订单）
            AppLog.d("Orders", "load: status='${status.ifBlank { "all" }}'")
            runCatching { withContext(Dispatchers.IO) { service.load(status) } }
                .onSuccess {
                    _orders.value = it.orders; _summary.value = "${it.total} 笔 · 合计 ¥${it.amount}"
                    // 调试日志：筛选条件与结果统计（条数/总额）
                    AppLog.d("Orders", "load ok: status='${status.ifBlank { "all" }}', orders=${it.orders.size}, total=${it.total}")
                }
                .onFailure {
                    // 错误日志：用户模式下也能看到订单加载失败
                    AppLog.e("Orders", "load failed: status='${status.ifBlank { "all" }}'", it)
                    _error.value = it.message ?: "订单加载失败"
                }
            _loading.value = false
        }
    }
}
