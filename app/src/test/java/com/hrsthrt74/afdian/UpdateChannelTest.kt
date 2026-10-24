package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.update.UpdateChannel
import com.hrsthrt74.afdian.data.update.isActionsRunNewer
import com.hrsthrt74.afdian.data.update.parseIsoInstantMs
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新通道与 Actions 通道判定规则的纯逻辑测试。
 *
 * 这块决定「装了 Actions 包的用户会不会被反复提示同一条构建」「正式版用户切到
 * Actions 通道后能不能看到新构建」，都是纯函数，在 JVM 上锁住边界最省事。
 */
class UpdateChannelTest {

    // ── 通道存储值 ──

    @Test
    fun channelRestoresFromStorage() {
        assertEquals(UpdateChannel.Stable, UpdateChannel.fromStorage(null))
        assertEquals(UpdateChannel.Stable, UpdateChannel.fromStorage(""))
        assertEquals(UpdateChannel.Stable, UpdateChannel.fromStorage("stable"))
        assertEquals(UpdateChannel.Actions, UpdateChannel.fromStorage("actions"))
        // 不认识的值（旧版本 / 手改配置）一律回落到正式版
        assertEquals(UpdateChannel.Stable, UpdateChannel.fromStorage("nightly"))
    }

    // ── ISO 时间解析 ──

    @Test
    fun isoInstantIsParsed() {
        assertEquals(
            Instant.parse("2026-10-24T07:12:33Z").toEpochMilli(),
            parseIsoInstantMs("2026-10-24T07:12:33Z"),
        )
    }

    @Test
    fun invalidIsoInstantFallsBackToZero() {
        // 解析失败返回 0：调用方据此退化成「不提示」，不会把 1970 当成构建时间
        assertEquals(0L, parseIsoInstantMs(""))
        assertEquals(0L, parseIsoInstantMs("yesterday"))
    }

    // ── Actions 构建是否更新 ──

    @Test
    fun sameCommitIsNeverNewer() {
        // sha 一致 = 装的就是这条构建，编号再大也不提示
        assertFalse(
            isActionsRunNewer(
                runNumber = 999,
                headSha = "abc1234",
                createdAtMs = 10_000L,
                localBuildNumber = 100,
                localSha = "abc1234",
                localBuildTimeMs = 1_000L,
            ),
        )
    }

    @Test
    fun actionsBuildComparesRunNumber() {
        // 本地是 Actions 包：严格比编号，不降级
        assertTrue(runIsNewer(runNumber = 101, localBuildNumber = 100))
        assertFalse(runIsNewer(runNumber = 100, localBuildNumber = 100))
        assertFalse(runIsNewer(runNumber = 99, localBuildNumber = 100))
    }

    @Test
    fun stableBuildComparesBuildTime() {
        // 本地是正式版（BUILD_NUMBER=0）：按运行时间与包构建时间比较
        val buildTime = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        assertTrue(
            isActionsRunNewer(
                runNumber = 5,
                headSha = "def5678",
                createdAtMs = buildTime + 60_000L,
                localBuildNumber = 0,
                localSha = "",
                localBuildTimeMs = buildTime,
            ),
        )
        assertFalse(
            isActionsRunNewer(
                runNumber = 5,
                headSha = "def5678",
                createdAtMs = buildTime - 60_000L,
                localBuildNumber = 0,
                localSha = "",
                localBuildTimeMs = buildTime,
            ),
        )
    }

    @Test
    fun invalidRunNumberIsNeverNewer() {
        assertFalse(runIsNewer(runNumber = 0, localBuildNumber = 0))
        assertFalse(runIsNewer(runNumber = -1, localBuildNumber = 0))
    }

    private fun runIsNewer(runNumber: Int, localBuildNumber: Int): Boolean =
        isActionsRunNewer(
            runNumber = runNumber,
            headSha = "head123",
            createdAtMs = 10_000L,
            localBuildNumber = localBuildNumber,
            localSha = "local99",
            localBuildTimeMs = 1_000L,
        )
}
