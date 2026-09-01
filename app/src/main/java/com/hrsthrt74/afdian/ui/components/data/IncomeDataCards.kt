package com.hrsthrt74.afdian.ui.components.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hrsthrt74.afdian.data.content.TodayIncome

/**
 * 收入数据卡片组。
 *
 * 今日、昨日、前日等数据都应作为 DataCardItem 放入同一个卡片组，
 * 不为每个日期创建一个独立组件。当前接口只提供今日收入，后续可直接追加更多项。
 */
@Composable
fun IncomeDataCards(
    items: List<DataCardItem>,
    modifier: Modifier = Modifier,
) {
    DataCardGrid(items = items, modifier = modifier)
}

/** 将现有今日收入模型转换为统一的数据卡片项。 */
fun TodayIncome.toDataCardItems(): List<DataCardItem> = listOf(
    DataCardItem("今日收入", "%.2f".format(amount)),
    DataCardItem("今日订单数", orderCount.toString()),
)
