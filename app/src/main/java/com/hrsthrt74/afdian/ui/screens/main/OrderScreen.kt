package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState

@Composable
fun OrderScreen(onBack: () -> Unit, providedViewModel: OrderViewModel? = null) {
    val app = LocalContext.current.applicationContext as Application
    val vm = providedViewModel ?: viewModel<OrderViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    )
    val status by vm.status.collectAsState()
    val orders by vm.orders.collectAsState()
    val summary by vm.summary.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val filters = listOf("" to "全部", "1" to "待支付", "2" to "已支付", "8" to "待发货", "9" to "已收货")

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "全部订单",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text("返回")
                    }
                }
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(summary)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filters.forEach { (value, title) ->
                            Button(
                                onClick = { vm.load(value) },
                                enabled = !loading
                            ) {
                                Text(if (status == value) "✓ $title" else title)
                            }
                        }
                    }
                }

                item {
                    when {
                        loading -> Text("正在加载订单…")
                        error != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(error!!)
                                Button(onClick = { vm.load(status) }) { Text("重试") }
                            }
                        }
                        orders.isEmpty() -> Text("暂无订单")
                    }
                }

                if (!loading && error == null) {
                    items(orders, key = { it.id }) { order ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(order.title)
                            Text(order.creator)
                            Text("¥${order.amount} · ${order.status}")
                            Text("订单 ${order.id} · ${order.time}")
                        }
                    }
                }
            }
        }
    }
}
