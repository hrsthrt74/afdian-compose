package com.hrsthrt74.afdian.ui.components.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hrsthrt74.afdian.AppLog

/**
 * 用系统浏览器打开更新链接（安装包直链或 Releases 页面）。
 *
 * 为什么不用 App 内的 Custom Tabs 导航器（`LocalAppLinkNavigator`）：
 * 那个组合局部只在 MainScreen 里提供，冷启动时的更新弹窗挂在根组合上，取不到它；
 * 而且下载安装包本来就该交给浏览器 / 下载管理器，Custom Tabs 的品牌化地址栏反而碍事。
 *
 * @param context 上下文
 * @param url 要打开的地址
 */
fun openUpdateUrl(context: Context, url: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "没有可用的下载链接", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        // 根组合里没有 Activity 上下文，必须自己带 NEW_TASK
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onSuccess { AppLog.d("Update", "打开更新链接：$url") }
        .onFailure { error ->
            // 设备上没有任何能处理 http(s) 的应用时才会走到这
            AppLog.w("Update", "打开更新链接失败：$url", error)
            Toast.makeText(context, "没有找到可以打开链接的应用", Toast.LENGTH_SHORT).show()
        }
}
