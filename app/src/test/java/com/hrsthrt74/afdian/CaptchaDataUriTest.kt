package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.auth.decodeCaptchaDataUri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 图形验证码 data URI 解析的单元测试。
 *
 * 服务端返回的是 `data:image/png;base64,xxxx`，解析失败会直接导致
 * 「发码按钮点不动」，所以这里把边界情况都钉住。
 */
class CaptchaDataUriTest {

    @Test
    fun `解析标准 data uri`() {
        // "hello" 的 base64
        val bytes = decodeCaptchaDataUri("data:image/png;base64,aGVsbG8=")
        assertArrayEquals("hello".toByteArray(), bytes)
    }

    @Test
    fun `没有 data 前缀时按纯 base64 处理`() {
        val bytes = decodeCaptchaDataUri("aGVsbG8=")
        assertArrayEquals("hello".toByteArray(), bytes)
    }

    @Test
    fun `空值与空串返回 null`() {
        assertNull(decodeCaptchaDataUri(null))
        assertNull(decodeCaptchaDataUri(""))
        assertNull(decodeCaptchaDataUri("data:image/png;base64,"))
    }

    @Test
    fun `非法 base64 返回 null 而不是抛异常`() {
        assertNull(decodeCaptchaDataUri("data:image/png;base64,这不是base64"))
    }
}
