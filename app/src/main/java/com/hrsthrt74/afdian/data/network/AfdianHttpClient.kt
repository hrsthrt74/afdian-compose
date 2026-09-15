package com.hrsthrt74.afdian.data.network

import com.hrsthrt74.afdian.AppLog
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * 全应用共享的底层 OkHttpClient。
 *
 * OkHttpClient 自带连接池与线程池，重复构造会平白占用网络资源；数据层各服务
 * 默认复用它。仅在测试等需要隔离的场景才向服务显式传入自定义 client。
 */
val sharedAfdianHttpClient: OkHttpClient by lazy { OkHttpClient() }

/**
 * 爱发电网页接口的统一传输入口。
 *
 * 读取请求优先使用 ifdian.net；只有网络失败，或明确表示当前域名不可用的
 * HTTP 状态才回退到 afdian.com。写请求默认只执行一次，避免服务端已经成功
 * 后客户端因超时再次提交消息、点赞或优惠码操作。
 */
internal class AfdianHttpClient(
    private val client: OkHttpClient,
    private val warningLog: (String, String, Throwable?) -> Unit = { tag, message, error ->
        AppLog.w(tag, message, error)
    },
    private val errorLog: (String, String, Throwable?) -> Unit = { tag, message, error ->
        AppLog.e(tag, message, error)
    },
) {
    private companion object {
        const val TAG = "AfdianHttpClient"
        const val NET_HOST = "ifdian.net"
        const val COM_HOST = "afdian.com"
    }

    /**
     * 执行 API 请求。GET 默认允许域名回退，POST/其他方法默认禁止自动重试。
     * 调用方仍负责读取业务 ec 和关闭返回的 Response。
     */
    fun execute(request: Request, allowFallback: Boolean = request.method == "GET"): Response {
        val host = request.url.host
        if (host != NET_HOST && host != COM_HOST) {
            return client.newCall(request).execute()
        }

        val primaryRequest = request.forHost(NET_HOST)
        val fallbackRequest = request.forHost(COM_HOST)
        return try {
            val response = client.newCall(primaryRequest).execute()
            if (!allowFallback || response.isSuccessful || !isDomainFailure(response.code)) {
                response
            } else {
                warningLog(
                    TAG,
                    "${primaryRequest.url.encodedPath} -> ifdian.net HTTP ${response.code}，回退 afdian.com",
                    null,
                )
                response.close()
                executeFallback(fallbackRequest)
            }
        } catch (error: IOException) {
            if (!allowFallback) {
                warningLog(
                    TAG,
                    "${primaryRequest.url.encodedPath} -> ifdian.net 网络失败，写请求不自动重试：${error.javaClass.simpleName}",
                    error,
                )
                throw error
            }
            warningLog(
                TAG,
                "${primaryRequest.url.encodedPath} -> ifdian.net 网络失败，回退 afdian.com：${error.javaClass.simpleName}",
                error,
            )
            executeFallback(fallbackRequest)
        }
    }

    private fun executeFallback(request: Request): Response = try {
        client.newCall(request).execute()
    } catch (error: IOException) {
        errorLog(
            TAG,
            "${request.url.encodedPath} -> afdian.com 网络失败：${error.javaClass.simpleName}",
            error,
        )
        throw error
    }

    /** 只把域名不可用类状态视为回退条件，参数/权限/限流错误不重复请求。 */
    private fun isDomainFailure(code: Int): Boolean = code in setOf(404, 405, 408, 421, 500, 502, 503, 504)

    /** 替换请求 URL，并同步修正 Referer/Origin，避免回退后头部仍指向原域名。 */
    private fun Request.forHost(host: String): Request {
        val builder = newBuilder()
            .url(url.newBuilder().host(host).build())
        val referers = headers.values("Referer")
        val origins = headers.values("Origin")
        if (referers.isNotEmpty() || origins.isNotEmpty()) {
            builder.removeHeader("Referer")
            builder.removeHeader("Origin")
            referers.forEach { value ->
                val rewritten = value
                    .replace("https://$NET_HOST", "https://$host")
                    .replace("https://$COM_HOST", "https://$host")
                builder.addHeader("Referer", rewritten)
            }
            origins.forEach { value ->
                val rewritten = value
                    .replace("https://$NET_HOST", "https://$host")
                    .replace("https://$COM_HOST", "https://$host")
                builder.addHeader("Origin", rewritten)
            }
        }
        return builder.build()
    }
}
