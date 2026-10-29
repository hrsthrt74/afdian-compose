package com.hrsthrt74.afdian.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.CurrentAccount
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

/** AI 记录的专属 DataStore，与「设置」「浏览历史」分开：存储管理里能单独统计和清理。 */
private val Context.aiHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_history")

/**
 * AI 问答记录的本地仓库。
 *
 * 整体骨架照抄 [com.hrsthrt74.afdian.data.history.HistoryStore]，保持两处实现同构：
 * 独立 DataStore 文件 + [Mutex] 串行化 + JSON 列表整存整取 + 按账号分区。
 *
 * 数据按账号隔离：每个账号占一个 `entries_json_<user_id>` 分区，只读写当前账号那一份。
 * 这样做的理由是收入分析本身是账号敏感数据，换账号后不该看到上一个账号的分析结论；
 * 这一点与「AI 配置（API Key）是设备级」刻意不同。
 */
class AiHistoryStore(context: Context) {
    private val context = context.applicationContext

    // DataStore 的读-改-写不是原子的，连点两次「问 AI」可能并发写入丢记录，用互斥锁串起来
    private val mutex = Mutex()

    // 宽容解析：将来给 AiHistoryEntry 加字段后，旧数据仍能读出来
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /**
     * 当前账号的 AI 记录，按时间倒序。
     *
     * 账号未确定（刚登录、资料还没回来）或已登出时返回空列表，
     * 避免把上一个账号的记录错误地展示给新账号。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: Flow<List<AiHistoryEntry>> = CurrentAccount.userId.flatMapLatest { owner ->
        if (owner.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            context.aiHistoryDataStore.data.map { preferences -> decode(preferences[entriesKey(owner)]) }
        }
    }

    /**
     * 追加一条记录。
     *
     * 空回答直接丢弃：调用方理论上只在成功时调用，这里再兜一层，避免把空白条目写进历史。
     * 超出 [MAX_ENTRIES] 时按时间截掉最旧的 —— 用户回看 50 条已经足够，
     * 而 DataStore 每次都是整文件重写，条数不封顶会让写入越来越慢。
     */
    suspend fun record(entry: AiHistoryEntry) {
        if (entry.answer.isBlank()) {
            AppLog.d("AiHistoryStore", "回答为空，跳过本次记录")
            return
        }
        val owner = requireOwner("写入 AI 记录") ?: return
        runHistoryOperation("写入 AI 记录失败: ${entry.id}") {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val old = readEntries(owner)
                    val next = (old.filterNot { it.id == entry.id } + entry)
                        .sortedByDescending { it.createdAt }
                        .take(MAX_ENTRIES)
                    writeEntries(owner, next)
                    AppLog.d(
                        "AiHistoryStore",
                        "已记录 AI 问答：${entry.scene}，正文 ${entry.answer.length} 字，当前共 ${next.size} 条",
                    )
                }
            }
        }
    }

    /** 删除单条（历史页长按 / 详情弹层里的「删除」）。 */
    suspend fun delete(id: String) {
        val owner = requireOwner("删除 AI 记录") ?: return
        runHistoryOperation("删除 AI 记录失败: $id") {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val old = readEntries(owner)
                    val next = old.filterNot { it.id == id }
                    if (next.size == old.size) return@withLock
                    writeEntries(owner, next)
                    AppLog.d("AiHistoryStore", "已删除 AI 记录: $id")
                }
            }
        }
    }

    /** 清空**当前账号**的 AI 记录（历史页顶栏的「清空」）。 */
    suspend fun clearAll() {
        val owner = requireOwner("清空 AI 记录") ?: return
        runHistoryOperation("清空 AI 记录失败") {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    writeEntries(owner, emptyList())
                    AppLog.d("AiHistoryStore", "已清空当前账号的 AI 记录")
                }
            }
        }
    }

    /**
     * 清空**所有账号**的 AI 记录（存储管理页的「清理」）。
     *
     * 存储管理面向的是磁盘占用，必须把每个账号的分区都清掉，否则用户点完「清理」发现空间没变化。
     */
    suspend fun clearAllAccounts() = withContext(Dispatchers.IO) {
        mutex.withLock {
            context.aiHistoryDataStore.edit { preferences ->
                preferences.asMap().keys
                    .filter { it.name.startsWith(ENTRIES_KEY_PREFIX) }
                    .forEach { preferences.remove(it) }
            }
            AppLog.d("AiHistoryStore", "已清空全部账号的 AI 记录")
        }
    }

    /** 取当前账号；未就绪时打一条日志并返回 null，由调用方直接放弃本次操作。 */
    private fun requireOwner(action: String): String? =
        CurrentAccount.userId.value?.takeIf { it.isNotBlank() }
            ?: run {
                AppLog.d("AiHistoryStore", "账号身份未就绪，跳过$action")
                null
            }

    private fun entriesKey(ownerUserId: String) = stringPreferencesKey("$ENTRIES_KEY_PREFIX$ownerUserId")

    private suspend fun readEntries(ownerUserId: String): List<AiHistoryEntry> =
        decode(context.aiHistoryDataStore.data.first()[entriesKey(ownerUserId)])

    private suspend fun writeEntries(ownerUserId: String, entries: List<AiHistoryEntry>) {
        context.aiHistoryDataStore.edit {
            it[entriesKey(ownerUserId)] = json.encodeToString(ListSerializer(AiHistoryEntry.serializer()), entries)
        }
    }

    /** 把分区里的 JSON 解成列表；损坏数据只告警一次并当作空列表。 */
    private fun decode(raw: String?): List<AiHistoryEntry> {
        if (raw == null) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(AiHistoryEntry.serializer()), raw) }
            .onFailure { AppLog.w("AiHistoryStore", "AI 记录解析失败，已忽略损坏数据", it) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAt }
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
            AppLog.w("AiHistoryStore", message, error)
        }
    }

    private companion object {
        /** 分区键前缀：`entries_json_<user_id>`，与浏览历史同一套命名。 */
        const val ENTRIES_KEY_PREFIX = "entries_json_"

        /** 单账号最多保留的记录条数，超出按时间截掉最旧的。 */
        const val MAX_ENTRIES = 50
    }
}
