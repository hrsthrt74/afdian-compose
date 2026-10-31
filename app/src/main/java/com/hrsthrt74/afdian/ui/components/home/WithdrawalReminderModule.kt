package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.network.afdianWebUrl
import com.hrsthrt74.afdian.data.stats.CardKey
import com.hrsthrt74.afdian.data.stats.CardMetric
import com.hrsthrt74.afdian.data.stats.CardPeriod
import com.hrsthrt74.afdian.data.stats.WithdrawalAmountTexts
import com.hrsthrt74.afdian.data.stats.WithdrawalSchedule
import com.hrsthrt74.afdian.data.stats.WithdrawalStatus
import com.hrsthrt74.afdian.data.stats.settlementAmountTexts
import com.hrsthrt74.afdian.ui.navigation.LocalAppLinkNavigator
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds

private const val TAG = "WithdrawalReminder"

/**
 * 提现状态的刷新间隔。
 *
 * 文案最细只到「天 + 小时」，每分钟读一次系统时间已经足够：
 * 跨午夜、跨 1 日 10:00 最多滞后一分钟，也不会引入每秒重组。
 */
private val StatusRefreshInterval = 60.seconds

/**
 * 随时间自动推进的提现状态。
 *
 * 不能像旧实现那样用无 key 的 `remember { LocalDateTime.now() }`：
 * 这个模块在月末 7 天常驻，进程跨过午夜后 now 会永远停在「上个月」那一刻，
 * 于是 1 日 0:00–10:00 一直判不出结算期，卡片会照旧显示「本月收入 0.00」。
 *
 * 这里用 [produceState] 每分钟重算一次，并且**只在状态真的变化时**才写回 state：
 * 剩余时长按小时变化，实际重组约每小时一次，不会因为定时器变成每分钟重组。
 *
 * @param isBalanceSettling 服务端结算期信号；变化时重启计时循环，立刻按新信号重算
 */
@Composable
private fun rememberWithdrawalStatus(isBalanceSettling: Boolean): WithdrawalStatus =
    produceState(
        // 首帧就给出真值，避免先渲染一帧别的口径再跳
        initialValue = WithdrawalSchedule.calculate(LocalDateTime.now(), isBalanceSettling),
        // 服务端信号翻转（结算开始 / 结算完成）时，重新计算基准
        key1 = isBalanceSettling,
    ) {
        while (true) {
            val next = WithdrawalSchedule.calculate(LocalDateTime.now(), isBalanceSettling)
            if (next != value) {
                AppLog.d(
                    TAG,
                    "提现状态更新：withdrawTime=${next.withdrawalTime}, show=${next.shouldShowReminder}, " +
                        "settlementWindow=${next.isSettlementWindow}, serverSettling=$isBalanceSettling",
                )
                value = next
            }
            delay(StatusRefreshInterval)
        }
    }.value

/** 月末提现提醒模块：每月最后 7 天 + 1 日结算期展示，无关闭入口。 */
@Composable
fun WithdrawalReminderModule(context: HomeModuleContentContext) {
    val appLinkNavigator = LocalAppLinkNavigator.current
    // 状态随系统时间推进（跨午夜自动翻月），同时吃服务端的结算期信号
    val status = rememberWithdrawalStatus(context.isBalanceSettling)
    if (!status.shouldShowReminder) return

    val monthIncomeKey = CardKey(CardMetric.Income, CardPeriod.ThisMonth).storageValue
    val monthIncomeAfterFeeKey = CardKey(CardMetric.IncomeAfterFee, CardPeriod.ThisMonth).storageValue

    // ---- 金额口径 ----
    // 结算期（每月 1 日 0:00–10:00，或服务端 balance 报告「结算中」）内，
    // dashboard 的「本月」口径已经翻篇成新月份（通常是 0.00），用户关心的是正在结算的上月收入：
    // 「收入」直接取 summary.unconfirmed_amount（待结算金额），
    // 「到手」没有对应接口，用同一份到手比例折算（见 settlementAmountTexts）。
    val periodLabel = if (status.isSettlementWindow) "上月" else "本月"
    val amounts = if (status.isSettlementWindow) {
        settlementAmountTexts(context.unconfirmedAmount)
    } else {
        WithdrawalAmountTexts(
            income = context.creatorCards[monthIncomeKey]?.value ?: "---",
            afterFee = context.creatorCards[monthIncomeAfterFeeKey]?.value ?: "---",
        )
    }

    // 第一行文案：结算还没完但提现时间点已过时，说「已到提现日」才通顺
    val countdownText = if (status.isWithdrawalTimeReached) {
        "已到提现日，结算完成后即可提现~"
    } else {
        "距离提现还有 ${status.remainingText}，请注意~"
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp)) {
        Row(
            Modifier
                .background(MiuixTheme.colorScheme.primaryContainer)
                .fillMaxWidth()
                // 提现页在外站，域名跟随优先线路
                .clickable { appLinkNavigator.open(afdianWebUrl("/withdraw")) }
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 提现提醒图标
            Icon(MiuixIcons.Promotions, "提现提醒", tint = MiuixTheme.colorScheme.onPrimaryContainer)

            Text(
                "$countdownText\n" +
                "${periodLabel}收入 ￥${amounts.income}，" +
                "到手 ￥${amounts.afterFee}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }
}
