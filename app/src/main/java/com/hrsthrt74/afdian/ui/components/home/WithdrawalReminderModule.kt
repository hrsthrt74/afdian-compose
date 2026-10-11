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
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.math.RoundingMode
import java.time.LocalDateTime
import com.hrsthrt74.afdian.data.stats.CREATOR_SHARE_RATIO
import com.hrsthrt74.afdian.data.stats.CardKey
import com.hrsthrt74.afdian.data.stats.CardMetric
import com.hrsthrt74.afdian.data.stats.CardPeriod
import com.hrsthrt74.afdian.data.stats.CardValue
import com.hrsthrt74.afdian.data.stats.WithdrawalSchedule
import com.hrsthrt74.afdian.data.stats.displayText
import com.hrsthrt74.afdian.ui.navigation.LocalAppLinkNavigator

/** 月末提现提醒模块：每月最后 7 天展示，无关闭入口。 */
@Composable
fun WithdrawalReminderModule(context: HomeModuleContentContext) {
    val appLinkNavigator = LocalAppLinkNavigator.current
    val now = remember { LocalDateTime.now() }
    val status = remember(now) { WithdrawalSchedule.calculate(now) }
    AppLog.d(
        "WithdrawalReminder",
        "now=$now, withdrawTime=${status.withdrawalTime}, show=${status.shouldShowReminder}, " +
            "settlementWindow=${status.isSettlementWindow}",
    )
    if (!status.shouldShowReminder) return

    val monthIncomeKey = CardKey(CardMetric.Income, CardPeriod.ThisMonth).storageValue
    val monthIncomeAfterFeeKey = CardKey(CardMetric.IncomeAfterFee, CardPeriod.ThisMonth).storageValue
    val lastMonthIncomeKey = CardKey(CardMetric.Income, CardPeriod.LastMonth).storageValue
    val lastMonthIncomeAfterFeeKey = CardKey(CardMetric.IncomeAfterFee, CardPeriod.LastMonth).storageValue

    // ---- 金额口径 ----
    // 结算窗口（每月 1 日 0:00–10:00）内 dashboard 的「本月」口径已翻篇成新月份（通常是 0.00），
    // 而此时用户关心的是正在结算的上月收入，因此整卡切换为「上月」口径；窗口外维持原样。
    val periodLabel: String
    val incomeText: String
    val actualIncomeText: String
    if (status.isSettlementWindow) {
        periodLabel = "上月"
        // 上月收入：优先 dashboard 同帧的未确认金额（= 待结算的上月收入，不依赖账单翻页）；
        // 该字段缺失时回退上月收入卡（账单聚合口径），仍取不到则显示 “--”。
        incomeText = context.unconfirmedAmount?.let { CardValue.Money(it).displayText() }
            ?: context.creatorCards[lastMonthIncomeKey]?.value?.takeIf { it != "--" }
            ?: "---"
        // 上月到手：优先官方口径（月度账单 creator_amount，1 日出账后自动生效）；
        // 官方值未出时用未确认金额 × 94% 估算——与当月抽成后卡的算法同源，
        // 估算向下取整到分，宁可少算一分也不多算。
        actualIncomeText = context.creatorCards[lastMonthIncomeAfterFeeKey]?.value?.takeIf { it != "--" }
            ?: context.unconfirmedAmount?.let {
                CardValue.Money(it.multiply(CREATOR_SHARE_RATIO).setScale(2, RoundingMode.FLOOR)).displayText()
            }
            ?: "---"
    } else {
        periodLabel = "本月"
        incomeText = context.creatorCards[monthIncomeKey]?.value ?: "---"
        actualIncomeText = context.creatorCards[monthIncomeAfterFeeKey]?.value ?: "---"
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp)) {
        Row(
            Modifier
                .background(MiuixTheme.colorScheme.primaryContainer)
                .fillMaxWidth()
                .clickable { appLinkNavigator.open("https://ifdian.net/withdraw") }
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 提现提醒图标
            Icon(MiuixIcons.Promotions, "提现提醒", tint = MiuixTheme.colorScheme.onPrimaryContainer)

            Text(
                "距离提现还有 ${status.remainingText}，请注意~\n" +
                "${periodLabel}收入 ￥$incomeText，" +
                "到手 ￥$actualIncomeText",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }
}
