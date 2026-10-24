package com.hrsthrt74.afdian

import android.content.Context
import android.os.Build
import com.hrsthrt74.afdian.data.log.LogStore
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常处理器：把崩溃现场连同完整环境信息写进日志目录，
 * 然后把异常交还系统默认处理器，保证「应用已停止运行」的对话框照常弹出。
 *
 * 工作方式：包装而不是替换——安装时保存原本的默认 handler，自己处理完
 * （冲刷日志 + 同步写崩溃报告）后必须调用它。直接吞掉异常会让系统拿不到
 * 崩溃回调，进程死得不明不白，也没法弹对话框。
 *
 * 关键约束：`uncaughtException` 跑在**正在崩溃的线程**上，进程随时会被系统杀死，
 * 所以写崩溃报告必须是**同步**落盘（`LogStore.writeCrashReportSync`），
 * 不能提交任何异步任务；且全程 runCatching——崩溃处理自身再出错也绝不能
 * 拦住「交还系统 handler」这一步。
 */
object CrashHandler {

    /**
     * 安装崩溃捕获。在 `AfdianApplication.onCreate` 最先调用，
     * 早于任何可能崩溃的业务初始化，才能兜住启动阶段的崩溃。
     */
    fun install(context: Context, logStore: LogStore) {
        // 保存系统默认 handler（通常是 RuntimeInit 的 KillApplicationHandler，
        // 负责弹崩溃对话框、终止进程）。App 已被装过 handler 时也正确接管（链式）。
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                // 1) 把队列里已入队的日志冲进文件：闪退前最后的上下文都在里面
                logStore.flush()
                // 2) 同步写崩溃报告（含完整环境信息 + 堆栈），单独成文件便于导出时一眼定位。
                //    此处刻意不再走 AppLog 打日志：崩溃线程上入队的任何日志都等不到下次 flush
                logStore.writeCrashReportSync(buildCrashReport(context, thread, throwable))
            }
            // 3) 无论如何交还系统：弹崩溃对话框、走系统收尾流程
            original?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 拼崩溃报告文本：环境信息 + 完整堆栈。
     *
     * 元信息标签一律用英文（Time / Thread / Variant ...）：日志文件可能被贴进
     * issue 工具、脚本 grep 或英文交流场景，中文标签对机器处理和检索都不友好；
     * 同时这也是崩溃报告的通用惯例。
     *
     * 环境信息是定位「哪个版本、哪个包、哪台设备」的关键：
     * 变体名与构建时间来自 BuildConfig（不同 flavor / 构建批次一眼可辨）；
     * 设备与系统版本用于判断机型相关崩溃。
     * 注意 release 包开了混淆，堆栈里的类名是重命名后的，
     * 需要对应变体的 mapping.txt 才能还原——所以变体名必须写进报告里。
     */
    private fun buildCrashReport(context: Context, thread: Thread, throwable: Throwable): String = buildString {
        appendLine("===== Crash Report =====")
        appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
        // 只写线程名不写线程 id：Thread.id 在新 SDK 已弃用，替代 API threadId() 要 API 35；
        // 线程名足够定位（main / OkHttp Dispatcher 等），id 的增量价值不大
        appendLine("Thread: ${thread.name}")
        appendLine("Variant: ${BuildConfig.APP_VARIANT_NAME}")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Build Time: ${Date(BuildConfig.BUILD_TIMESTAMP_MS)}")
        appendLine("Package: ${context.packageName}")
        appendLine("OS: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Logging: debug=${AppLog.debugEnabled} file=${LogStore.get(context).fileLoggingEnabled}")
        appendLine("----- Stack Trace -----")
        // stackTraceToString 自带「Caused by」完整因果链
        appendLine(throwable.stackTraceToString())
    }
}
