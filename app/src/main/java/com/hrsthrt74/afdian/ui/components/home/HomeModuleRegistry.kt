package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.runtime.Composable
import com.hrsthrt74.afdian.data.settings.HomeModule

/** 首页模块定义：模块元数据和内容绑定放在一起，配置界面可直接复用。 */
data class HomeModuleSpec(
    val module: HomeModule,
    val content: @Composable (HomeModuleContentContext) -> Unit,
)

object HomeModuleRegistry {
    /** 注册顺序同时作为新用户的默认顺序。 */
    val all: List<HomeModuleSpec> = listOf(
        HomeModuleSpec(HomeModule.QuickEntries) { QuickEntriesModule(it) },
        HomeModuleSpec(HomeModule.WithdrawalReminder) { WithdrawalReminderModule(it) },
        HomeModuleSpec(HomeModule.CreatorData) { CreatorDataModule(it) },
        HomeModuleSpec(HomeModule.RecentIncome) { RecentIncomeModule(it) },
        HomeModuleSpec(HomeModule.Sponsoring) { SponsoringModule(it) },
        HomeModuleSpec(HomeModule.MarkedCreators) { MarkedCreatorsModule(it) },
        HomeModuleSpec(HomeModule.History) { HistoryModule(it) },
    )

    fun spec(module: HomeModule): HomeModuleSpec? = all.firstOrNull { it.module == module }
}
