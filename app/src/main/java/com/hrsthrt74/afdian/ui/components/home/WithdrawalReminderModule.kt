package com.hrsthrt74.afdian.ui.components.home

import com.hrsthrt74.afdian.AppLog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
fun WithdrawalReminderModule(context: HomeModuleContentContext) {
    val uriHandler = LocalUriHandler.current
    val today = remember { LocalDate.now() }
    val month = remember { YearMonth.from(today) }
    val now = remember { LocalDateTime.now(ZoneId.systemDefault()) }
    // 提现截止时间：若今天是1日且还没到10点则为今天10点，否则为下月1日10点
    val withdrawTime = remember {
        val nextFirst = month.plusMonths(1).atDay(1).atTime(10, 0)
        val todayFirst = month.atDay(1).atTime(10, 0)
        if (today.dayOfMonth == 1 && now.isBefore(todayFirst)) todayFirst else nextFirst
    }
    AppLog.d("WithdrawalReminder", "today=$today, now=$now, withdrawTime=$withdrawTime, isBefore=${now.isBefore(withdrawTime)}, dayOfMonth=${today.dayOfMonth}, lengthOfMonth=${month.lengthOfMonth()}, threshold=${month.lengthOfMonth() - 6}")
    // 仅在每月最后 7 天展示，且1日10点前仍展示
    val shouldShow = today.dayOfMonth >= month.lengthOfMonth() - 6 ||
        (today.dayOfMonth == 1 && now.isBefore(month.atDay(1).atTime(10, 0)))
    if (!shouldShow) return
    // 计算距离下月 1 日上午 10:00 的时间间隔（精确到天与小时）
    val remainText = remember {
        val duration = Duration.between(now, withdrawTime)
        if (duration.isNegative) {
            "已到提现日"
        } else {
            val days = duration.toDays()
            val hours = duration.toHours() % 24
            if (days == 0L && hours == 0L) {
                "不足一小时"
            } else {
                "${days} 天 ${hours} 小时"
            }
        }
    }
    // 本月收入：从管理概况（Overview）的「本月收入」字段取，未获取到则显示 ---
    val monthIncomeStr = context.creatorOverview
        ?.rows
        ?.firstOrNull { it.first == "本月收入" }
        ?.second
        ?.takeIf { it.isNotBlank() && it != "--" }
        ?: "---"
    // 计算实际到手金额（平台抽成约6%，实际到手94%）
    val actualIncomeText = remember(monthIncomeStr) {
        if (monthIncomeStr == "---") {
            "---"
        } else {
            try {
                // 去除可能存在的逗号和货币符号，提取数字
                val cleanStr = monthIncomeStr.replace("[,，￥¥$]".toRegex(), "")
                val income = cleanStr.toDouble()
                // 计算94%，保留两位小数
                val actualIncome = income * 0.94
                // 格式化：如果小数部分为0则显示整数，否则保留两位小数
                if (actualIncome == actualIncome.toLong().toDouble()) {
                    actualIncome.toLong().toString()
                } else {
                    String.format("%.2f", actualIncome)
                }
            } catch (e: NumberFormatException) {
                // 解析失败时显示原始值的94%估算
                "---"
            }
        }
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp)) {
        Row(
            Modifier
                .background(MiuixTheme.colorScheme.primaryContainer)
                .fillMaxWidth()
                .clickable { uriHandler.openUri("https://ifdian.net/withdraw") }
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(MiuixIcons.Promotions, "提现提醒", tint = MiuixTheme.colorScheme.onPrimaryContainer)

            Text(
                "距离提现还有 $remainText，请注意~\n" +
                "本月收入 ￥$monthIncomeStr，" +
                "到手约 ￥$actualIncomeText",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }
}
