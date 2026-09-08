package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.MessagesViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Reply
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onNavigate: (Route) -> Unit
) {
    val dialogs by viewModel.filteredDialogs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val showUnreadOnly by viewModel.showUnreadOnly.collectAsState()
    val showRecentOnly by viewModel.showRecentOnly.collectAsState()
    val showSponsorDetail by viewModel.showSponsorDetail.collectAsState()

    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val listState = rememberLazyListState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    // 下拉触发刷新：isRefreshing 变 true 时调用 viewModel.refresh()
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
        }
    }

    // 数据加载完成（isLoading 从 true→false）时关闭刷新指示器，并滚动到列表顶部
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
            listState.animateScrollToItem(0)
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
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                TopAppBar(
                    title = "消息",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior
                )
            }
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
            modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 消息列表（始终渲染，保证 nestedScroll 和模糊采样正常）
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = contentBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    state = listState
                ) {
                    // 过滤选项 chip 行
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                text = "未读",
                                selected = showUnreadOnly,
                                onClick = { viewModel.toggleUnreadFilter() },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                text = "2 周内",
                                selected = showRecentOnly,
                                onClick = { viewModel.toggleRecentFilter() },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                text = "发电明细",
                                selected = showSponsorDetail,
                                onClick = { viewModel.toggleSponsorDetail() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    items(dialogs, key = { it.id }) { dialog ->
                        MessageDialogRow(
                            dialog = dialog,
                            showSponsorDetail = showSponsorDetail,
                            onClick = {
                                onNavigate(Route.MessageConversation(dialog.user.user_id, dialog.user.name, dialog.user.avatar))
                            }
                        )
                    }

                    if (isLoadingMore) {
                        item { LoadingComponent() }
                    }

                    // 全部加载完成且无过滤时，显示对话总数
                    if (!isLoading && !isLoadingMore && !hasMore && dialogs.isNotEmpty()
                        && !showUnreadOnly && !showRecentOnly
                    ) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "共 ${dialogs.size} 条对话",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }

                // 加载中覆盖层
                if (isLoading && dialogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingComponent()
                    }
                }

                // 空状态覆盖层
                if (!isLoading && dialogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (showUnreadOnly || showRecentOnly) {
                                Icon(MiuixIcons.Light.Filter, null, Modifier.size(96.dp), MiuixTheme.colorScheme.primaryContainer)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("无匹配对话", style = MiuixTheme.textStyles.title2)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("点击已选中的筛选项可取消过滤", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            } else {
                                Icon(MiuixIcons.Light.Reply, null, Modifier.size(96.dp), MiuixTheme.colorScheme.primaryContainer)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("暂无对话", style = MiuixTheme.textStyles.title2)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("私信和发电相关通知会显示在这里", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
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

/* 消息条目:
* ╭──────────────────────╮
* │  头  用户名字  时间  │
* │  像  对话内容  未读  │
* │      --------        │
* │      <-￥3.25        │
* ╰──────────────────────╯
* */
@Composable
private fun MessageDialogRow(
    dialog: MessageDialog,
    showSponsorDetail: Boolean,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column{

            // 主要内容
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
                Column(modifier = Modifier.weight(1f)) {

                    // 用户名
                    Text(
                        text = dialog.user.name,
                        style = MiuixTheme.textStyles.headline1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // desc
                    dialog.desc?.let { preview ->
                        if (preview.isNotBlank()) {
                            Text(
                                text = preview,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // 发电明细
                    AnimatedVisibility(
                        visible = showSponsorDetail && dialog.sponsor_info != null,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        val info = dialog.sponsor_info
                        val outAmount = info?.out_detail?.current_amount
                        val inAmount = info?.in_detail?.current_amount
                        if (!outAmount.isNullOrBlank() && outAmount != "0.00" ||
                            !inAmount.isNullOrBlank() && inAmount != "0.00"
                        ) {
                            Column() {

                                HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 4.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {

                                    // 我对他发电
                                    if (!outAmount.isNullOrBlank() && outAmount != "0.00") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = MiuixIcons.Demibold.Undo,
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "¥$outAmount",
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                            Spacer(Modifier.width(12.dp))
                                        }
                                    }

                                    // 他对我发电
                                    if (!inAmount.isNullOrBlank() && inAmount != "0.00") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = MiuixIcons.Demibold.Redo,
                                                contentDescription = null,
                                                tint = Color(0xFFFCB239), // 与官方相同
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "¥$inAmount",
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                        }
                                    }
                                }
                            }
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

/** 过滤选项 Chip：点击切换状态 */
@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {   MiuixTheme.colorScheme.primaryContainer
    } else {                                MiuixTheme.colorScheme.secondary }

    val contentColor = if (selected) {      MiuixTheme.colorScheme.onPrimaryContainer
    } else {                                MiuixTheme.colorScheme.onSecondaryVariant }

    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = contentColor
        )
    }
}
