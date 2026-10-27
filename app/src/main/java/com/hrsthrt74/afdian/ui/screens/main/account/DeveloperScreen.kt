package com.hrsthrt74.afdian.ui.screens.main.account

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.CardActionBar
import com.hrsthrt74.afdian.ui.components.basic.CardActionBarItem
import com.hrsthrt74.afdian.ui.components.basic.CategoryIconTile
import com.hrsthrt74.afdian.ui.components.basic.CopyButton
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.navigation.DEV_DOC_OVERVIEW_POST_ID
import com.hrsthrt74.afdian.ui.navigation.DEV_DOC_WEBHOOK_API_POST_ID
import com.hrsthrt74.afdian.ui.navigation.DocTileGreen
import com.hrsthrt74.afdian.ui.navigation.DocTilePurple
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import com.hrsthrt74.afdian.ui.viewmodel.DeveloperViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 开发者页：对应官网 /dashboard/dev。
 *
 * 只做官网已有的三件事——查看 user_id、配置 Webhook URL、生成 API Token；
 * 不做本地调试器 / 一键发起 API 请求之类官网没有的功能。
 *
 * 布局顺序：
 * 1. 说明卡片（页面顶部的取用说明）
 * 2. user_id 卡片（只读展示 + 复制）
 * 3. API Token 卡片（显示 / 生成两个动作走 CardActionBar）
 * 4. Webhook URL 卡片（输入 + 保存 / 发送测试 + 推送格式）
 * 5. 开发者文档卡片（两篇官方动态入口）
 */
