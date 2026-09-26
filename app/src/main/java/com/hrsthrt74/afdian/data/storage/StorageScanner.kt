package com.hrsthrt74.afdian.data.storage

import android.content.Context
import android.os.StatFs
import com.hrsthrt74.afdian.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 「历史记录」分类下可分别勾选的子项。
 *
 * 只有这一类需要逐项勾选：浏览记录与搜索关键词都能单独清、用户也常只想清其中一个；
 * 但两者都只有几百字节到几 KB，拆成两行展示又太碎，所以合成一行 + 勾选弹窗。
 */
enum class HistoryPart(val label: String) {
    /** 浏览过的创作者 / 动态 / 会话，以及旧版遗留的头像缓存目录。 */
    Browsing("浏览历史"),

    /** 本地保存的搜索关键词。 */
    Search("搜索历史"),
}

/**
 * 存储占用分类。
 *
 * @property id 稳定标识：既作 `LazyColumn` 的 key，也用于「正在清理」集合的比对。
 * @property title 列表展示名。
 * @property summary 副标题，说明这一类里到底装了什么。
 * @property cleanable 是否可清理。为 false 的分类不显示箭头、不可点击，也不参与「清理全部缓存」。
 * @property selectableParts 可逐项勾选清理的子项。为空表示整类一起清；
 *   非空时点击该分类弹的是勾选弹窗（默认全选），而不是直接问「确认清理整类」。
 */
enum class StorageCategory(
    val id: String,
    val title: String,
    val summary: String,
    val cleanable: Boolean,
    val selectableParts: List<HistoryPart> = emptyList(),
) {
    /** Coil 全局图片磁盘缓存：封面、头像、正文插图等。通常是最大的一项。 */
    ImageCache("image_cache", "图片", "封面、头像、正文插图等缓存", true),

    /**
     * 浏览记录与搜索关键词。
     *
     * 合成一类展示：两者都属于「本地历史」，都是能自动重建的小体积数据，分开展示只会让明细列表变长。
     * 但保留分别清理的能力，所以带上 [selectableParts]，点击时让用户勾选要清哪几项。
     */
    History(
        id = "history",
        title = "历史记录",
        summary = "浏览记录、搜索记录",
        cleanable = true,
        selectableParts = listOf(HistoryPart.Browsing, HistoryPart.Search),
    ),

    /**
     * 首页首屏与「我的」资料快照。
     *
     * 合成一类而不是分开展示：两者都是「冷启动先回填、网络回来再覆盖」的临时快照，
     * 体积都很小、都能自动重建，分开列出只会让明细列表变长、清理时还得选两次。
     * 清理时两个 DataStore 一起清，避免只清一半留下不一致的观感。
     */
    Snapshot("snapshot", "快照", "首页与我的页的快照，用于冷启动秒显", true),

    /**
     * 登录凭证与全部设置项。
     * 这是红线分类：清了 auth 等于静默登出，因此恒为不可清理，一键清理也绝不包含。
     */
    AppData("app_data", "设置与登录数据", "登录凭证与设置项，不可清理", false),

    /** 兜底分类：旧版本残留、无数据库/无 WebView 场景下的系统文件等。 */
    Other("other", "其他", "系统文件等，不可清理", false),
}

/** 单个分类的占用结果。 */
data class StorageCategoryUsage(val category: StorageCategory, val bytes: Long)

/**
 * 一次完整扫描的结果。
 *
 * [totalBytes] 恒等于 [categories] 各项之和（扫描器做了兜底归并），
 * 因此 UI 上「总占用」与明细列表不会自相矛盾。
 */
data class StorageSnapshot(
    val totalBytes: Long,
    /** 设备可用空间（/data 分区，普通用户可用字节）。 */
    val usableBytes: Long,
    /** 设备总空间（/data 分区）。 */
    val deviceTotalBytes: Long,
    /** 按 [StorageCategory] 声明顺序排列的明细，必定包含全部枚举项。 */
    val categories: List<StorageCategoryUsage>,
)

