package com.hrsthrt74.afdian.ui.screens.main.management

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.data.ManagementAddCardSheet
import com.hrsthrt74.afdian.ui.components.data.ManagementDataLayout
import com.hrsthrt74.afdian.ui.components.data.ManagementEditableDataGrid
import com.hrsthrt74.afdian.ui.components.data.toDataCardItem
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
    // 数据
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
    val scope = rememberCoroutineScope()

    // 页面
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val listState = rememberLazyListState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 卡片
    val settingsStore = remember { SettingsStore(context) }
    val managementLayout by settingsStore.managementLayout.collectAsState(initial = emptyList())
    // 卡片单位
    val showIncomeUnit by settingsStore.showIncomeUnit.collectAsState(initial = false)
    val showCountUnit by settingsStore.showCountUnit.collectAsState(initial = false)
    val cardItems = remember(cardModels, showIncomeUnit, showCountUnit) {
        cardModels.mapValues { (_, card) -> card.toDataCardItem(showIncomeUnit, showCountUnit) }
    }
    var showCustomization by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editableLayout by remember(managementLayout) { mutableStateOf(managementLayout) }

    Scaffold(
        topBar = {
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                TopAppBar(
                    title = "管理",
                    largeTitle = "管理",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                    actions = { // 顶栏操作按钮
                        AnimatedContent(
                            targetState = editing,
//                            transitionSpec = { fadeIn() togetherWith fadeOut() },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
        ) {
            AnimatedContent(
                targetState = editing && section == ManagementSection.Overview && !loading && error == null,
                modifier = Modifier.weight(1f),
                label = "management_layout_switch",
            ) { isEditableGrid ->
            if (isEditableGrid) {
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
                    topInset = paddingValues.calculateTopPadding(),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                )
            } else LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic(),
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
                    // 数据卡布局
                    item {
                        ManagementDataLayout(
                            cards = cardItems,
                            layout = managementLayout,
                            charts = charts,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            }
        }
    }
    ManagementAddCardSheet( // 添加卡片 Sheet
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
