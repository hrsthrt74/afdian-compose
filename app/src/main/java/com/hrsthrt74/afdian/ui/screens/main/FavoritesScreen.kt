package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onBack: () -> Unit,
) {
    val kind by viewModel.kind.collectAsState()
    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = "我的收藏",
                largeTitle = "我的收藏",
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
                        tabs = listOf("创作者", "动态", "众筹"),
                        selectedTabIndex = kind,
                        onTabSelected = viewModel::select,
                    )
                }

                if (loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingComponent()
                        }
                    }
                } else if (error != null) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(error!!)
                            Button(onClick = viewModel::load) { Text("重试") }
                        }
                    }
                } else if (items.isEmpty()) {
                    item { Text("还没有收藏内容", modifier = Modifier.padding(horizontal = 12.dp)) }
                } else {
                    items(items, key = { it.id }) { item ->
                        val onClick = if (kind == 0) {
                            { onOpenCreator(item.user ?: AfadianUser(user_id = item.id, name = item.title, avatar = item.image)) }
                        } else null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Avatar(
                                model = item.image,
                                size = 48.dp,
                            )
                            Column {
                                Text(item.title)
                                Text(item.subtitle)
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
