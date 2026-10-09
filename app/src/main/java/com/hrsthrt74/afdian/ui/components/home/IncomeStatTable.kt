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
 * 收入明细页的列宽分配：日期列略宽（要放 `2026/09/06`），最后一列（老客数）略窄。
 * 首页「近5天收入」模块用另一套（见 [CompactIncomeStatColumnWeights]），不要改这里。
 */
val IncomeStatColumnWeights = listOf(1.4f, 1f, 1f, 1f, 1f, 0.8f)

/**
 * 首页「近5天收入」模块的列宽分配：日期只有 `09/06`，不再需要为年份留位置，
 * 因此把宽度让给数值列——其中「发电金额」可能出现 `1234.56` 这类长数字，权重最高；
 * 「老客数」通常只有个位数，最窄。
 */
val CompactIncomeStatColumnWeights = listOf(0.7f, 1f, 1.2f, 1f, 1f, 0.8f)

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
 * @param compactDate 是否用「去掉年份」的紧凑日期（09/06）。
 *   收入明细页展示全量历史、需要年份，故传 false（默认）；首页「近5天收入」只有 5 天窗口，
 *   年份没有信息量，传 true 省下的宽度留给数值列。
 * @param columnWeights 六列宽度分配，必须与表头传入的是同一份，否则表头与数据错位。
 * @param insidePadding 行内边距，收入明细页与首页模块共用同一套数值。
 */
@Composable
fun IncomeStatTableRow(
    record: IncomeStatRecord,
    modifier: Modifier = Modifier,
    compactDate: Boolean = false,
    columnWeights: List<Float> = IncomeStatColumnWeights,
    insidePadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
) {
    // 格式化金额：直接用 BigDecimal 的两位小数字符串（接口本身形如 13.99）
    val cells = listOf(
        if (compactDate) record.displayMonthDay else record.displayDate,
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

