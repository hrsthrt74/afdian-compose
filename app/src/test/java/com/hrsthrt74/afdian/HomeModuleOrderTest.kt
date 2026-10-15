package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.settings.HomeModule
import com.hrsthrt74.afdian.data.settings.mergeHomeModuleOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** 首页模块顺序合并逻辑：缺失模块要补进来，且补到声明顺序里的前驱之后。 */
class HomeModuleOrderTest {

    /** 空配置（首次启动）应得到枚举声明顺序的全部模块。 */
    @Test
    fun emptySavedYieldsDeclarationOrder() {
        assertEquals(HomeModule.entries.toList(), mergeHomeModuleOrder(emptyList()))
    }

    /**
     * 老用户配置里缺「近5天收入」时，它必须补到「创作者数据」之后，
     * 而不是像旧的 `(saved + entries).distinct()` 那样被追加到最末尾。
     *
     * 「快捷方式」是枚举首项、没有前驱，走的是「追加末尾」那条兜底分支——
     * 老用户默认会看到它排在最后，需要的话在「自定义首页」里拖到上面即可。
     */
    @Test
    fun missingModuleInsertedAfterPredecessor() {
        val saved = listOf(
            HomeModule.WithdrawalReminder,
            HomeModule.CreatorData,
            HomeModule.Sponsoring,
            HomeModule.MarkedCreators,
            HomeModule.History,
        )
        val merged = mergeHomeModuleOrder(saved)
        assertEquals(
            listOf(
                HomeModule.WithdrawalReminder,
                HomeModule.CreatorData,
                HomeModule.RecentIncome,
                HomeModule.Sponsoring,
                HomeModule.MarkedCreators,
                HomeModule.History,
                HomeModule.QuickEntries,
            ),
            merged,
        )
    }

    /** 用户自定义过顺序时，以自定义顺序为准，缺失项只补位置不重排已有项。 */
    @Test
    fun customOrderPreserved() {
        val saved = listOf(HomeModule.History, HomeModule.RecentIncome, HomeModule.CreatorData)
        val merged = mergeHomeModuleOrder(saved)
        // 已有三项的相对顺序不动；缺失项按「前驱在列表中的位置」插入：
        // QuickEntries 是首项、无前驱 → 先追加末尾；WithdrawalReminder 前驱是 QuickEntries → 插到其后；
        // Sponsoring 前驱是 RecentIncome → 插到其后；MarkedCreators 前驱是 Sponsoring → 再插到其后
        assertEquals(
            listOf(
                HomeModule.History,
                HomeModule.RecentIncome,
                HomeModule.Sponsoring,
                HomeModule.MarkedCreators,
                HomeModule.CreatorData,
                HomeModule.QuickEntries,
                HomeModule.WithdrawalReminder,
            ),
            merged,
        )
    }

    /** 存储值被改坏导致重复项时，结果里不应出现重复模块。 */
    @Test
    fun duplicatedSavedEntriesCollapsed() {
        val saved = listOf(HomeModule.CreatorData, HomeModule.CreatorData, HomeModule.History)
        val merged = mergeHomeModuleOrder(saved)
        assertEquals(merged.distinct(), merged)
        assertEquals(HomeModule.entries.size, merged.size)
    }

    /** 「近5天收入」默认关闭，其余模块默认开启。 */
    @Test
    fun defaultEnabledSetExcludesRecentIncome() {
        assertEquals(false, HomeModule.RecentIncome.defaultEnabled)
        assertEquals(
            HomeModule.entries.filter { it != HomeModule.RecentIncome }.toSet(),
            HomeModule.defaultEnabledSet,
        )
    }
}