package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.data.history.HistoryTarget
import com.hrsthrt74.afdian.data.history.toAfadianUser
import com.hrsthrt74.afdian.data.history.toRecommendedPost
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.HistoryEntryOption
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 浏览历史完整页面：记录按访问时间分组，条目本身复用 HistoryEntryOption。 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenCreator: (AfadianUser) -> Unit,
    onOpenPost: (com.hrsthrt74.afdian.data.content.RecommendedPost) -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val groups = historyGroups(entries)

    Scaffold(
        topBar = {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                TopAppBar(
                    title = "浏览历史",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding(),
                ),
            ) {
                if (groups.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "还没有浏览记录",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                groups.forEach { group ->
                    item(key = "title-${group.title}") {
                        SmallTitle(text = group.title, modifier = Modifier.padding(start = 4.dp))
                    }
                    item(key = "card-${group.title}") {
                        Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            group.entries.forEach { entry ->
                                HistoryEntryOption(
                                    entry = entry,
                                    avatarModel = viewModel.avatarModel(entry.target.avatarUrl),
                                    onClick = {
                                        when (val target = entry.target) {
                                            is HistoryTarget.CreatorDetailTarget -> onOpenCreator(target.toAfadianUser())
                                            is HistoryTarget.PostDetailTarget -> onOpenPost(target.toRecommendedPost())
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
}

private data class HistoryGroup(val title: String, val entries: List<HistoryEntry>)

private fun historyGroups(entries: List<HistoryEntry>, now: Long = System.currentTimeMillis()): List<HistoryGroup> {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    val todayYear = calendar.get(Calendar.YEAR)
    val todayDay = calendar.get(Calendar.DAY_OF_YEAR)
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return entries.groupBy { entry ->
        calendar.timeInMillis = entry.visitedAt
        val year = calendar.get(Calendar.YEAR)
        val day = calendar.get(Calendar.DAY_OF_YEAR)
        when {
            year == todayYear && day == todayDay && now - entry.visitedAt < 60_000L -> "刚刚"
            year == todayYear && day == todayDay -> "今天早些时候"
            year == yesterday.get(Calendar.YEAR) && day == yesterday.get(Calendar.DAY_OF_YEAR) -> "昨天"
            else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(entry.visitedAt))
        }
    }.entries.map { (title, groupedEntries) -> HistoryGroup(title, groupedEntries) }
}
