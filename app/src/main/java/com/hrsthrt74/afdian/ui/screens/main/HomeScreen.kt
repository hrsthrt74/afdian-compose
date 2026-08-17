package com.hrsthrt74.afdian.ui.screens.main

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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    val posts by viewModel.posts.collectAsState()
    val account by viewModel.account.collectAsState()
    val sponsoring by viewModel.sponsoring.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "首页",
                scrollBehavior = scrollBehavior
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
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = account?.let { "你好，${it.displayName}" } ?: "正在加载账号信息",
                                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title3
                            )
                        }
                        Button(
                            onClick = viewModel::refresh,
                            enabled = !loading
                        ) {
                            Text(if (loading) "加载中…" else "刷新")
                        }
                    }
                }

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
                                Column(modifier = Modifier.width(132.dp)) {
                                    AsyncImage(
                                        model = item.user.avatar,
                                        contentDescription = item.user.name,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(28.dp))
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

                item {
                    SmallTitle(text = "全站精选")
                }

                if (error != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(error!!)
                            Button(onClick = viewModel::refresh) {
                                Text("重试")
                            }
                        }
                    }
                } else if (posts.isEmpty()) {
                    item {
                        Text(if (loading) "正在加载精选内容…" else "暂无精选内容")
                    }
                } else {
                    items(posts, key = { it.id }) { post ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row {
                                AsyncImage(
                                    model = post.user.avatar,
                                    contentDescription = post.user.name,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(post.user.name.ifBlank { "爱发电用户" })
                                    Text(post.title)
                                }
                            }
                            if (!post.cover.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AsyncImage(
                                    model = post.cover,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            if (post.summary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(post.summary, maxLines = 3)
                            }
                            Text("点赞 ${post.like_count ?: 0}   评论 ${post.comment_count ?: 0}")
                        }
                    }
                }
            }
        }
    }
}
