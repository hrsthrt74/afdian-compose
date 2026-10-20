package com.hrsthrt74.afdian.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hrsthrt74.afdian.AppLog
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 本地凭证的对称加解密。
 *
 * - 密钥存放在 AndroidKeyStore，alias 固定为 [DEFAULT_KEY_ALIAS]；
 *   多账号共用一个密钥、每个 token 各自加密，密文格式与旧版本完全一致，
 *   所以升级后老用户的 `auth_token` 仍然可解，不会被迫重新登录。
 * - 密文格式：[Base64]（`iv(12B) + AES/GCM 密文`），IV 随机、不额外存长度字段。
 *
 * 说明：这里刻意不做「按账号派生密钥」——KeyStore 生成的是设备级密钥，
 * 用同一把密钥加密多份 token 已经足够（攻击者要拿到的是 KeyStore 本身）。
 */
internal class TokenCipher(private val keyAlias: String = DEFAULT_KEY_ALIAS) {

    /**
     * 取（必要时创建）KeyStore 里的 AES 密钥。
     *
     * 加锁是必要的：首次加密可能有多条协程并发进入，
     * 不加锁会生成两把不同密钥，导致后写入的密文用前一把解不开。
     */
    @Synchronized
    private fun key(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return if (keyStore.containsAlias(keyAlias)) {
            (keyStore.getEntry(keyAlias, null) as java.security.KeyStore.SecretKeyEntry).secretKey
        } else {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }.generateKey()
        }
    }

    /** 加密明文；返回可直接落盘的 Base64 文本。 */
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /**
     * 解密；失败返回 null。
     *
     * 失败通常意味着 KeyStore 密钥失效（清除凭据、换机恢复），
     * 静默返回 null 会让「明明有账号却被当成未登录」难以排查，所以这里留一条 W 日志。
     */
    fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE)))
        }
        String(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)), StandardCharsets.UTF_8)
    }.onFailure {
        AppLog.w("TokenCipher", "本地凭证解密失败: ${it.message}")
    }.getOrNull()

    companion object {
        /**
         * 与旧版本一致的 KeyStore 别名。
         * 改这个值会导致所有已保存的凭证解不开，等同于强制全量重新登录。
         */
        const val DEFAULT_KEY_ALIAS = "afdian_auth_token"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** GCM 推荐 12 字节 IV，与旧实现的 `copyOfRange(0, 12)` 对应 */
        private const val IV_SIZE = 12
    }
}
