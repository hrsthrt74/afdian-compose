package com.hrsthrt74.afdian.data.content

import java.time.Duration
import java.time.ZonedDateTime

/**
 * 跨天自动刷新的时间计算。
 *
 * 抽成纯函数便于单元测试（见 `DailyRefreshTest`），也避免 ViewModel 里出现难以验证的
 * `LocalDate.now()` + 手算时长。
 */

/** 最小延迟（毫秒）：时钟抖动或刚好卡在 0 点时，至少等 1 秒再刷新，防止连触两次。 */
private const val MIN_DELAY_MILLIS = 1_000L

/** 额外缓冲（毫秒）：延迟按毫秒取整，落在 23:59:59.999 时还不算「新的一天」。 */
private const val MIDNIGHT_BUFFER_MILLIS = 1_000L

/**
 * 距离下一个「本地自然日 0 点」还有多少毫秒。
 *
 * - 用 [ZonedDateTime] 而不是 `LocalDateTime`：跨夏令时地区「明天 0 点」不是固定 24 小时后，
 *   按带时区的时间点相减才不会提前或延后触发。
 * - 结果加 1 秒缓冲：保证刷新时 `LocalDate.now()` 一定已经是新的一天，
 *   数据卡里的「今日/昨天/本月」等日期口径才会翻篇。
 *
 * @param now 当前带时区时间（由调用方注入，测试可直接构造）
 */
internal fun millisUntilNextLocalDay(now: ZonedDateTime): Long {
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return (Duration.between(now, nextMidnight).toMillis() + MIDNIGHT_BUFFER_MILLIS)
        .coerceAtLeast(MIN_DELAY_MILLIS)
}
