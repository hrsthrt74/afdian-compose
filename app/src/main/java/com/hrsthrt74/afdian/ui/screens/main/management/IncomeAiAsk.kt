package com.hrsthrt74.afdian.ui.screens.main.management

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrsthrt74.afdian.data.stats.IncomeAiFeedState
import com.hrsthrt74.afdian.data.stats.IncomeAiRange
import com.hrsthrt74.afdian.ui.components.ai.HintText
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.format.DateTimeFormatter

/** 选中态底色/文字色的过渡时长：短补间足够「跟手」，再长会显得拖沓。 */
private const val RangeSegmentColorDurationMs = 150

/** 分段选择器轨道内边距：3dp 让选中块的胶囊边缘与轨道边缘留出呼吸。 */
private val RangeTrackPadding = 3.dp

/** 单个分段的上下内边距：与轨道内边距相加约 40dp 高，和 Miuix 小控件同一档。 */
private val RangeSegmentVerticalPadding = 8.dp

/**
 * 「问 AI」的首轮提问区：时间范围的分段选择器。
 *
 * 为什么不是气泡了：气泡是「已经说出口的话」，而这只是一张还没提交的表单。
 * 旧实现把 5 枚 chip 和「发送」一起塞进一个胶囊气泡里，胶囊套胶囊、选项还会折行，
 * 视觉上分不清「哪些是可选项、哪个是动作」；现在拆成两件清楚的事——
 * 条件在对话区（这里），触发在底部动作栏（「开始分析」）。
 *
 * 交互约定：本组件**不含发送按钮**。发送由 AI 底栏的动作栏负责，
 * 所以它在「未发车 / 失败 / 停止」时出现，跑起来后整块收成一枚摘要 chip。
 *
 * 视觉约定：一整条胶囊轨道（`secondaryContainer`）+ 5 个等宽分段，
 * 选中段用 `primaryContainer` 实心——和 `AppChip`、Miuix `TabRow` 的选中语言一致；
 * 分段等宽而不是按文字自适应，这样永远是一行、不折行、不会有一颗孤零零掉到第二行。
 *
 * @param selected 当前选中的时间范围
 * @param onSelect 选中回调（点已选中的分段也会回调，等于空操作）
 */
@Composable
internal fun IncomeAiComposer(
    selected: IncomeAiRange,
    onSelect: (IncomeAiRange) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Column {
        // 选择器小标题：光看「7天 / 30天」不知道在选什么
        Text(
            text = "分析范围",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        // 分段选择器轨道
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Capsule())
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .padding(RangeTrackPadding),
        ) {
            IncomeAiRange.entries.forEach { range ->
                RangeSegment(
                    text = range.label,
                    selected = range == selected,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(range)
                    },
                )
            }
        }
    }
}

/**
 * 单枚分段：等分宽度、文字居中，只有底色与文字色随选中态变化。
 *
 * @param text 分段文案（「7天」「30天」…）
 * @param selected 是否选中：选中用 `primaryContainer` 实心 + `onPrimaryContainer` 文字
 */
@Composable
private fun RowScope.RangeSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 选中态配色走短补间：切换时底色与文字色一起过渡，不「啪」地硬切
    val background by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(RangeSegmentColorDurationMs),
        label = "rangeSegmentBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.onPrimaryContainer
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        animationSpec = tween(RangeSegmentColorDurationMs),
        label = "rangeSegmentContent",
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .clip(Capsule())
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = RangeSegmentVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        // 分段文案：窄屏/大字号时自动缩字，保证「3个月」「6个月」不被截断
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
            autoSize = TextAutoSize.StepBased(10.sp, 13.sp),
            maxLines = 1,
        )
    }
}

/**
 * 拉数据阶段的进度提示（替换默认的「正在拉取数据…」）。
 *
 * 一次全量取数可能要翻十几二十页，只写一句「正在拉取」会让人以为是卡死；
 * 这里把「已拉多少条、最早到哪天」实时亮出来——数字在涨就说明还在动。
 * 样式走 HintText（body2 灰字），与底栏其余提示同一档。
 */
@Composable
internal fun IncomeAiPreparingHint(feed: IncomeAiFeedState) {
    val text = when {
        // 拉到一半出错：不再空等，用已拿到的部分继续分析
        feed.error != null -> "数据拉取中断，先用已拿到的 ${feed.count} 条继续…"
        feed.complete -> "数据已就绪，正在整理…"
        feed.count == 0 -> "正在拉取数据…"
        else -> buildString {
            append("正在拉取数据…（已 ${feed.count} 条")
            feed.oldestDate?.let { append("，最早到 ${it.format(HINT_DATE_FORMAT)}") }
            append("）")
        }
    }
    HintText(text = text)
}

private val HINT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
