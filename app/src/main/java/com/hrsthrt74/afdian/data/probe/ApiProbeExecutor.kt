package com.hrsthrt74.afdian.data.probe

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 单个接口探针的执行器。
 *
 * 设计原则：
 * - **不解析业务模型**：探针只负责把「接口原文」拿回来，解析交给摘要函数，
 *   因此不需要为每个接口写一份 @Serializable 模型。
 * - **不打乱 Repository 的翻页策略**：这里只发一次单页请求，绝不复用
 *   `CreatorStatsRepository` 的翻页聚合逻辑。
 * - **失败即数据**：任何异常都被兜成 [ApiProbeResult.Failure]，
 *   只有协程取消例外（必须原样抛出以遵循结构化并发），所以调用方不需要 try/catch。
 * - **凭据不外泄**：展示用 URL 与日志都经过 [maskSecrets]，永不打完整 token。
 *
 * 可见性设为 internal：UI 层只依赖 [com.hrsthrt74.afdian.ui.viewmodel.ApiProbeViewModel]，
 * 把 internal 收敛在数据层。
 */
internal class ApiProbeExecutor(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient = sharedAfdianHttpClient,
    /** 取当前时间戳。抽成函数是为了单元测试能注入假时钟，不影响生产行为。 */
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** 解析响应体用的宽松 Json（探针不关心未知字段，尽量别因为脏数据报错）。 */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /** 统一传输入口：GET 会自动做 ifdian.net -> afdian.com 的域名回退。 */
    private val http = AfdianHttpClient(client)

    private companion object {
        const val TAG = "ApiProbe"

        /** 探针默认打 afdian.com；若域名不可用，[AfdianHttpClient] 会在两个域名间挑可用的。 */
        const val BASE_HOST = "https://afdian.com"
    }

    /**
     * 运行一次探针。
     *
     * 整个函数体在 [Dispatchers.IO] 上执行：读 token 可能触发一次磁盘 IO，
     * 网络请求与响应体读取更必须离开主线程。
     */
    suspend fun run(spec: ApiProbeSpec): ApiProbeResult = withContext(Dispatchers.IO) {
        // token 首次读取内部可能阻塞，所以必须在 IO 线程；为空说明没登录，请求不用发
        val token = tokenStore.token
        if (token.isNullOrBlank()) {
            AppLog.d(TAG, "${spec.id} 跳过：本地没有 auth_token")
            return@withContext ApiProbeResult.Failure(
                kind = FailureKind.NotLoggedIn,
                message = "未登录，无法携带 auth_token 请求",
            )
        }

        val url = (BASE_HOST + spec.path).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", token)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            // 与 AccountService 的做法一致：query 与 Cookie 双通道携带，服务端两种都认
            .header("Cookie", "auth_token=$token")
            .build()

        val start = now()
        try {
            // Response 可能来自回退后的新请求，必须 use 关闭；body 只读一次后全部复用
            http.execute(request).use { response ->
                val bytes = response.body.bytes()
                val text = bytes.toString(Charsets.UTF_8)
                val elapsed = now() - start
                // 展示实际命中的域名与脱敏 URL：能一眼看出是否发生了域名回退
                val resolvedHost = response.request.url.host
                val shownUrl = response.request.url.toString().maskSecrets()

                // 解析失败不算致命：仍要把原文展示出来（下面按 Parse 分支处理）
                val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull()
                val root = parsed as? JsonObject
                val ec = root?.readInt("ec")
                val em = root?.readMessage()
                val highlights = buildHighlights(parsed, spec)
                val (prettyText, truncated) = prettyPrintAndCap(text, parsed)

                when {
                    // ① HTTP 层失败：附带响应体，方便看服务端返回的错误页
                    !response.isSuccessful -> {
                        AppLog.w(TAG, "${spec.id} -> HTTP ${response.code}（${elapsed}ms）")
                        ApiProbeResult.Failure(
                            kind = FailureKind.Http,
                            message = "HTTP ${response.code}",
                            httpCode = response.code,
                            businessCode = ec,
                            elapsedMs = elapsed,
                            resolvedHost = resolvedHost,
                            requestUrl = shownUrl,
                            rawJson = prettyText,
                            rawTruncated = truncated,
                            rawBytes = bytes.size.toLong(),
                            highlights = highlights,
                        )
                    }

                    // ② 业务层失败：ec 存在且不是 200。em 优先，没有就报原始业务码
                    ec != null && ec != 200 -> {
                        AppLog.w(TAG, "${spec.id} -> 业务失败 ec=$ec（${elapsed}ms）")
                        ApiProbeResult.Failure(
                            kind = FailureKind.Business,
                            message = em?.takeIf { it.isNotBlank() } ?: "业务码 ec=$ec",
                            httpCode = response.code,
                            businessCode = ec,
                            elapsedMs = elapsed,
                            resolvedHost = resolvedHost,
                            requestUrl = shownUrl,
                            rawJson = prettyText,
                            rawTruncated = truncated,
                            rawBytes = bytes.size.toLong(),
                            highlights = highlights,
                        )
                    }

                    // ③ 不是合法 JSON：原文照上屏，摘要区为空
                    parsed == null -> {
                        AppLog.w(TAG, "${spec.id} -> 响应不是合法 JSON（${elapsed}ms）")
                        ApiProbeResult.Failure(
                            kind = FailureKind.Parse,
                            message = "响应不是合法 JSON",
                            httpCode = response.code,
                            businessCode = ec,
                            elapsedMs = elapsed,
                            resolvedHost = resolvedHost,
                            requestUrl = shownUrl,
                            rawJson = prettyText,
                            rawTruncated = truncated,
                            rawBytes = bytes.size.toLong(),
                        )
                    }

                    // ④ 成功。注意 ec 缺失（旧接口）不算失败，与 requireAfdianSuccess 的语义一致
                    else -> {
                        // 只记请求标识、状态码与耗时；URL 里的 auth_token 已被脱敏，且这里根本不打 URL
                        AppLog.d(
                            TAG,
                            "${spec.id} -> HTTP ${response.code} ec=$ec" +
                                "（${elapsed}ms，${bytes.size}B，host=$resolvedHost）",
                        )
                        ApiProbeResult.Success(
                            httpCode = response.code,
                            businessCode = ec,
                            businessMessage = em,
                            elapsedMs = elapsed,
                            bodyBytes = bytes.size.toLong(),
                            resolvedHost = resolvedHost,
                            requestUrl = shownUrl,
                            rawJson = prettyText,
                            rawTruncated = truncated,
                            rawBytes = bytes.size.toLong(),
                            highlights = highlights,
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            // 取消必须原样抛出，否则会破坏结构化并发（页面退出时协程无法正常收敛）
            throw e
        } catch (e: Exception) {
            // 断网 / 超时 / DNS 失败等：兜成失败态，绝不让异常冒到 UI
            AppLog.w(TAG, "${spec.id} 探针网络异常", e)
            ApiProbeResult.Failure(
                kind = FailureKind.Network,
                message = e.message ?: "网络异常",
                elapsedMs = now() - start,
                requestUrl = request.url.toString().maskSecrets(),
            )
        }
    }

    /** 读整数型字段（业务码 ec）。非数字或缺失都返回 null。 */
    private fun JsonObject.readInt(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    /** 读业务错误描述：爱发电不同接口分别用 em / msg / message，按优先级取第一个非空的。 */
    private fun JsonObject.readMessage(): String? =
        sequenceOf("em", "msg", "message")
            .mapNotNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { it.isNotBlank() }
}
