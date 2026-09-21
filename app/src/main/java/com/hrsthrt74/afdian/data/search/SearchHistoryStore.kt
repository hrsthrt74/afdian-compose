package com.hrsthrt74.afdian.data.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

/** 搜索关键词历史：独立存储，但写入受设置页「历史记录」开关控制。 */
class SearchHistoryStore(context: Context) {
    private val context = context.applicationContext
    private val settingsStore = SettingsStore(this.context)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val historyKey = stringPreferencesKey("queries_json")

    val queries: Flow<List<String>> = context.searchHistoryDataStore.data.map { preferences ->
        preferences[historyKey]?.let {
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }
                .onFailure { error -> AppLog.w("SearchHistoryStore", "搜索历史解析失败", error) }
                .getOrDefault(emptyList())
        }.orEmpty()
    }

    suspend fun record(query: String) {
        val keyword = query.trim()
        if (keyword.isBlank() || !settingsStore.historyEnabled.first()) return
        runOperation("写入搜索历史失败") {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val current = readQueries()
                    val next = (listOf(keyword) + current.filterNot { it == keyword }).take(MAX_QUERIES)
                    writeQueries(next)
                    AppLog.d("SearchHistoryStore", "已记录搜索历史: $keyword")
                }
            }
        }
    }

    suspend fun remove(query: String) {
        runOperation("删除搜索历史失败") {
            withContext(Dispatchers.IO) {
                mutex.withLock { writeQueries(readQueries().filterNot { it == query }) }
            }
        }
    }

    suspend fun clear() {
        runOperation("清空搜索历史失败") {
            withContext(Dispatchers.IO) {
                mutex.withLock { writeQueries(emptyList()) }
            }
        }
    }

    private suspend fun readQueries(): List<String> =
        context.searchHistoryDataStore.data.first()[historyKey]?.let {
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrDefault(emptyList())
        }.orEmpty()

    private suspend fun writeQueries(queries: List<String>) {
        context.searchHistoryDataStore.edit {
            it[historyKey] = json.encodeToString(ListSerializer(String.serializer()), queries)
        }
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
    }
}
