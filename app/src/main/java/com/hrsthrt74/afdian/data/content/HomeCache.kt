package com.hrsthrt74.afdian.data.content

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.stats.ChartDataItem
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 首页首屏快照缓存，单独使用 DataStore，避免和资料缓存互相影响。 */
private val Context.homeDataStore: DataStore<Preferences> by preferencesDataStore(name = "home")

/**
 * 首页快照的 JSON 配置：忽略未知字段 + 把 null 收敛为字段默认值，
 * 保证给快照新增字段后，旧版本写入的 JSON 仍能解析（缺字段走默认值）。
 * 抽成文件级常量，让单元测试与生产共用同一份配置，避免两边漂移。
 */
internal val homeCacheJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

@Serializable
data class HomeCacheSnapshot(
    val ownerUserId: String = "",
    val sponsoring: List<SponsoringItem> = emptyList(),
    val markedCreators: List<FavoriteItem> = emptyList(),
    val creatorCards: List<HomeCacheCard> = emptyList(),
    /** 创作者图表数据，key 为 ChartType.storageValue；旧快照没有该字段时反序列化为空 Map。 */
    val creatorCharts: Map<String, ChartDataItem> = emptyMap(),
)

@Serializable
data class HomeCacheCard(
    val id: String,
    val title: String,
    val value: String,
    val subtitle: String? = null,
)

class HomeCache(private val context: Context) {
    private val json = homeCacheJson
    private val cacheKey = stringPreferencesKey("home_snapshot_json")

    suspend fun load(ownerUserId: String?): HomeCacheSnapshot? {
        val raw = context.homeDataStore.data.first()[cacheKey] ?: run {
            AppLog.d("HomeCache", "首页缓存未命中")
            return null
        }
        return runCatching { json.decodeFromString(HomeCacheSnapshot.serializer(), raw) }
            .onSuccess { snapshot ->
                if (!ownerUserId.isNullOrBlank() && snapshot.ownerUserId != ownerUserId) {
                    AppLog.w("HomeCache", "首页缓存账号不匹配，忽略旧缓存")
                } else {
                    AppLog.d("HomeCache", "首页缓存命中，发电=${snapshot.sponsoring.size}，收藏=${snapshot.markedCreators.size}，卡片=${snapshot.creatorCards.size}，图表=${snapshot.creatorCharts.size}")
                }
            }
            .onFailure { AppLog.w("HomeCache", "首页缓存解析失败，忽略缓存", it) }
            .getOrNull()
            ?.takeIf { ownerUserId.isNullOrBlank() || it.ownerUserId == ownerUserId }
    }

    suspend fun save(snapshot: HomeCacheSnapshot) {
        runCatching {
            context.homeDataStore.edit { it[cacheKey] = json.encodeToString(HomeCacheSnapshot.serializer(), snapshot) }
            AppLog.d("HomeCache", "首页快照已写入缓存")
        }.onFailure { AppLog.w("HomeCache", "首页缓存写入失败", it) }
    }

    suspend fun clear() {
        runCatching { context.homeDataStore.edit { it.remove(cacheKey) } }
            .onFailure { AppLog.w("HomeCache", "清理首页缓存失败", it) }
    }
}
