package com.hrsthrt74.afdian.data.mcp

import android.app.Application
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MCP 服务器的生命周期控制器：订阅设置（开关/端口/令牌），自动启停与热重启。
 *
 * 由 AfdianApplication 创建并持有（进程级单例），随应用进程存活：
 * 应用活着就能连，应用被杀即断 —— 与用户选定的「随进程存活」方案一致，
 * 不做前台服务、不挂常驻通知。
 *
 * 热更新语义：开关关闭 -> stop；开启且 (端口, 令牌) 与运行中实例一致 -> 不动；
 * 否则按新参数重启（用户改端口/重置令牌后立即生效，无需重启应用）。
 *
 * @param context 应用上下文（读设置与构建取数工具）
 * @param appScope 控制器内部协程的作用域（Application 级）
 */
class McpServerController(
    context: Application,
    private val appScope: CoroutineScope,
) {
    private val settings = SettingsStore(context)
    private val handler = McpRequestHandler(buildDefaultMcpTools(context))
    private var server: McpServer? = null

    /** 串行化启停：combine 的连续发射可能重入，必须保证 stop/start 成对不交错 */
    private val lifecycleMutex = Mutex()
    private var watchJob: Job? = null

    /** 当前是否在监听（设置页展示「运行中」状态用）。 */
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** 当前监听端口（与 [running] 配对展示；未运行时是设置里的目标端口）。 */
    private val _port = MutableStateFlow(MCP_DEFAULT_PORT)
    val port: StateFlow<Int> = _port.asStateFlow()

    /** 开始订阅设置流；由 AfdianApplication 在 onCreate 调用一次。 */
    fun start() {
        if (watchJob != null) return
        watchJob = appScope.launch {
            combine(settings.mcpEnabled, settings.mcpPort, settings.mcpTokenFlow) { enabled, port, token ->
                Triple(enabled, port, token)
            }.collect { (enabled, port, token) ->
                applySettings(enabled, port, token)
            }
        }
    }

    /** 停止订阅并关掉服务器；Application 不退出，这里主要留给测试与完整性。 */
    fun shutdown() {
        watchJob?.cancel()
        watchJob = null
        appScope.launch { lifecycleMutex.withLock { stopServer() } }
    }

    /** 按最新设置对齐服务器状态。 */
    private suspend fun applySettings(enabled: Boolean, port: Int, token: String?) {
        lifecycleMutex.withLock {
            _port.value = port
            val current = server
            when {
                // 关闭：一律停
                !enabled -> stopServer()
                // 开着且参数没变：什么都不做（每次 DataStore 写盘都会重发这条组合流）
                current != null && current.isRunning && current.matches(port, token) -> Unit
                // 开启但令牌还没生成：跳过，等设置页 ensureMcpToken 写入后由下一次发射启动
                token.isNullOrBlank() -> {
                    AppLog.w(TAG, "MCP 已开启但访问令牌尚未生成，等待设置页生成后再启动")
                }
                // 开启（或参数变化）：按当前参数重启
                else -> {
                    stopServer()
                    server = McpServer(port = port, bearerToken = token, handler = handler).also { it.start() }
                    _running.value = true
                    AppLog.d(TAG, "MCP 服务器运行中：127.0.0.1:$port")
                }
            }
        }
    }

    private suspend fun stopServer() {
        server?.stop()
        server = null
        _running.value = false
    }

    /** 运行中实例的参数比对：端口与令牌都没变才免重启。 */
    private fun McpServer.matches(port: Int, token: String?): Boolean = this.port == port && bearerToken == token

    private companion object {
        const val TAG = "McpController"
    }
}
