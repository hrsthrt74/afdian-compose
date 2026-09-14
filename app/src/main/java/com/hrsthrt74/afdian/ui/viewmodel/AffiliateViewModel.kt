package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AfadianContentService
import com.hrsthrt74.afdian.data.content.Affiliate
import com.hrsthrt74.afdian.data.content.AffiliateForm
import com.hrsthrt74.afdian.data.content.AffiliateSelectablePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 优惠码页的 ViewModel：封装 creator/list-affiliate 的加载。
 *
 * 接口一次返回全部优惠码、无分页，因此只保留「加载中 / 错误」两种状态，
 * 下拉重试直接复用 [load]。
 */
class AffiliateViewModel(application: Application) : AndroidViewModel(application) {
    // 网络服务复用统一的内容服务，内部自带 Cookie auth_token
    private val service = AfadianContentService(AuthTokenStore(application))

    private val _affiliates = MutableStateFlow<List<Affiliate>>(emptyList())
    val affiliates = _affiliates.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    // 正在提交开关请求的优惠码集合：避免重复点击，并让对应开关短暂禁用
    private val _pendingAffiliateCodes = MutableStateFlow<Set<String>>(emptySet())
    val pendingAffiliateCodes = _pendingAffiliateCodes.asStateFlow()
    // 当前账号的 url_slug，用于拼接优惠码分享链接；拉取失败时为空
    private val _creatorSlug = MutableStateFlow("")
    val creatorSlug = _creatorSlug.asStateFlow()

