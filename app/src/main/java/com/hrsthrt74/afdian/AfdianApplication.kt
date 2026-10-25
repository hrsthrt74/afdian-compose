package com.hrsthrt74.afdian

import android.app.Application
import com.hrsthrt74.afdian.data.log.LogStore
import com.hrsthrt74.afdian.data.network.AfdianRouteState
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 自定义 Application：全 app 最早能拿到 Context 的时机。
 *
 * 在这里完成日志管线的装配（顺序有意如此，均为关键路径）：
 * 1. `LogStore` 单例创建 + writer 线程启动——后续所有日志先入队，线程没启动也不会丢；
 * 2. `AppLog.sink` 接通——从此 AppLog 的每条日志都会尝试落盘；
 * 3. `CrashHandler.install`——兜住包括启动阶段在内的一切未捕获异常；
 * 4. 订阅「写入日志文件」开关——DataStore 读出后写入 LogStore，进程内即时生效；
 * 5. 订阅「优先线路」——DataStore 读出后写入网络层的进程级全局值，下一次请求即生效。
 *
 * 之所以新增 Application 类而不是把初始化挂在 MainActivity：
 * 崩溃可能发生在 MainActivity 之前的任何阶段（Application / 首个 Activity 创建），
 * 挂在 Activity 上会漏掉最早期的崩溃——而这恰恰是线上用户最需要被记录的。
 *
 * 注意：本类越轻越好，严禁在这里做耗时初始化（会影响冷启动）。
 */
class AfdianApplication : Application() {

    /**
     * Application 生命周期级的协程作用域：订阅 DataStore 设置流用。
     * 没有 viewModelScope 可用，自建 SupervisorJob——某个订阅出错不会拖垮其他订阅；
     * 不用 GlobalScope 是为了语义清晰、可控（虽然 Application 级作用域与它实际等价）。
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // 1) 日志存储：单例 + writer 线程启动（内部含启动时的过期日志清理）
        val logStore = LogStore.get(this)
        logStore.start()

        // 2) 接通日志落盘：AppLog 保持零依赖，落盘实现在 LogStore
        AppLog.sink = logStore::enqueue

        // 3) 崩溃捕获：必须早于一切业务初始化，兜住启动阶段崩溃
        CrashHandler.install(this, logStore)

        AppLog.d(TAG, "Application 初始化完成，日志管线已接通")

        // 4) 订阅「写入日志文件」开关（DataStore），读出后写入 LogStore 全局状态。
        //    collect 首帧就会发出当前值（默认关闭），冷启动无需等待。
        //    runCatching 兜住 DataStore 读取异常，绝不能让初始化协程炸掉进程。
        appScope.launch {
            runCatching {
                SettingsStore(this@AfdianApplication).logToFile.collect { enabled ->
                    logStore.fileLoggingEnabled = enabled
                }
            }.onFailure { error ->
                // 走 AppLog.w 而不是 e：落盘不一定已接通（collect 失败通常意味着 DataStore 损坏），
                // 至少保证 Logcat 有据可查
                AppLog.w(TAG, "订阅「写入日志文件」开关失败", error)
            }
        }

        // 5) 订阅「优先线路」（DataStore）→ 进程级全局值。
        //    AfdianHttpClient 每次请求现读它，所以切完对下一次请求立即生效，不需要重启或重建 Service。
        //    冷启动最初几毫秒（DataStore 还没读出）的请求仍走默认 ifdian.net —— 三条链都能用，
        //    这点时间差不影响功能，换来的是不在 onCreate 里做阻塞读盘。
        appScope.launch {
            runCatching {
                SettingsStore(this@AfdianApplication).networkRoute.collect { route ->
                    AfdianRouteState.update(route)
                }
            }.onFailure { error ->
                AppLog.w(TAG, "订阅「优先线路」设置失败", error)
            }
        }
    }

    private companion object {
        const val TAG = "AfdianApp"
    }
}
