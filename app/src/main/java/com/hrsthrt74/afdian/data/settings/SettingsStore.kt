package com.hrsthrt74.afdian.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.auth.CurrentAccount
import com.hrsthrt74.afdian.data.content.PinnedDialogCard
import com.hrsthrt74.afdian.data.stats.ChartCatalog
import com.hrsthrt74.afdian.data.stats.DataCardCatalog
import com.hrsthrt74.afdian.data.stats.IncomeAmountMode
import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * 数据卡数值字体（MiSans Condensed 可变字体）的 wdth（宽度）轴合法区间与默认值。
 *
 * 字体自身的 fvar 轴是 10–200，这里刻意只放开**偏窄的一半（10–100）**：
 * 该字体定位就是窄体数字，100 以上越走越宽、与「窄体」的意图相悖；
 * 上限收到 100 也让滑块的每一档都落在有意义的区间里。
 * 默认值 60 是产品选定的视觉档位，比 100 更窄，符合「窄体数字」的预期。
 * 定义在数据层，UI 侧（ui/theme/DataCardFont.kt）与渲染侧统一引用，避免多处各写一份魔数。
 */
const val DATACARD_FONT_WIDTH_MIN = 10
const val DATACARD_FONT_WIDTH_MAX = 100
const val DATACARD_FONT_WIDTH_DEFAULT = 60

/**
 * 数据卡字体宽度滑块的**档位间隔**（wdth 单位）：每 10 个单位一档。
 *
 * 于是 10–100 一共 10 个可选档位（10 / 20 / … / 100）。
 * 为什么是 10 而不是 1：可变轴的视觉差异要跨到 10 个单位才明显，
 * 1 单位一档对用户来说是「拖到哪都一样」，手感黏、也停不准想要的档位。
 */
const val DATACARD_FONT_WIDTH_STEP = 10

/**
 * 传给 miuix `Slider(steps = ...)` 的值。
 *
 * miuix 的 `steps` 指的是**两端点之间的档位数**，所以要减 2：
 * 档位数 =(MAX - MIN) / STEP = 9 段 → steps = 8（对应 10 个可选档位）。
 * 单独抽成常量是为了让「档位间隔 → steps」的换算只写一处，并由单测锁住。
 */
const val DATACARD_FONT_WIDTH_SLIDER_STEPS =
    (DATACARD_FONT_WIDTH_MAX - DATACARD_FONT_WIDTH_MIN) / DATACARD_FONT_WIDTH_STEP - 1

/**
 * 把任意宽度值**归一化**到合法档位：先夹进 [DATACARD_FONT_WIDTH_MIN]..[DATACARD_FONT_WIDTH_MAX]，
 * 再吸附到最近的 [DATACARD_FONT_WIDTH_STEP] 整数倍档位。
 *
 * 读取 DataStore、写入 DataStore、构建字体族三处都调用它，于是「存储值 / 滑块显示的档位 /
 * 字体实际用的轴值」三者永远一致。归一化而不是单纯夹取的原因：
 * 档位本身是 10 单位一档，非档位值（例如上个版本 1 单位滑块留下的 63）属于非法值 ——
 * 若只是夹取，界面会显示一个滑块停不住的 63，看着像档位设置没生效。
 */
fun normalizeDataCardFontWidth(width: Int): Int {
    val clamped = width.coerceIn(DATACARD_FONT_WIDTH_MIN, DATACARD_FONT_WIDTH_MAX)
    val stepsFromMin = ((clamped - DATACARD_FONT_WIDTH_MIN).toFloat() / DATACARD_FONT_WIDTH_STEP)
        .roundToInt()
    return (DATACARD_FONT_WIDTH_MIN + stepsFromMin * DATACARD_FONT_WIDTH_STEP)
        .coerceIn(DATACARD_FONT_WIDTH_MIN, DATACARD_FONT_WIDTH_MAX)
}

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
    QuickEntries("quick_entries", "快捷方式", "自定义 4 个常用入口"),
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

