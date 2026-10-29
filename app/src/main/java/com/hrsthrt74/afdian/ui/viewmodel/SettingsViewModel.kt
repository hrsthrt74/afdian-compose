package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AfdianApplication
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.ai.AiChatClient
import com.hrsthrt74.afdian.data.ai.AiConfigDraft
import com.hrsthrt74.afdian.data.ai.AiMessage
import com.hrsthrt74.afdian.data.ai.AiProviderPreset
import com.hrsthrt74.afdian.data.ai.AiStreamEvent
import com.hrsthrt74.afdian.data.ai.AiThinkingLevel
import com.hrsthrt74.afdian.data.ai.sharedAiHttpClient
import com.hrsthrt74.afdian.data.mcp.MCP_DEFAULT_PORT
import com.hrsthrt74.afdian.data.network.AfdianRoute
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_DEFAULT
import androidx.compose.ui.graphics.Color
import com.hrsthrt74.afdian.data.settings.ThemeColorPreset
import kotlinx.coroutines.runBlocking
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.settings.ThemeRepository
import com.hrsthrt74.afdian.data.update.UpdateChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)

    // 直接把 DataStore 的 flow 转成 StateFlow，写入后自动回推 UI
    val startupPage: StateFlow<Int> = store.startupPage
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 配色模式：0=跟随系统 1=浅色 2=深色 */
    val colorMode: StateFlow<Int> = store.colorMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 高级过渡动画总开关，默认开启 */
    val advancedTransition: StateFlow<Boolean> = store.advancedTransition
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 更新通道：正式版 / Actions 构建，默认正式版 */
    val updateChannel: StateFlow<UpdateChannel> = store.updateChannel
        .stateIn(viewModelScope, SharingStarted.Eagerly, UpdateChannel.Stable)

    /** 页面内边缘滑动返回手势，默认开启 */
    val gestureBack: StateFlow<Boolean> = store.gestureBack
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 系统预测性返回开关，默认开启。 */
    val predictiveBack: StateFlow<Boolean> = store.predictiveBack
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 浏览历史记录开关，默认开启。 */
    val historyEnabled: StateFlow<Boolean> = store.historyEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 首页顶栏账号切换器开关，默认关闭；仅在已登录多账号时才在设置页露出。 */
    val homeAccountSwitcherEnabled: StateFlow<Boolean> = store.homeAccountSwitcherEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 金额类数据卡显示「元」单位，默认关闭。 */
    val showIncomeUnit: StateFlow<Boolean> = store.showIncomeUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 人数/次数类数据卡显示「人」「次」单位，默认关闭。 */
    val showCountUnit: StateFlow<Boolean> = store.showCountUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 数据卡数字滚动动画开关，默认开启。 */
    val rollingNumberEnabled: StateFlow<Boolean> = store.rollingNumberEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 数据卡数值是否使用窄体可变字体，默认关闭（关闭 = 系统默认字体）。 */
    val dataCardCustomFontEnabled: StateFlow<Boolean> = store.dataCardCustomFontEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 数据卡数值字体的 wdth（宽度）轴，默认 60。 */
    val dataCardFontWidth: StateFlow<Int> = store.dataCardFontWidth
        .stateIn(viewModelScope, SharingStarted.Eagerly, DATACARD_FONT_WIDTH_DEFAULT)

    /** 外部爱发电链接是否优先使用官方客户端，默认关闭即优先 Custom Tabs。 */
    val preferOfficialClient: StateFlow<Boolean> = store.preferOfficialClient
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * 网络优先线路，默认 ifdian.net。
     *
     * stateIn 的初值必须与 SettingsStore 的缺省值（[AfdianRoute.Default]）同源，
     * 否则下拉会先渲染默认档、等 DataStore 读回再跳一帧（即「Monet 闪一下」那类问题）。
     */
    val networkRoute: StateFlow<AfdianRoute> = store.networkRoute
        .stateIn(viewModelScope, SharingStarted.Eagerly, AfdianRoute.Default)

    fun setHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setHistoryEnabled(enabled) }
    }

    /** 切换首页账号切换器；DataStore 回推后首页顶栏立即显示/隐藏头像。 */
    fun setHomeAccountSwitcherEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setHomeAccountSwitcherEnabled(enabled) }
    }

    /** 切换底部导航栏样式（0=常规 1=悬浮），DataStore 回推后 MainScreen 立即重组生效 */
    fun setBottomBarStyle(style: Int) {
        viewModelScope.launch { store.setBottomBarStyle(style) }
    }

    fun setShowIncomeUnit(enabled: Boolean) {
        viewModelScope.launch { store.setShowIncomeUnit(enabled) }
    }

    fun setShowCountUnit(enabled: Boolean) {
        viewModelScope.launch { store.setShowCountUnit(enabled) }
    }

    /** 切换数字滚动动画；DataStore 回推后 MainScreen 更新全局开关，卡片立即切换渲染方式 */
    fun setRollingNumberEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setRollingNumberEnabled(enabled) }
    }

    /** 切换数据卡数值窄体字体；DataStore 回推后 MainActivity 更新全局字体，卡片立即换字体 */
    fun setDataCardCustomFontEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setDataCardCustomFontEnabled(enabled) }
    }

    /** 写入数据卡数值字体宽度（wdth）；只在滑块松手时调用，避免拖动过程每帧写库 */
    fun setDataCardFontWidth(width: Int) {
        viewModelScope.launch { store.setDataCardFontWidth(width) }
    }

    fun setPreferOfficialClient(enabled: Boolean) {
        viewModelScope.launch { store.setPreferOfficialClient(enabled) }
    }

    /** 切换优先线路；写入后 Application 的订阅立刻更新全局值，下一次请求就按新链走。 */
    fun setNetworkRoute(route: AfdianRoute) {
        viewModelScope.launch { store.setNetworkRoute(route) }
    }

    /**
     * 切换更新通道。
     *
     * 写库后立刻清掉上一次检查的残留结果：旧通道的「发现新版本」对话框
     * 留在新通道上会让人以为这个通道真有更新。
     */
    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            store.setUpdateChannel(channel)
            UpdateCenter.reset(getApplication())
        }
    }

    /** 重置管理页卡片布局为默认顺序；成功/失败都给 Toast 反馈，失败额外记警告日志。 */
    fun resetManagementLayout() {
        viewModelScope.launch {
            runCatching { store.resetManagementLayout() }
                .onSuccess { Toast.makeText(getApplication(), "已重置管理页卡片布局", Toast.LENGTH_SHORT).show() }
                .onFailure { error ->
                    AppLog.w("Settings", "重置管理页卡片布局失败", error)
                    Toast.makeText(getApplication(), "重置失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
        }
    }

    /** 重置首页创作者数据卡片布局为默认顺序；反馈逻辑同 [resetManagementLayout]。 */
    fun resetHomeCreatorDataLayout() {
        viewModelScope.launch {
            runCatching { store.resetHomeCreatorDataLayout() }
                .onSuccess { Toast.makeText(getApplication(), "已重置首页创作者数据卡片布局", Toast.LENGTH_SHORT).show() }
                .onFailure { error ->
                    AppLog.w("Settings", "重置首页创作者数据卡片布局失败", error)
                    Toast.makeText(getApplication(), "重置失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun setAdvancedTransition(enabled: Boolean) {
        viewModelScope.launch { store.setAdvancedTransition(enabled) }
    }

    fun setGestureBack(enabled: Boolean) {
        viewModelScope.launch { store.setGestureBack(enabled) }
    }

    fun setPredictiveBack(enabled: Boolean) {
        AppLog.d("Settings", "预测性返回已${if (enabled) "开启" else "关闭"}")
        viewModelScope.launch { store.setPredictiveBack(enabled) }
    }

    fun setColorMode(mode: Int) {
        viewModelScope.launch { store.setColorMode(mode) }
    }

    /**
     * 写入「主题」选择（false=MIUIX true=Monet）。
     *
     * 这里刻意只提供写入方、不提供读取的 StateFlow：当前选中的主题由全局主题的
     * `MiuixTheme.isDynamicColor` 驱动。因为本 VM 是进入设置页时才创建的，
     * 读 StateFlow 的初值会先按「MIUIX」渲染一帧，导致下拉闪一下。
     */
    fun setMonetEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setMonetEnabled(enabled) }
    }

    /**
     * 写入主题色。
     *
     * 同样只有写入方：外观页高亮的色球直接读全局主题下发的
     * [com.hrsthrt74.afdian.ui.theme.LocalThemeColor]，那是「真正生效」的那一档，
     * 不受本 VM 初值影响。
     */
    fun setThemeColor(preset: ThemeColorPreset) {
        viewModelScope.launch { store.setThemeColor(preset) }
    }

    /** 保存自定义主题色的具体色值（调色盘确认后调用） */
    fun setCustomThemeColor(color: Color) {
        viewModelScope.launch { store.setCustomThemeColor(color) }
    }

    fun setStartupPage(index: Int) {
        viewModelScope.launch { store.setStartupPage(index) }
    }

    fun setEnableBlur(enabled: Boolean) {
        viewModelScope.launch { ThemeRepository.setEnableBlur(getApplication(), enabled) }
    }

    fun setProgressiveBlur(enabled: Boolean) {
        viewModelScope.launch { ThemeRepository.setProgressiveBlur(getApplication(), enabled) }
    }

    // ── AI 集成（BYOK）────────────────────────────────────────────────────────

    /**
     * AI 配置草稿（不含 Key 明文）；设置页与各功能页的「能否发起请求」判断共用。
     *
     * 初值必须是**真实值**，不能是「先给个默认档、等 DataStore 回推再改」：
     * 设置页一进来就按初值画第一帧，初值若是默认档，思考滑块会先停在「高」，
     * 真实值到达后再被弹簧动画滑走——这就是「进入页面闪一下」。
     *
     * 做法是在 ViewModel 构造时同步取一次首值：DataStore 的 `data` 是热流，
     * App 启动期间已有订阅者（如日志开关、优先线路）读过，这里基本是内存快照，微秒级；
     * 只有冷启动后直奔本页才会真读一次盘，代价是一次可以忽略的等待，
     * 换来的是页面第一帧就是最终状态。
     */
    val aiConfigDraft: StateFlow<AiConfigDraft> = store.aiConfigDraft.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        store.aiConfigDraft.firstBlocking(
            AiConfigDraft(
                preset = AiProviderPreset.DeepSeek,
                baseUrl = "",
                model = "",
                hasKey = false,
            ),
        ),
    )

    /** 正在进行的「测试连接」请求；期间按钮禁用防重复触发。 */
    private val _aiTesting = MutableStateFlow(false)
    val aiTesting = _aiTesting.asStateFlow()

    fun setAiPreset(preset: AiProviderPreset) {
        viewModelScope.launch { store.setAiPreset(preset) }
    }

    fun setAiBaseUrl(raw: String) {
        viewModelScope.launch { store.setAiBaseUrl(raw) }
    }

    fun setAiModel(raw: String) {
        viewModelScope.launch { store.setAiModel(raw) }
    }

    /** 保存 API Key；传 null 表示清除已保存的 Key。 */
    fun setAiApiKey(plainKey: String?) {
        viewModelScope.launch { store.setAiApiKey(plainKey) }
    }

    /**
     * 设置页「思考」滑块的唯一落盘入口：一个档位同时决定开关与强度。
     *
     * 两个字段在数据层一次写完（见 SettingsStore.setAiThinking）：
     * 分两次写会产生中间态，滑块会先跳回上一档再过去。
     * 关闭档 effort 为 null，不动强度——关闭时本来就不发强度，保留旧值无副作用。
     */
    fun setAiThinkingLevel(level: AiThinkingLevel) {
        viewModelScope.launch { store.setAiThinking(level.mode, level.effort) }
    }

    /**
     * 「测试连接」：用当前配置发一条极简请求。
     * 结果经回调返回（成功=模型回的首行摘要 / 失败=可读错误），异常不抛出协程。
     *
     * 刻意用回调而不是 StateFlow 装结果：结果是一次性事件（toast 呈现），
     * 塞进状态流反而要处理「下一次测试前清旧结果」的额外状态。
     */
    fun testAiConnection(onResult: (Result<String>) -> Unit) {
        if (_aiTesting.value) {
            onResult(Result.failure(IllegalStateException("正在测试中，请稍候")))
            return
        }
        viewModelScope.launch {
            _aiTesting.value = true
            try {
                // 测试永远按「当前已保存的配置」来，与 UI 输入框里未保存的草稿无关——
                // 保存动作与测试动作分离，避免「没保存就测」出现配置不一致的困惑
                val config = store.aiConfig()
                    ?: return@launch onResult(Result.failure(IllegalStateException("请先完整填写并保存供应商配置")))
                // 不再限制 max_tokens（新模型普遍不支持该参数），改用极简提示词压住输出长度：
                // 验证的是「能连上、能鉴权、模型存在」，不是生成质量
                val client = AiChatClient(sharedAiHttpClient, config)
                var reply: String? = null
                client.stream(listOf(AiMessage.user("Hi，只回复 OK，不要任何解释")))
                    .collect { event ->
                        if (event is AiStreamEvent.Done) reply = event.text
                    }
                // 不回显整段回答：仅取首行前 20 字符做 toast 摘要
                val preview = reply.orEmpty().lineSequence().firstOrNull()?.take(20).orEmpty()
                onResult(Result.success(preview.ifBlank { "连接成功" }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "AI 测试连接失败", e)
                onResult(Result.failure(e))
            } finally {
                _aiTesting.value = false
            }
        }
    }

    // ── MCP 本地服务器 ───────────────────────────────────────────────────────

    /** 服务器控制器（Application 级单例，随进程存活）。 */
    private val mcpController = (getApplication() as AfdianApplication).mcpServerController

    /** MCP 开关（初值取真实值，理由同 [aiConfigDraft]：避免开关先画成「关」再跳到「开」）。 */
    val mcpEnabled: StateFlow<Boolean> = store.mcpEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.mcpEnabled.firstBlocking(false))

    /** 监听端口（同样同步取首值，避免输入框先显示默认端口再跳）。 */
    val mcpPort: StateFlow<Int> = store.mcpPort
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.mcpPort.firstBlocking(MCP_DEFAULT_PORT))

    /** 服务器是否在监听（设置页展示运行状态）。 */
    val mcpRunning: StateFlow<Boolean> = mcpController.running

    /** 访问令牌明文（展示/复制用；重置后由 resetMcpToken 回写）。 */
    private val _mcpToken = MutableStateFlow<String?>(null)
    val mcpToken = _mcpToken.asStateFlow()

    init {
        // 令牌是低频读：进设置页时拉一次，重置动作负责更新
        viewModelScope.launch { _mcpToken.value = store.mcpToken() }
    }

    /**
     * 切换 MCP 服务器开关；开启时顺手确保令牌存在（没有则生成），
     * 控制器检测到「开关+令牌齐了」就启动监听。
     */
    fun setMcpEnabled(enabled: Boolean) {
        viewModelScope.launch {
            store.setMcpEnabled(enabled)
            if (enabled) _mcpToken.value = store.ensureMcpToken()
        }
    }

    /** 保存监听端口；控制器检测到变化会自动重启服务器。 */
    fun setMcpPort(port: Int) {
        viewModelScope.launch { store.setMcpPort(port) }
    }

    /** 重置访问令牌：旧令牌立即作废（外部客户端需换成新令牌）。 */
    fun resetMcpToken() {
        viewModelScope.launch { _mcpToken.value = store.resetMcpToken() }
    }

    private companion object {
        const val TAG = "Settings"
    }
}

/**
 * 同步取一个 Flow 的首个值，失败时用 [fallback] 兜底。
 *
 * 专供设置页的 `stateIn` 初值：这些页面一进来就按初值画第一帧，
 * 初值若是「默认值」再等 DataStore 回推，开关/滑块就会肉眼可见地跳一下。
 * DataStore 的 `data` 是热流，App 启动期间已有订阅者读过，这里基本是内存快照（微秒级）；
 * 只有冷启动后直奔设置页才会真读一次盘，代价是一次可以忽略的等待。
 */
private fun <T> Flow<T>.firstBlocking(fallback: T): T =
    runCatching { runBlocking { first() } }.getOrDefault(fallback)
