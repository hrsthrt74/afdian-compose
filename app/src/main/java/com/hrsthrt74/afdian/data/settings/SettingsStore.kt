package com.hrsthrt74.afdian.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 首页可配置模块。字符串值用于 DataStore 持久化，后续新增模块可兼容旧版本配置。 */
enum class HomeModule(val storageValue: String, val title: String, val summary: String) {
    CreatorData("creator_data", "创作者数据", "[创作者] 今日收入等数据"),
    Sponsoring("sponsoring", "我发电的", "快速访问已发电的创作者"),
    MarkedCreators("marked_creators", "收藏的创作者", "快速访问收藏的创作者"),
    WithdrawalReminder("withdrawal_reminder", "提现提醒", "[创作者] 月底显示提现提醒");

    companion object {
        fun fromStorage(value: String): HomeModule? = entries.firstOrNull { it.storageValue == value }
    }
}

/** 进程级单例的设置 DataStore，替代旧的 SharedPreferences("settings")。 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        // 与旧 SP 中的键名保持一致，方便日后人工对照排查
        val STARTUP_PAGE = intPreferencesKey("startup_page")
        val USE_AFDIAN_COLOR = booleanPreferencesKey("use_afdian_color")
        val DISCLAIMER = booleanPreferencesKey("disclaimer")
        // 配色模式：0=跟随系统 1=浅色 2=深色
        val COLOR_MODE = intPreferencesKey("color_mode")
        // 高级过渡动画总开关（卡片展开等）
        val ADVANCED_TRANSITION = booleanPreferencesKey("advanced_transition")
        // 页面内边缘滑动返回手势总开关
        val GESTURE_BACK = booleanPreferencesKey("gesture_back")
        // 调试日志总开关：开启后 AppLog 才输出 D 级详细日志（网络请求、耗时等）
        val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
        val HOME_CREATOR_DATA = booleanPreferencesKey("home_creator_data")
        val HOME_SPONSORING = booleanPreferencesKey("home_sponsoring")
        val HOME_MARKED_CREATORS = booleanPreferencesKey("home_marked_creators")
        val HOME_WITHDRAWAL_REMINDER = booleanPreferencesKey("home_withdrawal_reminder")
        val HOME_ENABLED_MODULES = androidx.datastore.preferences.core.stringPreferencesKey("home_enabled_modules")
        val HOME_MODULE_ORDER = androidx.datastore.preferences.core.stringPreferencesKey("home_module_order")

    }

    /** 启动页索引，未设置时默认第 0 页 */
    val startupPage: Flow<Int> = context.settingsDataStore.data.map { it[Keys.STARTUP_PAGE] ?: 0 }

    /** 是否使用爱发电品牌紫作为主题色 */
    val useAfdianColor: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.USE_AFDIAN_COLOR] ?: false }

    /** 免责声明是否已同意 */
    val disclaimerAccepted: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DISCLAIMER] ?: false }

    /** 配色模式：0=跟随系统 1=浅色 2=深色，默认跟随系统 */
    val colorMode: Flow<Int> = context.settingsDataStore.data.map { it[Keys.COLOR_MODE] ?: 0 }

    /** 高级过渡动画总开关（卡片展开等），默认开启 */
    val advancedTransition: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ADVANCED_TRANSITION] ?: true }

    /** 页面内边缘滑动返回手势，默认开启（系统预测性返回不受此控制） */
    val gestureBack: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.GESTURE_BACK] ?: true }

    /**
     * 调试日志开关，默认关闭。
     * 关闭 = 普通用户模式（AppLog 只输出警告/错误）；开启 = 排查问题模式（额外输出 D 级详细日志）。
     */
    val debugLogging: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.DEBUG_LOGGING] ?: false }

    /** 首页模块开关，默认全部开启；新账号无需额外初始化配置。 */
    val homeCreatorData: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HOME_CREATOR_DATA] ?: true }
    val homeSponsoring: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HOME_SPONSORING] ?: true }
    val homeMarkedCreators: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HOME_MARKED_CREATORS] ?: true }
    val homeWithdrawalReminder: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HOME_WITHDRAWAL_REMINDER] ?: true }
    /** 首页启用模块集合；未写入时从旧版分散布尔开关迁移，确保升级不丢用户配置。 */
    val homeEnabledModules: Flow<Set<HomeModule>> = context.settingsDataStore.data.map { preferences ->
        val stored = preferences[Keys.HOME_ENABLED_MODULES]
        if (stored != null) {
            stored.split(",").mapNotNull(HomeModule::fromStorage).toSet()
        } else {
            HomeModule.entries.filter { module ->
                when (module) {
                    HomeModule.CreatorData -> preferences[Keys.HOME_CREATOR_DATA] ?: true
                    HomeModule.Sponsoring -> preferences[Keys.HOME_SPONSORING] ?: true
                    HomeModule.MarkedCreators -> preferences[Keys.HOME_MARKED_CREATORS] ?: true
                    HomeModule.WithdrawalReminder -> preferences[Keys.HOME_WITHDRAWAL_REMINDER] ?: true
                }
            }.toSet()
        }
    }
    /** 首页模块顺序；过滤未知模块并补上未来新增模块，避免升级后模块消失。 */
    val homeModuleOrder: Flow<List<HomeModule>> = context.settingsDataStore.data.map { preferences ->
        val saved = preferences[Keys.HOME_MODULE_ORDER]
            ?.split(",")
            ?.mapNotNull(HomeModule::fromStorage)
            .orEmpty()
        (saved + HomeModule.entries).distinct()
    }
    suspend fun setHomeCreatorData(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_CREATOR_DATA] = enabled } }
    suspend fun setHomeSponsoring(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_SPONSORING] = enabled } }
    suspend fun setHomeMarkedCreators(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_MARKED_CREATORS] = enabled } }
    suspend fun setHomeWithdrawalReminder(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_WITHDRAWAL_REMINDER] = enabled } }
    suspend fun setHomeEnabledModules(modules: Set<HomeModule>) {
        context.settingsDataStore.edit { it[Keys.HOME_ENABLED_MODULES] = modules.joinToString(",") { module -> module.storageValue } }
    }
    suspend fun setHomeModuleOrder(order: List<HomeModule>) {
        context.settingsDataStore.edit { it[Keys.HOME_MODULE_ORDER] = order.joinToString(",") { module -> module.storageValue } }
    }

    suspend fun setDebugLogging(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DEBUG_LOGGING] = enabled }
    }

    suspend fun setAdvancedTransition(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ADVANCED_TRANSITION] = enabled }
    }

    suspend fun setGestureBack(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GESTURE_BACK] = enabled }
    }

    suspend fun setColorMode(mode: Int) {
        context.settingsDataStore.edit { it[Keys.COLOR_MODE] = mode }
    }

    suspend fun setStartupPage(index: Int) {
        context.settingsDataStore.edit { it[Keys.STARTUP_PAGE] = index }
    }

    suspend fun setUseAfdianColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_AFDIAN_COLOR] = enabled }
    }

    suspend fun acceptDisclaimer() {
        context.settingsDataStore.edit { it[Keys.DISCLAIMER] = true }
    }
}
