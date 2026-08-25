package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** 月末提现提醒模块：每月最后 7 天展示，无关闭入口。 */
@Composable
fun PayoutReminderModule(context: HomeModuleContentContext) {
    val today = remember { LocalDate.now() }
    val month = remember { YearMonth.from(today) }
    // 仅在每月最后 7 天展示
    val shouldShow = today.dayOfMonth >= month.lengthOfMonth() - 6
    if (!shouldShow) return
    // 计算距离下月 1 日上午 10:00 的时间间隔（精确到天与小时）
    val remainText = remember {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        // 下月 1 日 上午 10:00
        val nextMonthFirst = month.plusMonths(1).atDay(1).atTime(10, 0)
        val duration = Duration.between(now, nextMonthFirst)
        if (duration.isNegative) {
            "已到提现日"
        } else {
            val days = duration.toDays()
            val hours = duration.toHours() % 24
            "${days} 天 ${hours} 小时"
        }
    }
    // 本月收入：从管理概况（Overview）的「本月收入」字段取，未获取到则显示 ---
    val monthIncome = context.creatorOverview
        ?.rows
        ?.firstOrNull { it.first == "本月收入" }
        ?.second
        ?.takeIf { it.isNotBlank() && it != "--" }
        ?: "---"
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier
                .background(MiuixTheme.colorScheme.primaryContainer)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(MiuixIcons.Promotions, "提现提醒", tint = MiuixTheme.colorScheme.onPrimaryContainer)

            Text(
                "距离提现还有 $remainText，请注意~\n" +
                "截至当前，本月收入 ￥$monthIncome",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }
}
