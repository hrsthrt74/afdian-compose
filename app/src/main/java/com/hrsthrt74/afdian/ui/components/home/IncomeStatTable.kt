package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 收入统计表格的表头与数据行。
 *
 * 从「收入明细」页（`IncomeDetailScreen`）原样提取，供首页「近5天收入」模块复用，
 * 两处共用同一套列宽与排版，改样式只需改这里。
 */

/**
 * 收入明细页的列宽分配：日期列最宽——要放下 `26/10/16 五`（短年份 + 半角空格 + 星期汉字，
 * 12sp 下约 59–68dp），权重和 6.3 时 360dp 屏上是 72.4dp，能容下最宽的字体；
 * 其余四个数值列等宽（48.3dp，仍宽于 11sp 的四字表头 44dp），最后一列（老客数）只有三个字，略窄。
 * 首页「近5天收入」模块用另一套（见 [CompactIncomeStatColumnWeights]），不要改这里。
 */
val IncomeStatColumnWeights = listOf(1.5f, 1f, 1f, 1f, 1f, 0.8f)

/**
 * 首页「近5天收入」模块的列宽分配：日期只需放下 `09/06 五`（12sp 约 46.6dp），比收入明细页窄；
 * 其余数值列等宽——「发电金额」可能出现 `1234.56` 这类长数字，「老客数」只有三个字、最窄。
 * 调权重时守两条底线：日期列 ≥ 46.6dp（否则被截），四个数值列 ≥ 11sp 四字表头的 44dp（否则折行）。
 */
val CompactIncomeStatColumnWeights = listOf(1.1f, 1f, 1f, 1f, 1f, 0.8f)

/** 表格六列的标题文案。 */
private val ColumnTitles = listOf("日期", "访问人数", "发电金额", "发电次数", "发电人数", "老客数")

/**
 * 固定表头行：六列统计口径标题。
 *
 * @param columnWeights 六列宽度分配，必须与数据行传入的是同一份，否则表头与数据错位。
 */
@Composable
fun IncomeStatTableHeader(
    modifier: Modifier = Modifier,
    columnWeights: List<Float> = IncomeStatColumnWeights,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        ColumnTitles.forEachIndexed { index, title ->
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(columnWeights[index]),
            )
        }
    }
}

/**
 * 表格数据行：一行一条统计记录，金额保留两位小数。
 *
 * @param compactDate 是否用「去掉年份」的紧凑日期。
 *   收入明细页展示短年份日期（26/09/06 五），传 false（默认）；
 *   首页「近5天收入」只有 5 天窗口，年份没有信息量，传 true 省下的宽度留给数值列。
 * @param showWeekday 是否在日期后附带星期（`26/10/16 五` / `09/06 五`）。
 *   默认 true：首页模块与收入明细「按日」都要看星期几。
 *   按周/按月的日期是周期起始日（周一 / 1 号），星期没有信息量，由收入明细页显式传 false。
 * @param columnWeights 六列宽度分配，必须与表头传入的是同一份，否则表头与数据错位。
 * @param insidePadding 行内边距，收入明细页与首页模块共用同一套数值。
 */
@Composable
fun IncomeStatTableRow(
    record: IncomeStatRecord,
    modifier: Modifier = Modifier,
    compactDate: Boolean = false,
    showWeekday: Boolean = true,
    columnWeights: List<Float> = IncomeStatColumnWeights,
    insidePadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
) {
    // 格式化金额：直接用 BigDecimal 的两位小数字符串（接口本身形如 13.99）
    val cells = listOf(
        // 日期：首页用紧凑的 月/日，收入明细用短年份 年/月/日；showWeekday 关掉时都不拼星期
        when {
            compactDate -> if (showWeekday) record.displayMonthDayWithWeekday else record.displayMonthDay
            showWeekday -> record.displayShortYearDateWithWeekday
            else -> record.displayShortYearDate
        },
        record.uv.toString(),
        record.amount.toPlainString(),
        record.orderCount.toString(),
        record.userCount.toString(),
        record.oldUserCount.toString(),
    )
    Row(modifier = modifier.fillMaxWidth().padding(insidePadding)) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontFeatureSettings = "tnum"
                ),
                // 日期列用次级文字色弱化，其余数值列用默认前景色
                color = if (index == 0) MiuixTheme.colorScheme.onSurfaceVariantSummary else Color.Unspecified,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(columnWeights[index]),
            )
        }
    }
}

