package com.hrsthrt74.afdian.data.stats

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 创作者数据卡的数据仓库。
 *
 * 这里负责接口请求和数据聚合，UI 只消费已经按卡片键整理好的类型化值。
 * 同一批数据只请求一次，再在内存中生成不同时间范围的卡片，避免每张卡片各发一个请求。
 */
class CreatorStatsRepository(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * 加载管理页和首页共用的创作者数字卡与图表卡。
     * dashboard 是基础接口，失败时向上抛出；其他统计接口失败时只让对应卡片显示“--”。
     *
     * 图表序列复用同一次已抓取的原始数据（按日统计 / 月度账单）生成，不再额外发网络请求。
     */
    suspend fun loadOverview(
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): OverviewData = coroutineScope {
        val today = now.toLocalDate()
        val boundedPeriods = listOf(
            CardPeriod.Today,
            CardPeriod.Yesterday,
            CardPeriod.LastDays(7),
            CardPeriod.LastDays(30),
            CardPeriod.ThisMonth,
            CardPeriod.LastMonth,
        )
        val boundedRanges = boundedPeriods.map { it to it.toDateRange(today) }
        val earliestStart = boundedRanges.mapNotNull { it.second.start }.minOrNull()
        val latestEndExclusive = boundedRanges.mapNotNull { it.second.endExclusive }.maxOrNull()

        val dashboardDeferred = async { getData("/api/my/dashboard") }
        val orderResultDeferred = async {
            optionalFetch("收到发电账单加载失败，相关卡片显示为不可用") {
                if (earliestStart == null || latestEndExclusive == null) {
                    emptyList()
                } else {
                    fetchPaidOrders(
                        beginTimeSeconds = earliestStart.atStartOfDay(zoneId).toEpochSecond(),
                        endTimeSeconds = latestEndExclusive.atStartOfDay(zoneId).toEpochSecond(),
                    )
                }
            }
        }
        val dailyStatResultDeferred = async {
            optionalFetch("按日统计加载失败，访问卡片显示为不可用") {
                fetchDailyStats(stopBefore = earliestStart)
            }
        }
        val monthlyIncomeResultDeferred = async {
            optionalFetch("月度收入加载失败，抽成后收入卡片显示为不可用") {
                fetchMonthlyIncome()
            }
        }

        val dashboard = dashboardDeferred.await()
        val ordersResult = orderResultDeferred.await()
        val dailyStatsResult = dailyStatResultDeferred.await()
        val monthlyIncomeResult = monthlyIncomeResultDeferred.await()
        val values = DataCardCatalog.all
            .associate { it.key.storageValue to (CardValue.Unavailable as CardValue) }
            .toMutableMap()

        fun set(metric: CardMetric, period: CardPeriod, value: CardValue) {
            values[CardKey(metric, period).storageValue] = value
        }

        fun setSnapshot(metric: CardMetric, value: CardValue) {
            values[CardKey(metric).storageValue] = value
        }

        // 账单接口同时提供收入、发电人数和发电次数，按时间范围统一聚合。
        ordersResult.getOrNull()?.let { orders ->
            boundedRanges.forEach { (period, range) ->
                val aggregate = aggregatePaidOrders(orders, range, zoneId)
                set(CardMetric.Income, period, CardValue.Money(aggregate.amount))
                set(CardMetric.SponsorCount, period, CardValue.Count(aggregate.sponsorCount))
                set(CardMetric.SponsorOrderCount, period, CardValue.Count(aggregate.orderCount))
            }
        }

        // 按日统计用于非今日 UV；PV 不使用按日结果，只读取 dashboard 的历史总量。
        dailyStatsResult.getOrNull()?.let { dailyStats ->
            boundedRanges.forEach { (period, range) ->
                val aggregate = aggregateDailyStats(dailyStats, range)
                if (period != CardPeriod.Today) {
                    aggregate.uv?.let { set(CardMetric.ProfileUv, period, CardValue.Count(it)) }
                }
            }
        }

        val monthlyIncome = monthlyIncomeResult.getOrNull()
        if (monthlyIncome != null) {
            val currentMonth = today.withDayOfMonth(1)
            val previousMonth = currentMonth.minusMonths(1)
            monthlyIncome.firstOrNull { it.month == currentMonth }?.creatorAmount?.let {
                set(CardMetric.IncomeAfterFee, CardPeriod.ThisMonth, CardValue.Money(it))
            }
            monthlyIncome.firstOrNull { it.month == previousMonth }?.creatorAmount?.let {
                set(CardMetric.IncomeAfterFee, CardPeriod.LastMonth, CardValue.Money(it))
            }
            set(
                CardMetric.IncomeAfterFee,
                CardPeriod.History,
                CardValue.Money(monthlyIncome.fold(BigDecimal.ZERO) { total, item -> total + item.creatorAmount }),
            )
        }

        val summary = dashboard["summary"]?.jsonObject
        summary?.money("all_sum_amount")?.let {
            set(CardMetric.Income, CardPeriod.History, CardValue.Money(it))
        }
        summary?.long("all_sponsor_count")?.let {
            set(CardMetric.SponsorCount, CardPeriod.History, CardValue.Count(it))
        }
        summary?.long("uv")?.let {
            set(CardMetric.ProfileUv, CardPeriod.History, CardValue.Count(it))
        }
        summary?.long("pv")?.let {
            set(CardMetric.ProfilePv, CardPeriod.History, CardValue.Count(it))
        }
        dashboard.money("balance")?.let { setSnapshot(CardMetric.Balance, CardValue.Money(it)) }
        dashboard.money("balance_after_tax")?.let { setSnapshot(CardMetric.BalanceAfterTax, CardValue.Money(it)) }

        val withdrawalStatus = WithdrawalSchedule.calculate(now)
        setSnapshot(
            CardMetric.WithdrawalRemainingDays,
            CardValue.DurationDays(withdrawalStatus.remainingDays),
        )
        // 图表卡复用上面已抓取的原始序列生成：缺失数据源的图不加入结果，
        // UI 对应展示“暂无数据”。
        val charts = mutableMapOf<String, ChartDataItem>()
        dailyStatsResult.getOrNull()?.let { dailyStats ->
            charts[ChartType.IncomeDaily30.storageValue] = buildDailyIncomeChart(dailyStats, today, 30)
            charts[ChartType.UvDaily30.storageValue] = buildUvChart(dailyStats, today, 30)
        }
        monthlyIncome?.takeIf { it.isNotEmpty() }?.let {
            charts[ChartType.IncomeMonthly.storageValue] = buildMonthlyIncomeChart(it)
        }

        AppLog.d(
            TAG,
            "创作者数据加载完成：订单=${ordersResult.getOrNull()?.size ?: 0}，按日统计=${dailyStatsResult.getOrNull()?.size ?: 0}，" +
                "卡片=${values.size}，图表=${charts.size}",
        )
        val cards = DataCardCatalog.all.associate { definition ->
            val id = definition.key.storageValue
            id to DataCardModel(id = id, title = definition.title, value = values[id] ?: CardValue.Unavailable)
        }
        OverviewData(cards = cards, charts = charts)
    }

    /** 拉取指定时间段内的已支付账单，用于金额和去重人数统计。 */
    private fun fetchPaidOrders(beginTimeSeconds: Long, endTimeSeconds: Long): List<PaidOrderRecord> {
        val result = mutableListOf<PaidOrderRecord>()
        var page = 1
        var lastOrderId = ""
        while (page <= MAX_PAGE_COUNT) {
            val builder = (AFDIAN_HOST + "/api/my/sponsored-bill-filter").toHttpUrl().newBuilder()
                .addQueryParameter("auth_token", tokenStore.token)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort_field", "update_time")
                .addQueryParameter("sort_value", "desc")
                .addQueryParameter("status", "2")
                .addQueryParameter("begin_time", beginTimeSeconds.toString())
                // 接口结束时间按闭区间处理，所以传入 endExclusive 前一秒。
                .addQueryParameter("end_time", (endTimeSeconds - 1).coerceAtLeast(beginTimeSeconds).toString())
            if (lastOrderId.isNotBlank()) builder.addQueryParameter("last_order_id", lastOrderId)
            val data = getData(builder.build().toString())
            val list = data["list"]?.jsonArray.orEmpty()
            list.mapNotNullTo(result) { it.jsonObject.toPaidOrder() }
            val nextLastOrderId = data.string("last_order_id").orEmpty()
            val hasMore = data.long("has_more") == 1L
            if (!hasMore || list.isEmpty() || nextLastOrderId.isBlank() || nextLastOrderId == lastOrderId) break
            lastOrderId = nextLastOrderId
            page++
        }
        if (page > MAX_PAGE_COUNT) {
            AppLog.w(TAG, "sponsored-bill-filter 翻页超过 $MAX_PAGE_COUNT 页，已提前截断")
        }
        return result
    }

    /** 拉取按日统计，用于访问人数和访问次数。 */
    private fun fetchDailyStats(stopBefore: LocalDate?): List<DailyStatRecord> {
        val result = mutableListOf<DailyStatRecord>()
        var page = 1
        while (page <= MAX_PAGE_COUNT) {
            val data = getData("/api/my/stat?page=$page&type=day")
            val list = data["list"]?.jsonArray.orEmpty()
            val pageRecords = list.mapNotNull { it.jsonObject.toDailyStat() }
            result += pageRecords
            // 接口按日期倒序返回；整页都早于最早需要日期时即可停止翻页。
            if (stopBefore != null && pageRecords.isNotEmpty() && pageRecords.all { it.date.isBefore(stopBefore) }) break
            if (data.long("has_more") != 1L || list.isEmpty()) break
            page++
        }
        if (page > MAX_PAGE_COUNT) {
            AppLog.w(TAG, "/api/my/stat 翻页超过 $MAX_PAGE_COUNT 页，已提前截断")
        }
        return result
    }

    /** 拉取月度账单；抽成后金额只用接口明确返回的 creator_amount。 */
    private fun fetchMonthlyIncome(): List<MonthlyBillRecord> {
        val data = getData("/api/my/income")
        return data["monthly_bill"]?.jsonArray.orEmpty().flatMap { yearElement ->
            val yearObject = yearElement.jsonObject
            val year = yearObject.long("year") ?: return@flatMap emptyList()
            yearObject["data"]?.jsonArray.orEmpty().mapNotNull { monthElement ->
                val monthObject = monthElement.jsonObject
                val month = monthObject.long("month") ?: return@mapNotNull null
                val bill = monthObject["data"]?.jsonObject ?: return@mapNotNull null
                val creatorAmount = bill.money("creator_amount") ?: return@mapNotNull null
                MonthlyBillRecord(
                    month = LocalDate.of(year.toInt(), month.toInt(), 1),
                    totalAmount = bill.money("total_amount") ?: creatorAmount,
                    creatorAmount = creatorAmount,
                    sponsorCount = bill.long("sponsor_count") ?: 0L,
                )
            }
        }
    }

    /** 统一网络响应解析，禁止把凭据写入日志。 */
    private fun getData(urlOrPath: String): JsonObject {
        val url = if (urlOrPath.startsWith("http")) {
            urlOrPath.toHttpUrl()
        } else {
            (AFDIAN_HOST + urlOrPath).toHttpUrl().newBuilder()
                .addQueryParameter("auth_token", tokenStore.token)
                .build()
        }
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val pathForLog = url.encodedPath
        val start = System.currentTimeMillis()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$pathForLog -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("创作者数据加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val ec = root["ec"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (ec != null && ec != 200) {
                AppLog.w(TAG, "$pathForLog 业务失败 ec=$ec")
                error("创作者数据加载失败 (ec=$ec)")
            }
            AppLog.d(TAG, "$pathForLog -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
            root["data"]?.jsonObject ?: error("创作者数据返回结构异常")
        }
    }

    private fun JsonObject.toPaidOrder(): PaidOrderRecord? {
        if (long("status") != 2L) return null
        val updateTime = long("update_time") ?: return null
        return PaidOrderRecord(
            userId = string("user_id") ?: this["user"]?.jsonObject?.string("user_id"),
            amount = money("total_amount"),
            updateTimeSeconds = updateTime,
        )
    }

    private fun JsonObject.toDailyStat(): DailyStatRecord? {
        val dateText = string("date_str") ?: return null
        val date = runCatching {
            LocalDate.parse(dateText, DateTimeFormatter.BASIC_ISO_DATE)
        }.getOrNull() ?: return null
        return DailyStatRecord(
            date = date,
            income = money("paid_order_real_amount"),
            orderCount = long("paid_order_count") ?: 0L,
            uv = long("uv") ?: 0L,
            pv = long("pv") ?: 0L,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = string(key)?.let { value ->
        value.toLongOrNull() ?: value.toDoubleOrNull()?.toLong()
    }

    private fun JsonObject.money(key: String): BigDecimal? = string(key)?.toBigDecimalOrNull()

    /** 可选接口失败时保留其余卡片，同时不能吞掉协程取消。 */
    private fun <T> optionalFetch(message: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w(TAG, message, e)
        Result.failure(e)
    }

    private companion object {
        const val TAG = "CreatorStatsRepository"
        const val AFDIAN_HOST = "https://afdian.com"
        const val MAX_PAGE_COUNT = 100
    }
}
