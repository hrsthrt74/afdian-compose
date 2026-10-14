package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「近5天收入」模块：展示收入明细页（`/api/my/stat?type=day`）最近 5 天的每日统计。
 *
 * 样式完全沿用收入明细页：表头 + Card 内 6 列数据行、行间 `HorizontalDivider`。
 * 与收入明细页的差别只有两点：
 * 1. 收入明细页的表头在顶栏里（固定不滚动），这里是首页模块没有顶栏，表头放进 Card 内随内容一起滚；
 * 2. 这里固定 5 行、不可翻页，标题点击可跳进收入明细页看完整数据。
 *
 * 数据由 MainViewModel 随首页刷新一起拉取（`recentIncomeRecords`），本模块只负责渲染。
 */
@Composable
fun RecentIncomeModule(context: HomeModuleContentContext) {
    val records = context.recentIncomeRecords
    Column {
        // 标题可点：跳进收入明细页看全量数据（该路由已存在，无需新增页面）
        ClickableSmallTitle(title = "收入明细", onClick = context.onOpenIncomeDetail)
        Card(modifier = Modifier.padding(horizontal = 16.dp)) {
            when {
                // 首次加载中且没有任何历史数据：标准加载组件，不画空表格
                context.loading && records.isEmpty() -> {
                    // 用 App 统一加载组件；小尺寸（24dp）适配卡片内的紧凑高度
                    LoadingComponent(
                        modifier = Modifier.padding(vertical = 64.dp),
                        size = 24.dp,
                    )
                }
                // 无数据（未开通创作者、近 5 天无统计、接口失败）：空态提示，保留模块位置便于用户理解
                records.isEmpty() -> {
                    TablePlaceholder("暂无统计数据")
                }
                // 正常表格：表头 + 数据行整体包在一张 Card 里
                else -> {
                    Column {
                        // 表头（收入明细页里它在顶栏外，首页没顶栏故放进 Card 内顶部）
                        // 表头与数据行必须传同一份列宽，否则两行会错位
                        IncomeStatTableHeader(
                            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp),
                            columnWeights = CompactIncomeStatColumnWeights,
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                        records.forEachIndexed { index, record ->
                            // 首行上方已有表头下方的分割线，不再重复画一条
                            if (index > 0) {
                                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            }
                            // 日期只显示月/日：本模块只有 5 天窗口，年份不提供信息量；
                            // 列宽也用紧凑版：省下的日期列宽度让给「发电金额」等数值列
                            IncomeStatTableRow(
                                record,
                                compactDate = true,
                                columnWeights = CompactIncomeStatColumnWeights,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 表格区的空态文案：居中、脚注字号、三级次色，
 * 与收入明细页的「暂无统计数据」视觉保持一致。（加载中已改用统一的 LoadingComponent）
 */
@Composable
private fun TablePlaceholder(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(PaddingValues(vertical = 24.dp)),
    )
}