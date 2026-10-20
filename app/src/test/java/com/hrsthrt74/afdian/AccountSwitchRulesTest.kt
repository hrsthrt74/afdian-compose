package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.auth.SessionEpoch
import com.hrsthrt74.afdian.data.auth.SwitchFailure
import com.hrsthrt74.afdian.data.auth.authTokenFingerprint
import com.hrsthrt74.afdian.data.auth.classifySwitchFailure
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 切换规则测试：失败分类、token 指纹、会话代次。
 *
 * 这三项决定了「断网时会不会误标账号失效」「当前账号判定是否可靠」
 * 「快速连切时旧结果会不会覆盖新会话」，都是纯逻辑，适合在 JVM 上锁住。
 */
class AccountSwitchRulesTest {

    @Test
    fun ioFailureIsNetworkFailure() {
        assertEquals(SwitchFailure.Network, classifySwitchFailure(IOException("connection reset")))
        assertEquals(SwitchFailure.Network, classifySwitchFailure(SocketTimeoutException("timeout")))
    }

    @Test
    fun businessFailureIsExpired() {
        // AccountService 对 HTTP 非 2xx 与业务错误统一抛 IllegalStateException
        assertEquals(SwitchFailure.Expired, classifySwitchFailure(IllegalStateException("请求失败 (401)")))
        assertEquals(SwitchFailure.Expired, classifySwitchFailure(RuntimeException("登录已失效")))
    }

    @Test
    fun fingerprintIsStableSixteenCharsAndTokenSpecific() {
        val a = authTokenFingerprint("token-a")
        val b = authTokenFingerprint("token-b")

        assertEquals(a, authTokenFingerprint("token-a"))
        assertEquals(16, a.length)
        assertNotEquals(a, b)
    }

    @Test
    fun sessionEpochIsMonotonic() {
        val before = SessionEpoch.current()
        val next = SessionEpoch.next()

        assertTrue(next > before)
        assertEquals(next, SessionEpoch.current())

        val after = SessionEpoch.next()
        assertTrue(after > next)
    }
}
