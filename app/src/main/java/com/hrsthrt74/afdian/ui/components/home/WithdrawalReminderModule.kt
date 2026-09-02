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
import java.time.LocalDateTime
import com.hrsthrt74.afdian.data.stats.CardKey
import com.hrsthrt74.afdian.data.stats.CardMetric
import com.hrsthrt74.afdian.data.stats.CardPeriod
import com.hrsthrt74.afdian.data.stats.WithdrawalSchedule

/** 月末提现提醒模块：每月最后 7 天展示，无关闭入口。 */
@Composable
fun WithdrawalReminderModule(context: HomeModuleContentContext) {
    val uriHandler = LocalUriHandler.current
    val now = remember { LocalDateTime.now() }
    val status = remember(now) { WithdrawalSchedule.calculate(now) }
    AppLog.d("WithdrawalReminder", "now=$now, withdrawTime=${status.withdrawalTime}, show=${status.shouldShowReminder}")
    if (!status.shouldShowReminder) return
    val monthIncomeKey = CardKey(CardMetric.Income, CardPeriod.ThisMonth).storageValue
    val monthIncomeStr = context.creatorCards[monthIncomeKey]?.value ?: "---"
    val monthIncomeAfterFeeKey = CardKey(CardMetric.IncomeAfterFee, CardPeriod.ThisMonth).storageValue
    val actualIncomeText = context.creatorCards[monthIncomeAfterFeeKey]?.value ?: "---"
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
                "距离提现还有 ${status.remainingText}，请注意~\n" +
                "本月收入 ￥$monthIncomeStr，" +
                "到手 ￥$actualIncomeText",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }
}
