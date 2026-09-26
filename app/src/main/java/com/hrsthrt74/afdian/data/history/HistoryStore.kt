package com.hrsthrt74.afdian.data.history

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
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "history")

/**
 * 浏览历史本地仓库。
 *
 * 只保存浏览过的目标（文字与头像**链接**），**不下载头像**：
 * 头像由 UI 层带着链接交给 Coil 按需加载，磁盘缓存落在 Coil 的全局缓存里，
 * 因此这里不再维护任何图片文件。旧版本曾把头像预下载到专属目录
 * （[FilePaths.legacyAvatarCacheDirectory]），那批残留由 [clearLegacyAvatarCache] 负责回收。
 */
class HistoryStore(context: Context) {
    private val context = context.applicationContext
    private val settingsStore = SettingsStore(this.context)
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
        runHistoryOperation("写入浏览历史失败: ${keyOf(target)}") {
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
        }
    }

    /** 用详情接口返回的数据补全标题与头像链接，但不改变原始访问时间。 */
    suspend fun updateSnapshot(target: HistoryTarget) {
        if (target.targetId.isBlank() || !settingsStore.historyEnabled.first()) return
        runHistoryOperation("更新浏览历史失败: ${keyOf(target)}") {
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
        }
    }

    /**
     * 回收旧版本「历史专属头像缓存」目录里遗留的图片文件。
     *
     * 新版本不再往这个目录写任何东西，所以它只可能装着升级前留下的残留。
     * 由 [HistoryViewModel] 在启动时调用一次，让老用户不必手动清理就能拿回这部分空间。
     */
    suspend fun clearLegacyAvatarCache() = withContext(Dispatchers.IO) {
        mutex.withLock { deleteLegacyAvatarCache() }
    }

    /** 清空所有浏览历史条目，并顺带回收旧版遗留的头像缓存目录。 */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeEntries(emptyList())
            deleteLegacyAvatarCache()
            AppLog.d("HistoryStore", "已清空浏览历史")
        }
    }

    /**
     * 删除旧版遗留的头像缓存目录。
     *
     * 目录不存在时直接返回 —— 对绝大多数（新安装的）用户来说这就是一次 `exists()` 判断，开销可忽略。
     */
    private fun deleteLegacyAvatarCache() {
        val directory = FilePaths.legacyAvatarCacheDirectory(context)
        if (!directory.exists()) return
        runCatching {
            if (!directory.deleteRecursively()) error("目录删除失败")
            AppLog.d("HistoryStore", "已回收旧版历史头像缓存目录")
        }.onFailure { AppLog.w("HistoryStore", "回收旧版历史头像缓存目录失败", it) }
    }

    private suspend fun readEntries(): List<HistoryEntry> = context.historyDataStore.data.first()[entriesKey]        ?.let { runCatching { json.decodeFromString(ListSerializer(HistoryEntry.serializer()), it) }.getOrDefault(emptyList()) }
        .orEmpty()

    private suspend fun writeEntries(entries: List<HistoryEntry>) {
        context.historyDataStore.edit { it[entriesKey] = json.encodeToString(ListSerializer(HistoryEntry.serializer()), entries) }
    }

    /** 本地写入失败时保留错误态，但页面取消必须继续向上传播。 */
    private suspend inline fun runHistoryOperation(
        message: String,
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.w("HistoryStore", message, error)
        }
    }

    private fun keyOf(target: HistoryTarget): String = when (target) {
        is HistoryTarget.CreatorDetailTarget -> "creator:${target.userId}"
        is HistoryTarget.PostDetailTarget -> "post:${target.postId}"
        is HistoryTarget.MessageConversationTarget -> "message:${target.userId}"
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
        // 私信会话：只合并名字和头像，其余字段不存在
        new is HistoryTarget.MessageConversationTarget && old is HistoryTarget.MessageConversationTarget -> new.copy(
            name = new.name.ifBlank { old.name }, avatarUrl = new.avatarUrl ?: old.avatarUrl,
        )
        else -> new
    }

    private fun mergeCreator(new: CreatorSnapshot, old: CreatorSnapshot) = new.copy(
        userId = new.userId.ifBlank { old.userId }, name = new.name.ifBlank { old.name },
        avatarUrl = new.avatarUrl ?: old.avatarUrl, urlSlug = new.urlSlug ?: old.urlSlug,
        coverUrl = new.coverUrl ?: old.coverUrl, doing = new.doing ?: old.doing,
    )

    private object FilePaths {
        /**
         * 旧版本「历史专属头像缓存」目录。
         *
         * 旧设计把头像预下载到这里、刻意不用 Coil 全局缓存，为的是在设置页里能精确删除。
         * 现在改为只存链接、按需加载，本目录不会再被写入，保留这个路径只为回收升级前的残留。
         */
        fun legacyAvatarCacheDirectory(context: Context) = context.filesDir.resolve("history_record_cache")
    }

    private companion object { const val MAX_ENTRIES = 100 }
}
