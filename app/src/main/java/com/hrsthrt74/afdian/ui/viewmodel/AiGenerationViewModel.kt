package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.data.ai.AiChatClient
import com.hrsthrt74.afdian.data.ai.AiHistoryStore
import com.hrsthrt74.afdian.data.ai.AiMessage
import com.hrsthrt74.afdian.data.ai.sharedAiHttpClient
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/*
 * AI 生成的 ViewModel 层：把可测内核 [AiGenerationEngine] 接到 Android 依赖上，
 * 并把用户操作收成五个事件方法。本文件同时定义这层的对外类型：
 * [AiPhase]（阶段）、[AiGenerationUiState]（单一状态快照）、[AiGenerationRequest]（发车输入）。
 *
 * 分工回顾：
 * - `AiGenerationEngine.kt`：状态机、流收集、100ms 节流、思考计时、落盘时机（纯逻辑，可 JVM 单测）；
 * - 本文件：用真实依赖（SettingsStore / AiChatClient / AiHistoryStore）装配引擎，暴露事件；
 * - `ui/components/ai/AiSheet.kt`：只按 [AiGenerationUiState] 渲染、把点击转成事件。
 */

/**
 * AI 生成任务的阶段。
 *
 * 「未开始」用可空变量（null）而不是加 Idle 枚举值：底栏刚打开/等发送时的渲染分支统一走 null，
 * when 里少一个永不展示的状态。
 */
internal enum class AiPhase {
    /** 正在拉取数据 / 构建提示词（全量数据接口可能要翻几页）。 */
    Preparing,

    /** 模型正在流式输出。 */
    Streaming,

    /** 正常完成。 */
    Success,

    /** 失败（网络/鉴权/配置缺失），[AiGenerationUiState.errorMessage] 有可读原因。 */
    Failed,

    /** 用户点了停止：保留已生成的部分，可重新生成。 */
    Stopped,
}

/**
 * 一次生成任务的不可变状态快照——UI 唯一的状态来源。
 *
 * answer / reasoning / thinkingSeconds 是并行存在的数据（正文与思考同时在长），
 * 所以用 data class 平铺而不是 sealed 分支；phase 单独承载「加载 / 失败 / 停止」的显式建模。
 *
 * @param phase 当前阶段；null = 还没发车（底栏刚打开、composer 等用户点发送）
 * @param attempt 渲染代数：每次打开 / 发送 / 重试 +1。UI 用它当 `remember(attempt)` 的 key
 *   重建流式 Markdown 渲染状态——上一轮的正文绝不残留到这一轮
 * @param answer 已收到的正文全文（含被停止时的半截）；流式渲染不读它（走增量流），复制/采用/落盘才读
 * @param reasoning 思考过程原文，只在思考块以灰字展示
 * @param thinkingSeconds 思考用时（秒）；0 = 还在思考 / 没有思考
 * @param errorMessage 失败原因，仅 [AiPhase.Failed] 时有意义
 */
internal data class AiGenerationUiState(
    val phase: AiPhase? = null,
    val attempt: Int = 0,
    val answer: String = "",
    val reasoning: String = "",
    val thinkingSeconds: Int = 0,
    val errorMessage: String? = null,
)

/**
 * 发车输入：一次生成任务所需的全部「调用方提供物」。
 *
 * 把原来 AiSheet 的 title / scene / buildPrompt / historyQuery 四个散参收成一处，
 * 并把「问题摘要落盘时才求值」的隐式契约显式化。
 *
 * @param title 底栏标题，如「AI 收入分析」，日志与 AI 记录都用它
 * @param scene AI 记录的场景标识（[com.hrsthrt74.afdian.data.ai.AiHistoryEntry.SCENE_INCOME]）
 * @param resolveHistoryQuery 「问题摘要」的取值方式：**每次读取时都求值**——
 *   发车后底栏气泡显示它、成功后落盘也存它。时间尺度是运行时 UI 状态，
 *   提前传 String 会拿到底栏刚打开时的旧值。返回值不应为空白
 * @param buildPrompt 数据拉取 + 提示词构建；数据没就位时可以在这里挂起（底栏显示拉取提示）。
 *   隐私语义：只有底栏真正打开并发车后它才会执行
 */
