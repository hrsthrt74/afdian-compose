package com.hrsthrt74.afdian.data.auth

import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.hrsthrt74.afdian.AppLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Base64
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AuthService(private val client: OkHttpClient = sharedAfdianHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = AfdianHttpClient(client)

    suspend fun passwordLogin(account: String, password: String): String? {
        val aesKey = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val keyText = aesKey.joinToString("") { "%02x".format(it) }
        val body = json.encodeToString(LoginRequest(
            account = aesEncrypt(account.trim(), keyText),
            password = aesEncrypt(password, keyText),
            mp_token = "",
            ar_ept = rsaEncrypt(keyText),
        ))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("https://afdian.com/api/passport/login").post(body)
            .header("referer", "https://afdian.com").build()
        // 登录接口流程打点：开始 → HTTP 结果 → 业务码，方便定位「登录失败」卡在哪一环
        AppLog.d(TAG, "POST /api/passport/login 开始")
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                // HTTP 层就挂了（4xx/5xx），直接按失败返回
                AppLog.w(TAG, "登录请求 HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                return null
            }
            val result = json.decodeFromString<LoginResponse>(response.body.string())
            AppLog.d(TAG, "登录响应 HTTP ${response.code} ec=${result.ec} cost=${System.currentTimeMillis() - start}ms")
            if (result.ec != 200) error(result.message ?: "登录失败，请检查账号或密码")
            result.data?.authToken
        }
    }

    private fun aesEncrypt(value: String, keyText: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyText.toByteArray(), "AES"), IvParameterSpec("7brVHncu7wIDAQAB".toByteArray()))
        return Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun rsaEncrypt(value: String): String {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)))
        return Base64.encodeToString(Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, key) }.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private companion object {
        // 登录接口日志统一 tag
        const val TAG = "AuthService"
        val PUBLIC_KEY = """
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4Top/Mt2ofZeAIMh9AHw4d6Q+iyBxXbou+1mbhclLsB3YSMbFD+X6QnlAY1vMHO7fteKevn25iVIELBXsmcQS5/oA2hO3VHi9uTG3XmYVcrw94cK5ppODeBOV0hV0dFS/NOT66pqPAuLW6HgRrntgznl4ju6ttOddDNJ7e97RH9qrZEpzjl9GqVZQ2sFdmmw4dNET9fP9HWq8VlfW+BF G7TuxzEjZNcxAgrG/f41Z0+G3RxAccF8LOxu4Ztk1ZDdv5xukdx2ukoEhgdmKUkDv/W5r3HPj1uX+buzDi/UsumMblWXb0Bys7ENhZ/n4+naZ3b3rJ32DnTF7brVHncu7wIDAQAB
""".replace(" ", "").replace("\n", "")
    }
}

@Serializable private data class LoginRequest(val account: String, val password: String, val mp_token: String, val ar_ept: String)
@Serializable private data class LoginResponse(val ec: Int? = null, val em: String? = null, val msg: String? = null, val data: LoginData? = null) {
    val message: String? get() = em ?: msg
}
@Serializable private data class LoginData(val auth_token: String? = null) {
    val authToken: String get() = auth_token.orEmpty()
}
