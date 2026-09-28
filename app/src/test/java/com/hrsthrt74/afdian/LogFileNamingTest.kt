package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.log.APP_LOG_PREFIX
import com.hrsthrt74.afdian.data.log.appLogFileName
import com.hrsthrt74.afdian.data.log.crashLogFileName
import com.hrsthrt74.afdian.data.log.exportFileName
import com.hrsthrt74.afdian.data.log.formatLogLine
import com.hrsthrt74.afdian.data.log.mergeLogsForExport
import com.hrsthrt74.afdian.data.log.parseAppLogDay
import com.hrsthrt74.afdian.data.log.sanitizeSensitive
import com.hrsthrt74.afdian.data.log.selectExpiredLogs
import com.hrsthrt74.afdian.data.log.truncateIncompleteTailLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日志文件命名 / 淘汰 / 行格式化 / 导出合并的纯函数回归测试。
 *
 * 这些函数是「按天滚动 + 保留 3 天」策略的基础，一旦漂移会出现
 * 「日志被误删」「崩溃报告丢失」「导出内容混乱」等问题，因此单独测。
 * 全部传固定时区（UTC），保证输出确定、不随测试机时区漂移。
 */
class LogFileNamingTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    // ---------- 文件名生成与解析 ----------

    @Test
    fun appLogFileNamePadsDateCorrectly() {
        // 补零：月 / 日都必须是两位，否则文件名字典序不再等价于时间序
        assertEquals("app-2026-01-05.log", appLogFileName(LocalDate.of(2026, 1, 5)))
        assertEquals("app-2026-12-31.log", appLogFileName(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun crashAndExportFileNamesContainTimestamp() {
        // 1970-01-01 00:00:00 UTC，精确到秒、不补毫秒
        assertEquals("crash-1970-01-01_000000.log", crashLogFileName(0L, utc))
        assertEquals("export-1970-01-01_000000.log", exportFileName(0L, utc))
    }

    @Test
    fun parseAppLogDayAcceptsOnlyOwnNaming() {
        assertEquals(LocalDate.of(2026, 9, 28), parseAppLogDay("app-2026-09-28.log"))
        // 崩溃报告 / 导出文件 / 其他文件名一律不解析，避免被天数淘汰误删
        assertNull(parseAppLogDay("crash-2026-09-28_140321.log"))
        assertNull(parseAppLogDay("export-2026-09-28_140321.log"))
        assertNull(parseAppLogDay("app-.log"))
        assertNull(parseAppLogDay("app-2026-13-99.log"))
        assertNull(parseAppLogDay("random.txt"))
        assertNull(parseAppLogDay("app-2026-09-28.log.bak"))
    }

    // ---------- 3 天淘汰算法 ----------

    @Test
    fun selectExpiredLogsKeepsLastThreeDays() {
        val today = LocalDate.of(2026, 9, 28)
        val names = listOf(
            "app-2026-09-28.log", // 今天：保留
            "app-2026-09-27.log", // 昨天：保留
            "app-2026-09-26.log", // 前天：保留（今天 + 前 2 天 = 3 个自然日）
            "app-2026-09-25.log", // 大前天：过期
            "app-2026-09-01.log", // 更早：过期
        )
        assertEquals(
            listOf("app-2026-09-25.log", "app-2026-09-01.log"),
            selectExpiredLogs(names, today, retainDays = 3),
        )
    }

    @Test
    fun selectExpiredLogsHandlesMonthAndYearBoundary() {
        // 跨月：10 月 1 日时保留 09-29/09-30/10-01 三天，09-29 恰好是「前 2 天」仍保留
        assertEquals(
            listOf("app-2026-09-01.log"),
            selectExpiredLogs(
                listOf("app-2026-09-29.log", "app-2026-09-30.log", "app-2026-09-01.log"),
                today = LocalDate.of(2026, 10, 1),
                retainDays = 3,
            ),
        )
        // 跨年：1 月 1 日时保留 12-30/12-31/1-1 三天，12-29 已是「前 2 天」之外
        assertEquals(
            listOf("app-2025-12-29.log"),
            selectExpiredLogs(
                listOf("app-2025-12-29.log", "app-2025-12-30.log", "app-2025-12-31.log"),
                today = LocalDate.of(2026, 1, 1),
                retainDays = 3,
            ),
        )
    }

    @Test
    fun selectExpiredLogsNeverTouchesCrashOrUnknownFiles() {
        // 崩溃报告与解析不出日期的文件不参与天数淘汰
        assertEquals(
            emptyList<String>(),
            selectExpiredLogs(
                listOf(
                    "crash-2025-01-01_000000.log",
                    "export-2025-01-01_000000.log",
                    "whatever.log",
                    "app-garbage.log",
                ),
                today = LocalDate.of(2026, 9, 28),
                retainDays = 3,
            ),
        )
    }

    @Test
    fun selectExpiredLogsEmptyInputAndDegenerateRetain() {
        assertTrue(selectExpiredLogs(emptyList(), LocalDate.of(2026, 9, 28)).isEmpty())
        // retainDays = 1 只保留今天，之前的全过期
        assertEquals(
            listOf("app-2026-09-27.log"),
            selectExpiredLogs(
                listOf("app-2026-09-27.log", "app-2026-09-28.log"),
                LocalDate.of(2026, 9, 28),
                retainDays = 1,
            ),
        )
    }

    // ---------- 日志行格式 ----------

    @Test
    fun formatLogLineMatchesExpectedLayout() {
        // 1970-01-01 00:00:00.000 UTC
        assertEquals(
            "1970-01-01 00:00:00.000 [main] D/FeedService: 拉取首页 feed",
            formatLogLine(0L, "main", 'D', "FeedService", "拉取首页 feed", utc),
        )
        assertEquals(
            "1970-01-01 00:00:00.000 [OkHttp Dispatcher] E/Api: failed",
            formatLogLine(0L, "OkHttp Dispatcher", 'E', "Api", "failed", utc),
        )
    }

    // ---------- 导出合并 ----------

    @Test
    fun mergeLogsPutsCrashFirstThenAppLogs() {
        val merged = mergeLogsForExport(
            crashTexts = listOf("crash line 1\n"),
            appTexts = listOf("day 1 log\n", "day 2 log\n"),
        )
        // 崩溃报告在最前，运行日志随后
        assertTrue(merged.startsWith("===== 崩溃报告 =====\n"))
        assertTrue(merged.contains("crash line 1"))
        assertTrue(merged.contains("===== 运行日志 ====="))
        assertTrue(merged.indexOf("day 1 log") < merged.indexOf("day 2 log"))
    }

    @Test
    fun mergeLogsTruncatesIncompleteTailLine() {
        // 最后一个文件末尾是半截行（写入方还在写），裁掉而不是带出去
        val merged = mergeLogsForExport(
            crashTexts = emptyList(),
            appTexts = listOf("line A\n", "line B\nline B-half"),
        )
        assertFalse(merged.contains("line B-half"))
        assertTrue(merged.contains("line B\n"))
    }

    @Test
    fun mergeLogsHandlesTruncationEdgeCases() {
        // 整个文本都是半截行 → 裁成空
        assertEquals("", truncateIncompleteTailLine("half line"))
        // 多行文本只裁最后一段
        assertEquals("a\n", truncateIncompleteTailLine("a\nb-half"))
        // 已完整（换行结尾）原样保留
        assertEquals("a\n", truncateIncompleteTailLine("a\n"))
    }

    @Test
    fun mergeLogsFallsBackToPlaceholderWhenEmpty() {
        // 完全没日志时导出内容给出占位说明，避免 0 字节文件让人困惑
        assertEquals("（暂无日志）\n", mergeLogsForExport(emptyList(), emptyList()))
    }

    // ---------- 敏感信息脱敏 ----------

    @Test
    fun sanitizeSensitiveMasksCommonCredentialStyles() {
        assertEquals("auth_token=***", sanitizeSensitive("auth_token=abc123"))
        assertEquals("参数 auth_token=*** 结束", sanitizeSensitive("参数 auth_token=abc123&x=1 结束"))
        assertTrue(sanitizeSensitive("Cookie: session=xyz; uid=1").endsWith("***"))
        assertEquals("\"token\":\"***\"", sanitizeSensitive("\"token\":\"secret\""))
        // 普通文本不受影响
        assertEquals("登录成功 userId=42", sanitizeSensitive("登录成功 userId=42"))
    }

    // ---------- 前缀常量一致性 ----------

    @Test
    fun prefixesStayConsistent() {
        // 生成器与解析器共享同一组前缀常量，防手滑改了一处漏另一处
        assertTrue(appLogFileName(LocalDate.of(2026, 1, 1)).startsWith(APP_LOG_PREFIX))
        assertTrue(parseAppLogDay(appLogFileName(LocalDate.of(2026, 1, 1))) != null)
    }
}
