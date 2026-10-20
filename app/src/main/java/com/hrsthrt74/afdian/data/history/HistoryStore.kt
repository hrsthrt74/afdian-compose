package com.hrsthrt74.afdian.data.history

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
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "history")

/**
 * 浏览历史本地仓库。
 *
 * 只保存浏览过的目标（文字与头像**链接**），**不下载头像**：
 * 头像由 UI 层带着链接交给 Coil 按需加载，磁盘缓存落在 Coil 的全局缓存里，
 * 因此这里不再维护任何图片文件。旧版本曾把头像预下载到专属目录
 * （[FilePaths.legacyAvatarCacheDirectory]），那批残留由 [clearLegacyAvatarCache] 负责回收。
 *
 * 数据按账号隔离：每个账号在 DataStore 里占一个 `entries_json_<user_id>` 分区，
 * 读写都只碰当前账号的分区。同一账号换 token 重新登录，历史仍然保留；
 * 换到另一个账号，则完全看不到上一个账号的记录。
 */
class HistoryStore(context: Context) {
    private val context = context.applicationContext
    private val settingsStore = SettingsStore(this.context)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /**
     * 历史列表：账号身份变化时自动切换到对应的分区。
     *
     * 账号未确定（刚登录、资料还没回来）或已登出时返回空列表，
     * 避免把上一个账号的记录错误地展示给新账号。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: Flow<List<HistoryEntry>> = CurrentAccount.userId.flatMapLatest { owner ->
        if (owner.isNullOrBlank()) {
            flowOf(emptyList<HistoryEntry>())
        } else {
            context.historyDataStore.data.map { preferences -> decode(preferences[entriesKey(owner)]) }
        }
    }

    /** 新增或置顶记录；同一创作者/动态不会产生重复条目。 */
    suspend fun recordOpened(target: HistoryTarget) {
        if (target.targetId.isBlank() || !settingsStore.historyEnabled.first()) return
        // 账号身份未就绪时放弃写入：记录没有归属，写进任何一个分区都会污染数据
        val owner = requireOwner("写入浏览历史") ?: return
        runHistoryOperation("写入浏览历史失败: ${keyOf(target)}") {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val old = readEntries(owner)
                    val current = old.firstOrNull { it.key == keyOf(target) }
                    val mergedTarget = mergeTarget(target, current?.target)
                    val next = (old.filterNot { it.key == keyOf(target) } + HistoryEntry(mergedTarget, System.currentTimeMillis()))
                        .sortedByDescending { it.visitedAt }
                        .take(MAX_ENTRIES)
                    writeEntries(owner, next)
                    AppLog.d("HistoryStore", "已记录浏览历史: ${keyOf(target)}")
                }
            }
        }
    }

    /** 用详情接口返回的数据补全标题与头像链接，但不改变原始访问时间。 */
    suspend fun updateSnapshot(target: HistoryTarget) {
        if (target.targetId.isBlank() || !settingsStore.historyEnabled.first()) return
        val owner = requireOwner("更新浏览历史") ?: return
        runHistoryOperation("更新浏览历史失败: ${keyOf(target)}") {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val old = readEntries(owner)
                    val current = old.firstOrNull { it.key == keyOf(target) } ?: return@withLock
                    writeEntries(owner, old.map { entry ->
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

    /** 清空**当前账号**的浏览历史（历史页的「清空」按钮），并顺带回收旧版遗留的头像缓存目录。 */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val owner = requireOwner("清空浏览历史") ?: return@withContext
        mutex.withLock {
            writeEntries(owner, emptyList())
            deleteLegacyAvatarCache()
            AppLog.d("HistoryStore", "已清空当前账号的浏览历史")
        }
    }

    /**
     * 清空**所有账号**的浏览历史（存储管理页的「清理」）。
     *
     * 存储管理面向的是磁盘占用，必须把每个账号的分区都清掉，
     * 否则用户点完「清理」发现空间没变化。
     */
    suspend fun clearAllAccounts() = withContext(Dispatchers.IO) {
        mutex.withLock {
            context.historyDataStore.edit { preferences ->
                preferences.asMap().keys
                    .filter { it.name.startsWith(ENTRIES_KEY_PREFIX) }
                    .forEach { preferences.remove(it) }
                preferences.remove(LEGACY_ENTRIES_KEY)
            }
            deleteLegacyAvatarCache()
            AppLog.d("HistoryStore", "已清空全部账号的浏览历史")
        }
    }

    /**
     * 把升级前「单账号、无归属」的旧记录迁移到指定账号名下。
     *
     * 旧版本只有一个 `entries_json` 键，里面的数据必然属于升级前最后登录的那个账号；
     * 新版本第一次确定账号身份时把它接到该账号名下，避免老用户升级后历史凭空消失。
     * 幂等：旧键不存在时直接返回；目标账号已有记录时只删旧键，不覆盖。
     */
    suspend fun migrateLegacyEntries(ownerUserId: String) = withContext(Dispatchers.IO) {
        val owner = ownerUserId.takeIf { it.isNotBlank() } ?: return@withContext
        mutex.withLock {
            val legacy = context.historyDataStore.data.first()[LEGACY_ENTRIES_KEY] ?: return@withLock
            context.historyDataStore.edit { preferences ->
                if (preferences[entriesKey(owner)] == null) {
                    preferences[entriesKey(owner)] = legacy
                    AppLog.d("HistoryStore", "已把旧版浏览历史迁移到当前账号")
                } else {
                    AppLog.d("HistoryStore", "当前账号已有历史记录，丢弃无归属的旧版数据")
                }
                preferences.remove(LEGACY_ENTRIES_KEY)
            }
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

    /** 取当前账号；未就绪时打一条日志并返回 null，由调用方直接放弃本次操作。 */
    private fun requireOwner(action: String): String? =
        CurrentAccount.userId.value?.takeIf { it.isNotBlank() }
            ?: run {
                AppLog.d("HistoryStore", "账号身份未就绪，跳过$action")
                null
            }

    private fun entriesKey(ownerUserId: String) = stringPreferencesKey("$ENTRIES_KEY_PREFIX$ownerUserId")

    private suspend fun readEntries(ownerUserId: String): List<HistoryEntry> =
        decode(context.historyDataStore.data.first()[entriesKey(ownerUserId)])

    private suspend fun writeEntries(ownerUserId: String, entries: List<HistoryEntry>) {
        context.historyDataStore.edit {
            it[entriesKey(ownerUserId)] = json.encodeToString(ListSerializer(HistoryEntry.serializer()), entries)
        }
    }

    /** 把分区里的 JSON 解成列表；损坏数据只告警一次并当作空列表。 */
    private fun decode(raw: String?): List<HistoryEntry> {
        if (raw == null) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(HistoryEntry.serializer()), raw) }
            .onFailure { AppLog.w("HistoryStore", "历史记录解析失败，已忽略损坏数据", it) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.visitedAt }
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

    private companion object {
        const val MAX_ENTRIES = 100

        /** 每个账号的分区键前缀，完整键形如 `entries_json_<user_id>` */
        const val ENTRIES_KEY_PREFIX = "entries_json_"

        /** 升级前的单账号键；迁移后即删除 */
        val LEGACY_ENTRIES_KEY = stringPreferencesKey("entries_json")
    }
}
