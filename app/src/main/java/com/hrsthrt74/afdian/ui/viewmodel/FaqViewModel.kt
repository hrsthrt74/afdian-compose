package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.FaqItem
import com.hrsthrt74.afdian.data.content.FaqService
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FAQ 设置页的 ViewModel：封装 /api/faq/list、edit-faq、delete-faq。
 *
 * 接口一次返回全部条目、无分页，因此只保留「加载中 / 错误」两种状态，
 * 下拉重试直接复用 [load]。新增与编辑走同一个接口，由条目 id 是否为 0 区分。
 */
class FaqViewModel(application: Application) : AndroidViewModel(application) {
    // 复用共享 OkHttpClient，内部自带 Cookie auth_token
    private val service = FaqService(AuthTokenStore(application), sharedAfdianHttpClient)

    private val _faqs = MutableStateFlow<List<FaqItem>>(emptyList())
    val faqs = _faqs.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    // 保存（新增 / 编辑）提交中
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()
    // 删除提交中
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()
    // 下拉刷新状态
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        load()
    }

    /** 首次加载 / 重试：重新拉取全部条目，网络异常转成错误态而非抛出 */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // 网络请求必须放在 IO 线程
                val list = withContext(Dispatchers.IO) { service.fetchFaqs() }
                _faqs.value = list
                AppLog.d(TAG, "自动回复加载成功，共 ${list.size} 条")
            } catch (e: Exception) {
                AppLog.e(TAG, "自动回复加载失败", e)
                _error.value = e.message ?: "自动回复加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 下拉刷新：刷新成功后才变成刷新成功态 */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 网络请求必须放在 IO 线程
                val list = withContext(Dispatchers.IO) { service.fetchFaqs() }
                _faqs.value = list
                _error.value = null
                AppLog.d(TAG, "自动回复刷新成功，共 ${list.size} 条")
            } catch (e: Exception) {
                AppLog.e(TAG, "自动回复刷新失败", e)
                _error.value = e.message ?: "刷新失败"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 保存一条 FAQ（新增 / 编辑共用）。
     *
     * @param id 条目 id，新增传 0（服务端视为不传 id，走新增）
     * @param onResult 结果回调（在主线程）：成功/失败与用于 Toast 的文案
     */
    fun saveFaq(id: Int, title: String, content: String, onResult: (success: Boolean, message: String) -> Unit) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                // 网络请求必须放在 IO 线程
                withContext(Dispatchers.IO) { service.editFaq(id, title, content) }
                AppLog.d(TAG, "自动回复保存成功：id=$id title=$title")
                onResult(true, "保存成功")
                // 刷新列表把最新内容显示出来
                load()
            } catch (e: Exception) {
                AppLog.e(TAG, "自动回复保存失败：id=$id", e)
                onResult(false, e.message ?: "保存失败")
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /**
     * 删除一条 FAQ。
     *
     * @param id 条目 id
     * @param onResult 结果回调（在主线程）：成功/失败与用于 Toast 的文案
     */
    fun deleteFaq(id: Int, onResult: (success: Boolean, message: String) -> Unit) {
        if (_isDeleting.value || id <= 0) return
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                // 网络请求必须放在 IO 线程
                withContext(Dispatchers.IO) { service.deleteFaq(id) }
                AppLog.d(TAG, "自动回复删除成功：id=$id")
                onResult(true, "删除成功")
                load()
            } catch (e: Exception) {
                AppLog.e(TAG, "自动回复删除失败：id=$id", e)
                onResult(false, e.message ?: "删除失败")
            } finally {
                _isDeleting.value = false
            }
        }
    }

    private companion object {
        const val TAG = "FaqViewModel"
    }
}
