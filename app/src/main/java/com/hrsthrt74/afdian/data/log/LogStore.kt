package com.hrsthrt74.afdian.data.log

import android.content.Context
import com.hrsthrt74.afdian.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 写入线程待处理的命令。
 *
 * 把「写日志 / flush / 清空 / 淘汰」统一建模成队列元素，全部在唯一的 writer 线程上
 * 串行执行：文件句柄、当天日期等可变状态只被这个线程触碰，天然免锁；
 * flush / clear 借助 CountDownLatch 变成同步等待，供崩溃处理器与导出流程使用。
 */
private sealed interface LogCommand {

    /** 追加一条日志（[tr] 携带原始异常引用，由 writer 线程序列化，避免入队线程白算） */
    class Write(
        val millis: Long,
        val threadName: String,
        val level: Char,
        val tag: String,
        val message: String,
        val tr: Throwable?,
    ) : LogCommand

    /** 把当前句柄 flush 到磁盘；[latch] 由 writer 线程 countDown，调用方限时等待 */
    class Flush(val latch: CountDownLatch) : LogCommand

    /** 清空日志目录；先关句柄再删文件，[latch] 同上 */
    class Clear(val latch: CountDownLatch) : LogCommand

    /** 执行「保留 3 天 + 崩溃报告数量上限」清理 */
    class Prune(val latch: CountDownLatch) : LogCommand
}

/**
 * 本地日志数据层：`files/logs/` 目录下的按天滚动写入、3 天淘汰、占用统计、清空与导出。
 *
 * 设计要点：
 * - **单线程写入**：所有文件 I/O 都在唯一的 writer 线程上串行执行（见 [LogCommand]），
 *   调用方（任意线程的 AppLog 调用点）只做一次入队，绝不阻塞；
 * - **有界队列**：日志风暴（如网络重试循环）也不会撑爆内存，满时丢最旧并记账，
 *   稍后补一条「日志丢弃 N 条」标记，让导出的日志自己交代丢失；
 * - **目录选 `filesDir` 而不是 `cacheDir`**：cacheDir 在存储紧张时会被系统无提示清空，
 *   崩溃证据跟着一起没；日志是「要交给开发者的证据」，不是临时缓存；
 * - **崩溃报告永远记录**：`crash-*.log` 不受「写入日志文件」开关与 3 天淘汰控制
 *   （只做数量上限），这是本功能存在的意义——闪退后用户手里得有东西可交。
 *
 * 安全红线：写入前统一过 [sanitizeSensitive] 脱敏，作为「调用方不得拼敏感字段」之外的
 * 第二道防线。
 */
class LogStore private constructor(context: Context) {

    companion object {
        /** 队列容量：日志条目很小（几十字节级），4096 条远超正常峰值 */
        private const val QUEUE_CAPACITY = 4096

        /** flush 等待上限：崩溃处理流程绝不能被日志落盘卡死 */
        private const val FLUSH_TIMEOUT_SECONDS = 1L

        /** clear 等待上限：删文件可能略慢，给宽一点 */
        private const val CLEAR_TIMEOUT_SECONDS = 3L

        /** 运行日志保留的自然日数（今天 + 前 2 天） */
        const val RETAIN_DAYS = 3

        /** 崩溃报告数量上限：不参与天数淘汰，但也不能无限堆积 */
        private const val MAX_CRASH_FILES = 10

        /** 导出合并文件数量上限：合并产物随时可重新导出，只留最近几个即可 */
        private const val MAX_EXPORT_FILES = 3

        @Volatile
        private var instance: LogStore? = null

        /** 进程级单例。应用只有一个日志目录，双检锁保证并发首调也只建一份。 */
        fun get(context: Context): LogStore = instance ?: synchronized(this) {
            instance ?: LogStore(context.applicationContext).also { instance = it }
        }
    }

    /** 日志目录：`filesDir/logs/`。与 `files/datastore/` 同级，存储页按前缀 `files/logs/` 归类 */
    private val logDir: File = File(context.filesDir, "logs")

    /** 写入线程待处理命令队列（有界） */
    private val queue = LinkedBlockingQueue<LogCommand>(QUEUE_CAPACITY)

    /** 队列满时被丢弃的日志条数，drain 后补一条标记写进文件 */
    private val droppedCount = AtomicLong(0)

