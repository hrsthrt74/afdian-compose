package com.hrsthrt74.afdian.data.auth

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.AppLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 账号列表的 DataStore。
 *
 * 独立于 `auth`（活动 token）与 `settings`：账号列表属于「登录体系」，
 * 清活动 token 不应该顺手把列表一起删掉；同时 `authDataStore` 是 AuthSession.kt 的
 * 文件私有扩展属性，这里也不能重复声明同名 store。
 */
private val Context.savedAccountsDataStore: DataStore<Preferences> by preferencesDataStore(name = "accounts")

/**
 * 已保存账号仓库。
 *
 * - 所有写操作都过 [mutex]：面板上的删除、切换后的 touch、添加账号可能并发，
 *   读-改-写不加锁会丢更新（和 HomeCache 的保存锁同一套理由）。
 * - token 在写入前用 [TokenCipher] 加密，磁盘上只有密文。
 */
class SavedAccountStore(private val application: Application) {
    private val mutex = Mutex()
    private val cipher = TokenCipher()

    /** 全部账号，按最近使用倒序（读失败时返回空表，不抛异常）。 */
    suspend fun load(): List<SavedAccount> = readFile().accounts

    /** 解密指定账号的 token；记录不存在或解密失败返回 null。 */
    suspend fun readToken(userId: String): String? {
        val record = load().firstOrNull { it.userId == userId } ?: return null
        return cipher.decrypt(record.encryptedToken)
    }

    /**
     * 新增或更新账号，并标记为最近使用。
     *
     * @return true 表示已保存；false 表示参数非法或已达 [MAX_SAVED_ACCOUNTS] 上限
     */
    suspend fun upsert(
        userId: String,
        name: String,
        avatar: String?,
        token: String,
        now: Long,
    ): Boolean {
        if (userId.isBlank() || token.isBlank()) return false
        return mutex.withLock {
            val file = readFile()
            val account = SavedAccount(
                userId = userId,
                name = name,
                avatar = avatar.orEmpty(),
                // 每次登录/添加都会拿到新 token，这里一并刷新密文与指纹
                encryptedToken = cipher.encrypt(token),
                tokenFingerprint = authTokenFingerprint(token),
                lastUsedAt = now,
            )
            val next = upsertAccount(file.accounts, account) ?: run {
                AppLog.w("SavedAccountStore", "账号已达上限 ${MAX_SAVED_ACCOUNTS}，未保存")
                return@withLock false
            }
            writeFile(file.copy(accounts = next))
            AppLog.d("SavedAccountStore", "账号已保存，当前共 ${next.size} 个")
            true
        }
    }

    /** 只更新最近使用时间（切换成功后调用，让列表顺序反映最近使用）。 */
    suspend fun touch(userId: String, now: Long) = mutex.withLock {
        val file = readFile()
        val next = file.accounts
            .map { if (it.userId == userId) it.copy(lastUsedAt = now) else it }
            .sortedByDescending { it.lastUsedAt }
        if (next != file.accounts) writeFile(file.copy(accounts = next))
    }

    /** 用最新资料刷新展示字段（昵称/头像可能在服务端改过）。 */
    suspend fun updateProfile(userId: String, name: String, avatar: String?) = mutex.withLock {
        val file = readFile()
        val next = file.accounts.map {
            if (it.userId == userId) it.copy(name = name, avatar = avatar.orEmpty()) else it
        }
        if (next != file.accounts) writeFile(file.copy(accounts = next))
    }

    /** 删除一条账号记录（不影响其他账号，也不动活动 token）。 */
    suspend fun remove(userId: String) = mutex.withLock {
        val file = readFile()
        val next = file.accounts.filterNot { it.userId == userId }
        if (next.size != file.accounts.size) {
            writeFile(file.copy(accounts = next))
            AppLog.d("SavedAccountStore", "账号记录已删除，剩余 ${next.size} 个")
        }
    }

    private suspend fun readFile(): SavedAccountsFile {
        val raw = application.savedAccountsDataStore.data.first()[ACCOUNTS_KEY] ?: return SavedAccountsFile()
        return runCatching { savedAccountsJson.decodeFromString<SavedAccountsFile>(raw) }
            .onFailure { AppLog.w("SavedAccountStore", "账号列表解析失败，按空表处理", it) }
            .getOrDefault(SavedAccountsFile())
    }

    private suspend fun writeFile(file: SavedAccountsFile) {
        application.savedAccountsDataStore.edit {
            it[ACCOUNTS_KEY] = savedAccountsJson.encodeToString(file)
        }
    }

    private companion object {
        val ACCOUNTS_KEY = stringPreferencesKey("saved_accounts_json")
    }
}
