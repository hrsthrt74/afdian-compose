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
     * 数字滚轮（Odometer 动画）日志的统一 tag，`AnimatedValueText` 的动画流程都用它。
     * 定义在此供「数字滚轮日志」开关按 tag 静音：调用方与静音开关共用这一个常量，
     * 避免两边各写一份字符串、改 tag 时悄悄失同步。
     */
    const val TAG_ODOMETER = "Odometer"

    /**
     * 被静音的调试日志 tag 集合：命中集合的 D 级日志不输出（Logcat 与落盘都不输出）。
     * 用途：「调试日志」开着、但某类日志量大到刷屏（如数字滚轮动画，每个数字位都打一条，
     * 动辄上千行），不值得一刀切关掉整个调试日志，就按 tag 单独静音。
     * 默认空集 = 全部照常输出；由 MainActivity 按日志页各「分类日志开关」组装写入，
     * @Volatile 保证 UI 线程写入后，网络/IO 线程立即读到。
     * 只作用于 D 级：W/E 是报错时的最后线索，无论如何都要留。
     */
    @Volatile
    var mutedTags: Set<String> = emptySet()

    /**
     * 是否输出调试级（D）日志。
     * 初值按构建类型：debug 构建默认开启——用户设置要等 DataStore 读出后才写入本字段，
     * 若初值固定 false，启动最初几百毫秒（恰恰是崩溃多发段）的 D 日志会缺席。
     * DataStore 读出后由 MainActivity 按用户实际设置校正（手动关过则回落 false）。
     * @Volatile 保证 UI 线程写入后，网络/IO 线程能立即读到最新值。
     */
    @Volatile
    var debugEnabled: Boolean = BuildConfig.DEBUG

    /**
     * 日志落盘回调，由 AfdianApplication 注入 `LogStore::enqueue`。
     *
     * 用函数类型而非直接依赖 LogStore：AppLog 保持零依赖（无 Context、无数据层），
     * 在 Application 之前的任何阶段被调用都只是空转。
     * 实现方保证「不抛异常、不阻塞」，这里不再包一层 try-catch。
     */
    @Volatile
    internal var sink: ((level: Char, tag: String, message: String, tr: Throwable?) -> Unit)? = null

    /** 调试日志：仅在开关打开、且 tag 未被分类开关静音时输出（Logcat + 落盘） */
    fun d(tag: String, message: String) {
        if (debugEnabled && tag !in mutedTags) {
            Log.d(tag, message)
            sink?.invoke('D', tag, message, null)
        }
    }

    /** 调试日志（带异常堆栈）：仅在开关打开、且 tag 未被分类开关静音时输出 */
    fun d(tag: String, message: String, tr: Throwable?) {
        if (debugEnabled && tag !in mutedTags) {
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
