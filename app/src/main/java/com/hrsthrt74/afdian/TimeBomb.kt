package com.hrsthrt74.afdian

/**
 * 内测/调试版本的时间限制判断。
 *
 * 该类只依赖传入的时间，因此可以在 JVM 单元测试中覆盖截止边界，避免测试
 * 直接依赖设备时钟。系统时间回拨不在本次需求的防护范围内。
 */
object TimeBomb {
    /** 内测包允许使用的时长：120 小时（5 天）。 */
    const val VALIDITY_MS: Long = 120L * 60L * 60L * 1000L

    /** 返回构建时间加 120 小时后的截止时间。 */
    fun expiresAt(buildTimestampMs: Long): Long = buildTimestampMs + VALIDITY_MS

    /** 达到截止瞬间即过期。 */
    fun isExpired(buildTimestampMs: Long, nowMs: Long): Boolean =
        nowMs >= expiresAt(buildTimestampMs)
}
