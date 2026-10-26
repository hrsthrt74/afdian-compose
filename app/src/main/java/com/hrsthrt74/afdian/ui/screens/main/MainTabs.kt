package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.hrsthrt74.afdian.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Search

/**
 * 底部导航 Tab 定义：标题 + 图标。
 * 图标用 @Composable lambda 惰性提供，支持需要 Composable 上下文的
 * [ImageVector.vectorResource]（如自定义 drawable 图标）。
 */
internal data class MainTab(val title: String, val icon: @Composable () -> ImageVector)

/**
 * 主界面底栏的四个 Tab，顺序即底栏从左到右的顺序。
 *
 * 从 `MainScreen.kt` 提到这里是为了让「设置 → 外观 → 底栏样式」的预览卡
 * 能画出**真正的那条 navbar**（同一份图标与文案），而不是另抄一套假图标 ——
 * 抄一份必然随着以后改 tab 而悄悄走样。
 */
internal val mainTabs = listOf(
    // 首页：自定义描边小房子（drawable/home_outlined，抄自 exTile 项目）
    MainTab("首页") { ImageVector.vectorResource(R.drawable.home_outlined) },
    MainTab("发现") { MiuixIcons.Search },
    MainTab("消息") { MiuixIcons.Community },
    MainTab("我的") { MiuixIcons.Contacts },
)
