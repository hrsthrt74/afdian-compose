package com.hrsthrt74.afdian.data.auth

import android.app.Application
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** 登录态三态：Loading 表示正在从 DataStore 读取 token，此期间不渲染任何界面。 */
sealed interface AuthState {
    data object Loading : AuthState
    data class LoggedIn(val token: String) : AuthState
    data object LoggedOut : AuthState
}

class AuthSession(application: Application) : AndroidViewModel(application) {
    private val store = AuthTokenStore(application)
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 验证码发送中：用于禁用「发送」按钮并显示进度
    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode: StateFlow<Boolean> = _isSendingCode.asStateFlow()

    // 重发倒计时（秒）；0 表示可以发送
    private val _codeCooldown = MutableStateFlow(0)
    val codeCooldown: StateFlow<Int> = _codeCooldown.asStateFlow()

    // 图形验证码图片（PNG 字节）。null 表示当前不需要人机校验
    private val _captchaBytes = MutableStateFlow<ByteArray?>(null)
    val captchaBytes: StateFlow<ByteArray?> = _captchaBytes.asStateFlow()

    // 倒计时协程：重复点击发送时先取消上一轮，避免出现两个计时器把数字跳乱
    private var cooldownJob: Job? = null

    init {
        // 启动时异步读取已保存的 token；读完才把状态置为 LoggedIn/LoggedOut，
        // 期间 UI 保持 Loading，避免登录页闪烁。
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { store.token }
            // 冷启动登录态判定结果：只记录「有没有 token」，绝不打印 token 本身
            AppLog.d("AuthSession", "启动读取登录态完成：${if (token.isNullOrBlank()) "未登录" else "已登录"}")
            _state.value = if (token.isNullOrBlank()) AuthState.LoggedOut else AuthState.LoggedIn(token)
        }
    }

    /** 密码登录。 */
    fun login(account: String, password: String) {
        // 只记账号长度不记账号内容（手机号/邮箱属于敏感信息）
        startLogin("密码登录", account) { AuthService().passwordLogin(account, password) }
    }

    /**
     * 验证码登录。
     *
     * @param account 手机号或邮箱
     * @param code 收到的短信/邮件验证码
     */
    fun loginWithCode(account: String, code: String) {
        startLogin("验证码登录", account) { AuthService().codeLogin(account, code) }
    }

    /**
     * 发送登录验证码。
     *
     * 首次调用 captchaCode 传空串；若服务端要求人机校验，会把图形验证码图片放进
     * [captchaBytes]，由界面展示后再调用一次并带上用户输入的图形码。
     */
    fun sendLoginCode(account: String, captchaCode: String) {
        // 已经在发送或还在倒计时里，直接忽略重复点击（按钮本身也会禁用，这里兜底）
        if (_isSendingCode.value || _codeCooldown.value > 0) return
        viewModelScope.launch {
            // 协程真正开始执行时再判一次，防止极短时间内两次点击都通过上面的同步检查
            if (_isSendingCode.value) return@launch
            _isSendingCode.value = true
            _error.value = null
            AppLog.d("AuthSession", "开始发送登录验证码 account_len=${account.length} captcha=${captchaCode.isNotBlank()}")
            try {
                val result = withContext(Dispatchers.IO) { AuthService().sendLoginCode(account, captchaCode) }
                when (result) {
                    SendCodeResult.Sent -> {
                        // 发码成功：收起图形验证码，开始 60 秒倒计时
                        _captchaBytes.value = null
                        AppLog.d("AuthSession", "登录验证码已发送")
                        startCooldown()
                    }

                    is SendCodeResult.CaptchaRequired -> {
                        // 需要人机校验：解码图片交给界面展示。
                        // 注意这里不算失败，所以不写 _error；只有「图形码填错了」才提示。
                        val imageBytes = withContext(Dispatchers.IO) {
                            decodeCaptchaDataUri(result.imageDataUri)
                        }
                        _captchaBytes.value = imageBytes
                        when {
                            // 服务端说要图形码却没给图，界面会一直停在「发不出去」的状态，必须明说
                            imageBytes == null -> _error.value = "图形验证码加载失败，请重试"
                            captchaCode.isNotBlank() -> _error.value = result.message ?: "图形验证码错误"
                        }
                        AppLog.d("AuthSession", "发码需要图形验证码，图片=${if (imageBytes == null) "缺失" else "已就绪"}")
                    }
                }
            } catch (e: Exception) {
                AppLog.e("AuthSession", "发送登录验证码失败: ${e.message}", e)
                _error.value = e.message ?: "验证码发送失败，请稍后重试"
            } finally {
                _isSendingCode.value = false
            }
        }
    }

    /**
     * 密码登录与验证码登录共用的收尾逻辑：拿 token → 加密落盘 → 切到已登录。
     *
     * @param label 仅用于日志区分登录方式
     */
    private fun startLogin(label: String, account: String, block: suspend () -> String?) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _error.value = null
            // 只记账号长度不记账号内容（手机号/邮箱属于敏感信息）
            AppLog.d("AuthSession", "开始$label account_len=${account.length}")
            try {
                val token = withContext(Dispatchers.IO) { block() }
                if (token.isNullOrBlank()) throw IllegalStateException("登录失败，服务器未返回登录凭证")
                withContext(Dispatchers.IO) { store.writeToken(token) }
                // 登录成功：token 已加密落盘，状态切换为 LoggedIn
                AppLog.d("AuthSession", "$label 成功，凭证已保存")
                _captchaBytes.value = null
                _state.value = AuthState.LoggedIn(token)
            } catch (e: Exception) {
                // 登录失败走 E 级：无论调试开关是否打开都能在 Logcat 看到
                AppLog.e("AuthSession", "$label 失败: ${e.message}", e)
                _error.value = e.message ?: "登录失败，请检查网络和账号信息"
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    /** 60 秒重发倒计时；页面退出不会中断，但协程随 ViewModel 销毁而取消。 */
    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in CODE_COOLDOWN_SECONDS downTo 1) {
                _codeCooldown.value = remaining
                delay(1_000)
            }
            _codeCooldown.value = 0
        }
    }

    /** 清空登录错误提示（切换登录方式时调用，避免红字残留）。 */
    fun clearError() { _error.value = null }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clear() }
            // 登录态的附属状态一并复位，避免下次打开登录页还残留倒计时或旧图形码
            cooldownJob?.cancel()
            _codeCooldown.value = 0
            _captchaBytes.value = null
            // 登出动作本身打一条 D，方便对照「登出后仍收到已登录回调」这类怪问题
            AppLog.d("AuthSession", "已登出，本地凭证已清除")
            _state.value = AuthState.LoggedOut
        }
    }

    private companion object {
        /** 重发验证码的冷却秒数；与网页端一致，避免短时间内反复请求被服务端限流 */
        const val CODE_COOLDOWN_SECONDS = 60
    }
}

