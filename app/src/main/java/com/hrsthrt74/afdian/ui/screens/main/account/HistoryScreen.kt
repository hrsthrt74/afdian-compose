package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.data.history.HistoryTarget
import com.hrsthrt74.afdian.data.history.toAfadianUser
import com.hrsthrt74.afdian.data.history.toRecommendedPost
import com.hrsthrt74.afdian.ui.components.HistoryEntryOption
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.HistoryViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 浏览历史完整页面：记录按访问时间分组，条目本身复用 HistoryEntryOption。 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    backStack: NavBackStack,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenPost: (com.hrsthrt74.afdian.data.content.RecommendedPost) -> Unit,
    // 点击私信会话历史条目：参数与会话页路由一致
    onOpenConversation: (userId: String, userName: String, avatarUrl: String?) -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    // 列表滚动状态：双击顶栏回顶 + 滚动条共用
    val listState = rememberLazyListState()
    val groups = historyGroups(entries)
    // 是否显示「清空历史记录」确认弹窗
    val clearDialogState = rememberDialogState()

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                TopAppBar(
                    title = "浏览历史",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        BackButton(onBack = onBack, backStack = backStack)
                    },
                    actions = {
                        // 仅当有历史记录时才显示删除按钮
                        if (entries.isNotEmpty()) {
                            IconButton(onClick = { clearDialogState.show() }) {
                                Icon(MiuixIcons.Delete, contentDescription = "清空历史记录")
                            }
                        }
                    },
                )
            }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding(),
                ),
            ) {
                if (groups.isEmpty()) {
                    item(key = "empty") {
                        // 空状态：撑满整屏居中展示
                        EmptyStateView(
                            title = "还没有浏览记录",
                            icon = MiuixIcons.Light.Recent,
                            summary = "在这里可以看到你最近浏览的创作者和动态",
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
                groups.forEach { group ->
                    item(key = "title-${group.title}") {
                        SmallTitle(text = group.title, modifier = Modifier.padding(start = 4.dp))
                    }
                    item(key = "card-${group.title}") {
                        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                            group.entries.forEach { entry ->
                                HistoryEntryOption(
                                    entry = entry,
                                    avatarModel = viewModel.avatarModel(entry.target.avatarUrl),
                                    onClick = {
                                        when (val target = entry.target) {
                                            is HistoryTarget.CreatorDetailTarget -> onOpenCreator(target.toAfadianUser())
                                            is HistoryTarget.PostDetailTarget -> onOpenPost(target.toRecommendedPost())
                                            // 私信会话：跳回对应聊天页
                                            is HistoryTarget.MessageConversationTarget ->
                                                onOpenConversation(target.userId, target.name, target.avatarUrl)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 清空历史记录确认弹窗
    AppDialog(
        state = clearDialogState,
        title = "清空浏览历史",
        confirmText = "清空",
        destructive = true,
        onConfirm = { viewModel.clearAll() },
    ) {
        Text(
            "确定要删除所有浏览记录吗？",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }
}

private data class HistoryGroup(val title: String, val entries: List<HistoryEntry>)

private fun historyGroups(entries: List<HistoryEntry>, now: Long = System.currentTimeMillis()): List<HistoryGroup> {
    // 先用 Calendar 算出"今天"和"昨天"的零点时间戳，作为分组边界
    val todayStart = Calendar.getInstance().apply { timeInMillis = now }.let { cal ->
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val yesterdayStart = todayStart - 86_400_000L

    val formatter = SimpleDateFormat("M月d日", Locale.getDefault())
    return entries.groupBy { entry ->
        val ts = entry.visitedAt
        when {
            now - ts < 300_000L -> "刚刚"                          // 5 分钟内
            ts >= todayStart  -> "今天"                     // 今天非"刚刚"
            ts >= yesterdayStart -> "昨天"
            else -> formatter.format(Date(ts))                     // 更早：x月x日
        }
    }.entries.map { (title, groupedEntries) -> HistoryGroup(title, groupedEntries) }
}
