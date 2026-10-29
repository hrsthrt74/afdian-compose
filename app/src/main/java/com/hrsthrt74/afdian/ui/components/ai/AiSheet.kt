package com.hrsthrt74.afdian.ui.components.ai

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.ai.AiHistoryEntry
import com.hrsthrt74.afdian.data.ai.AiProviderPreset
import com.hrsthrt74.afdian.ui.components.basic.CardActionBar
import com.hrsthrt74.afdian.ui.components.basic.CardActionBarItem
import com.hrsthrt74.afdian.ui.components.basic.DialogState
import com.hrsthrt74.afdian.ui.components.relativeTime
import com.hrsthrt74.afdian.ui.viewmodel.AiGenerationRequest
import com.hrsthrt74.afdian.ui.viewmodel.AiGenerationUiState
import com.hrsthrt74.afdian.ui.viewmodel.AiGenerationViewModel
import com.hrsthrt74.afdian.ui.viewmodel.AiPhase
import com.kyant.shapes.Capsule
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/*
 * AI 底栏的 UI 层：只有「怎么画」和「用户点了什么」，没有一处网络请求或数据读写。
 *
 * 三块内容：
 * 1. AiSheetScaffold     —— 两个底栏共用的版式骨架；
 * 2. AiResultSheet       —— 现场生成的底栏：持有 AiGenerationViewModel，
 *                          把用户操作转成事件（onSend / onStop / …），按 UiState 渲染；
 * 3. AiHistoryDetailSheet —— 历史回看的底栏，数据由调用方传进来，本文件不查任何仓库。
 *
 * 想让「哪部分是 UI、哪部分是数据」一眼看清楚，就照这个分工读：
 * 何时发请求、何时取消、何时落盘、何时定格计时——一律在
 * `ui/viewmodel/AiGenerationEngine.kt`，本文件只有渲染与事件转发。
 */

// ─────────────────────────── 共享骨架 ───────────────────────────

/**
 * 两个 AI 底栏共用的版式骨架。
 *
 * 高度分两态，中间是连续过渡（不是硬切）：
 * - **紧凑**（还没发车）：不加任何高度约束，Sheet 自己 wrap 到内容高度；
 * - **展开**（发车后 / 历史回看）：高度直取内容区可用高度，底部动作栏永远贴在底边。
 *
 * 为什么要动画而不是「内容长多少算多少」（筛选 Sheet 的做法）：那边的终点永远是内容高度，
 * 内容多高 Sheet 多高就完事。这里要的是「点开始 → 立刻顶满」，终点是一个**定值**（满屏），
 * 而起点是内容高度，两者之间只能靠高度动画连起来。
 *
 * 动画值走 `height(动画 Dp)`：每帧都按新高度重新测量，对话区被逐渐放开、动作栏始终贴底；
 * `animateContentSize()` 做不到这一点（它只裁切一张已经按满高排好的画面，动作栏会被裁掉）。
 *
 * 动作栏是**叠在对话区之上**的：正文一路滚到栏底下，靠动作栏上方的「上透明 → 下 Sheet 底色」
 * 渐变淡出，而不是留一条永远空着的白边。所以滚动内容要自己留出 [AiSheetSpacing.ActionBarReserve]。
 *
 * 关闭入口由骨架统一提供：标题栏左上角的 X（`startAction`），两个底栏位置一致。
 * 动作区只放「对内容做什么」，不再承担「离开」。
 *
 * @param expanded true = 撑满内容区可用高度；false = 只占内容高度
 * @param scrollState 由调用方持有：生成侧要用它做「流式输出时自动贴底」，
 *   回看侧不需要，但共用同一个参数比让骨架返回状态更简单
 * @param content 对话区内容（已在可滚动容器内，不需要自己加滚动）
 * @param actions 底部动作条目（[CardActionBar] 的图标 + 文字列，按顺序等分整行）；
 *   空列表 = 该阶段没有动作可做，整条动作栏连胶囊底一起不渲染
 * @param actionsBackground 动作栏胶囊底色：默认「浮层内容器色」，
 *   主操作（「开始分析」）时由调用方换成主色，条目内容色要同步给 `onPrimary`
 */
