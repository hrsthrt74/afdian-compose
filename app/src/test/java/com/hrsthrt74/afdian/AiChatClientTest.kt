package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.ai.AiChatClient
import com.hrsthrt74.afdian.data.ai.AiConfig
import com.hrsthrt74.afdian.data.ai.AiMessage
import com.hrsthrt74.afdian.data.ai.AiProviderPreset
import com.hrsthrt74.afdian.data.ai.AiReasoningEffort
import com.hrsthrt74.afdian.data.ai.AiRequestException
import com.hrsthrt74.afdian.data.ai.AiStreamEvent
import com.hrsthrt74.afdian.data.ai.AiThinkingMode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * OpenAI 兼容流式客户端的回归测试：用 mockwebserver3 假装 AI 端点，
 * 锁住 SSE 解析、[DONE] 结束、错误体转可读异常、非流式兜底这几条关键路径。
 */
class AiChatClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AiChatClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = AiChatClient(
            client = OkHttpClient(),
            config = AiConfig(
                preset = AiProviderPreset.Custom,
                baseUrl = server.url("/v1").toString(),
                model = "test-model",
                apiKey = "test-key",
            ),
        )
    }

    @After
    fun tearDown() {
        // mockwebserver3 的关闭入口是 Closeable.close()（5.x 没有 shutdown）
        server.close()
    }

    /** 标准 SSE：多条增量 + [DONE]，事件顺序与文本拼接都要正确。 */
    @Test
    fun `SSE 增量逐条产出并以 Done 收尾`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body(
                    """
                    data: {"choices":[{"delta":{"role":"assistant","content":null}}]}

                    data:{"choices":[{"delta":{"content":"你"}}]}

                    data: {"choices":[{"delta":{"content":"好"}}]}

                    data: [DONE]

                    """.trimIndent(),
                )
                .build(),
        )
        val events = client.stream(listOf(AiMessage.user("hi"))).toList()
        // 开头的空增量（content=null）不产出事件
        assertEquals(listOf("你", "好"), events.filterIsInstance<AiStreamEvent.Delta>().map { it.text })
        assertEquals("你好", events.last().let { it as AiStreamEvent.Done }.text)
    }

    /** 服务端无视 stream 参数直接回完整 JSON 的兜底路径。 */
    @Test
    fun `非流式 JSON 响应转为单段增量与 Done`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"choices":[{"message":{"role":"assistant","content":"完整回答"}}]}""")
                .build(),
        )
        val events = client.stream(listOf(AiMessage.user("hi"))).toList()
        assertEquals(2, events.size)
        assertEquals("完整回答", (events[0] as AiStreamEvent.Delta).text)
        assertEquals("完整回答", (events[1] as AiStreamEvent.Done).text)
    }

    /** HTTP 错误体 {"error":{"message":...}} → 带可读信息的 AiRequestException。 */
    @Test
    fun `非 200 响应转成可读异常`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(401)
                .setHeader("Content-Type", "application/json")
                .body("""{"error":{"message":"Invalid API key","type":"auth_error"}}""")
                .build(),
        )
        val error = runCatching {
            client.stream(listOf(AiMessage.user("hi"))).toList()
        }.exceptionOrNull()
        assertTrue("应当抛出 AiRequestException", error is AiRequestException)
        assertEquals("HTTP 401：Invalid API key", error!!.message)
    }

    /** 200 流里夹带 error 对象（部分网关的行为）也要显式失败。 */
    @Test
    fun `SSE 流内嵌 error 对象显式失败`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body("""data: {"error":{"message":"model not found"}}"""+ "\n\n")
                .build(),
        )
        val error = runCatching {
            client.stream(listOf(AiMessage.user("hi"))).toList()
        }.exceptionOrNull()
        assertTrue(error is AiRequestException)
        assertEquals("model not found", error!!.message)
    }

    /** 只有 [DONE] 没有任何内容：按失败处理而不是空成功。 */
    @Test
    fun `空流报模型无返回`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body("data: [DONE]\n\n")
                .build(),
        )
        val error = runCatching {
            client.stream(listOf(AiMessage.user("hi"))).toList()
        }.exceptionOrNull()
        assertTrue(error is AiRequestException)
        assertTrue(error!!.message.orEmpty().contains("模型没有返回任何内容"))
    }

    /**
     * 回归：中转网关常把 SSE 标成 `application/json`（或干脆不给 Content-Type），
     * 解析必须按内容识别流式，否则会退化成「整段读完整才一次性出现」，弹层长时间空白。
     */
    @Test
    fun `Content-Type 标错的 SSE 仍按增量产出`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(
                    """data: {"choices":[{"delta":{"content":"你"}}]}""" + "\n\n" +
                        """data: {"choices":[{"delta":{"content":"好"}}]}""" + "\n\n" +
                        "data: [DONE]\n\n"
                )
                .build(),
        )
        val events = client.stream(listOf(AiMessage.user("hi"))).toList()
        assertEquals(listOf("你", "好"), events.filterIsInstance<AiStreamEvent.Delta>().map { it.text })
        assertEquals("你好", events.filterIsInstance<AiStreamEvent.Done>().single().text)
    }

    /**
     * 一个事件的 JSON 被拆成多行 `data:`（SSE 规范允许）时要能拼回完整载荷再解析。
     * 旧实现按行独立解析，遇到这种流会整段静默丢弃。
     */
    @Test
    fun `多行 data 事件拼接后正常解析`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body(
                    "data: {\"choices\":[{\"delta\":\n" +
                        "data: {\"content\":\"你好\"}}]}\n\n" +
                        "data: [DONE]\n\n"
                )
                .build(),
        )
        val events = client.stream(listOf(AiMessage.user("hi"))).toList()
        assertEquals("你好", events.filterIsInstance<AiStreamEvent.Done>().single().text)
    }

    /**
     * 推理模型（小米 MiMo v2.6 默认开思维链）先长时间只吐 `reasoning_content`。
     * 这类增量要作为 [AiStreamEvent.Reasoning] 单独产出，不能静默丢弃，
     * 也不能混进正文。
     */
    @Test
    fun `思考增量单独产出且不混入正文`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body(
                    """data: {"choices":[{"delta":{"reasoning_content":"先看数据"}}]}""" + "\n\n" +
                        """data: {"choices":[{"delta":{"content":"结论"}}]}""" + "\n\n" +
                        "data: [DONE]\n\n"
                )
                .build(),
        )
        val events = client.stream(listOf(AiMessage.user("hi"))).toList()
        assertEquals(listOf("先看数据"), events.filterIsInstance<AiStreamEvent.Reasoning>().map { it.text })
        assertEquals("结论", events.filterIsInstance<AiStreamEvent.Done>().single().text)
    }

    /** 思考完却没正文：报错要说清是「只返回了思考过程」，而不是含糊的「没有返回内容」。 */
    @Test
    fun `只有思考过程时报错说明原因`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body(
                    """data: {"choices":[{"delta":{"reasoning_content":"嗯……"}}]}""" + "\n\n" +
                        "data: [DONE]\n\n"
                )
                .build(),
        )
        val error = runCatching {
            client.stream(listOf(AiMessage.user("hi"))).toList()
        }.exceptionOrNull()
        assertTrue(error is AiRequestException)
        assertTrue(error!!.message.orEmpty().contains("思考过程"))
    }

    /** 思考模式「开启」：请求体带 `thinking.type=enabled` 与 `reasoning_effort`。 */
    @Test
    fun `开启思考时请求体带 thinking 与 reasoning_effort`() = runBlocking {
        val enabledClient = AiChatClient(
            client = OkHttpClient(),
            config = AiConfig(
                preset = AiProviderPreset.Custom,
                baseUrl = server.url("/v1").toString(),
                model = "test-model",
                apiKey = "k",
                thinkingMode = AiThinkingMode.Enabled,
                reasoningEffort = AiReasoningEffort.Max,
            ),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body("""data: {"choices":[{"delta":{"content":"ok"}}]}""" + "\n\ndata: [DONE]\n\n")
                .build(),
        )
        enabledClient.stream(listOf(AiMessage.user("hi"))).toList()
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue(body.contains("\"reasoning_effort\":\"max\""))
    }

    /** 思考模式「关闭」：只发关闭标记，不发强度（发了也没意义）。 */
    @Test
    fun `关闭思考时只带 thinking 关闭标记`() = runBlocking {
        val disabledClient = AiChatClient(
            client = OkHttpClient(),
            config = AiConfig(
                preset = AiProviderPreset.Custom,
                baseUrl = server.url("/v1").toString(),
                model = "test-model",
                apiKey = "k",
                thinkingMode = AiThinkingMode.Disabled,
            ),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body("""data: {"choices":[{"delta":{"content":"ok"}}]}""" + "\n\ndata: [DONE]\n\n")
                .build(),
        )
        disabledClient.stream(listOf(AiMessage.user("hi"))).toList()
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertFalse("关闭档不该带强度", body.contains("reasoning_effort"))
    }

    /** 默认档位（未显式指定）：开启 + 高强度，避免「什么都不发」的模糊状态。 */
    @Test
    fun `默认档位发送开启与高强度`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body("""data: {"choices":[{"delta":{"content":"ok"}}]}""" + "\n\ndata: [DONE]\n\n")
                .build(),
        )
        client.stream(listOf(AiMessage.user("hi"))).toList()
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue(body.contains("\"reasoning_effort\":\"high\""))
    }

    /** 请求侧契约：POST 到 {baseUrl}/chat/completions，带 Bearer 头与 stream 标记。 */
    @Test
    fun `请求携带 Bearer 与 stream 标记`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body("""data: {"choices":[{"delta":{"content":"ok"}}]}""" + "\n\ndata: [DONE]\n\n")
                .build(),
        )
        client.stream(listOf(AiMessage.system("sys"), AiMessage.user("hi"))).toList()
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.target)
        assertEquals("Bearer test-key", recorded.headers["Authorization"])
        val body = recorded.body!!.utf8()
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("\"model\":\"test-model\""))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"content\":\"sys\""))
    }

    /** 空 Key（本地自建服务场景）不发 Authorization 头。 */
    @Test
    fun `空 Key 不发送 Authorization 头`() = runBlocking {
        val noKeyClient = AiChatClient(
            client = OkHttpClient(),
            config = AiConfig(
                preset = AiProviderPreset.Custom,
                baseUrl = server.url("/v1").toString(),
                model = "llama3.2",
                apiKey = "",
            ),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body("""data: {"choices":[{"delta":{"content":"ok"}}]}""" + "\n\ndata: [DONE]\n\n")
                .build(),
        )
        noKeyClient.stream(listOf(AiMessage.user("hi"))).toList()
        val recorded = server.takeRequest()
        assertEquals(null, recorded.headers["Authorization"])
    }
}