/**
 * 分类归属的纯函数：输入相对 `dataDir` 的路径，输出唯一分类。
 *
 * 之所以按「前缀」而不是「文件名全等」匹配：DataStore 写盘是
 * 先写 `<name>.preferences_pb.tmp` 再原子 rename，崩溃后可能残留 `.tmp`；
 * 前缀匹配能让这类临时文件自动归到正确分类，也兼容将来可能新增的后缀。
 *
 * 抽成顶层 `internal` 函数是为了能在单元测试里直接覆盖，不必构造 Context。
 */
internal fun categorizeStoragePath(relativePath: String): StorageCategory = when {
    // 图片缓存：Coil 全局磁盘缓存目录（目录名与 ImageLoader 构建共用同一个常量）
    relativePath.startsWith("cache/$IMAGE_CACHE_DIRECTORY_NAME/") -> StorageCategory.ImageCache
    // 历史记录：浏览条目 DataStore + 旧版遗留的头像缓存目录 + 搜索关键词
    // （头像已改为只存链接，这个目录只剩升级残留，仍归到这里以便一并回收）
    relativePath.startsWith("files/history_record_cache/") -> StorageCategory.History
    relativePath.startsWith("files/datastore/history.") -> StorageCategory.History
    relativePath.startsWith("files/datastore/search_history.") -> StorageCategory.History
    // 首页与「我的」资料快照：两个 DataStore 合成一类展示与清理
    relativePath.startsWith("files/datastore/home.") -> StorageCategory.Snapshot
    relativePath.startsWith("files/datastore/profile.") -> StorageCategory.Snapshot
    // 红线：登录凭证与全部设置项，统一归入不可清理的 AppData
    relativePath.startsWith("files/datastore/auth.") -> StorageCategory.AppData
    relativePath.startsWith("files/datastore/settings.") -> StorageCategory.AppData
    relativePath.startsWith("files/datastore/theme_settings.") -> StorageCategory.AppData
    // 未识别的路径（databases/、shared_prefs/ 旧版残留、no_backup/、其他 cache 等）一律兜底
    else -> StorageCategory.Other
}

/**
 * 字节数的可读表示，拆成「数值」与「单位」两段返回。
 *
 * 拆开是为了让调用方能做「大数值 + 小单位」的排版（数值用大字号、单位用 footnote 弱化），
 * 而不是把整串按同一字号渲染。[formatBytes] 就是它的拼接结果。
 *
 * 纯函数、不依赖 Context，便于单元测试。
 */
