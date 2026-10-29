package com.hrsthrt74.afdian.data.mcp

import android.app.Application
import com.hrsthrt74.afdian.data.ai.AiPromptBuilder
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.AccountService
import com.hrsthrt74.afdian.data.content.OrderService
import com.hrsthrt74.afdian.data.content.ReceivedSponsorService
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import com.hrsthrt74.afdian.data.stats.IncomeStatRepository
import com.hrsthrt74.afdian.data.stats.IncomeStatType
import com.hrsthrt74.afdian.data.stats.MonthlySummaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * MCP 工具的取数上下文：把「工具需要的数据」收拢成最小接口。
 *
 * 工具定义（[buildMcpTools]）只依赖本接口，真实实现走爱发电各领域 Service；
 * 单测注入假实现即可覆盖 tools/call 全流程，不需要登录态。
 *
 * 隐私红线（实现必须遵守）：
 * - 不提供私信/会话类数据（产品决策）；
 * - 收货地址、收件人电话、完整快递单号等个人敏感字段一律不进工具输出。
 */
interface McpDataContext {
    /** 当前账号资料（昵称、user_id、正在创作）。 */
    suspend fun userInfo(): String

    /**
     * 收入明细统计。
     * @param type day / week / month
     * @param maxPages 最多翻多少页（每页约 30 条，防止一次拉爆）
     */
    suspend fun incomeStats(type: String, maxPages: Int): String

    /** 月度账单；year/month 为 null 时返回全部月份。 */
    suspend fun monthlySummary(year: Int?, month: Int?): String

    /** 收到发电列表（指定页，每页约 20 条；不含收货信息）。 */
    suspend fun recentSponsors(page: Int): String

    /** 我发出的订单列表（我作为赞助者）。 */
    suspend fun myOrders(status: String, page: Int): String
}

/** 工具集描述：AI 客户端在 tools/list 里看到的目录，也是本文件自解释的索引。 */
object McpToolCatalog {
    const val USER_INFO = "get_user_info"
    const val INCOME_STATS = "get_income_stats"
    const val MONTHLY_SUMMARY = "get_monthly_summary"
    const val RECENT_SPONSORS = "get_recent_sponsors"
    const val MY_ORDERS = "get_my_orders"
}

/**
 * 构建暴露给外部 AI 客户端的工具集。
 *
 * 每个工具 = 名称 + 描述（写给模型看的用法说明）+ JSON Schema + 调用 [context] 取数。
 * 返回值统一是 Markdown 表格/文本（与 AiPromptBuilder 的数据口径同源），
 * 分析由外部模型完成，这里只负责如实给数据。
 */
