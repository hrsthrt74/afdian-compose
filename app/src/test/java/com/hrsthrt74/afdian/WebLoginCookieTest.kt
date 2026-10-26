package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.ui.screens.auth.parseAuthToken
import com.hrsthrt74.afdian.ui.screens.auth.parseAuthTokenFromUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 网页登录凭据解析的单元测试。
 *
 * 这段解析是「网页登录」能不能用的关键：解析不出来就拿不到 token，
 * 页面会一直停在 Sheet 里出不来。边界情况全部钉住。
 */
class WebLoginCookieTest {

    @Test
    fun `解析标准 cookie 头`() {
        assertEquals("abc123", parseAuthToken("auth_token=abc123"))
    }

    @Test
    fun `多个 cookie 中挑出 auth_token`() {
        val header = "other=1; auth_token=abc123; third=3"
        assertEquals("abc123", parseAuthToken(header))
    }

    @Test
    fun `auth_token 不在第一个位置也能找到`() {
        val header = "first=1; second=2; auth_token=xyz"
        assertEquals("xyz", parseAuthToken(header))
    }

    @Test
    fun `值里含等号时按第一个等号切分保留完整值`() {
        // token 可能是 Base64 变体，尾部带 = 填充，不能被截断
        assertEquals("aGVsbG8=", parseAuthToken("auth_token=aGVsbG8="))
        assertEquals("a=b=c", parseAuthToken("auth_token=a=b=c"))
    }

    @Test
    fun `键名两侧空白被忽略`() {
        assertEquals("abc", parseAuthToken("  auth_token = abc  ; x=1"))
    }

    @Test
    fun `没有 auth_token 时返回 null`() {
        assertNull(parseAuthToken("other=1; third=3"))
    }

    @Test
    fun `空值视为未登录`() {
        assertNull(parseAuthToken("auth_token="))
        assertNull(parseAuthToken("auth_token=   "))
    }

    @Test
    fun `null 与空串返回 null`() {
        assertNull(parseAuthToken(null))
        assertNull(parseAuthToken(""))
        assertNull(parseAuthToken("   "))
    }

    @Test
    fun `形如 auth_token_extra 的 cookie 不算命中`() {
        // 名字相近但不是同一个 cookie，不能误取
        assertNull(parseAuthToken("auth_token_extra=abc"))
        assertNull(parseAuthToken("xauth_token=abc"))
    }

    @Test
    fun `没有等号的片段被跳过`() {
        assertEquals("abc", parseAuthToken("garbage; auth_token=abc"))
    }

    // ── URL 的 atn 参数（跨站跳转携带凭据的路径）──

    @Test
    fun `解析 url 上的 atn 参数`() {
        assertEquals("abc123", parseAuthTokenFromUrl("https://ifdian.net/feed?atn=abc123"))
    }

    @Test
    fun `atn 参数在多个 query 参数中也能找到`() {
        val url = "https://ifdian.net/feed?origin_path=%2Ffeed&atn=abc123&other=1"
        assertEquals("abc123", parseAuthTokenFromUrl(url))
    }

    @Test
    fun `atn 参数值被 url 编码时正确解码`() {
        // token 里的 '+' 在 query 中会被编码成 %2B，解码后要还原
        assertEquals("a+b/c", parseAuthTokenFromUrl("https://ifdian.net/feed?atn=a%2Bb%2Fc"))
    }

    @Test
    fun `atn 参数值含等号时保留完整值`() {
        assertEquals("aGVsbG8=", parseAuthTokenFromUrl("https://ifdian.net/feed?atn=aGVsbG8%3D"))
    }

    @Test
    fun `hash 里的内容不作为凭据来源`() {
        // 网页端会把凭据从 hash 里清掉，hash 不作数
        assertNull(parseAuthTokenFromUrl("https://ifdian.net/feed#atn=abc123"))
    }

    @Test
    fun `没有 atn 参数时返回 null`() {
        assertNull(parseAuthTokenFromUrl("https://ifdian.net/feed"))
        assertNull(parseAuthTokenFromUrl("https://ifdian.net/feed?other=1"))
    }

    @Test
    fun `空的 atn 参数视为未登录`() {
        assertNull(parseAuthTokenFromUrl("https://ifdian.net/feed?atn="))
    }

    @Test
    fun `null 与空串的 url 返回 null`() {
        assertNull(parseAuthTokenFromUrl(null))
        assertNull(parseAuthTokenFromUrl(""))
    }
}
