package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.network.isConnectionNotEstablished
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「写请求能否安全换线路」的判定测试。
 *
 * 判定错一边都有代价：过宽会把已经送达的写请求再发一次（重复发消息/重复下单），
 * 过窄则让明明没连上的写操作白白失败。所以这里把允许与禁止的类型逐条钉死。
 */
class AfdianConnectFailureTest {

    @Test
    fun connectionNeverEstablishedIsRetryable() {
        // DNS 解析失败、拒绝连接、无路由：请求体必然没送到服务端
        assertTrue(UnknownHostException("dns failed").isConnectionNotEstablished())
        assertTrue(ConnectException("connection refused").isConnectionNotEstablished())
        assertTrue(NoRouteToHostException("no route to host").isConnectionNotEstablished())
    }

    @Test
    fun wrappedConnectFailureIsRetryable() {
        // OkHttp 常把底层失败包一层再抛，必须沿 cause 链判断
        assertTrue(IOException("connect failed", ConnectException("refused")).isConnectionNotEstablished())
    }

    @Test
    fun timeoutIsNotRetryable() {
        // 无法区分「连接超时」与「已发出但读超时」，后者重发即重复提交
        assertFalse(SocketTimeoutException("connect timed out").isConnectionNotEstablished())
        assertFalse(SocketTimeoutException("timeout").isConnectionNotEstablished())
    }

    @Test
    fun connectionResetIsNotRetryable() {
        // 连接已建立后被重置，服务端可能已经处理完请求
        assertFalse(SocketException("Connection reset").isConnectionNotEstablished())
    }

    @Test
    fun sslFailureIsNotRetryable() {
        // 握手失败多半是本地网络/代理问题，换域名同样失败，只会白等
        assertFalse(SSLHandshakeException("bad certificate").isConnectionNotEstablished())
        // 外层是 SSL 时一律否决，哪怕内层是连接类异常
        assertFalse(SSLException("tls", ConnectException("refused")).isConnectionNotEstablished())
    }

    @Test
    fun plainIoExceptionIsNotRetryable() {
        // 现有单测用裸 IOException 模拟网络失败：写请求不能因此换线
        assertFalse(IOException("simulated net failure").isConnectionNotEstablished())
    }

    @Test
    fun cyclicCauseChainDoesNotLoopForever() {
        // 畸形异常链（a -> b -> a 成环）不能把判定卡死；深度上限会把它判成「不可重试」
        val first = IOException("first")
        val second = IOException("second", first)
        first.initCause(second)

        assertFalse(first.isConnectionNotEstablished())
    }
}
