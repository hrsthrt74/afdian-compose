package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.AfadianUser
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh

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
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp
                ),
            ) {
                item {
                    SmallTitle(text = "我发电的")
                }

                if (sponsoring.isEmpty()) {
                    item {
                        Text(
                            if (loading) "正在加载发电记录…" else "还没有发电记录"
                        )
                    }
                } else {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(sponsoring, key = { it.id }) { item ->
                                Card(onClick = { onOpenCreator(item.user) }) {
                                    Column(modifier = Modifier.width(132.dp)) {
                                        AsyncImage(
                                            model = item.user.avatar,
                                            contentDescription = item.user.name,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                        Text(item.user.name.ifBlank { "创作者" })
                                        Text(item.planName)
                                        if ((item.new_post_count ?: 0) > 0) {
                                            Text("${item.new_post_count} 条新动态")
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
