package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.ai.AiConfig
import com.hrsthrt74.afdian.data.ai.AiHistoryEntry
import com.hrsthrt74.afdian.data.ai.AiProviderPreset
import com.hrsthrt74.afdian.data.ai.AiStreamEvent
import com.hrsthrt74.afdian.ui.viewmodel.AiGenerationEngine
import com.hrsthrt74.afdian.ui.viewmodel.AiGenerationRequest
import com.hrsthrt74.afdian.ui.viewmodel.AiPhase
import com.hrsthrt74.afdian.ui.viewmodel.elapsedSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiGenerationEngine] 的纯逻辑回归测试。
 *
 * 覆盖：阶段转移（Preparing→Streaming→Success/Failed/Stopped）、100ms 增量节流的刷出时机
 * 与 force 收尾、思考用时只定格一次、落盘仅在 Success 且正文非空、问题摘要落盘那一刻才求值。
 *
 * 全部用假时钟 + 测试侧可控的 Flow 驱动，不依赖协程虚拟时间调度：
 * [AiGenerationEngine.runBody] 可直接顺序调用，节奏完全由测试掌控。
 */
class AiGenerationEngineTest {

    /** 测试用配置：假 Key，绝不发真实请求（openStream 全部是假流）。 */
    private val testConfig = AiConfig(
        preset = AiProviderPreset.DeepSeek,
        baseUrl = "https://api.deepseek.com/v1",
        model = "deepseek-chat",
        apiKey = "unit-test-fake-key",
    )

    /** 构造一份默认发车输入；问题摘要默认恒定值。 */
    private fun request(
        resolveHistoryQuery: () -> String = { "按日 · 近 30 天" },
    ) = AiGenerationRequest(
        title = "AI 收入分析",
        scene = AiHistoryEntry.SCENE_INCOME,
        resolveHistoryQuery = resolveHistoryQuery,
        buildPrompt = { "提示词" },
    )

    /** 组一台引擎；各依赖默认「成功、无副作用」，测试按需覆盖。 */
    private fun engine(
        loadConfig: suspend () -> AiConfig? = { testConfig },
        openStream: (AiConfig, String) -> Flow<AiStreamEvent> = { _, _ -> flowOf(AiStreamEvent.Done("答")) },
        saveHistory: suspend (AiHistoryEntry) -> Unit = { },
        clock: () -> Long = { 1_000L },
    ) = AiGenerationEngine(
        loadConfig = loadConfig,
        openStream = openStream,
        saveHistory = saveHistory,
        clock = clock,
    )

    /**
     * 挂上增量收集器：UNDISPATCHED 立即订阅，保证任何增量都不会因「无人订阅」被丢掉或反压挂起。
     * 返回收集任务（用完记得 cancel，否则 runBlocking 会一直等它）。
     */
    private fun CoroutineScope.collectDeltas(
        engine: AiGenerationEngine,
        into: MutableList<String>,
    ): Job = launch(start = CoroutineStart.UNDISPATCHED) {
        engine.answerDeltas.collect { into += it }
    }

    // ─────────────────────────── 阶段转移 ───────────────────────────

    @Test
    fun successTransitionsAndRecordsHistory() = runBlocking {
        val saved = mutableListOf<AiHistoryEntry>()
        val deltas = mutableListOf<String>()
        val engine = engine(
            openStream = { _, _ ->
                flowOf(
                    AiStreamEvent.Reasoning("先想想"),
                    AiStreamEvent.Delta("答"),
                    AiStreamEvent.Done("答"),
                )
            },
            saveHistory = { saved += it },
        )
        val collector = collectDeltas(engine, deltas)

        engine.runBody(request())
        yield()
        collector.cancel()

        val s = engine.state.value
        assertEquals(AiPhase.Success, s.phase)
        assertEquals("答", s.answer)
        assertEquals("先想想", s.reasoning)
        assertEquals(listOf("答"), deltas)
        assertEquals("成功后应落盘一条记录", 1, saved.size)
        assertEquals("AI 收入分析", saved.single().title)
        assertEquals(AiHistoryEntry.SCENE_INCOME, saved.single().scene)
        assertEquals("deepseek-chat", saved.single().model)
    }

