package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.NoticeAction
import com.hrsthrt74.afdian.data.content.NoticeItem
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import com.hrsthrt74.afdian.ui.viewmodel.ReceivedNoticeViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 「收到的通知」弹层：收到的喜欢 / 收到的评论共用（收到的评论暂只做展示，不支持回复等操作）。
 *
 * 数据结构与网页端 /notice/like、/notice/comment 对齐：动作发起人（头像/昵称）、
 * 动作时间、被赞或被评论的动态（标题/封面）；评论通知额外展示评论正文。
 * 点击整张卡片跳转到对应动态详情。
 */
@Composable
fun ReceivedNoticeSheet(
    show: Boolean,
    viewModel: ReceivedNoticeViewModel,
    title: String,
    onDismiss: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val notices by viewModel.notices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()

    // 每次打开都刷新首页：用户可能刚收到新的通知；服务端也会在本次拉取时清除对应未读
    LaunchedEffect(show) {
        if (show) viewModel.refresh()
    }

    WindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        // 统一撑满 Sheet 允许的最大高度：加载/空/错误态也保持满高，
        // 列表加载完成出现时不会发生高度跳变
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            when {
                // 首屏加载中
                isLoading && notices.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingComponent()
                }
                // 加载失败：预设错误态（含重试按钮）
                notices.isEmpty() && error != null -> ErrorStateView(
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                    summary = error,
                )
                // 确实一条通知都没有
                notices.isEmpty() -> EmptyStateView(
                    title = "还没有收到通知",
                    summary = "有人与你的动态互动时会显示在这里",
                    icon = MiuixIcons.Light.Favorites,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> NoticeList(
                    notices = notices,
                    isLoadingMore = isLoadingMore,
                    hasMore = hasMore,
                    error = error,
                    // 结尾「共 N 条」的名词按通知类型区分
                    endNoun = when (viewModel.action) {
                        NoticeAction.Like -> "喜欢"
                        NoticeAction.Comment -> "评论"
                    },
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    onOpenPost = onOpenPost,
                )
            }
        }
    }
}

@Composable
private fun NoticeList(
    notices: List<NoticeItem>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    endNoun: String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    // 触底预加载：倒数第 3 项进入视口就开始拉下一页；loadMore 内部自带防抖
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (lastVisibleIndex >= layoutInfo.totalItemsCount - 3) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        // 外层 Box 已撑满 Sheet 高度，这里跟着撑满，条目少时列表也能占满整层
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(notices, key = { it.id }) { notice ->
            NoticeCard(
                notice = notice,
                onOpenPost = onOpenPost,
            )
        }

        if (isLoadingMore) {
            item(key = "loading_more") {
                LoadingComponent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }

        // 加载更多失败：列表仍在，给出可点击重试的一行提示
        if (error != null && notices.isNotEmpty() && !isLoadingMore) {
            item(key = "load_more_error") {
                Text(
                    text = "加载失败，点击重试",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRetry)
                        .padding(vertical = 12.dp),
                )
            }
        }

        // 全部加载完成：显示总数
        if (!isLoadingMore && !hasMore && error == null && notices.isNotEmpty()) {
            item(key = "end_marker") {
                Text(
                    text = "共 ${notices.size} 条$endNoun",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/* 单条通知卡片:
* ╭────────────────────────────────╮
* │ 头  昵称              时间     │
* │ 像  喜欢了动态/评论了动态「标题」│
* │    ────────────────            │
* │    「评论正文」（仅评论） [缩略图]│
* ╰────────────────────────────────╯
* 整卡可点 → 对应动态详情
* */
@Composable
private fun NoticeCard(
    notice: NoticeItem,
    onOpenPost: (String) -> Unit,
) {
    val user = notice.user
    val post = notice.post
    Card(
        // 弹层内 Card 统一配色：与 Sheet 背景拉开层次
        colors = AfdianCardDefaults.overlayColors(),
        // 整卡点击下沉反馈：按压视觉由 Card 承担，点击事件挂在内层 Row（DESIGN.md 约定）
        modifier = Modifier
            .fillMaxWidth()
            .pressable(interactionSource = null, indication = SinkFeedback(), delay = null),
    ) {
        Row(
            // post_id 缺失（如动态已被删除）时整卡不可点
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = post?.postId?.isNotBlank() == true) {
                    onOpenPost(post!!.postId)
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像（纯展示，不加点击动作）
            Avatar(
                model = user.avatar,
                size = 44.dp,
                contentDescription = user.name,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 昵称
                    Text(
                        text = user.name.ifBlank { "爱发电用户" },
                        style = MiuixTheme.textStyles.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 时间
                    val timeText = formatNoticeTime(notice.time)
                    if (timeText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = timeText,
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                // 「喜欢了动态 / 评论了动态 + 动态标题」：整行为次要色
                Text(
                    text = notice.subject.ifBlank { if (notice.commentText != null) "评论了动态" else "喜欢了动态" } +
                        post?.title?.takeIf(String::isNotBlank)?.let { "「$it」" }.orEmpty(),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // 评论正文：放在动作说明行下方，用分割线隔开（点赞通知没有这一块）
                val commentText = notice.commentText?.takeIf(String::isNotBlank)
                if (commentText != null) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MiuixTheme.colorScheme.onSecondaryContainerVariant)
                    Text(
                        text = commentText,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            val thumb = post?.thumb
            if (!thumb.isNullOrBlank()) {
                // 动态封面缩略图（点击跟随整卡行为）
                AsyncImage(
                    model = thumb,
                    contentDescription = post?.title?.ifBlank { null } ?: "动态封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .squircleClip(8.dp),
                )
            } else {
                // 动态无封面（纯文字动态）时用点赞图标占位
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MiuixTheme.colorScheme.disabledSecondary)
                        .squircleClip(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Demibold.Favorites,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                    )
                }
            }
        }
    }
}

/** 动作时间展示：跨年补年份，其余按官方客户端习惯就近展示相对时间。 */
private fun formatNoticeTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return ""
    val date = Date(timestamp * 1000)
    val now = Date()
    val diff = now.time - date.time
    val noticeYear = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    return when {
        noticeYear != currentYear -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(date)
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} 分钟前"
        diff < 24 * 60 * 60 * 1000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        diff < 7 * 24 * 60 * 60 * 1000 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(date)
    }
}
