package com.hrsthrt74.afdian.data.content

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.data.auth.authSessionFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.hrsthrt74.afdian.AppLog

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
 *
 * 缓存与登录会话绑定：写入时记录当时的会话指纹（token 的哈希），读取时比对。
 * 换账号后 token 变化 → 指纹变化 → 旧资料自动作废，不再依赖「登出清理是否成功」。
 */
class ProfileCache(private val application: Application) {
    // 与 AfadianContentService 相同的宽松解析配置：忽略服务端新增字段、容错非法值
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private object Keys {
        // 缓存条目唯一的键：整个 AfadianProfile 序列化后的 JSON 文本
        val PROFILE_JSON = stringPreferencesKey("profile_json")

        // 写入缓存时所属登录会话的指纹；缺失视为旧版本缓存（不做严格校验，保持兼容）
        val SESSION_KEY = stringPreferencesKey("profile_session_key")
    }

    /** 读取缓存的资料；首次使用、数据损坏或属于其他账号时返回 null，由上层走正常网络流程 */
    suspend fun load(): AfadianProfile? {
        val prefs = application.profileDataStore.data.first()
        val raw = prefs[Keys.PROFILE_JSON]
        // 缓存命中情况打点：冷启动回填是否生效、数据是否损坏，一眼可查
        if (raw == null) {
            AppLog.d("ProfileCache", "缓存未命中（首次使用或已被清除）")
            return null
        }
        // 会话指纹校验：当前 token 与写入缓存时的 token 不同，说明期间换过账号
        val cachedSession = prefs[Keys.SESSION_KEY]
        val currentSession = withContext(Dispatchers.IO) { authSessionFingerprint(application) }
        if (cachedSession != null && currentSession != null && cachedSession != currentSession) {
            AppLog.w("ProfileCache", "缓存属于其他登录会话，忽略旧资料")
            return null
        }
        return runCatching { json.decodeFromString(AfadianProfile.serializer(), raw) }
            .onSuccess { AppLog.d("ProfileCache", "缓存命中，昵称=${it.user.name}") }
            .onFailure { AppLog.w("ProfileCache", "缓存解析失败，忽略后走网络: ${it.message}") }
            .getOrNull()
    }

    /**
     * 网络刷新成功后写回缓存；写盘失败静默忽略，不影响主流程。
     *
     * @param expectedSessionKey 发起该次网络请求时的会话指纹。写入时会再算一次当前指纹，
     *   两者不一致说明期间切过账号，这份资料属于上一个账号，必须丢弃——
     *   否则它会被打上新账号的指纹，下一个账号冷启动读到的就是别人的昵称头像。
     */
    suspend fun save(profile: AfadianProfile, expectedSessionKey: String? = null) {
        val sessionKey = withContext(Dispatchers.IO) { authSessionFingerprint(application) }
        // 没有登录态时不写：登出瞬间仍在收尾的网络请求不能把旧账号资料又写回缓存
        if (sessionKey == null) {
            AppLog.d("ProfileCache", "当前无登录态，跳过资料缓存写入")
            return
        }
        if (expectedSessionKey != null && expectedSessionKey != sessionKey) {
            AppLog.d("ProfileCache", "会话已变化，丢弃上一会话的资料写入")
            return
        }
        runCatching {
            val encoded = json.encodeToString(AfadianProfile.serializer(), profile)
            application.profileDataStore.edit {
                it[Keys.PROFILE_JSON] = encoded
                it[Keys.SESSION_KEY] = sessionKey
            }
            AppLog.d("ProfileCache", "网络资料已写回缓存")
        }
    }

    /** 登出时清空缓存，避免换账号登录后短暂显示上一个账号的资料 */
    suspend fun clear() {
        application.profileDataStore.edit {
            it.remove(Keys.PROFILE_JSON)
            it.remove(Keys.SESSION_KEY)
        }
    }
}
