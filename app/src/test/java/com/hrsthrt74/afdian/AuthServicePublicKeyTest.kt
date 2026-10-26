package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.auth.AFDIAN_AES_IV
import com.hrsthrt74.afdian.data.auth.AFDIAN_RSA_PUBLIC_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 登录加密参数的回归测试。
 *
 * ## 为什么需要它
 *
 * 爱发电服务端会轮换网页端使用的 RSA 公钥。公钥一旦对不上，服务端解不出
 * `ar_ept` 里的 AES 密钥，就拿不到账号密码 —— 这时它把请求当成可疑流量，
 * 返回 `ec=400 操作频繁，请稍后再试`。
 *
 * 这个错报把「钥匙过期」伪装成「你操作太频繁」，排查成本极高
 * （实测确认：用旧公钥发一个非法 JSON 的 body，也一样返回「操作频繁」）。
 *
 * 所以这里把公钥指纹钉死：服务端轮换后本测试立刻失败，
 * 提示需要更新 [AFDIAN_RSA_PUBLIC_KEY]，而不是让线上登录静默失效。
 */
class AuthServicePublicKeyTest {

    /**
     * 当前网页端公钥的 SHA-256 指纹。
     *
     * 更新公钥时**必须同步更新这个值** —— 更新后请用同样的方法重算：
     * `sha256(公钥 base64 去掉换行与空格)` 的十六进制小写。
     */
    private val expectedFingerprint = "d5a1bbaa9366195facdf3097e2e02a1eb019a8d2a8c0ca4a7a3aee57464e8249"

    @Test
    fun `公钥指纹未变`() {
        val actual = sha256Hex(AFDIAN_RSA_PUBLIC_KEY)
        assertEquals(
            "爱发电网页端的 RSA 公钥已更换，需要更新 AFDIAN_RSA_PUBLIC_KEY，" +
                "并同步更新本测试里的 expectedFingerprint。" +
                "新公钥的获取方式：抓 https://static.afdiancdn.com/js/app.<hash>.js，" +
                "搜 BEGIN PUBLIC KEY，取 base64 部分。",
            expectedFingerprint,
            actual,
        )
    }

    @Test
    fun `公钥是合法的 X509 RSA 公钥`() {
        // 服务端要求的是标准 X509 SubjectPublicKeyInfo 格式：
        // 用错了 Base64 变体或漏了头部，JVM 会抛异常
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(AFDIAN_RSA_PUBLIC_KEY)))
        assertEquals("RSA", key.algorithm)
        // 2048 位：模数 256 字节，密文 ar_ept 恒为 256 字节
        assertEquals(256, (key as java.security.interfaces.RSAPublicKey).modulus.bitLength() / 8)
    }

    @Test
    fun `公钥不含空白字符`() {
        // 常量里若混入换行或空格，Base64 解码会失败或解出错误的字节
        assertTrue("公钥里不能有空白字符", AFDIAN_RSA_PUBLIC_KEY.none { it.isWhitespace() })
    }

    @Test
    fun `AES 初始向量与网页端一致`() {
        // 网页端 app.js: WordArray.parse("7brVHncu7wIDAQAB")
        assertEquals("7brVHncu7wIDAQAB", String(AFDIAN_AES_IV))
        assertEquals(16, AFDIAN_AES_IV.size)
    }

    @Test
    fun `RSA 加密产物长度符合预期`() {
        // 2048 位公钥 + PKCS1 v1.5 → 密文恒为 256 字节 → base64 344 字符
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(AFDIAN_RSA_PUBLIC_KEY)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val keyText = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val encrypted = cipher.doFinal(keyText.toByteArray())
        assertEquals(256, encrypted.size)
        assertEquals(344, Base64.getEncoder().encodeToString(encrypted).length)
    }

    @Test
    fun `AES 加密与网页端模式一致`() {
        // 网页端: CryptoJS AES/CBC/Pkcs7, iv 固定, 输出 base64
        val keyText = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyText.toByteArray(), "AES"), IvParameterSpec(AFDIAN_AES_IV))
        }
        val encrypted = cipher.doFinal("test-account".toByteArray())
        // Pkcs7 填充：明文长度补齐到 16 的倍数
        assertEquals(0, encrypted.size % 16)
        assertEquals(16, encrypted.size)
    }

    /** 计算 SHA-256 指纹（十六进制小写），用于锁定公钥。 */
    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