fun buildMcpTools(context: McpDataContext): List<McpToolDefinition> = listOf(
    McpToolDefinition(
        name = McpToolCatalog.USER_INFO,
        description = "获取当前登录的爱发电账号资料（昵称、user_id、创作者简介）。查询其他数据前可先用它确认身份。",
        inputSchema = emptyObjectSchema(),
        handler = { _ -> context.userInfo() },
    ),
    McpToolDefinition(
        name = McpToolCatalog.INCOME_STATS,
        description = "获取创作者收入明细统计（访问人数、发电金额、发电次数、发电人数、老客数）。" +
            "数据按时间倒序，适合做趋势分析、转化率计算。type=day 按日（默认）、week 按周、month 按月。",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("type", buildJsonObject {
                    put("type", "string")
                    put("enum", stringArray("day", "week", "month"))
                    put("description", "统计维度：day/week/month，默认 day")
                })
                put("max_pages", buildJsonObject {
                    put("type", "integer")
                    put("description", "最多拉取的页数（每页约30条），默认 3，上限 10")
                })
            })
        },
        handler = { args ->
            // 参数容错：非法 type 回退按日；页数夹取到 1..10
            val type = when (args.optString("type").lowercase()) {
                "week" -> "week"
                "month" -> "month"
                else -> "day"
            }
            val maxPages = (args.optInt("max_pages") ?: 3).coerceIn(1, 10)
            context.incomeStats(type, maxPages)
        },
    ),
    McpToolDefinition(
        name = McpToolCatalog.MONTHLY_SUMMARY,
        description = "获取创作者月度账单（每月总收入、抽成后收入、发电人数）。不传参数返回全部月份，可按年月过滤。",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("year", intSchema("年份，如 2026；不传返回全部"))
                put("month", intSchema("月份 1-12；需与 year 一起传"))
            })
        },
        handler = { args ->
            val year = args.optInt("year")
            val month = args.optInt("month")?.takeIf { it in 1..12 }
            // 只传了 month 没传 year 视为无效组合，按全量处理而不是猜年份
            context.monthlySummary(year, if (year != null) month else null)
        },
    ),
    McpToolDefinition(
        name = McpToolCatalog.RECENT_SPONSORS,
        description = "获取创作者「收到的发电」订单列表（买家昵称、方案名、金额、留言、时间），按时间倒序分页。" +
            "注意：不含收货地址等个人信息。",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("page", intSchema("页码，从 1 开始，默认 1"))
            })
        },
        handler = { args ->
            val page = (args.optInt("page") ?: 1).coerceIn(1, 100)
            context.recentSponsors(page)
        },
    ),
    McpToolDefinition(
        name = McpToolCatalog.MY_ORDERS,
        description = "获取当前账号「我发电的」订单列表（我作为赞助者买过的方案与商品）。" +
            "接口没有「全部」筛选，默认查已支付订单。",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("status", buildJsonObject {
                    put("type", "string")
                    put("enum", stringArray("paid", "unpaid"))
                    put("description", "订单状态：paid 已支付（默认）/ unpaid 待支付")
                })
                put("page", intSchema("页码，从 1 开始，默认 1"))
            })
        },
        handler = { args ->
            // 爱发电订单接口的 status 是数字码：2=已支付、1=待支付（见 OrderService 注释）
            val status = if (args.optString("status").lowercase() == "unpaid") "1" else "2"
            val page = (args.optInt("page") ?: 1).coerceIn(1, 100)
            context.myOrders(status, page)
        },
    ),
)

// ── JSON Schema 构造小工具 ──────────────────────────────────────────────────

private fun emptyObjectSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject { })
}

