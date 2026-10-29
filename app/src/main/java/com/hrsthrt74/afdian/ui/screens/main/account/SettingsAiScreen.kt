package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.ai.AiProviderPreset
import com.hrsthrt74.afdian.data.ai.AiThinkingLevel
import com.hrsthrt74.afdian.data.ai.normalizeAiBaseUrl
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.CopyButton
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults.SliderHapticEffect
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 供应商下拉选项：声明顺序即展示顺序。
 */
private val presetItems: List<DropdownItem> =
    AiProviderPreset.entries.map { DropdownItem(text = it.title) }

/**
 * 设置 → AI 助手。
 *
 * BYOK（Bring Your Own Key）：应用不内置任何模型，用户在自带供应商处申请 API Key，
 * 本页只负责「选预设 → 填端点/Key/模型 → 测试连通」。协议统一走 OpenAI 兼容
 * `/chat/completions` 流式，见 data/ai/AiChatClient。
 *
 * 布局：供应商（预设 + 申请提示）→ 连接（Base URL / 模型 / Key + 保存 + 测试）
 * → MCP 服务器 → 数据说明。
 */
@Composable
fun SettingsAiScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    // 配置草稿：ViewModel 用同步取到的真实值做初值，所以第一帧就是最终状态，不会跳
    val draft by viewModel.aiConfigDraft.collectAsState()
    val testing by viewModel.aiTesting.collectAsState()
    val context = LocalContext.current
    val toast: (String) -> Unit = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }

    // 本地编辑态：以 DataStore 值为 key 重建，保存成功回推后输入框自动「归位」
    var baseUrlInput by remember(draft.baseUrl) { mutableStateOf(draft.baseUrl) }
    var modelInput by remember(draft.model) { mutableStateOf(draft.model) }
    // API Key 输入框永远从空开始（明文 Key 不回显）；hasKey 变化时清空输入
    var keyInput by remember(draft.hasKey) { mutableStateOf("") }
    // Key 是否明文显示：默认掩码，用户点眼睛临时核对
    var showKey by remember { mutableStateOf(false) }

    // 「有未保存改动」判定：Key 输入框有内容即视为要更换 Key
    val baseUrlDirty = baseUrlInput.trim() != draft.baseUrl
    val modelDirty = modelInput.trim() != draft.model
    val keyDirty = keyInput.isNotBlank()
    val anyDirty = baseUrlDirty || modelDirty || keyDirty

    SettingsPageScaffold(
        title = "AI",
        onBack = onBack,
        backStack = backStack,
    ) {
        item { SmallTitle(text = "AI 集成", modifier = Modifier.padding(start = 4.dp)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                OverlaySpinnerPreference(
                    title = "供应商预设",
                    items = presetItems,
                    selectedIndex = AiProviderPreset.entries.indexOf(draft.preset),
                    onSelectedIndexChange = { index ->
                        val preset = AiProviderPreset.entries[index]
                        viewModel.setAiPreset(preset)
                        // 切换预设时跟随填充该家官方端点与常用模型（自定义档不覆盖，保留现值手改）
                        if (preset.defaultBaseUrl.isNotBlank()) viewModel.setAiBaseUrl(preset.defaultBaseUrl)
                        if (preset.defaultModel.isNotBlank()) viewModel.setAiModel(preset.defaultModel)
                    },
                )
            }
        }
        item {
            // Base URL：OpenAI 兼容端点（不含 /chat/completions 路径，粘贴完整端点也能自动剥掉）
            TextField(
                value = baseUrlInput,
                onValueChange = { baseUrlInput = it },
                label = "Base URL",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )
        }
        item {
            // 模型名：如 deepseek-chat / gpt-4o-mini，切换预设时自动填常用值
            TextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                label = "模型名",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )
        }
        item {
            // API Key
            TextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = if (draft.hasKey) "输入以更新 API Key" else "API Key",
                useLabelAsPlaceholder = true,
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )
        }
        item {
            if (anyDirty) {
                Button(
                    onClick = {
                        // Base URL 是唯一需要校验的字段：非法值（如漏协议头）在数据层会被拒绝写入，
                        // 这里先拦下来给用户明确提示，避免「点了保存却没生效」的困惑
                        if (baseUrlInput.isNotBlank() && normalizeAiBaseUrl(baseUrlInput) == null) {
                            toast("Base URL 无效：需要以 http(s):// 开头")
                            return@Button
                        }
                        viewModel.setAiBaseUrl(baseUrlInput)
                        viewModel.setAiModel(modelInput)
                        if (keyDirty) viewModel.setAiApiKey(keyInput.trim())
                        // 输入框由 remember(draft.*) 在 DataStore 回推后自动归位；Key 框始终清空
                        keyInput = ""
                        toast("已保存")
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                ) {
                    Text("保存配置")
                }
            }
            Button(
                onClick = {
                    viewModel.testAiConnection { result ->
                        result.onSuccess {
                            toast("连接成功${if (it.isNotBlank()) "：$it" else ""}")
                        }.onFailure { error ->
                            toast("连接失败：${error.message ?: "未知错误"}")
                        }
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), // 多一点下边距因为下面挨着个滑块
            ) {
                Text(if (testing) "测试中…" else "测试连接")
            }
        }

        // ── 思考 ──
        // 推理模型（DeepSeek、MiMo v2.6 等）回答前先输出思维链，耗时可能几十秒。
        // 一根四档滑块搞定：最左 = 关闭（只发 thinking.type=disabled），
        // 往右依次 low / high / max（开启 + 对应 reasoning_effort），
        // 不再有「跟随服务端」这种界面上说不清的中间态。
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                EffortSlider(
                    level = AiThinkingLevel.of(draft.thinkingMode, draft.reasoningEffort),
                    onLevelChange = viewModel::setAiThinkingLevel,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }

        // ── MCP 服务器 ──
        item { SmallTitle(text = "MCP", modifier = Modifier.padding(start = 4.dp)) }
        item {
            val mcpEnabled by viewModel.mcpEnabled.collectAsState()
            val mcpPort by viewModel.mcpPort.collectAsState()
            val mcpRunning by viewModel.mcpRunning.collectAsState()
            val mcpToken by viewModel.mcpToken.collectAsState()
            // 端口本地编辑态（与 store 值同步初始化，保存后回推归位）
            var portInput by remember(mcpPort) { mutableStateOf(mcpPort.toString()) }
            val portDirty = portInput.toIntOrNull()?.let { it != mcpPort } == true
            // 「重置令牌」确认弹窗：旧令牌立即作废，已连接的客户端会全部掉线
            val resetTokenDialogState = rememberDialogState()

            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                SwitchPreference(
                    checked = mcpEnabled,
                    onCheckedChange = viewModel::setMcpEnabled,
                    title = "MCP 服务器",
                    summary = if (mcpRunning) {
                        "运行中（随应用进程存活）"
                    } else {
                        "供外部 AI 应用读取爱发电数据"
                    },
                )
                // 开关打开时展开端口 / 地址 / 令牌配置，收起时纵向收缩动画隐藏
                AnimatedVisibility(
                    visible = mcpEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        BasicComponent(
                            title = "端口",
                            summary = "需要在 1024-65535 之间",
                            endActions = {
                                TextField(
                                    value = portInput,
                                    onValueChange = { portInput = it.filter { ch -> ch.isDigit() }.take(5) },
                                    label = "端口号",
                                    useLabelAsPlaceholder = true,
                                    singleLine = true,
                                    insideMargin = DpSize(16.dp, 8.dp),
                                    trailingIcon = {
                                        if (portDirty) {
                                            IconButton(
                                                onClick = {
                                                    val port = portInput.toIntOrNull()
                                                    if (port == null || port < 1024 || port > 65535) {
                                                        toast("端口无效：需要在 1024-65535 之间")
                                                    } else {
                                                        viewModel.setMcpPort(port)
                                                        toast("端口已保存")
                                                    }
                                                }
                                            ) {
                                                Icon(MiuixIcons.Demibold.Ok, "保存端口", modifier = Modifier.size(20.dp))
                                            }
                                        } else {
                                            Spacer(Modifier.width(16.dp))
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                )
                            }
                        )
                        // 服务器地址
                        BasicComponent(
                            title = "服务器地址",
                            summary = "http://127.0.0.1:$mcpPort/mcp",
                            endActions = {
                                // 应用内复制按钮：自带震动反馈 + 成功图标切换动画，无需手动操作剪贴板
                                CopyButton(
                                    text = "http://127.0.0.1:$mcpPort/mcp",
                                    toastText = "地址已复制",
                                    contentDescription = "复制连接地址",
                                )
                            },
                        )
                        // 访问令牌
                        BasicComponent(
                            title = "Auth Token",
                            summary = mcpToken?.let { if (it.length > 16) "${it.take(16)}*****" else it }
                                ?: "尚未生成（开启服务器时自动生成）",
                            endActions = {
                                IconButton(
                                    onClick = { resetTokenDialogState.show() },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(imageVector = MiuixIcons.Refresh, contentDescription = "重置令牌")
                                }
                                // 应用内复制按钮；令牌未生成时禁用置灰，点击无响应
                                CopyButton(
                                    text = mcpToken.orEmpty(),
                                    enabled = mcpToken != null,
                                    toastText = "令牌已复制",
                                    contentDescription = "复制令牌",
                                )
                            },
                        )
                    }
                }
            }
            // 重置令牌确认：旧令牌立即作废属于破坏性操作
            AppDialog(
                state = resetTokenDialogState,
                title = "重置访问令牌",
                summary = "重置后旧令牌立即失效，已配置的 AI 客户端需要换成新令牌才能继续连接。",
                confirmText = "重置",
                destructive = true,
                onConfirm = {
                    viewModel.resetMcpToken()
                    toast("令牌已重置")
                },
            )
        }
    }
}

