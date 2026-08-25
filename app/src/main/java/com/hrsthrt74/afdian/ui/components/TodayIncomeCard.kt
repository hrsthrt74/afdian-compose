package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.TodayIncome
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「今日收入」统计板块：两张独立小卡并排（金额 / 订单数）。
 * 样式对齐 ManagementScreen 的 Overview 统计卡
 * （primary 色标题 + title1 数值，内边距 horizontal=14dp / vertical=12dp），
 * 主页与管理页共用。
 */
@Composable
fun TodayIncomeCard(income: TodayIncome, modifier: Modifier = Modifier) {
    // 双列数据：左卡金额，右卡订单数
    val entries = listOf(
        "今日收入" to "%.2f".format(income.amount),
        "今日订单数" to income.orderCount.toString()
    )
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { (title, value) ->
            Card(modifier = Modifier.weight(1f), insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Column {
                    Text(
                        title,
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.subtitle
                    )
                    Text(value, style = MiuixTheme.textStyles.title1)
                }
            }
        }
    }
}
