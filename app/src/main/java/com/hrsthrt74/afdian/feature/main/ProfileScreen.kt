package com.hrsthrt74.afdian.feature.main

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun ProfileScreen(viewModel: MainViewModel, onLogout: () -> Unit) {
    var page by remember { mutableStateOf<String?>(null) }; val account by viewModel.account.collectAsState(); val sponsoring by viewModel.sponsoring.collectAsState(); var message by remember { mutableStateOf<String?>(null) }
    when (page) {
        "favorites" -> { val vm: FavoritesViewModel = viewModel(factory = ViewModelProvider.AndroidViewModelFactory.getInstance(androidx.compose.ui.platform.LocalContext.current.applicationContext as Application)); FavoritesScreen(vm) { page = null }; return }
        "sponsoring" -> { SponsoringScreen(viewModel) { page = null }; return }
        "orders" -> { OrderScreen(onBack = { page = null }); return }
        "management" -> { ManagementScreen(onBack = { page = null }); return }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(account?.displayName ?: "爱发电用户"); Text(account?.login?.email ?: account?.login?.phone ?: "账号信息") }
        item { Text("我的") }; item { ProfileRow("我发电的", "${sponsoring.size} 位创作者") { page = "sponsoring" } }; item { ProfileRow("购物车", "待结算商品") { message = "购物车页面将在下一步接入" } }
        item { Text("我的订单") }; item { ProfileRow("全部订单", "查看赞助和商品订单") { page = "orders" } }; item { ProfileRow("待支付订单", "需要继续处理的订单") { message = "订单页面将在下一步接入" } }
        item { Text("管理") }; item { ProfileRow("管理中心", "概况、收入、订单和赞助者") { page = "management" } }
        item { Text("我的收藏") }; item { ProfileRow("创作者 / 动态 / 众筹", "查看我的收藏") { page = "favorites" } }; item { ProfileRow("设置", "偏好、安全和关于") { message = "设置页面将在下一步接入" } }; item { Button(onClick = onLogout) { Text("退出登录") } }
    }
    if (message != null) androidx.compose.ui.window.Dialog(onDismissRequest = { message = null }) { Column(Modifier.padding(24.dp)) { Text(message!!); Button(onClick = { message = null }) { Text("知道了") } } }
}
@Composable private fun ProfileRow(title: String, subtitle: String, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(title); Text(subtitle) } } }