internal data class AiGenerationRequest(
    val title: String,
    val scene: String,
    val resolveHistoryQuery: () -> String,
    val buildPrompt: suspend () -> String,
)

/**
 * AI 生成的 ViewModel：会话作用域内持有生成状态机，负责装配真实数据层依赖。
 *
 * 作用域注意：由 AiResultSheet 以 `viewModel(...)` 获取，落在会话级 ViewModelStore
 * （见 MainActivity 关于「ViewModel 作用域必须包在最外层」的约定）。跨页面存活，
 * 所以 [onSheetOpened] 必重置、[onSheetClosed] 必断流——「关底栏即取消请求」是隐私红线。
 *
 * 旋转行为与旧实现一致：DialogState（remember）在重建后复位、底栏关闭 → onSheetClosed 断流，
 * 即「旋转 = 中断」。完整旋转存活（rememberSaveable + 渲染状态重放）留作后续增强。
 */
internal class AiGenerationViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val historyStore = AiHistoryStore(application)

    /** 可测内核：状态机 + 流收集 + 节流。这里只负责给它喂真实依赖。 */
    private val engine = AiGenerationEngine(
        // 读 AI 配置（含解密后的 Key，仅在发请求前用，绝不进日志）
        loadConfig = { settingsStore.aiConfig() },
        // 打开一条 OpenAI 兼容的流式请求（SSE 解析见 AiChatClient）
        openStream = { config, prompt ->
            AiChatClient(sharedAiHttpClient, config).stream(listOf(AiMessage.user(prompt)))
        },
        // 成功后落盘 AI 记录（内部已切 Dispatchers.IO）
        saveHistory = { entry -> historyStore.record(entry) },
    )

    /** 单一状态快照：UI 只读它渲染。 */
    val uiState: StateFlow<AiGenerationUiState> = engine.state

    /**
     * 当前配置的模型名（不含 Key）：底栏还没发车时用它说明「将使用谁分析」。
     *
     * 走 [SettingsStore.aiConfigDraft] 而不是 [SettingsStore.aiConfig]：
     * 这里只要展示用的模型名，没必要为了显示一行字去解密 API Key。
     * 初值空串——DataStore 首帧还没读到时 UI 侧用兜底文案，不会闪出半截句子。
     */
    val modelName: StateFlow<String> = settingsStore.aiConfigDraft
        .map { it.model }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * 节流后的正文增量流：UI 用它喂流式 Markdown 渲染。
     *
     * 只装增量（不是全量），UI 在 `LaunchedEffect(uiState.attempt)` 里收集并 `mdState.append`。
     * 契约：发车后的首个挂起点在任何增量产生之前（读配置/构建提示词都是真挂起），
     * 保证 UI 的收集器先于首个增量完成注册，增量不会因「无人订阅」被丢掉。
     */
    val answerDeltas: SharedFlow<String> = engine.answerDeltas

    /**
     * 打开底栏：重置到干净的待发状态。
     *
     * @param autoStart true = 没有提问区（打开即发）；false = composer 模式，等用户点发送
     */
    fun onSheetOpened(request: AiGenerationRequest, autoStart: Boolean) {
        if (autoStart) {
            engine.start(viewModelScope, request)
        } else {
            engine.reset()
        }
    }

    /** composer 的「发送」：清空上一轮内容并发车。 */
    fun onSend(request: AiGenerationRequest) {
        engine.start(viewModelScope, request)
    }

    /** 失败/停止后的「重试 / 重新生成」：与发送同一动作，语义留给按钮文案区分。 */
    fun onRetry(request: AiGenerationRequest) {
        engine.start(viewModelScope, request)
    }

    /** 手动停止：置 [AiPhase.Stopped] 并保留已生成部分。 */
    fun onStop() {
        engine.stop()
    }

    /** 关闭底栏：只断流、不改阶段（关闭不需要在 UI 上表现为「已停止」）。隐私红线：关即取消。 */
    fun onSheetClosed() {
        engine.cancel()
    }
}
