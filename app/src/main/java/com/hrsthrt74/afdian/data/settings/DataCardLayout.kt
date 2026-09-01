package com.hrsthrt74.afdian.data.settings

import kotlinx.serialization.Serializable

/** 管理页可选择的数据卡片。新增数据时只需扩展目录和数据映射。 */
enum class ManagementCardId(val storageValue: String, val title: String, val group: String) {
    TodayIncome("today_income", "今日收入", "收入"),
    TodayOrders("today_orders", "今日订单数", "收入"),
    MonthIncome("month_income", "本月收入", "收入"),
    TotalIncome("total_income", "累计收入", "收入"),
    MonthSponsors("month_sponsors", "近 31 天赞助者", "收入"),
    TotalSponsors("total_sponsors", "历史赞助者", "收入"),
    ProfileVisitors("profile_visitors", "主页访问人数", "访问数据"),
    ProfileViews("profile_views", "主页访问次数", "访问数据"),
    Balance("balance", "当前可提现", "提现"),
    BalanceAfterTax("balance_after_tax", "提现实际到手", "提现");

    companion object {
        fun fromStorage(value: String): ManagementCardId? = entries.firstOrNull { it.storageValue == value }
    }
}

/** 预置的管理页布局元素。标题不允许自由输入，避免设置数据和文案失控。 */
@Serializable
enum class ManagementLayoutType { CARD, TITLE, DIVIDER }

@Serializable
data class ManagementLayoutEntry(
    val type: ManagementLayoutType,
    val value: String,
)

/** 默认布局：按数据含义分组，同时保留所有原有 Overview 数据。 */
val defaultManagementLayout = listOf(
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.TodayIncome.storageValue),
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.TodayOrders.storageValue),
    ManagementLayoutEntry(ManagementLayoutType.TITLE, "收入"),
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.MonthIncome.storageValue),
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.TotalIncome.storageValue),
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.MonthSponsors.storageValue),
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.TotalSponsors.storageValue),
    ManagementLayoutEntry(ManagementLayoutType.DIVIDER, "divider_income"),
    ManagementLayoutEntry(ManagementLayoutType.TITLE, "访问数据"),
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.ProfileVisitors.storageValue),
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.ProfileViews.storageValue),
    ManagementLayoutEntry(ManagementLayoutType.DIVIDER, "divider_visitors"),
    ManagementLayoutEntry(ManagementLayoutType.TITLE, "提现"),
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.Balance.storageValue),
    ManagementLayoutEntry(ManagementLayoutType.CARD, ManagementCardId.BalanceAfterTax.storageValue),
)