@Composable
fun DeveloperScreen(
    viewModel: DeveloperViewModel,
    /** 打开一篇官方动态（post id），进动态详情页展示 */
    onOpenDoc: (String) -> Unit,
    backStack: NavBackStack,
    onBack: () -> Unit,
) {
    // 数据：首屏状态与三份内容
    val loadState by viewModel.loadState.collectAsState()
    val userId by viewModel.userId.collectAsState()
    val webhookUrl by viewModel.webhookUrl.collectAsState()
    val token by viewModel.token.collectAsState()
    // 三个写操作的进行中标记（禁用动作防连点）
    val savingWebhook by viewModel.savingWebhook.collectAsState()
    val testingWebhook by viewModel.testingWebhook.collectAsState()
    val creatingToken by viewModel.creatingToken.collectAsState()
    val message by viewModel.message.collectAsState()

    // Webhook 输入框草稿：用 TextFieldValue 承载，编辑期间不回读覆盖
    var urlDraft by remember { mutableStateOf(TextFieldValue("")) }
    // 草稿是否已与服务端对齐（首屏数据回来后自动同步一次，之后交给用户）
    var draftSynced by remember { mutableStateOf(false) }

    // 屏幕
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    // 生成 Token 前必须确认（旧 Token 会立即失效）
    var pendingCreateToken by remember { mutableStateOf(false) }
    val createTokenDialogState = rememberDialogState()
    // 操作结果提示弹窗：ViewModel 的 message 非空时弹出，关闭后消费掉
    val messageDialogState = rememberDialogState()
    // 推送数据格式：底部弹窗开关（JSON 正文用等宽块渲染，不用弹窗 summary）
    var showPayloadSheet by remember { mutableStateOf(false) }

    // 首屏数据到位后把草稿对齐一次；之后用户自己维护，避免回读覆盖正在输入的内容
    LaunchedEffect(webhookUrl) {
        if (!draftSynced && webhookUrl.isNotBlank()) {
            urlDraft = TextFieldValue(webhookUrl)
            draftSynced = true
        }
    }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "开发者",
                        largeTitle = "开发者",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding(),
                ),
            ) {
                // ---- 说明卡片：两种接入方式的分工与使用要点 ----
                item(key = "intro") {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = DEVELOPER_INTRO,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }

                // ---- user_id：开发者接口的对外身份标识 ----
                item(key = "userId") {
                    UserIdCard(
                        userId = userId,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp),
                    )
                }

                // ---- API Token：显示 / 生成两个动作走 CardActionBar ----
                item(key = "token") {
                    TokenCard(
                        token = token,
                        creating = creatingToken,
                        onCreate = {
                            pendingCreateToken = true
                            createTokenDialogState.show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp),
                    )
                }

                // ---- Webhook URL：输入 + 保存 / 发送测试 + 推送格式 ----
                item(key = "webhook") {
                    WebhookCard(
                        url = urlDraft,
                        onUrlChange = { urlDraft = it },
                        saving = savingWebhook,
                        testing = testingWebhook,
                        onSave = { viewModel.saveWebhook(urlDraft.text.trim()) },
                        onTest = { viewModel.testWebhook(urlDraft.text.trim()) },
                        onShowPayload = { showPayloadSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp),
                    )
                }

                // ---- 开发者文档入口：两篇官方动态（同源收录在「我的」页 → 文档） ----
                item(key = "docs") {
                    SmallTitle("文档", modifier = Modifier.padding(start = 4.dp, top = 12.dp))
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column {
                            // 按 post id 打开动态详情页；行样式与「我的 → 文档」的文档卡一致
                            //（图标块配色 / 图标 / 摘要与文档页一一对应）
                            ArrowPreference(
                                title = "Webhook 与 API 文档",
                                summary = "Webhook 推送与 API 签名调用",
                                startAction = { CategoryIconTile(icon = MiuixIcons.Link, iconBackground = DocTileGreen) },
                                onClick = { onOpenDoc(DEV_DOC_WEBHOOK_API_POST_ID) },
                            )
                            ArrowPreference(
                                title = "开发者功能汇总",
                                summary = "API、Webhook、OAuth2 与网页嵌入",
                                startAction = { CategoryIconTile(icon = MiuixIcons.Tune, iconBackground = DocTilePurple) },
                                onClick = { onOpenDoc(DEV_DOC_OVERVIEW_POST_ID) },
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            // 首屏整体失败（三份数据全挂）时的整页错误态：盖在列表之上，保留顶栏可返回
            if (loadState.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.background)
                        .padding(top = paddingValues.calculateTopPadding()),
                ) {
                    ErrorStateView(
                        onRetry = viewModel::load,
                        modifier = Modifier.fillMaxSize(),
                        summary = loadState.error,
                    )
                }
            }

            // 首屏加载中：内容区居中转圈
            if (!loadState.loaded && loadState.error == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding()),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingComponent()
                }
            }
        }
    }

    // 生成 Token 确认弹窗（破坏性：旧 Token 会立即失效）
    AppDialog(
        state = createTokenDialogState,
        title = "生成新的 Token？",
        summary = "重新生成后，当前的 API Token 会立即失效，所有依赖旧 Token 的服务都会调用失败。",
        confirmText = "生成",
        destructive = true,
        onDismissed = { pendingCreateToken = false },
        onConfirm = {
            // 只有弹窗期间仍处于待确认态才真正发起生成
            if (pendingCreateToken) viewModel.createToken()
            pendingCreateToken = false
        },
    )

    // 操作结果提示：成功用普通标题，失败走红色确认按钮
    AppDialog(
        state = messageDialogState,
        title = message?.let { if (it.error) "操作失败" else "操作完成" }.orEmpty(),
        summary = message?.text.orEmpty(),
        confirmText = "知道了",
        destructive = message?.error == true,
        onConfirm = viewModel::consumeMessage,
        onDismissed = viewModel::consumeMessage,
    )

    // Webhook 推送数据格式：JSON 正文用等宽块渲染，与接口调试页详情 Sheet 同一套写法
    WebhookPayloadSheet(show = showPayloadSheet, onDismiss = { showPayloadSheet = false })
}

/**
 * Sheet 里小节标题的内边距。
 *
 * 与 ApiProbeScreen 的 SectionTitlePaddingSheet 同一套对齐规则：
 * Sheet 自带 24dp 横向内边距 + 卡片内容内边距 16dp = 内容左边缘在 40dp，
 * 因此标题补 16dp（24 + 16 = 40）与卡片内容对齐。
 */
private val PayloadSectionTitlePadding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp)

/**
 * Sheet 内 Card 的内容内边距。
 *
 * 与 ApiProbeScreen 的 CardInsidePadding 同一套取值：等宽 JSON 需要 16.dp 留白
 * 才不贴边，否则左侧第一个 `{` 会紧贴卡片边缘。
 */
private val PayloadCardInsidePadding = PaddingValues(16.dp)

/**
 * Webhook 推送数据格式底部弹窗。
 *
 * JSON 用等宽小字 + 横向滚动展示（长行不折行），与 ApiProbeDetailSheet 的
 * MonospaceJsonBlock 同款；纵向滚动交给 Sheet 内的列表。
 */
