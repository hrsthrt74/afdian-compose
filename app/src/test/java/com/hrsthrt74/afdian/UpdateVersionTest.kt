package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.update.GithubAsset
import com.hrsthrt74.afdian.data.update.ReleaseVersion
import com.hrsthrt74.afdian.data.update.UpdatePolicy
import com.hrsthrt74.afdian.data.update.isNewerVersion
import com.hrsthrt74.afdian.data.update.parseReleaseVersion
import com.hrsthrt74.afdian.data.update.pickApkAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检查更新的纯逻辑测试：版本解析与比较、安装包挑选、自动检查节流。
 *
 * 这三块决定了「会不会把用户骗去装一个更旧的包」「会不会每次冷启动都打一次 GitHub API」，
 * 都是纯函数，适合在 JVM 上锁住边界。
 */
class UpdateVersionTest {

    // ── 版本解析 ──

    @Test
    fun parseAcceptsCommonTagShapes() {
        // GitHub tag 常见的 v 前缀
        assertEquals(ReleaseVersion(1, 2, 0), parseReleaseVersion("v1.2.0"))
        assertEquals(ReleaseVersion(1, 2, 0), parseReleaseVersion("V1.2.0"))
        // 裸版本号
        assertEquals(ReleaseVersion(1, 2, 0), parseReleaseVersion("1.2.0"))
        // 缺位按 0 补齐
        assertEquals(ReleaseVersion(1, 2, 0), parseReleaseVersion("1.2"))
        assertEquals(ReleaseVersion(1, 0, 0), parseReleaseVersion("1"))
        // 预发布后缀与构建元数据都丢弃
        assertEquals(ReleaseVersion(1, 2, 0), parseReleaseVersion("v1.2.0-beta1"))
        assertEquals(ReleaseVersion(1, 2, 0), parseReleaseVersion("1.2.0+build.7"))
    }

    @Test
    fun parseRejectsUnrecognisedStrings() {
        assertNull(parseReleaseVersion(""))
        assertNull(parseReleaseVersion("latest"))
        assertNull(parseReleaseVersion("v1.2.3.4"))
    }

    // ── 版本比较 ──

    @Test
    fun newerVersionIsDetected() {
        assertTrue(isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(isNewerVersion("v1.1.0", "1.0.9"))
        assertTrue(isNewerVersion("v2.0.0", "1.99.99"))
    }

    @Test
    fun sameOrOlderVersionIsNotNewer() {
        assertFalse(isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("v0.9.9", "1.0.0"))
        assertFalse(isNewerVersion("v1.0.0", "1.0.1"))
    }

    @Test
    fun unparsableVersionIsNeverNewer() {
        // 看不懂的 tag 不该拿来提示用户升级
        assertFalse(isNewerVersion("nightly", "1.0.0"))
        assertFalse(isNewerVersion("v1.0.1", "unknown"))
    }

    // ── 安装包挑选 ──

    @Test
    fun pickApkMatchesCurrentVariant() {
        val assets = listOf(
            asset("Afdian-1.0.0-standard-production-release.apk"),
            asset("Afdian-1.0.0-legacy-production-release.apk"),
            asset("Afdian-1.0.0-standard-internal-release.apk"),
        )

        // 正式完整版：standard + production
        val standard = pickApkAsset(assets, legacyEdition = false, internalBuild = false)
        assertEquals("Afdian-1.0.0-standard-production-release.apk", standard?.name)

        // 低配兼容版：legacy
        val legacy = pickApkAsset(assets, legacyEdition = true, internalBuild = false)
        assertEquals("Afdian-1.0.0-legacy-production-release.apk", legacy?.name)

        // 内测完整版：standard + internal
        val internal = pickApkAsset(assets, legacyEdition = false, internalBuild = true)
        assertEquals("Afdian-1.0.0-standard-internal-release.apk", internal?.name)
    }

    @Test
    fun pickApkPrefersReleaseOverDebug() {
        val assets = listOf(
            asset("Afdian-1.0.0-standard-production-debug.apk"),
            asset("Afdian-1.0.0-standard-production-release.apk"),
        )
        val picked = pickApkAsset(assets, legacyEdition = false, internalBuild = false)
        assertEquals("Afdian-1.0.0-standard-production-release.apk", picked?.name)
    }

    @Test
    fun pickApkReturnsNullWhenNoApk() {
        assertNull(pickApkAsset(emptyList(), legacyEdition = false, internalBuild = false))
        // 只有非 APK 附件时也没有可挑的
        assertNull(pickApkAsset(listOf(asset("source.zip")), legacyEdition = false, internalBuild = false))
    }

    // ── 自动检查节流 ──

    @Test
    fun internalBuildNeverAutoChecks() {
        val now = 1_000_000_000L
        assertFalse(UpdatePolicy.shouldAutoCheck(internalBuild = true, lastCheckAtMs = 0L, nowMs = now))
    }

    @Test
    fun firstCheckAlwaysRuns() {
        val now = 1_000_000_000L
        assertTrue(UpdatePolicy.shouldAutoCheck(internalBuild = false, lastCheckAtMs = 0L, nowMs = now))
    }

    @Test
    fun autoCheckIsThrottledWithinOneDay() {
        val now = 100L * 24L * 60L * 60L * 1000L
        val oneHourAgo = now - 60L * 60L * 1000L
        val oneDayAgo = now - UpdatePolicy.AUTO_CHECK_INTERVAL_MS
        val twoDaysAgo = now - 2 * UpdatePolicy.AUTO_CHECK_INTERVAL_MS

        // 一小时内查过：不查
        assertFalse(UpdatePolicy.shouldAutoCheck(false, oneHourAgo, now))
        // 刚好满 24 小时：查
        assertTrue(UpdatePolicy.shouldAutoCheck(false, oneDayAgo, now))
        // 超过 24 小时：查
        assertTrue(UpdatePolicy.shouldAutoCheck(false, twoDaysAgo, now))
    }

    /** 构造一个只有名字的附件对象（其余字段用不上）。 */
    private fun asset(name: String): GithubAsset = GithubAsset(
        name = name,
        downloadUrl = "https://example.com/$name",
        size = 1L,
    )
}
