package com.hrsthrt74.afdian.data.network

import com.hrsthrt74.afdian.AppLog
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
 * 域名顺序由设置页「优先线路」决定，三条有序链见 [AfdianRoute.chain]：
 * - 读请求（GET）：域名不可用类状态码，或任何网络异常，都换下一条线路；
 * - 写请求（非 GET）：只有「连接根本没建立」的异常才换下一条 —— 此时请求体必然没送达；
 *   超时、连接重置、TLS 失败一律原样抛出，因为服务端可能已经处理过，
 *   重发就是重复发消息、重复下单。
 *
 * 每次请求开头读一次当前线路，所以切换设置后下一次请求立即生效；
 * 飞行中的请求会沿旧链走完，不会中途换线（避免同一次业务请求打到两个站点）。
 */
internal class AfdianHttpClient(
    private val client: OkHttpClient,
    private val warningLog: (String, String, Throwable?) -> Unit = { tag, message, error ->
        AppLog.w(tag, message, error)
    },
    private val errorLog: (String, String, Throwable?) -> Unit = { tag, message, error ->
        AppLog.e(tag, message, error)
    },
    /** 当前优先线路。默认读进程级全局值；单测注入固定档位，避免靠改全局状态互相干扰。 */
    private val route: () -> AfdianRoute = { AfdianRouteState.preferred },
) {
    private companion object {
        const val TAG = "AfdianHttpClient"
    }

    /**
     * 执行 API 请求。调用方仍负责读取业务 ec 并关闭返回的 Response。
     *
     * 只接管爱发电的三个域名；CDN、GitHub 等请求原样放行，不改 URL、不改头部、不重试。
     */
    fun execute(request: Request): Response {
        if (!AfdianRoute.isAfdianHost(request.url.host)) {
            return client.newCall(request).execute()
        }

        // 整次请求只读一次线路：中途改档也不能让同一次调用横跨两条链
        val preferred = route()
        val chain = preferred.chain
        // 只记路径：query 里可能带 auth_token，绝不能进日志
        val path = request.url.encodedPath
        // GET 视为幂等，允许失败换线；写请求的换线条件见下面的 catch
        val idempotent = request.method == "GET"

        for ((index, host) in chain.withIndex()) {
            val nextHost = chain.getOrNull(index + 1)
            try {
                val response = client.newCall(request.forHost(host)).execute()
                val domainDead = isDomainFailure(response.code)

                // 最后一跳 / 写请求 / 非域名故障：三种情况都是「这就是最终结果」
                if (nextHost == null || !idempotent || !domainDead) {
                    if (nextHost == null && idempotent && domainDead) {
                        // 全链走完仍不可用：前面每跳各一条 warning，这里补唯一一条 error
                        errorLog(
                            TAG,
                            "$path 全部 ${chain.size} 个域名均不可用（最后 $host HTTP ${response.code}）",
                            null,
                        )
                    }
                    return response
                }

                warningLog(TAG, "$path -> $host HTTP ${response.code}，改试 $nextHost", null)
                // 换线前必须关闭：否则这条连接会一直被占着，直到 GC 才释放
                response.close()
            } catch (error: IOException) {
                // 写请求只有「连接没建立」才安全换线：此时请求体必然没送到服务端
                val canSwitch = idempotent || error.isConnectionNotEstablished()

                if (nextHost == null) {
                    errorLog(
                        TAG,
                        "$path 全部 ${chain.size} 个域名均失败（最后 $host）：${error.javaClass.simpleName}",
                        error,
                    )
                    throw error
                }
                if (!canSwitch) {
                    warningLog(
                        TAG,
                        "$path -> $host 网络失败（${error.javaClass.simpleName}），" +
                            "写请求可能已送达，不换线路",
                        error,
                    )
                    throw error
                }
                warningLog(
                    TAG,
                    "$path -> $host 网络失败（${error.javaClass.simpleName}），改试 $nextHost",
                    error,
                )
            }
        }
        // chain 至少一项，循环内每条分支都已 return 或 throw，这里只为满足编译器
        error("优先线路 ${preferred.storageValue} 的域名链为空")
    }

    /** 只把域名不可用类状态视为换线条件：401/403/429 说明站点活着，换域名重发没有意义。 */
    private fun isDomainFailure(code: Int): Boolean = when (code) {
        404, 405, 408, 421, 500, 502, 503, 504 -> true
        else -> false
    }

    /**
     * 把请求改到指定域名，并同步修正 Referer / Origin，避免换线后头部仍指向原域名。
     *
     * 这里不再用字符串 `replace`：`https://www.afdian.com` 含有子串 `https://afdian.com`，
     * 先替换裸域会得到 `https://www.www.afdian.com`。解析后只换 host 与顺序无关、且天然幂等。
     */
    private fun Request.forHost(host: String): Request {
        val builder = newBuilder()
            .url(url.newBuilder().host(host).build())
        // OkHttp 的 header 名比较不区分大小写，所以 ShopService 里小写的 referer 也能被取到
        val referers = headers.values("Referer")
        val origins = headers.values("Origin")
        if (referers.isEmpty() && origins.isEmpty()) return builder.build()

        builder.removeHeader("Referer")
        builder.removeHeader("Origin")
        referers.forEach { builder.addHeader("Referer", it.rewriteAfdianHost(host)) }
        origins.forEach { builder.addHeader("Origin", it.rewriteAfdianHost(host)) }
        return builder.build()
    }
}

