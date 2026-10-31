package com.hrsthrt74.afdian.data.stats

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth

/** 当前提现周期状态，管理页数据卡和首页提醒共用同一套计算。 */
data class WithdrawalStatus(
    val shouldShowReminder: Boolean,
    val withdrawalTime: LocalDateTime,
    val remainingDays: Long,
    val remainingText: String,
    /**
     * 提现时间点是否已经过去。
     *
     * 正常路径下 withdrawTime 永远在未来；只有「服务端 10:00 之后仍报告结算中」时才会为 true，
     * 此时提醒卡应说「已到提现日」而不是「距离提现还有 已到提现日」这种病句。
     */
    val isWithdrawalTimeReached: Boolean,
    /**
     * 是否处于结算期。
     *
     * 两个来源任一生效即为 true（见 [WithdrawalSchedule.calculate]）：
     * - 本地时钟落在每月 1 日 0:00–10:00（自然月已翻篇、结算还没完成）
     * - 服务端 dashboard.balance 返回「结算中」文案（服务器比本地时钟权威）
     *
     * 结算期内 dashboard 的「本月」口径已翻篇成新月份（通常是 0.00），
     * 提现提醒卡需要切换为「上月」口径，与 shouldShowReminder 的 1 日分支共用同一判断，
     * 避免两处时间逻辑漂移。
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
    /**
     * @param now 当前本地时间（调用方负责定时推进，见首页提现提醒模块）
     * @param isBalanceSettling 服务端是否报告「结算中」（dashboard.balance 是结算文案）。
     *   设备时区不同、用户改过系统时间、进程跨午夜后本地时钟都不够可信，
     *   此时以服务端信号为准把结算期补齐。
     */
    fun calculate(now: LocalDateTime, isBalanceSettling: Boolean = false): WithdrawalStatus {
        val today = now.toLocalDate()
        val month = YearMonth.from(today)
        val todayWithdrawalTime = month.atDay(1).atTime(10, 0)
        val nextWithdrawalTime = month.plusMonths(1).atDay(1).atTime(10, 0)
        // 是否为 1 号：本地窗口与服务端信号都只在 1 号成立
        val isFirstDay = today.dayOfMonth == 1
        // 本地窗口：每月 1 日 0:00（自然月翻篇）到 10:00（结算完成）之间
        val isLocalWindow = isFirstDay && now.isBefore(todayWithdrawalTime)
        // 服务端信号：balance 说「结算中」。
        // 只在 1 号当天认这个信号——它没有时间戳，dashboard 也只在刷新时才更新；
        // 若进程从 1 号一直活到 5 号都没刷新成功，不加日期保护就会把 5 号误判成结算期。
        val isServerSettling = isFirstDay && isBalanceSettling
        // 提现倒计时指向今天、提醒继续展示、口径切换上月，三个行为共用这一个判断。
        val isSettlementWindow = isLocalWindow || isServerSettling
        val withdrawalTime = if (isSettlementWindow) {
            todayWithdrawalTime
        } else {
            nextWithdrawalTime
        }
        val duration = Duration.between(now, withdrawalTime)
        val remainingDays = duration.toDays().coerceAtLeast(0L)
        // 服务端 10:00 之后仍报结算中时会走到 duration 为负的分支：
        // 「已到提现日」正好就是「到点了但钱还在结算」该说的话。
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
            isWithdrawalTimeReached = duration.isNegative,
            isSettlementWindow = isSettlementWindow,
        )
    }
}

/**
 * 提现提醒卡在结算期要展示的两行金额（已格式化，可直接拼进文案）。
 *
 * 取不到金额时两处都是 [UNKNOWN_AMOUNT]，不用 0.00 冒充——0.00 会被误读成「上月没收入」。
 */
data class WithdrawalAmountTexts(
    val income: String,
    val afterFee: String,
)

/** 金额缺失时的占位文案，与提醒卡窗口外的兜底保持一致。 */
private const val UNKNOWN_AMOUNT = "---"

/** 金额保留到分，与数据卡展示的小数位一致。 */
private const val MONEY_SCALE = 2

/**
 * 结算期（每月 1 日 0:00–10:00，或服务端仍在结算）的提现提醒金额口径。
 *
 * - 抽成前：直接取 `dashboard.summary.unconfirmed_amount`（待结算的上月收入）。
 *   刻意**不**回退数据卡里的「上月收入」：那是账单聚合口径，结算期里往往还是空的或滞后一轮，
 *   而 unconfirmed_amount 才是结算流程本身给出的金额。
 * - 抽成后：平台没有对应接口，用 [CREATOR_SHARE_RATIO] 折算，见 [estimatedAfterFee]。
 * - `unconfirmedAmount` 为 null（dashboard 未返回、或尚未到帧）→ 两处都是 "---"。
 * - `unconfirmedAmount` 为 0 → 照常显示 0.00：0 是有效业务值（上月确实没人发电，
 *   或结算已在 10:00 前完成、钱已转入余额）。
 */
internal fun settlementAmountTexts(unconfirmedAmount: BigDecimal?): WithdrawalAmountTexts {
    if (unconfirmedAmount == null) return WithdrawalAmountTexts(UNKNOWN_AMOUNT, UNKNOWN_AMOUNT)
    return WithdrawalAmountTexts(
        income = CardValue.Money(unconfirmedAmount).displayText(),
        afterFee = CardValue.Money(estimatedAfterFee(unconfirmedAmount)).displayText(),
    )
}

/**
 * 用平台到手比例估算抽成后金额，向下取整到分（3.1490 → 3.14）。
 *
 * 宁可少算一分也不多算。结算期的「到手」与「本月收入（抽成后）」卡共用这一份规则，
 * 平台调整抽成比例时只改 [CREATOR_SHARE_RATIO]。
 */
internal fun estimatedAfterFee(amount: BigDecimal): BigDecimal =
    (amount * CREATOR_SHARE_RATIO).setScale(MONEY_SCALE, RoundingMode.FLOOR)
