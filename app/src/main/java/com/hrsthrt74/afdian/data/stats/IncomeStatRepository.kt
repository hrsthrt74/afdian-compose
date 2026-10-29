package com.hrsthrt74.afdian.data.stats

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.ai.AiPromptBuilder
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 收入明细的统计类型，对应 /api/my/stat 的 type 参数。
 * day=按日，week=按周，month=按月；周/月类型的 date_str 为该周期起始日（YYYYMMDD）。
 */
enum class IncomeStatType(val apiValue: String, val title: String) {
    Day("day", "按日"),
    Week("week", "按周"),
    Month("month", "按月"),
}

/** 单条统计记录（收入明细表格的一行）。 */
data class IncomeStatRecord(
    // 接口原始日期字符串，格式 YYYYMMDD
    val dateStr: String,
    // 页面访问人数（uv）
    val uv: Long,
    // 实际发电金额（元）
    val amount: BigDecimal,
    // 发电订单次数
    val orderCount: Long,
    // 发电人数（去重）
    val userCount: Long,
    // 老客发电人数
    val oldUserCount: Long,
) {
    /**
     * 日期对应的星期汉字（一..日）；dateStr 不是合法的 YYYYMMDD 时为 null。
     * 在构造时解析一次并缓存：同一行在滚动、重组、切 tab 时会被反复读取，不必每次重算。
     * 解析失败（如月份 13）只让星期缺失，不影响其余字段展示。
     */
    private val weekdayLabel: String? = runCatching {
        LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE)
    }.getOrNull()?.let { date -> weekdayLabelOf(date) }

    /**
     * 完整日期：把 20260906 格式化成 2026/09/06；异常格式原样返回。
     * 「收入明细」页表格已改用短年份的 [displayShortYearDate]，
     * 本属性目前只用于 CSV 导出与「问 AI」的 Markdown——导出数据保留完整年份，避免歧义。
     */
    val displayDate: String
        get() = if (dateStr.length == 8) {
            "${dateStr.substring(0, 4)}/${dateStr.substring(4, 6)}/${dateStr.substring(6, 8)}"
        } else {
            dateStr
        }

    /**
     * 短年份日期：把 20260906 格式化成 26/09/06，省略年份前两位「20」。
     * 「收入明细」页的按周/按月用它（按日改用 [displayShortYearDateWithWeekday]）；
     * 比完整年份窄，近几十年内不会产生歧义。异常格式原样返回，与 [displayDate] 口径一致。
     */
    val displayShortYearDate: String
        get() = if (dateStr.length == 8) {
            "${dateStr.substring(2, 4)}/${dateStr.substring(4, 6)}/${dateStr.substring(6, 8)}"
        } else {
            dateStr
        }

    /**
     * 带星期的短年份日期：26/10/16 五（日期与星期之间一个半角空格）。
     * 只给「收入明细」页的「按日」维度用——按周/按月显示的是周期起始日，星期没有信息量。
     * 日期非法时退回 [displayShortYearDate]，不把解析失败暴露到 UI。
     */
    val displayShortYearDateWithWeekday: String
        get() = weekdayLabel?.let { "$displayShortYearDate $it" } ?: displayShortYearDate

    /**
     * 紧凑日期：只保留月/日（09/06），不显示年份、不带星期。
     * 首页「近5天收入」模块改用 [displayMonthDayWithWeekday]（同样省年份，但要看星期几）；
     * 本属性保留作无星期时的兜底与测试基准。异常格式原样返回，与 [displayDate] 口径一致。
     */
    val displayMonthDay: String
        get() = if (dateStr.length == 8) {
            "${dateStr.substring(4, 6)}/${dateStr.substring(6, 8)}"
        } else {
            dateStr
        }

    /**
     * 带星期的紧凑日期：09/06 五（月/日 + 一个半角空格 + 星期汉字）。
     * 首页「近5天收入」模块用——它的日期列比收入明细页窄，既省年份也要看星期几。
     * 日期非法时退回 [displayMonthDay]，不把解析失败暴露到 UI。
     */
    val displayMonthDayWithWeekday: String
        get() = weekdayLabel?.let { "$displayMonthDay $it" } ?: displayMonthDay
}

/** 一次分页请求的结果：当前页数据 + 是否还有下一页（null=接口未返回，未知）。 */
data class IncomeStatPage(val list: List<IncomeStatRecord>, val hasMore: Boolean?)

/**
 * 创作者后台「收入明细」数据仓库。
 * 数据源：GET https://afdian.com/api/my/stat?page=N&type=day|week|month，Cookie auth_token 认证。
 */