    @Test
    fun streamFailureEndsAsFailedAndKeepsPartialAnswer() = runBlocking {
        val saved = mutableListOf<AiHistoryEntry>()
        val deltas = mutableListOf<String>()
        val engine = engine(
            openStream = { _, _ ->
                flow {
                    emit(AiStreamEvent.Delta("半截"))
                    throw RuntimeException("网络断了")
                }
            },
            saveHistory = { saved += it },
        )
        val collector = collectDeltas(engine, deltas)

        engine.runBody(request())
        yield()
        collector.cancel()

        val s = engine.state.value
        assertEquals(AiPhase.Failed, s.phase)
        assertEquals("网络断了", s.errorMessage)
        assertEquals("失败也保留半截正文", "半截", s.answer)
        assertTrue("失败不落盘", saved.isEmpty())
    }

    @Test
    fun missingConfigFailsWithReadableMessage() = runBlocking {
        val engine = engine(loadConfig = { null })

        engine.runBody(request())

        val s = engine.state.value
        assertEquals(AiPhase.Failed, s.phase)
        assertEquals("尚未配置 AI 服务，请先到「设置 → AI 助手」完成配置", s.errorMessage)
    }

    @Test
    fun stopMarksStoppedAndKeepsPartialAnswer() = runBlocking {
        // 永不结束的流：模拟流式中途被用户停止
        val stream = MutableSharedFlow<AiStreamEvent>()
        val deltas = mutableListOf<String>()
        val engine = engine(openStream = { _, _ -> stream })
        val collector = collectDeltas(engine, deltas)

        engine.start(this, request())
        yield() // 让任务跑起来、挂起在流式收集上
        stream.emit(AiStreamEvent.Delta("半截"))
        yield() // 让收集器处理这条增量
        engine.stop()
        yield()
        collector.cancel()

        val s = engine.state.value
        assertEquals(AiPhase.Stopped, s.phase)
        assertEquals("停止保留半截正文", "半截", s.answer)
    }

    // ─────────────────────────── 100ms 节流 ───────────────────────────

    @Test
    fun answerDeltasFlushedIn100msWindows() = runBlocking {
        var now = 1_000L
        val deltas = mutableListOf<String>()
        val engine = engine(
            openStream = { _, _ ->
                flow {
                    emit(AiStreamEvent.Delta("a")) // t=1000：首包，立即刷出
                    now = 1_050
                    emit(AiStreamEvent.Delta("b")) // t=1050：窗口内，攒着
                    now = 1_150
                    emit(AiStreamEvent.Delta("c")) // t=1150：过窗，连同 b 拼成一批刷出
                    now = 1_160
                    emit(AiStreamEvent.Done("abc"))
                }
            },
            clock = { now },
        )
        val collector = collectDeltas(engine, deltas)

        engine.runBody(request())
        yield() // 让收集器消费完缓冲里的增量
        collector.cancel()

        assertEquals(listOf("a", "bc"), deltas)
    }

    @Test
    fun forceFlushDrainsTailBufferAtEnd() = runBlocking {
        // 任务在窗口内结束：尾部增量必须由 force flush 补上，不能丢
        var now = 2_000L
        val deltas = mutableListOf<String>()
        val engine = engine(
            openStream = { _, _ ->
                flow {
                    emit(AiStreamEvent.Delta("a")) // t=2000 立即刷
                    now = 2_050
                    emit(AiStreamEvent.Delta("尾")) // t=2050 窗口内攒着，之后流就结束了
                    emit(AiStreamEvent.Done("a尾"))
                }
            },
            clock = { now },
        )
        val collector = collectDeltas(engine, deltas)

        engine.runBody(request())
        yield()
        collector.cancel()

        assertEquals("收尾 force flush 要把尾部增量刷出来", listOf("a", "尾"), deltas)
    }

    // ─────────────────────────── 思考计时 ───────────────────────────

    @Test
    fun thinkingDurationFrozenOnlyOnce() = runBlocking {
        var now = 5_000L
        val deltas = mutableListOf<String>()
        val engine = engine(
            openStream = { _, _ ->
                flow {
                    emit(AiStreamEvent.Reasoning("想")) // t=5000 思考开始
                    now = 8_000
                    emit(AiStreamEvent.Delta("答")) // t=8000 正文开始 = 思考结束，定格 3 秒
                    now = 9_000
                    emit(AiStreamEvent.Delta("答2")) // 之后的时刻不该再改用时
                    now = 60_000
                    emit(AiStreamEvent.Done("答答2"))
                }
            },
            clock = { now },
        )
        val collector = collectDeltas(engine, deltas)

        engine.runBody(request())
        yield()
        collector.cancel()

        assertEquals("思考用时只在结束那一刻定格一次", 3, engine.state.value.thinkingSeconds)
    }

