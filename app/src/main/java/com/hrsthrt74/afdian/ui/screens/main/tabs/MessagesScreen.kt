package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.R
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.data.content.NoticeAction
import com.hrsthrt74.afdian.ui.components.AppChip
import com.hrsthrt74.afdian.ui.components.ReceivedNoticeSheet
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.MessagesViewModel
import com.hrsthrt74.afdian.ui.viewmodel.ReceivedNoticeViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Reply
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 爱发电官方客服音酱的信息（internal：屏蔽/拒绝名单页的说明文字也要跳客服会话，一并复用）
internal const val AFDIAN_CUSTOMER_SERVICE_ID = "27f7cea2370d11e8ae8852540025c377"
internal  const val AFDIAN_CUSTOMER_SERVICE_AVATAR = "https://pic1.afdiancdn.com/user/27f7cea2370d11e8ae8852540025c377/avatar/2d9659585fc4798068efbb652e56c08a.jpg"

@OptIn(ExperimentalScrollBarApi::class)
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
    // 置顶会话快照：既决定卡片上的 Pin 标记，也保证置顶的老会话即使还没翻到所在页
    // 也能出现在列表最前（列表数据由 VM 合并，这里只用来判断「这一条是否置顶」）
    val pinnedCards by viewModel.pinnedCards.collectAsState()
    val pinnedIds = remember(pinnedCards) { pinnedCards.mapTo(HashSet()) { it.userId } }
    // 长按卡片弹出的操作菜单（状态 + 弹层都在 ConversationActionMenu.kt）
    val conversationMenuState = rememberConversationActionMenuState()
    // 未读评论/喜欢数（来自 /api/my/check），显示在三点菜单对应项的图标徽标上
    val unreadCommentCount by viewModel.unreadCommentCount.collectAsState()
    val unreadLikeCount by viewModel.unreadLikeCount.collectAsState()
    // 「全部标为已读」的执行中状态：执行期间禁用确认按钮，防止重复触发
    val isMarkingAllRead by viewModel.isMarkingAllRead.collectAsState()
    val markAllDialogState = rememberDialogState()

    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val listState = rememberLazyListState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    // 「收到的通知」弹层（三点菜单入口：收到的喜欢 / 收到的评论）；VM 依赖 Application，走自定义工厂创建
    val appContext = LocalContext.current.applicationContext as Application
    val likesViewModel: ReceivedNoticeViewModel = viewModel(
        key = "received-notice-like",
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReceivedNoticeViewModel(appContext, NoticeAction.Like) as T
            }
        },
    )
    val commentsViewModel: ReceivedNoticeViewModel = viewModel(
        key = "received-notice-comment",
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReceivedNoticeViewModel(appContext, NoticeAction.Comment) as T
            }
        },
    )
    var showLikesSheet by rememberSaveable { mutableStateOf(false) }
    var showCommentsSheet by rememberSaveable { mutableStateOf(false) }

    // 关闭弹层后立即同步一次未读数：服务端在拉取通知列表时已自动清除对应未读，
    // 不主动刷的话菜单红点要等下一轮 30s 轮询才会消失
    val coroutineScope = rememberCoroutineScope()
    val dismissNoticeSheet = {
        coroutineScope.launch { viewModel.refreshUnreadCount() }
        showLikesSheet = false
        showCommentsSheet = false
    }

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
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                TopAppBar(
                    title = "消息",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        // 右上角三点菜单：快捷入口（两组，组间自动带分割线）
                        WindowIconDropdownMenu(
                            // 多组重载的 collapseOnSelection 默认为 false（entries.size <= 1 才默认收起），
                            // 必须显式开启，否则点击菜单项（如跳转客服会话）后弹层不会自动收起
                            collapseOnSelection = true,
                            entries = listOf(
                                // 第一组：全部标为已读。会话级的批量操作，与下方通知/设置分组隔开，
                                // 放在最前是因为它是消息页最高频的操作；点击后先弹确认框再执行
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "全部已读",
                                            icon = {
                                                // 「全部已读」专用图标：资料库提供的 mark_all_as_read 字形（信封 + 对勾），
                                                // 自定义 drawable 用 ImageVector.vectorResource 载入，颜色交给 onSurface tint
                                                // 与下方长按会话菜单里的单个「标为已读」（MiuixIcons.Ok）刻意区分，不要混用
                                                // 不传 badgeCount 即不带徽标，仅占位以与其他菜单项图标对齐
                                                MenuIconWithBadge(
                                                    it,
                                                    icon = ImageVector.vectorResource(R.drawable.ic_mark_all_as_read),
                                                )
                                            },
                                            onClick = { markAllDialogState.show() },
                                        ),
                                    ),
                                ),
                                // 第二组：收到的评论 / 收到的喜欢（评论暂只展示，不支持回复等操作）
                                DropdownEntry(
                                    items = listOf(
                                        // 收到的评论：图标带未读数徽标，点击弹出评论通知弹层
                                        DropdownItem(
                                            text = "收到的评论",
                                            icon = {
                                                MenuIconWithBadge(
                                                    it,
                                                    icon = MiuixIcons.Regular.Community,
                                                    badgeCount = unreadCommentCount,
                                                )
                                            },
                                            onClick = { showCommentsSheet = true },
                                        ),
                                        // 收到的喜欢：图标带未读数徽标，点击弹出喜欢通知弹层
                                        DropdownItem(
                                            text = "收到的喜欢",
                                            icon = {
                                                MenuIconWithBadge(
                                                    it,
                                                    icon = MiuixIcons.Regular.Favorites,
                                                    badgeCount = unreadLikeCount,
                                                )
                                            },
                                            onClick = { showLikesSheet = true },
                                        ),
                                    ),
                                ),
                                // 第三组：FAQ 设置（创作者维护自动回复菜单），与社区通知分组隔开。
                                // miuix 的 CascadingListPopupLayout 会在非末组之间自动插入分割线，
                                // 所以这里只要多给一个 DropdownEntry，组间分割线自然出现。
                                DropdownEntry(
                                    items = listOf(
                                        // 图标用 Regular.Help，与会话页 FAQ 快捷回复按钮同款，语义一致
                                        DropdownItem(
                                            text = "FAQ 设置",
                                            icon = {
                                                MenuIconWithBadge(it, icon = MiuixIcons.Regular.Help)
                                            },
                                            onClick = { onNavigate(Route.FaqSetting) },
                                        ),
                                    ),
                                ),
                                // 第四组：官方客服对话，与上方分组隔开
                                DropdownEntry(
                                    items = listOf(
                                        // 客服对话固定跳转；名字与头像用官方客服音酱的信息
                                        DropdownItem(
                                            text = "爱发电客服",
                                            icon = {
                                                MenuIconWithBadge(it, icon = MiuixIcons.Regular.ContactsCircle)
                                            },
                                            onClick = {
                                                onNavigate(
                                                    Route.MessageConversation(
                                                        userId = AFDIAN_CUSTOMER_SERVICE_ID,
                                                        // 会话页标题/历史记录用官方名「Lain音酱」，菜单项仍显示「爱发电客服」
                                                        userName = "Lain音酱",
                                                        avatarUrl = AFDIAN_CUSTOMER_SERVICE_AVATAR
                                                    )
                                                )
                                            }
                                        ),
                                    ),
                                ),
                            ),
                        ) {
                            // 三点图标；评论/喜欢任一有未读时右上角叠 miuix 小红点（Badge 不传内容即纯点）
                            BadgedBox(
                                badge = {
                                    if (unreadCommentCount > 0 || unreadLikeCount > 0) {
                                        Badge(containerColor = MiuixTheme.colorScheme.primary)
                                    }
                                },
                            ) {
                                Icon(imageVector = MiuixIcons.More, contentDescription = "更多")
                            }
                        }
                    },
                )
            }
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
                            AppChip(
                                text = "未读",
                                selected = showUnreadOnly,
                                onClick = { viewModel.toggleUnreadFilter() },
                                modifier = Modifier.weight(1f)
                            )
                            AppChip(
                                text = "2 周内",
                                selected = showRecentOnly,
                                onClick = { viewModel.toggleRecentFilter() },
                                modifier = Modifier.weight(1f)
                            )
                            AppChip(
                                text = "发电明细",
                                selected = showSponsorDetail,
                                onClick = { viewModel.toggleSponsorDetail() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // key = user_id：唯一性由 MessagesViewModel 的分页写入（mergeDialogs）保证，
                    // 这里不要再加 distinctBy 之类的兜底——重复说明数据层出了问题，应该在那里修
                    items(dialogs, key = { it.id }) { dialog ->
                        MessageDialogRow(
                            dialog = dialog,
                            // 是否已置顶：名字旁显示 Pin 标记
                            pinned = dialog.id in pinnedIds,
                            showSponsorDetail = showSponsorDetail,
                            onClick = {
                                // 消息页在 Pager 中常驻，进入会话后返回不会重新组合；先同步清除卡片 badge。
                                viewModel.markConversationRead(dialog.user.user_id)
                                onNavigate(Route.MessageConversation(dialog.user.user_id, dialog.user.name, dialog.user.avatar))
                            },
                            // 长按：冻结「会话 + 置顶状态 + 卡片窗口坐标」后弹出操作菜单
                            onLongClick = { cardBounds ->
                                conversationMenuState.show(
                                    ConversationMenuTarget(
                                        dialog = dialog,
                                        pinned = dialog.id in pinnedIds,
                                        bounds = cardBounds,
                                    )
                                )
                            },
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

                // 右侧滚动条
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    trackPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = 108.dp),
                )

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
                    if (showUnreadOnly || showRecentOnly) {
                        // 筛选后无匹配：引导取消过滤
                        EmptyStateView(
                            title = "无匹配对话",
                            icon = MiuixIcons.Light.Filter,
                            summary = "点击已选中的筛选项可取消过滤",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // 真正没有任何对话
                        EmptyStateView(
                            title = "暂无对话",
                            icon = MiuixIcons.Light.Reply,
                            summary = "私信和发电相关通知会显示在这里",
                            modifier = Modifier.fillMaxSize(),
                        )
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

        // 「收到的喜欢」弹层：点击卡片关闭弹层并跳转对应动态详情
        ReceivedNoticeSheet(
            show = showLikesSheet,
            viewModel = likesViewModel,
            title = "收到的喜欢",
            onDismiss = dismissNoticeSheet,
            onOpenPost = { postId ->
                dismissNoticeSheet()
                onNavigate(Route.PostDetailById(postId))
            },
        )

        // 「收到的评论」弹层：暂只展示评论内容，点击卡片同样跳动态详情
        ReceivedNoticeSheet(
            show = showCommentsSheet,
            viewModel = commentsViewModel,
            title = "收到的评论",
            onDismiss = dismissNoticeSheet,
            onOpenPost = { postId ->
                dismissNoticeSheet()
                onNavigate(Route.PostDetailById(postId))
            },
        )

        // 长按卡片的操作菜单（置顶 / 标为已读）。
        // 必须挂在本 Scaffold 内：PopupLayout 会注册到 Scaffold 提供的 LocalPopupStates，
        // 放到 Scaffold 外会注册进无人消费的孤儿列表，弹层永不显示。
        ConversationActionMenu(
            state = conversationMenuState,
            onTogglePin = viewModel::togglePinned,
            onMarkRead = viewModel::markSingleRead,
        )
    }

    // 「全部标为已读」确认弹窗：爱发电没有一键已读接口，实现方式是逐个读取未读会话，
    // 每个会话都会拉一整页消息，因此在这里如实告知代价与并发上限，由用户确认后再执行。
    AppDialog(
        state = markAllDialogState,
        title = "全部标为已读",
        summary = "将依次读取每个未读会话的消息以清除未读标记，最多同时处理 ${MessagesViewModel.MARK_ALL_CONCURRENCY} 个会话。",
        confirmText = "标记已读",
        // 执行期间置灰，避免用户重复点击叠加请求
        confirmEnabled = !isMarkingAllRead,
        onConfirm = { viewModel.markAllRead() },
    )
}

/* 三点菜单项图标:
* ╭────────╮
* │ icon ② │   ② = 未读数徽标（badgeCount 为 0 时不显示）
* ╰────────╯
* */
@Composable
private fun MenuIconWithBadge(
    iconSlot: Modifier,
    icon: ImageVector,
    badgeCount: Int = 0,
) {
    // iconSlot 由 miuix Dropdown 提供（最小 26dp 方形 + 右侧 12dp 间距），作为容器
    Box(modifier = iconSlot) {
        // 图标 + 右上角未读数徽标；badgeCount 为 0 时不渲染徽标，仅剩图标
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    // miuix 数字徽标：默认 error 红底白字，右上角定位由 BadgedBox 处理
                    Badge(containerColor = MiuixTheme.colorScheme.primary, contentColor = MiuixTheme.colorScheme.onPrimary) { Text("$badgeCount") }
                }
            },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            // 菜单项图标：显式 onSurface，与菜单文字色一致
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/* 消息条目:
* ╭──────────────────────╮
* │  头  用户名字📌 时间 │
* │  像  对话内容  未读  │
* │      --------        │
* │      <-￥3.25        │
* ╰──────────────────────╯
*
* 点击进会话；长按弹出操作菜单（置顶 / 标为已读），菜单需要卡片的窗口坐标作为锚点。
* */
@Composable
private fun MessageDialogRow(
    dialog: MessageDialog,
    pinned: Boolean,
    showSponsorDetail: Boolean,
    onClick: () -> Unit,
    onLongClick: (cardBounds: Rect) -> Unit,
) {
    // 卡片在窗口中的最新坐标：长按回调把这份快照交给弹层，既用于摆放菜单，
    // 也用于挖洞遮罩（弹层打开期间页面不再滚动，快照不会过期）
    var cardBoundsInWindow by remember { mutableStateOf<Rect?>(null) }

    Card( modifier = Modifier.fillMaxWidth().onGloballyPositioned{ cardBoundsInWindow = it.boundsInWindow() } ) {
        Column{
            // 主要内容
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 点击 + 长按：长按震动由 combinedClickable 内部触发（LongPress），无需再手动震。
                    // padding 放在手势之后：涟漪与触摸区域覆盖含 12dp 内边距的整卡
                    .combinedClickable(
                        onClick = onClick,
                        onLongClickLabel = "更多操作",
                        onLongClick = { cardBoundsInWindow?.let(onLongClick) },
                    )
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

                    // 用户名 + 置顶标记（置顶标记跟随名字末尾，长名字先省略文字、不影响图标）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dialog.user.name,
                            style = MiuixTheme.textStyles.headline1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (pinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            // 置顶 Pin
                            Icon(
                                imageVector = MiuixIcons.Demibold.Pin,
                                contentDescription = "已置顶",
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

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

// 对话时间
private fun formatMessageTime(timestamp: Double): String {
    val date = Date((timestamp * 1000).toLong())
    val now = Date()
    val diff = now.time - date.time
    // 跨年后补充年份，避免仅显示「07/08」时无法判断消息所属年份。
    val messageYear = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    return when {
        messageYear != currentYear -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(date)
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} 分钟前"
        diff < 24 * 60 * 60 * 1000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        diff < 7 * 24 * 60 * 60 * 1000 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(date)
    }
}
