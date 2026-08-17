package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.hrsthrt74.afdian.ui.navigation.Route

@Composable
fun ProfileScreen(viewModel: MainViewModel, onLogout: () -> Unit) {
    val backStack = rememberNavBackStack<Route>(Route.Profile)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transition = NavTransitions.MiuixDefault,
    ) {
        entry<Route.Profile> {
            ProfileMainScreen(
                viewModel = viewModel,
                onLogout = onLogout,
                onNavigate = { route -> backStack.add(route) },
            )
        }
        entry<Route.Favorites> {
            val vm: FavoritesViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
                    LocalContext.current.applicationContext as Application
                )
            )
            FavoritesScreen(vm) { backStack.removeLastOrNull() }
        }
        entry<Route.Sponsoring> {
            SponsoringScreen(viewModel) { backStack.removeLastOrNull() }
        }
        entry<Route.Orders> {
            OrderScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<Route.Management> {
            ManagementScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<Route.Settings> {
            SettingsScreen(onBack = { backStack.removeLastOrNull() })
        }
    }
}

@Composable
private fun ProfileMainScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    val account by viewModel.account.collectAsState()
    val sponsoring by viewModel.sponsoring.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "我的",
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp
                ),
            ) {
                item {
                    Text(
                        account?.displayName ?: "爱发电用户",
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title3
                    )
                    Text(
                        account?.login?.email ?: account?.login?.phone ?: "账号信息"
                    )
                }

                // 我的
                item { SmallTitle(text = "我的") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "我发电的",
                            summary = "${sponsoring.size} 位创作者",
                            onClick = { onNavigate(Route.Sponsoring) }
                        )
                    }
                }

                // 我的订单
                item { SmallTitle(text = "我的订单") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "全部订单",
                            summary = "查看赞助和商品订单",
                            onClick = { onNavigate(Route.Orders) }
                        )
                    }
                }

                // 管理
                item { SmallTitle(text = "管理") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "管理中心",
                            summary = "概况、收入、订单和赞助者",
                            onClick = { onNavigate(Route.Management) }
                        )
                    }
                }

                // 我的收藏
                item { SmallTitle(text = "我的收藏") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "创作者 / 动态 / 众筹",
                            summary = "查看我的收藏",
                            onClick = { onNavigate(Route.Favorites) }
                        )
                        ArrowPreference(
                            title = "设置",
                            summary = "偏好、安全和关于",
                            onClick = { onNavigate(Route.Settings) }
                        )
                    }
                }

                // 退出登录
                item {
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("退出登录")
                    }
                }
            }
        }
    }
}
