package com.hrsthrt74.afdian.data.auth

import android.app.Application
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AuthSession(application: Application) : AndroidViewModel(application) {
    private val store = AuthTokenStore(application)
    private val _authenticated = MutableStateFlow(!store.token.isNullOrBlank())
    val authenticated = _authenticated.asStateFlow()
    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun login(account: String, password: String) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _error.value = null
            try {
                val token = withContext(Dispatchers.IO) { AuthService().passwordLogin(account, password) }
                if (token.isNullOrBlank()) error("登录失败，服务器未返回登录凭证")
                store.token = token
                _authenticated.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "登录失败，请检查网络和账号信息"
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    fun logout() {
        store.clear()
        _authenticated.value = false
    }
}

class AuthTokenStore(private val application: Application) {
    private val preferences = application.getSharedPreferences("auth", 0)
    var token: String?
        get() = preferences.getString("auth_token", null)?.let(::decrypt)
        set(value) = preferences.edit().putString("auth_token", value?.let(::encrypt)).apply()

    fun clear() = preferences.edit().remove("auth_token").apply()

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
    }.getOrNull()

    private companion object { const val KEY_ALIAS = "afdian_auth_token" }
}
