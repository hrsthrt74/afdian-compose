package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.network.AfdianRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 线路枚举的契约测试。
 *
 * 重点锁住两件事：
 * 1. 三条回退链的顺序必须与产品约定完全一致（这是唯一会被用户直接感知的规则）；
 * 2. `fromStorage` 的兜底值必须等于 `Default`，否则设置页首帧与实际生效值会打架。
 */
class AfdianRouteTest {

    @Test
    fun defaultIsIfdianNet() {
        assertEquals(AfdianRoute.IfdianNet, AfdianRoute.Default)
        assertEquals("ifdian.net", AfdianRoute.Default.host)
    }

    @Test
    fun missingStorageFallsBackToDefault() {
        assertEquals(AfdianRoute.Default, AfdianRoute.fromStorage(null))
        assertEquals(AfdianRoute.Default, AfdianRoute.fromStorage(""))
        assertEquals(AfdianRoute.Default, AfdianRoute.fromStorage("nightly"))
        assertEquals(AfdianRoute.Default, AfdianRoute.fromStorage("www."))
    }

    @Test
    fun storageValuesRoundTrip() {
        AfdianRoute.entries.forEach { route ->
            assertEquals(route, AfdianRoute.fromStorage(route.storageValue))
        }
    }

    @Test
    fun storageValuesAreUnique() {
        val values = AfdianRoute.entries.map { it.storageValue }
        assertEquals(values.size, values.distinct().size)
    }

    @Test
    fun chainsFollowTheProductRule() {
        // 默认档：ifdian 优先，www 最后
        assertEquals(
            listOf("ifdian.net", "afdian.com", "www.afdian.com"),
            AfdianRoute.IfdianNet.chain,
        )
        // 选 afdian.com：ifdian 顶上，www 仍是最后
        assertEquals(
            listOf("afdian.com", "ifdian.net", "www.afdian.com"),
            AfdianRoute.AfdianCom.chain,
        )
        // 选 www：之后是 ifdian，最后才是 afdian.com
        assertEquals(
            listOf("www.afdian.com", "ifdian.net", "afdian.com"),
            AfdianRoute.AfdianWww.chain,
        )
    }

    @Test
    fun everyChainStartsWithItsHostAndCoversAllThreeHosts() {
        val allHosts = setOf("ifdian.net", "afdian.com", "www.afdian.com")
        AfdianRoute.entries.forEach { route ->
            assertEquals(route.chain.first(), route.host)
            assertEquals(3, route.chain.size)
            assertEquals(allHosts, route.chain.toSet())
        }
    }

    @Test
    fun wwwIsAlwaysLastUnlessItIsPrimary() {
        assertEquals("www.afdian.com", AfdianRoute.IfdianNet.chain.last())
        assertEquals("www.afdian.com", AfdianRoute.AfdianCom.chain.last())
        assertEquals("afdian.com", AfdianRoute.AfdianWww.chain.last())
    }

    @Test
    fun afdianHostsAreRecognised() {
        assertTrue(AfdianRoute.isAfdianHost("ifdian.net"))
        assertTrue(AfdianRoute.isAfdianHost("afdian.com"))
        assertTrue(AfdianRoute.isAfdianHost("www.afdian.com"))
    }

    @Test
    fun foreignHostsAreRejected() {
        // 精确匹配，不能把相似域名也接管过来（否则会把外站请求改写到爱发电）
        assertFalse(AfdianRoute.isAfdianHost(""))
        assertFalse(AfdianRoute.isAfdianHost("github.com"))
        assertFalse(AfdianRoute.isAfdianHost("afdiancdn.com"))
        assertFalse(AfdianRoute.isAfdianHost("evil-ifdian.net"))
        assertFalse(AfdianRoute.isAfdianHost("www.afdian.com.evil.com"))
    }
}