/**
 * 置顶会话快照专用的 JSON 配置：忽略未知字段。
 *
 * 用默认 Json 的话，日后给 [PinnedDialogCard] 删/改名一个字段，旧数据就会解析失败，
 * 读取端按「无置顶」兜底 → 用户的置顶会静默消失。忽略未知字段可以让旧数据继续可读。
 */
private val pinnedCardsJson = Json { ignoreUnknownKeys = true }

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
    // 分类日志开关：数字滚轮动画日志（tag=Odometer）默认关闭——每个数字位都打一条，量极大，
    // 排查滚动动画问题时再打开。后续新增的「量大但偶发需要」的日志分类在此继续追加 key
    val ODOMETER_LOGGING = booleanPreferencesKey("odometer_logging")
    // 日志落盘开关：开启后运行日志（D/W/E）追加写入 files/logs/ 按天文件（崩溃报告不受此开关控制）
    val LOG_TO_FILE = booleanPreferencesKey("log_to_file")
    // 数据卡单位：金额类（收入、可提现）是否显示「元」
    val SHOW_INCOME_UNIT = booleanPreferencesKey("show_income_unit")
    // 数据卡单位：人数/次数类（发电人数、发电次数、访问人数、访问次数）是否显示「人」「次」
    val SHOW_COUNT_UNIT = booleanPreferencesKey("show_count_unit")
    // 数据卡数字动画：是否使用「逐位滚动」的滚轮效果；关闭则回退为普通文本显示
    val ROLLING_NUMBER = booleanPreferencesKey("rolling_number")
        // 数据卡数值是否使用窄体可变字体 MiSans Condensed（默认关闭 = 系统默认字体）
        val DATACARD_CUSTOM_FONT = booleanPreferencesKey("datacard_custom_font")
        // 数据卡窄体字体的 wdth 宽度轴（10–100，默认 60）
        val DATACARD_FONT_WIDTH = intPreferencesKey("datacard_font_width")
        // 浏览历史开关：关闭后不再写入新的历史记录或头像缓存
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        // 首页账号切换器：开启后已登录多账号时，在首页顶栏起始侧显示当前账号头像
        val HOME_ACCOUNT_SWITCHER = booleanPreferencesKey("home_account_switcher")
        // 以下「首页布局 / 管理页布局 / 消息页」类设置是账号维度的，键名见 AccountKeys
        // 上一版实现只存了 user_id 集合（没有卡片数据，无法补位渲染）。写入新格式时顺手清掉
        val MSG_PINNED_DIALOGS_LEGACY = stringSetPreferencesKey("msg_pinned_dialogs")
        // 图片缓存（Coil 全局磁盘缓存）的体积上限，存 ImageCacheLimit.storageValue
        val IMAGE_CACHE_LIMIT = androidx.datastore.preferences.core.stringPreferencesKey("image_cache_limit")
        // 外部爱发电链接是否优先交给官方客户端，默认关闭（优先使用 Custom Tabs）
        val PREFER_OFFICIAL_CLIENT = booleanPreferencesKey("prefer_official_client")
    }

    /**
     * 账号维度设置的基础键名。
     *
     * 实际存储键为 `<基础名>@<user_id>`：同一个设置项每个账号各存一份，
     * 换账号后自动读到自己那份，互不影响。
     *
     * 判断标准：**内容属于账号自己**的配置才放这里（首页模块可见性、卡片布局、
     * 置顶会话、金额口径…）。主题、字体、手势、日志、缓存上限这些跟设备走，留在 [Keys]。
     */
    private object AccountKeys {
        const val HOME_CREATOR_DATA = "home_creator_data"
        const val HOME_SPONSORING = "home_sponsoring"
        const val HOME_MARKED_CREATORS = "home_marked_creators"
        const val HOME_WITHDRAWAL_REMINDER = "home_withdrawal_reminder"
        const val HOME_CREATOR_LAYOUT = "home_creator_layout"
        const val HOME_ENABLED_MODULES = "home_enabled_modules"
        const val HOME_MODULE_ORDER = "home_module_order"
        const val HOME_QUICK_ENTRY_KEYS = "home_quick_entries"
        const val MANAGEMENT_LAYOUT = "management_layout"
        const val MONTHLY_SUMMARY_AMOUNT_MODE = "monthly_summary_amount_mode"
        const val MSG_SHOW_SPONSOR_DETAIL = "msg_show_sponsor_detail"
        const val MSG_PINNED_DIALOG_CARDS = "msg_pinned_dialog_cards"

        /** 全部基础名，用于迁移前判断「还有没有旧版本的全局键」 */
        val ALL = listOf(
            HOME_CREATOR_DATA, HOME_SPONSORING, HOME_MARKED_CREATORS, HOME_WITHDRAWAL_REMINDER,
            HOME_CREATOR_LAYOUT, HOME_ENABLED_MODULES, HOME_MODULE_ORDER, HOME_QUICK_ENTRY_KEYS,
            MANAGEMENT_LAYOUT, MONTHLY_SUMMARY_AMOUNT_MODE, MSG_SHOW_SPONSOR_DETAIL,
            MSG_PINNED_DIALOG_CARDS,
        )
    }

    /** 账号维度键名：`<基础名>@<user_id>`；账号未确定时后缀为空，读到的是默认值。 */
    private fun accountKeyName(base: String, owner: String?): String = "$base@${owner.orEmpty()}"

    /** 账号维度设置的读取入口：账号身份变化时自动切换订阅，读取该账号自己的分区。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> accountSetting(read: (owner: String?) -> Flow<T>): Flow<T> =
        CurrentAccount.userId.flatMapLatest { owner -> read(owner) }

    /**
     * 账号维度设置的写入入口：未确定账号时拒绝写入。
     *
     * 拒绝而不是写进「空账号」分区：这些配置只可能在已登录界面被修改，
     * 万一登录态与界面不同步，写到无主分区也只会变成读不到的死数据。
     */
    private suspend fun editAccountSettings(block: (MutablePreferences, owner: String) -> Unit) {
        val owner = CurrentAccount.userId.value?.takeIf { it.isNotBlank() }
        if (owner == null) {
            AppLog.w("Settings", "账号身份未就绪，已拒绝本次账号级设置写入")
            return
        }
        context.settingsDataStore.edit { block(it, owner) }
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
     * 数字滚轮日志开关，默认关闭：滚轮动画每次数值刷新都会给每个数字位打一条 D 日志，
     * 随便滑两页首页就是上千行，正常排查用不上、还会稀释其他日志，故默认静音，
     * 排查滚动动画问题时再打开。仅当调试日志开启时才有意义；
     * 由 MainActivity 订阅后组装进 AppLog.mutedTags，进程内即时生效。
     */
    val odometerLogging: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ODOMETER_LOGGING] ?: false }

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

    /**
     * 首页顶栏是否显示账号切换器，默认关闭。
     *
     * 只在「已登录账号数 > 1」时才有意义：单账号没有可切换的对象，
     * 首页与设置页都会据此把入口整块隐藏（判断在 UI 层做，存储不关心账号数量）。
     */
    val homeAccountSwitcherEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.HOME_ACCOUNT_SWITCHER] ?: false }

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

    /**
     * 数据卡数值是否使用窄体可变字体 MiSans Condensed，默认关闭。
     *
     * 关闭时 [dataCardFontWidth] 不参与渲染，数值沿用系统默认字体（与改动前完全一致）。
     * 由 MainActivity 订阅后以组合局部
     * [com.hrsthrt74.afdian.ui.theme.LocalDataCardFontFamily] 下发，
     * 卡片读到它就会订阅，开关一变必然重组并换字体。
     */
    val dataCardCustomFontEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.DATACARD_CUSTOM_FONT] ?: false }

    /**
     * 数据卡窄体字体的 wdth（宽度）轴，默认 [DATACARD_FONT_WIDTH_DEFAULT]。
     *
     * 读取时归一化到合法档位（夹取 + 吸附到 10 单位档），
     * 于是旧版本 1 单位滑块留下的非档位值（如 63）在这里就被收成 60，
     * 界面显示的档位与字体实际轴值不会打架。
     */
    val dataCardFontWidth: Flow<Int> = context.settingsDataStore.data.map {
        normalizeDataCardFontWidth(it[Keys.DATACARD_FONT_WIDTH] ?: DATACARD_FONT_WIDTH_DEFAULT)
    }

    /**
     * 首页模块开关（账号维度）。
     *
     * 这四个分散布尔值只服务旧版本数据：新版本把开关统一存进 [homeEnabledModules] 的集合，
     * 保留读取是为了升级用户不丢配置，同时也是迁移的来源。
     */
    val homeCreatorData: Flow<Boolean> = accountSetting { owner ->
        context.settingsDataStore.data.map {
            it[booleanPreferencesKey(accountKeyName(AccountKeys.HOME_CREATOR_DATA, owner))] ?: true
        }
    }
    val homeSponsoring: Flow<Boolean> = accountSetting { owner ->
        context.settingsDataStore.data.map {
            it[booleanPreferencesKey(accountKeyName(AccountKeys.HOME_SPONSORING, owner))] ?: true
        }
    }
    val homeMarkedCreators: Flow<Boolean> = accountSetting { owner ->
        context.settingsDataStore.data.map {
            it[booleanPreferencesKey(accountKeyName(AccountKeys.HOME_MARKED_CREATORS, owner))] ?: true
        }
    }
    val homeWithdrawalReminder: Flow<Boolean> = accountSetting { owner ->
        context.settingsDataStore.data.map {
            it[booleanPreferencesKey(accountKeyName(AccountKeys.HOME_WITHDRAWAL_REMINDER, owner))] ?: true
        }
    }
    /** 首页创作者数据布局，和管理页共用卡片、图表、标题、分割线布局元素。 */
    val homeCreatorDataLayout: Flow<List<ManagementLayoutEntry>> = accountSetting { owner ->
        context.settingsDataStore.data.map { preferences ->
            preferences[stringPreferencesKey(accountKeyName(AccountKeys.HOME_CREATOR_LAYOUT, owner))]
                ?.let { runCatching { Json.decodeFromString<List<ManagementLayoutEntry>>(it) }.getOrNull() }
                ?.filter { it.isKnownLayoutEntry() }
                ?: defaultHomeCreatorDataLayout
        }
    }
    /** 首页启用模块集合；未写入时从旧版分散布尔开关迁移，确保升级不丢用户配置。 */
    val homeEnabledModules: Flow<Set<HomeModule>> = accountSetting { owner ->
        context.settingsDataStore.data.map { preferences ->
            val stored = preferences[stringPreferencesKey(accountKeyName(AccountKeys.HOME_ENABLED_MODULES, owner))]
            if (stored != null) {
                // 已有用户配置也自动补入新增模块，避免历史记录模块升级后悄悄消失。
                stored.split(",").mapNotNull(HomeModule::fromStorage).toSet()
            } else {
                // 旧版分散开关同样按账号分区读取（迁移会把它们搬进当前账号）
                fun legacy(base: String) =
                    preferences[booleanPreferencesKey(accountKeyName(base, owner))] ?: true
                HomeModule.entries.filter { module ->
                    when (module) {
                        HomeModule.QuickEntries -> true
                        HomeModule.CreatorData -> legacy(AccountKeys.HOME_CREATOR_DATA)
                        HomeModule.Sponsoring -> legacy(AccountKeys.HOME_SPONSORING)
                        HomeModule.MarkedCreators -> legacy(AccountKeys.HOME_MARKED_CREATORS)
                        HomeModule.WithdrawalReminder -> legacy(AccountKeys.HOME_WITHDRAWAL_REMINDER)
                        // 「近5天收入」没有旧版分散开关可比对，一律按枚举声明的默认值（关闭）
                        HomeModule.RecentIncome -> module.defaultEnabled
                        HomeModule.History -> true
                    }
                }.toSet()
            }
        }
    }
    /**
     * 首页模块顺序；过滤未知模块并补上未来新增模块，避免升级后模块消失。
     * 新补入的模块插到它在枚举声明顺序里的前驱之后（见 [mergeHomeModuleOrder]），
     * 这样新增模块的位置符合预期，而不是一律排到末尾。
     */
    val homeModuleOrder: Flow<List<HomeModule>> = accountSetting { owner ->
        context.settingsDataStore.data.map { preferences ->
            val saved = preferences[stringPreferencesKey(accountKeyName(AccountKeys.HOME_MODULE_ORDER, owner))]
                ?.split(",")
                ?.mapNotNull(HomeModule::fromStorage)
                .orEmpty()
            mergeHomeModuleOrder(saved)
        }
    }

    /**
     * 首页「快捷方式」模块选中的入口，按顺序存 [com.hrsthrt74.afdian.ui.navigation.QuickEntryPreset.storageValue]。
     *
     * 这里只当字符串存取，不认识具体预设：过滤未知值、去重、截断到 4 个与默认值回落
     * 统一交给 `normalizeQuickEntries`（与预设目录同文件、可单测），设置层不必依赖 UI 枚举。
     */
    val homeQuickEntryKeys: Flow<List<String>> = accountSetting { owner ->
        context.settingsDataStore.data.map { preferences ->
            preferences[stringPreferencesKey(accountKeyName(AccountKeys.HOME_QUICK_ENTRY_KEYS, owner))]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                .orEmpty()
        }
    }

    /** 管理页布局；未知卡片会被过滤，用户关闭的卡片不会被自动重新加入。 */
    val managementLayout: Flow<List<ManagementLayoutEntry>> = accountSetting { owner ->
        context.settingsDataStore.data.map { preferences ->
            val stored = preferences[stringPreferencesKey(accountKeyName(AccountKeys.MANAGEMENT_LAYOUT, owner))]
                ?.let { runCatching { Json.decodeFromString<List<ManagementLayoutEntry>>(it) }.getOrNull() }
                ?: defaultManagementLayout
            // 旧版本布局中的卡片键已不再认识时直接过滤掉，不做旧键转换。
            // 不再要求布局必须含数据卡：编辑态已保证至少保留一张卡片，
            // 且设置页有「重置布局」兜底。
            stored.filter { it.isKnownLayoutEntry() }
        }
    }

    suspend fun setHomeCreatorData(enabled: Boolean) = editAccountSettings { prefs, owner ->
        prefs[booleanPreferencesKey(accountKeyName(AccountKeys.HOME_CREATOR_DATA, owner))] = enabled
    }
    suspend fun setHomeSponsoring(enabled: Boolean) = editAccountSettings { prefs, owner ->
        prefs[booleanPreferencesKey(accountKeyName(AccountKeys.HOME_SPONSORING, owner))] = enabled
    }
    suspend fun setHomeMarkedCreators(enabled: Boolean) = editAccountSettings { prefs, owner ->
        prefs[booleanPreferencesKey(accountKeyName(AccountKeys.HOME_MARKED_CREATORS, owner))] = enabled
    }
    suspend fun setHomeWithdrawalReminder(enabled: Boolean) = editAccountSettings { prefs, owner ->
        prefs[booleanPreferencesKey(accountKeyName(AccountKeys.HOME_WITHDRAWAL_REMINDER, owner))] = enabled
    }
    suspend fun setHomeCreatorDataLayout(layout: List<ManagementLayoutEntry>) {
        AppLog.d("Settings", "保存首页创作者数据卡布局，项目数=${layout.size}")
        editAccountSettings { prefs, owner ->
            prefs[stringPreferencesKey(accountKeyName(AccountKeys.HOME_CREATOR_LAYOUT, owner))] =
                Json.encodeToString(layout)
        }
    }

    /**
     * 重置首页创作者数据卡片布局为默认顺序。
     * 直接删除 key 而非写入默认值：读取端在 key 缺失时自动回落 [defaultHomeCreatorDataLayout]，
     * 语义上回到「未自定义」状态，日后默认布局升级也能自动跟上。
     */
    suspend fun resetHomeCreatorDataLayout() {
        AppLog.d("Settings", "重置首页创作者数据卡布局为默认")
        editAccountSettings { prefs, owner ->
            prefs.remove(stringPreferencesKey(accountKeyName(AccountKeys.HOME_CREATOR_LAYOUT, owner)))
        }
    }
    suspend fun setHomeEnabledModules(modules: Set<HomeModule>) = editAccountSettings { prefs, owner ->
        prefs[stringPreferencesKey(accountKeyName(AccountKeys.HOME_ENABLED_MODULES, owner))] =
            modules.joinToString(",") { module -> module.storageValue }
    }
    suspend fun setHomeModuleOrder(order: List<HomeModule>) = editAccountSettings { prefs, owner ->
        prefs[stringPreferencesKey(accountKeyName(AccountKeys.HOME_MODULE_ORDER, owner))] =
            order.joinToString(",") { module -> module.storageValue }
    }

    /** 保存首页「快捷方式」模块选中的入口，参数是预设的 storageValue（顺序即展示顺序）。 */
    suspend fun setHomeQuickEntryKeys(keys: List<String>) {
        AppLog.d("Settings", "保存首页快捷方式，入口数=${keys.size}")
        editAccountSettings { prefs, owner ->
            prefs[stringPreferencesKey(accountKeyName(AccountKeys.HOME_QUICK_ENTRY_KEYS, owner))] =
                keys.joinToString(",")
        }
    }

    suspend fun setManagementLayout(layout: List<ManagementLayoutEntry>) {
        AppLog.d("Settings", "保存管理页数据卡片布局，项目数=${layout.size}")
        editAccountSettings { prefs, owner ->
            prefs[stringPreferencesKey(accountKeyName(AccountKeys.MANAGEMENT_LAYOUT, owner))] =
                Json.encodeToString(layout)
        }
    }

    /**
     * 重置管理页卡片布局为默认顺序与分组。
     * 同 [resetHomeCreatorDataLayout]：删 key 让读取端回落 [defaultManagementLayout]。
     */
    suspend fun resetManagementLayout() {
        AppLog.d("Settings", "重置管理页数据卡片布局为默认")
        editAccountSettings { prefs, owner ->
            prefs.remove(stringPreferencesKey(accountKeyName(AccountKeys.MANAGEMENT_LAYOUT, owner)))
        }
    }

    /** 「每月汇总」页的金额口径，默认抽成前（与爱发电网页版月度账单口径一致）。 */
    val monthlySummaryAmountMode: Flow<IncomeAmountMode> = accountSetting { owner ->
        context.settingsDataStore.data.map {
            IncomeAmountMode.fromStorage(
                it[stringPreferencesKey(accountKeyName(AccountKeys.MONTHLY_SUMMARY_AMOUNT_MODE, owner))]
            )
        }
    }

    suspend fun setMonthlySummaryAmountMode(mode: IncomeAmountMode) {
        AppLog.d("Settings", "保存每月汇总金额口径，mode=${mode.storageValue}")
        editAccountSettings { prefs, owner ->
            prefs[stringPreferencesKey(accountKeyName(AccountKeys.MONTHLY_SUMMARY_AMOUNT_MODE, owner))] =
                mode.storageValue
        }
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

    /** 写入「数字滚轮日志」开关；生效链路见 [odometerLogging] 注释。 */
    suspend fun setOdometerLogging(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ODOMETER_LOGGING] = enabled }
    }

    /** 写入「日志落盘」开关；生效链路见 [logToFile] 注释。 */
    suspend fun setLogToFile(enabled: Boolean) {
        AppLog.d("Settings", "日志落盘已${if (enabled) "开启" else "关闭"}")
        context.settingsDataStore.edit { it[Keys.LOG_TO_FILE] = enabled }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HISTORY_ENABLED] = enabled }
    }

    /** 写入「首页账号切换器」开关；DataStore 回推后首页顶栏立即显隐，无需重启。 */
    suspend fun setHomeAccountSwitcherEnabled(enabled: Boolean) {
        AppLog.d("Settings", "首页账号切换器已${if (enabled) "开启" else "关闭"}")
        context.settingsDataStore.edit { it[Keys.HOME_ACCOUNT_SWITCHER] = enabled }
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

    /** 写入「数据卡数值窄体字体」开关；生效链路见 [dataCardCustomFontEnabled] 注释。 */
    suspend fun setDataCardCustomFontEnabled(enabled: Boolean) {
        AppLog.d("Settings", "数据卡数值字体已${if (enabled) "开启" else "关闭"}")
        context.settingsDataStore.edit { it[Keys.DATACARD_CUSTOM_FONT] = enabled }
    }

    /** 写入数据卡数值字体宽度（wdth）；写入前归一化到合法档位，避免非法/非档位值落盘。 */
    suspend fun setDataCardFontWidth(width: Int) {
        val safe = normalizeDataCardFontWidth(width)
        AppLog.d("Settings", "保存数据卡字体宽度，wdth=$safe")
        context.settingsDataStore.edit { it[Keys.DATACARD_FONT_WIDTH] = safe }
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
    val msgShowSponsorDetail: Flow<Boolean> = accountSetting { owner ->
        context.settingsDataStore.data.map {
            it[booleanPreferencesKey(accountKeyName(AccountKeys.MSG_SHOW_SPONSOR_DETAIL, owner))] ?: false
        }
    }

    suspend fun setMsgShowSponsorDetail(enabled: Boolean) = editAccountSettings { prefs, owner ->
        prefs[booleanPreferencesKey(accountKeyName(AccountKeys.MSG_SHOW_SPONSOR_DETAIL, owner))] = enabled
    }

    /**
     * 消息页置顶的会话卡片列表（顺序即列表里的置顶顺序，新置顶的在前）。
     *
     * 爱发电没有会话置顶接口，这里只做本地持久化；但置顶是「这个账号关心哪些会话」，
     * 所以按账号分区存储 —— 换账号后互不可见，换设备 / 清数据仍会丢。
     * 解析失败（旧版本字段改名、手工改坏）按「没有置顶」处理，不让坏数据把消息页搞崩；
     * 空白 userId 的条目直接丢弃——它在列表里既打不开也无法取消置顶。
     */
    val msgPinnedDialogCards: Flow<List<PinnedDialogCard>> = accountSetting { owner ->
        context.settingsDataStore.data.map { preferences ->
            preferences[stringPreferencesKey(accountKeyName(AccountKeys.MSG_PINNED_DIALOG_CARDS, owner))]
                ?.let { json ->
                    runCatching { pinnedCardsJson.decodeFromString<List<PinnedDialogCard>>(json) }
                        .onFailure { AppLog.w("Settings", "置顶会话解析失败，按无置顶处理", it) }
                        .getOrNull()
                }
                ?.filter { it.userId.isNotBlank() }
                .orEmpty()
        }
    }

    /**
     * 写入置顶会话卡片列表。
     *
     * 列表为空时直接删 key 而不是写 `[]`：语义上回到「用户从未设置过置顶」，
     * 与首页布局重置（删 key 让读取端回落默认）保持同一套约定。
     * 同时清掉上一版只存 id 集合的遗留键，避免 DataStore 里长期留着读不到的死数据。
     */
    suspend fun setMsgPinnedDialogCards(cards: List<PinnedDialogCard>) {
        AppLog.d("Settings", "保存消息页置顶会话，数量=${cards.size}")
        editAccountSettings { preferences, owner ->
            preferences.remove(Keys.MSG_PINNED_DIALOGS_LEGACY)
            val key = stringPreferencesKey(accountKeyName(AccountKeys.MSG_PINNED_DIALOG_CARDS, owner))
            if (cards.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = pinnedCardsJson.encodeToString(cards)
            }
        }
    }

    /**
     * 把升级前「全局」的账号级设置迁移到指定账号名下。
     *
     * 旧版本这些键不带账号后缀，内容必然属于升级前最后登录的那个账号；
     * 新版本第一次确定账号身份时把它们搬进该账号的分区，避免老用户升级后
     * 首页布局、置顶会话等配置凭空消失。
     *
     * 幂等：旧键不存在时什么都不做；目标账号已有同名键时不覆盖，只删旧键。
     */
    suspend fun migrateLegacyAccountSettings(ownerUserId: String) {
        val owner = ownerUserId.takeIf { it.isNotBlank() } ?: return
        // 先读一次判断有没有旧键：正常（已迁移过）的情况下直接返回，
        // 避免每次首页刷新都触发一次「没有变化」的写盘。
        val hasLegacy = context.settingsDataStore.data.first()
            .asMap().keys.any { it.name in AccountKeys.ALL }
        if (!hasLegacy) return
        context.settingsDataStore.edit { preferences ->
            var migrated = 0
            // 旧版分散的首页模块开关
            if (preferences.migrateBoolean(AccountKeys.HOME_CREATOR_DATA, owner)) migrated++
            if (preferences.migrateBoolean(AccountKeys.HOME_SPONSORING, owner)) migrated++
            if (preferences.migrateBoolean(AccountKeys.HOME_MARKED_CREATORS, owner)) migrated++
            if (preferences.migrateBoolean(AccountKeys.HOME_WITHDRAWAL_REMINDER, owner)) migrated++
            // 首页布局 / 快捷方式 / 管理页布局 / 金额口径 / 置顶会话
            if (preferences.migrateString(AccountKeys.HOME_CREATOR_LAYOUT, owner)) migrated++
            if (preferences.migrateString(AccountKeys.HOME_ENABLED_MODULES, owner)) migrated++
            if (preferences.migrateString(AccountKeys.HOME_MODULE_ORDER, owner)) migrated++
            if (preferences.migrateString(AccountKeys.HOME_QUICK_ENTRY_KEYS, owner)) migrated++
            if (preferences.migrateString(AccountKeys.MANAGEMENT_LAYOUT, owner)) migrated++
            if (preferences.migrateString(AccountKeys.MONTHLY_SUMMARY_AMOUNT_MODE, owner)) migrated++
            // 消息页
            if (preferences.migrateBoolean(AccountKeys.MSG_SHOW_SPONSOR_DETAIL, owner)) migrated++
            if (preferences.migrateString(AccountKeys.MSG_PINNED_DIALOG_CARDS, owner)) migrated++
            if (migrated > 0) {
                AppLog.d("Settings", "已把 $migrated 项旧版账号级设置迁移到当前账号")
            }
        }
    }

    /** 把一个旧的全局布尔设置搬到当前账号分区；返回是否真的发生了迁移。 */
    private fun MutablePreferences.migrateBoolean(base: String, owner: String): Boolean {
        val legacyKey = booleanPreferencesKey(base)
        val value = this[legacyKey] ?: return false
        val targetKey = booleanPreferencesKey(accountKeyName(base, owner))
        if (this[targetKey] == null) this[targetKey] = value
        remove(legacyKey)
        return true
    }

    /** 把一个旧的全局字符串设置搬到当前账号分区；返回是否真的发生了迁移。 */
    private fun MutablePreferences.migrateString(base: String, owner: String): Boolean {
        val legacyKey = stringPreferencesKey(base)
        val value = this[legacyKey] ?: return false
        val targetKey = stringPreferencesKey(accountKeyName(base, owner))
        if (this[targetKey] == null) this[targetKey] = value
        remove(legacyKey)
        return true
    }

    suspend fun acceptDisclaimer() {
        AppLog.d("Settings", "保存免责声明同意状态")
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
