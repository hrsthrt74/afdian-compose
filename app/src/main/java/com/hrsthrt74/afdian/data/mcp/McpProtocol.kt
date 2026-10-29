package com.hrsthrt74.afdian.data.mcp

import com.hrsthrt74.afdian.AppLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

// ── MCP 协议常量 ─────────────────────────────────────────────────────────────
//
// 本实现是 MCP Streamable HTTP 的**最小合规子集**：
// - POST /mcp 收 JSON-RPC，回 application/json（规范允许不用 SSE 流式应答）；
// - GET /mcp 返回 405（声明不支持 SSE 下行通知通道，规范允许）；
// - DELETE /mcp 关闭会话。
// 因此只适配「无 SSE 也能工作」的客户端（Cherry Studio、ZCode、Claude 系等）。

/** 本服务器实现的 MCP 协议版本（2025-06-18 起废止批量请求，本实现按单请求处理）。 */
const val MCP_PROTOCOL_VERSION = "2025-06-18"

/** initialize 时可接受的历史协议版本（2025-03-26 与 06-18 在本子集内线上行为一致）。 */
val MCP_SUPPORTED_VERSIONS = setOf("2025-06-18", "2025-03-26")

/** 服务器标识：外部 AI 客户端的连接列表里显示的名字。 */
const val MCP_SERVER_NAME = "afdian-android"

/** 服务器实现版本（跟随应用功能迭代手动更新）。 */
const val MCP_SERVER_VERSION = "1.0"

/** JSON-RPC 标准错误码（协议约定值）。 */
private const val ERR_PARSE = -32700
private const val ERR_INVALID_REQUEST = -32600
private const val ERR_METHOD_NOT_FOUND = -32601
private const val ERR_INVALID_PARAMS = -32602

/** 工具执行抛出的业务错误：转成 MCP 的 isError 结果而不是 JSON-RPC error（规范如此约定）。 */
class McpToolException(message: String) : Exception(message)

/** 单个 MCP 工具的定义：名称/描述/JSON Schema + 取数实现。 */
class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val handler: suspend (JsonObject) -> String,
)

/** POST/DELETE /mcp 的处理结果：Ktor 层按此写回 HTTP。 */
sealed interface McpHttpResponse {

    /**
     * 带响应体的结果。
     * @param status HTTP 状态码（200=正常应答 / 202=通知确认 / 400=协议解析失败）
     * @param body JSON 文本；null 表示无响应体（202 通知确认 / DELETE 成功）
     * @param newSessionId initialize 时新分配的会话 id，经 Mcp-Session-Id 响应头下发
     */
    data class Json(val status: Int, val body: String?, val newSessionId: String? = null) : McpHttpResponse

    /** 无响应体的失败：404 会话不存在等，由 Ktor 直接回状态码。 */
    data class Failure(val status: Int) : McpHttpResponse
}

/**
 * MCP 会话管理：initialize 时签发随机 id，后续请求校验。
 *
 * 会话不存任何业务状态（每个请求都自带数据），只做「这个客户端握手过」的凭证，
 * 进程内一个并发 Set 就够；应用重启即全部失效，客户端会自动重新 initialize。
 */
class McpSessionManager {
    private val sessions: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val random = SecureRandom()

    /** 签发新会话 id（32 位十六进制，SecureRandom 生成）。 */
    fun create(): String = buildString {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        for (byte in bytes) append("%02x".format(byte))
    }.also { sessions.add(it) }

    /** 校验请求携带的会话 id；null 或未签发过都视为无效。 */
    fun validate(sessionId: String?): Boolean = sessionId != null && sessions.contains(sessionId)

    /** 客户端 DELETE /mcp 显式关闭会话。 */
    fun close(sessionId: String?) {
        if (sessionId != null) sessions.remove(sessionId)
    }
}

/**
 * MCP JSON-RPC 请求的核心处理器（纯协议逻辑，不碰 HTTP，单测直接喂字符串）。
 *
 * 支持方法：initialize、notifications/initialized、ping、tools/list、tools/call；
 * 会话规则：initialize 之外的方法都必须携带有效 Mcp-Session-Id（无效回 404，
 * 客户端会自动重新握手）。
 *
 * @param tools 对外暴露的工具集（构建见 McpTools.kt）
 * @param sessions 会话管理；默认自建，测试可注入独立实例
 */
