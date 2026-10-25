package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.AfdianRoute
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享 HTTP 层测试：验证优先线路的三跳顺序、GET 回退、写请求的换线条件，
 * 以及换线时 Referer / Origin 的同步改写。
 *
 * 线路一律用 lambda 注入（不读写进程级全局值），这样用例之间互不影响、也不会因为执行顺序不同而漂移。
 */
class AfdianHttpClientTest {

    @Test
    fun getPrefersNetAndFallsBackToComOnDomainFailure() {
        val recorder = Recorder()
        val client = clientOf(recorder) { index, chain -> response(chain, if (index == 0) 503 else 200) }
        val request = Request.Builder()
            .url("https://afdian.com/api/test")
            .header("Referer", "https://afdian.com/edit/test")
            .get()
            .build()

        httpOf(client).execute(request).use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(listOf("ifdian.net", "afdian.com"), recorder.hosts)
        assertEquals("https://ifdian.net/edit/test", recorder.referers[0])
        assertEquals("https://afdian.com/edit/test", recorder.referers[1])
    }

    @Test
    fun getTriesThirdHostWhenFirstTwoFail() {
        val recorder = Recorder()
        val client = clientOf(recorder) { index, chain -> response(chain, if (index < 2) 503 else 200) }
        val request = Request.Builder()
            .url("https://afdian.com/api/test")
            .header("Referer", "https://afdian.com/edit/test")
            .get()
            .build()

        httpOf(client).execute(request).use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(listOf("ifdian.net", "afdian.com", "www.afdian.com"), recorder.hosts)
        // 第三跳的 Referer 必须跟着改成 www，否则服务端看到的来源与 Host 不一致
        assertEquals("https://www.afdian.com/edit/test", recorder.referers[2])
    }