/**
 * 把 URL 文本里的爱发电域名换成 [host]，其余部分（路径、query、末尾斜杠的有无）原样保留。
 *
 * 非爱发电域名（例如外站 Referer）与畸形值都原样返回，
 * 与改动前「只替换两个固定字面量」的语义等价，但不会误伤 www 变体。
 */
private fun String.rewriteAfdianHost(host: String): String {
    val url = toHttpUrlOrNull() ?: return this
    if (!AfdianRoute.isAfdianHost(url.host)) return this
    val rewritten = url.newBuilder().host(host).build().toString()
    // HttpUrl 会给裸域名补尾斜杠：https://afdian.com -> https://afdian.com/。
    // AuthService 的 Referer 传的正是裸 HOST，Origin 规范也不允许带路径 ——
    // 原值没有斜杠就还原，免得服务端按来源字符串比对时因多一个「/」失配。
    return if (!endsWith("/") && rewritten.endsWith("/")) rewritten.dropLast(1) else rewritten
}

/**
 * 异常是否属于「连接根本没建立」——只有这类异常才允许写请求换域名。
 *
 * 沿 cause 链判断（最多 8 层）：OkHttp 会把底层失败包一层再抛（如 `IOException(cause = ConnectException)`），
 * 只看最外层会漏判。链上任何一层属于「禁止重试」类型就立即否决：
 * - `SocketTimeoutException`：无法区分「连接超时」与「已发出但读超时」，后者重发即重复提交；
 * - `SocketException`（refused 之外的重置类）、`SSLException`：可能发生在服务端已处理之后，
 *   且 TLS 失败多半是本地网络/代理问题，换域名同样失败、只会白等。
 *
 * 注意 `ConnectException` / `NoRouteToHostException` 本身是 `SocketException` 的子类，
 * 所以 `when` 里必须先判可重试类型、再判禁止类型，顺序不能反。
 */
internal fun Throwable.isConnectionNotEstablished(): Boolean {
    var node: Throwable? = this
    repeat(MAX_CAUSE_DEPTH) {
        val current = node ?: return false
        when (current) {
            is UnknownHostException, is ConnectException, is NoRouteToHostException -> return true
            is SocketTimeoutException, is SSLException, is SocketException -> return false
        }
        // 防御自引用 cause，避免构造异常链时死循环
        if (current.cause === current) return false
        node = current.cause
    }
    return false
}

/** cause 链的最大追溯深度；足够覆盖 OkHttp 的包装层数，又不会在异常链畸形时死循环。 */
private const val MAX_CAUSE_DEPTH = 8
