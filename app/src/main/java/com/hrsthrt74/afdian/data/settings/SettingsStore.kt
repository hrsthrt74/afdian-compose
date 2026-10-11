package com.hrsthrt74.afdian.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.stats.ChartCatalog
import com.hrsthrt74.afdian.data.stats.DataCardCatalog
import com.hrsthrt74.afdian.data.stats.IncomeAmountMode
import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * 首页可配置模块。字符串值用于 DataStore 持久化，后续新增模块可兼容旧版本配置。
 *
 * 声明顺序即新用户的默认模块顺序；[defaultEnabled] 为 false 的模块默认关闭，
 * 需要用户在「自定义首页」里手动打开（读取端的初值也用它，避免首帧闪出默认关闭的模块）。
 */
enum class HomeModule(
    val storageValue: String,
    val title: String,
    val summary: String,
    val defaultEnabled: Boolean = true,
) {
    WithdrawalReminder("withdrawal_reminder", "提现提醒", "[创作者] 每月底提醒"),
    CreatorData("creator_data", "创作者数据", "[创作者] 收入数据。长按卡片自定义"),
    // 默认关闭：明细表占版面较高，是否上首页交给用户决定
    RecentIncome("recent_income", "收入明细", "[创作者] 最近5天的每日收入明细", defaultEnabled = false),
    Sponsoring("sponsoring", "我发电的", "已发电的创作者"),
    MarkedCreators("marked_creators", "收藏的创作者", "收藏的创作者"),
    History("history", "浏览历史", "最近浏览的创作者和动态"),
    ;

    companion object {
        fun fromStorage(value: String): HomeModule? = entries.firstOrNull { it.storageValue == value }

        /** 默认启用的模块集合：DataStore 尚未读到时的首帧初值，避免默认关闭的模块闪一帧。 */
        val defaultEnabledSet: Set<HomeModule> = entries.filter { it.defaultEnabled }.toSet()
    }
}

/**
 * 把用户已保存的模块顺序与全量枚举合并：
 * 已保存的相对顺序不动，缺失的模块**插入到它在枚举里的前驱之后**（如新增模块插到「创作者数据」后面），
 * 而不是一律追加到末尾——否则升级后新模块会莫名其妙跑到最后一位。
 */
