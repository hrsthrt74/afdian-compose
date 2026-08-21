package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

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
            TopAppBar(
                title = "全部订单",
                largeTitle = "全部订单",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "Back"
                        )
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
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding()
                )
            ) {
                item {
                    Text(summary, modifier = Modifier.padding(horizontal = 12.dp))
                }

                item {
                    TabRow(
                        tabs = filters.map { it.second },
                        selectedTabIndex = filters.indexOfFirst { it.first == status }.coerceAtLeast(0),
                        onTabSelected = { index -> vm.load(filters[index].first) },
                    )
                }

                item {
                    when {
                        loading -> LoadingComponent(modifier = Modifier.padding(horizontal = 12.dp))
                        error != null -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(error!!)
                                Button(onClick = { vm.load(status) }) { Text("重试") }
                            }
                        }
                        orders.isEmpty() -> Text("暂无订单", modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }

                if (!loading && error == null) {
                    items(orders, key = { it.id }) { order ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(order.title)
                            Text(order.creator)
                            Text("¥${order.amount} · ${order.status}")
                            Text("订单 ${order.id} · ${order.time}")
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
