package com.hrsthrt74.afdian.ui.screens.main

// Miuix 提供的「滚动到边界触觉反馈」修饰符：列表快速滑动到顶/底边界时震动一下
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.OrderViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun OrderScreen(onBack: () -> Unit, backStack: NavBackStack, providedViewModel: OrderViewModel? = null) {
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
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
            TopAppBar(
                title = "全部订单",
                largeTitle = "全部订单",
                color = blurBarColor(backdrop),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton(onBack = onBack, backStack = backStack)
                }
            )
            }
            }
        }
    ) { paddingValues ->
        // 垂直布局：摘要 + TabRow 固定在列表上方（同 FavoritesScreen 的做法）
        Column(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            // 顶部留出 TopAppBar 的高度（随折叠动画动态变化）
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(paddingValues.calculateTopPadding())
            )

            // 固定头部：订单摘要 + TabRow
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                Text(summary, modifier = Modifier.padding(horizontal = 16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    TabRow(
                        tabs = filters.map { it.second },
                        selectedTabIndex = filters.indexOfFirst { it.first == status }.coerceAtLeast(0),
                        onTabSelected = { index -> vm.load(filters[index].first) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                        .scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        bottom = contentBottomPadding()
                    )
                ) {
                item {
                    when {
                        loading -> LoadingComponent(modifier = Modifier.padding(horizontal = 12.dp))
                        error != null -> {
                            // 加载失败：空状态视图 + 重试按钮
                            EmptyStateView(
                                title = "加载失败",
                                summary = error!!,
                                modifier = Modifier.fillParentMaxSize(),
                                action = {
                                    Button(onClick = { vm.load(status) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("重试")
                                    }
                                },
                            )
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
            // 右侧滚动条
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
            }
        }
    }
}
