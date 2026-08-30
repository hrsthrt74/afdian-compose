package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.hrsthrt74.afdian.ui.components.TodayIncomeCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 创作者收入和管理概况模块。 */
@Composable
fun CreatorDataModule(context: HomeModuleContentContext) {
    Column {
        context.todayIncome?.let {
            ClickableSmallTitle(
                title = "创作者数据",
                onClick = context.onOpenManagement,
            )
            TodayIncomeCard(it, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
        }
        context.creatorOverview?.let {
            HomeOverviewCards(it.rows, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 创作者数据模块的管理概况卡片，与模块本身保持内聚。 */
@Composable
private fun HomeOverviewCards(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val visibleRows = rows.filter { it.first in setOf("本月收入", "主页访问人数", "主页访问次数", "近 31 天赞助者") }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visibleRows.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (title, value) ->
                    Card(modifier = Modifier.weight(1f), insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Column {
                            Text(title, color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(value, style = MiuixTheme.textStyles.title1, maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}
