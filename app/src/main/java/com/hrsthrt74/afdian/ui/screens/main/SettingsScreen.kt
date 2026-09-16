package com.hrsthrt74.afdian.ui.screens.main

// Miuix 提供的「滚动到边界触觉反馈」修饰符：列表快速滑动到顶/底边界时震动一下
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.theme.ThemeColorTransition
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import androidx.compose.foundation.lazy.rememberLazyListState

private val startupPageNames = listOf("首页", "发现", "消息", "我的")
// 配色模式选项，索引与 SettingsStore 的 0/1/2 约定一致
private val colorModeNames = listOf("跟随系统", "浅色模式", "深色模式")

/**
 * 设置页。
 *
 * 内容整体包在 [ThemeColorTransition] 里：本页是唯一能改配色的地方，切「配色模式」
 * 或「使用爱发电主题色」时整页颜色渐变过去，而不是瞬间换色。模糊取色也在包裹范围内，
 * 顶栏才能跟着一起渐变。其他页面保持原来的瞬间生效（切设置时它们看不见，没必要每帧重算）。
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
    val startupPage by viewModel.startupPage.collectAsState()
    val useAfdianColor by viewModel.useAfdianColor.collectAsState()
    val colorMode by viewModel.colorMode.collectAsState()
    val advancedTransition by viewModel.advancedTransition.collectAsState()
    val gestureBack by viewModel.gestureBack.collectAsState()
    val predictiveBack by viewModel.predictiveBack.collectAsState()
    val debugLogging by viewModel.debugLogging.collectAsState()
    val historyEnabled by viewModel.historyEnabled.collectAsState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    Scaffold(
        topBar = {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
            TopAppBar(
                title = "设置",
                largeTitle = "设置",
                color = blurBarColor(backdrop),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton(onBack = onBack, backStack = backStack)
                }
            )
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
                    // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding()
                )
            ) {
                item { SmallTitle(text = "启动", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        OverlayDropdownPreference(
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
                            summary = "为顶栏和底栏启用毛玻璃效果",
                        )
                        SwitchPreference(
                            checked = themeSettings.progressiveBlur,
                            onCheckedChange = viewModel::setProgressiveBlur,
                            title = "渐进模糊",
                            summary = "仅顶栏使用渐进模糊",
                        )
                    }
                }
                item { SmallTitle(text = "动画", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            checked = advancedTransition,
                            onCheckedChange = viewModel::setAdvancedTransition,
                            title = "高级过渡动画",
                            summary = "创作者主页以卡片展开的方式打开和返回",
                        )
                        SwitchPreference(
                            checked = gestureBack,
                            onCheckedChange = viewModel::setGestureBack,
                            title = "手势返回",
                            summary = "在页面左缘向右滑动即可返回上一页（系统返回手势不受影响；退出此页面后生效）",
                        )
                        SwitchPreference(
                            checked = predictiveBack,
                            onCheckedChange = viewModel::setPredictiveBack,
                            title = "预测性返回",
                            summary = "关闭系统返回时的页面预览动画，返回键和返回手势仍然可用",
                        )
                    }
                }
                item { SmallTitle(text = "其他", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            checked = historyEnabled,
                            onCheckedChange = viewModel::setHistoryEnabled,
                            title = "记录浏览历史",
                            summary = "记录创作者主页和动态详情的最近访问",
                        )
                        ArrowPreference(
                            title = "清除浏览历史",
                            summary = "删除所有浏览历史条目和本地头像缓存",
                            onClick = viewModel::clearHistoryAll,
                        )
                        SwitchPreference(
                            checked = debugLogging,
                            onCheckedChange = viewModel::setDebugLogging,
                            title = "调试日志",
                            // 面向用户的说明：开启属于排查问题模式，普通使用没必要开
                            summary = "日常使用建议关闭，重启应用后生效",
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                // 退出登录
                item {
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("退出登录")
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
                trackPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            )
        }
    }
}