internal fun mergeHomeModuleOrder(saved: List<HomeModule>): List<HomeModule> {
    // 先去掉已保存列表里的重复项（存储值被手工改坏时也不会出现重复行）
    val result = saved.distinct().toMutableList()
    // 逐个补入缺失模块，保持枚举声明顺序
    HomeModule.entries.forEachIndexed { index, module ->
        if (module in result) return@forEachIndexed
        // 前驱模块（声明顺序里的上一个）已在列表中时插到它后面，否则追加末尾
        val anchor = result.indexOfFirst { it.ordinal == index - 1 }
        if (anchor >= 0) result.add(anchor + 1, module) else result.add(module)
    }
    return result
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
        // Monet 配色总开关：开启后改用系统壁纸 / 指定种子色生成整套色板
        val MONET_ENABLED = booleanPreferencesKey("monet_enabled")
        // Monet 种子色预设，存 KeyColorPreset.storageValue（不存下标，避免档位增删后错位）
        val KEY_COLOR = androidx.datastore.preferences.core.stringPreferencesKey("key_color")
        // 高级过渡动画总开关（卡片展开等）
        val ADVANCED_TRANSITION = booleanPreferencesKey("advanced_transition")
        // 页面内边缘滑动返回手势总开关
        val GESTURE_BACK = booleanPreferencesKey("gesture_back")
        // 系统预测性返回总开关；关闭后由导航 entry 消费系统返回事件
        val PREDICTIVE_BACK = booleanPreferencesKey("predictive_back")
        // 底部导航栏样式：0=常规（贴底整条） 1=悬浮胶囊
        val BOTTOM_BAR_STYLE = intPreferencesKey("bottom_bar_style")
    // 调试日志总开关：开启后 AppLog 才输出 D 级详细日志（网络请求、耗时等）
    val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
    // 日志落盘开关：开启后运行日志（D/W/E）追加写入 files/logs/ 按天文件（崩溃报告不受此开关控制）
    val LOG_TO_FILE = booleanPreferencesKey("log_to_file")
    // 数据卡单位：金额类（收入、可提现）是否显示「元」
    val SHOW_INCOME_UNIT = booleanPreferencesKey("show_income_unit")
    // 数据卡单位：人数/次数类（发电人数、发电次数、访问人数、访问次数）是否显示「人」「次」
    val SHOW_COUNT_UNIT = booleanPreferencesKey("show_count_unit")
    // 数据卡数字动画：是否使用「逐位滚动」的滚轮效果；关闭则回退为普通文本显示
    val ROLLING_NUMBER = booleanPreferencesKey("rolling_number")
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
        // 图片缓存（Coil 全局磁盘缓存）的体积上限，存 ImageCacheLimit.storageValue
        val IMAGE_CACHE_LIMIT = androidx.datastore.preferences.core.stringPreferencesKey("image_cache_limit")
        // 外部爱发电链接是否优先交给官方客户端，默认关闭（优先使用 Custom Tabs）
        val PREFER_OFFICIAL_CLIENT = booleanPreferencesKey("prefer_official_client")
    }

    /** 启动页索引，未设置时默认第 0 页 */
    val startupPage: Flow<Int> = context.settingsDataStore.data.map { it[Keys.STARTUP_PAGE] ?: 0 }

    /** 是否使用爱发电品牌紫作为主题色 */
    val useAfdianColor: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.USE_AFDIAN_COLOR] ?: true }

    /** 免责声明是否已同意 */
    val disclaimerAccepted: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DISCLAIMER] ?: false }

    /** 配色模式：0=跟随系统 1=浅色 2=深色，默认跟随系统 */
    val colorMode: Flow<Int> = context.settingsDataStore.data.map { it[Keys.COLOR_MODE] ?: 0 }

    /**
     * Monet 配色总开关，默认关闭。
     * 关闭时外观与改动前完全一致；开启后 [colorMode] 的三档语义不变，
     * 只是背后换成 Monet 色板（详见 AfdianTheme 里的模式映射）。
     */
    val monetEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.MONET_ENABLED] ?: false }

    /** Monet 种子色预设，默认「跟随系统壁纸」；未开启 Monet 时该值不参与主题。 */
    val keyColor: Flow<KeyColorPreset> =
        context.settingsDataStore.data.map { KeyColorPreset.fromStorage(it[Keys.KEY_COLOR]) }

    /** 高级过渡动画总开关（卡片展开等），默认开启 */
    val advancedTransition: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ADVANCED_TRANSITION] ?: true }

    /** 页面内边缘滑动返回手势，默认开启（系统预测性返回不受此控制） */
    val gestureBack: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.GESTURE_BACK] ?: true }

    /**
     * 底部导航栏样式：0=常规（贴底整条，默认） 1=悬浮胶囊。
     * 用 Int 而非 Boolean 存储是为了以后新增档位（如沉浸式、双排）不用迁移键。
     */
    val bottomBarStyle: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.BOTTOM_BAR_STYLE] ?: 0 }

    /** 系统预测性返回，默认开启；legacy 低配版（SUPPORT_PREDICTIVE_BACK=false）在数据源层
     *  强制关闭，MainScreen 的 PredictiveBackGuard 据此拦截系统返回事件、降级为普通返回。 */
    val predictiveBack: Flow<Boolean> =
        context.settingsDataStore.data.map {
            (it[Keys.PREDICTIVE_BACK] ?: true) && BuildConfig.SUPPORT_PREDICTIVE_BACK
        }

    /**
     * 调试日志开关，默认值按构建类型：internal 内测版默认开启（内测重在收集排查信息），
     * 正式版默认关闭。关闭 = 普通用户模式（AppLog 只输出警告/错误）；
     * 开启 = 排查问题模式（额外输出 D 级详细日志）。
     * 用户手动改过后写入 DataStore，之后始终以存储值为准（默认值只在键缺失时兜底）。
     */
    val debugLogging: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.DEBUG_LOGGING] ?: BuildConfig.INTERNAL_BUILD }

    /**
     * 日志落盘开关，默认值同 [debugLogging]：internal 内测版默认开启、正式版默认关闭。
     * 关闭时不写任何运行日志文件（崩溃报告始终记录）；开启后 D/W/E 追加写入 `files/logs/`，
     * 按天一个文件、保留 3 天。由 AfdianApplication 订阅后写入 LogStore，进程内即时生效。
     */
    val logToFile: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.LOG_TO_FILE] ?: BuildConfig.INTERNAL_BUILD }

    /** 是否记录浏览历史，默认开启；关闭只暂停新增，不删除已有记录。 */
    val historyEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.HISTORY_ENABLED] ?: true }

    /** 金额类数据卡是否显示「元」单位，默认关闭（只显示数字）。 */
    val showIncomeUnit: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.SHOW_INCOME_UNIT] ?: false }

    /** 人数/次数类数据卡是否显示「人」「次」单位，默认关闭（只显示数字）。 */
    val showCountUnit: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.SHOW_COUNT_UNIT] ?: false }

    /**
     * 数据卡数字是否播放「逐位滚动」动画，默认开启。
     *
     * 关闭后数字回退为改动前的普通文本显示（整串 Text + 自适应字号）。
     * 由 MainActivity 订阅后以组合局部
     * [com.hrsthrt74.afdian.ui.components.LocalRollingNumberEnabled] 下发，卡片直接读它，
     * 开关一变就必然重组并切换渲染方式。
     */
    val rollingNumberEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ROLLING_NUMBER] ?: true }

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
                    // 「近5天收入」没有旧版分散开关可比对，一律按枚举声明的默认值（关闭）
                    HomeModule.RecentIncome -> module.defaultEnabled
                    HomeModule.History -> true
                }
            }.toSet()
        }
    }
    /**
     * 首页模块顺序；过滤未知模块并补上未来新增模块，避免升级后模块消失。
     * 新补入的模块插到它在枚举声明顺序里的前驱之后（见 [mergeHomeModuleOrder]），
     * 这样新增模块的位置符合预期，而不是一律排到末尾。
     */
    val homeModuleOrder: Flow<List<HomeModule>> = context.settingsDataStore.data.map { preferences ->
        val saved = preferences[Keys.HOME_MODULE_ORDER]
            ?.split(",")
            ?.mapNotNull(HomeModule::fromStorage)
            .orEmpty()
        mergeHomeModuleOrder(saved)
    }

    /** 管理页布局；未知卡片会被过滤，用户关闭的卡片不会被自动重新加入。 */
    val managementLayout: Flow<List<ManagementLayoutEntry>> = context.settingsDataStore.data.map { preferences ->
        val stored = preferences[Keys.MANAGEMENT_LAYOUT]
            ?.let { runCatching { Json.decodeFromString<List<ManagementLayoutEntry>>(it) }.getOrNull() }
            ?: defaultManagementLayout
        // 旧版本布局中的卡片键已不再认识时直接过滤掉，不做旧键转换。
        // 不再要求布局必须含数据卡：编辑态已保证至少保留一张卡片，
        // 且设置页有「重置布局」兜底。
        stored.filter { it.isKnownLayoutEntry() }
    }

    suspend fun setHomeCreatorData(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_CREATOR_DATA] = enabled } }
    suspend fun setHomeSponsoring(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_SPONSORING] = enabled } }
    suspend fun setHomeMarkedCreators(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_MARKED_CREATORS] = enabled } }
    suspend fun setHomeWithdrawalReminder(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.HOME_WITHDRAWAL_REMINDER] = enabled } }
    suspend fun setHomeCreatorDataLayout(layout: List<ManagementLayoutEntry>) {
        AppLog.d("Settings", "保存首页创作者数据卡布局，项目数=${layout.size}")
        context.settingsDataStore.edit { it[Keys.HOME_CREATOR_LAYOUT] = Json.encodeToString(layout) }
    }

    /**
     * 重置首页创作者数据卡片布局为默认顺序。
     * 直接删除 key 而非写入默认值：读取端在 key 缺失时自动回落 [defaultHomeCreatorDataLayout]，
     * 语义上回到「未自定义」状态，日后默认布局升级也能自动跟上。
     */
    suspend fun resetHomeCreatorDataLayout() {
        AppLog.d("Settings", "重置首页创作者数据卡布局为默认")
        context.settingsDataStore.edit { it.remove(Keys.HOME_CREATOR_LAYOUT) }
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

    /**
     * 重置管理页卡片布局为默认顺序与分组。
     * 同 [resetHomeCreatorDataLayout]：删 key 让读取端回落 [defaultManagementLayout]。
     */
    suspend fun resetManagementLayout() {
        AppLog.d("Settings", "重置管理页数据卡片布局为默认")
        context.settingsDataStore.edit { it.remove(Keys.MANAGEMENT_LAYOUT) }
    }

    /** 「每月汇总」页的金额口径，默认抽成前（与爱发电网页版月度账单口径一致）。 */
    val monthlySummaryAmountMode: Flow<IncomeAmountMode> =
        context.settingsDataStore.data.map { IncomeAmountMode.fromStorage(it[Keys.MONTHLY_SUMMARY_AMOUNT_MODE]) }

    suspend fun setMonthlySummaryAmountMode(mode: IncomeAmountMode) {
        AppLog.d("Settings", "保存每月汇总金额口径，mode=${mode.storageValue}")
        context.settingsDataStore.edit { it[Keys.MONTHLY_SUMMARY_AMOUNT_MODE] = mode.storageValue }
    }

    /**
     * 图片缓存（Coil 全局磁盘缓存）的体积上限。
     *
     * 注意这里是**构建期**配置：上限只能交给 `DiskCache.Builder.maxSizeBytes`，
     * 由 Coil 自己的 LRU 在写入时淘汰最早的条目；改档位要重建 ImageLoader 才生效，
     * 所以界面上标注「重启后生效」（见 MainActivity 里 ImageLoader 的构建）。
     */
    val imageCacheLimit: Flow<ImageCacheLimit> =
        context.settingsDataStore.data.map { ImageCacheLimit.fromStorage(it[Keys.IMAGE_CACHE_LIMIT]) }

    /** 外部爱发电链接默认使用 Custom Tabs；开启后优先尝试官方客户端。 */
    val preferOfficialClient: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.PREFER_OFFICIAL_CLIENT] ?: false }

    suspend fun setImageCacheLimit(limit: ImageCacheLimit) {
        AppLog.d("Settings", "保存图片缓存上限，limit=${limit.storageValue}（重启后生效）")
        context.settingsDataStore.edit { it[Keys.IMAGE_CACHE_LIMIT] = limit.storageValue }
    }

    suspend fun setPreferOfficialClient(enabled: Boolean) {
        AppLog.d("Settings", "官方客户端链接跳转已${if (enabled) "开启" else "关闭"}")
        context.settingsDataStore.edit { it[Keys.PREFER_OFFICIAL_CLIENT] = enabled }
    }

    suspend fun setDebugLogging(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DEBUG_LOGGING] = enabled }
    }

    /** 写入「日志落盘」开关；生效链路见 [logToFile] 注释。 */
    suspend fun setLogToFile(enabled: Boolean) {
        AppLog.d("Settings", "日志落盘已${if (enabled) "开启" else "关闭"}")
        context.settingsDataStore.edit { it[Keys.LOG_TO_FILE] = enabled }
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

    /** 写入「数字滚动动画」开关；生效链路见 [rollingNumberEnabled] 注释。 */
    suspend fun setRollingNumberEnabled(enabled: Boolean) {
        AppLog.d("Settings", "数字滚动动画已${if (enabled) "开启" else "关闭"}")
        context.settingsDataStore.edit { it[Keys.ROLLING_NUMBER] = enabled }
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

    /** 写入底部导航栏样式档位（0=常规 1=悬浮），立即生效无需重启 */
    suspend fun setBottomBarStyle(style: Int) {
        AppLog.d("Settings", "保存底栏样式，style=$style")
        context.settingsDataStore.edit { it[Keys.BOTTOM_BAR_STYLE] = style }
    }

    suspend fun setColorMode(mode: Int) {
        context.settingsDataStore.edit { it[Keys.COLOR_MODE] = mode }
    }

    suspend fun setMonetEnabled(enabled: Boolean) {
        AppLog.d("Settings", "Monet 配色已${if (enabled) "开启" else "关闭"}")
        context.settingsDataStore.edit { it[Keys.MONET_ENABLED] = enabled }
    }

    suspend fun setKeyColor(preset: KeyColorPreset) {
        AppLog.d("Settings", "保存 Monet 种子色，preset=${preset.storageValue}")
        context.settingsDataStore.edit { it[Keys.KEY_COLOR] = preset.storageValue }
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
