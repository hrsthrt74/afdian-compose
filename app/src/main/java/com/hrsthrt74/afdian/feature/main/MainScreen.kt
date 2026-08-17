package com.hrsthrt74.afdian.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.navigation.Route
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

@Composable
fun MainScreen(onLogout: () -> Unit, viewModel: MainViewModel = viewModel()) {
    val backStack = rememberNavBackStack<Route>(Route.Home)
    val tabs = listOf("首页", "发现", "动态", "消息", "我的")
    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Bolt, Icons.Default.Email, Icons.Default.Person)
    val selected = when (backStack.last()) { Route.Home -> 0; Route.Discover -> 1; Route.Activity -> 2; Route.Messages -> 3; Route.Profile -> 4; else -> 0 }
    Scaffold(bottomBar = { NavigationBar { tabs.forEachIndexed { index, title -> NavigationBarItem(selected == index, { backStack[backStack.lastIndex] = tabs[index].route() }, icons[index], title) } } }) { padding ->
        NavDisplay(backStack = backStack, modifier = Modifier.padding(padding)) {
            entry<Route.Home> { MainTab(0, onLogout, viewModel) }; entry<Route.Discover> { MainTab(1, onLogout, viewModel) }; entry<Route.Activity> { MainTab(2, onLogout, viewModel) }; entry<Route.Messages> { MainTab(3, onLogout, viewModel) }; entry<Route.Profile> { MainTab(4, onLogout, viewModel) }
        }
    }
}
private fun String.route(): Route = when (this) { "首页" -> Route.Home; "发现" -> Route.Discover; "动态" -> Route.Activity; "消息" -> Route.Messages; else -> Route.Profile }
@Composable private fun MainTab(selected: Int, onLogout: () -> Unit, viewModel: MainViewModel) { Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(listOf("首页", "发现", "动态", "消息", "我的")[selected]); when (selected) { 0 -> HomeScreen(viewModel); 1 -> Text("搜索创作者和内容"); 2 -> Text("发电动态将在这里显示"); 3 -> Text("消息会话将在这里显示"); else -> ProfileScreen(viewModel, onLogout) } } }
