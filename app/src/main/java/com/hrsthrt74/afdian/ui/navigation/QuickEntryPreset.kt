package com.hrsthrt74.afdian.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Th1
import top.yukonga.miuix.kmp.icon.extended.Th10
import top.yukonga.miuix.kmp.icon.extended.Th11
import top.yukonga.miuix.kmp.icon.extended.Th12
import top.yukonga.miuix.kmp.icon.extended.Th2
import top.yukonga.miuix.kmp.icon.extended.Th3
import top.yukonga.miuix.kmp.icon.extended.Th4
import top.yukonga.miuix.kmp.icon.extended.Th5
import top.yukonga.miuix.kmp.icon.extended.Th6
import top.yukonga.miuix.kmp.icon.extended.Th7
import top.yukonga.miuix.kmp.icon.extended.Th8
import top.yukonga.miuix.kmp.icon.extended.Th9
import top.yukonga.miuix.kmp.icon.extended.Tune
import java.time.YearMonth

/**
 * 快捷方式分组：既是自定义面板的分区，也是「我的」页快捷入口区的分区标题。
 * 声明顺序即面板与页面里的展示顺序。
 */
enum class QuickEntryGroup(val title: String) {
    /** 通用功能：发电、收藏、订单、浏览历史 */
    COMMON("常用"),

    /** 创作者后台功能 */
    CREATOR("创作者"),

    /** 账号与应用设置 */
    ACCOUNT("账号"),
}

/**
 * 快捷方式预设目录：全 App「点一下就跳某个页面」的入口都在这里登记一次。
 *
 * 「我的」页快捷入口区、首页「快捷方式」模块与它的自定义面板共用这一份目录，
 * 新增入口只在这里加一行，三处自动同步，不会出现「面板能选、页面没有」的情况。
 *
 * 只收「目标是一个 [Route]」的入口。需要自己干点别的动作（刷新、开外链……）的入口
 * 不进目录，在调用点直接用 [com.hrsthrt74.afdian.ui.components.basic.QuickEntry]
 * 手写 onClick 即可——组件与预设是两回事，别为了统一而硬塞。
 *
 * @param icon 静态图标；null 表示图标要按业务动态算，见 [resolveIcon]。
 */
enum class QuickEntryPreset(
    val storageValue: String,
    val title: String,
    val group: QuickEntryGroup,
    val route: Route,
    val icon: ImageVector? = null,
) {
    // ===== 常用 =====
    Sponsoring("sponsoring", "我发电的", QuickEntryGroup.COMMON, Route.Sponsoring, MiuixIcons.Demibold.Favorites),
    Favorites("favorites", "我的收藏", QuickEntryGroup.COMMON, Route.Favorites, Icons.Filled.BookmarkBorder),
    Orders("orders", "我的订单", QuickEntryGroup.COMMON, Route.Orders, MiuixIcons.Medium.Notes),
    BrowsingHistory("browsing_history", "浏览历史", QuickEntryGroup.COMMON, Route.History, Icons.Filled.History),

    // ===== 创作者 =====
    Management("management", "管理面板", QuickEntryGroup.CREATOR, Route.Management, MiuixIcons.Demibold.All),
    IncomeDetail("income_detail", "收入明细", QuickEntryGroup.CREATOR, Route.IncomeDetail, MiuixIcons.Demibold.ListView),
    ReceivedSponsors("received_sponsors", "收到发电", QuickEntryGroup.CREATOR, Route.ReceivedSponsors, MiuixIcons.Demibold.Store),
    SponsorManage("sponsor_manage", "赞助者管理", QuickEntryGroup.CREATOR, Route.SponsorManage, MiuixIcons.Demibold.ContactsCircle),
    Shop("shop", "售卖商品", QuickEntryGroup.CREATOR, Route.Shop, MiuixIcons.Demibold.Backup),
    Affiliates("affiliates", "优惠码", QuickEntryGroup.CREATOR, Route.Affiliates, MiuixIcons.Demibold.Rename),
    /** 图标是「上个月」的月份数字，随月份变化，故不静态给图标 */
    MonthlySummary("monthly_summary", "每月汇总", QuickEntryGroup.CREATOR, Route.MonthlySummary),

    // ===== 账号 =====
    Settings("settings", "设置", QuickEntryGroup.ACCOUNT, Route.Settings, MiuixIcons.Demibold.Settings),
    Blocklist("blocklist", "屏蔽与拒绝名单", QuickEntryGroup.ACCOUNT, Route.Blocklist, MiuixIcons.Demibold.Blocklist),
    Developer("developer", "开发者", QuickEntryGroup.ACCOUNT, Route.Developer, MiuixIcons.Demibold.Tune),
    ;

    companion object {
        fun fromStorage(value: String): QuickEntryPreset? = entries.firstOrNull { it.storageValue == value }
    }
}

/** 该分组下的全部预设，保持枚举声明顺序。 */
fun QuickEntryGroup.presets(): List<QuickEntryPreset> = QuickEntryPreset.entries.filter { it.group == this }

/**
 * 取预设入口的图标。
 *
 * 「每月汇总」的图标是上个月的月份数字，必须每次取值时才算：枚举的构造参数只在类加载时
 * 求值一次，写死在构造参数里的话，App 跨月不重启就会一直显示旧月份。
 */
fun QuickEntryPreset.resolveIcon(): ImageVector = icon ?: lastMonthNumberIcon()

/** 「每月汇总」入口的图标：Miuix 的 ThN 数字图标，取上个月（本月 9 月 → 8）。 */
private fun lastMonthNumberIcon(): ImageVector =
    when (YearMonth.now().minusMonths(1).monthValue) {
        1 -> MiuixIcons.Th1
        2 -> MiuixIcons.Th2
        3 -> MiuixIcons.Th3
        4 -> MiuixIcons.Th4
        5 -> MiuixIcons.Th5
        6 -> MiuixIcons.Th6
        7 -> MiuixIcons.Th7
        8 -> MiuixIcons.Th8
        9 -> MiuixIcons.Th9
        10 -> MiuixIcons.Th10
        11 -> MiuixIcons.Th11
        // 剩下只可能是 12 月
        else -> MiuixIcons.Th12
    }

/** 首页「快捷方式」模块的槽位数：一行放 4 个，与「我的」页快捷入口一致。 */
const val QUICK_ENTRY_SLOTS = 4

/** 未配置过时的默认 4 个入口。
 *  TODO: 「我的」页加入更多基础功能之后，把这里改成跟我的页首行不一样的 */
val defaultHomeQuickEntries: List<QuickEntryPreset> = listOf(
    QuickEntryPreset.Sponsoring,
    QuickEntryPreset.Favorites,
    QuickEntryPreset.Orders,
    QuickEntryPreset.BrowsingHistory,
)

/**
 * 把落盘的快捷方式存储值解析成预设列表：
 * 丢掉目录里已经不存在的值（手改配置或日后删条目）→ 去重 → 截断到 [QUICK_ENTRY_SLOTS]。
 *
 * 全部落空（没配过 / 存的值全不认识）时回落 [defaultHomeQuickEntries]：
 * 模块至少要有东西可显示，否则首页会凭空少一块，用户还以为坏了。
 */
internal fun normalizeQuickEntries(saved: List<String>): List<QuickEntryPreset> =
    saved.asSequence()
        .mapNotNull(QuickEntryPreset::fromStorage)
        .distinct()
        .take(QUICK_ENTRY_SLOTS)
        .toList()
        .ifEmpty { defaultHomeQuickEntries }
