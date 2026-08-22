package com.hrsthrt74.afdian.data.content

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * 进程级单例的资料缓存 DataStore。
 * 注意：DataStore 对同一文件全进程只允许存在一个实例（重复声明会抛
 * IllegalStateException），因此必须只在这里声明一次，
 * 其他代码一律通过 [ProfileCache] 间接读写，切勿再新建同名 store。
 */
private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

/**
 * 「我的」个人资料的磁盘缓存：冷启动时先回填上次的资料消除用户名/头像闪烁，
 * 网络刷新成功后再写回覆盖。以 JSON 字符串形式存储在 DataStore 中。
 */
class ProfileCache(private val context: Context) {
    // 与 AfadianContentService 相同的宽松解析配置：忽略服务端新增字段、容错非法值
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private object Keys {
        // 缓存条目唯一的键：整个 AfadianProfile 序列化后的 JSON 文本
        val PROFILE_JSON = stringPreferencesKey("profile_json")
    }

    /** 读取缓存的资料；首次使用或数据损坏时返回 null，由上层走正常网络流程 */
    suspend fun load(): AfadianProfile? {
        val raw = context.profileDataStore.data.first()[Keys.PROFILE_JSON] ?: return null
        return runCatching { json.decodeFromString(AfadianProfile.serializer(), raw) }.getOrNull()
    }

    /** 网络刷新成功后写回缓存；写盘失败静默忽略，不影响主流程 */
    suspend fun save(profile: AfadianProfile) {
        runCatching {
            val encoded = json.encodeToString(AfadianProfile.serializer(), profile)
            context.profileDataStore.edit { it[Keys.PROFILE_JSON] = encoded }
        }
    }

    /** 登出时清空缓存，避免换账号登录后短暂显示上一个账号的资料 */
    suspend fun clear() {
        context.profileDataStore.edit { it.remove(Keys.PROFILE_JSON) }
    }
}
