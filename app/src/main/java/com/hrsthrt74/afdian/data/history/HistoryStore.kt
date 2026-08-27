package com.hrsthrt74.afdian.data.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "history")

/** 浏览历史本地仓库：记录与头像缓存都不依赖远程服务。 */
class HistoryStore(context: Context) {
    private val context = context.applicationContext
    private val settingsStore = SettingsStore(this.context)
    private val avatarCache = HistoryAvatarCache(FilePaths.historyCacheDirectory(this.context))
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val entriesKey = stringPreferencesKey("entries_json")

    val entries: Flow<List<HistoryEntry>> = context.historyDataStore.data.map { preferences ->
        val raw = preferences[entriesKey] ?: return@map emptyList()
        runCatching { json.decodeFromString(ListSerializer(HistoryEntry.serializer()), raw) }
            .onFailure { AppLog.w("HistoryStore", "历史记录解析失败，已忽略损坏数据", it) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.visitedAt }
    }

    /** 新增或置顶记录；同一创作者/动态不会产生重复条目。 */
    suspend fun recordOpened(target: HistoryTarget) {
        if (target.targetId.isBlank() || !settingsStore.historyEnabled.first()) return
        runCatching {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val old = readEntries()
                    val current = old.firstOrNull { it.key == keyOf(target) }
                    val mergedTarget = mergeTarget(target, current?.target)
                    val next = (old.filterNot { it.key == keyOf(target) } + HistoryEntry(mergedTarget, System.currentTimeMillis()))
                        .sortedByDescending { it.visitedAt }
                        .take(MAX_ENTRIES)
                    writeEntries(next)
                    AppLog.d("HistoryStore", "已记录浏览历史: ${keyOf(target)}")
                }
            }
            // 缓存下载与记录写入分开，头像服务异常不会阻塞历史记录本身。
            withContext(Dispatchers.IO) { avatarCache.ensureCached(target.avatarUrl) }
        }.onFailure { AppLog.w("HistoryStore", "写入浏览历史失败: ${keyOf(target)}", it) }
    }

    /** 用详情接口返回的数据补全标题和头像，但不改变原始访问时间。 */
    suspend fun updateSnapshot(target: HistoryTarget) {
        if (target.targetId.isBlank() || !settingsStore.historyEnabled.first()) return
        runCatching {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val old = readEntries()
                    val current = old.firstOrNull { it.key == keyOf(target) } ?: return@withLock
                    writeEntries(old.map { entry ->
                        if (entry.key == current.key) entry.copy(target = mergeTarget(target, entry.target)) else entry
                    })
                    AppLog.d("HistoryStore", "已更新浏览历史快照: ${keyOf(target)}")
                }
            }
            withContext(Dispatchers.IO) { avatarCache.ensureCached(target.avatarUrl) }
        }.onFailure { AppLog.w("HistoryStore", "更新浏览历史失败: ${keyOf(target)}", it) }
    }

    /** 只删除历史记录头像，不删除历史条目和其他应用缓存。 */
    suspend fun clearAvatarCache() = withContext(Dispatchers.IO) {
        mutex.withLock { avatarCache.clear() }
    }

    /** 清空所有浏览历史条目并同步删除头像缓存。 */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeEntries(emptyList())
            avatarCache.clear()
            AppLog.d("HistoryStore", "已清空浏览历史")
        }
    }

    fun avatarModel(url: String?): Any? = avatarCache.modelFor(url)

    private suspend fun readEntries(): List<HistoryEntry> = context.historyDataStore.data.first()[entriesKey]
        ?.let { runCatching { json.decodeFromString(ListSerializer(HistoryEntry.serializer()), it) }.getOrDefault(emptyList()) }
        .orEmpty()

    private suspend fun writeEntries(entries: List<HistoryEntry>) {
        context.historyDataStore.edit { it[entriesKey] = json.encodeToString(ListSerializer(HistoryEntry.serializer()), entries) }
    }

    private fun keyOf(target: HistoryTarget): String = when (target) {
        is HistoryTarget.CreatorDetailTarget -> "creator:${target.userId}"
        is HistoryTarget.PostDetailTarget -> "post:${target.postId}"
    }

    private fun mergeTarget(new: HistoryTarget, old: HistoryTarget?): HistoryTarget = when {
        new is HistoryTarget.CreatorDetailTarget && old is HistoryTarget.CreatorDetailTarget -> new.copy(
            name = new.name.ifBlank { old.name }, avatarUrl = new.avatarUrl ?: old.avatarUrl,
            urlSlug = new.urlSlug ?: old.urlSlug, coverUrl = new.coverUrl ?: old.coverUrl,
            doing = new.doing ?: old.doing,
        )
        new is HistoryTarget.PostDetailTarget && old is HistoryTarget.PostDetailTarget -> new.copy(
            title = new.title.ifBlank { old.title }, preview = new.preview ?: old.preview,
            creator = mergeCreator(new.creator, old.creator),
        )
        else -> new
    }

    private fun mergeCreator(new: CreatorSnapshot, old: CreatorSnapshot) = new.copy(
        userId = new.userId.ifBlank { old.userId }, name = new.name.ifBlank { old.name },
        avatarUrl = new.avatarUrl ?: old.avatarUrl, urlSlug = new.urlSlug ?: old.urlSlug,
        coverUrl = new.coverUrl ?: old.coverUrl, doing = new.doing ?: old.doing,
    )

    private object FilePaths {
        fun historyCacheDirectory(context: Context) = context.filesDir.resolve("history_record_cache")
    }

    private companion object { const val MAX_ENTRIES = 100 }
}
