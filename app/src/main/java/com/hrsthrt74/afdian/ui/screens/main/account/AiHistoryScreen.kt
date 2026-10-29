package com.hrsthrt74.afdian.ui.screens.main

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.R
import com.hrsthrt74.afdian.data.ai.AiHistoryEntry
import com.hrsthrt74.afdian.ui.components.ai.AiHistoryDetailSheet
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.components.relativeTime
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.components.ai.AiEntryIconSize
import com.hrsthrt74.afdian.ui.components.ai.AiSparkleAvatar
import com.hrsthrt74.afdian.ui.util.aiAnswerSummary
import com.hrsthrt74.afdian.ui.util.aiHistoryGroups
import com.hrsthrt74.afdian.ui.viewmodel.AiHistoryViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * AI 记录完整页面：只读回看，按问答时间分组。
 *
 * 结构照 `HistoryScreen` 一一对应（分组标题 + 一张 Card 装一组 + 整页空态 + 顶栏清空），
 * 差异只在条目：AI 记录没有头像也没有目标可跳，点开是详情弹层而不是新页面。
 *
 * @param viewModel 与消息页入口卡片共享的同一个实例，删除后卡片上的摘要会同步刷新
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AiHistoryScreen(
    viewModel: AiHistoryViewModel,
    onBack: () -> Unit,
    backStack: NavBackStack,
) {
    val entries by viewModel.entries.collectAsState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    // 列表滚动状态：双击顶栏回顶 + 滚动条共用
    val listState = rememberLazyListState()
    // 分组依赖 Now：只在列表数据变化时重算，避免每次重组都新建 SimpleDateFormat
    val groups = remember(entries) { aiHistoryGroups(entries) }

    // 详情弹层只记 id：记录被删掉后 entries 里找不到它，弹层会自动关闭，不用额外同步状态
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = entries.firstOrNull { it.id == selectedId }
    // 待删除的记录与确认弹窗（先关弹层再弹确认框，避免弹窗压在 Sheet 窗口上）
    var pendingDelete by remember { mutableStateOf<AiHistoryEntry?>(null) }
    val deleteDialogState = rememberDialogState()
    val clearDialogState = rememberDialogState()

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                TopAppBar(
                    title = "AI 记录",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        BackButton(onBack = onBack, backStack = backStack)
                    },
                    actions = {
                        // 仅当有记录时才显示删除按钮（空页面给一个能点的清空按钮没有意义）
                        if (entries.isNotEmpty()) {
                            IconButton(onClick = { clearDialogState.show() }) {
                                Icon(MiuixIcons.Delete, contentDescription = "清空全部 AI 记录")
                            }
                        }
                    },
                )
            }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding(),
                ),
            ) {
                if (groups.isEmpty()) {
                    item(key = "empty") {
                        // 空状态：撑满整屏居中展示
                        EmptyStateView(
                            title = "还没有 AI 记录",
                            icon = ImageVector.vectorResource(R.drawable.ic_ai_sparkle),
                            summary = "在「收入明细 → 问 AI」生成的报告会保存在这里",
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
                                AiHistoryEntryOption(
                                    entry = entry,
                                    onClick = { selectedId = entry.id },
                                )
                            }
                        }
                    }
                }
            }
            // 滚动条：与列表同一状态，trackPadding 让开顶栏
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                trackPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }

    // 详情弹层：把当时那次问答原样铺回来
    AiHistoryDetailSheet(
        entry = selected,
        onDismiss = { selectedId = null },
        onDelete = { entry ->
            // 先关弹层：否则确认框会叠在 Sheet 自己的 Window 之上，返回键行为会很怪
            selectedId = null
            pendingDelete = entry
            deleteDialogState.show()
        },
    )

    // 删除单条确认
    AppDialog(
        state = deleteDialogState,
        title = "删除这条记录",
        confirmText = "删除",
        destructive = true,
        onConfirm = { pendingDelete?.let { viewModel.delete(it.id) } },
        onDismissed = { pendingDelete = null },
    ) {
        Text(
            "删除后无法恢复。",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }

    // 清空全部确认：文案与消息页长按「AI 记录」卡片弹的那个框逐字一致——
    // 两个入口做的是同一件事，说法不同会让人以为是两种清理
    AppDialog(
        state = clearDialogState,
        title = "清空全部 AI 记录",
        summary = "将删除本机保存的全部 AI 问答记录，删除后无法恢复。",
        confirmText = "清空",
        destructive = true,
        onConfirm = { viewModel.clearAll() },
    )
}

/**
 * 列表条目：一问一答压成两行。
 *
 * 不复用 `HistoryEntryOption`：那个组件绑死了 `HistoryEntry` 与 AsyncImage 头像链接，
 * 硬套要给它加一堆空参数，反而更难读。
 */
@Composable
private fun AiHistoryEntryOption(
    entry: AiHistoryEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // clickable 放在 padding 之前：涟漪覆盖整行，而不是只覆盖文字区
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标块：AI 不是人，不用 Avatar，用与消息页「AI 记录」入口同款的 sparkle 圆形块。
        // 版式与配色都来自 AiSparkleAvatar，两处共用一份定义，不会再出现一个蓝底一个灰底
        AiSparkleAvatar(size = AiEntryIconSize)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 第一行：问的是哪份数据（近7天）
                // 字号比标题档小一号（18sp → 17sp）：这一行是列表条目而非页面标题，
                // 用 title4 会和下面那条灰色摘要拉开过大落差，缩一档后两行更像一组信息
                Text(
                    text = entry.query.ifBlank { entry.title },
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // 右侧：相对时间
                Text(
                    text = relativeTime(entry.createdAt),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            // 第二行：正文摘要（去掉 Markdown 标记、压成一行）
            Text(
                text = aiAnswerSummary(entry.answer),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