    /**
     * 是否把运行日志追加写入文件（「写入日志文件」开关）。
     * 由 AfdianApplication 订阅 DataStore 后写入，进程内即时生效。
     * 初值按构建类型：internal 内测版默认开启——DataStore 读出用户设置前的启动最初几条
     * 日志（恰恰是崩溃多发段）也能落盘；用户手动关过后会被订阅校正回 false。
     * 注意：只控制 `app-*.log`；崩溃报告始终记录。
     */
    @Volatile
    var fileLoggingEnabled: Boolean = BuildConfig.INTERNAL_BUILD

    /** writer 线程：守护线程，进程退出不阻塞；所有文件状态只被它触碰 */
    private val writerThread = Thread({
        while (true) {
            // take() 阻塞等待下一条命令；线程被中断（理论上进程死亡才会）则直接退出
            val command = try {
                queue.take()
            } catch (_: InterruptedException) {
                return@Thread
            }
            // 单条命令的处理必须与线程存活隔离：任何异常都不能杀死循环，
            // 否则后续日志全部滞留队列、flush/clear 全部超时
            runCatching { handle(command) }
        }
    }, "afdian-log-writer").apply { isDaemon = true }

    // —— 以下状态只在 writer 线程访问 ——
    /** 当前打开的「当天」文件句柄；跨天或被清空后置 null，下次写入重建 */
    private var currentWriter: BufferedWriter? = null

    /** 当前句柄对应的日期 key（yyyy-MM-dd），跨天比较用 */
    private var currentDayKey: String? = null

    /** 启动 writer 线程并做一次初始清理。在 Application.onCreate 调用一次。 */
    fun start() {
        writerThread.start()
        // 启动即清一次：把上次运行期间到期的旧日志删掉（跨天滚动时也会再清）。
        // 走队列而不是直接调 runPrune：文件操作必须全部落在 writer 线程
        val latch = CountDownLatch(1)
        queue.offer(LogCommand.Prune(latch))
    }

    /**
     * AppLog 的落盘入口：只入队，绝不阻塞、绝不抛异常。
     * 开关关闭时静默丢弃运行日志；崩溃报告走 [writeCrashReportSync]，不经过这里。
     */
    fun enqueue(level: Char, tag: String, message: String, tr: Throwable?) {
        if (!fileLoggingEnabled) return
        val command = LogCommand.Write(
            millis = System.currentTimeMillis(),
            threadName = Thread.currentThread().name,
            level = level,
            tag = tag,
            message = message,
            tr = tr,
        )
        if (!queue.offer(command)) {
            // 队列满：丢最旧一条腾位置，保证「最新的日志」优先留存，并记账
            queue.poll()
            queue.offer(command)
            droppedCount.incrementAndGet()
        }
    }

