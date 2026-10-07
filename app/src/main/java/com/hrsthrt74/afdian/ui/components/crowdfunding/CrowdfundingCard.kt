package com.hrsthrt74.afdian.ui.components.crowdfunding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.FavoriteCrowdfunding
import com.hrsthrt74.afdian.data.content.FavoriteCrowdfundingGoal
import com.hrsthrt74.afdian.data.content.FavoriteItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 众筹卡片，复刻爱发电官方「我收藏的 → 众筹」页（/dashboard/mark-project）的 vm-project-card：
 *
 * ```
 * ┌──────────────────────────┐
 * │ 封面图（2:1 裁切）         │
 * │ ──────────────────────── │
 * │ [众筹中] 《标题》单行省略   │
 * │ ￥1670779.00    已达成!!! │
 * │ ██████████░░░░    5569.3% │
 * │ 8586 人支持  23天…剩余     │
 * └──────────────────────────┘
 * ```
 *
 * 官方状态规则（process 字段）：
 * - 2 众筹中（主色标）：金额行 + 进度条 + 支持人数/剩余倒计时；进度 ≥ 100% 显示「已达成!!!」
 * - 1 即将开始 / 3 已结束（黑标）：只显示「众筹目标 ¥xxx」一行；即将开始额外带主色倒计时
 * - 倒计时到点后官方会整段隐藏，这里同样处理
 *
 * 官方金额/进度条/倒计时数字/绿标均用页面主题色（默认紫），本 App 统一用 Miuix 主题 primary。
 *
 * @param item 收藏条目，众筹详情从 [FavoriteItem.crowdfunding] 读取
 * @param onClick 点击回调；客户端暂无众筹详情页，暂不传（留待详情页接入）
 */