class IncomeStatRepository(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient = sharedAfdianHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = AfdianHttpClient(client)

    /**
     * 拉取指定类型与页码的统计数据。
     * 网络请求放在 Dispatchers.IO，业务失败（ec != 200）转成异常交由 ViewModel 呈现错误态。
     */
    suspend fun fetch(type: IncomeStatType, page: Int): IncomeStatPage = withContext(Dispatchers.IO) {
        // 拼接请求 URL：路径参数 + auth_token 查询参数（与 Cookie 双重认证，同站内其他接口做法）
        val url = (AFDIAN_HOST + "/api/my/stat").toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("type", type.apiValue)
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        // 日志只记路径与参数，绝不输出 auth_token
        AppLog.d(TAG, "请求收入明细 page=$page type=${type.apiValue}")
        val start = System.currentTimeMillis()
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "/api/my/stat -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("收入明细加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, "/api/my/stat", "收入明细加载失败", requireEc = true)
            AppLog.d(TAG, "/api/my/stat -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            val data = root["data"]?.jsonObject ?: error("收入明细返回结构异常")
            val list = data["list"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toRecord() }
            // has_more 可能缺失（该接口翻页行为未被充分验证），缺失时记为 null 表示「未知」
            IncomeStatPage(list = list, hasMore = data.long("has_more")?.let { it == 1L })
        }
    }

    /** 把接口返回的单个 JSON 对象解析成表格行记录；date_str 缺失或非法时丢弃该行。 */
    private fun JsonObject.toRecord(): IncomeStatRecord? {
        val date = string("date_str")
        if (date.isBlank()) return null
        return IncomeStatRecord(
            dateStr = date,
            uv = long("uv") ?: 0L,
            amount = money("paid_order_real_amount") ?: BigDecimal.ZERO,
            orderCount = long("paid_order_count") ?: 0L,
            userCount = long("paid_user_count") ?: 0L,
            oldUserCount = long("paid_old_user_count") ?: 0L,
        )
    }

    /**
     * 拉取指定类型的全量统计数据：从第 1 页循环翻页直到数据拉完。
     * 翻页终止条件以「空页」为主（接口的 has_more 可能缺失，不可全信）：
     * 页面非空就继续，返回空页或显式 has_more=false 才停。页数有上限防护。
     */
    suspend fun fetchAll(type: IncomeStatType): List<IncomeStatRecord> {
        val result = mutableListOf<IncomeStatRecord>()
        var page = 1
        while (page <= MAX_PAGE_COUNT) {
            val pageData = fetch(type, page)
            result += pageData.list
            // 空页说明已经拉完；显式 has_more=false 也停（缺失=null 时继续尝试下一页）
            if (pageData.list.isEmpty() || pageData.hasMore == false) break
            page++
        }
        if (page > MAX_PAGE_COUNT) {
            AppLog.w(TAG, "/api/my/stat 全量拉取超过 $MAX_PAGE_COUNT 页，已提前截断")
        }
        AppLog.d(TAG, "fetchAll ok: type=${type.apiValue}, total=${result.size}")
        return result
    }

    /**
     * 生成「问 AI」用的文本：内置提示词 + 全量数据的 Markdown 表格，不做时间过滤。
     * 提示词在前、数据在后，粘给 AI 即可直接分析。
     *
     * 提示词与表格的构建统一委托给 [AiPromptBuilder]——应用内「问 AI」直调与
     * 本复制方案共用同一份文案与数据口径，改动只发生在那一处。
     */
    suspend fun fetchAllMarkdown(type: IncomeStatType): String {
        val records = fetchAll(type)
        return AiPromptBuilder.incomeAnalysisPrompt(type, records)
    }

    /** 生成导出用的 CSV 文本：全量数据 + 中文表头，带 UTF-8 BOM 防止 Excel 打开乱码。 */
    suspend fun fetchAllCsv(type: IncomeStatType): String {
        val records = fetchAll(type)
        return buildCsv(records)
    }

    /** 拼 CSV：首行中文表头，字段不含逗号无需转义；开头的 BOM 让 Excel 正确识别 UTF-8。 */
    private fun buildCsv(records: List<IncomeStatRecord>): String = buildString {
        append("\uFEFF")
        append("日期,访问人数,发电金额,发电次数,发电人数,老客数\n")
        for (record in records) {
            append("${record.displayDate},${record.uv},${record.amount.toPlainString()},${record.orderCount},${record.userCount},${record.oldUserCount}\n")
        }
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    /** 数值字段兼容 int / string 两种返回形态。 */
    private fun JsonObject.long(key: String): Long? = string(key).let { value ->
        value.toLongOrNull() ?: value.toDoubleOrNull()?.toLong()
    }

    /** 金额字段是字符串，转 BigDecimal 便于展示时保留两位小数。 */
    private fun JsonObject.money(key: String): BigDecimal? = string(key).toBigDecimalOrNull()

    private companion object {
        const val TAG = "IncomeStatRepository"
        const val AFDIAN_HOST = "https://afdian.com"
        /** 全量拉取的页数上限，超过即告警截断 */
        const val MAX_PAGE_COUNT = 100
    }
}
