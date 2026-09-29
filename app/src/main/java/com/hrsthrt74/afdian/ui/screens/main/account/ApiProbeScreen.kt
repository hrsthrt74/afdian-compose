package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.probe.ApiProbeResult
import com.hrsthrt74.afdian.data.probe.Highlight
import com.hrsthrt74.afdian.data.storage.formatBytes
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.CopyButton
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import com.hrsthrt74.afdian.ui.viewmodel.ApiProbeViewModel
import com.hrsthrt74.afdian.ui.viewmodel.ProbeEnvironment
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** 卡片内容的内边距：统一 16dp，与 Miuix 组件的默认内边距对齐。 */
private val CardInsidePadding = PaddingValues(16.dp)

/**
 * 页面里小节标题的内边距。
 *
 * 页面 Card 外边距 16dp + 卡片内容内边距 16dp = 内容左边缘在 32dp；
 * `SmallTitle` 自带 28dp 横向内边距，因此再补 4dp 正好与卡片内容对齐。
 */
private val SectionTitlePaddingPage = PaddingValues(start = 4.dp)

/**
 * Sheet 里小节标题的内边距。
 *
 * Sheet 自带 24dp 横向内边距 + 卡片内容内边距 16dp = 内容左边缘在 40dp，
 * 因此标题补 16dp（24 + 16 = 40）与卡片内容对齐。
 */
private val SectionTitlePaddingSheet = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp)

/**
 * 接口调试页：逐个「主动探针」运行核心无参接口，展示本次返回的状态、关键字段与原始 JSON。
 *
 * 与日志页同构（Scaffold + LazyColumn + 右侧滚动条 + 浮动模糊顶栏）。列表只呈现「一眼要看的数字」，
 * 完整的响应原文（请求元信息 + 原始 JSON）放到点开的详情 Sheet 里：列表更短更好扫视，
 * 也不存在超长 JSON 把卡片撑开的观感问题。
 *
 * 关于探针的脱敏：展示的请求 URL 与日志中的 auth_token 一律脱敏成 `***`；
 * 响应里的手机号（含服务端半掩形态如 187***2066）统一掩成 `1**********`，
 * 邮箱本地部分掩成 `***`（保留域名）。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun ApiProbeScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    // AndroidViewModel 需要 Application，因此不能用默认工厂
    viewModel: ApiProbeViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val haptic = LocalHapticFeedback.current

    val cards by viewModel.cards.collectAsState()
    val environment by viewModel.environment.collectAsState()
    // null = 还没读到登录态，此时不判断空态也不渲染列表
    val loggedIn: Boolean? = environment?.loggedIn

    // 详情 Sheet 正在展示哪个探针（null = 已关闭）
    var detailProbeId by remember { mutableStateOf<String?>(null) }
    // Sheet 关闭动画期间仍要渲染内容，因此额外记住「最后一次展示的卡片」做兜底。
    // 只在打开时写入、不在组合期回写，避免多余的额外重组。
    var lastDetailCard by remember { mutableStateOf<ApiProbeViewModel.Card?>(null) }

    // 进入页面即自动全跑：等登录态读出来且已登录才触发，未登录时探针必然全失败
    LaunchedEffect(loggedIn) {
        if (loggedIn == true) viewModel.runAll()
    }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "接口调试",
                        largeTitle = "接口调试",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = contentBottomPadding(),
                ),
            ) {
                item(key = "hint") {
                    // 说明卡：交代用户猜不到的规则（自动全跑 / 详情 / 刷新 / 脱敏 / 截断 / 并发），
                    // 用 tertiaryContainer 底色与普通信息卡区分开，摆在页面顶端进门就能看到
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.tertiaryContainer,
                            contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                        ),
                    ) {
                        Text(
                            text =  "• auth_token、手机号已脱敏为 ***\n" +
                                    "• 探针只取单页快照\n" +
                                    "• 原始 JSON 超长会自动截断\n" +
                                    "• 一键全跑并发 3，避免触发服务端限流\n" +
                                    "• 数据不会保存或被追踪",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp, 12.dp),
                        )
                    }
                }
                item(key = "env_title") {
                    SmallTitle(text = "环境", modifier = Modifier.padding(SectionTitlePaddingPage))
                }
                item(key = "env_card") {
                    EnvironmentCard(environment = environment)
                }

                when (loggedIn) {
                    // 登录态还没读出来：给个明确的加载态，避免闪一下空态
                    null -> item(key = "env_loading") {
                        LoadingComponent(
                            text = "正在读取登录态…",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }

                    // 未登录：探针一定失败，直接给空态并把「刷新登录态」摆在手边
                    false -> item(key = "not_logged_in") {
                        EmptyStateView(
                            title = "未登录",
                            summary = "这些接口都需要 auth_token，登录后才能探针。",
                            modifier = Modifier.fillParentMaxSize(),
                            action = {
                                TextButton(
                                    text = "刷新登录态",
                                    onClick = { viewModel.refreshEnvironment() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        )
                    }

                    true -> {
                        // ===== 操作区：一键全跑 =====
                        item(key = "action") {
                            // 一键全跑：受并发上限保护，逐张卡回填结果
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    viewModel.runAll()
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                            ) {
                                Text("一键全跑")
                            }
                        }

                        // ===== 探针卡片列表 =====
                        item(key = "probe_title") {
                            SmallTitle(text = "数据", modifier = Modifier.padding(SectionTitlePaddingPage))
                        }
                        items(items = cards, key = { it.spec.id }) { card ->
                            ApiProbeCard(
                                card = card,
                                onRun = { viewModel.runOne(card.spec.id) },
                                onOpenDetail = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lastDetailCard = card
                                    detailProbeId = card.spec.id
                                },
                            )
                        }
                    }
                }
            }

            // 右侧滚动条
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                ),
            )
        }
    }

    // ===== 详情 Sheet =====
    // 打开时按 id 派生当前卡片，所以在 Sheet 里点「重新运行」后内容会实时刷新；
    // 关闭时 id 立刻置空，但内容回退到 lastDetailCard，退出动画期间不会闪空。
    val detailCard = detailProbeId?.let { id -> cards.firstOrNull { it.spec.id == id } } ?: lastDetailCard
    ApiProbeDetailSheet(
        card = detailCard,
        show = detailProbeId != null,
        onDismiss = { detailProbeId = null },
        onRerun = { detailCard?.let { viewModel.runOne(it.spec.id) } },
    )
}

/**
 * 环境信息卡片：构建变体、探针有效期。
 *
 * 安全约定：登录态只显示「已登录 / 未登录」，**任何情况下都不展示 token 内容**。
 */
