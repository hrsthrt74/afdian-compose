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
import com.hrsthrt74.afdian.ui.navigation.Route
import java.math.BigDecimal

/** 首页模块渲染上下文。模块之间通过明确的数据和回调通信，避免直接依赖 HomeScreen。 */
data class HomeModuleContentContext(
    val sponsoring: List<SponsoringItem>,
    val loading: Boolean,
    val creatorCards: Map<String, DataCardItem>,
    val creatorCharts: Map<String, ChartDataItem>,
    /**
     * dashboard 的未确认金额（抽成前）；结算期（每月 1 日 0:00–10:00 或服务端仍在结算）内
     * 它就是待结算的「上月收入」的权威口径，供提现提醒卡切换上月口径。
     * null 表示尚未拿到（缓存无该数据或 dashboard 未返回），由模块自行兜底。
     */
    val unconfirmedAmount: BigDecimal? = null,
    /**
     * 服务端是否报告「结算中」（dashboard.balance 返回「结算中，请耐心等待」这类文案）。
     * true 表示当前处于每月 1 日的结算期；与本地时钟一起决定提现提醒卡的口径。
     * 不落缓存，冷启动由本地时钟 + 缓存的 [unconfirmedAmount] 兜住。
     */
    val isBalanceSettling: Boolean = false,
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
    /** 通用路由跳转：「快捷方式」模块点任意预设入口都走它。 */
    val onNavigate: (Route) -> Unit,
    /** 请求打开「快捷方式」的自定义面板。 */
    val onEditQuickEntries: () -> Unit,
    /**
     * 「近5天收入」模块的最近 5 天按日统计，由 MainViewModel 随首页刷新一起拉取。
     * 空列表表示尚未拿到数据（加载中、未开通创作者或接口失败），模块据此显示占位/空态。
     */
    val recentIncomeRecords: List<IncomeStatRecord> = emptyList(),
    /** 打开「收入明细」页（近5天收入模块的标题点击入口）。 */
    val onOpenIncomeDetail: () -> Unit = {},
    /**
     * 「快捷方式」模块已选中的入口（预设 storageValue，顺序即展示顺序）。
     *
     * 由首页容器统一订阅后下发，而不是让模块自己读 DataStore：
     * 容器要等它到齐才肯铺整个列表，两边各订阅一份会出现「容器已铺、模块还是空壳」的错位帧。
     * null 表示尚未读到真值。
     */
    val quickEntryKeys: List<String>? = null,
)

/** 模块注册入口。新增模块时在 HomeModuleRegistry 中注册，不需要修改首页容器。 */
@Composable
fun HomeModuleContent(module: HomeModule, context: HomeModuleContentContext) {
    HomeModuleRegistry.spec(module)?.content?.invoke(context)
}
