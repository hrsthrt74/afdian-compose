package com.hrsthrt74.afdian.ui.components.data

import com.hrsthrt74.afdian.data.stats.CardKey
import com.hrsthrt74.afdian.data.stats.DataCardModel
import com.hrsthrt74.afdian.data.stats.displayText
import com.hrsthrt74.afdian.data.stats.unitText

/**
 * 数据卡片展示项。
 *
 * 将业务数据转换为稳定 ID、标题和值后再交给 UI 渲染，避免基础 DataCard 组件依赖具体业务模型。
 */
data class DataCardItem(
    val id: String,
    val title: String,
    val value: String,
    val subtitle: String? = null,
    /** 数值右侧的后导单位，留空则不显示。 */
    val unit: String? = null,
)

/**
 * UI 边界统一把类型化卡片值转换为展示项。
 *
 * 单位由稳定 ID 反查指标得到，所以缓存回填（值类型退化成 [com.hrsthrt74.afdian.data.stats.CardValue.Text]）
 * 也能正常显示单位。
 *
 * @param showIncomeUnit 是否显示金额类（收入、可提现）的「元」
 * @param showCountUnit 是否显示人次类的「人」「次」
 */
fun DataCardModel.toDataCardItem(
    showIncomeUnit: Boolean = false,
    showCountUnit: Boolean = false,
): DataCardItem = DataCardItem(
    id = id,
    title = title,
    value = value.displayText(),
    subtitle = subtitle,
    unit = CardKey.fromStorage(id)?.metric?.unitText(showIncomeUnit, showCountUnit),
)
