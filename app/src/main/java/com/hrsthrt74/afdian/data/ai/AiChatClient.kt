package com.hrsthrt74.afdian.data.ai

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容的流式对话客户端（BYOK）。
 *
 * 协议：`POST {baseUrl}/chat/completions`，`stream=true` 时响应是 SSE
 * （`text/event-stream`，每条增量一行 `data: {json}`，以 `data: [DONE]` 结束）。
 * 解析只取 `choices[0].delta.content`（可能缺失，如角色开头的空增量）；
 * DeepSeek 等推理模型的 `reasoning_content` 思考过程刻意忽略，只保留最终答案。
 *
 * 取消语义：调用方取消 Flow 时通过 [Job.invokeOnCompletion] 调 `call.cancel()`，
 * 把阻塞中的 socket 读打断 —— 长回答中途关掉弹层必须立刻断流，不能等模型说完。
 *
 * 网络层不复用爱发电的域名链重试（那些逻辑只属于 afdian 域名），
 * 但共享连接池：由 [sharedAiHttpClient] 基于 `sharedAfdianHttpClient` 派生，
 * 只改超时 —— 流式生成的「读超时」是两次字节到达之间的间隔，
 * 推理模型思考时可能远超普通接口的 10s 默认值，放宽到 5 分钟。
 */