/**
 * 思考强度滑块。
 *
 * 结构刻意保持极简：只有顶部一行文本 + 一根滑块。
 * 顶部文本左半是标题「思考强度」（默认文字色），右半是当前档位名（主题色强调），整行水平居中；
 * 不再展示 summary 说明行与轨道下方的刻度文案——档位名已经在顶部给出了。
 *
 * @param level 当前档位（由持久化的「开关 + 强度」反查得出）
 * @param onLevelChange 松手时回调，由上层落盘（拖动过程中不写 DataStore）
 */
@Composable
private fun EffortSlider(
    level: AiThinkingLevel,
    onLevelChange: (AiThinkingLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 滑块本地态：拖动时只动本地保证跟手，松手才回传；
    // 反过来直接读 DataStore 会有回推延迟，快速拖动时指针会来回弹
    var sliderIndex by remember(level) { mutableStateOf(level.ordinal.toFloat()) }
    // 手指当前停在的档位：顶部档位名跟着它实时变化，拖动过程中就有反馈
    val shownLevel = AiThinkingLevel.entries[
        sliderIndex.roundToInt().coerceIn(0, AiThinkingLevel.entries.lastIndex)
    ]

    // 测量器：事先量出所有档位名的宽度，用来把文字容器的宽度下限钉死
    val textMeasurer = rememberTextMeasurer()
    // 标题用主字号（比正文大一档），档位名同字号再加粗，作为这一行唯一的重点
    val titleStyle = MiuixTheme.textStyles.main
    val valueStyle = titleStyle.copy(fontWeight = FontWeight.Bold)
    val density = LocalDensity.current
    // 最宽的那个档位名的宽度（dp）：容器宽度不跟着当前文案变，整行才不会被推得左右晃。
    // 量的是加粗后的样式，否则真实排版比测量值宽，宽度约束会失效
    val labelWidth = remember(valueStyle, density) {
        with(density) {
            AiThinkingLevel.entries.maxOf {
                textMeasurer.measure(text = it.label, style = valueStyle, softWrap = false, maxLines = 1).size.width
            }.toDp()
        }
    }

    Column(modifier = modifier) {
        // 顶部文本：「思考强度」+ 当前档位名，两者水平居中排成一行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 标题：不设颜色，跟随主题默认文字色
            Text(
                text = "思考强度",
                style = titleStyle,
            )
            // 标题与档位名之间的间距，避免两个词黏在一起
            Spacer(modifier = Modifier.width(8.dp))
            // 档位名：主题色强调，这是这一行唯一的视觉重点。
            // 宽度下限 = 最宽档位名，切档时容器不缩不放，整行位置恒定；
            // 用 widthIn 而不是 width，万一测量有 1px 误差也不会把文字挤换行。
            Box(
                modifier = Modifier.widthIn(min = labelWidth),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = shownLevel,
                    transitionSpec = {
                        // 档位升高（往右拖）：新文字从下方淡入、旧的向上淡出；降低时反过来
                        val rising = targetState.ordinal > initialState.ordinal
                        val dir = if (rising) 1 else -1
                        // 容器宽度已被 widthIn 钉住、各档位名高度一致，尺寸动画实际不会产生位移
                        (fadeIn(tween(160)) + slideInVertically(tween(160)) { height -> dir * height / 2 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(120)) { height -> -dir * height / 2 })
                    },
                    label = "思考强度档位",
                ) { target ->
                    Text(
                        text = target.label,
                        style = valueStyle,
                        color = MiuixTheme.colorScheme.primary,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
        }
        // 滑块本体：值 = 档位序号（0 关闭 / 1-3 强度），steps 让刻度吸附到每一档
        Slider(
            value = sliderIndex,
            onValueChange = { raw ->
                // 带 steps 时 Miuix 已把值吸附到整档，这里再 round 一次防浮点误差
                sliderIndex = raw.roundToInt()
                    .coerceIn(0, AiThinkingLevel.entries.lastIndex)
                    .toFloat()
            },
            onValueChangeFinished = {
                // 松手才落盘：一次拖动最多写一次 DataStore
                onLevelChange(shownLevel)
            },
            valueRange = 0f..AiThinkingLevel.entries.lastIndex.toFloat(),
            steps = AiThinkingLevel.entries.size - 2,
            showKeyPoints = true,
            height = 36.dp, // 好看
            hapticEffect = SliderHapticEffect.Step,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}