    @Test
    fun thinkingDurationFrozenEvenOnFailure() = runBlocking {
        var now = 1_000L
        val engine = engine(
            openStream = { _, _ ->
                flow {
                    emit(AiStreamEvent.Reasoning("想")) // t=1000
                    now = 4_500
                    throw RuntimeException("崩了") // t=4500 失败，用时也要定格
                }
            },
            clock = { now },
        )

        engine.runBody(request())

        val s = engine.state.value
        assertEquals(AiPhase.Failed, s.phase)
        // (4500-1000)/1000 = 3 秒：失败时标题不能一直停在「正在思考」
        assertEquals(3, s.thinkingSeconds)
    }

    @Test
    fun elapsedSecondsNeverBelowOne() {
        assertEquals(1, elapsedSeconds(1_000L, 1_000L))
        assertEquals(1, elapsedSeconds(1_000L, 1_500L))
        assertEquals(3, elapsedSeconds(1_000L, 4_000L))
    }

    // ─────────────────────────── 落盘时机 ───────────────────────────

    @Test
    fun blankAnswerNotRecordedEvenOnSuccess() = runBlocking {
        val saved = mutableListOf<AiHistoryEntry>()
        val engine = engine(
            // 只有思考没有正文：Done 结果为空
            openStream = { _, _ -> flowOf(AiStreamEvent.Reasoning("只有思考"), AiStreamEvent.Done("")) },
            saveHistory = { saved += it },
        )

        engine.runBody(request())

        assertEquals(AiPhase.Success, engine.state.value.phase)
        assertTrue("正文为空不落盘，回看不该出现空记录", saved.isEmpty())
    }

    @Test
    fun historyQueryResolvedOnlyAtRecordTime() = runBlocking {
        var queryCalls = 0
        val saved = mutableListOf<AiHistoryEntry>()
        val deltas = mutableListOf<String>()
        val engine = engine(
            openStream = { _, _ ->
                flow {
                    assertEquals("发车与流式期间不该求值问题摘要", 0, queryCalls)
                    emit(AiStreamEvent.Delta("答"))
                    emit(AiStreamEvent.Done("答"))
                }
            },
            saveHistory = { saved += it },
        )
        val collector = collectDeltas(engine, deltas)

        engine.runBody(request(resolveHistoryQuery = { queryCalls++; "按日 · 近 30 天" }))
        yield()
        collector.cancel()

        assertEquals("问题摘要只在落盘那一刻求值", 1, queryCalls)
        assertEquals("按日 · 近 30 天", saved.single().query)
    }

    // ─────────────────────────── 状态重置 ───────────────────────────

    @Test
    fun resetClearsContentAndBumpsAttempt() = runBlocking {
        val deltas = mutableListOf<String>()
        val engine = engine(
            openStream = { _, _ -> flowOf(AiStreamEvent.Delta("旧正文"), AiStreamEvent.Done("旧正文")) },
        )
        val collector = collectDeltas(engine, deltas)
        engine.runBody(request())
        yield()
        collector.cancel()
        val attemptAfterRun = engine.state.value.attempt

        engine.reset()

        val s = engine.state.value
        assertNull("重置后回到未发车", s.phase)
        assertEquals("", s.answer)
        assertEquals("", s.reasoning)
        assertEquals(0, s.thinkingSeconds)
        assertEquals("渲染代数 +1，UI 以此重建渲染状态", attemptAfterRun + 1, s.attempt)
    }

    @Test
    fun startWipesPreviousRound() = runBlocking {
        val engine = engine(
            openStream = { _, _ -> flowOf(AiStreamEvent.Done("答")) },
        )
        engine.runBody(request())
        val attemptAfterRun = engine.state.value.attempt

        // 第二轮发车：第一轮的正文不得残留
        engine.start(this, request())
        yield()

        val s = engine.state.value
        assertEquals(AiPhase.Success, s.phase)
        assertEquals("答", s.answer)
        assertEquals(attemptAfterRun + 1, s.attempt)
    }
}