@Composable
private fun WebhookPayloadSheet(show: Boolean, onDismiss: () -> Unit) {
    WindowBottomSheet(
        show = show,
        title = "Webhook 推送数据格式",
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().scrollEndHaptic(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            // ---- 推送方与响应约定 ----
            item(key = "note") {
                Text(
                    text = WEBHOOK_PAYLOAD_NOTE,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 12.dp),
                )
            }

            item(key = "payload_title") {
                SmallTitle("爱发电发送的数据", insideMargin = PayloadSectionTitlePadding)
            }
            // ---- 爱发电推过来的订单 JSON ----
            item(key = "payload") {
                Card(
                    colors = AfdianCardDefaults.overlayColors(),
                    insideMargin = PayloadCardInsidePadding,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    MonospaceJsonBlock(WEBHOOK_PAYLOAD_SAMPLE)
                }
            }

            item(key = "response_title") {
                SmallTitle("你的服务器需要返回", insideMargin = PayloadSectionTitlePadding)
            }
            // ---- 必须返回的响应 JSON ----
            item(key = "response") {
                Card(
                    colors = AfdianCardDefaults.overlayColors(),
                    insideMargin = PayloadCardInsidePadding,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    MonospaceJsonBlock(WEBHOOK_RESPONSE_SAMPLE)
                }
            }
        }
    }
}

/**
 * 等宽 JSON 代码块。
 *
 * 纵向滚动交给外层 Sheet 的列表；这里只负责横向滚动（softWrap = false）：
 * 长行不折行、一个字段一眼看完。外层包 SelectionContainer，支持长按选中局部内容。
 */
@Composable
private fun MonospaceJsonBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
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

/**
 * user_id 卡片：只读展示开发者 id，右上角带复制按钮。
 *
 * user_id 是 API 签名与 webhook 回调都要用到的对外身份标识。
 */
