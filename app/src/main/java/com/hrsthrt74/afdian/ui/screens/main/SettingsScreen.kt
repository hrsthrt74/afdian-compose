package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

private val startupPageNames = listOf("首页", "发现", "动态", "消息", "我的")

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

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "设置",
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
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { SmallTitle(text = "启动") }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        OverlayDropdownPreference(
                            title = "启动时显示的页面",
                            summary = "打开 App 后默认进入 ${startupPageNames.getOrElse(startupPage) { "首页" }}",
                            items = startupPageNames,
                            selectedIndex = startupPage,
                            onSelectedIndexChange = viewModel::setStartupPage
                        )
                    }
                }
                item { SmallTitle(text = "外观") }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            checked = useAfdianColor,
                            onCheckedChange = viewModel::setUseAfdianColor,
                            title = "使用爱发电主题色",
                            summary = "用爱发电品牌紫作为主色"
                        )
                    }
                }
            }
        }
    }
}