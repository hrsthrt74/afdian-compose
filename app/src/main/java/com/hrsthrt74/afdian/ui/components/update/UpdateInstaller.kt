package com.hrsthrt74.afdian.ui.components.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import java.io.File

/**
 * 把下载好的安装包交给系统安装器。
 *
 * 三条铁律：
 * 1. 安装前必须校验包名与 versionCode —— 装错 edition 会被 `EditionGuard` 在启动时拦掉
 *    （看起来就是「一装就废」），降级安装则会被系统直接拒绝；
 * 2. APK 通过 FileProvider 暴露，`file_paths.xml` 只白名单 `updates/` 一个子目录；
 * 3. 未授予「安装未知应用」时不硬来，先带去授权页，回来再装。
 */
object UpdateInstaller {

    /** 是否已获得「安装未知应用」权限。minSdk 29，`canRequestPackageInstalls` 一定存在。 */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * 打开本应用的「安装未知应用」授权页。
     *
     * 少数 ROM 没有这个页面（抛 ActivityNotFoundException），失败只记日志：
     * 用户仍可以自己去系统设置里找，比直接崩掉好。
     */
    fun openPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { AppLog.w("UpdateInstaller", "打开「安装未知应用」设置失败", it) }
    }

    /**
     * 安装前自检。
     *
     * @return null 表示可以安装；否则是直接给用户看的错误文案
     */
    @Suppress("DEPRECATION") // getPackageArchiveInfo(String, Int) 在 API 33 才有新重载，旧签名仍可用
    fun verify(context: Context, apk: File): String? {
        if (!apk.exists() || apk.length() == 0L) return "安装包不存在，请重新下载"
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return "安装包解析失败，请重新下载"
        // 包名必须与当前安装包完全一致：standard / legacy 是两个独立的包，互相装不上
        if (archive.packageName != context.packageName) {
            return "这个安装包与当前版本不匹配，请到 Release 页面下载对应版本"
        }
        if (archive.longVersionCode < BuildConfig.VERSION_CODE.toLong()) {
            return "安装包版本低于当前版本，已阻止降级安装"
        }
        return null
    }

    /**
     * 拉起系统安装器。
     *
     * @return 是否成功发起（设备没有安装器时会返回 false）
     */
    fun install(context: Context, apk: File): Boolean {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            // 根组合 / Application context 里没有 Activity 栈，必须新开任务
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }
            .onFailure { AppLog.w("UpdateInstaller", "拉起系统安装器失败", it) }
            .isSuccess
    }
}
