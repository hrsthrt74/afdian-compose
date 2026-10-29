package com.hrsthrt74.afdian.ui.viewmodel

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.ai.AiConfig
import com.hrsthrt74.afdian.data.ai.AiHistoryEntry
import com.hrsthrt74.afdian.data.ai.AiStreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * AI 生成的可测内核：状态机 + 流收集 + 增量节流 + 思考计时 + 落盘时机。
 *
 * 与 [AiGenerationViewModel]（Android 装配壳）的分工：
 * - 这里：一次生成任务的全部编排逻辑，依赖以 lambda 注入（[loadConfig] / [openStream] /
 *   [saveHistory] / [clock]），不碰 Context、不碰 OkHttp，可在 JVM 单测里完整驱动；
 * - 那里：把 SettingsStore / AiChatClient / AiHistoryStore 接进来，暴露 UI 事件方法。
 *
 * 状态用 [MutableStateFlow] 而不是 Compose `mutableStateOf`：本类是纯 Kotlin，
 * 不依赖 Compose 运行时；细粒度重组由 UI 侧对 [AiGenerationUiState] 字段的拆分读取保证
 * （思考块只读 reasoning/thinkingSeconds，Markdown 只吃增量流）。
 */

/**
 * 一次 AI 生成任务的运行时编排器。
 *
 * 对外只暴露只读状态（[state] / [answerDeltas]）与四个动作（[reset] / [start] / [stop] / [cancel]），
 * 调用方无法把状态改坏。
 *
 * @param loadConfig 读取一次 AI 请求的完整配置（含解密 Key）；null = 尚未配置
 * @param openStream 打开一条流式请求（若干 Delta/Reasoning → 一个 Done）
 * @param saveHistory 成功后落盘一条 AI 记录
 * @param clock 取当前毫秒时间戳；单测注入假时钟以精确断言节流窗口与思考用时
 */
