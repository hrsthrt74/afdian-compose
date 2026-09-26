package com.hrsthrt74.afdian.data.storage

import coil3.disk.DiskCache
import com.hrsthrt74.afdian.AppLog
import okio.FileSystem

/**
 * Coil 全局图片磁盘缓存的目录名。
 *
 * Coil 3 自己的默认单例磁盘缓存用的是
 * `FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "coil3_disk_cache"`（Android 上 SYSTEM_TEMPORARY_DIRECTORY
 * 即 `java.io.tmpdir`，指向应用的 cacheDir）。ImageLoader 的构建与存储页的分类都要认这个目录名，
 * 集中在这里，避免两处各写一份、日后 Coil 改名时漏改一处。
 */
internal const val IMAGE_CACHE_DIRECTORY_NAME = "coil3_disk_cache"

/** 日志 TAG。 */
private const val TAG = "ImageCache"

/**
 * 图片缓存（Coil 全局磁盘缓存）的体积上限档位。
 *
 * @property storageValue DataStore 里的持久化值。用字符串而不是下标，日后增删档位不会打乱旧值。
 * @property label 设置项上展示的档位名。
 * @property maxBytes 上限字节数，直接喂给 `DiskCache.Builder.maxSizeBytes`。
 */
enum class ImageCacheLimit(val storageValue: String, val label: String, val maxBytes: Long) {
    M128("128m", "128 MB", 128L * 1024 * 1024),
    M256("256m", "256 MB    ", 256L * 1024 * 1024),
    G1("1g", "1 GB    ", 1024L * 1024 * 1024),

    /**
     * 不额外设限。
     *
     * 这里不能简单地「跳过 maxSizeBytes」——那会退回 Coil 的默认策略
     * （可用空间的 2%，钳制在 10~250MB），比 1GB 档还小，滑块档位就不是单调递增的了。
     * 所以显式给一个极大值：`DiskLruCache` 只把 maxSize 用在 `size > maxSize` 这类比较上，
     * 没有任何与它成比例的分配，等价于永不裁剪。
     */
    Unlimited("unlimited", "无限制", Long.MAX_VALUE),
    ;

    companion object {
        /** 默认档位：128 MB */
        val Default = M128

        fun fromStorage(value: String?): ImageCacheLimit =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

/**
 * 按 [limit] 构建图片磁盘缓存。
 *
 * **为什么必须自己建一个**：Coil 的 `DiskCache.maxSize` 是不可变的 `val`，
 * `DiskLruCache.trimToSize()` 是 `private`，公开 API 里只有 `evictAll()`（整体清空）。
 * 也就是说「超出上限就删掉最早的缓存」这件事**没有任何运行时入口**，
 * 只能把上限在构建时交给 Coil，由它自己的 LRU 在每次写入后执行
 * `while (size > maxSize) removeOldestEntry()` —— 这恰好就是要的效果。
 *
 * **代价**：上限属于构建期配置，改档位必须重建 ImageLoader；
 * 而 Coil 明确要求「同一目录不允许两个 DiskCache 实例同时存活」，
 * 运行时热替换有损坏缓存的风险，所以这个档位标注为「重启后生效」。
 *
 * 会打开并解析该目录下的 journal 文件（Coil 默认的单例磁盘缓存也是这么做的）。
 */
internal fun buildImageDiskCache(limit: ImageCacheLimit): DiskCache {
    val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(IMAGE_CACHE_DIRECTORY_NAME)
    AppLog.d(TAG, "构建图片磁盘缓存：上限=${limit.label}，目录=$directory")
    return DiskCache.Builder()
        .directory(directory)
        .maxSizeBytes(limit.maxBytes)
        .build()
}