class AiChatClient(
    private val client: OkHttpClient,
    private val config: AiConfig,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 发起流式对话。冷 Flow：每次 collect 都发一次全新请求。
     *
     * 事件序列：若干 [AiStreamEvent.Delta]（按到达顺序）→ 一个 [AiStreamEvent.Done]；
     * 失败时 Flow 以 [AiRequestException] 结束（UI 按「失败态 + 重试」呈现）。
     */
    fun stream(messages: List<AiMessage>): Flow<AiStreamEvent> = flow {
        val request = buildRequest(messages)
        val call = client.newCall(request)
        val tracker = StreamProgress(System.currentTimeMillis())
        // 请求一旦发出就把「发到哪、多大」记下来：卡住时第一件事就是确认请求真的出去了
        AppLog.d(
            TAG,
            "发起请求：POST ${request.url}，请求体 ${request.body?.contentLength() ?: -1L} 字节"
        )
        // 无论 Flow 如何结束（正常/取消/异常）都确保 HTTP 连接被中断；
        // 对已完成的 call 调 cancel() 是无害的空操作，所以正常完成路径也不用特判
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            // 看门狗：读 socket 是阻塞的，没数据时主流程一行日志都打不出来，
            // 只能靠另一个协程定时汇报「现在卡在哪一步、最后一条数据是多久前」
            coroutineScope {
                val watchdog = launch {
                    while (isActive) {
                        delay(WATCHDOG_INTERVAL_MS)
                        AppLog.d(TAG, tracker.describe())
                    }
                }
                try {
                    call.execute().use { response ->
                        tracker.onResponse(response.code)
                        if (!response.isSuccessful) throw aiErrorFrom(response)
                        AppLog.d(
                            TAG,
                            "HTTP ${response.code}，收到响应头（${tracker.responseCostMs()}ms），开始读响应体"
                        )
                        this@flow.emitResponse(response, tracker)
                    }
                } finally {
                    watchdog.cancel()
                }
            }
            AppLog.d(TAG, "chat done（${System.currentTimeMillis() - tracker.startedAt}ms）")
        } finally {
            cancellationHandle.dispose()
        }
    }.flowOn(Dispatchers.IO)

    /** 构建请求体与请求头。 */
    private fun buildRequest(messages: List<AiMessage>): Request {
        val body = buildString {
            append("{\"model\":")
            append(jsonLiteral(config.model))
            append(",\"messages\":[")
            messages.forEachIndexed { index, message ->
                if (index > 0) append(',')
                append("{\"role\":")
                append(jsonLiteral(message.role))
                append(",\"content\":")
                append(jsonLiteral(message.content))
                append('}')
            }
            append("],\"stream\":true")
            // 不传 temperature / max_tokens：新模型（GPT-5、o 系列等）不支持调温，
            // 多传反而会被部分网关判为非法参数，长度也交给模型自行决定
            //
            // 思考开关（DeepSeek / MiMo 等采用的通用写法）：
            // - 开启：thinking.type=enabled + reasoning_effort
            // - 关闭：只发 thinking.type=disabled（关闭时发强度没有意义）
            when (config.thinkingMode) {
                AiThinkingMode.Enabled -> {
                    append(",\"thinking\":{\"type\":\"enabled\"}")
                    append(",\"reasoning_effort\":")
                    append(jsonLiteral(config.reasoningEffort.storageValue))
                }
                AiThinkingMode.Disabled -> {
                    append(",\"thinking\":{\"type\":\"disabled\"}")
                }
            }
            append('}')
        }.toRequestBody(JSON_MEDIA_TYPE)

        val builder = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + CHAT_COMPLETIONS_PATH)
            .post(body)
            // 明确要 SSE；个别网关会按 Accept 决定是否分块
            .header("Accept", "text/event-stream")
            // 显式关掉压缩：部分网关/反代对 gzip 的 SSE 会攒够一个压缩块才下发，
            // 客户端看到的就是「HTTP 200 但长时间零字节」。SSE 本身开销小，不压缩更稳
            .header("Accept-Encoding", "identity")
        // 本地自建服务允许空 Key；此时不发 Authorization 头，避免部分网关对空 Bearer 报 401
        if (config.apiKey.isNotEmpty()) {
            builder.header("Authorization", "Bearer ${config.apiKey}")
        }
        return builder.build()
    }

    /**
     * 读取响应体并产出事件流。
     *
     * 三条关键设计（都踩过坑）：
     *
     * 1. **不按 `Content-Type` 分流**：大量中转/聚合网关把 SSE 标成 `application/json`
     *    或干脆不给这个头，信了它就会用 `readUtf8()` 整段读，表现是长时间空白、说完才一次性出现。
     *    这里见到第一行 `data:` 就认定为 SSE，一直没见到才把攒下的原文当整段 JSON 解析。
     * 2. **不按行阻塞读**：`readUtf8Line()` 必须等到换行符才返回，网关若把一次事件拆成
     *    多个不带换行的数据块（或压缩缓冲未刷），读线程会一直挂着，一个增量都出不来。
     *    改成按块读（有 1 字节就返回）再由自己切行，数据一到就能处理。
     * 3. **解析失败要可见**：旧实现把解析异常吞成空串，网关格式一变就变成「静默全丢」，
     *    日志里什么都看不到。现在失败计数 + 首个失败样例进日志，能直接定位。
     *
     * SSE 规范（OpenAI 兼容端点都遵循）：每个事件是一个或多个 `data:` 行，事件之间用空行分隔，
     * 末尾 `data: [DONE]` 收尾；`data:` 后可有一个空格，也可能没有。
     */
    private suspend fun FlowCollector<AiStreamEvent>.emitResponse(
        response: Response,
        tracker: StreamProgress,
    ) {
        val contentType = response.header("Content-Type").orEmpty()
        val source = response.body.source()
        AppLog.d(TAG, "响应 Content-Type=${contentType.ifBlank { "(缺失)" }}，按内容识别流式")

        // SSE 拼好的完整文本
        val sseText = StringBuilder()
        // 尚未确认是 SSE 之前攒下的原始行（可能是整段 JSON）
        val rawText = StringBuilder()
        var sawSse = false
        var deltaCount = 0
        var reasoningCount = 0
        // 解析失败的事件数 + 首个失败样例：网关换了格式时靠它定位，不再静默丢弃
        var badEventCount = 0
        var firstBadSample: String? = null
        var firstByteAt = 0L
        var firstDeltaAt = 0L
        var totalBytes = 0L
        var done = false
        // 跨块拼接的原始字节：按 \n 切出完整行后再解码，避免半个 UTF-8 字符被切坏
        val buffer = Buffer()
        // 多行 data 的拼接缓冲（SSE 允许一个事件拆成多个 data: 行）
        val pendingData = StringBuilder()

        /** 处理一行文本：SSE 数据行入解析，其余行在确认 SSE 前先攒着当整段 JSON。 */
        suspend fun handleLine(rawLine: String) {
            val line = rawLine.trimEnd('\r')
            if (line.isEmpty()) return
            if (!line.startsWith("data:")) {
                if (!sawSse) rawText.appendLine(line)
                return
            }
            sawSse = true
            // 兼容 `data: {...}` 与 `data:{...}` 两种写法
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) return
            if (payload == DONE_MARKER) {
                done = true
                return
            }
            // 先单独解析；失败则可能是多行 data 的前半截，拼上一条再试
            val merged = if (pendingData.isEmpty()) payload else pendingData.toString() + payload
            val parsed = parseDeltaPayload(merged)
            if (parsed == null) {
                // 解析不出来：可能是被拆行的 JSON，先留着等下一行；攒太长就判定为脏数据
                if (merged.length > MAX_PENDING_DATA) {
                    badEventCount++
                    tracker.onBadEvent()
                    if (firstBadSample == null) firstBadSample = merged.take(ERROR_BODY_PREVIEW)
                    pendingData.clear()
                } else {
                    pendingData.clear()
                    pendingData.append(merged)
                }
                return
            }
            pendingData.clear()
            // 前几个事件原样打出来：网关格式一变，看这行就知道它到底返回了什么
            tracker.onEvent(parsed.content.length, parsed.reasoning.length)
            if (tracker.eventCount <= EVENT_PREVIEW_COUNT) {
                AppLog.d(TAG, "事件#${tracker.eventCount}：${merged.take(ERROR_BODY_PREVIEW)}")
            }
            // 思考过程单独成事件发给上层（不拼进正文）：推理模型在思考阶段只会吐这个，
            // 全丢掉会让界面长时间毫无反馈
            if (parsed.reasoning.isNotEmpty()) {
                reasoningCount++
                emit(AiStreamEvent.Reasoning(parsed.reasoning))
            }
            if (parsed.content.isNotEmpty()) {
                if (deltaCount == 0) firstDeltaAt = System.currentTimeMillis()
                deltaCount++
                sseText.append(parsed.content)
                emit(AiStreamEvent.Delta(parsed.content))
            }
        }

        while (true) {
            // 取消请求后 call.cancel() 会让读取抛 IOException，这里补一刀确保抛出的是
            // CancellationException 而不是普通异常
            currentCoroutineContext().ensureActive()
            // 阻塞到至少 1 字节可读；返回 -1 表示对端已关闭
            val read = source.read(buffer, READ_CHUNK_BYTES)
            if (read == -1L) break
            if (tracker.onBytes(read)) {
                AppLog.d(
                    TAG,
                    "首字节到达：响应头后 ${tracker.firstByteDelayMs()}ms，本块 $read 字节" +
                        "（响应头之后等了这么久才有数据，推理模型在这一段通常是在思考）"
                )
            }
            if (firstByteAt == 0L) firstByteAt = System.currentTimeMillis()
            totalBytes += read
            // 把缓冲里所有完整的行切出来处理，剩下的半行留给下一块
            while (true) {
                val newlineIndex = buffer.indexOf(NEWLINE)
                if (newlineIndex == -1L) break
                // readUtf8(byteCount) 按字节数取，正好是完整的一行（不含 \n）
                handleLine(buffer.readUtf8(newlineIndex))
                buffer.readByte() // 丢掉行尾的 \n
            }
            if (done) break
        }
        // 收尾：对端关闭时缓冲里可能还剩最后一行（没有换行符）
        if (buffer.size > 0) handleLine(buffer.readUtf8())

        if (sawSse) {
            val fullText = sseText.toString()
            AppLog.d(
                TAG,
                "SSE 流结束：$deltaCount 个正文增量 / $reasoningCount 个思考增量 / $badEventCount 个解析失败，" +
                    "共 ${fullText.length} 字符、$totalBytes 字节" +
                    if (firstDeltaAt > 0 && firstByteAt > 0) "，首包 ${firstDeltaAt - firstByteAt}ms" else ""
            )
            if (firstBadSample != null) {
                AppLog.w(TAG, "首个无法解析的 data 载荷样例：${firstBadSample!!.take(ERROR_BODY_PREVIEW)}")
            }
            if (fullText.isEmpty()) {
                // 流提前断掉且一个字都没吐：多半是上游报错、模型名不对，或返回格式不认识
                throw AiRequestException(
                    when {
                        reasoningCount > 0 -> "模型只返回了思考过程，没有给出正文，请换一个非推理型模型试试"
                        badEventCount > 0 -> "无法解析服务端返回的数据格式（$badEventCount 个事件），请确认该端点兼容 OpenAI 协议"
                        else -> "模型没有返回任何内容，请检查模型名是否正确"
                    }
                )
            }
            emit(AiStreamEvent.Done(fullText))
        } else {
            // 整段 JSON 兜底（个别端点完全忽略 stream 参数）
            val body = rawText.toString()
            val full = parseNonStreaming(body)
                ?: throw AiRequestException("响应格式异常，不是 OpenAI 兼容接口（$contentType）")
            emit(AiStreamEvent.Delta(full))
            emit(AiStreamEvent.Done(full))
        }
    }

    /** 一条 `data:` 载荷解析出的内容：正文与思考过程分开，二者都可能为空。 */
    private data class DeltaPayload(val content: String, val reasoning: String)

    /**
     * 解析一条 `data:` 载荷。
     *
     * @return JSON 结构不合法时返回 null（调用方据此判断「是不是被拆行的多行 data」，
     *         而不是当成无内容丢弃）。
     */
    private fun parseDeltaPayload(payload: String): DeltaPayload? = runCatching {
        val root = json.parseToJsonElement(payload).jsonObject
        // 部分网关把错误混在 200 流里下发：{"error": {...}}，必须显式失败而不是静默吞掉
        (root["error"] as? JsonObject)?.let { error ->
            throw AiRequestException(readableErrorMessage(error) ?: "服务端返回错误")
        }
        val choice = (root["choices"]?.jsonArray?.firstOrNull() as? JsonObject)
            ?: return DeltaPayload("", "")
        val delta = choice["delta"] as? JsonObject
        // 正文：兼容 chat.completions（delta.content）与旧 completions（text）两种增量形态
        val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
            ?: choice["text"]?.jsonPrimitive?.contentOrNull
            ?: ""
        // 思考过程：OpenAI 兼容端点用 reasoning_content，部分网关简写成 reasoning
        val reasoning = delta?.get(REASONING_FIELD)?.jsonPrimitive?.contentOrNull
            ?: delta?.get("reasoning")?.jsonPrimitive?.contentOrNull
            ?: ""
        DeltaPayload(content, reasoning)
    }.getOrElse { error ->
        if (error is AiRequestException) throw error
        null
    }

    /** 解析「无视 stream 参数」的非流式响应体，取完整文本；结构不对返回 null。 */
    private fun parseNonStreaming(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        (root["error"] as? JsonObject)?.let { error ->
            throw AiRequestException(readableErrorMessage(error) ?: "服务端返回错误")
        }
        val message = (root["choices"]?.jsonArray?.firstOrNull() as? JsonObject)
            ?.get("message") as? JsonObject ?: return null
        message["content"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** 非 2xx 响应 → 带可读信息的异常。错误体一般是 {"error":{"message":"..."}} 或纯文本。 */
    private fun aiErrorFrom(response: Response): AiRequestException {
        val body = runCatching { response.body.string() }.getOrNull().orEmpty()
        val detail = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.let { element ->
                (element as? JsonObject)?.let { readableErrorMessage(it) }
                    // error 是字符串形态（少见但存在）：{"error": "api key invalid"}
                    ?: (element as? JsonPrimitive)?.contentOrNull
            }
        }.getOrNull() ?: body.take(ERROR_BODY_PREVIEW)
        val cleaned = detail.orEmpty().trim().removeSuffix("\n")
        AppLog.w(TAG, "chat 请求失败 HTTP ${response.code}：${cleaned.take(ERROR_BODY_PREVIEW)}")
        return AiRequestException(
            "HTTP ${response.code}" + (if (cleaned.isEmpty()) "" else "：$cleaned"),
        )
    }

    /** 从 error 对象里提取人类可读信息：message → code → 兜底 null。 */
    private fun readableErrorMessage(error: JsonObject): String? {
        error["message"]?.jsonPrimitive?.contentOrNull?.let { return it }
        error["code"]?.jsonPrimitive?.contentOrNull?.let { return it }
        return null
    }

    /** 字符串转 JSON 字面量（含引号与转义）。手写而不用序列化器：请求体是手工拼的 JSON。 */
    private fun jsonLiteral(value: String): String = buildString(value.length + 8) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private companion object {
        const val TAG = "AiChatClient"
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        const val DONE_MARKER = "[DONE]"
        /**
         * 推理模型的思考过程字段名（OpenAI 兼容端点通用叫法）。
         * 思考增量单独走 [AiStreamEvent.Reasoning]，不拼进正文，但也不丢弃——
         * 小米 MiMo v2.6 系列默认开思维链，思考阶段可能几十秒、正文一个字都没有。
         */
        const val REASONING_FIELD = "reasoning_content"
        /** 单次读取的字节上限：够大能减少系统调用，够小能保证增量及时上屏。 */
        const val READ_CHUNK_BYTES = 8L * 1024L
        /** 多行 data 拼接缓冲的上限：超过还解析不出来就判定为脏数据，避免无限增长。 */
        const val MAX_PENDING_DATA = 1024 * 1024
        /** 行分隔符（SSE 用 \n，个别网关用 \r\n，行尾的 \r 在 handleLine 里 trim 掉）。 */
        val NEWLINE: Byte = '\n'.code.toByte()
        /** 看门狗的汇报间隔：太密刷屏，太疏看不出「在动」。 */
        const val WATCHDOG_INTERVAL_MS = 5_000L
        /** 前几个事件把原始载荷打进日志：网关换格式时靠它一眼看穿。 */
        const val EVENT_PREVIEW_COUNT = 3
        /** 错误详情进日志/异常的最长字符数：足够定位问题，又不至于把整页 HTML 塞进来 */
        const val ERROR_BODY_PREVIEW = 200
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** 一条对话消息；role 取 OpenAI 兼容协议的 system / user / assistant。 */
data class AiMessage(val role: String, val content: String) {
    companion object {
        fun system(content: String): AiMessage = AiMessage(ROLE_SYSTEM, content)
        fun user(content: String): AiMessage = AiMessage(ROLE_USER, content)
    }
}

/** 角色常量（协议约定值，独立成常量便于构造处自解释）。 */
const val ROLE_SYSTEM = "system"
const val ROLE_USER = "user"

/**
 * 一次流式请求的实时进度，供看门狗协程定时汇报。
 *
 * 为什么需要它：读 socket 的协程会长时间阻塞在 `read()` 上，循环体里根本插不进心跳日志，
 * 出问题时日志只能看到「HTTP 200」然后就断了。把进度放在这个共享对象里，
 * 由读线程写、看门狗协程每几秒读一次打印，就能明确区分三种「卡住」：
 * 还没收到响应头 / 收到响应头但没数据 / 有数据但已经多久没新的了。
 *
 * 单写者（读线程）+ 单读者（看门狗），字段用 @Volatile 保证可见性即可，不必加锁。
 */
private class StreamProgress(val startedAt: Long) {
    @Volatile var statusCode = 0
    @Volatile var responseAt = 0L
    @Volatile var firstByteAt = 0L
    @Volatile var lastActivityAt = 0L
    @Volatile var totalBytes = 0L
    @Volatile var eventCount = 0
    @Volatile var deltaCount = 0
    @Volatile var reasoningCount = 0
    @Volatile var badEventCount = 0
    @Volatile var contentChars = 0
    @Volatile var reasoningChars = 0
    @Volatile var lastEventKind = "无"

    fun onResponse(code: Int) {
        statusCode = code
        responseAt = System.currentTimeMillis()
    }

    /** 响应头耗时（从发起请求算起）。 */
    fun responseCostMs(): Long = responseAt - startedAt

    /** 首字节日志用：响应头之后等了多久才有数据。 */
    fun firstByteDelayMs(): Long = firstByteAt - responseAt

    /** 记录一块字节；返回 true 表示这是首字节（调用方据此打一次性日志）。 */
    fun onBytes(count: Long): Boolean {
        totalBytes += count
        val now = System.currentTimeMillis()
        lastActivityAt = now
        lastEventKind = "响应体"
        if (firstByteAt == 0L) {
            firstByteAt = now
            return true
        }
        return false
    }

    /** 记录一个解析成功的事件（正文 / 思考的字数各自累加）。 */
    fun onEvent(contentLength: Int, reasoningLength: Int) {
        eventCount++
        lastActivityAt = System.currentTimeMillis()
        if (contentLength > 0) {
            deltaCount++
            contentChars += contentLength
            lastEventKind = "正文"
        }
        if (reasoningLength > 0) {
            reasoningCount++
            reasoningChars += reasoningLength
            // 同一帧里正文和思考都有时以正文为准
            if (contentLength == 0) lastEventKind = "思考"
        }
    }

    fun onBadEvent() {
        badEventCount++
        lastActivityAt = System.currentTimeMillis()
        lastEventKind = "无法解析的数据"
    }

    /** 看门狗每隔几秒调用一次，产出一行人类可读的状态。 */
    fun describe(): String {
        val now = System.currentTimeMillis()
        val elapsed = (now - startedAt) / 1000
        return when {
            responseAt == 0L ->
                "等待响应头…已 ${elapsed}s（请求已发出，服务端还没回 HTTP 头）"
            firstByteAt == 0L ->
                "已收到 HTTP $statusCode，等待首字节…响应头后已 ${(now - responseAt) / 1000}s" +
                    "（服务端在思考，或网关在缓冲）"
            else -> buildString {
                append("进行中 ${elapsed}s：正文 $contentChars 字 / 思考 $reasoningChars 字")
                append("（正文增量 $deltaCount / 思考增量 $reasoningCount / 事件 $eventCount")
                if (badEventCount > 0) append(" / 解析失败 $badEventCount")
                append("），最后数据 ${(now - lastActivityAt) / 1000}s 前（$lastEventKind）")
            }
        }
    }
}

/** 流式响应事件。 */
sealed interface AiStreamEvent {
    /** 一段正文增量（空串的增量不会产出）。 */
    data class Delta(val text: String) : AiStreamEvent

    /**
     * 一段「思考过程」增量（推理模型的 `reasoning_content`）。
     *
     * 刻意与 [Delta] 分开：思考过程不是答案，不能拼进正文渲染；但也不能像以前那样
     * 直接丢掉——小米 MiMo 的 v2.6 系列默认就开着思维链，思考阶段可能持续几十秒，
     * 期间正文一个字都没有，全丢掉的表现就是「界面完全空白、像卡死」。
     * 上层把多条 [text] 攒起来放进可折叠的「思考块」里展示，与正文分区呈现。
     */
    data class Reasoning(val text: String) : AiStreamEvent

    /** 正常结束，[text] 是完整回答（与各次 Delta 拼接结果一致，免去调用方再拼）。 */
    data class Done(val text: String) : AiStreamEvent
}

/** AI 请求失败；message 已整理为可直接 toast/展示的中文描述。 */
class AiRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 共享的 AI 专用 OkHttpClient：复用爱发电客户端的连接池与线程池（newBuilder 不复制池），
 * 只放宽超时 —— 连接保持常规 15s，读/写放宽到 5 分钟以容纳推理模型的长时间输出。
 * 测试场景（mockwebserver）应注入自己的 client，不依赖这个全局单例。
 */
val sharedAiHttpClient: OkHttpClient by lazy {
    sharedAfdianHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()
}
