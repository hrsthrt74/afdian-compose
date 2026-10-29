package com.hrsthrt74.afdian.data.mcp

import com.hrsthrt74.afdian.AppLog
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.URI

/**
 * MCP 本地服务器：Ktor CIO 引擎，仅监听回环地址 127.0.0.1。
 *
 * 安全三道闸（顺序即请求处理顺序）：
 * 1. **Origin 校验**：浏览器发起的请求必带 Origin 头；出现时只放行本机回环来源，
 *    阻断 DNS rebinding（恶意网页把 127.0.0.1 解析成自己的域名再访问本机端口）。
 *    非浏览器客户端（Cherry Studio / ZCode 等）不发送 Origin，直接放行；
 * 2. **Bearer 鉴权**：请求头 `Authorization: Bearer <访问令牌>`，令牌在设置页生成；
 * 3. **会话校验**：initialize 之外的请求必须携带握手时下发的 Mcp-Session-Id。
 *
 * 生命周期由 [McpServerController] 按设置开关启停；服务器本身无状态，
 * stop 后可以换端口/换令牌再次 start。
 */
class McpServer(
    internal val port: Int,
    internal val bearerToken: String,
    private val handler: McpRequestHandler,
) {
    private var engine: EmbeddedServer<*, *>? = null

    /** 是否正在运行（供设置页展示状态与测试断言）。 */
    @Volatile
    var isRunning: Boolean = false
        private set

    /** 启动服务器；已在运行时先停掉旧实例（换端口/换令牌后的重启路径）。 */
    fun start() {
        stop()
        AppLog.d(TAG, "MCP 服务器启动中：127.0.0.1:$port")
        engine = embeddedServer(CIO, host = BIND_HOST, port = port) {
            routing {
                post(MCP_PATH) { handlePostRequest(call) }
                get(MCP_PATH) { handleGetRequest(call) }
                delete(MCP_PATH) { handleDeleteRequest(call) }
            }
        }.start(wait = false)
        isRunning = true
        AppLog.d(TAG, "MCP 服务器已启动：127.0.0.1:$port")
    }

    /** 停止服务器；未启动时调用是安全的空操作。 */
    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        engine = null
        if (isRunning) {
            AppLog.d(TAG, "MCP 服务器已停止")
        }
        isRunning = false
    }

    /** POST /mcp：鉴权 → 读体 → 协议处理 → 按 [McpHttpResponse] 写回。 */
    private suspend fun handlePostRequest(call: ApplicationCall) {
        val originError = checkOrigin(call)
        if (originError != null) {
            call.respondText(originError, ContentType.Application.Json, HttpStatusCode.Forbidden)
            return
        }
        if (!isAuthorized(call)) {
            AppLog.w(TAG, "MCP 鉴权失败（令牌缺失或不匹配）")
            call.respondText(
                jsonError("Unauthorized: 访问令牌缺失或不匹配"),
                ContentType.Application.Json,
                HttpStatusCode.Unauthorized,
            )
            return
        }
        val body = call.receiveText()
        val response = handler.handlePost(body, call.request.headers[MCP_SESSION_HEADER])
        when (response) {
            is McpHttpResponse.Json -> {
                // initialize 时把新会话 id 放进响应头（Streamable HTTP 规范要求）
                response.newSessionId?.let { call.response.headers.append(MCP_SESSION_HEADER, it) }
                call.respondText(
                    response.body.orEmpty(),
                    ContentType.Application.Json,
                    HttpStatusCode.fromValue(response.status),
                )
            }
            is McpHttpResponse.Failure -> call.respondText(
                jsonError("Session not found: 请重新 initialize"),
                ContentType.Application.Json,
                HttpStatusCode.fromValue(response.status),
            )
        }
    }

    /**
     * GET /mcp：本实现不提供 SSE 下行通知通道，按规范回 405 并在 Allow 头里
     * 告知可用方法。收到 405 的客户端不会尝试维持长连接。
     */
    private suspend fun handleGetRequest(call: ApplicationCall) {
        if (!isAuthorized(call)) {
            call.respondText(
                jsonError("Unauthorized: 访问令牌缺失或不匹配"),
                ContentType.Application.Json,
                HttpStatusCode.Unauthorized,
            )
            return
        }
        call.response.headers.append("Allow", "POST, DELETE")
        call.respondText(
            "SSE downlink is not supported by this server",
            ContentType.Text.Plain,
            HttpStatusCode.MethodNotAllowed,
        )
    }

    /** DELETE /mcp：客户端显式关闭会话，成功回 204。 */
    private suspend fun handleDeleteRequest(call: ApplicationCall) {
        if (!isAuthorized(call)) {
            call.respondText(
                jsonError("Unauthorized: 访问令牌缺失或不匹配"),
                ContentType.Application.Json,
                HttpStatusCode.Unauthorized,
            )
            return
        }
        when (val response = handler.handleDelete(call.request.headers[MCP_SESSION_HEADER])) {
            is McpHttpResponse.Json -> call.respondText(
                "",
                ContentType.Application.Json,
                HttpStatusCode.NoContent,
            )
            is McpHttpResponse.Failure -> call.respondText(
                "",
                ContentType.Application.Json,
                HttpStatusCode.fromValue(response.status),
            )
        }
    }

    /** Bearer 令牌校验：头缺失、格式不对、令牌不匹配都算失败。 */
    private fun isAuthorized(call: ApplicationCall): Boolean {
        val header = call.request.headers["Authorization"] ?: return false
        // 常量时间比较防止时序侧信道（本地服务威胁模型下意义有限，但成本为零）
        return constantTimeEquals(header, "Bearer $bearerToken")
    }

    private companion object {
        const val TAG = "McpServer"
        const val MCP_PATH = "/mcp"
        const val MCP_SESSION_HEADER = "Mcp-Session-Id"
        /** 只绑定回环地址：外部设备（含局域网）根本连不上，这是第一道也是最强的一道闸 */
        const val BIND_HOST = "127.0.0.1"

        /** JSON 错误体的最小信封（鉴权/Origin 失败时给客户端一句人话）。 */
        fun jsonError(message: String): String =
            "{\"error\":\"$message\"}"

        /** 常量时间字符串比较：长度不同直接 false，等长时全量比较不提前返回。 */
        fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var diff = 0
            for (index in a.indices) diff = diff or (a[index].code xor b[index].code)
            return diff == 0
        }
    }
}

/**
 * Origin 头校验：null（非浏览器请求）放行；非 null 时必须是本机回环来源。
 * 返回 null 表示通过，返回字符串表示拒绝原因（写进 403 响应体）。
 */
internal fun checkOrigin(call: ApplicationCall): String? {
    val origin = call.request.headers["Origin"] ?: return null
    val host = runCatching { URI(origin).host?.lowercase() }.getOrNull()
    val allowed = host == "127.0.0.1" || host == "localhost" || host == "::1"
    if (allowed) return null
    AppLog.w("McpServer", "MCP Origin 校验失败：$origin")
    return "{\"error\":\"Forbidden: Origin 不在白名单（仅允许本机来源）\"}"
}
