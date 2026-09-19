package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.stats.IncomeAmountMode
import com.hrsthrt74.afdian.data.stats.MonthlyBillSection
import com.hrsthrt74.afdian.data.stats.MonthlySummaryRepository
import com.hrsthrt74.afdian.data.stats.monthlyBillSections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「每月汇总」页的 ViewModel：拉取月度账单并按年分组，管理金额显示口径。
 *
 * 数据来自创作者后台 /api/my/income（一次返回自开通以来的全部月份）。
 * 金额口径（抽成前 / 抽成后）持久化在 DataStore，切换后由 flow 回流刷新 UI。
 */
class MonthlySummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MonthlySummaryRepository(AuthTokenStore(application))
    private val settingsStore = SettingsStore(application)

    /** 按年份分组的月度账单；空列表表示无数据（空态） */
    private val _sections = MutableStateFlow<List<MonthlyBillSection>>(emptyList())
    val sections: StateFlow<List<MonthlyBillSection>> = _sections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 直接把 DataStore 的 flow 转成 StateFlow，写入后自动回推 UI（与 SettingsViewModel 同款写法）
    val amountMode: StateFlow<IncomeAmountMode> = settingsStore.monthlySummaryAmountMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, IncomeAmountMode.default)

    init {
        load()
    }

    /** 首次加载 / 重试：重新拉取全量月度账单 */
    fun load() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val bills = repository.fetchMonthlyBills()
                _sections.value = monthlyBillSections(bills)
                AppLog.d(TAG, "load: ${bills.size} 个月，${_sections.value.size} 个年份分组")
            } catch (e: Exception) {
                // 网络异常转成错误状态，不能直接抛出去杀掉协程
                AppLog.e(TAG, "load failed", e)
                _error.value = e.message ?: "每月汇总加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 切换金额口径：只写 DataStore，UI 状态由 [amountMode] 回流更新 */
    fun setAmountMode(mode: IncomeAmountMode) {
        if (amountMode.value == mode) return
        AppLog.d(TAG, "切换金额口径：${mode.storageValue}")
        viewModelScope.launch { settingsStore.setMonthlySummaryAmountMode(mode) }
    }

    private companion object {
        const val TAG = "MonthlySummary"
    }
}
