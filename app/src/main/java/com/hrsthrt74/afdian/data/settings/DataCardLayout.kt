package com.hrsthrt74.afdian.data.settings

import com.hrsthrt74.afdian.data.stats.CardKey
import com.hrsthrt74.afdian.data.stats.CardMetric
import com.hrsthrt74.afdian.data.stats.CardPeriod
import com.hrsthrt74.afdian.data.stats.defaultHomeCreatorDataCardKeys
import kotlinx.serialization.Serializable

/** 预置的管理页布局元素。标题不允许自由输入，避免设置数据和文案失控。 */
@Serializable
enum class ManagementLayoutType { CARD, TITLE, DIVIDER }

@Serializable
data class ManagementLayoutEntry(
    val type: ManagementLayoutType,
    val value: String,
)

/** 默认布局：经典爱发电布局，但是更好~ */
val defaultManagementLayout = listOf(
    ManagementLayoutEntry(ManagementLayoutType.TITLE, "收入"),
    cardEntry(CardMetric.Income, CardPeriod.Today),
    cardEntry(CardMetric.SponsorOrderCount, CardPeriod.Today),
    cardEntry(CardMetric.Income, CardPeriod.ThisMonth),
    cardEntry(CardMetric.Income, CardPeriod.History),

//    ManagementLayoutEntry(ManagementLayoutType.DIVIDER, "divider_income"),

    ManagementLayoutEntry(ManagementLayoutType.TITLE, "发电人数"),
    cardEntry(CardMetric.SponsorCount, CardPeriod.ThisMonth),
    cardEntry(CardMetric.SponsorCount, CardPeriod.History),

    ManagementLayoutEntry(ManagementLayoutType.TITLE, "发电次数"),
    cardEntry(CardMetric.SponsorOrderCount, CardPeriod.Yesterday),
    cardEntry(CardMetric.SponsorOrderCount, CardPeriod.ThisMonth),

//    ManagementLayoutEntry(ManagementLayoutType.DIVIDER, "divider_sponsor"),

    ManagementLayoutEntry(ManagementLayoutType.TITLE, "访问数据"),
    cardEntry(CardMetric.ProfileUv, CardPeriod.History),
    cardEntry(CardMetric.ProfilePv, CardPeriod.History),

//    ManagementLayoutEntry(ManagementLayoutType.DIVIDER, "divider_visitors"),

    ManagementLayoutEntry(ManagementLayoutType.TITLE, "提现"),
    cardEntry(CardMetric.Balance),
    cardEntry(CardMetric.BalanceAfterTax),
    cardEntry(CardMetric.WithdrawalRemainingDays),

    // 其他分类预留，暂不添加卡片：
    // ManagementLayoutEntry(ManagementLayoutType.TITLE, "其他"),
    // cardEntry(...),
)

private fun cardEntry(metric: CardMetric, period: CardPeriod? = null): ManagementLayoutEntry =
    ManagementLayoutEntry(ManagementLayoutType.CARD, CardKey(metric, period).storageValue)

/** 首页创作者数据模块默认只放卡片，标题和分割线由编辑模式添加。 */
val defaultHomeCreatorDataLayout = defaultHomeCreatorDataCardKeys.map {
    ManagementLayoutEntry(ManagementLayoutType.CARD, it)
}
