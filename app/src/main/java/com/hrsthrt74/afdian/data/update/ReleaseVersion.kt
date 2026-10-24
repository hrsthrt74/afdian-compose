package com.hrsthrt74.afdian.data.update

/**
 * 语义化版本号。
 *
 * 只保留 major / minor / patch 三段：更新检查只需要「谁更新」的判断，
 * 预发布后缀（-beta1）之类的比较规则差异大，解析时直接丢弃即可。
 */
data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<ReleaseVersion> {

    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(
        this,
        other,
        ReleaseVersion::major,
        ReleaseVersion::minor,
        ReleaseVersion::patch,
    )

    override fun toString(): String = "$major.$minor.$patch"
}

/**
 * 解析版本号字符串。
 *
 * 兼容这些写法：
 * - `v1.2.0` / `V1.2.0`（GitHub tag 的常见形态）
 * - `1.2.0`（裸版本号）
 * - `1.2`（缺 patch，按 0 处理）
 * - `1`（只给 major，其余按 0）
 * - `1.2.0-beta1` / `1.2.0+build`（丢掉后缀）
 *
 * @param raw 原始字符串（tag 名或 versionName）
 * @return 解析成功返回版本号；无法解析返回 null（调用方应视为「不认识这个版本」）
 */
fun parseReleaseVersion(raw: String): ReleaseVersion? {
    val trimmed = raw.trim().removePrefix("v").removePrefix("V")
    if (trimmed.isEmpty()) return null
    // 预发布后缀与构建元数据都不参与比较
    val core = trimmed.substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    // 超过三段说明不是语义化版本（可能是别的编号体系），不猜
    if (parts.isEmpty() || parts.size > 3) return null
    val numbers = parts.map { it.trim().toIntOrNull() ?: return null }
    return ReleaseVersion(
        major = numbers[0],
        minor = numbers.getOrElse(1) { 0 },
        patch = numbers.getOrElse(2) { 0 },
    )
}

/**
 * 候选版本是否比当前版本新。
 *
 * 解析不出来时返回 false（视为「不比现在新」）：
 * 拿一个看不懂的 tag 去提示用户升级，只会制造困惑。
 */
fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateVersion = parseReleaseVersion(candidate) ?: return false
    val currentVersion = parseReleaseVersion(current) ?: return false
    return candidateVersion > currentVersion
}

/**
 * 从 Release 的附件里挑出「当前变体该装的那个包」。
 *
 * 命名约定（由发布流程保证，见 README 的「版本号与发布流程」一节）：
 * `Afdian-<版本>-<edition>-<buildType>.apk`
 * 例如 `Afdian-1.0.0-standard-release.apk`。
 *
 * 挑选规则：
 * 1. 只看 `.apk` 结尾的附件，并排除历史上残留的 `internal` 内测附件；
 * 2. edition 必须与当前安装包一致（legacy 包只装 legacy，反之亦然）——
 *    装错 edition 会被 `EditionGuard` 在启动时直接拦掉；
 * 3. 同名候选里 release 优先于 debug。
 *
 * 挑不到时返回 null，调用方降级为「去 Releases 页面自己挑」。
 */
fun pickApkAsset(
    assets: List<GithubAsset>,
    legacyEdition: Boolean,
): GithubAsset? {
    // 排除已废弃的 internal 内测附件：历史 Release 里若还留着，
    // 会被正版包当成可用更新推给用户
    val apks = assets.filter { asset ->
        asset.name.endsWith(".apk", ignoreCase = true) &&
            !asset.name.contains("internal", ignoreCase = true)
    }
    if (apks.isEmpty()) return null

    // edition 必须匹配：文件名里带 legacy 的只给 legacy 包用
    val editionMatched = apks.filter { asset ->
        asset.name.contains("legacy", ignoreCase = true) == legacyEdition
    }
    val pool = editionMatched.ifEmpty { apks }

    // release 优先于 debug
    return pool.firstOrNull { it.name.contains("release", ignoreCase = true) }
        ?: pool.firstOrNull()
}

/**
 * 自动检查更新的策略。
 *
 * 单独抽成纯函数是为了能在 JVM 单元测试里覆盖时间边界，
 * 不必依赖设备时钟。
 */
object UpdatePolicy {

    /** 自动检查的最小间隔：24 小时。防止每次冷启动都打一次 GitHub API。 */
    const val AUTO_CHECK_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L

    /**
     * 现在是否值得做一次自动检查。
     *
     * @param lastCheckAtMs 上次检查时间戳（0 表示从未检查过）
     * @param nowMs 当前时间戳
     */
    fun shouldAutoCheck(
        lastCheckAtMs: Long,
        nowMs: Long,
    ): Boolean {
        // 从未检查过：值得查一次
        if (lastCheckAtMs <= 0L) return true
        // 24 小时内查过：不查
        return nowMs - lastCheckAtMs >= AUTO_CHECK_INTERVAL_MS
    }
}
