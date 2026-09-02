package com.hrsthrt74.afdian.ui.components.data

import com.hrsthrt74.afdian.data.stats.DataCardModel
import com.hrsthrt74.afdian.data.stats.displayText

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
)

/** UI 边界统一把类型化卡片值转换为展示项。 */
fun DataCardModel.toDataCardItem(): DataCardItem = DataCardItem(
    id = id,
    title = title,
    value = value.displayText(),
    subtitle = subtitle,
)
