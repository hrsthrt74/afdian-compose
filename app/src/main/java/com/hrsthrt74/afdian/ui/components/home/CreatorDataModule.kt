package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.data.DataCardItem
import com.hrsthrt74.afdian.ui.components.data.DataCardGrid
import com.hrsthrt74.afdian.ui.components.data.IncomeDataCards
import com.hrsthrt74.afdian.ui.components.data.toDataCardItems

/** 创作者收入和管理概况模块。 */
@Composable
fun CreatorDataModule(context: HomeModuleContentContext) {
    Column {
        context.todayIncome?.let {
            ClickableSmallTitle(
                title = "创作者数据",
                onClick = context.onOpenManagement,
            )
            IncomeDataCards(it.toDataCardItems(), Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
        }
        context.creatorOverview?.let {
            DataCardGrid(
                items = it.rows
                    .filter { row -> row.first in setOf("本月收入", "主页访问人数", "主页访问次数", "近 31 天赞助者") }
                    .map { (title, value) -> DataCardItem(title, value) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
