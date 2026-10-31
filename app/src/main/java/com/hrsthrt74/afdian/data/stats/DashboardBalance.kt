package com.hrsthrt74.afdian.data.stats

import java.math.BigDecimal

/**
 * `/api/my/dashboard` 里 `balance`（可提现余额）字段的解析结果。
 *
 * 这个字段有两种形态：
 * - 平时：金额文本（如 `"88.00"`）
 * - 每月 1 日 0:00–10:00 结算期间：一段文案（如「结算中，请耐心等待」），此时拿不到余额数字
 *
 * 第二种形态本身就是「当前处于结算期」的服务端信号，因此抽成独立的解析结果，
 * 让调用方既能跳过写余额卡，又能把「正在结算」这个事实带到 UI 层。
 *
 * @param amount 能解析成数字时的余额；非数字（含结算文案）时为 null
 * @param isSettling 文案表示「结算中」时为 true，代表当前处于每月 1 日的结算期
 */
data class DashboardBalance(
    val amount: BigDecimal?,
    /** 注意：字段缺失 / 空串 / 未知文案都不算结算信号，避免把解析失败误判成结算期。 */
    val isSettling: Boolean,
)

/**
 * 结算文案的识别关键字。
 *
 * 服务端原文是「结算中，请耐心等待」，这里只匹配「结算」两个字：
 * 标点、后缀、语气词随时可能被平台改，锁死整句会在改文案当天静默失效。
 */
private const val SETTLING_KEYWORD = "结算"

/**
 * 解析 dashboard 的 `balance` 字段。
 *
 * 容错策略（解析逻辑抽成顶层纯函数，便于单元测试，见 `DashboardBalanceTest`）：
 * - 数字文本（允许首尾空格）→ `(金额, false)`：正常写「可提现（抽成前）」卡
 * - 非数字且含「结算」→ `(null, true)`：不写卡（保持「--」），但透出结算期信号
 * - null / 空串 → `(null, false)`：字段缺失，既不是金额也不是结算信号
 * - 其它非数字文案 → `(null, false)`：未知格式，由调用方记一条告警，不当成结算期
 *
 * @param raw 接口返回的 balance 原始文本（`JsonPrimitive.contentOrNull`）
 */
internal fun parseDashboardBalance(raw: String?): DashboardBalance {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return DashboardBalance(amount = null, isSettling = false)
    // 数字形态优先判定：金额里不会出现「结算」二字，反过来也不会是合法 BigDecimal
    text.toBigDecimalOrNull()?.let { return DashboardBalance(amount = it, isSettling = false) }
    return DashboardBalance(amount = null, isSettling = text.contains(SETTLING_KEYWORD))
}
