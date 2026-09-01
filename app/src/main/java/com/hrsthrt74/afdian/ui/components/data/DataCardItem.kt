package com.hrsthrt74.afdian.ui.components.data

/**
 * 数据卡片展示项。
 *
 * 将业务数据转换为标题和值后再交给 UI 渲染，避免基础 DataCard 组件依赖具体业务模型。
 */
data class DataCardItem(
    val title: String,
    val value: String,
)
