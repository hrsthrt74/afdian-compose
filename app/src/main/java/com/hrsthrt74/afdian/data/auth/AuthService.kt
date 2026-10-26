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

/**
 * 发送登录验证码的结果。
 *
 * 爱发电在「需要人机校验」时不会直接发码，而是返回一张图形验证码（ec=201），
 * 要求带上 captcha_code 再发一次。因此这里必须把两种情况分开建模，
 * 不能用 Boolean 含糊表达。
 */
sealed interface SendCodeResult {
    /** 验证码已发出（短信或邮件），客户端可以开始倒计时 */
    data object Sent : SendCodeResult

    /**
     * 服务端要求先填图形验证码。
     *
     * @param imageDataUri `data:image/png;base64,...` 形式的图片，直接来自接口
     * @param message 服务端提示语（通常是「填写图形验证码」）
     */
    data class CaptchaRequired(val imageDataUri: String?, val message: String?) : SendCodeResult
}

class AuthService(private val client: OkHttpClient = sharedAfdianHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = AfdianHttpClient(client)

    suspend fun passwordLogin(account: String, password: String): String? {
        val keyText = newAesKeyText()
        val payload = LoginRequest(
            account = aesEncrypt(account.trim(), keyText),
            password = aesEncrypt(password, keyText),
            mp_token = "",
            ar_ept = rsaEncrypt(keyText),
        )
        return requestToken("/api/passport/login", json.encodeToString(payload), "登录失败，请检查账号或密码")
    }

    /**
     * 发送手机/邮箱登录验证码。
     *
     * @param account 手机号或邮箱，明文传入，内部加密
     * @param captchaCode 图形验证码；首次发送传空串，服务端返回 201 后再带上重试
     */
    suspend fun sendLoginCode(account: String, captchaCode: String): SendCodeResult {
        val keyText = newAesKeyText()
        val payload = SendCodeRequest(
            account = aesEncrypt(account.trim(), keyText),
            captcha_code = captchaCode,
            ar_ept = rsaEncrypt(keyText),
        )
        val request = Request.Builder()
            .url("$HOST/api/passport/send-ql-code")
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("referer", HOST)
            .build()

        AppLog.d(TAG, "POST /api/passport/send-ql-code 开始 captcha=${captchaCode.isNotBlank()}")
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.w(TAG, "发码请求 HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("验证码发送失败（${response.code}）")
            }
            val result = json.decodeFromString<SendCodeResponse>(response.body.string())
            AppLog.d(
                TAG,
                "发码响应 HTTP ${response.code} ec=${result.ec} cost=${System.currentTimeMillis() - start}ms",
            )
            when (result.ec) {
                // 200 = 已发出
                200 -> SendCodeResult.Sent
                // 201 = 需要图形验证码（同时带回新图片）
                201 -> SendCodeResult.CaptchaRequired(
                    imageDataUri = result.data?.captchaImg,
                    message = result.message ?: "请输入图形验证码",
                )
                // 其余按业务错误抛出：图形码错误(501)、频繁发送、账号格式不对等
                else -> error(result.message ?: "验证码发送失败")
            }
        }
    }

    /**
     * 验证码登录。
     *
     * 请求字段与网页端一致：account / code 加密，mp_token 留空，ar_ept 传 RSA 加密后的 AES 密钥。
     */
    suspend fun codeLogin(account: String, code: String): String? {
        val keyText = newAesKeyText()
        val payload = QuickLoginRequest(
            account = aesEncrypt(account.trim(), keyText),
            code = aesEncrypt(code.trim(), keyText),
            mp_token = "",
            ar_ept = rsaEncrypt(keyText),
        )
        return requestToken("/api/passport/quick-login", json.encodeToString(payload), "验证码登录失败")
    }

    /** 统一的「POST + 取 data.auth_token」实现，密码登录与验证码登录共用。 */
    private fun requestToken(path: String, body: String, fallbackMessage: String): String? {
        val request = Request.Builder()
            .url("$HOST$path")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("referer", HOST)
            .build()
        AppLog.d(TAG, "POST $path 开始")
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                // HTTP 层就挂了（4xx/5xx），直接按失败返回
                AppLog.w(TAG, "$path HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                return null
            }
            val result = json.decodeFromString<LoginResponse>(response.body.string())
            AppLog.d(TAG, "$path 响应 HTTP ${response.code} ec=${result.ec} cost=${System.currentTimeMillis() - start}ms")
            if (result.ec != 200) error(result.message ?: fallbackMessage)
            result.data?.authToken
        }
    }

    /** 生成 16 字节随机 AES 密钥，并以 32 位 hex 文本作为真正的 AES-256 密钥。 */
    private fun newAesKeyText(): String =
        ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun aesEncrypt(value: String, keyText: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyText.toByteArray(), "AES"), IvParameterSpec(AES_IV))
        return Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun rsaEncrypt(value: String): String {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)))
        return Base64.encodeToString(Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, key) }.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private companion object {
        // 登录接口日志统一 tag
        const val TAG = "AuthService"
        const val HOST = "https://afdian.com"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val AES_IV = AFDIAN_AES_IV
        // 公钥与 IV 的说明见 [AFDIAN_RSA_PUBLIC_KEY] 的文档注释
        val PUBLIC_KEY = AFDIAN_RSA_PUBLIC_KEY
    }
}

@Serializable private data class LoginRequest(val account: String, val password: String, val mp_token: String, val ar_ept: String)
@Serializable private data class LoginResponse(val ec: Int? = null, val em: String? = null, val msg: String? = null, val data: LoginData? = null) {
    val message: String? get() = em ?: msg
}
@Serializable private data class LoginData(val auth_token: String? = null) {
    val authToken: String get() = auth_token.orEmpty()
}

/** 发送登录验证码的请求体；字段名与网页端严格一致（captcha_code 为下划线命名）。 */
@Serializable private data class SendCodeRequest(
    val account: String,
    val captcha_code: String,
    val ar_ept: String,
)

@Serializable private data class SendCodeResponse(
    val ec: Int? = null,
    val em: String? = null,
    val msg: String? = null,
    val data: SendCodeData? = null,
) {
    val message: String? get() = em ?: msg
}

@Serializable private data class SendCodeData(
    // 形如 data:image/png;base64,xxxx，可直接解码成图片
    val captcha_img: String? = null,
) {
    val captchaImg: String? get() = captcha_img
}

/** 验证码登录请求体；mp_token 保留字段，App 端固定传空串。 */
@Serializable private data class QuickLoginRequest(
    val account: String,
    val code: String,
    val mp_token: String,
    val ar_ept: String,
)
