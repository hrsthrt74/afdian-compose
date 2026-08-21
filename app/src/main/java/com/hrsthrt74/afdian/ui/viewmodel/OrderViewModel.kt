package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
            runCatching { withContext(Dispatchers.IO) { service.load(status) } }
                .onSuccess { _orders.value = it.orders; _summary.value = "${it.total} 笔 · 合计 ¥${it.amount}" }
                .onFailure { _error.value = it.message ?: "订单加载失败" }
            _loading.value = false
        }
    }
}
