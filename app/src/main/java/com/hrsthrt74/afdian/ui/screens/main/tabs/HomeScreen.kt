package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.settings.HomeModule
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.data.toDataCardItem
import com.hrsthrt74.afdian.ui.components.home.CreatorDataEditPage
import com.hrsthrt74.afdian.ui.components.home.HomeCustomizationSheet
import com.hrsthrt74.afdian.ui.components.home.HomeModuleContent
import com.hrsthrt74.afdian.ui.components.home.HomeModuleContentContext
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.HistoryViewModel
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 首页容器只负责页面骨架、数据订阅和模块顺序；具体模块 UI 放在 home 包内。 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenManagement: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSponsoring: () -> Unit,
    historyViewModel: HistoryViewModel,
    onOpenHistory: () -> Unit,
    onOpenHistoryEntry: (com.hrsthrt74.afdian.data.history.HistoryEntry) -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val listState = rememberLazyListState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }

    val sponsoring by viewModel.sponsoring.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val creatorCards by viewModel.creatorCards.collectAsState()
    // 卡片单位是否显示由设置决定，开关变化后立即重算展示项
    val showIncomeUnit by settingsStore.showIncomeUnit.collectAsState(initial = false)
    val showCountUnit by settingsStore.showCountUnit.collectAsState(initial = false)
    val creatorCardItems = remember(creatorCards, showIncomeUnit, showCountUnit) {
        creatorCards.mapValues { (_, card) -> card.toDataCardItem(showIncomeUnit, showCountUnit) }
    }
    val charts by viewModel.creatorCharts.collectAsState()
    val markedCreators by viewModel.markedCreators.collectAsState()
    val historyEntries by historyViewModel.entries.collectAsState()
    val enabledModules by settingsStore.homeEnabledModules.collectAsState(initial = HomeModule.entries.toSet())
    val moduleOrder by settingsStore.homeModuleOrder.collectAsState(initial = HomeModule.entries)
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showRefreshAnimation by remember { mutableStateOf(false) }
    // 创作者数据是否处于整页编辑：为 true 时隐藏其它模块，只渲染可拖拽的编辑页。
    var editingCreatorData by remember { mutableStateOf(false) }
    var refreshStartedAt by remember { mutableStateOf(0L) }
    val refreshAnimation = rememberInfiniteTransition(label = "home_refresh_animation")
    val refreshRotation by refreshAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "home_refresh_rotation",
    )
    // 即使网络请求瞬间完成，刷新动画也至少保持 1 秒，避免图标一闪而过。
    LaunchedEffect(loading) {
        if (loading) {
            refreshStartedAt = System.currentTimeMillis()
            showRefreshAnimation = true
        } else if (showRefreshAnimation) {
            val elapsed = System.currentTimeMillis() - refreshStartedAt
            delay((1000L - elapsed).coerceAtLeast(0L))
            showRefreshAnimation = false
        }
    }

    // 首页是根页面：首次进入时清掉 Compose 可能恢复的旧滚动位置，确保顶部模块可见。
    // 只执行一次，返回创作者详情页后不会因为重组而把用户滚动位置拉回顶部。
    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
        topAppBarState.heightOffset = 0f
        topAppBarState.contentOffset = 0f
    }

    val moduleContext = HomeModuleContentContext(
        sponsoring = sponsoring, loading = loading, creatorCards = creatorCardItems,
        creatorCharts = charts,
        markedCreators = markedCreators,
        settingsStore = settingsStore,
        onOpenCreator = onOpenCreator, onOpenManagement = onOpenManagement, onOpenFavorites = onOpenFavorites,
        onOpenSponsoring = onOpenSponsoring,
        historyEntries = historyEntries,
        onOpenHistory = onOpenHistory,
        onOpenHistoryEntry = onOpenHistoryEntry,
        onEditCreatorData = { editingCreatorData = true },
    )

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = if (editingCreatorData) "编辑创作者数据" else "首页",
                        color = blurBarColor(backdrop), scrollBehavior = scrollBehavior,
                        actions = {
                            if (!editingCreatorData) {
                                IconButton(onClick = viewModel::refresh) {
                                    Icon(
                                        MiuixIcons.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.rotate(if (showRefreshAnimation) refreshRotation else 0f),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            if (editingCreatorData) {
                // 整页编辑：隐藏其它模块，编辑网格作为唯一滚动层，避免“双层滚动”。
                CreatorDataEditPage(
                    context = moduleContext,
                    modifier = Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .padding(bottom = contentBottomPadding()),
                    onExit = { editingCreatorData = false },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).scrollEndHaptic(),
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = contentBottomPadding()),
                ) {
                    moduleOrder.filter { it in enabledModules }.forEach { module ->
                        item(key = module.storageValue) { HomeModuleContent(module, moduleContext) }
                    }
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
                                    .clip(Capsule())
                                    .clickable { showCustomizeSheet = true }
                                    .background(MiuixTheme.colorScheme.surfaceContainer)
                                    .padding(16.dp, 8.dp)
                            ) {
                                Text(
                                    "自定义",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    HomeCustomizationSheet(settingsStore, showCustomizeSheet) { showCustomizeSheet = false }
}
