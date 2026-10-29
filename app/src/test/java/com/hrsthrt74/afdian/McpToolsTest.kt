package com.hrsthrt74.afdian

import com.hrsthrt74.afdian.data.mcp.McpDataContext
import com.hrsthrt74.afdian.data.mcp.McpToolCatalog
import com.hrsthrt74.afdian.data.mcp.McpToolDefinition
import com.hrsthrt74.afdian.data.mcp.buildMcpTools
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MCP 工具集的参数解析与分发测试：用假取数上下文（不登录、不发网络请求），
 * 锁住「参数容错规则」与「上下文收到的实际值」。
 */
class McpToolsTest {

    /** 假上下文：记录每次调用收到的参数，返回固定文本。 */
    private class FakeContext : McpDataContext {
        var incomeType: String? = null
        var incomeMaxPages: Int? = null
        var summaryYear: Int? = null
        var summaryMonth: Int? = null
        var sponsorsPage: Int? = null
        var ordersStatus: String? = null
        var ordersPage: Int? = null

        override suspend fun userInfo(): String = "user"

        override suspend fun incomeStats(type: String, maxPages: Int): String {
            incomeType = type
            incomeMaxPages = maxPages
            return "income"
        }

        override suspend fun monthlySummary(year: Int?, month: Int?): String {
            summaryYear = year
            summaryMonth = month
            return "summary"
        }

        override suspend fun recentSponsors(page: Int): String {
            sponsorsPage = page
            return "sponsors"
        }

        override suspend fun myOrders(status: String, page: Int): String {
            ordersStatus = status
            ordersPage = page
            return "orders"
        }
    }

    private val fake = FakeContext()
    private val tools: Map<String, McpToolDefinition> =
        buildMcpTools(fake).associateBy { it.name }

    /** 按工具名与 JSON 字符串参数执行 handler（参数为空时给空对象）。 */
    private fun call(name: String, arguments: String): String = runBlocking {
        val args: JsonObject = if (arguments.isBlank()) {
            buildJsonObject { }
        } else {
            Json.parseToJsonElement(arguments) as JsonObject
        }
        tools.getValue(name).handler(args)
    }

    // ── 工具目录 ──

    @Test
    fun `五个工具全部注册`() {
        assertEquals(
            setOf(
                McpToolCatalog.USER_INFO,
                McpToolCatalog.INCOME_STATS,
                McpToolCatalog.MONTHLY_SUMMARY,
                McpToolCatalog.RECENT_SPONSORS,
                McpToolCatalog.MY_ORDERS,
            ),
            tools.keys,
        )
        // 每个工具都带非空描述与 schema
        tools.values.forEach { tool ->
            assertTrue(tool.description.isNotBlank())
            assertTrue(tool.inputSchema.containsKey("type"))
        }
    }

    // ── get_income_stats 参数容错 ──

    @Test
    fun `收入统计 非法维度回退按日`() = runBlocking {
        call(McpToolCatalog.INCOME_STATS, """{"type":"bogus"}""")
        assertEquals("day", fake.incomeType)
    }

    @Test
    fun `收入统计 页数夹取到 1-10`() = runBlocking {
        call(McpToolCatalog.INCOME_STATS, """{"max_pages":99}""")
        assertEquals(10, fake.incomeMaxPages)
        call(McpToolCatalog.INCOME_STATS, """{"max_pages":0}""")
        assertEquals(1, fake.incomeMaxPages)
    }

    // ── get_monthly_summary 组合规则 ──

    @Test
    fun `月度账单 只传 month 视为无效组合按全量处理`() = runBlocking {
        call(McpToolCatalog.MONTHLY_SUMMARY, """{"month":3}""")
        assertEquals(null, fake.summaryYear)
        assertEquals(null, fake.summaryMonth)
    }

    @Test
    fun `月度账单 年月齐全才过滤`() = runBlocking {
        call(McpToolCatalog.MONTHLY_SUMMARY, """{"year":2026,"month":3}""")
        assertEquals(2026, fake.summaryYear)
        assertEquals(3, fake.summaryMonth)
    }

    @Test
    fun `月度账单 非法月份被忽略`() = runBlocking {
        call(McpToolCatalog.MONTHLY_SUMMARY, """{"year":2026,"month":13}""")
        assertEquals(2026, fake.summaryYear)
        assertEquals(null, fake.summaryMonth)
    }

    // ── get_recent_sponsors 分页夹取 ──

    @Test
    fun `收到发电 页码夹取到 1-100`() = runBlocking {
        call(McpToolCatalog.RECENT_SPONSORS, """{"page":-5}""")
        assertEquals(1, fake.sponsorsPage)
        call(McpToolCatalog.RECENT_SPONSORS, """{"page":999}""")
        assertEquals(100, fake.sponsorsPage)
    }

    // ── get_my_orders 状态映射 ──

    @Test
    fun `我的订单 默认查已支付`() = runBlocking {
        call(McpToolCatalog.MY_ORDERS, "{}")
        assertEquals("2", fake.ordersStatus)
    }

    @Test
    fun `我的订单 unpaid 映射到接口数字码 1`() = runBlocking {
        call(McpToolCatalog.MY_ORDERS, """{"status":"unpaid"}""")
        assertEquals("1", fake.ordersStatus)
    }
}
