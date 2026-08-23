package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import top.yukonga.miuix.kmp.blur.layerBackdrop

private val startupPageNames = listOf("首页", "发现", "消息", "我的")
// 配色模式选项，索引与 SettingsStore 的 0/1/2 约定一致
private val colorModeNames = listOf("跟随系统", "浅色模式", "深色模式")

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val startupPage by viewModel.startupPage.collectAsState()
    val useAfdianColor by viewModel.useAfdianColor.collectAsState()
    val colorMode by viewModel.colorMode.collectAsState()
    val advancedTransition by viewModel.advancedTransition.collectAsState()
    val gestureBack by viewModel.gestureBack.collectAsState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "Back"
                        )
                    }
                }
            )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding()
                )
            ) {
                item { SmallTitle(text = "启动", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
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
                    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
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
//                            summary = "用爱发电品牌紫作为主色"
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
                    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
