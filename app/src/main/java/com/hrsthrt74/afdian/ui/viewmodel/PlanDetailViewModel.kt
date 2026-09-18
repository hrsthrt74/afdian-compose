package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.CreatorPlanSkus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlanDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AfadianContentService(AuthTokenStore(application))
    private val _detail = MutableStateFlow<CreatorPlanSkus?>(null)
    val detail = _detail.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var currentPlanId: String? = null

    fun load(planId: String) {
        if (planId.isBlank()) {
            _error.value = "方案信息不完整"
            return
        }
        if (currentPlanId == planId && (_loading.value || _detail.value != null)) return
        currentPlanId = planId
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _detail.value = null
            AppLog.d("PlanDetail", "load: planId=$planId")
            runCatching { service.fetchPlanSkus(planId) }
                .onSuccess {
                    _detail.value = it
                    AppLog.d("PlanDetail", "load ok: planId=$planId, skuCount=${it.list.size}")
                }
                .onFailure {
                    _detail.value = null
                    _error.value = it.message ?: "商品详情加载失败"
                    AppLog.e("PlanDetail", "load failed: planId=$planId", it)
                }
            _loading.value = false
        }
    }

    fun retry() {
        currentPlanId?.let {
            currentPlanId = null
            load(it)
        }
    }
}