@Composable
private fun UserIdCard(userId: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column {
            // 头部：字段名 + 复制按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            ) {
                Text(
                    text = "user_id",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                // 复制按钮：复制的是原文；未取到 user_id 时置灰不可点
                CopyButton(
                    text = userId,
                    enabled = userId.isNotBlank(),
                    toastText = "已复制 user_id",
                    contentDescription = "复制 user_id",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // 内容区：等宽小字，长 id 完整换行展示
            Text(
                text = userId.ifBlank { "—" },
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

/**
 * API Token 卡片：显示 / 生成两个动作走 CardActionBar。
 *
 * Token 属于凭据，默认打码展示，点「显示 Token」才看明文；复制走头部 CopyButton。
 */
@Composable
private fun TokenCard(
    token: String,
    creating: Boolean,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 明文可见状态，默认关闭
    var revealed by remember { mutableStateOf(false) }
    val hasToken = token.isNotBlank()

    Card(modifier = modifier) {
        Column {
            // 头部：字段名 + 切勿泄露提示 + 复制按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            ) {
                Text(
                    text = "API Token",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "切勿泄露",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
                // 复制 Token：震动 → 写剪贴板 → 图标切 Ok + Toast，全部由 CopyButton 内部处理
                CopyButton(
                    text = token,
                    enabled = hasToken,
                    toastText = "已复制 Token",
                    contentDescription = "复制 Token",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // 内容区：等宽小字；明文默认打码，避免路过时误看到凭据
            Text(
                text = if (hasToken) {
                    if (revealed) token else MASKED_TOKEN
                } else {
                    "还没有生成 Token"
                },
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = if (hasToken) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
            )

            // 两个动作：显示 / 隐藏、生成 Token（生成走确认弹窗，销毁性由弹窗承担）
            CardActionBar(
                items = listOf(
                    // ── 显示 / 隐藏：明文默认收起 ──
                    CardActionBarItem(
                        icon = if (revealed) {
                            MiuixIcons.Demibold.Hide
                        } else {
                            MiuixIcons.Demibold.Show
                        },
                        label = if (revealed) "隐藏" else "显示",
                        onClick = if (hasToken) ({ revealed = !revealed }) else null,
                    ),
                    // ── 生成：重新生成会让旧 Token 失效，弹窗里已写明风险 ──
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.Add,
                        label = if (creating) "生成中" else "生成 Token",
                        tint = if (!creating) MiuixTheme.colorScheme.primary else null,
                        onClick = if (!creating) ({ onCreate() }) else null,
                    ),
                ),
                showDivider = true,
            )
        }
    }
}

/**
 * Webhook 配置卡片：输入框 + 保存 / 发送测试 / 推送格式三个动作。
 *
 * @param url 输入框当前内容（草稿）
 * @param saving 保存请求进行中，为 true 时禁用保存动作
 * @param testing 测试请求进行中，为 true 时禁用测试动作
 * @param onShowPayload 打开推送数据格式底部弹窗
 */
@Composable
private fun WebhookCard(
    url: TextFieldValue,
    onUrlChange: (TextFieldValue) -> Unit,
    saving: Boolean,
    testing: Boolean,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onShowPayload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasUrl = url.text.isNotBlank()

    Card(modifier = modifier) {
        Column {
            // 头部：字段名 + 说明
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            ) {
                Text(
                    text = "Webhook URL",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "用来被动接收订单通知",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                // 撑高占位：本卡头行没有 CopyButton（它的 40dp 最小热区），
                // 不补这一下会比 user_id / API Token 两张卡的头行矮一截
                Spacer(Modifier.height(HeaderRowMinHeight))
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // 输入框：普通文本输入，用 TextField（InputField 是搜索框专用形态）
            // 未配置时 label 作占位提示，已填则 label 上浮
            TextField(
                value = url,
                onValueChange = onUrlChange,
                label = "https://",
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )

            // 未配置时的引导提示，避免用户不知道空地址意味着什么
            if (!hasUrl) {
                Text(
                    text = "填写你的服务器地址，每当有订单时爱发电会请求这个地址。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
                )
            }

            // 三个动作：保存 / 发送测试 / 推送格式；url 为空或请求进行中时置灰
            CardActionBar(
                items = listOf(
                    // ── 保存：写入 Webhook 地址 ──
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.Download,
                        label = if (saving) "保存中" else "保存",
                        tint = if (!saving && hasUrl) MiuixTheme.colorScheme.primary else null,
                        onClick = if (!saving && hasUrl) ({ onSave() }) else null,
                    ),
                    // ── 发送测试：让服务端立刻推一条测试订单 ──
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.Send,
                        label = if (testing) "发送中" else "发送测试",
                        onClick = if (!testing && hasUrl) ({ onTest() }) else null,
                    ),
                    // ── 推送格式：查看服务端会推过来的 JSON 结构 ──
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.File,
                        label = "推送格式",
                        onClick = onShowPayload,
                    ),
                ),
                showDivider = true,
            )
        }
    }
}

/**
 * 卡片头行的统一最小高度。
 *
 * user_id / API Token 两张卡的头行内有 CopyButton，其 IconButton 最小热区是
 * 40.dp（Default 档 = 24.dp 图标 + 16.dp 留白），头行高度被它撑到 40.dp；
 * Webhook URL 卡头行没有复制按钮，只按文字行高排，会比另两张矮一截。
 * 这里用一个不可见的 Spacer 补齐，使三张卡的头行严格等高。
 */
private val HeaderRowMinHeight = 40.dp

/** Token 打码展示占位（长度与真实 token 无关，纯视觉）。 */
private const val MASKED_TOKEN = "••••••••••••••••••••••••••••••••" // 32 个

private const val DEVELOPER_INTRO =
    "爱发电提供了 webhook 和 API 两种方式为开发者提供便利。\n" +
        "Webhook 功能需要配置好 URL，每当有订单时会请求配置的 URL；\n" +
        "API 功能需要开发者通过 Token 生成 sign 签名，主动请求接口查询历史订单。"

private val WEBHOOK_PAYLOAD_NOTE =
    "每当有订单时，爱发电会向你配置的 URL 发起一次请求，" +
        "数据为下面的 JSON 格式。你的服务器需要返回 JSON，且 ec 必须为 200，否则视为推送失败。"

/** Webhook 推送的订单示例（与官网一致，字段解释见开发者文档）。 */
private val WEBHOOK_PAYLOAD_SAMPLE = """
{
  "ec": 200,
  "em": "ok",
  "data": {
    "type": "order",
    "order": {
      "out_trade_no": "202106232138371083454010626",
      "user_id": "adf397fe8374811eaacee52540025c377",
      "plan_id": "a45353328af911eb973052540025c377",
      "month": 1,
      "total_amount": "5.00",
      "show_amount": "5.00",
      "status": 2,
      "remark": "",
      "redeem_id": "",
      "product_type": 0,
      "discount": "0.00",
      "sku_detail": [],
      "address_person": "",
      "address_phone": "",
      "address_address": ""
    }
  }
}
""".trimIndent()

/** 你的服务器必须返回的响应格式：仅 ec 为 200 时才视为推送成功。 */
private const val WEBHOOK_RESPONSE_SAMPLE = """{"ec":200,"em":""}"""