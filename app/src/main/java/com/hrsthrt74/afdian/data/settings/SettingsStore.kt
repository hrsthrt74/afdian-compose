package com.hrsthrt74.afdian.data.settings

import android.content.Context
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.stats.ChartCatalog
import com.hrsthrt74.afdian.data.stats.DataCardCatalog
import com.hrsthrt74.afdian.data.stats.IncomeAmountMode
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** 首页可配置模块。字符串值用于 DataStore 持久化，后续新增模块可兼容旧版本配置。 */
enum class HomeModule(val storageValue: String, val title: String, val summary: String) {
    WithdrawalReminder("withdrawal_reminder", "提现提醒", "[创作者] 月底显示提现提醒"),
    CreatorData("creator_data", "创作者数据", "[创作者] 今日收入等数据"),
    Sponsoring("sponsoring", "我发电的", "快速访问已发电的创作者"),
    MarkedCreators("marked_creators", "收藏的创作者", "快速访问收藏的创作者"),
    History("history", "浏览历史", "快速访问最近浏览的创作者和动态");

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
        // 系统预测性返回总开关；关闭后由导航 entry 消费系统返回事件
        val PREDICTIVE_BACK = booleanPreferencesKey("predictive_back")
    // 调试日志总开关：开启后 AppLog 才输出 D 级详细日志（网络请求、耗时等）
    val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
    // 数据卡单位：金额类（收入、可提现）是否显示「元」
    val SHOW_INCOME_UNIT = booleanPreferencesKey("show_income_unit")
    // 数据卡单位：人数/次数类（发电人数、发电次数、访问人数、访问次数）是否显示「人」「次」
    val SHOW_COUNT_UNIT = booleanPreferencesKey("show_count_unit")
        // 浏览历史开关：关闭后不再写入新的历史记录或头像缓存
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val HOME_CREATOR_DATA = booleanPreferencesKey("home_creator_data")
        val HOME_SPONSORING = booleanPreferencesKey("home_sponsoring")
        val HOME_MARKED_CREATORS = booleanPreferencesKey("home_marked_creators")
        val HOME_WITHDRAWAL_REMINDER = booleanPreferencesKey("home_withdrawal_reminder")
        val HOME_CREATOR_LAYOUT = androidx.datastore.preferences.core.stringPreferencesKey("home_creator_layout")
        val HOME_ENABLED_MODULES = androidx.datastore.preferences.core.stringPreferencesKey("home_enabled_modules")
        val HOME_MODULE_ORDER = androidx.datastore.preferences.core.stringPreferencesKey("home_module_order")
        val MANAGEMENT_LAYOUT = androidx.datastore.preferences.core.stringPreferencesKey("management_layout")
        // 「每月汇总」页的金额口径：抽成前 / 抽成后（存 IncomeAmountMode.storageValue）
        val MONTHLY_SUMMARY_AMOUNT_MODE = androidx.datastore.preferences.core.stringPreferencesKey("monthly_summary_amount_mode")
        // 消息页「发电明细」开关
        val MSG_SHOW_SPONSOR_DETAIL = booleanPreferencesKey("msg_show_sponsor_detail")
    }

    /** 启动页索引，未设置时默认第 0 页 */
    val startupPage: Flow<Int> = context.settingsDataStore.data.map { it[Keys.STARTUP_PAGE] ?: 0 }

    /** 是否使用爱发电品牌紫作为主题色 */
    val useAfdianColor: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.USE_AFDIAN_COLOR] ?: true }

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

    /** 系统预测性返回，默认开启。 */
    val predictiveBack: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.PREDICTIVE_BACK] ?: true }

    /**
     * 调试日志开关，默认关闭。
     * 关闭 = 普通用户模式（AppLog 只输出警告/错误）；开启 = 排查问题模式（额外输出 D 级详细日志）。
     */
    val debugLogging: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.DEBUG_LOGGING] ?: false }

    /** 是否记录浏览历史，默认开启；关闭只暂停新增，不删除已有记录。 */
    val historyEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.HISTORY_ENABLED] ?: true }

    /** 金额类数据卡是否显示「元」单位，默认关闭（只显示数字）。 */
    val showIncomeUnit: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.SHOW_INCOME_UNIT] ?: false }

    /** 人数/次数类数据卡是否显示「人」「次」单位，默认关闭（只显示数字）。 */
    val showCountUnit: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.SHOW_COUNT_UNIT] ?: false }

    /** 首页模块开关，默认全部开启；新账号无需额外初始化配置。 */
    val homeCreatorData: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HOME_CREATOR_DATA] ?: true }
    val homeSponsoring: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HOME_SPONSORING] ?: true }
    val homeMarkedCreators: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HOME_MARKED_CREATORS] ?: true }
    val homeWithdrawalReminder: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HOME_WITHDRAWAL_REMINDER] ?: true }
    /** 首页创作者数据布局，和管理页共用卡片、图表、标题、分割线布局元素。 */
    val homeCreatorDataLayout: Flow<List<ManagementLayoutEntry>> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.HOME_CREATOR_LAYOUT]
            ?.let { runCatching { Json.decodeFromString<List<ManagementLayoutEntry>>(it) }.getOrNull() }
            ?.filter { it.isKnownLayoutEntry() }
            ?: defaultHomeCreatorDataLayout
    }
    /** 首页启用模块集合；未写入时从旧版分散布尔开关迁移，确保升级不丢用户配置。 */
    val homeEnabledModules: Flow<Set<HomeModule>> = context.settingsDataStore.data.map { preferences ->
        val stored = preferences[Keys.HOME_ENABLED_MODULES]
        if (stored != null) {
            // 已有用户配置也自动补入新增模块，避免历史记录模块升级后悄悄消失。
            stored.split(",").mapNotNull(HomeModule::fromStorage).toSet()
        } else {
            HomeModule.entries.filter { module ->
                when (module) {
                    HomeModule.CreatorData -> preferences[Keys.HOME_CREATOR_DATA] ?: true
                    HomeModule.Sponsoring -> preferences[Keys.HOME_SPONSORING] ?: true
                    HomeModule.MarkedCreators -> preferences[Keys.HOME_MARKED_CREATORS] ?: true
                    HomeModule.WithdrawalReminder -> preferences[Keys.HOME_WITHDRAWAL_REMINDER] ?: true
                    HomeModule.History -> true
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

    /** 管理页布局；未知卡片会被过滤，用户关闭的卡片不会被自动重新加入。 */
    val managementLayout: Flow<List<ManagementLayoutEntry>> = context.settingsDataStore.data.map { preferences ->
        val stored = preferences[Keys.MANAGEMENT_LAYOUT]
            ?.let { runCatching { Json.decodeFromString<List<ManagementLayoutEntry>>(it) }.getOrNull() }
            ?: defaultManagementLayout
        val knownItems = stored.filter { it.isKnownLayoutEntry() }
        // 旧版本布局中的卡片键已不再认识时直接回到新默认布局，不做旧键转换。
        knownItems.takeIf { items -> items.any { it.type == ManagementLayoutType.CARD } }
            ?: defaultManagementLayout
    }

    suspend fun setHomeCreatorData(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_CREATOR_DATA] = enabled } }
    suspend fun setHomeSponsoring(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_SPONSORING] = enabled } }
    suspend fun setHomeMarkedCreators(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_MARKED_CREATORS] = enabled } }
    suspend fun setHomeWithdrawalReminder(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_WITHDRAWAL_REMINDER] = enabled } }
    suspend fun setHomeCreatorDataLayout(layout: List<ManagementLayoutEntry>) {
        AppLog.d("Settings", "保存首页创作者数据卡布局，项目数=${layout.size}")
        context.settingsDataStore.edit { it[Keys.HOME_CREATOR_LAYOUT] = Json.encodeToString(layout) }
    }
    suspend fun setHomeEnabledModules(modules: Set<HomeModule>) {
        context.settingsDataStore.edit { it[Keys.HOME_ENABLED_MODULES] = modules.joinToString(",") { module -> module.storageValue } }
    }
    suspend fun setHomeModuleOrder(order: List<HomeModule>) {
        context.settingsDataStore.edit { it[Keys.HOME_MODULE_ORDER] = order.joinToString(",") { module -> module.storageValue } }
    }

    suspend fun setManagementLayout(layout: List<ManagementLayoutEntry>) {
        AppLog.d("Settings", "保存管理页数据卡片布局，项目数=${layout.size}")
        context.settingsDataStore.edit { it[Keys.MANAGEMENT_LAYOUT] = Json.encodeToString(layout) }
    }

    /** 「每月汇总」页的金额口径，默认抽成前（与爱发电网页版月度账单口径一致）。 */
    val monthlySummaryAmountMode: Flow<IncomeAmountMode> =
        context.settingsDataStore.data.map { IncomeAmountMode.fromStorage(it[Keys.MONTHLY_SUMMARY_AMOUNT_MODE]) }

    suspend fun setMonthlySummaryAmountMode(mode: IncomeAmountMode) {
        AppLog.d("Settings", "保存每月汇总金额口径，mode=${mode.storageValue}")
        context.settingsDataStore.edit { it[Keys.MONTHLY_SUMMARY_AMOUNT_MODE] = mode.storageValue }
    }


    suspend fun setDebugLogging(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DEBUG_LOGGING] = enabled }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HISTORY_ENABLED] = enabled }
    }

    suspend fun setShowIncomeUnit(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_INCOME_UNIT] = enabled }
    }

    suspend fun setShowCountUnit(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_COUNT_UNIT] = enabled }
    }

    suspend fun setAdvancedTransition(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ADVANCED_TRANSITION] = enabled }
    }

    suspend fun setGestureBack(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GESTURE_BACK] = enabled }
    }

    suspend fun setPredictiveBack(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PREDICTIVE_BACK] = enabled }
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

    /** 消息页「发电明细」开关，默认关闭 */
    val msgShowSponsorDetail: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.MSG_SHOW_SPONSOR_DETAIL] ?: false }

    suspend fun setMsgShowSponsorDetail(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MSG_SHOW_SPONSOR_DETAIL] = enabled }
    }

    suspend fun acceptDisclaimer() {
        context.settingsDataStore.edit { it[Keys.DISCLAIMER] = true }
    }
}

/**
 * 布局条目是否仍被识别：未知卡片/图表键会被过滤掉，
 * 避免 DataStore 里残留升级前不认识的键导致异常渲染。
 */
private fun ManagementLayoutEntry.isKnownLayoutEntry(): Boolean = when (type) {
    ManagementLayoutType.CARD -> DataCardCatalog.find(value) != null
    ManagementLayoutType.CHART -> ChartCatalog.find(value) != null
    ManagementLayoutType.TITLE, ManagementLayoutType.DIVIDER -> true
}