internal fun formatBytesParts(bytes: Long): Pair<String, String> {
    // 小于 1KB 时直接显示整数 B，避免出现「0.0 KB」这种对用户无意义的展示
    if (bytes < 1024L) return bytes.toString() to "B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    // 大于等于 100 时去掉小数位，减少数字宽度抖动
    val number = if (value >= 100.0) {
        value.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    return number to units[index]
}

/**
 * 字节数的可读文本，如 `1.5 MB`。由 [formatBytesParts] 拼接而成，两者不会漂移。
 * 纯函数、不依赖 Context，便于单元测试。
 */
internal fun formatBytes(bytes: Long): String {
    val (number, unit) = formatBytesParts(bytes)
    return "$number $unit"
}

/**
 * 「应用占用 / 设备总容量」的占比，钳制在 0..1。
 *
 * 设备容量读到 0 或异常值时一律返回 0：直接相除会得到 NaN / Infinity，
 * 而 Miuix 的 `CircularProgressIndicator` 内部是 `progress.coerceIn(0f, 1f)`，
 * 对 NaN 两个比较都为假、原样返回，最后算出 NaN 的扫过角度画出一团乱七八糟的东西。
 * 纯函数，便于单测。
 */
internal fun deviceUsageFraction(appBytes: Long, deviceTotalBytes: Long): Float =
    if (deviceTotalBytes <= 0L) {
        0f
    } else {
        (appBytes.toDouble() / deviceTotalBytes).toFloat().coerceIn(0f, 1f)
    }

/**
 * 占比的可读文案。
 *
 * 应用占用相对设备容量往往极小（几百 MB 对几百 GB），所以小于 0.05% 时显示「<0.1%」
 * 而不是四舍五入成「0.0%」——后者看起来像没占空间。小于 10% 保留一位小数，其余取整。
 */
internal fun formatPercent(fraction: Float): String {
    // 调用方（deviceUsageFraction）已保证有限，这里再兜一层，避免渲染出「NaN%」
    if (!fraction.isFinite()) return "0%"
    val percent = (fraction * 100f).coerceAtLeast(0f)
    return when {
        percent < 0.05f -> "<0.1%"
        percent < 10f -> String.format(Locale.US, "%.1f%%", percent)
        else -> String.format(Locale.US, "%.0f%%", percent)
    }
}

/**
 * 本地存储占用扫描器。
 *
 * 以 `context.dataDir`（`/data/user/0/<包名>`）为根遍历一次，把每个文件的字节数
 * 归入唯一分类。不写死包名，因此 internal / legacy 变体各自统计自己的数据目录。
 */
class StorageScanner(private val context: Context) {

    /** 必须在 IO 线程执行（遍历 + 逐文件 stat 都是阻塞调用）。 */
    suspend fun scan(): StorageSnapshot = withContext(Dispatchers.IO) {
        val dataDir = context.dataDir
        val buckets = linkedMapOf<StorageCategory, Long>()
        // 预置全部分类，保证即使某类为 0 也会出现在明细里
        StorageCategory.entries.forEach { buckets[it] = 0L }

        var total = 0L
        dataDir.walkTopDown()
            .onEnter { dir -> shouldEnter(dir) }
            // 只累加文件本身，绝不给目录计一次，否则父子会被重复计算
            .filter { it.isFile }
            .forEach { file ->
                val length = file.length().coerceAtLeast(0L)
                total += length
                val category = categorizeStoragePath(file.relativeTo(dataDir).path)
                buckets[category] = (buckets[category] ?: 0L) + length
            }

        // 兜底：把「已归类之外」的差额并入 Other。因为是 total - named，
        // 从构造上保证 sum(categories) == total，也顺带吸收将来新增的未知路径。
        val namedTotal = buckets.filterKeys { it != StorageCategory.Other }.values.sum()
        buckets[StorageCategory.Other] = (total - namedTotal).coerceAtLeast(0L)

        val snapshot = StorageSnapshot(
            totalBytes = total,
            usableBytes = readUsableBytes(dataDir),
            deviceTotalBytes = readDeviceTotalBytes(dataDir),
            categories = StorageCategory.entries.map { StorageCategoryUsage(it, buckets[it] ?: 0L) },
        )

        // 关键路径日志：总占用与明细，便于核对「总量 == 各分类之和」
        AppLog.d(
            TAG,
            "扫描完成：总占用=${formatBytes(snapshot.totalBytes)}，可用=${formatBytes(snapshot.usableBytes)}，" +
                "明细=" + snapshot.categories.joinToString { "${it.category.title}=${formatBytes(it.bytes)}" },
        )
        snapshot
    }

    /**
     * 是否进入该目录。
     *
     * @return false 表示跳过整棵子树。
     */
    private fun shouldEnter(dir: File): Boolean {
        // code_cache 是编译/JIT 产物，既不是用户数据也不可清理，计入总量只会误导
        if (dir.name == EXCLUDED_DIRECTORY) return false
        // 跳过软链接：dataDir/lib 通常指向 /data/app/<pkg>/lib，跟进会把 APK 原生库算成本应用占用
        return runCatching { !Files.isSymbolicLink(dir.toPath()) }.getOrDefault(true)
    }

    /** 设备可用空间。StatFs 异常时回退到 File.usableSpace（后者在路径不存在时返回 0）。 */
    private fun readUsableBytes(dataDir: File): Long =
        runCatching { StatFs(dataDir.path).availableBytes }
            .onFailure { AppLog.w(TAG, "读取可用空间失败，回退 File.usableSpace", it) }
            .getOrElse { dataDir.usableSpace }

    /** 设备总空间。 */
    private fun readDeviceTotalBytes(dataDir: File): Long =
        runCatching { StatFs(dataDir.path).totalBytes }
            .onFailure { AppLog.w(TAG, "读取总空间失败，回退 File.totalSpace", it) }
            .getOrElse { dataDir.totalSpace }

    private companion object {
        const val TAG = "StorageScanner"

        /** 遍历时整棵跳过的目录名。 */
        const val EXCLUDED_DIRECTORY = "code_cache"
    }
}
