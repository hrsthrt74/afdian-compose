package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.runtime.Composable
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.FavoriteItem
import com.hrsthrt74.afdian.data.content.SponsoringItem
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.data.settings.HomeModule
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import com.hrsthrt74.afdian.ui.components.data.DataCardItem
import java.math.BigDecimal

/** 首页模块渲染上下文。模块之间通过明确的数据和回调通信，避免直接依赖 HomeScreen。 */
data class HomeModuleContentContext(
    val sponsoring: List<SponsoringItem>,
    val loading: Boolean,
    val creatorCards: Map<String, DataCardItem>,
    val creatorCharts: Map<String, ChartDataItem>,
    /**
     * dashboard 的未确认金额；每月 1 日结算窗口内等于「上月收入」，供提现提醒卡切换口径。
     * null 表示尚未拿到（缓存无该数据或 dashboard 未返回），由模块自行兜底。
     */
    val unconfirmedAmount: BigDecimal? = null,
    val markedCreators: List<FavoriteItem>,
    val settingsStore: SettingsStore,
    val onOpenCreator: (AfadianUser) -> Unit,
    val onOpenManagement: () -> Unit,
    val onOpenFavorites: () -> Unit,
    val onOpenSponsoring: () -> Unit,
    val historyEntries: List<HistoryEntry>,
    val onOpenHistory: () -> Unit,
    val onOpenHistoryEntry: (HistoryEntry) -> Unit,
    /** 请求进入“创作者数据”整页编辑（首页会隐藏其它模块）。 */
    val onEditCreatorData: () -> Unit,
    /**
     * 「近5天收入」模块的最近 5 天按日统计，由 MainViewModel 随首页刷新一起拉取。
     * 空列表表示尚未拿到数据（加载中、未开通创作者或接口失败），模块据此显示占位/空态。
     */
    val recentIncomeRecords: List<IncomeStatRecord> = emptyList(),
    /** 打开「收入明细」页（近5天收入模块的标题点击入口）。 */
    val onOpenIncomeDetail: () -> Unit = {},
)

/** 模块注册入口。新增模块时在 HomeModuleRegistry 中注册，不需要修改首页容器。 */
@Composable
fun HomeModuleContent(module: HomeModule, context: HomeModuleContentContext) {
    HomeModuleRegistry.spec(module)?.content?.invoke(context)
}
