package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.storage.HistoryPart
import com.hrsthrt74.afdian.data.storage.StorageCategory
import com.hrsthrt74.afdian.data.storage.categorizeStoragePath
import com.hrsthrt74.afdian.data.storage.deviceUsageFraction
import com.hrsthrt74.afdian.data.storage.formatBytes
import com.hrsthrt74.afdian.data.storage.formatBytesParts
import com.hrsthrt74.afdian.data.storage.formatPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 存储扫描器的纯逻辑回归测试：字节格式化与路径分类。
 *
 * 这两个函数是「总占用 == 各分类之和」口径的基础，一旦漂移会导致页面数字自相矛盾，
 * 因此单独立测。
 */
class StorageScannerTest {

    @Test
    fun formatBytesUsesBinaryUnits() {
        assertEquals("0 B", formatBytes(0))
        // 小于 1KB 时直接显示整数 B，避免出现「0.0 KB」这种无意义展示
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun formatBytesDropsDecimalsForLargeValues() {
        // 大于等于 100 时去掉小数位，减少数字宽度抖动
        assertEquals("100 KB", formatBytes(102_400))
        assertEquals("250 MB", formatBytes(250L * 1024 * 1024))
    }

    @Test
    fun formatBytesPartsSplitsNumberAndUnit() {
        // 拆成两段是给「大数值 + 小单位」排版用的（数值大字号、单位 footnote）
        assertEquals("0" to "B", formatBytesParts(0))
        assertEquals("1023" to "B", formatBytesParts(1023))
        assertEquals("1.0" to "KB", formatBytesParts(1024))
        assertEquals("1.5" to "KB", formatBytesParts(1536))
        assertEquals("1.0" to "MB", formatBytesParts(1024L * 1024))
        assertEquals("100" to "KB", formatBytesParts(102_400))
        assertEquals("1.0" to "GB", formatBytesParts(1024L * 1024 * 1024))
    }

    @Test
    fun formatBytesIsExactlyTheTwoPartsJoined() {
        // 两者共用同一套换算，不允许漂移
        listOf(
            0L, 1L, 1023L, 1024L, 1536L, 102_400L,
            1024L * 1024, 250L * 1024 * 1024, 1024L * 1024 * 1024,
        ).forEach { bytes ->
            val (number, unit) = formatBytesParts(bytes)
            assertEquals("$bytes 的拼接结果与 formatBytes 不一致", "$number $unit", formatBytes(bytes))
        }
    }

    @Test
    fun imageCacheIsClassifiedFromCoilDirectory() {
        // Coil 3 默认磁盘缓存目录
        assertEquals(StorageCategory.ImageCache, categorizeStoragePath("cache/coil3_disk_cache/x.0"))
        assertEquals(StorageCategory.ImageCache, categorizeStoragePath("cache/coil3_disk_cache/journal"))
    }

    @Test
    fun historyCoversBothDataStoreAndAvatarCache() {
        assertEquals(StorageCategory.History, categorizeStoragePath("files/datastore/history.preferences_pb"))
        // DataStore 先写 .tmp 再原子 rename，前缀匹配必须兼容残留的临时文件
        assertEquals(StorageCategory.History, categorizeStoragePath("files/datastore/history.preferences_pb.tmp"))
        assertEquals(
            StorageCategory.History,
            categorizeStoragePath("files/history_record_cache/avatars/abc.img"),
        )
    }

    @Test
    fun historyAndSearchHistoryShareOneCategoryWithSelectableParts() {
        // 浏览记录与搜索关键词合成一类展示，但保留「可分别勾选清理」的能力
        assertEquals(StorageCategory.History, categorizeStoragePath("files/datastore/history.preferences_pb"))
        assertEquals(StorageCategory.History, categorizeStoragePath("files/datastore/search_history.preferences_pb"))
        assertEquals(
            listOf(HistoryPart.Browsing, HistoryPart.Search),
            StorageCategory.History.selectableParts,
        )
        // 其余分类不应带子项，否则点击时会错误地弹勾选窗
        StorageCategory.entries
            .filter { it != StorageCategory.History }
            .forEach { assertEquals("${it.id} 不该有可勾选子项", emptyList<HistoryPart>(), it.selectableParts) }
    }

    @Test
    fun cacheStoresMapToExpectedCategories() {
        // 首页与「我的」资料快照合成一类：两者都是能自动重建的冷启动快照，分开清没有意义
        assertEquals(StorageCategory.Snapshot, categorizeStoragePath("files/datastore/home.preferences_pb"))
        assertEquals(StorageCategory.Snapshot, categorizeStoragePath("files/datastore/profile.preferences_pb"))
    }

    @Test
    fun logsDirectoryIsClassifiedAsCleanableLogs() {
        // 日志目录若不显式归类会掉进 Other 兜底，导致存储页「总占用 == 各分类之和」口径外泄
        assertEquals(StorageCategory.Logs, categorizeStoragePath("files/logs/app-2026-09-28.log"))
        assertEquals(StorageCategory.Logs, categorizeStoragePath("files/logs/crash-2026-09-28_140321.log"))
        assertEquals(StorageCategory.Logs, categorizeStoragePath("files/logs/export-2026-09-28_140321.log"))
        // 可清理：一键清理与单类清理都能回收日志空间
        assertEquals(true, StorageCategory.Logs.cleanable)
        // 不该有可勾选子项（上方测试断言：除 History 外其余分类一律为空）
        assertEquals(emptyList<HistoryPart>(), StorageCategory.Logs.selectableParts)
    }

    @Test
    fun authAndSettingsAreUncleanableAppData() {
        // 登录凭证是红线：必须落在不可清理的分类里
        assertEquals(StorageCategory.AppData, categorizeStoragePath("files/datastore/auth.preferences_pb"))
        assertEquals(StorageCategory.AppData, categorizeStoragePath("files/datastore/settings.preferences_pb"))
        assertEquals(StorageCategory.AppData, categorizeStoragePath("files/datastore/theme_settings.preferences_pb"))
        assertEquals(false, StorageCategory.AppData.cleanable)
    }

    @Test
    fun unknownPathsFallIntoSingleUncleanableBucket() {
        // 旧版 SharedPreferences 残留、其他 cache 文件等一律兜底
        assertEquals(StorageCategory.Other, categorizeStoragePath("shared_prefs/legacy.xml"))
        assertEquals(StorageCategory.Other, categorizeStoragePath("cache/http/abc.tmp"))
        assertEquals(StorageCategory.Other, categorizeStoragePath("databases/afdian.db"))
        assertEquals(false, StorageCategory.Other.cleanable)
    }

    @Test
    fun deviceUsageFractionClampsAndGuardsAgainstZero() {
        assertEquals(0.5f, deviceUsageFraction(appBytes = 50L, deviceTotalBytes = 100L), 1e-6f)

        // 设备容量读到 0 时必须回落到 0 而不是 NaN：Miuix 进度条内部是 coerceIn，
        // 对 NaN 两个比较都为假、会原样透传，最终算出 NaN 的扫过角度
        assertEquals(0f, deviceUsageFraction(appBytes = 10L, deviceTotalBytes = 0L), 1e-6f)
        assertEquals(0f, deviceUsageFraction(appBytes = 0L, deviceTotalBytes = 0L), 1e-6f)

        // 防御式钳制在 1 以内
        assertEquals(1f, deviceUsageFraction(appBytes = 200L, deviceTotalBytes = 100L), 1e-6f)

        // 真实量级：2GB / 256GB，不应因浮点精度出问题
        val realistic = deviceUsageFraction(
            appBytes = 2L * 1024 * 1024 * 1024,
            deviceTotalBytes = 256L * 1024 * 1024 * 1024,
        )
        assertTrue("实际 $realistic", realistic > 0f && realistic < 0.01f)
    }

    @Test
    fun formatPercentKeepsTinyRatiosMeaningful() {
        // 极小占比不能显示成「0.0%」，否则看起来像完全没占空间
        assertEquals("<0.1%", formatPercent(0f))
        assertEquals("<0.1%", formatPercent(0.0004f))
        assertEquals("0.1%", formatPercent(0.001f))
        assertEquals("5.2%", formatPercent(0.052f))
        // 10% 以上取整，减少数字宽度抖动
        assertEquals("12%", formatPercent(0.123f))
        assertEquals("50%", formatPercent(0.5f))
        assertEquals("100%", formatPercent(1f))

        // 兜底：非有限值不应渲染成「NaN%」
        assertEquals("0%", formatPercent(Float.NaN))
        assertEquals("0%", formatPercent(Float.POSITIVE_INFINITY))
    }
}