@Composable
fun CrowdfundingCard(
    item: FavoriteItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val cf = item.crowdfunding
    val process = cf?.process ?: 0
    // 官方卡片只取第一个目标
    val goal = cf?.goal_list?.firstOrNull()

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
            // 封面图：官方 640×320 即 2:1，加载前铺占位色
            AsyncImage(
                model = cf?.pic ?: item.image,
                contentDescription = "众筹封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .background(MiuixTheme.colorScheme.disabledSecondary),
            )
            Column(modifier = Modifier.padding(16.dp)) {
                // 状态徽标 + 标题（单行省略）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CrowdfundingTag(process = process)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (process == 2 && goal != null) {
                    // ── 众筹中：金额行 + 进度条 ──
                    Spacer(modifier = Modifier.height(16.dp))
                    // 官方 percent 是「已筹/目标」的倍数（55.697 → 5569.7%）
                    val percent = goal.percent.toDoubleOrNull() ?: 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 已筹金额
                        Text(
                            text = "￥${goal.sum_income}",
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 官方规则：进度 ≥ 100% 才出现「已达成!!!」
                            if (percent >= 1.0) {
                                Text(
                                    text = "已达成!!!",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = String.format(Locale.US, "%.1f%%", percent * 100),
                                style = MiuixTheme.textStyles.footnote1,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    CrowdfundingProgressBar(fraction = percent.toFloat().coerceIn(0f, 1f))
                } else if (goal != null && goal.amount.isNotBlank()) {
                    // ── 即将开始 / 已结束：官方只显示目标金额一行 ──
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "众筹目标 ¥${goal.amount}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (process == 2) {
                    // ── 众筹中：支持人数 + 剩余倒计时（倒计时靠右，到点隐藏） ──
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${cf?.sponsor_count ?: 0} 人支持",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        // 弹性撑开，把倒计时推到行尾
                        Spacer(modifier = Modifier.weight(1f))
                        rememberCountdown(targetSec = cf?.end_time ?: 0)?.let {
                            Text(
                                text = "${it.day}天 ${it.time} 剩余",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                } else if (process == 1) {
                    // ── 即将开始：距开始的倒计时，官方为「紫色数字 + 底色文字」 ──
                    rememberCountdown(targetSec = cf?.begin_time ?: 0)?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary)) { append("${it.day}") }
                                append("天 ")
                                withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary)) { append(it.time) }
                                append(" 后开始")
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

/** 状态徽标：「众筹中」主色标，深色标「即将开始/已结束」（暗色模式底字互换保持可读）。 */
@Composable
private fun CrowdfundingTag(process: Int) {
    if (process !in 1..3) return
    val container = if (process == 2) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.disabledOnSurface
    }
    val content = if (process == 2) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.surface
    Text(
        text = when (process) {
            1 -> "众筹即将开始"
            2 -> "众筹中"
            else -> "众筹已结束"
        },
        fontSize = 12.sp,
        color = content,
        modifier = Modifier
            .background(container, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 官方同款细进度条：5dp 高、2dp 圆角，轨道 10% 前景色，进度带主题主色。 */
@Composable
private fun CrowdfundingProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "crowdfundingProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}

/** 倒计时的拆分结果：天数不补零，时分秒两位补零（官方格式「23天 03:21:29」）。 */
private data class Countdown(val day: Long, val time: String)

/**
 * 距 [targetSec]（秒级时间戳）的倒计时，每秒刷新一次；
 * 未传时间或已到期返回 null，对应官方到点后隐藏倒计时的行为。
 */
@Composable
private fun rememberCountdown(targetSec: Long): Countdown? {
    var nowSec by remember(targetSec) { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(targetSec) {
        while (isActive) {
            delay(1000)
            nowSec = System.currentTimeMillis() / 1000
        }
    }
    val diff = targetSec - nowSec
    if (targetSec <= 0 || diff <= 0) return null
    val time = String.format(
        Locale.US,
        "%02d:%02d:%02d",
        (diff % 86400) / 3600,
        (diff % 3600) / 60,
        diff % 60,
    )
    return Countdown(day = diff / 86400, time = time)
}

// ──────── 预览 ────────

private fun previewItem(
    process: Int,
    percent: String,
    sumIncome: String,
    sponsor: Long = 8586,
) = FavoriteItem(
    id = "preview",
    title = "《纸房子官方美术设定集》预售开启！",
    subtitle = "",
    image = null,
    crowdfunding = FavoriteCrowdfunding(
        crowdfunding_id = "preview",
        pic = null,
        process = process,
        begin_time = if (process == 1) System.currentTimeMillis() / 1000 + 3 * 86400 + 3 * 3600 else 0,
        end_time = if (process == 2) System.currentTimeMillis() / 1000 + 23 * 86400 + 3 * 3600 + 21 * 60 else 0,
        sponsor_count = sponsor,
        goal_list = listOf(FavoriteCrowdfundingGoal(amount = "30000.00", sum_income = sumIncome, percent = percent)),
    ),
)

private fun previewMod() = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)

/** 众筹中 · 已达成 */
@Preview(showBackground = true)
@Composable
private fun CrowdfundingCardReachedPreview() {
    CrowdfundingCard(
        item = previewItem(process = 2, percent = "55.697", sumIncome = "1670779.00"),
        modifier = previewMod(),
    )
}

/** 众筹中 · 未达成 */
@Preview(showBackground = true)
@Composable
private fun CrowdfundingCardOngoingPreview() {
    CrowdfundingCard(
        item = previewItem(process = 2, percent = "0.653", sumIncome = "196.00", sponsor = 3),
        modifier = previewMod(),
    )
}

/** 众筹即将开始 */
@Preview(showBackground = true)
@Composable
private fun CrowdfundingCardUpcomingPreview() {
    CrowdfundingCard(
        item = previewItem(process = 1, percent = "0", sumIncome = "0.00"),
        modifier = previewMod(),
    )
}

/** 众筹已结束 */
@Preview(showBackground = true)
@Composable
private fun CrowdfundingCardEndedPreview() {
    CrowdfundingCard(
        item = previewItem(process = 3, percent = "2.315", sumIncome = "69455.00", sponsor = 512),
        modifier = previewMod(),
    )
}
