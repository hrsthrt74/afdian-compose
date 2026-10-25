package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.ui.components.post.normalizeImageUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 图片地址归一化测试。
 *
 * 重点：站内相对路径要能跟随「优先线路」，而不是永远补成 afdian.com。
 * 默认值读进程级全局状态，所以这里一律显式传 host，保证用例之间互不影响。
 */
class PostImageUrlTest {

    @Test
    fun relativePathUsesGivenHost() {
        assertEquals("https://www.afdian.com/a.png", normalizeImageUrl("/a.png", "www.afdian.com"))
        assertEquals("https://ifdian.net/a.png", normalizeImageUrl("/a.png", "ifdian.net"))
    }

    @Test
    fun protocolRelativeUrlKeepsItsHost() {
        // 编辑器常见的 // 开头写法：只补协议，不该被线路域名覆盖
        assertEquals(
            "https://pic1.afdiancdn.com/x.png",
            normalizeImageUrl("//pic1.afdiancdn.com/x.png", "www.afdian.com"),
        )
    }

    @Test
    fun absoluteUrlIsUntouched() {
        assertEquals(
            "https://pic1.afdiancdn.com/x.png",
            normalizeImageUrl("https://pic1.afdiancdn.com/x.png", "www.afdian.com"),
        )
    }

    @Test
    fun httpUrlIsUpgradedToHttps() {
        assertEquals(
            "https://pic1.afdiancdn.com/x.png",
            normalizeImageUrl("http://pic1.afdiancdn.com/x.png", "www.afdian.com"),
        )
    }

    @Test
    fun blankAndWhitespaceAreNormalised() {
        assertEquals("", normalizeImageUrl("   ", "www.afdian.com"))
        // 前后空白要先 trim，否则相对路径判断会落到 else 分支
        assertEquals("https://afdian.com/a.png", normalizeImageUrl("  /a.png  ", "afdian.com"))
    }
}