class McpRequestHandler(
    private val tools: List<McpToolDefinition>,
    private val sessions: McpSessionManager = McpSessionManager(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    // tools/call 可能并发（多个客户端同时请求）；工具表构建后不可变，按名索引即可
    private val toolByName: Map<String, McpToolDefinition> = tools.associateBy { it.name }

    /** 暴露会话管理器（服务器层与测试需要主动校验/关闭会话）。 */
    val sessionManager: McpSessionManager get() = sessions

    /** 处理 POST /mcp 的请求体。 */
    suspend fun handlePost(body: String, sessionId: String?): McpHttpResponse {
        // 解析失败：JSON-RPC 约定回 -32700（HTTP 400，客户端不得原样重试）
        val root: JsonElement = runCatching { json.parseToJsonElement(body) }.getOrElse {
            AppLog.w(TAG, "MCP 请求体解析失败：${body.take(LOG_PREVIEW)}")
            return McpHttpResponse.Json(400, rpcError(null, ERR_PARSE, "Parse error"))
        }
        // 2025-06-18 起批量请求已废止：收到数组一律拒绝，避免客户端误以为支持
        if (root is JsonArray) {
            return McpHttpResponse.Json(
                400,
                rpcError(null, ERR_INVALID_REQUEST, "Batch requests are not supported"),
            )
        }
        val request = root as? JsonObject
            ?: return McpHttpResponse.Json(400, rpcError(null, ERR_INVALID_REQUEST, "Invalid Request"))
        val method = request.string("method")
        val id = request["id"]
        // 显式 null id 按通知处理（JSON-RPC 规范：请求的 id 不得为 null）
        val isNotification = id == null || id is JsonNull

        // initialize：唯一不需要会话的方法；握手即签发新会话
        if (method == "initialize") {
            val requestedVersion = (request["params"] as? JsonObject)
                ?.string("protocolVersion")
                .orEmpty()
            // 客户端请求的版本在支持列表里就按它的来，否则回我们最新的
            val negotiated = requestedVersion.takeIf { it in MCP_SUPPORTED_VERSIONS }
                ?: MCP_PROTOCOL_VERSION
            val newSession = sessions.create()
            AppLog.d(TAG, "MCP initialize：client=$requestedVersion -> $negotiated，新会话已签发")
            val result = buildJsonObject {
                put("protocolVersion", negotiated)
                putJsonObject("capabilities") {
                    putJsonObject("tools") {
                        // 工具集是静态的，不承诺 listChanged 通知
                        put("listChanged", false)
                    }
                }
                putJsonObject("serverInfo") {
                    put("name", MCP_SERVER_NAME)
                    put("version", MCP_SERVER_VERSION)
                }
            }
            return McpHttpResponse.Json(200, rpcResult(id, result), newSessionId = newSession)
        }

        // 其余方法一律要求有效会话（Streamable HTTP 规范：无效/过期会话回 404）
        if (!sessions.validate(sessionId)) {
            AppLog.w(TAG, "MCP 会话无效：method=$method，携带=${sessionId?.take(8) ?: "null"}")
            return McpHttpResponse.Failure(404)
        }

        // 通知类（notifications/initialized 等）与无 id 请求：202 空响应
        if (method?.startsWith("notifications/") == true || isNotification) {
            return McpHttpResponse.Json(202, null)
        }

        return when (method) {
            "ping" -> McpHttpResponse.Json(200, rpcResult(id, buildJsonObject { }))
            "tools/list" -> handleToolsList(id)
            "tools/call" -> handleToolsCall(id, request["params"] as? JsonObject)
            else -> McpHttpResponse.Json(200, rpcError(id, ERR_METHOD_NOT_FOUND, "Method not found: $method"))
        }
    }

    /** DELETE /mcp：客户端主动关闭会话；会话不存在回 404。 */
    fun handleDelete(sessionId: String?): McpHttpResponse {
        if (!sessions.validate(sessionId)) return McpHttpResponse.Failure(404)
        sessions.close(sessionId)
        return McpHttpResponse.Json(200, null)
    }

    /** tools/list：输出工具名、描述与 JSON Schema。 */
    private fun handleToolsList(id: JsonElement?): McpHttpResponse {
        val result = buildJsonObject {
            put("tools", buildJsonArray {
                tools.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchema", tool.inputSchema)
                        },
                    )
                }
            })
        }
        return McpHttpResponse.Json(200, rpcResult(id, result))
    }

    /** tools/call：按名分发；业务错误转 isError 结果，参数问题转 -32602。 */
    private suspend fun handleToolsCall(id: JsonElement?, params: JsonObject?): McpHttpResponse {
        val name = params?.string("name")
        if (name.isNullOrBlank()) {
            return McpHttpResponse.Json(200, rpcError(id, ERR_INVALID_PARAMS, "Missing tool name"))
        }
        val tool = toolByName[name]
            ?: return McpHttpResponse.Json(200, rpcError(id, ERR_INVALID_PARAMS, "Unknown tool: $name"))
        val arguments = params?.get("arguments") as? JsonObject ?: JsonObject(emptyMap())
        val start = System.currentTimeMillis()
        return try {
            val output = tool.handler(arguments)
            AppLog.d(TAG, "tools/call $name ok（${System.currentTimeMillis() - start}ms）")
            McpHttpResponse.Json(200, toolResult(id, output, isError = false))
        } catch (e: McpToolException) {
            // 工具业务失败（如未登录）：按 MCP 规范放进 isError 结果，让模型看到原因
            AppLog.w(TAG, "tools/call $name 业务失败：${e.message}")
            McpHttpResponse.Json(200, toolResult(id, e.message ?: "工具执行失败", isError = true))
        } catch (e: Exception) {
            AppLog.e(TAG, "tools/call $name 异常", e)
            McpHttpResponse.Json(200, toolResult(id, "工具执行异常：${e.message}", isError = true))
        }
    }

    // ── JSON-RPC 信封构造 ──

    private fun rpcResult(id: JsonElement?, result: JsonObject): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            put("result", result)
        }.toString()

    private fun rpcError(id: JsonElement?, code: Int, message: String): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }.toString()

    private fun toolResult(id: JsonElement?, text: String, isError: Boolean): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("result") {
                put("isError", isError)
                put("content", buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", text)
                        },
                    )
                })
            }
        }.toString()

    private companion object {
        const val TAG = "McpProtocol"

        /** 日志里请求体的预览长度 */
        const val LOG_PREVIEW = 120

        /** JsonObject 的字符串字段容错读取（缺失/非字符串都返回空串）。 */
        fun JsonObject.string(key: String): String =
            (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}

/** JsonObject 的字符串字段容错读取（mcp 包内多处要用，收拢成顶层扩展）。 */
internal fun JsonObject.optString(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

/** JsonObject 的整数字段容错读取：兼容数字与数字字符串两种形态（如 "3" 与 3）。 */
internal fun JsonObject.optInt(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()

// ── 本地服务器参数 ──────────────────────────────────────────────────────────

/** MCP 服务器默认端口：避开常用端口段，好记（爱发电谐音「8787」也不冲突）。 */
const val MCP_DEFAULT_PORT = 8787

/** 端口合法区间：避开特权端口（<1024）与动态端口上限。 */
const val MCP_PORT_MIN = 1024
const val MCP_PORT_MAX = 65535

/** 归一化端口：夹取到合法区间（设置页与数据层读取统一走它，口径一致）。 */
fun normalizeMcpPort(raw: Int): Int = raw.coerceIn(MCP_PORT_MIN, MCP_PORT_MAX)

/** 生成访问令牌：SecureRandom 16 字节的十六进制（32 字符，无歧义、可手抄）。 */
fun generateMcpToken(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return buildString(bytes.size * 2) {
        for (byte in bytes) append("%02x".format(byte))
    }
}
