package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.ui.components.AppDialog
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.DataCard
import com.hrsthrt74.afdian.ui.components.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.rememberDialogState
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.theme.ThemeColorTransition
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 「启动」页 下拉菜单选项
private val startupPageNames = listOf(
    DropdownItem(
        icon = { Icon(MiuixIcons.Home, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "首页"
    ),
    DropdownItem(
        icon = { Icon(MiuixIcons.Search, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "发现"
    ),
    DropdownItem(
        icon = { Icon(MiuixIcons.Community, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "消息"
    ),
    DropdownItem(
        icon = { Icon(MiuixIcons.Contacts, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "我的"
    ),
)
// 配色模式选项，索引与 SettingsStore 的 0/1/2 约定一致
private val colorModeNames = listOf("跟随系统", "浅色", "深色")

/**
 * 设置页
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    // 登出回调：由外层（MainScreen）传入，点击底部「退出登录」时触发
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    /**
     * 内容整体包在 [ThemeColorTransition] 里：本页是唯一能改配色的地方，切「配色模式」
     * 或「使用爱发电主题色」时整页颜色渐变过去，而不是瞬间换色。模糊取色也在包裹范围内，
     * 顶栏才能跟着一起渐变。其他页面保持原来的瞬间生效（切设置时它们看不见，没必要每帧重算）。
     */
    ThemeColorTransition {
        SettingsScreenContent(
            onBack = onBack,
            backStack = backStack,
            onLogout = onLogout,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun SettingsScreenContent(
    onBack: () -> Unit,
    backStack: NavBackStack,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel,
) {
    // 设置
    val startupPage by viewModel.startupPage.collectAsState()
    val useAfdianColor by viewModel.useAfdianColor.collectAsState()
    val colorMode by viewModel.colorMode.collectAsState()
    val advancedTransition by viewModel.advancedTransition.collectAsState()
    val gestureBack by viewModel.gestureBack.collectAsState()
    val predictiveBack by viewModel.predictiveBack.collectAsState()
    val debugLogging by viewModel.debugLogging.collectAsState()
    val historyEnabled by viewModel.historyEnabled.collectAsState()
    val showIncomeUnit by viewModel.showIncomeUnit.collectAsState()
    val showCountUnit by viewModel.showCountUnit.collectAsState()
    val themeSettings = LocalThemeSettings.current

    // 页面
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val haptic = LocalHapticFeedback.current

    // 「重置卡片布局」确认弹窗状态
    val resetManagementDialogState = rememberDialogState()
    val resetHomeCreatorDialogState = rememberDialogState()


    Scaffold(
        topBar = {
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "设置",
                        largeTitle = "设置",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack = onBack, backStack = backStack) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = (contentBottomPadding() + 48.dp)
                )
            ) {
                // 设置项目
                item { SmallTitle(text = "启动", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        OverlaySpinnerPreference(
                            title = "打开 App 后默认进入",
                            items = startupPageNames,
                            selectedIndex = startupPage,
                            onSelectedIndexChange = viewModel::setStartupPage
                        )
                    }
                }

                item { SmallTitle(text = "外观", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        OverlayDropdownPreference(
                            title = "配色模式",
                            items = colorModeNames,
                            selectedIndex = colorMode.coerceIn(0, colorModeNames.lastIndex),
                            onSelectedIndexChange = viewModel::setColorMode
                        )
                        SwitchPreference(
                            checked = useAfdianColor,
                            onCheckedChange = viewModel::setUseAfdianColor,
                            title = "使用爱发电主题色",
                        )
                        SwitchPreference(
                            checked = themeSettings.enableBlur,
                            onCheckedChange = viewModel::setEnableBlur,
                            title = "模糊效果",
                            summary = "顶栏、底栏、动画中的模糊效果",
                        )
                        SwitchPreference(
                            checked = themeSettings.progressiveBlur,
                            onCheckedChange = viewModel::setProgressiveBlur,
                            title = "渐进模糊",
                        )
                    }
                }

                item { SmallTitle(text = "动画", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            checked = advancedTransition,
                            onCheckedChange = viewModel::setAdvancedTransition,
                            title = "高级过渡动画（实验性）",
                            summary = "创作者主页以卡片展开的方式打开和返回",
                        )
                        SwitchPreference(
                            checked = gestureBack,
                            onCheckedChange = viewModel::setGestureBack,
                            title = "手势返回",
                            summary = "向右滑动即可返回（退出设置后生效）",
                        )
                        SwitchPreference(
                            checked = predictiveBack,
                            onCheckedChange = viewModel::setPredictiveBack,
                            title = "预测性返回",
                        )
                    }
                }

                item { SmallTitle(text = "数据卡片", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    // 开关的实时预览：切换下面两个开关时，预览卡的单位会立即出现/消失
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DataCard(
                            title = "收入预览",
                            value = "1280.00",
                            unit = "元".takeIf { showIncomeUnit },
                            modifier = Modifier.weight(1f),
                        )
                        DataCard(
                            title = "人数预览",
                            value = "256",
                            unit = "人".takeIf { showCountUnit },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            checked = showIncomeUnit,
                            onCheckedChange = viewModel::setShowIncomeUnit,
                            title = "显示收入单位",
                            summary = "收入、可提现等金额卡片在数值后显示「元」",
                        )
                        SwitchPreference(
                            checked = showCountUnit,
                            onCheckedChange = viewModel::setShowCountUnit,
                            title = "显示人次单位",
                            summary = "发电人数、发电次数、访问人次等卡片显示「人」「次」",
                        )
                    }
                }

                item { SmallTitle(text = "其他", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            checked = debugLogging,
                            onCheckedChange = viewModel::setDebugLogging,
                            title = "调试日志",
                            summary = "日常使用建议关闭，重启应用后生效",
                        )
                    }
                }

                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)){
                        SwitchPreference(
                            checked = historyEnabled,
                            onCheckedChange = viewModel::setHistoryEnabled,
                            title = "记录历史",
                            summary = "最近访问、最近搜索等" +
                                    "（仅保存在本地）",
                        )
                        ArrowPreference(
                            title = "清除浏览历史",
                            onClick = viewModel::clearHistoryAll,
                        )
                    }
                }

                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)){
                        ArrowPreference(
                            title = "重置管理页卡片布局",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                resetManagementDialogState.show() },
                        )
                        ArrowPreference(
                            title = "重置首页创作者数据卡片布局",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                resetHomeCreatorDialogState.show()
                            },
                        )
                    }
                }

                // 退出登录
                item {
                    TextButton(
                        text = "退出登录",
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }

                item { SmallTitle(text = "关于", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "开源许可",
                            onClick = { if (backStack.none { it == Route.OssLicenses }) backStack.add(Route.OssLicenses) },
                        )
                    }
                }
            }
            // 右侧滚动条
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            )
        }
    }

    // 重置管理页卡片布局确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = resetManagementDialogState,
        title = "重置管理页卡片布局",
        confirmText = "重置",
        destructive = true,
        onConfirm = viewModel::resetManagementLayout,
    ) {
        Text(
            "确定要恢复默认布局吗？当前自定义的卡片顺序将丢失。",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }

    // 重置首页创作者数据卡片布局确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = resetHomeCreatorDialogState,
        title = "重置首页创作者数据卡片布局",
        confirmText = "重置",
        destructive = true,
        onConfirm = viewModel::resetHomeCreatorDataLayout,
    ) {
        Text(
            "确定要恢复默认布局吗？当前自定义的卡片顺序将丢失。",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }
}