@Composable
private fun EnvironmentCard(environment: ProbeEnvironment?) {
    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        // 构建变体：与设置页底部同一来源，便于区分测试包
        BasicComponent(
            title = "构建变体",
//            summary = if (environment?.internalBuild == true) "内测版" else "正式版",
            endActions = { EnvValue(environment?.variantName ?: "读取中…") },
        )
        // 探针有效期：内测包由时间炸弹限制 7 天；正式版不会锁，这里仅作展示
        BasicComponent(
            title = "内测包体有效期剩余",
//            summary = "构建时间起 7 天",
            endActions = { EnvValue(environment?.let { formatRemaining(it.remainingMs) } ?: "读取中…") },
        )
        /*
        // 登录态：只暴露布尔值，绝不显示凭据
        BasicComponent(
            title = "登录态",
            summary = "请求会携带本地 auth_token",
            endActions = {
                EnvValue(
                    when (environment?.loggedIn) {
                        null -> "读取中…"
                        true -> "已登录"
                        false -> "未登录"
                    }
                )
            },
        )
        */
    }
}

/**
 * 单张探针卡片。
 *
 * 排版分四段，三种运行状态共用同一套骨架（标题 → 路径 → 分隔线 → 内容），
 * 卡片高度与结构稳定，列表扫视时不会跳动。
 *
 * 层次靠**字号与颜色两件事同时拉开**，而不是只靠颜色：
 * - 标题 `body1` + Medium；
 * - **字段值 `body1` + 等宽 + Medium —— 全卡视觉焦点**（调试页看的就是这个数字）；
 * - 字段名 `footnote1` + 等宽 + 次要色（只是给数字做注解）；
 * - 技术元信息 `footnote2` + 三级次色 + 小图标（排查时才需要，不与数字抢重量；异常项标错误色）。
 *
 * 交互：点按整张卡片或底部「详情」打开详情 Sheet（卡片本身不加按压下沉，
 * 避免与操作栏的点击抢观感）；底部「刷新」单独重跑该接口，操作栏形态照 AffiliateCard。
 */
