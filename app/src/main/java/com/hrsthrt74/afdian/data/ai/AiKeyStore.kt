package com.hrsthrt74.afdian.data.ai

import com.hrsthrt74.afdian.data.auth.TokenCipher

/**
 * AI API Key 的加解密入口。
 *
 * 刻意复用 [TokenCipher] 的实现（AndroidKeyStore AES/GCM），但用**独立别名**：
 * 与登录凭证分开一把钥匙，将来若要给 AI Key 换加密方案（如加用户态口令），
 * 不会牵连登录态；反之重装/清凭据影响的范围也各自独立。
 *
 * 密文格式与 TokenCipher 一致：Base64(iv + GCM 密文)，IV 随机。
 */
internal object AiKeyStore {

    /** KeyStore 密钥别名；改名等同丢弃所有已保存的 API Key，用户需重新填写。 */
    private const val KEY_ALIAS = "afdian_ai_api_key"

    private val cipher = TokenCipher(KEY_ALIAS)

    /** 加密明文 Key，返回可直接落盘 DataStore 的 Base64 密文。 */
    fun encrypt(plainKey: String): String = cipher.encrypt(plainKey)

    /** 解密密文；密钥失效（清凭据/换机恢复）返回 null，调用方按「未配置」处理。 */
    fun decrypt(cipherText: String): String? = cipher.decrypt(cipherText)
}
