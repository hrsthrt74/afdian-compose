package com.hrsthrt74.afdian.data.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.CurrentAccount
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

/**
 * 搜索关键词历史：独立存储，但写入受设置页「历史记录」开关控制。
 *
 * 与浏览历史一样按账号隔离：每个账号占一个 `queries_json_<user_id>` 分区，
 * 读写的始终是当前账号那一份。
 */
class SearchHistoryStore(context: Context) {
    private val context = context.applicationContext
    private val settingsStore = SettingsStore(this.context)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** 当前账号的搜索历史；账号未确定或已登出时为空列表。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val queries: Flow<List<String>> = CurrentAccount.userId.flatMapLatest { owner ->
        if (owner.isNullOrBlank()) {
            flowOf(emptyList<String>())
        } else {
            context.searchHistoryDataStore.data.map { preferences -> decode(preferences[queriesKey(owner)]) }
        }
    }

    suspend fun record(query: String) {
        val keyword = query.trim()
        if (keyword.isBlank() || !settingsStore.historyEnabled.first()) return
        // 账号身份未就绪时放弃写入，避免把关键词记到错误的分区
        val owner = requireOwner("记录搜索历史") ?: return
        runOperation("写入搜索历史失败") {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val current = readQueries(owner)
                    val next = (listOf(keyword) + current.filterNot { it == keyword }).take(MAX_QUERIES)
                    writeQueries(owner, next)
                    AppLog.d("SearchHistoryStore", "已记录搜索历史: $keyword")
                }
            }
        }
    }

    suspend fun remove(query: String) {
        val owner = requireOwner("删除搜索历史") ?: return
        runOperation("删除搜索历史失败") {
            withContext(Dispatchers.IO) {
                mutex.withLock { writeQueries(owner, readQueries(owner).filterNot { it == query }) }
            }
        }
    }

    /** 清空**当前账号**的搜索历史（搜索面板的「清空」）。 */
    suspend fun clear() {
        val owner = requireOwner("清空搜索历史") ?: return
        runOperation("清空搜索历史失败") {
            withContext(Dispatchers.IO) {
                mutex.withLock { writeQueries(owner, emptyList()) }
            }
        }
    }

    /** 清空**所有账号**的搜索历史（存储管理页的「清理」）。 */
    suspend fun clearAllAccounts() {
        runOperation("清空全部搜索历史失败") {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    context.searchHistoryDataStore.edit { preferences ->
                        preferences.asMap().keys
                            .filter { it.name.startsWith(QUERIES_KEY_PREFIX) }
                            .forEach { preferences.remove(it) }
                        preferences.remove(LEGACY_QUERIES_KEY)
                    }
                    AppLog.d("SearchHistoryStore", "已清空全部账号的搜索历史")
                }
            }
        }
    }

    /**
     * 把升级前「单账号、无归属」的搜索历史迁到指定账号名下。
     * 幂等：旧键不存在时直接返回；目标账号已有记录时只删旧键，不覆盖。
     */
    suspend fun migrateLegacy(ownerUserId: String) = withContext(Dispatchers.IO) {
        val owner = ownerUserId.takeIf { it.isNotBlank() } ?: return@withContext
        mutex.withLock {
            val legacy = context.searchHistoryDataStore.data.first()[LEGACY_QUERIES_KEY] ?: return@withLock
            context.searchHistoryDataStore.edit { preferences ->
                if (preferences[queriesKey(owner)] == null) {
                    preferences[queriesKey(owner)] = legacy
                    AppLog.d("SearchHistoryStore", "已把旧版搜索历史迁移到当前账号")
                } else {
                    AppLog.d("SearchHistoryStore", "当前账号已有搜索历史，丢弃无归属的旧版数据")
                }
                preferences.remove(LEGACY_QUERIES_KEY)
            }
        }
    }

    /** 取当前账号；未就绪时打一条日志并返回 null，由调用方直接放弃本次操作。 */
    private fun requireOwner(action: String): String? =
        CurrentAccount.userId.value?.takeIf { it.isNotBlank() }
            ?: run {
                AppLog.d("SearchHistoryStore", "账号身份未就绪，跳过$action")
                null
            }

    private fun queriesKey(ownerUserId: String) = stringPreferencesKey("$QUERIES_KEY_PREFIX$ownerUserId")

    private suspend fun readQueries(ownerUserId: String): List<String> =
        decode(context.searchHistoryDataStore.data.first()[queriesKey(ownerUserId)])

    private suspend fun writeQueries(ownerUserId: String, queries: List<String>) {
        context.searchHistoryDataStore.edit {
            it[queriesKey(ownerUserId)] = json.encodeToString(ListSerializer(String.serializer()), queries)
        }
    }

    /** 把分区里的 JSON 解成列表；损坏数据只告警一次并当作空列表。 */
    private fun decode(raw: String?): List<String> {
        if (raw == null) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .onFailure { error -> AppLog.w("SearchHistoryStore", "搜索历史解析失败", error) }
            .getOrDefault(emptyList())
    }

    private suspend inline fun runOperation(
        message: String,
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.w("SearchHistoryStore", message, error)
        }
    }

    private companion object {
        const val MAX_QUERIES = 20

        /** 每个账号的分区键前缀，完整键形如 `queries_json_<user_id>` */
        const val QUERIES_KEY_PREFIX = "queries_json_"

        /** 升级前的单账号键；迁移后即删除 */
        val LEGACY_QUERIES_KEY = stringPreferencesKey("queries_json")
    }
}
