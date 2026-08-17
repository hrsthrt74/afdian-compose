package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.ui.util.rememberMainPagerState
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底部导航 Tab 定义：标题 + 图标。
 */
private data class MainTab(val title: String, val icon: ImageVector)

private val mainTabs = listOf(
    MainTab("首页", MiuixIcons.Home),
    MainTab("发现", MiuixIcons.Search),
    MainTab("动态", MiuixIcons.Album),
    MainTab("消息", MiuixIcons.Community),
    MainTab("我的", MiuixIcons.Contacts),
)

/**
 * 主界面壳层：底部 [NavigationBar] + [HorizontalPager] 横滑切换（参考 exTile 布局模式）。
 * 各页面自己再包一层 Scaffold + TopAppBar，通过 beyondViewportPageCount 保留全部页面状态。
 */
@Composable
fun MainScreen(onLogout: () -> Unit, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val startupPage = remember {
        context.getSharedPreferences("settings", 0)
            .getInt("startup_page", 0)
            .coerceIn(0, mainTabs.lastIndex)
    }
    val pagerState = rememberPagerState(
        initialPage = startupPage,
        pageCount = { mainTabs.size }
    )
    val mainPagerState = rememberMainPagerState(pagerState)

    // 横滑翻页后把当前页回写到导航栏高亮
    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val haptic = LocalHapticFeedback.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                mainTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = mainPagerState.selectedPage == index,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mainPagerState.animateToPage(index)
                        },
                        icon = tab.icon,
                        label = tab.title
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = mainTabs.size,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) { page ->
            // 页面容器：全宽背景铺满，与各页面 Scaffold 背景同色
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                when (page) {
                    0 -> HomeScreen(viewModel)
                    1 -> DiscoverScreen()
                    2 -> ActivityScreen()
                    3 -> MessagesScreen()
                    else -> ProfileScreen(viewModel, onLogout)
                }
            }
        }
    }
}
