package com.hrsthrt74.afdian.ui.screens.main

// Miuix 提供的「滚动到边界触觉反馈」修饰符：列表快速滑动到顶/底边界时震动一下
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.EmptyStateView
import com.hrsthrt74.afdian.ui.components.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.PostCard
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun DiscoverScreen(
    viewModel: MainViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenPost: (com.hrsthrt74.afdian.data.content.RecommendedPost) -> Unit = {},
) {
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

    // 滚动到底部附近时触发加载更多（公共组件，收藏页动态 tab 也复用）
    LoadMoreEffect(listState, hasMore, isLoadingMore) { viewModel.loadMore() }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "发现",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding()
                )
            ) {
                item {
                    SmallTitle(text = "全站精选", modifier = Modifier.padding(start = 4.dp))
                }

                if (error != null) {
                    // 加载失败：空状态视图 + 重试按钮
                    item {
                        EmptyStateView(
                            title = "加载失败",
                            summary = error!!,
                            modifier = Modifier.fillParentMaxSize(),
                            action = {
                                Button(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                                    Text("重试")
                                }
                            },
                        )
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
                        // 操作栏的点赞/收藏逻辑已内置在组件里（读全局互动状态），无需逐卡片传参
                        PostCard(
                            post = post,
                            profile = post.user,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            onClick = { onOpenPost(post) },
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

