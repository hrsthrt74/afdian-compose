package com.hrsthrt74.afdian.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.ai.AiSheetSpacing.ThinkingWindowLines
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * AI 思考块：DeepSeek 式「灰字 + 左竖线、可开合」的思考过程展示。
 *
 * 从 AiMessageParts.kt 拆出来的重件：标题开合 + 折叠小窗 + 贴底跟随。
 * 贴底跟随与手势接管抽成了 [Modifier.streamAutoFollow]——那是「怎么滚」的策略，
 * 本文件剩下的都是「怎么画」。
 */

/**
 * 流式小窗「贴底跟随」的状态。
 *
 * 只有两个写入口：[Modifier.streamAutoFollow] 挂上时恢复跟随、手势接管时暂停/恢复，
 * 避免流式刷新把正在回看的用户拽走。
 */
@Stable
internal class StreamAutoFollowState {
    /** 是否跟随内容增长贴到最底。用户上滑回看时暂停，停回底部时恢复。 */
    internal var followBottom by mutableStateOf(true)
}

/** 创建一个贴底跟随状态（在组合里记住，随小窗的生命周期走）。 */
@Composable
internal fun rememberStreamAutoFollow(): StreamAutoFollowState = remember { StreamAutoFollowState() }

/**
 * 流式小窗的贴底跟随策略：内容增长自动滚到最底；用户一碰就暂停，
 * 松手（含惯性滚动）停稳后仍停在底部才恢复跟随。
 *
 * 挂在可滚动小窗上（与 `verticalScroll` 一起）；Modifier 卸载 = 小窗收起，跟随循环自动结束，
 * 重新挂上（展开又收起）时恢复跟随。
 *
 * @param state 跟随状态（[rememberStreamAutoFollow]）
 * @param scrollState 小窗自己的滚动状态
 */
internal fun Modifier.streamAutoFollow(
    state: StreamAutoFollowState,
    scrollState: ScrollState,
): Modifier = composed {
    // 贴底跟随：内容增长 = maxValue 变化，跟随中就把窗口贴到最底（value<max 才滚，避免空转）
    LaunchedEffect(state, scrollState) {
        // 重新进入小窗（含展开又收起）：恢复跟随
        state.followBottom = true
        snapshotFlow { scrollState.maxValue }.collect { maxValue ->
            if (state.followBottom && scrollState.value < maxValue) {
                scrollState.scrollTo(maxValue)
            }
        }
    }
    this.pointerInput(state, scrollState) {
        // 手势接管检测：一碰窗口就暂停贴底跟随，
        // 松手（含惯性滚动）停稳后仍停在底部才恢复。
        // awaitPointerEventScope 是受限挂起作用域，
        // 只放指针等待；等惯性停稳等普通挂起逻辑放在 scope 外。
        while (true) {
            awaitPointerEventScope {
                // 手一碰窗口就暂停跟随，流式刷新不再抢滚动
                awaitFirstDown(requireUnconsumed = false)
                state.followBottom = false
                // 等这一轮手势的所有手指都抬起
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
            }
            // 回到普通挂起上下文：等惯性滚动停稳，
            // 停在底部 = 看完了，恢复跟随；停在半路 = 还在回看
            snapshotFlow { scrollState.isScrollInProgress }.first { !it }
            state.followBottom = scrollState.value >= scrollState.maxValue - 4
        }
    }
}

/**
 * 思考块：灰字 + 左侧竖线，标题可点开合。
 *
 * 形态对齐 DeepSeek 移动端——思考中标题是「正在思考」，正文一开始就定格成
 * 「已思考（用时 N 秒）」。展开逻辑分两段：
 * - 思考进行中：默认**折叠成 6 行小窗**，思考内容在窗口里滚动、自动贴底跟随最新思路
 *   （用户上滑回看时暂停跟随，不打扰）；点标题可展开为不限行数，随外层滚动看全文。
 * - 思考结束后：默认收成一行标题；再点标题展开可回看全部思考。
 * 正文（[text]）为空时说明还一个思考 token 都没到，交给 WaitingHint 显示秒表。
 *
 * 历史回看也复用它：传 `seconds = 记录里的思考用时` 即自动进入「已思考（用时 N 秒）」形态，
 * 默认收起、点开才看全文，正好是回看语义。
 */