private fun intSchema(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

/** 字符串数组的 JSON 字面量（enum / required 用）。 */
private fun stringArray(vararg values: String) = kotlinx.serialization.json.buildJsonArray {
    values.forEach { add(JsonPrimitive(it)) }
}

/**
 * 真实取数上下文：组合爱发电各领域 Service。
 *
 * 所有请求都要求登录态（auth_token）；未登录时统一抛 [McpToolException]，
 * 由协议层转成 isError 结果告诉外部模型「需要先在应用内登录」。
 */
class AfdianMcpDataContext(
    private val tokenStore: AuthTokenStore,
    private val client: okhttp3.OkHttpClient = sharedAfdianHttpClient,
) : McpDataContext {
    private val accountService = AccountService(tokenStore, client)
    private val incomeRepository = IncomeStatRepository(tokenStore, client)
    private val monthlyRepository = MonthlySummaryRepository(tokenStore, client)
    private val receivedSponsorService = ReceivedSponsorService(tokenStore, client)
    private val orderService = OrderService(tokenStore, client)

    /** 未登录的统一报错。 */
    private fun requireLogin() {
        if (tokenStore.token.isNullOrBlank()) {
            throw McpToolException("尚未登录爱发电账号，请先打开应用完成登录")
        }
    }

    override suspend fun userInfo(): String {
        requireLogin()
        val profile = accountService.fetchProfile()
        val user = profile.user
        return buildString {
            appendLine("当前账号：${user.name.ifBlank { "爱发电用户" }}")
            appendLine("user_id：${user.user_id.ifBlank { "未知" }}")
            user.url_slug?.takeIf { it.isNotBlank() }?.let { appendLine("主页 slug：$it") }
            user.creator?.doing?.takeIf { it.isNotBlank() }?.let { appendLine("正在创作：$it") }
        }.trim()
    }

    override suspend fun incomeStats(type: String, maxPages: Int): String {
        requireLogin()
        val statType = IncomeStatType.entries.firstOrNull { it.apiValue == type } ?: IncomeStatType.Day
        val records = withContext(Dispatchers.IO) {
            val result = mutableListOf<IncomeStatRecord>()
            for (page in 1..maxPages) {
                val pageData = incomeRepository.fetch(statType, page)
                result += pageData.list
                if (pageData.list.isEmpty() || pageData.hasMore == false) break
            }
            result
        }
        if (records.isEmpty()) return "指定范围内没有统计数据"
        return AiPromptBuilder.buildIncomeTable(records).trimEnd() +
            "\n\n（共 ${records.size} 条，最多拉取 $maxPages 页；如需更多数据请调大 max_pages）"
    }

    override suspend fun monthlySummary(year: Int?, month: Int?): String {
        requireLogin()
        val bills = withContext(Dispatchers.IO) { monthlyRepository.fetchMonthlyBills() }
            .sortedBy { it.month }
        val filtered = bills.filter { bill ->
            (year == null || bill.month.year == year) && (month == null || bill.month.monthValue == month)
        }
        if (filtered.isEmpty()) return "没有符合条件（year=$year, month=$month）的月度账单"
        return AiPromptBuilder.buildMonthlyBillsTable(filtered).trimEnd()
    }

    override suspend fun recentSponsors(page: Int): String {
        requireLogin()
        val result = withContext(Dispatchers.IO) {
            receivedSponsorService.fetchReceivedSponsors(page = page)
        }
        if (result.orders.isEmpty()) return "第 $page 页没有收到发电记录"
        return buildString {
            appendLine("| 时间 | 买家 | 方案 | 金额(元) | 留言 |")
            appendLine("|---|---|---|---|---|")
            for (order in result.orders) {
                val time = formatSeconds(order.paidAtSeconds)
                // 隐私红线：只输出昵称/方案/金额/留言，不含收件人与地址（见 McpDataContext 注释）
                appendLine(
                    "| $time | ${order.userName} | ${order.planName} | ${order.amount} " +
                        "| ${order.remark.orEmpty().take(40)} |",
                )
            }
            appendLine()
            append("共 ${result.orders.size} 条（第 $page 页）" + if (result.hasMore) "，还有下一页" else "，已到最后一页")
        }
    }

    override suspend fun myOrders(status: String, page: Int): String {
        requireLogin()
        // OrderService.load 是阻塞实现，包到 IO 线程避免占用 MCP 协程；status 已是接口数字码
        val result = withContext(Dispatchers.IO) {
            orderService.load(status, page)
        }
        if (result.orders.isEmpty()) return "没有符合条件（status=$status, page=$page）的订单"
        return buildString {
            appendLine("| 时间 | 商品/方案 | 规格 | 数量 | 金额(元) | 状态 | 创作者 |")
            appendLine("|---|---|---|---|---|---|---|")
            for (order in result.orders) {
                val time = formatSeconds(order.timeSeconds)
                appendLine(
                    "| $time | ${order.title} | ${order.skuName.orEmpty()} | ${order.skuCount} " +
                        "| ${order.amount} | ${order.status} | ${order.creator} |",
                )
            }
            appendLine()
            append("共 ${result.orders.size} 条（第 $page 页，总数 ${result.totalCount}）")
        }
    }

    /** Unix 秒 → 本地时区的「yyyy-MM-dd HH:mm」；空值显示「未知」。 */
    private fun formatSeconds(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return "未知"
        return Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
}

/** 工具集的快捷入口：构建默认（真实取数）的工具列表。 */
fun buildDefaultMcpTools(context: Application): List<McpToolDefinition> =
    buildMcpTools(AfdianMcpDataContext(AuthTokenStore(context)))