    @Test
    fun getStopsAtFirstNonDomainFailure() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, chain -> response(chain, 401) }
        val request = Request.Builder().url("https://afdian.com/api/test").get().build()

        httpOf(client).execute(request).use { response ->
            assertEquals(401, response.code)
        }

        // 401 说明站点活着（只是没登录），换域名重发没有意义
        assertEquals(listOf("ifdian.net"), recorder.hosts)
    }

    @Test
    fun getReturnsLastResponseAndLogsOneErrorWhenAllHostsFail() {
        val recorder = Recorder()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val client = clientOf(recorder) { _, chain -> response(chain, 503) }
        val request = Request.Builder().url("https://afdian.com/api/test").get().build()

        httpOf(client, warnings = warnings, errors = errors).execute(request).use { response ->
            assertEquals(503, response.code)
        }

        assertEquals(3, recorder.hosts.size)
        // 两次跳转各一条 warning，全链失败补一条 error
        assertEquals(2, warnings.size)
        assertEquals(1, errors.size)
    }

    @Test
    fun getFallsBackAfterNetworkFailure() {
        val recorder = Recorder()
        val client = clientOf(recorder) { index, chain ->
            if (index == 0) throw IOException("simulated net failure")
            response(chain, 200)
        }
        val request = Request.Builder().url("https://afdian.com/api/test").get().build()

        httpOf(client).execute(request).use { response ->
            assertTrue(response.isSuccessful)
        }

        assertEquals(listOf("ifdian.net", "afdian.com"), recorder.hosts)
    }

    @Test
    fun routeAfdianComStartsAtCom() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, chain -> response(chain, 503) }
        val request = Request.Builder().url("https://ifdian.net/api/test").get().build()

        httpOf(client, route = AfdianRoute.AfdianCom).execute(request).use { response ->
            assertEquals(503, response.code)
        }

        assertEquals(listOf("afdian.com", "ifdian.net", "www.afdian.com"), recorder.hosts)
    }

    @Test
    fun routeAfdianWwwStartsAtWww() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, chain -> response(chain, 200) }
        val request = Request.Builder()
            .url("https://ifdian.net/api/test")
            .header("Referer", "https://ifdian.net/edit/test")
            .get()
            .build()

        httpOf(client, route = AfdianRoute.AfdianWww).execute(request).use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(listOf("www.afdian.com"), recorder.hosts)
        assertEquals("https://www.afdian.com/edit/test", recorder.referers[0])
    }

    @Test
    fun requestToWwwHostIsInterceptedToo() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, chain -> response(chain, 200) }
        val request = Request.Builder()
            .url("https://www.afdian.com/api/test")
            .header("Referer", "https://www.afdian.com/edit/test")
            .get()
            .build()

        httpOf(client).execute(request).use { response ->
            assertEquals(200, response.code)
        }

        // 改动前 www 开头的请求会被「不是裸域」直接放行，既不换线也不改 Referer
        assertEquals(listOf("ifdian.net"), recorder.hosts)
        assertEquals("https://ifdian.net/edit/test", recorder.referers[0])
    }

    @Test
    fun postDoesNotRetryAfterHttpFailure() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, chain -> response(chain, 503) }
        val request = postRequest()

        httpOf(client).execute(request).use { response ->
            assertEquals(503, response.code)
        }

        assertEquals(listOf("ifdian.net"), recorder.hosts)
    }

    @Test
    fun postRetriesNextHostOnConnectFailure() {
        val recorder = Recorder()
        val client = clientOf(recorder) { index, chain ->
            // 连接没建立 = 请求体必然没送达，此时换线是安全的
            if (index == 0) throw ConnectException("connection refused")
            response(chain, 200)
        }
        val request = postRequest()

        httpOf(client).execute(request).use { response ->
            assertTrue(response.isSuccessful)
        }

        assertEquals(2, recorder.hosts.size)
        assertEquals("afdian.com", recorder.hosts[1])
    }

    @Test
    fun postDoesNotRetryOnReadTimeout() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, _ -> throw SocketTimeoutException("timeout") }
        val request = postRequest()

        assertThrows(IOException::class.java) { httpOf(client).execute(request) }

        // 超时可能发生在服务端已处理之后，重发会重复提交
        assertEquals(listOf("ifdian.net"), recorder.hosts)
    }

    @Test
    fun postDoesNotRetryOnConnectionReset() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, _ -> throw SocketException("Connection reset") }
        val request = postRequest()

        assertThrows(IOException::class.java) { httpOf(client).execute(request) }

        assertEquals(listOf("ifdian.net"), recorder.hosts)
    }

    @Test
    fun bareOriginKeepsNoTrailingSlash() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, chain -> response(chain, 200) }
        val request = Request.Builder()
            .url("https://afdian.com/api/test")
            .header("Origin", "https://afdian.com")
            .get()
            .build()

        httpOf(client).execute(request).use { }

        // HttpUrl 会给裸域名补尾斜杠，这里必须还原成 https://ifdian.net
        assertEquals("https://ifdian.net", recorder.origins[0])
    }

    @Test
    fun externalRefererIsNotRewritten() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, chain -> response(chain, 503) }
        val request = Request.Builder()
            .url("https://afdian.com/api/test")
            .header("Referer", "https://example.com/page")
            .get()
            .build()

        httpOf(client).execute(request).use { }

        // 外站来源不该被改动，三跳都应保持原值
        assertEquals(List(3) { "https://example.com/page" }, recorder.referers)
    }

    @Test
    fun nonAfdianRequestBypassesChain() {
        val recorder = Recorder()
        val client = clientOf(recorder) { _, chain -> response(chain, 200) }
        val request = Request.Builder().url("https://api.github.com/repos/x/y").get().build()

        httpOf(client).execute(request).use { response ->
            assertEquals(200, response.code)
        }

        // 更新检查等非爱发电请求必须原样放行：不改 host、也不重试
        assertEquals(listOf("api.github.com"), recorder.hosts)
    }

    /** 模拟一次 POST（写请求）：用来验证「只有连接没建立才换线」。 */
    private fun postRequest(): Request = Request.Builder()
        .url("https://afdian.com/api/mutation")
        .post("{}".toRequestBody("application/json".toMediaType()))
        .build()

    /** 记录每次实际发出的 host 与相关头部，供断言使用。 */
    private class Recorder {
        val hosts = CopyOnWriteArrayList<String>()
        val referers = CopyOnWriteArrayList<String>()
        val origins = CopyOnWriteArrayList<String>()
    }

    /**
     * 构造一个「不发真实请求」的 client：拦截器先把本跳信息记进 [recorder]，
     * 再交给 [handler] 决定返回什么响应、或抛什么异常。
     */
    private fun clientOf(
        recorder: Recorder,
        handler: (index: Int, chain: Interceptor.Chain) -> Response,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            // 拦截器按跳次顺序同步执行，用已有条数当本次下标
            val index = recorder.hosts.size
            recorder.hosts += chain.request().url.host
            recorder.referers += chain.request().header("Referer").orEmpty()
            recorder.origins += chain.request().header("Origin").orEmpty()
            handler(index, chain)
        }
        .build()

    /**
     * 构造被测对象。[warnings] / [errors] 传 null 表示不关心日志。
     * 线路通过 lambda 注入，不触碰进程级全局状态。
     */
    private fun httpOf(
        client: OkHttpClient,
        route: AfdianRoute = AfdianRoute.IfdianNet,
        warnings: MutableList<String>? = null,
        errors: MutableList<String>? = null,
    ): AfdianHttpClient = AfdianHttpClient(
        client = client,
        warningLog = { _, message, _ -> warnings?.add(message) },
        errorLog = { _, message, _ -> errors?.add(message) },
        route = { route },
    )

    private fun response(chain: Interceptor.Chain, code: Int): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
}