@Composable
internal fun ThinkingBlock(
    text: String,
    seconds: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 思考正文样式：footnote2 默认不带 lineHeight（跟随系统字体默认行距，CJK 小字偏挤），
    // 这里补一档行高——既更易读，也让折叠小窗的「6 行」高度能按 行高×6 精确计算
    val thinkingStyle = MiuixTheme.textStyles.footnote2
        .copy(lineHeight = AiSheetSpacing.ThinkingLineHeight)
    // 折叠小窗的高度上限 = 6 行正文（sp→dp 已含用户字号缩放）
    val collapsedWindowHeight = with(LocalDensity.current) {
        thinkingStyle.lineHeight.toDp() * ThinkingWindowLines
    }
    // 是否仍在思考：用时没定格 = 思考中 / 等首包
    val thinking = seconds == 0
    // 折叠小窗生效条件：思考中 + 用户没展开 + 已有思考文字（没文字时只有一行等待提示，不用滚）
    val windowed = thinking && !expanded && text.isNotEmpty()
    // 内容区是否显示：思考中始终显示（折叠=6 行小窗）；思考结束后只有展开才显示
    val showContent = thinking || expanded

    // 贴底跟随状态与小窗滚动状态（小窗卸载后重建即回到默认跟随）
    val follow = rememberStreamAutoFollow()
    val thinkingScroll = rememberScrollState()

    // 箭头：收起时朝右、展开时朝下（ChevronForward 默认朝右，转 90° 即朝下）
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "thinkingChevron",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题行：点按开合
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(AiSheetSpacing.ThinkingTitleCorner))
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 标题文案：思考中 / 已思考（用时 N 秒）
            Text(
                text = if (seconds > 0) thinkingDurationText(seconds) else "正在思考",
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariantSummary,
            )
            // 开合箭头
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(14.dp)
                    .rotate(chevronRotation),
            )
        }
        // 思考内容区（展开/收起动画）
        AnimatedVisibility(
            visible = showContent,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // IntrinsicSize.Min 让左侧竖线与文字同高（文字多行时竖线跟着拉长）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(top = 2.dp),
            ) {
                // 左侧竖线
                Box(
                    modifier = Modifier
                        .width(AiSheetSpacing.ThinkingBarWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(AiSheetSpacing.ThinkingBarCorner))
                        .background(scheme.dividerLine),
                )
                if (text.isEmpty()) {
                    // 等首包秒表
                    WaitingHint(
                        modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                    )
                } else {
                    // 思考正文
                    Text(
                        text = text,
                        style = thinkingStyle,
                        color = scheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .padding(bottom = 4.dp)
                            .then(
                                // 折叠小窗：限高 6 行，超出部分在窗口里滚动（含贴底跟随）；
                                // 展开态不限行数，交给外层滚动看全文
                                if (windowed) {
                                    Modifier
                                        .heightIn(max = collapsedWindowHeight)
                                        .verticalScroll(thinkingScroll)
                                        .streamAutoFollow(follow, thinkingScroll)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 等待首包的秒表提示。
 *
 * 秒数状态刻意收在这个小组件里：若放在外面，每秒一次的自增会让整段 Markdown 跟着重组
 * （每次都要重解析整篇），长报告时肉眼可见地卡。这里独立成节点后，每秒只刷自己这一行。
 * 前 2 秒不报数字，避免「0s / 1s」闪一下反而显慢。
 */
@Composable
private fun WaitingHint(modifier: Modifier = Modifier) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            seconds++
        }
    }
    HintText(
        text = if (seconds >= 2) "正在等待模型响应…（${seconds}s）" else "正在等待模型响应…",
        modifier = modifier,
    )
}

/** 思考用时的展示：不足 1 分钟只报秒，超过 1 分钟拆成分 + 秒。 */
internal fun thinkingDurationText(seconds: Int): String =
    if (seconds < 60) "已思考（用时 $seconds 秒）"
    else "已思考（用时 ${seconds / 60} 分 ${seconds % 60} 秒）"