internal class AiGenerationEngine(
    private val loadConfig: suspend () -> AiConfig?,
    private val openStream: (config: AiConfig, prompt: String) -> Flow<AiStreamEvent>,
    private val saveHistory: suspend (AiHistoryEntry) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val _state = MutableStateFlow(AiGenerationUiState())

    /** 单一状态快照。 */
    val state: StateFlow<AiGenerationUiState> = _state.asStateFlow()

    private val _answerDeltas = MutableSharedFlow<String>(
        // 增量是用户正文，绝不丢字：缓冲满时反压上游（挂起 emit），不用 DROP
        extraBufferCapacity = DELTA_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    /**
     * 节流后的正文增量流。只装增量，UI 换渲染代数（attempt）时重建 Markdown 渲染状态，
     * 不重放历史增量。
     */
    val answerDeltas: SharedFlow<String> = _answerDeltas.asSharedFlow()

    /** 当前任务协程：重试 / 重发会先取消上一次。 */
    private var job: Job? = null

    // 思考起始时刻：用字段而不是协程局部变量，用户中途停止 / 失败时也要能定格用时。
    // 0 = 思考尚未开始（真实时钟是 epoch 毫秒，不会是 0；单测用假时钟时避免从 0 起跳）
    private var thinkingStartedAt = 0L

    // 日志里带上标题，出问题时能一眼看出是哪次任务
    private var currentTitle: String = ""

    /**
     * 重置到干净的待发状态：清空上一轮全部内容、渲染代数 +1、不发请求。
     * composer 模式打开底栏时用（等用户点发送再发车）。
     */
    fun reset() {
        job?.cancel()
        job = null
        wipe(phase = null)
    }

    /**
     * 发车：同步进入「已发车」状态（attempt+1、清空、Preparing），然后启动任务协程。
     *
     * 状态同步写入而不是放进协程里：点「发送」的同一帧 UI 就要看到阶段切换与渲染代数更新；
     * 协程由调用方的 scope（viewModelScope = Main.immediate）启动，任务体首个挂起点在
     * 读配置——这保证任何增量都产生在 UI 收集器注册之后（见 answerDeltas 的契约）。
     *
     * @param scope 任务协程的归属；UI 传 viewModelScope，单测传 TestScope/runBlocking scope
     */
    fun start(scope: CoroutineScope, request: AiGenerationRequest) {
        job?.cancel()
        currentTitle = request.title
        wipe(phase = AiPhase.Preparing)
        job = scope.launch { runBody(request) }
    }

    /**
     * 手动停止：置 [AiPhase.Stopped] 保留半截结果（收集协程因取消抛 CancellationException 跳出）。
     */
    fun stop() {
        val s = _state.value
        AppLog.d(
            TAG,
            "用户停止：$currentTitle（正文 ${s.answer.length} 字，思考 ${s.reasoning.length} 字）",
        )
        job?.cancel()
        job = null
        // 思考到一半被停：用时同样定格，标题切到「已思考（用时 N 秒）」
        freezeThinkingDuration()
        _state.update { it.copy(phase = AiPhase.Stopped) }
    }

    /** 只断流、不改阶段：底栏关闭时用（关闭不需要在 UI 上表现为「已停止」）。 */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /**
     * 一次生成任务的完整流程：读配置 → 构建提示词 → 流式收集 → 落盘。
     *
     * 抽成独立 suspend 方法（而不是藏在 launch 里）是为了让单测能直接、顺序地驱动它，
     * 不需要任何虚拟时间调度。[start] 只负责把它放进协程。
     */
    internal suspend fun runBody(request: AiGenerationRequest) {
        currentTitle = request.title
        // 直接调用（单测）时 [start] 的同步准备没跑过，这里补齐；经 start 进来则是幂等覆盖
        _state.update { it.copy(phase = AiPhase.Preparing, errorMessage = null) }
        // 准备阶段可能很长（全量数据要翻页），先落一条日志，卡住时能看出卡在拉数据这一步
        AppLog.d(TAG, "开始准备：${request.title}（拉取数据并组装提示词）")
        try {
            val config = loadConfig()
                ?: error("尚未配置 AI 服务，请先到「设置 → AI 助手」完成配置")
            val prompt = request.buildPrompt()
            _state.update { it.copy(phase = AiPhase.Streaming) }
            AppLog.d(TAG, "AI 任务开始：${request.title}，提示词 ${prompt.length} 字符")

            // ── 增量节流 ──
            // Markdown 每 append 一次都要重解析整篇、思考块每改一次都要重排版，
            // 逐包刷新会让长报告卡住主线程；攒到 100ms 一并刷，观感上仍是流式。
            // 刷出时机 =「下一次增量到来时检查窗口」，任务结束时 force 收尾补上尾部。
            val pendingAnswer = StringBuilder()
            val pendingReasoning = StringBuilder()
            // 首包立即刷出：把上次刷出时刻初始化到窗口之前，等价于「从未刷过」
            var lastFlush = clock() - FLUSH_MS

            // append/emit 都是 suspend（状态流同步、SharedFlow 反压），故 flush 也声明为 suspend
            suspend fun flush(force: Boolean = false) {
                if (pendingAnswer.isEmpty() && pendingReasoning.isEmpty()) return
                val now = clock()
                if (!force && now - lastFlush < FLUSH_MS) return
                if (pendingAnswer.isNotEmpty()) {
                    _answerDeltas.emit(pendingAnswer.toString())
                    pendingAnswer.clear()
                }
                if (pendingReasoning.isNotEmpty()) {
                    _state.update { it.copy(reasoning = it.reasoning + pendingReasoning.toString()) }
                    pendingReasoning.clear()
                }
                lastFlush = now
            }

            openStream(config, prompt).collect { event ->
                when (event) {
                    is AiStreamEvent.Delta -> {
                        // 正文第一条增量 = 思考结束：此刻定格用时，思考块随之自动收起
                        freezeThinkingDuration()
                        _state.update { it.copy(answer = it.answer + event.text) }
                        pendingAnswer.append(event.text)
                        flush()
                    }
                    // 思考过程单独攒一份原文（不进正文）：DeepSeek 式「可展开的思考块」要能回看
                    is AiStreamEvent.Reasoning -> {
                        if (thinkingStartedAt == 0L) thinkingStartedAt = clock()
                        pendingReasoning.append(event.text)
                        flush()
                    }
                    is AiStreamEvent.Done -> {
                        // 服务端拼接结果更权威，覆盖本地累加值
                        _state.update { it.copy(answer = event.text) }
                    }
                }
            }

            // 收尾把缓冲里剩下的补上（Done 之后再刷一次，保证渲染内容与 answer 一致）
            flush(force = true)
            freezeThinkingDuration()
            _state.update { it.copy(phase = AiPhase.Success) }
            val done = _state.value
            AppLog.d(
                TAG,
                "AI 任务完成：${request.title}，正文 ${done.answer.length} 字，思考 ${done.reasoning.length} 字",
            )
            recordHistory(config, request)
        } catch (e: CancellationException) {
            // 停止/关闭导致的取消不算失败：已生成部分保留（Stopped 态由 stop() 设置）
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "AI 任务失败：${request.title}", e)
            // 失败也把思考用时定格，标题不会一直停在「正在思考」
            freezeThinkingDuration()
            _state.update { it.copy(phase = AiPhase.Failed, errorMessage = e.message ?: "未知错误") }
        }
    }

    /**
     * 成功后落盘一条 AI 记录。
     *
     * 只在 [AiPhase.Success] 路径调用：「停止」是用户主动打断的半截内容、失败则根本没有正文，
     * 把它们记进历史只会让「回看」变成翻一堆残稿。正文为空同样不落盘。
     *
     * 写盘放在 NonCancellable 里：用户看到成功立刻点「关闭」会取消本协程，
     * 但这一次写盘不能被掐断，否则记录会偶发性丢失。
     */
    private suspend fun recordHistory(config: AiConfig, request: AiGenerationRequest) {
        val snapshot = _state.value
        if (snapshot.answer.isBlank()) return
        withContext(NonCancellable) {
            runCatching {
                saveHistory(
                    AiHistoryEntry.create(
                        scene = request.scene,
                        title = request.title,
                        // 落盘那一刻才求值：用户可能刚切过时间尺度
                        query = request.resolveHistoryQuery(),
                        answer = snapshot.answer,
                        reasoning = snapshot.reasoning,
                        thinkingSeconds = snapshot.thinkingSeconds,
                        model = config.model,
                        provider = config.preset.storageValue,
                    ),
                )
            }.onFailure { AppLog.w(TAG, "AI 记录落盘失败：${request.title}", it) }
        }
    }

    /** 思考结束（或被打断）：把起始时刻换算成用时定格下来，只定格一次。 */
    private fun freezeThinkingDuration() {
        val startedAt = thinkingStartedAt
        if (startedAt > 0L && _state.value.thinkingSeconds == 0) {
            _state.update { it.copy(thinkingSeconds = elapsedSeconds(startedAt, clock())) }
        }
    }

    /**
     * 进入新一轮的干净状态：清空正文/思考/错误、渲染代数 +1。
     *
     * attempt 递增是「上一轮正文绝不残留」的关键：UI 用它当 `remember(attempt)` 的 key
     * 重建流式 Markdown 渲染状态。每次打开 / 发送 / 重试都走这里。
     */
    private fun wipe(phase: AiPhase?) {
        thinkingStartedAt = 0L
        _state.value = AiGenerationUiState(
            phase = phase,
            attempt = _state.value.attempt + 1,
        )
    }
}

/** 起始时刻到当前时刻的整秒数（至少 1 秒：刚开就结束也别显示「用时 0 秒」）。 */
internal fun elapsedSeconds(startedAt: Long, now: Long): Int =
    ((now - startedAt) / 1000).toInt().coerceAtLeast(1)

private const val TAG = "AiGeneration"

/** 流式内容（正文 + 思考）的刷新节流间隔：太密会反复重解析/重排版，太疏会显得一卡一卡。 */
private const val FLUSH_MS = 100L

/** 增量流的缓冲容量：攒住 UI 一时跟不上的增量（满则反压，不丢字）。 */
private const val DELTA_BUFFER_CAPACITY = 64
