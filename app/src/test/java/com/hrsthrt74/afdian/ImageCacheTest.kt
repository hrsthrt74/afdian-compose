package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 图片缓存上限档位的回归测试。
 *
 * 这块的关键不变式是「档位单调递增」：上限直接喂给 Coil 的 `maxSizeBytes`，
 * 一旦高位的上限比低位还小，滑块往右拖反而把缓存限制得更紧，是完全说不通的行为。
 */
class ImageCacheTest {

    @Test
    fun limitsAreMonotonicallyIncreasing() {
        val bytes = ImageCacheLimit.entries.map { it.maxBytes }
        assertEquals("档位上限必须递增，实际 $bytes", bytes.sorted(), bytes)
    }

    @Test
    fun unlimitedIsNotSmallerThanTheLargestFiniteLimit() {
        // 「无限制」当初差点写成「不调用 maxSizeBytes」，那会退回 Coil 默认的 250MB，
        // 比 1GB 档还小 —— 这条断言就是防止再犯
        val finiteMax = ImageCacheLimit.entries.filter { it != ImageCacheLimit.Unlimited }.maxOf { it.maxBytes }
        assertEquals(Long.MAX_VALUE, ImageCacheLimit.Unlimited.maxBytes)
        assertEquals(true, ImageCacheLimit.Unlimited.maxBytes > finiteMax)
    }

    @Test
    fun storageValuesRoundTripAndFallBackToDefault() {
        ImageCacheLimit.entries.forEach { limit ->
            assertEquals(limit, ImageCacheLimit.fromStorage(limit.storageValue))
        }
        // 旧版本没有这个键（null）、或读到无法识别的值，都要回落到默认档而不是崩
        assertEquals(ImageCacheLimit.Default, ImageCacheLimit.fromStorage(null))
        assertEquals(ImageCacheLimit.Default, ImageCacheLimit.fromStorage(""))
        assertEquals(ImageCacheLimit.Default, ImageCacheLimit.fromStorage("不存在的档位"))
    }
}