@Composable
private fun ApiProbeCard(
    card: ApiProbeViewModel.Card,
    onRun: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val result = card.result
    // 操作栏点击的轻触觉反馈：先震动再执行动作（手感与 AffiliateCard 一致）
    val haptic = LocalHapticFeedback.current
    fun hapticThen(action: () -> Unit): () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }

    // 整卡可点但不下沉：Miuix Card(onClick) 的 pressFeedbackType 默认 None
    Card(
        onClick = onOpenDetail,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp)) {
            // ① 标题行：接口语义名 + 运行状态
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.spec.title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                ProbeStatusLabel(result)
            }

            // ② 路径行：等宽小字，方便与抓包 / 接口文档逐字核对。
            // 不写「GET 」前缀——清单里全是 GET，前缀只挤占宽度；方法在详情 Sheet 里保留。
            Text(
                text = card.spec.path,
                style = MiuixTheme.textStyles.footnote2,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            // ③ 分隔线：三种状态都画，卡片骨架保持一致
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MiuixTheme.colorScheme.dividerLine,
            )

            // ④ 内容区：状态切换（加载态 ↔ 结果）用 AnimatedContent 默认预设做淡入淡出过渡。
            // 注意：AnimatedContent 的内容容器是 Box，多组件的分支必须自己包一层 Column，
            // 否则字段列表和元信息行会全部叠在左上角。
            AnimatedContent(
                targetState = result,
                modifier = Modifier.fillMaxWidth(),
                label = "probeResult",
            ) { target ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (target) {
                        // 未运行：只给一句接口说明，卡片止步于此，整份清单更短更好扫
                        ApiProbeResult.Idle -> Text(
                            text = card.spec.summary,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )

                        ApiProbeResult.Running -> LoadingComponent(
                            text = "请求中…",
        //                    size = 20.dp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )

                        is ApiProbeResult.Success -> {
                            // 关键字段：放在最前面，数字是这张卡的答案
                            ProbeFieldList(target.highlights)
                            // 技术元信息：次要色小字，放在数字之后；耗时超 1 秒标错误色提醒偏慢
                            ProbeMetaLine(
                                buildList {
                                    add(ProbeMetaItem(MiuixIcons.Demibold.Ok, "HTTP ${target.httpCode}"))
                                    add(
                                        ProbeMetaItem(
                                            MiuixIcons.Demibold.Timer,
                                            "${target.elapsedMs} ms",
                                            isError = target.elapsedMs >= SLOW_PROBE_MS,
                                        )
                                    )
                                    add(ProbeMetaItem(MiuixIcons.Demibold.Folder, formatBytes(target.bodyBytes)))
                                    target.businessCode?.let { add(ProbeMetaItem(MiuixIcons.Demibold.Info, "ec $it")) }
                                },
                            )
                        }

                        is ApiProbeResult.Failure -> {
                            // 失败原因单独成行、用错误色：它比任何字段都重要，不该埋在元信息里
                            Text(
                                text = "${target.kind.label}，${target.message}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            )
                            ProbeFieldList(target.highlights)
                            // 失败时的元信息：HTTP / 业务码整项标错误色，耗时同样按 1 秒阈值判慢
                            ProbeMetaLine(
                                buildList {
                                    target.httpCode?.let {
                                        add(ProbeMetaItem(MiuixIcons.Demibold.Close, "HTTP $it", isError = true))
                                    }
                                    target.businessCode?.let {
                                        add(ProbeMetaItem(MiuixIcons.Demibold.Info, "ec $it", isError = it != 200))
                                    }
                                    add(
                                        ProbeMetaItem(
                                            MiuixIcons.Demibold.Timer,
                                            "${target.elapsedMs} ms",
                                            isError = target.elapsedMs >= SLOW_PROBE_MS,
                                        )
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // ⑤ 操作栏上方的分隔线：在内容区内边距里，骨架与 ③ 一致
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MiuixTheme.colorScheme.dividerLine,
            )
        }

        // ⑥ 底部操作栏：刷新 / 详情，两列均分（照 AffiliateCard 的操作栏形态）
        Row(modifier = Modifier.fillMaxWidth()) {
            // ── 第 1 列：刷新（单独重跑该接口）──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = hapticThen(onRun))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 刷新图标
                    Icon(
                        imageVector = MiuixIcons.Demibold.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.size(18.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "刷新",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            // ── 第 2 列：详情（打开详情 Sheet；震动由 onOpenDetail 统一处理）──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenDetail)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 详情图标
                    Icon(
                        imageVector = MiuixIcons.Demibold.Info,
                        contentDescription = "详情",
                        modifier = Modifier.size(18.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "详情",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/**
 * 运行状态标签：彩色圆点 + 状态词，圆点负责颜色信号、文字负责语义。
 *
 * 刻意不套胶囊底色：
 * - `primaryContainer` 在 Miuix 里是高饱和的纯蓝（浅色 0xFF5D9BFF），
 *   14 张卡片每张顶一个蓝胶囊会非常吵；
 * - 中性底 `surfaceContainerHighest` 在深色模式下是 0xFF2D2D2D，与卡片底色 0xFF242424
 *   只差一点点，胶囊边界几乎看不见，白做一层底。
 *
 * 因此只在圆点上放颜色，文字跟色，克制的形态反而更稳。
 */
@Composable
private fun ProbeStatusLabel(result: ApiProbeResult) {
    val scheme = MiuixTheme.colorScheme
    val (label, dotColor, textColor) = when (result) {
        // 未运行：圆点用三级次色，视觉上「静默」，不抢已运行卡片的注意力
        ApiProbeResult.Idle -> Triple("未运行", scheme.onSurfaceVariantActions, scheme.onSurfaceVariantSummary)
        ApiProbeResult.Running -> Triple("请求中", scheme.primary, scheme.primary)
        is ApiProbeResult.Success -> Triple("成功", scheme.primary, scheme.primary)
        is ApiProbeResult.Failure -> Triple(result.kind.label, scheme.error, scheme.error)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 状态圆点：6dp 与 footnote1 的字高相称，8dp 会显得笨重
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = textColor,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** 耗时项的告警阈值：超过 1 秒视为偏慢，该项目的图标与文字一起标错误色。 */
private const val SLOW_PROBE_MS = 1_000L

/** 技术元信息行里的单个指标：小图标 + 文字；`isError` 时整项换成错误色。 */
private data class ProbeMetaItem(
    val icon: ImageVector,
    val text: String,
    val isError: Boolean = false,
)

/**
 * 技术元信息行（HTTP 码 / 耗时 / 响应大小 / 业务码），每项带一枚小图标：
 * HTTP 状态成功用 `Ok`、失败用 `Close`，耗时 `Timer`，响应大小 `Folder`，业务码 `Info`。
 *
 * 用 `footnote2` + 三级次色：这是排查时才要看的细节，不该和关键字段抢视觉重量。
 * 异常项（失败状态、超 1 秒的慢请求）整项换错误色，扫一眼就能定位哪条指标不对。
 */
@Composable
private fun ProbeMetaLine(items: List<ProbeMetaItem>) {
    if (items.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 指标小图标：12dp 与 footnote2 小字相称；错误时跟文字一起换错误色
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (item.isError) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantActions
                    },
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = item.text,
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (item.isError) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantActions
                    },
                )
            }
        }
    }
}

/**
 * 关键字段列表：左侧字段路径、右侧字段值。这是整张卡片最重要的一处排版。
 *
 * - **值用 `body1` + 等宽 + Medium**：调试时看的就是这个数字，它必须是全卡的视觉焦点；
 * - **字段名用 `footnote1` + 等宽 + 次要色**：它只是给数字做注解；
 * - 字段名单行省略（`maxLines = 1` + `Ellipsis`）且有 `weight(1f)`：长路径
 *   （如 `list[0].paid_order_real_amount`）只会截尾，绝不会把右边的值挤走或把卡片撑高；
 *   值本身不参与权重分配，因此每行值的右边缘天然对齐成一列。
 *
 * 值解析不到时显示灰色「（缺失）」，与字段值是字面 `null`（显示 `null`）可区分。
 */
@Composable
private fun ProbeFieldList(highlights: List<Highlight>) {
    if (highlights.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        highlights.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 字段名：次要色、单行省略，只作数字的注解
                Text(
                    text = item.path,
                    style = MiuixTheme.textStyles.footnote2,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                Spacer(modifier = Modifier.width(4.dp))
                // 字段值：全卡焦点，等宽 + 中等字重；缺失时退回次要色弱化
                Text(
                    text = item.value ?: "（缺失）",
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = if (item.value != null) FontFamily.Monospace else null,
                    color = if (item.value != null) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    textAlign = TextAlign.End,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
    }
}

/**
 * 接口详情 Sheet：一次探针的完整信息。
 *
 * 分区：接口路径（含「重新运行」）→ 本次请求（状态码 / 耗时 / 大小 / 命中域名 / 请求 URL）
 * → 关键字段 → 原始 JSON。复制原始 JSON 放在 Sheet 顶部右侧，无论滚到哪都够得着。
 *
 * 为什么用 `WindowBottomSheet`：它是独立窗口层，不受页面滚动与模糊层影响；
 * 且 Sheet 内容区自带纵向滚动，长 JSON 直接交给它，不必再嵌一层滚动容器。
 */
@Composable
private fun ApiProbeDetailSheet(
    card: ApiProbeViewModel.Card?,
    show: Boolean,
    onDismiss: () -> Unit,
    onRerun: () -> Unit,
) {
    val result = card?.result
    val rawJson = result?.rawJsonOrNull()
    // 只有终态（成功 / 失败）才带截断标记，这里提前取出来，避免在 item 里对可空接收者做智能转换
    val rawTruncated = when (result) {
        is ApiProbeResult.Success -> result.rawTruncated
        is ApiProbeResult.Failure -> result.rawTruncated
        else -> false
    }

    WindowBottomSheet(
        show = show,
        title = card?.spec?.title ?: "接口详情",
        onDismissRequest = onDismiss,
        startAction = {
            // 就地重跑：结果会实时回填到本 Sheet
            IconButton(
                onClick = onRerun,
                enabled = result != ApiProbeResult.Running,
            ) {
                Icon(MiuixIcons.Refresh, null)
            }
        },
        endAction = {
            // 复制完整原始 JSON；请求中或没有响应体时禁用
            CopyButton(
                text = rawJson.orEmpty(),
                enabled = rawJson != null,
                contentDescription = "复制原始 JSON",
            )
        },
    ) {
        if (card == null) return@WindowBottomSheet

        LazyColumn(
            modifier = Modifier.fillMaxWidth().scrollEndHaptic(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            // ===== 接口路径 =====
            item(key = "path") {
                // 路径：等宽字体，长路径自动换行
                Text(
                    text = "GET ${card.spec.path}",
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }

            // ===== 本次请求 =====
            item(key = "request_title") {
                SmallTitle("本次请求", insideMargin = SectionTitlePaddingSheet)
            }
            item(key = "request_card") {
                Card(
                    colors = AfdianCardDefaults.overlayColors(),
                    insideMargin = CardInsidePadding,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (result) {
                            null, ApiProbeResult.Idle -> {
                                SheetValueRow(label = "状态", value = "尚未运行", last = true)
                            }

                            ApiProbeResult.Running -> {
                                LoadingComponent(
                                    text = "请求中…",
                                    size = 20.dp,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                )
                            }

                            is ApiProbeResult.Success -> {
                                SheetValueRow(label = "HTTP 状态", value = result.httpCode.toString())
                                SheetValueRow(
                                    label = "业务码",
                                    value = result.businessCode?.let { "ec=$it" } ?: "该接口未返回 ec",
                                )
                                SheetValueRow(label = "耗时", value = "${result.elapsedMs} ms")
                                SheetValueRow(label = "响应大小", value = formatBytes(result.bodyBytes))
                                SheetValueRow(label = "命中域名", value = result.resolvedHost)
                                SheetValueRow(
                                    label = "请求 URL（已脱敏）",
                                    value = result.requestUrl,
                                    mono = true,
                                    last = true,
                                )
                            }

                            is ApiProbeResult.Failure -> {
                                SheetValueRow(label = "失败类型", value = result.kind.label, isError = true)
                                SheetValueRow(label = "说明", value = result.message)
                                result.httpCode?.let {
                                    SheetValueRow(label = "HTTP 状态", value = it.toString())
                                }
                                result.businessCode?.let {
                                    SheetValueRow(label = "业务码", value = "ec=$it")
                                }
                                SheetValueRow(label = "耗时", value = "${result.elapsedMs} ms")
                                result.resolvedHost?.let {
                                    SheetValueRow(label = "命中域名", value = it)
                                }
                                val requestUrl = result.requestUrl
                                if (requestUrl != null) {
                                    SheetValueRow(
                                        label = "请求 URL（已脱敏）",
                                        value = requestUrl,
                                        mono = true,
                                        // 后面还有原始 JSON 区块时不要画末尾分隔线
                                        last = result.rawJson == null,
                                    )
                                } else {
                                    // 连请求都没发出去（网络异常）：补一行，避免卡片底部悬空
                                    SheetValueRow(label = "请求未完成", value = "未收到任何响应", last = true)
                                }
                            }
                        }
                    }
                }
            }

            // ===== 关键字段 =====
            val highlights = result?.highlightsOrEmpty().orEmpty()
            if (highlights.isNotEmpty()) {
                item(key = "fields_title") {
                    SmallTitle("关键字段", insideMargin = SectionTitlePaddingSheet)
                }
                item(key = "fields_card") {
                    Card(
                        colors = AfdianCardDefaults.overlayColors(),
                        insideMargin = CardInsidePadding,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            highlights.forEachIndexed { index, item ->
                                SheetValueRow(
                                    label = item.path,
                                    value = item.value ?: "（缺失）",
                                    mono = true,
                                    muted = item.value == null,
                                    last = index == highlights.lastIndex,
                                )
                            }
                        }
                    }
                }
            }

            // ===== 原始 JSON =====
            if (rawJson != null) {
                item(key = "raw_title") {
                    SmallTitle(
                        text = if (rawTruncated) "原始 JSON（已截断）" else "原始 JSON",
                        insideMargin = SectionTitlePaddingSheet,
                    )
                }
                item(key = "raw_card") {
                    Card(
                        colors = AfdianCardDefaults.overlayColors(),
                        insideMargin = CardInsidePadding,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        MonospaceJsonBlock(text = rawJson)
                    }
                }
            }
        }
    }
}

/**
 * Sheet 内的一行 key-value。
 *
 * 标签与值按 1 : 1.2 分宽，值右对齐：既保证长路径（如 `summary.month_amount`）能完整显示，
 * 也保证长值（如请求 URL）有足够空间。
 *
 * @param mono 值是否用等宽字体（字段路径与 URL 用，普通文案不用）
 * @param muted 值是否需要弱化（用于「（缺失）」这类占位）
 * @param isError 值是否用错误色（用于失败类型）
 * @param last 是否是本组最后一行；最后一行不画分隔线
 */
@Composable
private fun SheetValueRow(
    label: String,
    value: String,
    mono: Boolean = false,
    muted: Boolean = false,
    isError: Boolean = false,
    last: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 标签：次要色，与右侧值拉开层次
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = MiuixTheme.textStyles.footnote2,
                fontFamily = if (mono) FontFamily.Monospace else null,
                color = when {
                    isError -> MiuixTheme.colorScheme.error
                    muted -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else -> MiuixTheme.colorScheme.onSurfaceContainerHighest
                },
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
        }
        if (!last) {    // 分割线
            Spacer(Modifier.padding(top = 6.dp))
            HorizontalDivider()
        }
    }
}

/**
 * Sheet 内的原始 JSON 代码块。
 *
 * 纵向滚动交给 Sheet 自身的列表；这里只负责横向滚动（`softWrap = false`）：
 * 长行不折行、一个字段一眼看完，也让每行只测量一次，超长 JSON 不会拖垮布局。
 * 外层包 `SelectionContainer`，支持长按选中局部内容。
 */
@Composable
private fun MonospaceJsonBlock(text: String) {
    val horizontalScroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScroll)) {
        SelectionContainer {
            Text(
                text = text,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                softWrap = false,
            )
        }
    }
}

/** 环境信息行右侧的值，样式与设置页 / 日志页的 `BasicComponent` 值保持一致。 */
@Composable
private fun EnvValue(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}

/** 取原始 JSON：成功与失败都可能带响应体（例如业务失败时服务端仍会回 em 与 data）。 */
private fun ApiProbeResult.rawJsonOrNull(): String? = when (this) {
    is ApiProbeResult.Success -> rawJson
    is ApiProbeResult.Failure -> rawJson
    else -> null
}

/** 取关键字段摘要：只有成功与失败两种终态才可能有。 */
private fun ApiProbeResult.highlightsOrEmpty(): List<Highlight> = when (this) {
    is ApiProbeResult.Success -> highlights
    is ApiProbeResult.Failure -> highlights
    else -> emptyList()
}

/** 剩余时间的人类可读格式：X 天 Y 小时 / Y 小时 Z 分 / Z 分 / 已过期。 */
private fun formatRemaining(remainingMs: Long): String {
    if (remainingMs <= 0L) return "已过期"
    val totalMinutes = remainingMs / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = totalMinutes % (60 * 24) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "$days 天 $hours 小时"
        hours > 0 -> "$hours 小时 $minutes 分"
        else -> "$minutes 分"
    }
}
