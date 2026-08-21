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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.navigation.Route
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onNavigate: (Route) -> Unit
) {
    val dialogs by viewModel.dialogs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val error by viewModel.error.collectAsState()

    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val listState = rememberLazyListState()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startAutoRefresh()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = layoutInfo.totalItemsCount
                if (lastVisibleIndex >= totalItems - 3 && !isLoadingMore && !isLoading) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "消息",
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf(
                "下拉刷新",
                "松开刷新",
                "正在刷新",
                "刷新成功"
            ),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && dialogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingComponent()
                    }
                } else if (dialogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂无消息", style = MiuixTheme.textStyles.title2)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("私信和发电相关通知会显示在这里", style = MiuixTheme.textStyles.body2)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 8.dp,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        state = listState
                    ) {
                        items(dialogs, key = { it.id }) { dialog ->
                            MessageDialogRow(
                                dialog = dialog,
                                onClick = {
                                    onNavigate(Route.MessageConversation(dialog.user.user_id, dialog.user.name, dialog.user.avatar))
                                }
                            )
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("加载更多...", style = MiuixTheme.textStyles.body2)
                                }
                            }
                        }
                    }
                }

                error?.let { errorMessage ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = errorMessage,
                                    style = MiuixTheme.textStyles.body2,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "重试",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { viewModel.refresh() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageDialogRow(
    dialog: MessageDialog,
    onClick: () -> Unit
) {
    Card( modifier = Modifier.fillMaxWidth() ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 头像
            Avatar(
                model = dialog.user.avatar,
                size = 48.dp,
                contentDescription = dialog.user.name,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 2. 两行文本
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = dialog.user.name,
                    style = MiuixTheme.textStyles.headline1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                dialog.desc?.let { preview ->
                    if (preview.isNotBlank()) {
                        Text(
                            text = preview,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 3. 两行：时间 + 消息计数
            Column(
                horizontalAlignment = Alignment.End
            ) {
                dialog.send_time?.let { timestamp ->
                    Text(
                        text = formatMessageTime(timestamp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                if (dialog.unread_count > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.primary)
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${dialog.unread_count}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Double): String {
    val date = Date((timestamp * 1000).toLong())
    val now = Date()
    val diff = now.time - date.time

    return when {
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} 分钟前"
        diff < 24 * 60 * 60 * 1000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        diff < 7 * 24 * 60 * 60 * 1000 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(date)
    }
}
