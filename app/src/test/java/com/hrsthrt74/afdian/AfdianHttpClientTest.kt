package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 共享 HTTP 层测试：验证域名优先级、GET 回退和写请求不重复提交。 */
class AfdianHttpClientTest {
    @Test
    fun getPrefersNetAndFallsBackToComOnDomainFailure() {
        val hosts = CopyOnWriteArrayList<String>()
        val referers = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                hosts += chain.request().url.host
                referers += chain.request().header("Referer").orEmpty()
                response(chain, if (hosts.size == 1) 503 else 200)
            }
            .build()
        val request = Request.Builder()
            .url("https://afdian.com/api/test")
            .header("Referer", "https://afdian.com/edit/test")
            .get()
            .build()

        AfdianHttpClient(client, noOpLog, noOpLog).execute(request).use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(listOf("ifdian.net", "afdian.com"), hosts)
        assertEquals("https://ifdian.net/edit/test", referers[0])
        assertEquals("https://afdian.com/edit/test", referers[1])
    }

    @Test
    fun getFallsBackAfterNetworkFailure() {
        val hosts = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                hosts += chain.request().url.host
                if (hosts.size == 1) throw IOException("simulated net failure")
                response(chain, 200)
            }
            .build()
        val request = Request.Builder().url("https://afdian.com/api/test").get().build()

        AfdianHttpClient(client, noOpLog, noOpLog).execute(request).use { response ->
            assertTrue(response.isSuccessful)
        }

        assertEquals(listOf("ifdian.net", "afdian.com"), hosts)
    }

    @Test
    fun postDoesNotRetryAfterHttpFailure() {
        val hosts = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                hosts += chain.request().url.host
                response(chain, 503)
            }
            .build()
        val request = Request.Builder()
            .url("https://afdian.com/api/mutation")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        AfdianHttpClient(client, noOpLog, noOpLog).execute(request).use { response ->
            assertEquals(503, response.code)
        }

        assertEquals(listOf("ifdian.net"), hosts)
    }

    private fun response(chain: okhttp3.Interceptor.Chain, code: Int): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

    private companion object {
        val noOpLog: (String, String, Throwable?) -> Unit = { _, _, _ -> }
    }
}