/**
 * 把 `data:image/png;base64,xxxx` 形式的图形验证码拆成 PNG 字节。
 *
 * 单独抽成顶层函数便于单元测试：只取第一个逗号之后的内容，
 * 没有 `data:` 前缀时按纯 base64 处理。用 [java.util.Base64] 而不是
 * `android.util.Base64`，这样纯 JVM 单测里也能直接跑。
 *
 * @return 解码后的字节；入参为空或 base64 非法时返回 null
 */
internal fun decodeCaptchaDataUri(dataUri: String?): ByteArray? {
    val base64 = dataUri
        // 有逗号取逗号之后（data URI），没有逗号就当作纯 base64
        ?.let { if (it.contains(',')) it.substringAfter(',') else it }
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return runCatching { java.util.Base64.getDecoder().decode(base64) }.getOrNull()
}

/** 基于 DataStore 的加密 token 存取，替代旧的 SharedPreferences("auth")。 */
class AuthTokenStore(private val application: Application) {
    /** 同步读取（内部可能阻塞一次磁盘 IO，请勿在主线程首次调用） */
    val token: String?
        get() {
            if (!sharedLoaded) load()
            return sharedCachedToken
        }

    private fun load() {
        synchronized(cacheLock) {
            if (sharedLoaded) return
            sharedCachedToken = runBlocking {
                application.authDataStore.data.first()[TOKEN_KEY]?.let(::decrypt)
            }
            sharedLoaded = true
        }
    }

    suspend fun writeToken(value: String) {
        val encrypted = encrypt(value)
        application.authDataStore.edit { it[TOKEN_KEY] = encrypted }
        synchronized(cacheLock) {
            sharedCachedToken = value
            sharedLoaded = true
        }
    }

    suspend fun clear() {
        application.authDataStore.edit { it.remove(TOKEN_KEY) }
        synchronized(cacheLock) {
            sharedCachedToken = null
            sharedLoaded = true
        }
    }

    // 与旧 SP 中的键名一致
    private val TOKEN_KEY = stringPreferencesKey("auth_token")

    private fun key(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, null) as java.security.KeyStore.SecretKeyEntry).secretKey
        } else {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }.onFailure {
        // 解密失败通常意味着 KeyStore 密钥失效（如清除凭据/换机恢复），
        // 原实现静默返回 null 会导致「明明有 token 却被当成未登录」，这里必须留痕
        AppLog.w("AuthSession", "本地凭证解密失败，将按未登录处理: ${it.message}")
    }.getOrNull()

    private companion object {
        const val KEY_ALIAS = "afdian_auth_token"

        // 同一进程内所有 AuthTokenStore 实例共享镜像，避免不同 ViewModel 看到过期登录态。
        private val cacheLock = Any()
        @Volatile private var sharedCachedToken: String? = null
        @Volatile private var sharedLoaded = false
    }
}

/** 进程级单例的认证 DataStore */
private val Application.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")
