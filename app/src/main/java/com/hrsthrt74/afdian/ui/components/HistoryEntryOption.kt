package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.history.HistoryEntry
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单条历史记录选项。
 *
 * 该组件不包裹 Card，只负责一条可点击内容，因此既能放入外显预览卡片，
 * 也能放入完整历史页面的分组卡片，避免两处出现两套稍有差异的点击逻辑。
 */
@Composable
fun HistoryEntryOption(
    entry: HistoryEntry,
    avatarModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = remember(entry.key, entry.summary) {
        val raw = entry.summary
        if (raw.contains('<')) org.jsoup.Jsoup.parse(raw).text() else raw
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.disabledSecondary),
        ) {
            AsyncImage(
                model = avatarModel,
                contentDescription = "${entry.title}的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title,
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeTime(entry.visitedAt),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 「我的」页使用的历史预览卡片，最多显示最近五条记录。 */
@Composable
fun HistoryPreviewCard(
    entries: List<HistoryEntry>,
    avatarModel: (String?) -> Any?,
    onOpenHistory: () -> Unit,
    onOpenEntry: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        entries.take(5).forEach { entry ->
            HistoryEntryOption(
                entry = entry,
                avatarModel = avatarModel(entry.target.avatarUrl),
                onClick = { onOpenEntry(entry) },
            )
        }
        if (entries.isEmpty()) {
            Text(
                text = "还没有浏览记录",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

/** 将访问时间格式化成截图中使用的中文相对时间。 */
fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val elapsed = (now - timestamp).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "刚刚"
        elapsed < 3_600_000L -> "${elapsed / 60_000L}分钟前"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L}小时前"
        else -> "${elapsed / 86_400_000L}天前"
    }
}
