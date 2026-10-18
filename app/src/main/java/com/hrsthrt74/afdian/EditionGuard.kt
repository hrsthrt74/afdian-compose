package com.hrsthrt74.afdian

import android.os.Build

/**
 * 版本适配拦截：legacy（低配兼容版）被安装在系统版本足够高的设备上时，属于「装错包」。
 *
 * 两个 edition 的定位：
 * - standard 完整版：minSdk 34（见 BuildConfig.STANDARD_MIN_SDK），有模糊、有预测性返回；
 * - legacy 兼容版：minSdk 29，关掉模糊与预测性返回，只为 Android 10~13 的旧设备兜底。
 *
 * 因此 legacy 的有效区间是 API 29~33。设备一旦达到 API 34，standard 完全跑得动，
 * 继续用 legacy 只会白白丢掉效果，用户体验反而更差 —— 这时在启动阶段直接阻断，
 * 引导用户换装完整版（提示页见 `ui/screens/auth/WrongEditionScreen.kt`）。
 *
 * 判定口径刻意做成纯函数，不读任何设置、不区分 buildType、不做持久化：
 * 每次 `MainActivity.onCreate` 现算一次，进程重建/任务恢复都会重新判定，不存在绕过路径。
 */
object EditionGuard {

    /**
     * standard 完整版的最低系统要求（API 34 / Android 14）。
     *
     * 取自 Gradle 注入的 [BuildConfig.STANDARD_MIN_SDK]，与 standard flavor 的 minSdk 同源，
     * 不要在别处再写死数字。
     */
    val STANDARD_MIN_SDK: Int = BuildConfig.STANDARD_MIN_SDK

    /**
     * 纯函数判定：当前安装包在此系统版本上是否属于「装错版本」。
     *
     * 把 `sdkInt` 作为入参而不是直接读 [Build.VERSION.SDK_INT]，是为了能在 JVM 单元测试里
     * 覆盖各个边界（见 `EditionGuardTest`），不必依赖真机系统版本。
     *
     * @param legacyEdition 当前安装包是否为 legacy 兼容版
     * @param sdkInt 设备系统 API 级别
     */
    fun isWrongEdition(legacyEdition: Boolean, sdkInt: Int): Boolean =
        legacyEdition && sdkInt >= STANDARD_MIN_SDK

    /** 当前安装包在当前设备上是否属于「装错版本」，命中即阻断启动。 */
    fun isBlocked(): Boolean =
        isWrongEdition(BuildConfig.LEGACY_EDITION, Build.VERSION.SDK_INT)
}
