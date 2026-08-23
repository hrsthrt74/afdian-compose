package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.PostCard
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import top.yukonga.miuix.kmp.blur.layerBackdrop

@Composable
fun DiscoverScreen(viewModel: MainViewModel, onOpenCreator: (AfadianUser) -> Unit) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    val posts by viewModel.posts.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    val listState = rememberLazyListState()

    // 滚动到底部附近时触发加载更多
    LoadMoreEffect(listState, hasMore, isLoadingMore) { viewModel.loadMore() }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
            TopAppBar(
                title = "发现",
                color = blurBarColor(backdrop),
                scrollBehavior = scrollBehavior
            )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding()
                )
            ) {
                item {
                    SmallTitle(text = "全站精选", modifier = Modifier.padding(start = 4.dp))
                }

                if (error != null) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(error!!)
                            Button(onClick = viewModel::refresh) {
                                Text("重试")
                            }
                        }
                    }
                } else if (posts.isEmpty()) {
                    item {
                        if (loading) {
                            LoadingComponent(modifier = Modifier.padding(horizontal = 12.dp))
                        } else {
                            Text("暂无精选内容", modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                } else {
                    items(posts, key = { it.id }) { post ->
                        // 捕获动态卡片头部行的边界，作为「主页展开」过渡动画的起点
                        val origin = rememberCreatorOrigin()
                        PostCard(
                            post = post,
                            profile = post.user,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            onCreatorClick = origin.onClick { onOpenCreator(post.user) },
                            showDoing = true,
                            creatorOrigin = origin,
                        )
                    }
                    // 底部加载指示器
                    if (isLoadingMore) {
                        item { LoadingComponent(modifier = Modifier.padding(vertical = 16.dp)) }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * 当列表滚动到倒数第 [threshold] 个元素时，触发 [onLoadMore]。
 * 仅在有更多数据且当前未加载中时触发。
 */
@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    threshold: Int = 3,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = layoutInfo.totalItemsCount
            hasMore && !isLoadingMore && total > 0 && lastVisible >= total - threshold
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
}
