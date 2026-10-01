package com.hrsthrt74.afdian.data.stats

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** 当前提现周期状态，管理页数据卡和首页提醒共用同一套计算。 */
data class WithdrawalStatus(
    val shouldShowReminder: Boolean,
    val withdrawalTime: LocalDateTime,
    val remainingDays: Long,
    val remainingText: String,
    /**
     * 是否处于每月 1 日 0:00–10:00 的结算窗口。
     * 此区间 dashboard 的「本月」口径已翻篇成新月份，提现提醒卡需要切换为「上月」口径，
     * 与 shouldShowReminder 的 1 日分支共用同一判断，避免两处时间逻辑漂移。
     */
    val isSettlementWindow: Boolean,
)

/**
 * 提现时间计算器。
 *
 * 爱发电提现日规则沿用现有首页提醒：每月 1 日 10:00；1 日 10:00 前仍指向当天，
 * 其他时间指向下月 1 日 10:00。now 由调用方传入，方便单元测试并避免 Composable 内部各自计算。
 */
object WithdrawalSchedule {
    fun calculate(now: LocalDateTime): WithdrawalStatus {
        val today = now.toLocalDate()
        val month = YearMonth.from(today)
        val todayWithdrawalTime = month.atDay(1).atTime(10, 0)
        val nextWithdrawalTime = month.plusMonths(1).atDay(1).atTime(10, 0)
        // 结算窗口：每月 1 日 0:00（自然月翻篇）到 10:00（结算完成）之间。
        // 提现倒计时指向今天、提醒继续展示、口径切换上月，三个行为共用这一个判断。
        val isSettlementWindow = today.dayOfMonth == 1 && now.isBefore(todayWithdrawalTime)
        val withdrawalTime = if (isSettlementWindow) {
            todayWithdrawalTime
        } else {
            nextWithdrawalTime
        }
        val duration = Duration.between(now, withdrawalTime)
        val remainingDays = duration.toDays().coerceAtLeast(0L)
        val remainingText = when {
            duration.isNegative -> "已到提现日"
            duration.toDays() == 0L && duration.toHours() == 0L -> "不足一小时"
            else -> "${duration.toDays()} 天 ${duration.toHours() % 24} 小时"
        }
        val shouldShowReminder = today.dayOfMonth >= month.lengthOfMonth() - 6 || isSettlementWindow
        return WithdrawalStatus(
            shouldShowReminder = shouldShowReminder,
            withdrawalTime = withdrawalTime,
            remainingDays = remainingDays,
            remainingText = remainingText,
            isSettlementWindow = isSettlementWindow,
        )
    }
}
