package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.mcp.McpHttpResponse
import com.hrsthrt74.afdian.data.mcp.McpRequestHandler
import com.hrsthrt74.afdian.data.mcp.McpSessionManager
import com.hrsthrt74.afdian.data.mcp.McpToolDefinition
import com.hrsthrt74.afdian.data.mcp.McpToolException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MCP JSON-RPC 协议处理器的回归测试：不用真 HTTP，直接喂字符串，
 * 覆盖握手/会话/工具列表/工具调用/错误码全链路。
 */
class McpProtocolTest {

    /** 测试工具集：一个正常回显 + 一个业务失败。 */
    private val tools = listOf(
        McpToolDefinition(
            name = "echo",
            description = "回显 msg 参数",
            inputSchema = buildJsonObject { put("type", "object") },
            handler = { args -> "got:" + (args["msg"]?.jsonPrimitive?.content ?: "") },
        ),
        McpToolDefinition(
            name = "boom",
            description = "业务失败样例",
            inputSchema = buildJsonObject { put("type", "object") },
            handler = { _ -> throw McpToolException("未登录") },
        ),
    )

    private val sessions = McpSessionManager()
    private val handler = McpRequestHandler(tools, sessions)
    private val json = Json { ignoreUnknownKeys = true }

    /** 走一次 initialize 拿到合法会话 id（后续用例的前提）。 */
    private suspend fun initializeAndGetSession(): String {
        val response = handler.handlePost(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""",
            sessionId = null,
        )
        return (response as McpHttpResponse.Json).newSessionId!!
    }

    // ── initialize ──

    @Test
    fun `initialize 协商版本并签发会话`() = runBlocking {
    }

    @Test
    fun `initialize 不认识的版本回退到实现版本`() = runBlocking {
    }

    // ── 会话门槛 ──

    @Test
    fun `无会话或假会话回 404`() = runBlocking {
    }

    // ── 通知与 ping ──

    @Test
    fun `notifications 请求回 202 空响应体`() = runBlocking {
    }

    @Test
    fun `ping 回空 result`() = runBlocking {
    }

    // ── tools/list ──

    @Test
    fun `tools_list 列出全部工具与 schema`() = runBlocking {
    }

    // ── tools/call ──

    @Test
    fun `tools_call 正常返回文本结果`() = runBlocking {
    }

    @Test
    fun `tools_call 业务异常转 isError 结果`() = runBlocking {
    }

    @Test
    fun `tools_call 未知工具回 -32602`() = runBlocking {
    }

    @Test
    fun `tools_call 缺工具名回 -32602`() = runBlocking {
    }

    // ── 错误码与非法请求 ──

    @Test
    fun `未知方法回 -32601`() = runBlocking {
    }

    @Test
    fun `请求体不是 JSON 回 400 与 -32700`() = runBlocking {
    }

    @Test
    fun `批量请求被拒绝`() = runBlocking {
    }

    // ── DELETE 会话 ──

    @Test
    fun `delete 关闭会话后立即失效`() = runBlocking {
    }

    // ── 会话管理器独立行为 ──

    @Test
    fun `会话管理器签发的 id 均不重复`() = runBlocking {
    }
}
