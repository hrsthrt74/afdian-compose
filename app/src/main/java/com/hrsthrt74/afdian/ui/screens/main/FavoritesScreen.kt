package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.CreatorListItem
import com.hrsthrt74.afdian.ui.components.LoadMoreEffect
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.PostCard
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.FavoritesViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onBack: () -> Unit,
) {
    val kind by viewModel.kind.collectAsState()
    val items by viewModel.items.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    // 点赞/收藏状态集合（收藏页拉到的动态默认全部已收藏，由 VM 预置）
    val likedIds by viewModel.interactions.likedIds.collectAsState()
    val markedIds by viewModel.interactions.markedIds.collectAsState()

    // 动态 tab 的滚动加载更多需要感知列表位置
    val listState = rememberLazyListState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    Scaffold(
        topBar = {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
            TopAppBar(
                title = "我的收藏",
                largeTitle = "我的收藏",
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
        // 垂直布局：TabRow 放在列表外面，结构上永远固定在列表上方，
        // 不依赖 stickyHeader（miuix Scaffold 的内容是全屏铺开、被 TopAppBar
        // 覆盖的，sticky 吸附点会被顶栏挡住）
        Column(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            // 顶部留出 TopAppBar 的高度（随折叠动画动态变化）
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(paddingValues.calculateTopPadding())
            )

            // 固定的 TabRow，加背景色防止列表内容透出
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(vertical = 8.dp, horizontal = 16.dp)
            ) {
                TabRow(
                    tabs = listOf("创作者", "动态", "众筹"),
                    selectedTabIndex = kind,
                    onTabSelected = viewModel::select,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    bottom = contentBottomPadding()
                )
            ) {
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
                } else if (kind == 1) {
                    // 动态 tab：复用发现页全站精选的 PostCard 渲染完整动态卡片
                    if (posts.isEmpty()) {
                        item {
                            Text(
                                "还没有收藏内容",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )
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
                                liked = post.id in likedIds,
                                marked = post.id in markedIds,
                                onLikeToggle = { viewModel.interactions.toggleLike(post.id) },
                                onMarkToggle = { viewModel.interactions.toggleMark(post.id) },
                            )
                        }
                        // 底部加载指示器
                        if (isLoadingMore) {
                            item { LoadingComponent(modifier = Modifier.padding(vertical = 16.dp)) }
                        }
                    }
                } else if (items.isEmpty()) {
                    item {
                        Text(
                            "还没有收藏内容",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    items(items, key = { it.id }) { item ->
                        val onClick = if (kind == 0) {
                            { onOpenCreator(item.user ?: AfadianUser(user_id = item.id, name = item.title, avatar = item.image)) }
                        } else null

                        // 创作者 tab 使用长条形卡片组件，其他 tab 保持普通列表行
                        if (kind == 0 && onClick != null) {
                            // 创作内容：subtitle 为空时回退到创作者简介（doing）和分区名，
                            // 由组件自动拼成「正在创作 xxx」
                            val doing = item.subtitle.ifBlank {
                                listOfNotNull(
                                    item.user?.creator?.doing,
                                    item.user?.creator?.category?.name
                                ).joinToString(" · ").ifBlank { null }
                            }
                            // 捕获卡片边界，作为「主页展开」过渡动画的起点
                            val origin = rememberCreatorOrigin()
                            CreatorListItem(
                                avatarUrl = item.image,
                                creatorName = item.title,
                                doing = doing,
                                onClick = origin.onClick(onClick),
                                modifier = origin.trackModifier()
                                    .padding(horizontal = 16.dp)
                                    .padding(vertical = 4.dp)
                            )
                        } else {
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
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // 滚动到底部附近自动加载下一页（动态 tab 专属，放在 LazyColumn 外的 Composable 上下文）
            if (kind == 1) {
                LoadMoreEffect(listState, hasMore, isLoadingMore) { viewModel.loadMore() }
            }
        }
    }
}
