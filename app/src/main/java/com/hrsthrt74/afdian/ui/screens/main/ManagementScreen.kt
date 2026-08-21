package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.content.ManagementSection
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ManagementScreen(onBack: () -> Unit, providedViewModel: ManagementViewModel? = null) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = providedViewModel ?: viewModel<ManagementViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val section by viewModel.section.collectAsState()
    val result by viewModel.result.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = "管理",
                largeTitle = "管理",
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
                    TabRow(
                        tabs = ManagementSection.values().map { it.title },
                        selectedTabIndex = ManagementSection.values().indexOf(section),
                        onTabSelected = { index ->
                            viewModel.load(ManagementSection.values()[index])
                        },
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        minWidth = 110.dp
                    )
                }

                item {
                    when {
                        loading -> Text("正在加载…", modifier = Modifier.padding(horizontal = 12.dp))
                        error != null -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(error!!)
                                Button(onClick = { viewModel.load(section) }) { Text("重试") }
                            }
                        }
                        result == null -> Text("暂无数据")
                    }
                }

                if (!loading && error == null) {
                    if (section == ManagementSection.Overview) {
                        items(result?.rows.orEmpty().chunked(2)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { (title, value) ->
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                title,
                                                color = MiuixTheme.colorScheme.primary,
                                                style = MiuixTheme.textStyles.subtitle,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                value,
                                                style = MiuixTheme.textStyles.title1,
                                                maxLines = 1,
                                                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 32.sp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(result?.rows.orEmpty(), key = { it.first }) { (title, value) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(title)
                                Text(value)
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
