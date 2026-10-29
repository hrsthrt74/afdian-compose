package com.hrsthrt74.afdian.data.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.ai.AiConfig
import com.hrsthrt74.afdian.data.ai.AiConfigDraft
import com.hrsthrt74.afdian.data.ai.AiKeyStore
import com.hrsthrt74.afdian.data.ai.AiProviderPreset
import com.hrsthrt74.afdian.data.ai.AiReasoningEffort
import com.hrsthrt74.afdian.data.ai.AiThinkingMode
import com.hrsthrt74.afdian.data.ai.DefaultAiReasoningEffort
import com.hrsthrt74.afdian.data.ai.DefaultAiThinkingMode
import com.hrsthrt74.afdian.data.ai.isAiConfigReady
import com.hrsthrt74.afdian.data.ai.normalizeAiBaseUrl
import com.hrsthrt74.afdian.data.ai.normalizeAiModel
import com.hrsthrt74.afdian.data.auth.CurrentAccount
import com.hrsthrt74.afdian.data.content.PinnedDialogCard
import com.hrsthrt74.afdian.data.mcp.MCP_DEFAULT_PORT
import com.hrsthrt74.afdian.data.mcp.generateMcpToken
import com.hrsthrt74.afdian.data.mcp.normalizeMcpPort
import com.hrsthrt74.afdian.data.network.AfdianRoute
import com.hrsthrt74.afdian.data.stats.ChartCatalog
import com.hrsthrt74.afdian.data.stats.DataCardCatalog
import com.hrsthrt74.afdian.data.stats.IncomeAmountMode
import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import com.hrsthrt74.afdian.data.update.UpdateChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        // 旧版「使用爱发电主题色」开关：只读，作为主题色的迁移依据（见 themeColor），不再写入
        val USE_AFDIAN_COLOR = booleanPreferencesKey("use_afdian_color")
        val DISCLAIMER = booleanPreferencesKey("disclaimer")
        // 配色模式：0=跟随系统 1=浅色 2=深色
        val COLOR_MODE = intPreferencesKey("color_mode")
        // 主题样式：false=MIUIX（Miuix 静态色板） true=Monet（按壁纸 / 主题色生成色板）。
        // 键名沿用旧版的 monet_enabled，语义一致（「Monet 即开启 Monet 配色」），不用迁移数据
        val MONET_ENABLED = booleanPreferencesKey("monet_enabled")
        // 主题色预设，存 ThemeColorPreset.storageValue（不存下标，避免档位增删后错位）
        val THEME_COLOR = androidx.datastore.preferences.core.stringPreferencesKey("theme_color")
        // 自定义主题色：存 ARGB Int（ThemeColorPreset.Custom 用的具体色值）
        val CUSTOM_THEME_COLOR = intPreferencesKey("custom_theme_color")
        // 旧版的 Monet 种子色键：只读，用于把老用户的选择迁移到 THEME_COLOR，不再写入
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
        // 网络优先线路：存 AfdianRoute.storageValue（ifdian / afdian / www），默认 ifdian
        val NETWORK_ROUTE = stringPreferencesKey("network_route")
        // 检查更新：更新通道（stable = 正式版 Release / actions = master 的 CI 构建）
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        // 检查更新：上次**成功**检查的时间戳（毫秒），用于 24 小时节流
        val UPDATE_LAST_CHECK_AT = longPreferencesKey("update_last_check_at")
        // 检查更新：上次**失败**检查的时间戳（毫秒），用于 1 小时退避。
        // 与上一条分开是因为旧实现把失败也塞进 24 小时窗口，一次弱网就静默一整天
        val UPDATE_LAST_CHECK_FAILURE_AT = longPreferencesKey("update_last_check_failure_at")
        // 检查更新：正式版通道被忽略的 tag 名，空串表示未忽略
        val UPDATE_IGNORED_VERSION = stringPreferencesKey("update_ignored_version")
        // 检查更新：Actions 通道被忽略的构建号（0 = 未忽略）。
        // 只忽略这一条构建，下一个构建仍然会提示 —— Actions 通道没有「版本号」可以按 tag 记
        val UPDATE_IGNORED_ACTIONS_RUN = intPreferencesKey("update_ignored_actions_run")
        // ── AI 集成（BYOK，设备级设置）──
        // AI 供应商预设，存 AiProviderPreset.storageValue，默认 DeepSeek
        val AI_PRESET = stringPreferencesKey("ai_preset")
        // AI 服务的 OpenAI 兼容 Base URL（写入时已归一化，见 normalizeAiBaseUrl）
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        // AI API Key 的 AES/GCM 密文（AiKeyStore 加解密）；键缺失或空串 = 未配置
        val AI_API_KEY_CIPHER = stringPreferencesKey("ai_api_key_cipher")
        // AI 模型名（如 deepseek-chat）
        val AI_MODEL = stringPreferencesKey("ai_model")
        // 思考模式：default / enabled / disabled（default = 不发送任何思考参数）
        val AI_THINKING_MODE = stringPreferencesKey("ai_thinking_mode")
        // 思考强度：low / medium / high / max（仅思考模式为 enabled 时随请求发送）
        val AI_REASONING_EFFORT = stringPreferencesKey("ai_reasoning_effort")
        // ── MCP 本地服务器（设备级设置）──
        // 开关：默认关闭；开启后随应用进程监听 127.0.0.1
        val MCP_ENABLED = booleanPreferencesKey("mcp_enabled")
        // 监听端口（1024..65535，默认 8787）
        val MCP_PORT = intPreferencesKey("mcp_port")
        // 访问令牌密文（AiKeyStore 加密，与 AI Key 同一把设备级密钥）；键缺失 = 未生成
        val MCP_TOKEN_CIPHER = stringPreferencesKey("mcp_token_cipher")
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

    /** 免责声明是否已同意 */
    val disclaimerAccepted: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DISCLAIMER] ?: false }

    /**
     * 更新通道，默认正式版。
     *
     * 设备级设置：换账号不改变「我追正式版还是追 Actions 构建」这件事。
     */
    val updateChannel: Flow<UpdateChannel> =
        context.settingsDataStore.data.map { UpdateChannel.fromStorage(it[Keys.UPDATE_CHANNEL]) }

    /**
     * 上次**成功**检查更新的时间戳（毫秒）；0 表示从未成功检查过。
     * 用于 24 小时节流：冷启动时不至于每次都打一次 GitHub API。
     */
    val updateLastCheckAt: Flow<Long> =
        context.settingsDataStore.data.map { it[Keys.UPDATE_LAST_CHECK_AT] ?: 0L }

    /** 上次检查**失败**的时间戳（毫秒）；0 表示从未失败。用于 1 小时退避。 */
    val updateLastCheckFailureAt: Flow<Long> =
        context.settingsDataStore.data.map { it[Keys.UPDATE_LAST_CHECK_FAILURE_AT] ?: 0L }

    /** 用户选择「忽略此版本」的版本标识（GitHub Release 的 tag 名）；空串表示未忽略任何版本 */
    val updateIgnoredVersion: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.UPDATE_IGNORED_VERSION] ?: "" }

    /** Actions 通道被忽略的构建号；0 表示未忽略。只忽略这一条构建，不影响后续构建。 */
    val updateIgnoredActionsRun: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.UPDATE_IGNORED_ACTIONS_RUN] ?: 0 }

    /** 配色模式：0=跟随系统 1=浅色 2=深色，默认跟随系统 */
    val colorMode: Flow<Int> = context.settingsDataStore.data.map { it[Keys.COLOR_MODE] ?: 0 }

    /**
     * 主题样式：false=MIUIX（默认，Miuix 静态色板） true=Monet（按壁纸 / 主题色生成整套色板）。
     *
     * 两种主题共用 [colorMode] 的三档明暗选择：MIUIX 映射到 Light/Dark/System，
     * Monet 映射到 MonetLight/MonetDark/MonetSystem（详见 AfdianTheme 里的模式映射）。
     */
    val monetEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.MONET_ENABLED] ?: false }

    /**
     * 主题色预设，默认 MIUIX 蓝。
     *
     * 同一个值在两种主题下都生效：MIUIX 主题把它当主色染 Miuix 色板（MIUIX 蓝直接用官方默认色板），
     * Monet 主题把它当种子色。新键优先，没有新键时按旧版设置迁移一次：
     * 1) 旧「种子色」key_color 有值 → 直接沿用（旧的 blue 会并到 MIUIX 蓝）；
     * 2) 否则看旧的「使用爱发电主题色」开关：显式设为 true 的保留爱发电紫，
     *    其余（含从未设置过）走新的默认值 MIUIX 蓝。
     */
    val themeColor: Flow<ThemeColorPreset> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.THEME_COLOR]?.let(ThemeColorPreset::fromStorage)
            ?: prefs[Keys.KEY_COLOR]?.let(ThemeColorPreset::fromStorage)
            ?: if (prefs[Keys.USE_AFDIAN_COLOR] == true) ThemeColorPreset.Afdian else ThemeColorPreset.Default
    }

    /**
     * 自定义主题色的具体色值（ARGB Int），只在 [themeColor] 选中 [ThemeColorPreset.Custom] 时参与主题。
     *
     * 默认 50% 灰：第一次点「自定义」还没挑色时，主题不会跳成某个预设色。
     */
    val customThemeColor: Flow<Color> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_THEME_COLOR]?.let { Color(it) } ?: ThemeColorPreset.DefaultCustomColor
    }

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
     * 调试日志开关，默认值按构建类型：debug 构建默认开启（便于收集排查信息），
     * release 默认关闭。关闭 = 普通用户模式（AppLog 只输出警告/错误）；
     * 开启 = 排查问题模式（额外输出 D 级详细日志）。
     * 用户手动改过后写入 DataStore，之后始终以存储值为准（默认值只在键缺失时兜底）。
     */
    val debugLogging: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.DEBUG_LOGGING] ?: BuildConfig.DEBUG }

    /**
     * 数字滚轮日志开关，默认关闭：滚轮动画每次数值刷新都会给每个数字位打一条 D 日志，
     * 随便滑两页首页就是上千行，正常排查用不上、还会稀释其他日志，故默认静音，
     * 排查滚动动画问题时再打开。仅当调试日志开启时才有意义；
     * 由 MainActivity 订阅后组装进 AppLog.mutedTags，进程内即时生效。
     */
    val odometerLogging: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ODOMETER_LOGGING] ?: false }

    /**
     * 日志落盘开关，默认值同 [debugLogging]：debug 构建默认开启、release 默认关闭。
     * 关闭时不写任何运行日志文件（崩溃报告始终记录）；开启后 D/W/E 追加写入 `files/logs/`，
     * 按天一个文件、保留 3 天。由 AfdianApplication 订阅后写入 LogStore，进程内即时生效。
     */
    val logToFile: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.LOG_TO_FILE] ?: BuildConfig.DEBUG }

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
            // 且「设置 → 通用」有「重置布局」兜底。
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

    /**
     * 网络优先线路，默认 ifdian.net。
     *
     * 设备级设置：换账号不改变「我更信哪个域名」这件事，与主题、手势、缓存上限同类。
     * 取值只影响请求先打哪个域名，不影响分享链接与站内跳转的文案。
     */
    val networkRoute: Flow<AfdianRoute> =
        context.settingsDataStore.data.map { AfdianRoute.fromStorage(it[Keys.NETWORK_ROUTE]) }

    suspend fun setImageCacheLimit(limit: ImageCacheLimit) {
        AppLog.d("Settings", "保存图片缓存上限，limit=${limit.storageValue}（重启后生效）")
        context.settingsDataStore.edit { it[Keys.IMAGE_CACHE_LIMIT] = limit.storageValue }
    }

    suspend fun setPreferOfficialClient(enabled: Boolean) {
        AppLog.d("Settings", "官方客户端链接跳转已${if (enabled) "开启" else "关闭"}")
        context.settingsDataStore.edit { it[Keys.PREFER_OFFICIAL_CLIENT] = enabled }
    }

    /** 切换优先线路；DataStore 回推后 Application 的订阅立刻更新进程级全局值。 */
    suspend fun setNetworkRoute(route: AfdianRoute) {
        AppLog.d("Settings", "切换优先线路：${route.storageValue}")
        context.settingsDataStore.edit { it[Keys.NETWORK_ROUTE] = route.storageValue }
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
        AppLog.d("Settings", "主题已切换为 ${if (enabled) "Monet" else "MIUIX"}")
        context.settingsDataStore.edit { it[Keys.MONET_ENABLED] = enabled }
    }

    /** 保存主题色预设；两种主题都读这一个值，切换主题后选择保持不变 */
    suspend fun setThemeColor(preset: ThemeColorPreset) {
        AppLog.d("Settings", "保存主题色，preset=${preset.storageValue}")
        context.settingsDataStore.edit { it[Keys.THEME_COLOR] = preset.storageValue }
    }

    /** 保存自定义主题色的具体色值（调色盘确认后调用） */
    suspend fun setCustomThemeColor(color: Color) {
        AppLog.d("Settings", "保存自定义主题色，#${color.toArgb().toUInt().toString(16)}")
        context.settingsDataStore.edit { it[Keys.CUSTOM_THEME_COLOR] = color.toArgb() }
    }

    suspend fun setStartupPage(index: Int) {
        context.settingsDataStore.edit { it[Keys.STARTUP_PAGE] = index }
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

    /** 记录上次**成功**检查更新的时间（毫秒），用于 24 小时节流。失败时不要调它。 */
    suspend fun setUpdateLastCheckAt(timestampMs: Long) {
        context.settingsDataStore.edit { it[Keys.UPDATE_LAST_CHECK_AT] = timestampMs }
    }

    /** 记录上次检查**失败**的时间（毫秒），用于 1 小时退避。 */
    suspend fun setUpdateLastCheckFailureAt(timestampMs: Long) {
        context.settingsDataStore.edit { it[Keys.UPDATE_LAST_CHECK_FAILURE_AT] = timestampMs }
    }

    /** 切换更新通道。 */
    suspend fun setUpdateChannel(channel: UpdateChannel) {
        AppLog.d("Settings", "切换更新通道：${channel.storageValue}")
        context.settingsDataStore.edit { it[Keys.UPDATE_CHANNEL] = channel.storageValue }
    }

    /** 记录用户选择「忽略此版本」的版本标识（tag 名）；传空串表示取消忽略。 */
    suspend fun setUpdateIgnoredVersion(version: String) {
        AppLog.d("Settings", "忽略更新版本：$version")
        context.settingsDataStore.edit { it[Keys.UPDATE_IGNORED_VERSION] = version }
    }

    /** 记录用户忽略的 Actions 构建号；传 0 表示取消忽略。 */
    suspend fun setUpdateIgnoredActionsRun(runNumber: Int) {
        AppLog.d("Settings", "记录用户忽略的 Actions 构建号：$runNumber")
        context.settingsDataStore.edit { it[Keys.UPDATE_IGNORED_ACTIONS_RUN] = runNumber }
    }

    // ── AI 集成（BYOK）────────────────────────────────────────────────────
    //
    // 设备级设置：API Key 绑定的是「这台设备的用户买了哪家的服务」，与登录哪个
    // 爱发电账号无关，因此不进账号分区（对比 homeEnabledModules 那组）。

    /** AI 供应商预设，默认 DeepSeek（国内服务零门槛起步）。 */
    val aiPreset: Flow<AiProviderPreset> =
        context.settingsDataStore.data.map {
            AiProviderPreset.fromStorage(it[Keys.AI_PRESET]) ?: AiProviderPreset.DeepSeek
        }

    /** AI 服务的 Base URL（写入时已归一化）。 */
    val aiBaseUrl: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.AI_BASE_URL].orEmpty() }

    /** AI 模型名（读出时顺手 trim，容忍手抖存进的空白）。 */
    val aiModel: Flow<String> =
        context.settingsDataStore.data.map { normalizeAiModel(it[Keys.AI_MODEL].orEmpty()) }

    /** 是否已保存过 API Key（只看密文存在与否，不解密、不外露内容）。 */
    val aiHasApiKey: Flow<Boolean> =
        context.settingsDataStore.data.map { !it[Keys.AI_API_KEY_CIPHER].isNullOrBlank() }

    /**
     * 思考开关：默认开启。
     * 历史上存过 `default`（旧版「跟随服务端」档），这里读不到就落默认值——
     * 旧档位在设置页会显示为「开启 + 高」，即最接近服务端默认行为的一档。
     */
    val aiThinkingMode: Flow<AiThinkingMode> = context.settingsDataStore.data.map {
        AiThinkingMode.fromStorage(it[Keys.AI_THINKING_MODE]) ?: DefaultAiThinkingMode
    }

    /** 思考强度：默认高（与 DeepSeek 服务端默认 effort 对齐）。 */
    val aiReasoningEffort: Flow<AiReasoningEffort> = context.settingsDataStore.data.map {
        AiReasoningEffort.fromStorage(it[Keys.AI_REASONING_EFFORT]) ?: DefaultAiReasoningEffort
    }

    /**
     * AI 配置草稿（不含 Key 明文）：设置页展示与各功能页判断「能否发起请求」都用它。
     * 六个流合并成一个，订阅方不必各自拼装、也不必担心口径漂移。
     * combine 带类型 lambda 的重载最多 5 个流，这里拆两半再合（比统一 Array 版保类型）。
     */
    val aiConfigDraft: Flow<AiConfigDraft> = combine(
        combine(aiPreset, aiBaseUrl, aiModel) { preset, baseUrl, model ->
            Triple(preset, baseUrl, model)
        },
        combine(aiHasApiKey, aiThinkingMode, aiReasoningEffort) { hasKey, thinkingMode, effort ->
            Triple(hasKey, thinkingMode, effort)
        },
    ) { head, tail ->
        AiConfigDraft(
            preset = head.first,
            baseUrl = head.second,
            model = head.third,
            hasKey = tail.first,
            thinkingMode = tail.second,
            reasoningEffort = tail.third,
        )
    }

    /**
     * 组装发起请求用的完整配置（Key 解密为明文），配置不完整返回 null。
     *
     * 一次性读取而非 Flow：调用方在用户点「问 AI」等动作时取用即可，
     * Key 明文不应常驻任何状态流。
     */
    suspend fun aiConfig(): AiConfig? {
        val prefs = context.settingsDataStore.data.first()
        val preset = AiProviderPreset.fromStorage(prefs[Keys.AI_PRESET]) ?: AiProviderPreset.DeepSeek
        val baseUrl = prefs[Keys.AI_BASE_URL].orEmpty()
        val model = normalizeAiModel(prefs[Keys.AI_MODEL].orEmpty())
        val cipherText = prefs[Keys.AI_API_KEY_CIPHER].orEmpty()
        val hasKey = cipherText.isNotBlank()
        if (!isAiConfigReady(preset, baseUrl, model, hasKey)) return null
        // 自定义端点可能指向本地服务，允许无 Key：密文缺失与解密失败统一按「无鉴权头」处理
        val apiKey = if (hasKey) AiKeyStore.decrypt(cipherText).orEmpty() else ""
        return AiConfig(
            preset = preset,
            baseUrl = baseUrl,
            model = model,
            apiKey = apiKey,
            thinkingMode = AiThinkingMode.fromStorage(prefs[Keys.AI_THINKING_MODE]) ?: DefaultAiThinkingMode,
            reasoningEffort = AiReasoningEffort.fromStorage(prefs[Keys.AI_REASONING_EFFORT]) ?: DefaultAiReasoningEffort,
        )
    }

    /** 切换 AI 供应商预设；Base URL 与模型由设置页按需跟随填充（这里只存档位）。 */
    suspend fun setAiPreset(preset: AiProviderPreset) {
        AppLog.d("Settings", "保存 AI 供应商预设：${preset.storageValue}")
        context.settingsDataStore.edit { it[Keys.AI_PRESET] = preset.storageValue }
    }

    /**
     * 保存 AI Base URL；非法值（缺协议头等）拒绝写入并保留原值，
     * 校验与用户提示由设置页负责，这里是数据层的最后防线。
     */
    suspend fun setAiBaseUrl(raw: String) {
        val normalized = normalizeAiBaseUrl(raw)
        if (normalized == null) {
            AppLog.w("Settings", "AI Base URL 非法，已拒绝写入：${raw.take(80)}")
            return
        }
        AppLog.d("Settings", "保存 AI Base URL：$normalized")
        context.settingsDataStore.edit { it[Keys.AI_BASE_URL] = normalized }
    }

    /** 保存 AI 模型名（trim 后写入，不校验拼写——模型名各家规则不同）。 */
    suspend fun setAiModel(raw: String) {
        val normalized = normalizeAiModel(raw)
        AppLog.d("Settings", "保存 AI 模型名：$normalized")
        context.settingsDataStore.edit { it[Keys.AI_MODEL] = normalized }
    }

    /**
     * 保存 AI API Key（加密落盘）；传 null / 空白表示清除。
     * 日志只记动作不记内容——Key 是全文最敏感的字段，一个字符都不能进日志。
     */
    suspend fun setAiApiKey(plainKey: String?) {
        context.settingsDataStore.edit { prefs ->
            if (plainKey.isNullOrBlank()) {
                prefs.remove(Keys.AI_API_KEY_CIPHER)
            } else {
                prefs[Keys.AI_API_KEY_CIPHER] = AiKeyStore.encrypt(plainKey.trim())
            }
        }
        AppLog.d("Settings", "AI API Key 已${if (plainKey.isNullOrBlank()) "清除" else "更新"}")
    }

    /**
     * 一次性保存思考开关与强度（设置页的四档滑块用）。
     *
     * 两个字段必须在**同一个 edit** 里写完：分两次写会触发两次流发射，
     * 中间态（新开关 + 旧强度）会被界面反查成另一个档位，
     * 表现就是滑块先跳回上一档再滑到目标档。effort 为 null 表示关闭档，此时不动强度。
     */
    suspend fun setAiThinking(mode: AiThinkingMode, effort: AiReasoningEffort?) {
        AppLog.d(
            "Settings",
            "保存 AI 思考：${mode.storageValue}" + (effort?.let { " / ${it.storageValue}" } ?: ""),
        )
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.AI_THINKING_MODE] = mode.storageValue
            if (effort != null) prefs[Keys.AI_REASONING_EFFORT] = effort.storageValue
        }
    }

    // ── MCP 本地服务器 ───────────────────────────────────────────────────────

    /** MCP 服务器开关，默认关闭（用户显式开启后才有本地端口监听）。 */
    val mcpEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.MCP_ENABLED] ?: false }

    /** MCP 监听端口（读出时夹取到合法区间，防手工改坏的数据把服务器起不起来）。 */
    val mcpPort: Flow<Int> =
        context.settingsDataStore.data.map { normalizeMcpPort(it[Keys.MCP_PORT] ?: MCP_DEFAULT_PORT) }

    /** 是否已生成过访问令牌。 */
    val mcpHasToken: Flow<Boolean> =
        context.settingsDataStore.data.map { !it[Keys.MCP_TOKEN_CIPHER].isNullOrBlank() }

    /** 访问令牌（解密后的明文，供设置页展示复制与服务器鉴权比对）；未生成或解密失败返回 null。 */
    suspend fun mcpToken(): String? {
        val cipherText = context.settingsDataStore.data.first()[Keys.MCP_TOKEN_CIPHER]
            ?: return null
        return AiKeyStore.decrypt(cipherText)
    }

    /** 访问令牌流：解密后的明文（null=未生成）；McpServerController 订阅它做「令牌变更即重启」。 */
    val mcpTokenFlow: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.MCP_TOKEN_CIPHER]?.let { AiKeyStore.decrypt(it) }
    }

    suspend fun setMcpEnabled(enabled: Boolean) {
        AppLog.d("Settings", "MCP 服务器已${if (enabled) "开启" else "关闭"}")
        context.settingsDataStore.edit { it[Keys.MCP_ENABLED] = enabled }
    }

    /** 保存监听端口（写前夹取）；运行中的服务器由 Controller 检测变化后重启。 */
    suspend fun setMcpPort(port: Int) {
        val safe = normalizeMcpPort(port)
        AppLog.d("Settings", "保存 MCP 端口：$safe")
        context.settingsDataStore.edit { it[Keys.MCP_PORT] = safe }
    }

    /**
     * 取访问令牌，没有则生成一个新的（SecureRandom 32 位十六进制）并加密落盘。
     * 返回明文给调用方（设置页展示 / 复制）。重置 = 删掉旧密文后再次调用本方法。
     */
    suspend fun ensureMcpToken(): String {
        mcpToken()?.let { return it }
        val token = generateMcpToken()
        context.settingsDataStore.edit { it[Keys.MCP_TOKEN_CIPHER] = AiKeyStore.encrypt(token) }
        AppLog.d("Settings", "已生成新的 MCP 访问令牌")
        return token
    }

    /** 重置访问令牌：删除旧密文并生成新的，返回新明文。旧客户端的连接会全部 401。 */
    suspend fun resetMcpToken(): String {
        context.settingsDataStore.edit { it.remove(Keys.MCP_TOKEN_CIPHER) }
        AppLog.d("Settings", "已重置 MCP 访问令牌")
        return ensureMcpToken()
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
