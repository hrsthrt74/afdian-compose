package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.ui.navigation.QUICK_ENTRY_SLOTS
import com.hrsthrt74.afdian.ui.navigation.QuickEntryGroup
import com.hrsthrt74.afdian.ui.navigation.QuickEntryPreset
import com.hrsthrt74.afdian.ui.navigation.defaultHomeQuickEntries
import com.hrsthrt74.afdian.ui.navigation.normalizeQuickEntries
import com.hrsthrt74.afdian.ui.navigation.presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首页「快捷方式」的存储值解析：过滤未知、去重、截断、默认值回落。 */
class QuickEntryPresetTest {

    /** 没配置过时给默认的 4 个，首页不至于凭空少一块。 */
    @Test
    fun emptySavedFallsBackToDefaults() {
        assertEquals(defaultHomeQuickEntries, normalizeQuickEntries(emptyList()))
    }

    /** 配置全是不认识的存储值（手改配置 / 日后目录删条目）时也要回落默认，而不是显示空白。 */
    @Test
    fun unknownOnlySavedFallsBackToDefaults() {
        assertEquals(defaultHomeQuickEntries, normalizeQuickEntries(listOf("no_such_entry", "???")))
    }

    /** 正常配置按原顺序解析。 */
    @Test
    fun savedKeepsOrder() {
        val saved = listOf(
            QuickEntryPreset.Settings.storageValue,
            QuickEntryPreset.Sponsoring.storageValue,
        )
        assertEquals(
            listOf(QuickEntryPreset.Settings, QuickEntryPreset.Sponsoring),
            normalizeQuickEntries(saved),
        )
    }

    /** 未知值被丢掉，同一个入口只保留第一次出现的那个。 */
    @Test
    fun unknownFilteredAndDuplicatedRemoved() {
        val saved = listOf(
            QuickEntryPreset.Favorites.storageValue,
            "no_such_entry",
            QuickEntryPreset.Favorites.storageValue,
            QuickEntryPreset.Orders.storageValue,
        )
        assertEquals(
            listOf(QuickEntryPreset.Favorites, QuickEntryPreset.Orders),
            normalizeQuickEntries(saved),
        )
    }

    /** 存了超过 4 个时只保留前 4 个：一行放不下更多，硬撑会把入口挤扁。 */
    @Test
    fun excessSavedTruncatedToSlots() {
        val saved = QuickEntryPreset.entries.take(QUICK_ENTRY_SLOTS + 3).map { it.storageValue }
        assertEquals(QUICK_ENTRY_SLOTS, normalizeQuickEntries(saved).size)
    }

    /** 每个分组的入口加起来就是整个目录，且互不重叠（面板分区渲染依赖这点）。 */
    @Test
    fun groupsPartitionCatalog() {
        val grouped = QuickEntryGroup.entries.flatMap { it.presets() }
        assertEquals(QuickEntryPreset.entries.toList(), grouped)
        assertEquals(grouped.size, grouped.distinct().size)
    }

    /** 存储值互不相同：DataStore 里就是拿它当键的，撞了会互相覆盖。 */
    @Test
    fun storageValuesUnique() {
        val values = QuickEntryPreset.entries.map { it.storageValue }
        assertEquals(values.size, values.distinct().size)
        // 反查也要能对上，否则老配置读回来全是 null
        values.forEach { value ->
            assertTrue(value, QuickEntryPreset.fromStorage(value) != null)
        }
    }

    /** 默认的 4 个不超槽位数，且都在目录里。 */
    @Test
    fun defaultsFitSlots() {
        assertTrue(defaultHomeQuickEntries.size <= QUICK_ENTRY_SLOTS)
        assertEquals(defaultHomeQuickEntries.size, defaultHomeQuickEntries.distinct().size)
    }
}
