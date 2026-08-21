package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.util.rememberMainPagerState
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import com.hrsthrt74.afdian.ui.viewmodel.MessagesViewModel

/**
 * 底部导航 Tab 定义：标题 + 图标。
 */
private data class MainTab(val title: String, val icon: ImageVector)

private val mainTabs = listOf(
    MainTab("首页", MiuixIcons.Home),
    MainTab("发现", MiuixIcons.Search),
    MainTab("消息", MiuixIcons.Community),
    MainTab("我的", MiuixIcons.Contacts),
)

/**
 * 主界面壳层：全局 [NavDisplay] 管理创作者主页等覆盖路由，
 * 根路由 [Route.MainTabs] 承载底部 [NavigationBar] + [HorizontalPager] 横滑切换。
 */
@Composable
fun MainScreen(onLogout: () -> Unit, viewModel: MainViewModel = viewModel()) {
    val messagesViewModel: MessagesViewModel = viewModel()
    val backStack = rememberNavBackStack<Route>(Route.MainTabs)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transition = NavTransitions.MiuixDefault,
    ) {
        entry<Route.MainTabs> {
            MainTabsContent(
                onLogout = onLogout,
                viewModel = viewModel,
                messagesViewModel = messagesViewModel,
                onOpenCreator = { user -> backStack.add(Route.CreatorDetail(user)) },
                onOpenConversation = { userId, userName, avatarUrl -> backStack.add(Route.MessageConversation(userId, userName, avatarUrl)) },
            )
        }
        entry<Route.CreatorDetail>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
            CreatorDetailScreen(user = route.user, onBack = { backStack.removeLastOrNull() })
        }
        entry<Route.MessageConversation>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
            MessageConversationScreen(
                userId = route.userId,
                userName = route.userName,
                avatarUrl = route.avatarUrl,
                viewModel = messagesViewModel,
                onBack = { backStack.removeLastOrNull() }
            )
        }
    }
}

@Composable
private fun MainTabsContent(
    onLogout: () -> Unit,
    viewModel: MainViewModel,
    messagesViewModel: MessagesViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenConversation: (String, String, String?) -> Unit,
) {
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
    val hasUnreadMessages by messagesViewModel.hasUnreadMessages.collectAsState()
    val unreadCount by messagesViewModel.unreadCount.collectAsState()

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
                        label = tab.title,
                        badge = if (index == 2 && hasUnreadMessages) {
                            { Badge( containerColor = MiuixTheme.colorScheme.primary ) { Text("$unreadCount") } }
                        } else {
                            null
                        }
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
                    0 -> HomeScreen(viewModel, onOpenCreator)
                    1 -> DiscoverScreen(viewModel, onOpenCreator)
                    2 -> MessagesScreen(messagesViewModel) { route ->
                        when (route) {
                            is Route.MessageConversation -> onOpenConversation(route.userId, route.userName, route.avatarUrl)
                            else -> {}
                        }
                    }
                    else -> ProfileScreen(viewModel, onLogout, onOpenCreator)
                }
            }
        }
    }
}