@Composable
private fun AiSheetScaffold(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    scrollState: ScrollState,
    expanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    actions: List<CardActionBarItem>,
    actionsBackground: Color = MiuixTheme.colorScheme.secondaryContainer,
) {
    // Sheet 的 Window 是 edge-to-edge（miuix 给 Dialog 设了 decorFitsSystemWindows = false），
    // 内容会一直铺到手势条底下。动作区必须自己让开系统导航栏的高度，
    // 否则底部按钮会被手势条压住、点不着。
    val navigationBarPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    WindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        // 标题栏左侧：关闭。两个 AI 底栏唯一的关闭入口——动作区只放「对内容做什么」
        //（停止/重试/复制/采用/删除），「离开」统一收在标题栏，两个底栏位置一致
        startAction = {
            IconButton(onClick = onDismissRequest) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭")
            }
        },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 内容区可用高度（Sheet 自己已用 heightIn(max = 窗口高 - 顶栏) 限过一次）
            val fullHeight = maxHeight
            val density = LocalDensity.current
            // 紧凑态的自然高度：紧凑态不加高度约束，交给布局自己量，文案变化也跟得上
            var compactHeight by remember { mutableStateOf(0.dp) }
            val sheetHeight by animateDpAsState(
                targetValue = if (expanded) fullHeight else compactHeight,
                animationSpec = tween(SheetHeightAnimMs),
                label = "aiSheetHeight",
            )

            // Sheet 的底色：渐变的下端必须和它同色，否则动作栏上方会出现一道色带
            val sheetBackground = MiuixTheme.colorScheme.background

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 紧凑态不加约束：Sheet 自己 wrap 到内容高度
                    .then(if (expanded) Modifier.height(sheetHeight) else Modifier)
                    .onSizeChanged {
                        // 只在紧凑态记录：展开态的高度是动画值，记下来只会自我确认
                        if (!expanded) compactHeight = with(density) { it.height.toDp() }
                    },
            ) {
                // 对话区：铺满整块，正文可以一直滚到动作栏底下（底部留出动作栏高度的空白）
                // 水平不再额外加内边距：Sheet 自带 24dp insideMargin，再加就比标题更往内缩了
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .scrollEndHaptic()
                        .padding(
                            top = AiSheetSpacing.ContentTop,
                            // 有动作栏时，底部空白要留够「渐变 + 动作栏 + 手势条」，
                            // 这样滚到底的最后一行正好停在渐变结束处，而不是被动作栏盖住
                            bottom = AiSheetSpacing.ContentBottom +
                                if (actions.isEmpty()) {
                                    navigationBarPadding
                                } else {
                                    AiSheetSpacing.ActionBarReserve + navigationBarPadding
                                },
                        ),
                ) {
                    content()
                }
                // 底部「栏背景」层：上透明 → 下 Sheet 底色 的渐变 + 不透明的动作栏，
                // 叠在对话区之上。滚到这里的正文被渐变吃掉，不会跟动作栏硬切
                if (actions.isNotEmpty()) {
                    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        // 渐变段：用同色 + alpha=0 起步，避免插值出灰色（Color.Transparent 是透明黑）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AiSheetSpacing.ActionBarFade)
                                .background(
                                    Brush.verticalGradient(
                                        0f to sheetBackground.copy(alpha = 0f),
                                        1f to sheetBackground,
                                    ),
                                ),
                        )
                        // 动作栏段：不透明底，胶囊按钮浮在上面
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(sheetBackground)
                                .padding(
                                    bottom = AiSheetSpacing.ActionRowBottom + navigationBarPadding,
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 胶囊底默认取弹层内的容器色（与 AiStatusCard 的 overlayColors 同一档），
                                    // 既和 Sheet 背景分出层次，又不抢正文——动作栏是容器，不是按钮。
                                    // 唯一的例外是主操作「开始分析」：整条换成主色实心，这时它就是个真按钮
                                    .clip(Capsule())
                                    .background(actionsBackground),
                            ) {
                                CardActionBar(items = actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────── 现场生成 ───────────────────────────

/**
 * 提问侧（用户那一头）的形态：二选一。
 *
 * 两者的区别不只是外观，而是「谁决定什么时候发车」：
 * [Static] 打开即发，[Composer] 由用户点底部动作栏的「开始分析」才发。
 *
 * 为什么是 sealed 而不是两个可空参数（queryChip / composer）：
 * 旧 API 两个都传会静默进入 composer 模式，语义靠注释约定；收成互斥的两态后编译器替你守住。
 */
internal sealed interface AiQuestionSlot {
    /** 静态 chip：打开即发，气泡里只放一枚「问的是哪份数据」的胶囊。 */
    data class Static(val chip: String) : AiQuestionSlot

    /**
     * 提问区：给了就进入「先问后答」模式——对话区里摆条件选择器，
     * 用户点底部动作栏的「开始分析」才发请求。
     *
     * **只在还没发车时渲染**；一旦发车就收成一枚问题摘要 chip（与回看时同一版式），
     * 中途失败/停止也不回到选择器——那时重试用的就是当时那份条件。
     *
     * @param content 条件选择器（**不含发送按钮**：发送是动作栏的事）
     */
    data class Composer(
        val content: @Composable () -> Unit,
    ) : AiQuestionSlot
}

/**
 * 可复用的「AI 生成结果」底栏：对话式版式 + 停止 / 重试 / 复制 / 可选「采用」。
 *
 * 版式自上而下三段（与回看的 [AiHistoryDetailSheet] 完全一致）：
 * 1. **用户侧气泡**：右对齐的小气泡，说明「问的是哪份数据」（[AiQuestionSlot]）；
 * 2. **思考块**：推理模型的思考过程，灰字 + 左侧竖线，可点标题开合；思考中默认收成
 *    6 行小窗、思考内容在窗口里滚动（可展开为不限行数），正文一开始再收成一行
 *    「已思考（用时 N 秒）」，与 DeepSeek 的行为一致；
 * 3. **正文**：Markdown 直接铺在页面上——没有头像、没有气泡卡片，读长报告时可用宽度最大。
 *
 * 为什么是 Sheet 而不是 Dialog：分析报告动辄十几段，Dialog 的固定小窗装不下，
 * 只能在小区域里挤着滚；Sheet 能撑到全屏高度，阅读体验接近聊天页。
 *
 * 状态归属：全部业务状态在 [AiGenerationViewModel]（会话作用域，本函数内部获取）。
 * 本函数只做三件事：把用户操作转成事件、按 [AiGenerationUiState] 渲染、
 * 保留纯视觉的局部态（思考块开合、滚动位置）。
 *
 * 关闭入口只有一个：标题栏左上角的 X（[AiSheetScaffold] 的 startAction）。
 * 动作区只放「对内容做什么」——等待时「开始分析」，跑起来「停止」，
 * 结束「复制 / 重试」加可选的「采用」。
 *
 * 隐私语义：只有底栏真正打开时 [AiGenerationRequest.buildPrompt] 才执行
 * （数据才会被拉取并发送），调用方的确认弹窗负责提前把这一点告知用户。
 *
 * @param request 发车输入（标题、场景、提示词构建、问题摘要）；
 *   其内部 lambda 都是「调用时才求值」的晚绑定，调用方可在 remember 里构造一次
 * @param question 提问侧内容（静态 chip 或条件选择器）；null = 不显示（仍然打开即发）
 * @param preparingHint 拉数据阶段的自定义提示（如拉取进度）；null 用默认文案
 * @param onAdopt 可选的「采用」回调（成功后显示「采用」按钮，点击采用并关闭底栏）；
 *   缺省时成功态只给「复制」
 * @param onSheetVisibilityChanged 底栏开/关回调（如调用方借它启停数据预取）
 */
@Composable
internal fun AiResultSheet(
    dialogState: DialogState,
    request: AiGenerationRequest,
    question: AiQuestionSlot? = null,
    preparingHint: (@Composable () -> Unit)? = null,
    onAdopt: ((String) -> Unit)? = null,
    onSheetVisibilityChanged: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    // 生成状态机（会话作用域）：一个会话里只存在一个「AI 结果底栏」的场景，
    // 打开/关闭之间由 onSheetOpened / onSheetClosed 重置与断流，不会串台
    val viewModel: AiGenerationViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val uiState by viewModel.uiState.collectAsState()

    val phase = uiState.phase
    val thinkingSeconds = uiState.thinkingSeconds
    // 有没有正文压成布尔：answer 每个增量都会变，直接读它会让整段对话区（含 Markdown）
    // 跟着逐包重组；压成布尔后只在 0→1 那次失效
    val hasAnswer by remember { derivedStateOf { uiState.answer.isNotEmpty() } }

    // attempt（渲染代数）变化即换新的 ReferenceLinkHandler，强制重建流式渲染状态——
    // 上一轮的正文绝不残留到这一轮（StreamingMarkdownState 的 impl 是 internal 的，
    // 重建只能靠换 remember 的 key）
    val linkHandler = remember(uiState.attempt) { ReferenceLinkHandlerImpl() }
    val mdState = rememberStreamingMarkdownState(referenceLinkHandler = linkHandler)
    val scrollState = rememberScrollState()

    // ── 纯 UI 态：只影响怎么画，不影响数据 ──
    // 思考块展开态：思考中默认收成 6 行小窗；正文开始后自动收成一行标题，
    // 用户手动开合过则不再自动动
    var thinkingExpanded by remember { mutableStateOf(false) }
    var thinkingTouchedByUser by remember { mutableStateOf(false) }

    // ── 生命周期：打开/关闭 ──
    // 打开：重置状态机（无提问区则直接发车）；关闭：断流（隐私红线：关底栏即取消请求）
    LaunchedEffect(dialogState.show) {
        if (dialogState.show) {
            AppLog.d(TAG, "打开底栏：${request.title}")
            viewModel.onSheetOpened(request, autoStart = question !is AiQuestionSlot.Composer)
            onSheetVisibilityChanged?.invoke(true)
        } else {
            viewModel.onSheetClosed()
            onSheetVisibilityChanged?.invoke(false)
        }
    }
    // 渲染代数换代：重置思考块形态 + 重新订阅正文增量。
    // 此 effect 内禁止再改 attempt——自增与订阅共用一个 key 会变成重组死循环
    LaunchedEffect(uiState.attempt) {
        thinkingExpanded = false
        thinkingTouchedByUser = false
        viewModel.answerDeltas.collect { mdState.append(it) }
    }
    // 思考结束（用时定格）→ 自动定型；用户手动开合过就不再抢他的操作。
    // 有正文 = 正常进入回答：收成一行标题；没正文 = 停止/失败中断：展开让人看完它想了什么
    LaunchedEffect(thinkingSeconds) {
        if (thinkingSeconds > 0 && !thinkingTouchedByUser) {
            thinkingExpanded = hasAnswer
        }
    }
    // 流式输出时自动滚到底部：内容一变就跟到最底；用户手动上滑回看不受影响
    LaunchedEffect(phase) {
        if (phase == AiPhase.Streaming) {
            snapshotFlow { scrollState.maxValue }.collect { value ->
                scrollState.scrollTo(value)
            }
        }
    }

    // 没有拿到正文也没吐出思考：整段空白时最容易让人以为卡死，这里给一行灰字兜底
    val thinkingVisible = uiState.reasoning.isNotEmpty() ||
        (phase == AiPhase.Streaming && !hasAnswer)

    // 还没发车（等用户选范围）：只用来判断动作栏是不是那句主操作「开始分析」。
    // 高度不用管——Sheet 自己按内容 wrap，内容变多就长高
    val waitingToStart = phase == null && question is AiQuestionSlot.Composer

    AiSheetScaffold(
        show = dialogState.show,
        title = request.title,
        onDismissRequest = {
            // 遮罩点击 / 返回键：断流并关闭（LaunchedEffect 的 show=false 分支做兜底取消）
            AppLog.d(TAG, "关闭底栏：${request.title}")
            viewModel.onSheetClosed()
            dialogState.dismiss()
        },
        scrollState = scrollState,
        // 还没发车 ⇒ 只占内容高度；一点「开始分析」⇒ 立刻（280ms 内）撑满，正文在满屏里流式铺开
        expanded = !waitingToStart,
        // 等发车时的动作栏就是那句「开始分析」，是这一屏唯一的主操作：整条主色实心。
        // 其余相位（停止/复制/重试）都退回容器色，动作栏只是容器
        actionsBackground = if (waitingToStart) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.secondaryContainer
        },
        // ── 对话区 ──
        content = {
            // 用户侧：只有「还没发车」才摆条件选择器（表单态，不套气泡）；
            // 一旦问出口——包括中途失败/停止——就固定成摘要 chip，跟回看时的排版一致。
            // 半途停止不该把表单又怼回用户脸上，重试用的是当时那份条件
            if (question != null) {
                if (question is AiQuestionSlot.Composer && phase == null) {
                    question.content()
                } else {
                    AiQueryBubble {
                        AiChip(
                            when (question) {
                                is AiQuestionSlot.Static -> question.chip
                                // 摘要与落盘共用一个取值：用户发车前切过的范围会如实显示
                                is AiQuestionSlot.Composer -> request.resolveHistoryQuery()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(AiSheetSpacing.SectionGap))
            }

            // 模型侧：思考块（可折叠）+ 正文（直接铺在页面上，没有头像也没有气泡卡片）
            if (thinkingVisible) {
                ThinkingBlock(
                    text = uiState.reasoning,
                    seconds = thinkingSeconds,
                    expanded = thinkingExpanded,
                    onToggle = {
                        thinkingTouchedByUser = true
                        thinkingExpanded = !thinkingExpanded
                    },
                )
                // 内容区可见（思考中小窗 / 展开态）时留大一点间距，收成一行标题时贴紧
                Spacer(
                    Modifier.height(
                        if (thinkingExpanded || thinkingSeconds == 0) {
                            AiSheetSpacing.ThinkGapExpanded
                        } else {
                            AiSheetSpacing.ThinkGapCollapsed
                        },
                    ),
                )
            }
            when {
                // 还没发送：只说一句「接下来谁来分析」，居中——流程不用解释，按钮就在下面。
                // 模型名只在字面里出现，不需要额外强调。读模型名的收集器放在这一支里：
                // 它变化时只重组这一行，不牵动整个底栏
                phase == null && question is AiQuestionSlot.Composer -> {
                    val modelName by viewModel.modelName.collectAsState()
                    HintText(
                        // 配置还没读到时的兜底：宁可笼统，也不要闪出「将使用  分析」这种半截句子
                        text = "将使用 ${modelName.ifBlank { "AI" }} 分析",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 拉数据 / 拼提示词：调用方可以给带进度的自定义提示（同样撑满防跳变）
                phase == AiPhase.Preparing ->
                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        if (preparingHint != null) preparingHint() else HintText("正在拉取数据…")
                    }
                // 正文未开始、思考块里已经在转圈提示：不重复给第二行提示
                !hasAnswer && thinkingVisible -> Unit
                else -> AiMarkdownStreaming(mdState)
            }
            // 失败 / 停止的补充说明卡挂在正文下方：卡片层次让它在长报告末尾也能被一眼看到
            if (phase == AiPhase.Failed) {
                AiStatusCard(
                    text = uiState.errorMessage ?: "未知错误",
                    isError = true,
                    modifier = Modifier.padding(top = AiSheetSpacing.StatusCardGap),
                )
            }
            if (phase == AiPhase.Stopped) {
                AiStatusCard(
                    text = "已停止生成。",
                    isError = false,
                    modifier = Modifier.padding(top = AiSheetSpacing.StatusCardGap),
                )
            }
        },
        // ── 动作区：随阶段切换 ──
        actions = aiActionItems(
            phase = phase,
            // 还没发车且是「先问后答」：动作栏就是那句「开始分析」
            hasComposer = question is AiQuestionSlot.Composer,
            onSend = { viewModel.onSend(request) },
            onCopy = { copyToClipboard(context, uiState.answer, request.title) },
            onAdopt = onAdopt?.let { adopt ->
                {
                    adopt(uiState.answer)
                    dialogState.dismiss()
                }
            },
            onStop = viewModel::onStop,
            onRetry = { viewModel.onRetry(request) },
        ),
    )
}

/**
 * 底部动作条目：把「某个阶段能做什么」收成一个 when，交给骨架渲染成 [CardActionBar]。
 *
 * 单独抽出来是为了让「某个阶段有什么动作」只看这一处——
 * 旧实现把同一阶段的视觉拆在内容区和动作区两个 when 里，读一个状态要跳两处。
 *
 * 动作栏只有图标 + 文字（[CardActionBarItem]），不再是实心按钮：
 * 这里只表达「对内容做什么」，不承担视觉强调，「离开」统一收在标题栏左上角的 X
 * （[AiSheetScaffold] 的 `startAction`）。
 *
 * @param phase null = 还没发车
 * @param hasComposer 是否有条件选择器：有的话「还没发车」这一行就是「开始分析」，
 *   没有则说明打开即发（静态 chip），不渲染任何动作
 * @param onSend 点「开始分析」
 * @param onAdopt 「采用」回调；null = 无采用动作，成功态只给「复制」
 * @return 动作条目，顺序即从左到右的排布顺序
 */
@Composable
private fun aiActionItems(
    phase: AiPhase?,
    hasComposer: Boolean,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onAdopt: (() -> Unit)?,
    onStop: () -> Unit,
    onRetry: () -> Unit,
): List<CardActionBarItem> = when (phase) {
    // 准备中 / 流式中：只有一个「停止」
    AiPhase.Preparing, AiPhase.Streaming -> listOf(
        CardActionBarItem(
            icon = MiuixIcons.Demibold.Close,
            label = "停止",
            onClick = onStop,
        ),
    )
    // 成功：复制（+ 可选的「采用」，主色标记出推荐动作）
    AiPhase.Success -> buildList {
        add(
            CardActionBarItem(
                icon = MiuixIcons.Demibold.Copy,
                label = "复制",
                onClick = onCopy,
            ),
        )
        if (onAdopt != null) {
            add(
                CardActionBarItem(
                    icon = MiuixIcons.Demibold.Ok,
                    label = "采用",
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = onAdopt,
                ),
            )
        }
    }
    // 失败 / 停止：只剩「重试 / 重新生成」（离开走标题栏的关闭）
    AiPhase.Failed, AiPhase.Stopped -> listOf(
        CardActionBarItem(
            icon = MiuixIcons.Demibold.Refresh,
            label = if (phase == AiPhase.Failed) "重试" else "重新生成",
            tint = MiuixTheme.colorScheme.primary,
            onClick = onRetry,
        ),
    )
    // 还没发车：有选择器就给「开始分析」（这是这一屏唯一的主操作，底色走主色），
    // 没有则是打开即发（静态 chip），此时没有动作可做
    null -> if (hasComposer) {
        listOf(
            CardActionBarItem(
                icon = MiuixIcons.Demibold.Send,
                label = "开始分析",
                // 底色是 primary（见 AiSheetScaffold 的 actionsBackground），文字与图标必须配 onPrimary
                tint = MiuixTheme.colorScheme.onPrimary,
                onClick = onSend,
            ),
        )
    } else {
        emptyList()
    }
}

// ─────────────────────────── 历史回看 ───────────────────────────

/**
 * AI 记录的详情底栏：把当时那次问答原样铺回来。
 *
 * 与 [AiResultSheet] 共用同一个骨架和同一套零件（[AiQueryBubble] / [ThinkingBlock] /
 * Markdown 配色），所以「当时看到的」和「回看时看到的」排版完全一致。
 *
 * 本函数是**纯展示**：数据由调用方传入，不查任何仓库、不发任何请求——
 * 删除动作也只上报意图（[onDelete]），由调用方确认后自己执行。
 *
 * 为什么仍用 Sheet 而不是独立页面：内容就是一篇文章 + 三个动作，Sheet 撑满高度、返回手势
 * 天然关闭；做成页面还要再注册一条路由、再写一套顶栏。
 *
 * @param entry 要展示的记录；传 null 表示关闭（与 [AiResultSheet] 的 DialogState 用法同构）
 * @param onDismiss 关闭请求（遮罩点击 / 返回键 / 标题栏左上角的 X）
 * @param onDelete 点「删除」：由调用方弹确认框，确认后再真正删除
 */
@Composable
internal fun AiHistoryDetailSheet(
    entry: AiHistoryEntry?,
    onDismiss: () -> Unit,
    onDelete: (AiHistoryEntry) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 冻结最后一帧：关闭动画期间调用方已把 entry 置 null，但收起动画还要播完，
    // 内容不能闪没——保留最后一次非空的记录给动画用
    val displayEntry = remember { mutableStateOf(entry) }
    if (entry != null) displayEntry.value = entry
    val shown = displayEntry.value ?: return

    // 思考块的展开态按记录隔离：换一条记录就回到默认收起
    var thinkingExpanded by remember(shown.id) { mutableStateOf(false) }

    AiSheetScaffold(
        // show 由 entry 驱动退出动画；内容固定用冻结的 shown，不再受 null 影响
        show = entry != null,
        title = shown.title,
        onDismissRequest = onDismiss,
        scrollState = scrollState,
        // ── 对话区 ──
        content = {
            // 用户侧：当时问的是哪份数据（如「按日 · 近 30 天」）
            if (shown.query.isNotBlank()) {
                AiQueryBubble { AiChip(shown.query) }
                Spacer(Modifier.height(AiSheetSpacing.SectionGap))
            }

            // 元信息：谁出的、什么时候问的。比正文弱一档，不抢阅读
            Text(
                text = metaLine(shown),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth().padding(bottom = AiSheetSpacing.MetaGap),
            )

            // 模型侧：思考块（默认收起，点标题才展开全文）
            if (shown.reasoning.isNotBlank()) {
                ThinkingBlock(
                    text = shown.reasoning,
                    seconds = shown.thinkingSeconds,
                    expanded = thinkingExpanded,
                    onToggle = { thinkingExpanded = !thinkingExpanded },
                )
                Spacer(Modifier.height(AiSheetSpacing.ThinkGapExpanded))
            }

            // 回答正文：与生成时同一套 Markdown 配色与字号
            AiMarkdownText(shown.answer)
        },
        // ── 动作区：只放内容动作，「关闭」在标题栏（骨架统一提供）──
        actions = listOf(
            CardActionBarItem(
                icon = MiuixIcons.Demibold.Copy,
                label = "复制",
                onClick = { copyToClipboard(context, shown.answer, shown.title) },
            ),
            // 删除用错误色标记：它会真的删掉一条记录（确认框由调用方弹）
            CardActionBarItem(
                icon = MiuixIcons.Demibold.Delete,
                label = "删除",
                tint = MiuixTheme.colorScheme.error,
                onClick = { onDelete(shown) },
            ),
        ),
    )
}

// ─────────────────────────── 工具 ───────────────────────────

/** 元信息行：供应商 · 模型名 · 相对时间，缺项自动省略，不留多余的分隔点。 */
private fun metaLine(entry: AiHistoryEntry): String = buildList {
    // 供应商存的是 storageValue，回显要转成展示名（自定义档直接显示空，避免出现 "custom"）
    AiProviderPreset.fromStorage(entry.provider)?.takeIf { it != AiProviderPreset.Custom }?.let { add(it.title) }
    if (entry.model.isNotBlank()) add(entry.model)
    add(relativeTime(entry.createdAt))
}.joinToString(" · ")

/** 文本复制到剪贴板的公共小工具（两个 AI 底栏共用）。 */
internal fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private const val TAG = "AiSheet"

/** 「紧凑 → 撑满」的高度过渡时长：略快于 Sheet 自身的入场动画，点完感觉就是立刻开始长高。 */
private const val SheetHeightAnimMs = 280
