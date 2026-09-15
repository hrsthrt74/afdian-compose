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

    fun login(account: String, password: String) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _error.value = null
            // 只记账号长度不记账号内容（手机号/邮箱属于敏感信息）
            AppLog.d("AuthSession", "开始登录 account_len=${account.length}")
            try {
                val token = withContext(Dispatchers.IO) { AuthService().passwordLogin(account, password) }
                if (token.isNullOrBlank()) throw IllegalStateException("登录失败，服务器未返回登录凭证")
                withContext(Dispatchers.IO) { store.writeToken(token) }
                // 登录成功：token 已加密落盘，状态切换为 LoggedIn
                AppLog.d("AuthSession", "登录成功，凭证已保存")
                _state.value = AuthState.LoggedIn(token)
            } catch (e: Exception) {
                // 登录失败走 E 级：无论调试开关是否打开都能在 Logcat 看到
                AppLog.e("AuthSession", "登录失败: ${e.message}", e)
                _error.value = e.message ?: "登录失败，请检查网络和账号信息"
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clear() }
            // 登出动作本身打一条 D，方便对照「登出后仍收到已登录回调」这类怪问题
            AppLog.d("AuthSession", "已登出，本地凭证已清除")
            _state.value = AuthState.LoggedOut
        }
    }
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
