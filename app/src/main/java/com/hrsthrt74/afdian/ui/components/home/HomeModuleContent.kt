package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.runtime.Composable
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.FavoriteItem
import com.hrsthrt74.afdian.data.content.SponsoringItem
import com.hrsthrt74.afdian.data.history.HistoryEntry
import com.hrsthrt74.afdian.data.settings.HomeModule
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import com.hrsthrt74.afdian.ui.components.data.DataCardItem

/** 首页模块渲染上下文。模块之间通过明确的数据和回调通信，避免直接依赖 HomeScreen。 */
data class HomeModuleContentContext(
    val sponsoring: List<SponsoringItem>,
    val loading: Boolean,
    val creatorCards: Map<String, DataCardItem>,
    val creatorCharts: Map<String, ChartDataItem>,
    val markedCreators: List<FavoriteItem>,
    val settingsStore: SettingsStore,
    val onOpenCreator: (AfadianUser) -> Unit,
    val onOpenManagement: () -> Unit,
    val onOpenFavorites: () -> Unit,
    val onOpenSponsoring: () -> Unit,
    val historyEntries: List<HistoryEntry>,
    val historyAvatarModel: (String?) -> Any?,
    val onOpenHistory: () -> Unit,
    val onOpenHistoryEntry: (HistoryEntry) -> Unit,
    /** 请求进入“创作者数据”整页编辑（首页会隐藏其它模块）。 */
    val onEditCreatorData: () -> Unit,
)

/** 模块注册入口。新增模块时在 HomeModuleRegistry 中注册，不需要修改首页容器。 */
@Composable
fun HomeModuleContent(module: HomeModule, context: HomeModuleContentContext) {
    HomeModuleRegistry.spec(module)?.content?.invoke(context)
}
