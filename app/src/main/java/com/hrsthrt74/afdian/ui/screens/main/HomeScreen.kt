package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.components.CreatorSmallCard
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel, onOpenCreator: (AfadianUser) -> Unit) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    val sponsoring by viewModel.sponsoring.collectAsState()
    val loading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "首页",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(MiuixIcons.Refresh, contentDescription = null)
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
                    SmallTitle(text = "我发电的", modifier = Modifier.padding(start = 4.dp))
                }

                if (sponsoring.isEmpty()) {
                    item {
                        if (loading) {
                            LoadingComponent(modifier = Modifier.padding(horizontal = 12.dp))
                        } else {
                            Text(
                                "还没有发电记录",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    item {
                        LazyRow(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(sponsoring, key = { it.id }) { item ->
                                // 双列小卡样式，横滑列表中固定宽度
                                CreatorSmallCard(
                                    avatarUrl = item.user.avatar,
                                    coverUrl = item.user.cover,
                                    creatorName = item.user.name.ifBlank { "创作者" },
                                    onClick = { onOpenCreator(item.user) },
                                    modifier = Modifier.width(130.dp)
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
