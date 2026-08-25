package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.hrsthrt74.afdian.ui.components.TodayIncomeCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 创作者收入和管理概况模块。 */
@Composable
fun CreatorDataModule(context: HomeModuleContentContext) {
    Column {
        context.todayIncome?.let {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallTitle("创作者数据", modifier = Modifier.padding(start = 4.dp))
                Box(Modifier.clickable(onClick = context.onOpenManagement, indication = null, interactionSource = remember { MutableInteractionSource() })){
                    Row(
                        modifier = Modifier.padding(28.dp, 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "更多",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Icon(
                            MiuixIcons.ChevronForward, contentDescription = null, tint = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.size(12.dp)
                        )
                    }

                }
            }
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
