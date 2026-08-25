package com.hrsthrt74.afdian

import android.util.Log

/**
 * 全局日志门面（facade）：全 app 统一从这里打日志，而不是直接调 android.util.Log。
 *
 * 设计目标：
 * - 「调试日志」开关关闭（默认，适合普通用户）：只输出 W/E 级别，
 *   保证出问题时 Logcat 仍有报错可查，但不会刷屏、不泄露运行细节；
 * - 开关打开（排查问题模式）：额外输出 D 级详细日志（网络请求、耗时、流程节点等）。
 *
 * 开关状态持久化在 SettingsStore（DataStore 键 debug_logging），
 * 由 MainActivity 的 AfdianApp 订阅后写入 [debugEnabled]，
 * 进程内全局生效，切换后无需重启 App。
 *
 * 安全红线：任何调用方都不得把账号、密码、auth_token、Cookie 等
 * 敏感字段拼进日志消息里。
 */
object AppLog {

    /**
     * 是否输出调试级（D）日志。
     * @Volatile 保证 UI 线程写入后，网络/IO 线程能立即读到最新值。
     */
    @Volatile
    var debugEnabled: Boolean = false

    /** 调试日志：仅在开关打开时输出 */
    fun d(tag: String, message: String) {
        if (debugEnabled) Log.d(tag, message)
    }

    /** 调试日志（带异常堆栈）：仅在开关打开时输出 */
    fun d(tag: String, message: String, tr: Throwable?) {
        if (debugEnabled) Log.d(tag, message, tr)
    }

    /** 警告日志：无论开关状态始终输出 */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    /** 警告日志（带异常堆栈）：无论开关状态始终输出 */
    fun w(tag: String, message: String, tr: Throwable?) {
        Log.w(tag, message, tr)
    }

    /** 错误日志：无论开关状态始终输出；tr 可为 null 表示只记消息 */
    fun e(tag: String, message: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, message, tr) else Log.e(tag, message)
    }
}