    // 可选赞助方案 / 商品（创建优惠码的「适用方案」列表）
    private val _selectablePlans = MutableStateFlow<List<AffiliateSelectablePlan>>(emptyList())
    val selectablePlans = _selectablePlans.asStateFlow()
    private val _plansLoading = MutableStateFlow(false)
    val plansLoading = _plansLoading.asStateFlow()
    // 随机生成优惠码进行中
    private val _isGeneratingCode = MutableStateFlow(false)
    val isGeneratingCode = _isGeneratingCode.asStateFlow()
    // 创建优惠码提交中
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()
    // 编辑模式：正在编辑的优惠码（列表秒开后由详情覆盖）；null 表示创建模式
    private val _editingAffiliate = MutableStateFlow<Affiliate?>(null)
    val editingAffiliate = _editingAffiliate.asStateFlow()
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading = _detailLoading.asStateFlow()
    // 删除提交中
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()
    // 下拉刷新状态
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        load()
        loadCreatorSlug()
    }

    /** 首次加载 / 重试：重新拉取全部优惠码，网络异常转成错误态而非抛出 */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // 网络请求必须放在 IO 线程
                val list = withContext(Dispatchers.IO) { service.fetchAffiliates() }
                _affiliates.value = list
                AppLog.d("Affiliate", "优惠码加载成功，共 ${list.size} 条")
            } catch (e: Exception) {
                AppLog.e("Affiliate", "优惠码加载失败", e)
                _error.value = e.message ?: "优惠码加载失败"
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
                val list = withContext(Dispatchers.IO) { service.fetchAffiliates() }
                _affiliates.value = list
                _error.value = null
                AppLog.d("Affiliate", "优惠码刷新成功，共 ${list.size} 条")
            } catch (e: Exception) {
                AppLog.e("Affiliate", "优惠码刷新失败", e)
                _error.value = e.message ?: "刷新失败"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 读取当前账号的 url_slug（生成优惠码分享链接用）；失败不打扰主流程，链接入口置灰 */
    private fun loadCreatorSlug() {
        viewModelScope.launch {
            try {
                // 网络请求必须放在 IO 线程
                val slug = withContext(Dispatchers.IO) { service.fetchProfile().user.url_slug.orEmpty() }
                _creatorSlug.value = slug
                AppLog.d("Affiliate", "创作者 slug 加载成功：$slug")
            } catch (e: Exception) {
                AppLog.w("Affiliate", "创作者 slug 加载失败，生成链接不可用", e)
            }
        }
    }

    /**
     * 切换优惠码启用状态：先乐观更新列表让开关即时响应，请求失败再回滚。
     *
     * @param affiliateCode 目标优惠码
     * @param enable true 启用（switch=1）、false 禁用（switch=0）
     * @param onResult 结果回调（在主线程）：成功/失败与用于 Toast 的文案
     */
    fun switchAffiliate(
        affiliateCode: String,
        enable: Boolean,
        onResult: (success: Boolean, message: String) -> Unit,
    ) {
        // 空白码或同一码正在提交时忽略，避免重复请求
        if (affiliateCode.isBlank() || affiliateCode in _pendingAffiliateCodes.value) return
        val snapshot = _affiliates.value
        _pendingAffiliateCodes.value = _pendingAffiliateCodes.value + affiliateCode
        _affiliates.value = snapshot.map { affiliate ->
            if (affiliate.affiliate_code == affiliateCode) {
                affiliate.copy(switch = if (enable) 1 else 0)
            } else {
                affiliate
            }
        }
        viewModelScope.launch {
            try {
                // 网络请求必须放在 IO 线程
                withContext(Dispatchers.IO) { service.setAffiliateSwitch(affiliateCode, enable) }
                AppLog.d("Affiliate", "优惠码 $affiliateCode 已切换为${if (enable) "启用" else "禁用"}")
                onResult(true, "设置成功")
            } catch (e: Exception) {
                AppLog.e("Affiliate", "优惠码 $affiliateCode 切换失败", e)
                // 失败回滚到操作前的列表
                _affiliates.value = snapshot
                onResult(false, e.message ?: "设置失败")
            } finally {
                _pendingAffiliateCodes.value = _pendingAffiliateCodes.value - affiliateCode
            }
        }
    }

    /**
     * 拉取可选的赞助方案与商品（创建面板的「适用方案」多选列表）。
     * 已加载过且非强制刷新时跳过，避免重复请求。
     */
    fun loadSelectablePlans(force: Boolean = false) {
        if (_plansLoading.value) return
        if (!force && _selectablePlans.value.isNotEmpty()) return
        viewModelScope.launch {
            _plansLoading.value = true
            try {
                // 网络请求必须放在 IO 线程
                val plans = withContext(Dispatchers.IO) { service.fetchAffiliateSelectablePlans() }
                _selectablePlans.value = plans
                AppLog.d("Affiliate", "可选方案加载成功，共 ${plans.size} 条")
            } catch (e: Exception) {
                // 失败不打扰列表主流程，仅日志；创建面板会在为空时给提示
                AppLog.e("Affiliate", "可选方案加载失败", e)
            } finally {
                _plansLoading.value = false
            }
        }
    }

    /** 随机生成优惠码：成功回调返回新码；失败回传 null（调用方 Toast 提示） */
    fun generateCode(onResult: (String?) -> Unit) {
        if (_isGeneratingCode.value) return
        viewModelScope.launch {
            _isGeneratingCode.value = true
            try {
                val code = withContext(Dispatchers.IO) { service.generateAffiliateCode() }
                AppLog.d("Affiliate", "生成优惠码成功：$code")
                onResult(code.takeIf { it.isNotBlank() })
            } catch (e: Exception) {
                AppLog.e("Affiliate", "生成优惠码失败", e)
                onResult(null)
            } finally {
                _isGeneratingCode.value = false
            }
        }
    }

    /**
     * 创建优惠码：成功后自动刷新列表，并通过回调回传结果供页面 Toast。
     *
     * @param form 提交表单（id 为空串表示新建）
     * @param onResult 结果回调（在主线程）：成功/失败与提示文案
     */
    fun createAffiliate(form: AffiliateForm, onResult: (success: Boolean, message: String) -> Unit) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                // 网络请求必须放在 IO 线程
                withContext(Dispatchers.IO) { service.editAffiliate(form) }
                AppLog.d("Affiliate", "优惠码创建成功：${form.affiliateCode}")
                onResult(true, "创建成功")
                // 刷新列表把新码显示出来
                load()
            } catch (e: Exception) {
                AppLog.e("Affiliate", "优惠码创建失败", e)
                onResult(false, e.message ?: "创建失败")
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /**
     * 打开编辑：先用列表项秒开面板，再拉取详情覆盖限制项；详情失败沿用列表数据。
     * 详情接口不返回 plans，这里用列表项里的 plans 补上，编辑时原样回传 plan_ids。
     */
    fun loadAffiliateDetail(affiliateCode: String) {
        val listItem = _affiliates.value.firstOrNull { it.affiliate_code == affiliateCode } ?: return
        _editingAffiliate.value = listItem
        viewModelScope.launch {
            _detailLoading.value = true
            try {
                // 网络请求必须放在 IO 线程
                val detail = withContext(Dispatchers.IO) { service.fetchAffiliateDetail(affiliateCode) }
                // 仅在仍编辑同一个码时覆盖，避免与其它操作串台
                if (_editingAffiliate.value?.affiliate_code == affiliateCode) {
                    _editingAffiliate.value = detail.copy(plans = listItem.plans)
                }
                AppLog.d("Affiliate", "优惠码详情加载成功：$affiliateCode")
            } catch (e: Exception) {
                AppLog.w("Affiliate", "优惠码详情加载失败，沿用列表数据：$affiliateCode", e)
            } finally {
                _detailLoading.value = false
            }
        }
    }

    /** 关闭编辑态（编辑面板关闭时调用） */
    fun clearEditingAffiliate() {
        _editingAffiliate.value = null
    }

    /** 编辑保存：提交后刷新列表并退出编辑态 */
    fun updateAffiliate(form: AffiliateForm, onResult: (success: Boolean, message: String) -> Unit) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                // 网络请求必须放在 IO 线程
                withContext(Dispatchers.IO) { service.editAffiliate(form) }
                AppLog.d("Affiliate", "优惠码保存成功：${form.affiliateCode}")
                onResult(true, "保存成功")
                _editingAffiliate.value = null
                load()
            } catch (e: Exception) {
                AppLog.e("Affiliate", "优惠码保存失败：${form.affiliateCode}", e)
                onResult(false, e.message ?: "保存失败")
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /** 删除优惠码：成功后刷新列表并退出编辑态 */
    fun deleteAffiliate(affiliateCode: String, onResult: (success: Boolean, message: String) -> Unit) {
        val target = _affiliates.value.firstOrNull { it.affiliate_code == affiliateCode } ?: return
        if (_isDeleting.value) return
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                // 网络请求必须放在 IO 线程
                withContext(Dispatchers.IO) { service.deleteAffiliate(target.id) }
                AppLog.d("Affiliate", "优惠码删除成功：$affiliateCode")
                onResult(true, "删除成功")
                _editingAffiliate.value = null
                load()
            } catch (e: Exception) {
                AppLog.e("Affiliate", "优惠码删除失败：$affiliateCode", e)
                onResult(false, e.message ?: "删除失败")
            } finally {
                _isDeleting.value = false
            }
        }
    }
}
