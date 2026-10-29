package com.hrsthrt74.afdian.ui.components.ai

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * AI 底栏的视觉 token 与状态零件。
 *
 * 间距 / 形状值统一收在 [AiSheetSpacing]：旧实现把 8/12/14/16/10/6/5dp 散落在三个文件里，
 * 改一处样式要满地找魔法数。生成底栏与回看底栏共用同一组值，两套版式才不会漂移。
 */

/**
 * AI 底栏的间距与形状 token。
 *
 * 命名按「用在哪」而不是「是多少」：调用处读起来是语义（SectionGap），
 * 调整视觉时只改这里，不必逐个文件找数字。
 */
internal object AiSheetSpacing {

    // ── 对话区（可滚动区）──
    /** 滚动区顶部内边距（水平不动：Sheet 自带 24dp insideMargin）。 */
    val ContentTop = 8.dp

    /** 滚动区底部内边距。 */
    val ContentBottom = 16.dp

    /** 用户气泡 → 模型侧内容。 */
    val SectionGap = 14.dp

    /** 思考块 → 正文：内容区可见（小窗/展开）时的间距。 */
    val ThinkGapExpanded = 10.dp

    /** 思考块 → 正文：收成一行标题时贴紧。 */
    val ThinkGapCollapsed = 6.dp

    /** 历史元信息行下方。 */
    val MetaGap = 12.dp

    // ── 状态卡 ──
    /** 正文 → 失败/停止状态卡。 */
    val StatusCardGap = 12.dp

    /** 状态卡内边距。 */
    val StatusCardPadding = 12.dp

    // ── 底部动作栏 ──
    /** 动作栏下边距（还需叠加 navigationBarsPadding 让开手势条）。 */
    val ActionRowBottom = 12.dp

    /** 动作栏上方的渐隐高度：上透明 → 下 Sheet 底色，让滚到底的正文淡出而不是被硬切。 */
    val ActionBarFade = 28.dp

    /**
     * 对话区底部要让给「渐隐 + 动作栏」的高度（不含手势条 inset）。
     *
     * 动作栏是叠在对话区之上的，所以滚动内容必须自己留出这块空白，
     * 否则最后一行会被盖住。数值 = [ActionBarFade] + 动作栏自身高度 + 一点余量；
     * 宁可多留几 dp（只是空白），也不能少留（正文被吃掉）。
     */
    val ActionBarReserve = 92.dp

    // ── 胶囊 chip（AiChip / RangeChip 共用）──
    /** chip 内边距。 */
    val ChipPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)

    // ── 思考块 ──
    /** 思考块标题行的按压圆角。 */
    val ThinkingTitleCorner = 8.dp

    /** 思考块左侧竖线宽。 */
    val ThinkingBarWidth = 2.dp

    /** 思考块左侧竖线圆角。 */
    val ThinkingBarCorner = 1.dp

    /** 思考正文补档行高（footnote2 默认无 lineHeight，CJK 小字偏挤）。 */
    val ThinkingLineHeight = 16.sp

    /** 思考块折叠小窗的可见行数：超出部分在窗口里滚动。 */
    const val ThinkingWindowLines = 6
}

/**
 * 失败 / 停止的状态卡：挂在正文下方的一句补充说明。
 *
 * 为什么是小卡片而不是裸 Text：说明散落在长报告末尾容易看不见，
 * 卡片底色（弹层内统一 overlayColors）把它和正文分出层次，一眼能找到「出什么事了」。
 * 不用 ErrorStateView：那是整页三态，此处正文可能有半截内容，重试在底部动作区。
 *
 * @param isError true = 失败（错误色）；false = 停止等中性提示（摘要灰）
 */
@Composable
internal fun AiStatusCard(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    // 弹层内 Card 必须 overlayColors：默认色与 Sheet 背景同值会「看不见」
    Card(
        colors = AfdianCardDefaults.overlayColors(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = if (isError) {
                MiuixTheme.colorScheme.error
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            modifier = Modifier.fillMaxWidth().padding(AiSheetSpacing.StatusCardPadding),
        )
    }
}
