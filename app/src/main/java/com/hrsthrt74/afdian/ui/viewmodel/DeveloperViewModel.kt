package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.DeveloperService
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 开发者页的整体加载状态（DESIGN.md：加载/错误显式建模，不用单个 Boolean 含糊表达）。
 *
 * @param loaded 是否至少成功完成过一次加载；用于区分「还在加载」与「加载失败」
 */
data class DeveloperLoadState(
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * 开发者页的状态（对应官网 /dashboard/dev）。
 *
 * 首屏并行拉取 user_id / Webhook URL / API Token 三份数据，它们互不依赖，
 * 任何一份失败都不影响其余两份（部分成功时页面照常展示已取到的内容）。
 */
class DeveloperViewModel(application: Application) : AndroidViewModel(application) {
    private val service = DeveloperService(AuthTokenStore(application), sharedAfdianHttpClient)

    // 首屏加载状态
    private val _loadState = MutableStateFlow(DeveloperLoadState())
    val loadState = _loadState.asStateFlow()

    /** 开发者 user_id（页面顶部展示，也是 API 签名与 webhook 回调里用到的那个 id）。 */
    private val _userId = MutableStateFlow("")
    val userId = _userId.asStateFlow()

    /** 已配置的 Webhook URL；空串表示还没配置过。 */
    private val _webhookUrl = MutableStateFlow("")
    val webhookUrl = _webhookUrl.asStateFlow()

    /** API Token；空串表示还没生成过。 */
    private val _token = MutableStateFlow("")
    val token = _token.asStateFlow()

    // 保存 Webhook 是否进行中（防连点）
    private val _savingWebhook = MutableStateFlow(false)
    val savingWebhook = _savingWebhook.asStateFlow()

    // 发送 Webhook 测试是否进行中（防连点）
    private val _testingWebhook = MutableStateFlow(false)
    val testingWebhook = _testingWebhook.asStateFlow()

    // 生成 Token 是否进行中（防连点）
    private val _creatingToken = MutableStateFlow(false)
    val creatingToken = _creatingToken.asStateFlow()

    // 各操作的成功/失败提示；非空时页面弹提示弹窗，关闭后由 consumeMessage 清空
    private val _message = MutableStateFlow<DeveloperMessage?>(null)
    val message = _message.asStateFlow()

    init {
        load()
    }

    /**
     * 首屏加载或失败重试。
     *
     * 三份数据并行请求，全部结束才算完成；单份失败只记录日志并置 error，
     * 不清空其它已取到的数据（避免一个非关键接口失败让整页空白）。
     */
    fun load() {
        if (_loadState.value.loaded && _loadState.value.error == null) return
        viewModelScope.launch {
            _loadState.value = _loadState.value.copy(error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    // 三份数据互不依赖，并行请求缩短首屏等待
                    val idDeferred = async { service.fetchUserId() }
                    val hookDeferred = async { service.fetchWebhookUrl() }
                    val tokenDeferred = async { service.fetchToken() }

                    // 每个 async 内部各自兜底：单个失败不应让其余结果被丢弃
                    val id = idDeferred.awaitCatching()
                    val hook = hookDeferred.awaitCatching()
                    val tk = tokenDeferred.awaitCatching()
                    Triple(id, hook, tk)
                }
            }.onSuccess { (id, hook, tk) ->
                id.onSuccess { _userId.value = it }
                hook.onSuccess { _webhookUrl.value = it }
                tk.onSuccess { _token.value = it }

                // 三份全失败才算首屏失败（此时页面没有任何可展示内容）
                val allFailed = id.isFailure && hook.isFailure && tk.isFailure
                _loadState.value = DeveloperLoadState(
                    loaded = !allFailed,
                    error = if (allFailed) (id.exceptionOrNull()?.message ?: "开发者信息加载失败") else null,
                )
                AppLog.d(TAG, "load: userId=${id.isSuccess} webhook=${hook.isSuccess} token=${tk.isSuccess}")
            }.onFailure { e ->
                if (e is CancellationException) throw e
                AppLog.e(TAG, "load failed", e)
                _loadState.value = DeveloperLoadState(loaded = false, error = e.message ?: "加载失败")
            }
        }
    }

    /**
     * 保存 Webhook URL。
     *
     * 成功后用服务端返回的提示文案弹提示；失败同样弹提示，URL 保持用户输入的原样不回滚。
     */
    fun saveWebhook(url: String) {
        if (_savingWebhook.value) return
        viewModelScope.launch {
            _savingWebhook.value = true
            try {
                val em = withContext(Dispatchers.IO) { service.updateWebhookUrl(url) }
                // 服务端确认保存，回读一次避免本地与服务端不一致
                val saved = withContext(Dispatchers.IO) { service.fetchWebhookUrl() }
                _webhookUrl.value = saved
                AppLog.d(TAG, "saveWebhook: 服务端返回「$em」，回读 url=$saved")
                _message.value = DeveloperMessage(em.ifBlank { "保存成功" }, error = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "saveWebhook failed", e)
                _message.value = DeveloperMessage(e.message ?: "保存失败", error = true)
            } finally {
                _savingWebhook.value = false
            }
        }
    }

    /**
     * 发送 Webhook 测试。
     *
     * 服务端会立即向目标 URL 发一条测试订单通知；这里只展示服务端返回的提示文案
     * （是否真的收到通知由用户的服务器自己决定，App 侧无从判断）。
     */
    fun testWebhook(url: String) {
        if (_testingWebhook.value) return
        viewModelScope.launch {
            _testingWebhook.value = true
            try {
                val em = withContext(Dispatchers.IO) { service.testWebhookUrl(url) }
                AppLog.d(TAG, "testWebhook: 服务端返回「$em」")
                _message.value = DeveloperMessage(em.ifBlank { "已发送测试请求" }, error = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "testWebhook failed", e)
                _message.value = DeveloperMessage(e.message ?: "发送测试失败", error = true)
            } finally {
                _testingWebhook.value = false
            }
        }
    }

    /**
     * 生成新的 API Token（会使旧 Token 立即失效，调用前必须让用户确认）。
     *
     * 成功后回读一次 token 展示，避免服务端返回体里没有 token 字段时页面显示为空。
     */
    fun createToken() {
        if (_creatingToken.value) return
        viewModelScope.launch {
            _creatingToken.value = true
            try {
                val em = withContext(Dispatchers.IO) { service.createToken() }
                val newToken = withContext(Dispatchers.IO) { service.fetchToken() }
                _token.value = newToken
                AppLog.d(TAG, "createToken: 服务端返回「$em」，新 token 长度=${newToken.length}")
                _message.value = DeveloperMessage(em.ifBlank { "已生成新的 Token" }, error = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "createToken failed", e)
                _message.value = DeveloperMessage(e.message ?: "生成 Token 失败", error = true)
            } finally {
                _creatingToken.value = false
            }
        }
    }

    /** 提示弹窗展示完毕（关闭）后清空，避免同一条提示重复弹出。 */
    fun consumeMessage() {
        _message.value = null
    }

    private companion object {
        const val TAG = "Developer"
    }
}

/**
 * 页面上要弹窗提示的一条操作结果。
 *
 * @param text 服务端返回的提示文案（em），失败时为错误摘要
 * @param error 是否为失败，用于决定弹窗标题与确认按钮是否变红
 */
data class DeveloperMessage(val text: String, val error: Boolean)

/**
 * 包装单个异步请求的结果。
 *
 * 首屏三份数据并行拉取时，某一份失败不应该让另外两份的成功结果被丢弃，
 * 所以这里只记录各自的异常，等全部回来再由调用方决定如何呈现。
 */
private suspend fun <T> kotlinx.coroutines.Deferred<T>.awaitCatching(): Result<T> = try {
    Result.success(await())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppLog.w("Developer", "单项请求失败: ${e.message}", e)
    Result.failure(e)
}