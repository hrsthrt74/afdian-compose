package com.hrsthrt74.afdian.data.log

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日志文件的命名、按天淘汰、行格式化、导出合并、敏感信息脱敏等**纯函数**集合。
 *
 * 全部不依赖 Context / Android API，可以在纯 JVM 单元测试里直接覆盖
 * （见 `LogFileNamingTest`）。带 `zone` 默认参数的函数在单测里传固定时区保证输出确定。
 */
internal const val APP_LOG_PREFIX = "app-"

/** 崩溃报告文件名前缀：crash-yyyy-MM-dd_HHmmss.log */
internal const val CRASH_LOG_PREFIX = "crash-"

/** 导出合并文件名前缀：export-yyyy-MM-dd_HHmmss.log */
internal const val EXPORT_PREFIX = "export-"

/** 运行日志、崩溃报告与导出合并文件共用的扩展名 */
internal const val LOG_EXTENSION = ".log"

/** 运行日志文件名里的日期格式（ISO，字符串比较等价于时间比较） */
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

/** 日志行的时间戳格式：2026-09-28 14:03:21.482 */
private val LINE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

/** 崩溃报告 / 导出文件名里的时间格式：2026-09-28_140321（精确到秒） */
private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.US)

/** 生成某一天的运行日志文件名，如 `app-2026-09-28.log`。 */
internal fun appLogFileName(date: LocalDate): String =
    "$APP_LOG_PREFIX${DAY_FORMAT.format(date)}$LOG_EXTENSION"

/** 生成崩溃报告文件名，如 `crash-2026-09-28_140321.log`。每次崩溃一个新文件。 */
internal fun crashLogFileName(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "$CRASH_LOG_PREFIX${STAMP_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone))}$LOG_EXTENSION"

/** 生成导出合并文件名，如 `export-2026-09-28_140321.log`。 */
internal fun exportFileName(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "$EXPORT_PREFIX${STAMP_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone))}$LOG_EXTENSION"

/**
 * 从运行日志文件名解析出日期；不是 `app-yyyy-MM-dd.log` 形态的（崩溃报告、导出文件、
 * 非法名、日期越界）一律返回 null。
 *
 * 解析失败返回 null 而不是抛异常：淘汰算法把它当「不动」处理，绝不能因为一个
 * 奇怪文件名让清理流程中断。
 */
internal fun parseAppLogDay(fileName: String): LocalDate? {
    if (!fileName.startsWith(APP_LOG_PREFIX) || !fileName.endsWith(LOG_EXTENSION)) return null
    return runCatching {
        LocalDate.parse(
            fileName.removePrefix(APP_LOG_PREFIX).removeSuffix(LOG_EXTENSION),
            DAY_FORMAT,
        )
    }.getOrNull()
}

/**
 * 计算应删除的运行日志文件名。
 *
 * 规则：只保留「今天 + 前 `retainDays - 1` 天」共 `retainDays` 个自然日的 `app-*.log`；
 * `crash-*.log` / `export-*.log` **不参与天数淘汰**——崩溃报告是稀有证据，天数淘汰
 * 可能刚好删掉用户还没导出的那次闪退，数量保护（保留最近 N 个）交给调用方另做。
 * 解析不出日期的文件一律不动（不是本模块生成的，宁可多留不可误删）。
 *
 * @param fileNames 现有日志目录下的全部文件名
 * @param today 今天，注入以便单测覆盖跨月 / 跨年边界
 */
internal fun selectExpiredLogs(
    fileNames: List<String>,
    today: LocalDate,
    retainDays: Int = 3,
): List<String> {
    if (retainDays <= 0) return emptyList()
    val earliestKept = today.minusDays((retainDays - 1).toLong())
    return fileNames.filter { name ->
        val day = parseAppLogDay(name)
        day != null && day.isBefore(earliestKept)
    }
}

/**
 * 格式化一条日志行（不含异常堆栈；堆栈由调用方另起行追加）。
 *
 * 格式：`2026-09-28 14:03:21.482 [main] D/FeedService: 拉取首页 feed`
 * 带线程名是为了对时序——网络回调、IO 线程、主线程的日志交织时，
 * 只看内容很难判断先后与归属。
 */
internal fun formatLogLine(
    millis: Long,
    threadName: String,
    level: Char,
    tag: String,
    message: String,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val time = LINE_TIME_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone))
    return "$time [$threadName] $level/$tag: $message"
}

/**
 * 合并全部日志文本用于导出：崩溃报告置顶（最关键的信息最先被看到），
 * 运行日志随后按传入顺序（调用方保证按日期升序）拼接。
 * 完全没有内容时给一句占位提示，避免导出一个 0 字节的空文件让人困惑。
 *
 * @param crashTexts 各崩溃报告文件的文本
 * @param appTexts 各运行日志文件的文本（已按日期升序）
 */
internal fun mergeLogsForExport(crashTexts: List<String>, appTexts: List<String>): String = buildString {
    if (crashTexts.isNotEmpty()) {
        appendLine("===== 崩溃报告 =====")
        crashTexts.forEach { text ->
            append(truncateIncompleteTailLine(text))
            if (!endsWith("\n")) appendLine()
        }
    }
    if (appTexts.isNotEmpty()) {
        if (isNotEmpty()) appendLine()
        appendLine("===== 运行日志 =====")
        appTexts.forEach { text ->
            append(truncateIncompleteTailLine(text))
            if (!endsWith("\n")) appendLine()
        }
    }
    if (isEmpty()) appendLine("（暂无日志）")
}

/**
 * 裁掉文本末尾的「半截行」。
 *
 * 导出瞬间写入方可能正在追加最后一行，读到的文本末尾是一行没写完的内容；
 * 没有换行符结尾就意味着这行不完整，裁掉它比保留半截更利于阅读。
 */
internal fun truncateIncompleteTailLine(text: String): String {
    if (text.isEmpty() || text.endsWith("\n")) return text
    val lastNewline = text.lastIndexOf('\n')
    // 整个文本都找不到换行 = 全文都是一行没写完的半截，直接置空
    return if (lastNewline < 0) "" else text.take(lastNewline + 1)
}

/** 凭证样式脱敏：`auth_token=xxx`（含 Cookie 场景的 URL 参数形态） */
private val AUTH_TOKEN_PATTERN = Regex("(?i)auth_token=\\S+")

/** 凭证样式脱敏：`Cookie: xxx` 整段值（值内可能含多个字段，整段掩掉最稳） */
private val COOKIE_PATTERN = Regex("(?i)cookie:\\s*\\S.*")

/** 凭证样式脱敏：JSON 形态的 `"token":"xxx"` */
private val TOKEN_JSON_PATTERN = Regex("(?i)\"token\"\\s*:\\s*\"[^\"]+\"")

/**
 * 敏感信息脱敏（第二道防线）。
 *
 * AppLog 的红线本来就是「调用方不得把 auth_token / Cookie / 密码拼进日志」，
 * 但人是会犯错的——落盘前统一再扫一遍最常见的凭证样式，把值替换成 `***`。
 * 覆盖不了所有形态，但能把最常见的意外兜住。
 */
internal fun sanitizeSensitive(text: String): String = text
    .replace(AUTH_TOKEN_PATTERN, "auth_token=***")
    .replace(COOKIE_PATTERN, "Cookie: ***")
    .replace(TOKEN_JSON_PATTERN, "\"token\":\"***\"")