    /**
     * 同步等待队列中已有日志全部落盘。
     * 供崩溃处理器（崩溃前冲刷）与导出流程（保证读到完整内容）使用；
     * 超时即放弃——崩溃流程宁可少几条日志也不能被 IO 卡死。
     */
    fun flush() {
        val latch = CountDownLatch(1)
        if (!queue.offer(LogCommand.Flush(latch))) latch.countDown() // 队列满时放弃等待
        latch.await(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * 清空日志目录（运行日志 + 崩溃报告 + 导出文件全删）。
     * 内部提交到 writer 线程执行：必须先关掉持有的句柄再删文件，
     * 否则文件被句柄占用时部分设备上删不掉。存储页「日志」分类与日志页「清空」共用。
     */
    fun clear() {
        val latch = CountDownLatch(1)
        if (!queue.offer(LogCommand.Clear(latch))) latch.countDown()
        latch.await(CLEAR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** 日志目录总占用字节数（直接读文件元数据，不需要进入 writer 线程）。 */
    fun totalBytes(): Long = logDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    /** 日志文件个数（运行日志 + 崩溃报告 + 导出文件）。 */
    fun fileCount(): Int = logDir.listFiles()?.count { it.isFile } ?: 0

    /**
     * 合并全部日志文本并写入一个导出文件（默认落在 `files/logs/export-*.log`），
     * 返回该文件供分享（FileProvider）使用。
     *
     * 文件名可用 [fileName] 覆盖（分享 / 保存共用同一个建议名，让接收方看到的
     * 文件名与用户保存到本地的一致）。默认名放在 logs/ 目录是为了复用
     * FileProvider 的 `<files-path name="logs">` 白名单；下次清空日志时一并删除。
     */
    suspend fun writeExportFile(
        fileName: String = exportFileName(System.currentTimeMillis()),
    ): File? = withContext(Dispatchers.IO) {
        // 先冲刷，保证队列里已入队的日志都进了文件再读
        flush()
        val files = logDir.listFiles()?.filter { it.isFile } ?: return@withContext null
        val merged = mergeAllTexts(files)
        runCatching {
            logDir.mkdirs()
            val exportFile = File(logDir, fileName)
            exportFile.writeText(sanitizeSensitive(merged), StandardCharsets.UTF_8)
            exportFile
        }.getOrNull()
    }

    /**
     * 合并全部日志为一段文本（崩溃报告置顶 + 运行日志按日期升序），**不落盘**。
     * 供「保存到本地」直接写入用户通过 SAF 选定的 uri。
     * 目录读不到时返回占位文本而不是失败——写出一个「（暂无日志）」也比静默失败好懂。
     */
    suspend fun exportMergedText(): String = withContext(Dispatchers.IO) {
        flush()
        val files = logDir.listFiles()?.filter { it.isFile } ?: emptyList()
        mergeAllTexts(files)
    }

    /** 读取 + 按类别合并：崩溃报告置顶，运行日志按日期升序。 */
    private fun mergeAllTexts(files: List<File>): String {
        val crashTexts = files
            .filter { it.name.startsWith(CRASH_LOG_PREFIX) }
            .sortedBy { it.name } // 文件名含时间戳，字典序即时间序
            .map { readTextSafely(it) }
        val appTexts = files
            .filter { parseAppLogDay(it.name) != null }
            .sortedBy { parseAppLogDay(it.name) } // 按日期升序拼接，读起来时间线连续
            .map { readTextSafely(it) }
        return mergeLogsForExport(crashTexts, appTexts)
    }

    /**
     * 同步写崩溃报告（崩溃线程调用，**不可异步**——进程随时被杀）。
     * 与 writer 线程写的 `app-*.log` 是不同文件，无共享状态，并发安全。
     */
    fun writeCrashReportSync(report: String) {
        runCatching {
            logDir.mkdirs()
            val file = File(logDir, crashLogFileName(System.currentTimeMillis()))
            file.appendText(sanitizeSensitive(report), StandardCharsets.UTF_8)
        }
        // 崩溃后没有下次启动前的 flush 兜底了，这里直接补一次
        flush()
    }

    // ---------- 以下均为 writer 线程内执行 ----------

    private fun handle(command: LogCommand) {
        when (command) {
            is LogCommand.Write -> writeLine(command)
            is LogCommand.Flush -> flushWriter()
            is LogCommand.Clear -> handleClear(command.latch)
            is LogCommand.Prune -> runPrune(command.latch)
        }
    }

    /** 写一条日志：跨天滚动 + 脱敏 + 追加；任何失败都静默吞掉（绝不反噬调用方）。 */
    private fun writeLine(command: LogCommand.Write) {
        // 队列里滞留的旧命令在开关关闭后才被处理：此时也不该再写
        if (!fileLoggingEnabled) return
        val writer = writerFor(command.millis) ?: return
        runCatching {
            writer.write(
                sanitizeSensitive(
                    formatLogLine(command.millis, command.threadName, command.level, command.tag, command.message),
                ),
            )
            val tr = command.tr
            if (tr != null) {
                // printStackTrace 而不是 stackTrace.joinToString：后者会丢「Caused by」嵌套链
                val sw = StringWriter()
                tr.printStackTrace(PrintWriter(sw))
                writer.write("\n")
                writer.write(sanitizeSensitive(sw.toString().trimEnd('\n')))
            }
            writer.write("\n")
        }.onFailure {
            // 写失败（磁盘满 / 目录被第三方清掉等）：弃掉句柄，下次写入自动重建
            currentWriter = null
            currentDayKey = null
        }
    }

    /**
     * 取当前应写入的 writer；跨天时关闭旧句柄、flush、清理过期文件并打开新文件。
     * 返回 null 表示打开失败（极端磁盘问题），调用方静默放弃本条。
     */
    private fun writerFor(millis: Long): BufferedWriter? {
        // 按日志时间戳滚动（而不是处理时刻）：延迟写入的日志仍归入它发生的那一天。
        // 显式指定系统时区，避免「默认时区 / UTC 天数取整」在凌晨时段错切一天。
        val day = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val dayKey = day.toString() // LocalDate.toString() 即 ISO yyyy-MM-dd
        if (dayKey == currentDayKey && currentWriter != null) return currentWriter

        runCatching {
            currentWriter?.flush()
            currentWriter?.close()
        }
        currentWriter = null
        // 跨天（或首写）：顺手做一次淘汰，保证目录里最多只剩 3 天的运行日志
        runPrune()
        currentWriter = runCatching {
            logDir.mkdirs()
            val file = File(logDir, appLogFileName(day))
            BufferedWriter(OutputStreamWriter(java.io.FileOutputStream(file, true), StandardCharsets.UTF_8))
        }.getOrNull() ?: return null
        currentDayKey = dayKey
        return currentWriter
    }

    /** flush 当前句柄（不关闭），并顺带把「日志丢弃计数」补写成文件标记。 */
    private fun flushWriter() {
        runCatching { currentWriter?.flush() }
        writeDroppedMarkerIfNeeded()
    }

    /**
     * 队列满时丢过日志的话，补一条 W 级标记进文件。
     * 选 flush 时机补写（而不是丢弃瞬间入队）：避免递归入队；崩溃与导出前都会 flush，
     * 标记不会漏。导出的日志自己交代「中间丢了多少条」，比沉默丢失可查。
     */
    private fun writeDroppedMarkerIfNeeded() {
        val dropped = droppedCount.getAndSet(0)
        if (dropped <= 0L) return
        val writer = currentWriter ?: return
        runCatching {
            writer.write(
                sanitizeSensitive(
                    formatLogLine(System.currentTimeMillis(), "AppLog", 'W', "AppLog", "日志队列已满，丢弃 $dropped 条"),
                ),
            )
            writer.write("\n")
        }
    }

    /** 清空：先关句柄再删全部文件，最后复位状态。 */
    private fun handleClear(latch: CountDownLatch) {
        runCatching {
            currentWriter?.flush()
            currentWriter?.close()
        }
        currentWriter = null
        currentDayKey = null
        logDir.listFiles()?.forEach { file -> runCatching { file.delete() } }
        droppedCount.set(0)
        // 这里刻意不打日志：此刻打的任何日志都会立刻重建一个新文件，
        // 让「清空后占用归零」的直觉落空；清空动作的日志由 VM 层按需输出
        latch.countDown()
    }

    /**
     * 淘汰清理：运行日志按天（保留 [RETAIN_DAYS] 天），崩溃报告按数量（保留最近 [MAX_CRASH_FILES] 个）。
     * 启动时与每次跨天滚动时触发。
     */
    private fun runPrune(latch: CountDownLatch? = null) {
        val files = logDir.listFiles()?.filter { it.isFile }
        if (files == null) {
            latch?.countDown()
            return
        }
        val byDay = selectExpiredLogs(files.map { it.name }, LocalDate.now(), RETAIN_DAYS)
        byDay.forEach { name -> runCatching { File(logDir, name).delete() } }
        // 崩溃报告不按天删，但超数量上限时删最旧的（文件名含时间戳，字典序即时间序）
        val crashFiles = files
            .filter { it.name.startsWith(CRASH_LOG_PREFIX) }
            .sortedBy { it.name }
        if (crashFiles.size > MAX_CRASH_FILES) {
            crashFiles.take(crashFiles.size - MAX_CRASH_FILES)
                .forEach { file -> runCatching { file.delete() } }
        }
        // 导出合并文件同理：随时可重新导出，只留最近几个
        val exportFiles = files
            .filter { it.name.startsWith(EXPORT_PREFIX) }
            .sortedBy { it.name }
        if (exportFiles.size > MAX_EXPORT_FILES) {
            exportFiles.take(exportFiles.size - MAX_EXPORT_FILES)
                .forEach { file -> runCatching { file.delete() } }
        }
        latch?.countDown()
    }

    /** 读文本文件，失败（并发删除、编码异常等）返回空串而不是中断导出。 */
    private fun readTextSafely(file: File): String =
        runCatching { file.readText(StandardCharsets.UTF_8) }.getOrDefault("")
}
