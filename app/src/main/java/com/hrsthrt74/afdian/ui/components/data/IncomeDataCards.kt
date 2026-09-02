package com.hrsthrt74.afdian.ui.components.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 收入数据卡片组的通用布局封装。
 *
 * 今日、昨日、近 n 天等数据都应作为 DataCardItem 放入同一个卡片组，
 * 不为每个日期创建一个独立组件。
 */
@Composable
fun IncomeDataCards(
    items: List<DataCardItem>,
    modifier: Modifier = Modifier,
) {
    DataCardGrid(items = items, modifier = modifier)
}
