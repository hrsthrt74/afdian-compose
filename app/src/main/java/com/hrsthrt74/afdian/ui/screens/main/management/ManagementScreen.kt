package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.content.ManagementSection
import com.hrsthrt74.afdian.data.settings.ManagementLayoutType
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.EmptyStateView
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.data.ManagementAddCardSheet
import com.hrsthrt74.afdian.ui.components.data.ManagementDataLayout
import com.hrsthrt74.afdian.ui.components.data.ManagementEditableDataGrid
import com.hrsthrt74.afdian.ui.components.data.toDataCardItem
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.ManagementViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ManagementScreen(onBack: () -> Unit, backStack: NavBackStack, providedViewModel: ManagementViewModel? = null) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = providedViewModel ?: viewModel<ManagementViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val section by viewModel.section.collectAsState()
    val result by viewModel.result.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val cardModels by viewModel.cards.collectAsState()
    val charts by viewModel.charts.collectAsState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    // 列表滚动状态：双击顶栏回顶 + 滚动条共用
    val listState = rememberLazyListState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val managementLayout by settingsStore.managementLayout.collectAsState(initial = emptyList())
    // 卡片单位是否显示由设置决定，开关变化后立即重算展示项
    val showIncomeUnit by settingsStore.showIncomeUnit.collectAsState(initial = false)
    val showCountUnit by settingsStore.showCountUnit.collectAsState(initial = false)
    val cardItems = remember(cardModels, showIncomeUnit, showCountUnit) {
        cardModels.mapValues { (_, card) -> card.toDataCardItem(showIncomeUnit, showCountUnit) }
    }
    var showCustomization by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editableLayout by remember(managementLayout) { mutableStateOf(managementLayout) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                TopAppBar(
                    title = "管理",
                    largeTitle = "管理",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        BackButton(onBack = onBack, backStack = backStack)
                    },
                    actions = {
                        AnimatedContent(
                            targetState = editing,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "top_bar_actions",
                        ) { isEditing ->
                            if (isEditing) {
                                // 编辑态：添加 + 确认图标
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showCustomization = true
                                    }) {
                                        Icon(imageVector = MiuixIcons.Add, contentDescription = "添加卡片")
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        editing = false
                                    }) {
                                        Icon(imageVector = MiuixIcons.Ok, contentDescription = "完成")
                                    }
                                }
                            } else {
                                // 非编辑态：编辑图标
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    editing = true
                                }) {
                                    Icon(imageVector = MiuixIcons.Edit, contentDescription = "编辑")
                                }
                            }
                        }
                    }
                )
            }
            }
        }
    ) { paddingValues ->
        // 垂直布局：原 TabRow（整体概况 / 发电者管理）已随「赞助者管理」独立成页而移除，
        // 现在只剩整体概况一个分区。
        // 注意：内容必须从屏幕顶（顶栏背后）开始铺，模糊栏才有滚动内容可采样；
        // 顶部可视留白改用 contentPadding / padding 实现，不能用 Spacer 把内容压到顶栏下方。
        Column(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            if (editing && section == ManagementSection.Overview && !loading && error == null) {
                ManagementEditableDataGrid(
                    cards = cardItems,
                    layout = editableLayout,
                    charts = charts,
                    onLayoutChanged = { nextLayout ->
                        editableLayout = nextLayout
                    },
                    onLayoutCommitted = { committedLayout ->
                        // 拖拽过程中只更新内存，松手后一次性持久化最终顺序。
                        scope.launch { settingsStore.setManagementLayout(committedLayout) }
                    },
                    // topInset 走 contentPadding：首行让出顶栏、网格仍穿过顶栏供模糊采样
                    topInset = paddingValues.calculateTopPadding(),
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                )
            } else LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic(),
                // top：内容从屏幕顶布局、首项让出顶栏高度；滚动时列表穿过顶栏供模糊采样
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding(),
                ),
            ) {
                // 加载错误提示
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
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { viewModel.load(section) })
                                    { Text("重试") }
                                },
                            )
                        }
                        result == null -> Text("暂无数据")
                    }
                }

                if (!loading && error == null) {
                    // 分区只剩整体概况（赞助者管理已独立成页面），直接渲染数据卡布局
                    item {
                        ManagementDataLayout(
                            cards = cardItems,
                            layout = managementLayout,
                            charts = charts,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
    ManagementAddCardSheet(
        layout = editableLayout,
        cards = cardItems,
        charts = charts,
        show = showCustomization,
        onAdd = { entry ->
            if (!(entry.type == ManagementLayoutType.CARD && editableLayout.any { it.type == ManagementLayoutType.CARD && it.value == entry.value })) {
                editableLayout = editableLayout + entry
                scope.launch { settingsStore.setManagementLayout(editableLayout) }
            }
        },
        onDismiss = { showCustomization = false },
    )
}
