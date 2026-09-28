package com.hrsthrt74.afdian

import android.util.Log

/**
 * 全局日志门面（facade）：全 app 统一从这里打日志，而不是直接调 android.util.Log。
 *
 * 设计目标：
 * - 「调试日志」开关关闭（默认，适合普通用户）：只输出 W/E 级别，
 *   保证出问题时 Logcat 仍有报错可查，但不会刷屏、不泄露运行细节；
 * - 开关打开（排查问题模式）：额外输出 D 级详细日志（网络请求、耗时、流程节点等）；
 * - 日志落盘：非 null 的 [sink] 会被每条日志调用（D 仍受 [debugEnabled] 控制），
 *   由 `LogStore`（`data/log/LogStore.kt`）接收并异步写入本地文件；
 *   sink 为 null（Application 尚未初始化）时只走 Logcat，不丢不崩。
 *
 * 开关状态持久化在 SettingsStore（DataStore 键 debug_logging），
 * 由 MainActivity 的 AfdianApp 订阅后写入 [debugEnabled]；
 * 「写入日志文件」开关（log_to_file）由 AfdianApplication 订阅后写入 LogStore。
 * 两者都是进程内即时生效，切换后无需重启 App。
 *
 * 安全红线：任何调用方都不得把账号、密码、auth_token、Cookie 等
 * 敏感字段拼进日志消息里。落盘侧（LogStore）另有统一的二次脱敏兜底，
 * 但不要依赖它——红线永远在调用方自己身上。
 */
object AppLog {

    /**
     * 是否输出调试级（D）日志。
     * 初值按构建类型：internal 内测版默认开启——用户设置要等 DataStore 读出后才写入本字段，
     * 若初值固定 false，启动最初几百毫秒（恰恰是崩溃多发段）的 D 日志会缺席。
     * DataStore 读出后由 MainActivity 按用户实际设置校正（手动关过则回落 false）。
     * @Volatile 保证 UI 线程写入后，网络/IO 线程能立即读到最新值。
     */
    @Volatile
    var debugEnabled: Boolean = BuildConfig.INTERNAL_BUILD

    /**
     * 日志落盘回调，由 AfdianApplication 注入 `LogStore::enqueue`。
     *
     * 用函数类型而非直接依赖 LogStore：AppLog 保持零依赖（无 Context、无数据层），
     * 在 Application 之前的任何阶段被调用都只是空转。
     * 实现方保证「不抛异常、不阻塞」，这里不再包一层 try-catch。
     */
    @Volatile
    internal var sink: ((level: Char, tag: String, message: String, tr: Throwable?) -> Unit)? = null

    /** 调试日志：仅在开关打开时输出（Logcat + 落盘） */
    fun d(tag: String, message: String) {
        if (debugEnabled) {
            Log.d(tag, message)
            sink?.invoke('D', tag, message, null)
        }
    }

    /** 调试日志（带异常堆栈）：仅在开关打开时输出 */
    fun d(tag: String, message: String, tr: Throwable?) {
        if (debugEnabled) {
            Log.d(tag, message, tr)
            sink?.invoke('D', tag, message, tr)
        }
    }

    /** 警告日志：无论开关状态始终输出 */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        sink?.invoke('W', tag, message, null)
    }

    /** 警告日志（带异常堆栈）：无论开关状态始终输出 */
    fun w(tag: String, message: String, tr: Throwable?) {
        Log.w(tag, message, tr)
        sink?.invoke('W', tag, message, tr)
    }

    /** 错误日志：无论开关状态始终输出；tr 可为 null 表示只记消息 */
    fun e(tag: String, message: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, message, tr) else Log.e(tag, message)
        sink?.invoke('E', tag, message, tr)
    }
}
