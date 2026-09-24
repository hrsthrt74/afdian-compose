package com.hrsthrt74.afdian.data.stats

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 创作者数据卡的数据仓库。
 *
 * 这里负责接口请求和数据聚合，UI 只消费已经按卡片键整理好的类型化值。
 * 同一批数据只请求一次，再在内存中生成不同时间范围的卡片，避免每张卡片各发一个请求。
 * 加载采用流式推送（见 [loadOverviewStreaming]）：哪个数据源先完成就先把当前快照推给调用方。
 */
class CreatorStatsRepository(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient = sharedAfdianHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private val http = AfdianHttpClient(client)

    /**
     * 流式加载管理页和首页共用的创作者数字卡与图表卡。
     * dashboard 是基础接口，失败时向上抛出；其他统计接口失败时只让对应卡片显示“--”。
     *
     * 数据不再攒齐后才返回：四个请求同时并发发起，dashboard 一到就通过 [onPartial]
     * 推出第一帧（历史收入、本月收入、可提现、提现倒计时等都能立即上屏），
     * 其后账单 / 按日统计 / 月度账单谁先完成谁推一帧，每帧都是当前的全量快照
     * （未完成的数据源仍为 Unavailable，UI 显示 “--”）。最终返回值与最后一帧一致。
     * 这样订单很多的账号（账单要串行翻页几十页）只需等 dashboard，秒级先看到大部分卡片。
     *
     * 「本月收入」取自 dashboard.summary.month_amount；字段缺失时由账单阶段兜底。
     * 图表序列复用同一次已抓取的原始数据（按日统计 / 月度账单）生成，不再额外发网络请求。
     */
    suspend fun loadOverviewStreaming(
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        // 回调参数放最后，调用方才能用尾 lambda 语法：loadOverviewStreaming { partial -> ... }
        onPartial: (OverviewData) -> Unit,
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

        // 各数据源写入的共享可变状态。帧发射统一走 [publish]，内部用互斥锁串行化，
        // 保证「写入 + 构建快照 + 回调」原子完成，调用方不会看到写了一半的帧。
        val frameMutex = Mutex()
        val values = DataCardCatalog.all
            .associate { it.key.storageValue to (CardValue.Unavailable as CardValue) }
            .toMutableMap()
        val charts = mutableMapOf<String, ChartDataItem>()

        // 以下三个写入函数都只能在 [publish] 的锁内调用。
        fun set(metric: CardMetric, period: CardPeriod, value: CardValue) {
            values[CardKey(metric, period).storageValue] = value
        }

        fun setSnapshot(metric: CardMetric, value: CardValue) {
            values[CardKey(metric).storageValue] = value
        }

        // 「本月收入」与「本月收入（抽成后）」写进同一个局部函数，
        // 保证抽成后的估算基准与展示值同源。
        fun setThisMonthIncome(amount: BigDecimal) {
            set(CardMetric.Income, CardPeriod.ThisMonth, CardValue.Money(amount))
            // 当月「抽成后」取不到官方值：月度账单要等次月初才出账（见 fetchMonthlyIncome），
            // 因此用平台到手比例折算当月收入。折算结果向下取整到分（3.1490 → 3.14）：
            // 宁可少算一分，也不把到手钱算多。
            // 若月度账单里真的带上了当月，月度账单阶段会用真实 creator_amount 覆盖这个估算值。
            set(
                CardMetric.IncomeAfterFee,
                CardPeriod.ThisMonth,
                CardValue.Money((amount * CREATOR_SHARE_RATIO).setScale(MONEY_SCALE, RoundingMode.FLOOR)),
            )
        }

        /** 构建当前快照。必须在持有 [frameMutex] 时调用。 */
        fun buildSnapshot(): OverviewData {
            val cards = DataCardCatalog.all.associate { definition ->
                val id = definition.key.storageValue
                id to DataCardModel(id = id, title = definition.title, value = values[id] ?: CardValue.Unavailable)
            }
            return OverviewData(cards = cards, charts = charts.toMap())
        }

        /** 在锁内执行 [block] 的写入并发射一帧；所有数据源阶段的写入都必须经由这个入口。 */
        suspend fun publish(block: () -> Unit) {
            frameMutex.withLock {
                block()
                onPartial(buildSnapshot())
            }
        }

        // 四个请求同时并发发起（与旧版一致的并发度），下面按各自到达的顺序分帧推送。
        val dashboardDeferred = async { getData("/api/my/dashboard") }
        val ordersDeferred = async {
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
        val dailyStatDeferred = async {
            optionalFetch("按日统计加载失败，访问卡片显示为不可用") {
                // 图表要在末端裁掉今天/昨天等空天，还要保证裁剪后仍有 30 个点，
                // 因此往前多取 CHART_TRAILING_TRIM_BUFFER_DAYS 天（与卡片最早起点取更早者）。
                val chartStopBefore = today.minusDays(
                    (CHART_DAILY_WINDOW_DAYS - 1 + CHART_TRAILING_TRIM_BUFFER_DAYS).toLong(),
                )
                fetchDailyStats(stopBefore = listOfNotNull(earliestStart, chartStopBefore).minOrNull())
            }
        }
        val monthlyIncomeDeferred = async {
            optionalFetch("月度收入加载失败，抽成后收入卡片显示为不可用") {
                fetchMonthlyIncome()
            }
        }

        // ---- 第一帧：dashboard（基础接口，失败时从这里向上抛出）----
        // dashboard 是单请求快照，通常最先回来；它的数据足以支撑大部分卡片立即上屏。
        val dashboard = dashboardDeferred.await()
        publish {
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

            // 「本月收入」来自 dashboard.summary.month_amount：不依赖账单翻页，
            // 订单多或被限速时本月卡也能在这一帧立即显示。
            val monthAmount = summary?.money("month_amount")
            if (monthAmount != null) {
                setThisMonthIncome(monthAmount)
            } else {
                // month_amount 缺失：本月卡先保持不可用，等账单阶段用聚合值兜底。
                AppLog.d(TAG, "dashboard.summary 缺少 month_amount，本月收入将由账单聚合兜底")
            }

            dashboard.money("balance")?.let { setSnapshot(CardMetric.Balance, CardValue.Money(it)) }
            dashboard.money("balance_after_tax")?.let { setSnapshot(CardMetric.BalanceAfterTax, CardValue.Money(it)) }

            val withdrawalStatus = WithdrawalSchedule.calculate(now)
            setSnapshot(
                CardMetric.WithdrawalRemainingDays,
                CardValue.DurationDays(withdrawalStatus.remainingDays),
            )
        }

        // ---- 其余三个数据源：各自独立处理，谁先完成谁推一帧，互不等待 ----
        val ordersJob = launch {
            val ordersResult = ordersDeferred.await()
            ordersResult.getOrNull()?.let { orders ->
                AppLog.d(TAG, "账单加载完成：${orders.size} 条，推送对应卡片")
                publish {
                    // 账单接口同时提供收入、发电人数和发电次数，按时间范围统一聚合。
                    // 「本月收入」的主口径是 month_amount（dashboard 帧），这里不再重复写入。
                    boundedRanges.forEach { (period, range) ->
                        val aggregate = aggregatePaidOrders(orders, range, zoneId)
                        if (period != CardPeriod.ThisMonth) {
                            set(CardMetric.Income, period, CardValue.Money(aggregate.amount))
                        }
                        set(CardMetric.SponsorCount, period, CardValue.Count(aggregate.sponsorCount))
                        set(CardMetric.SponsorOrderCount, period, CardValue.Count(aggregate.orderCount))
                        // month_amount 缺失时，用账单聚合值兜底「本月收入」两张卡，
                        // 避免本月卡无理由显示 “--”。dashboard 帧已写入过则保持官方口径不动。
                        if (period == CardPeriod.ThisMonth) {
                            val thisMonthIncomeKey = CardKey(CardMetric.Income, CardPeriod.ThisMonth).storageValue
                            if (values[thisMonthIncomeKey] is CardValue.Unavailable) {
                                setThisMonthIncome(aggregate.amount)
                            }
                        }
                    }
                }
            }
        }

        val dailyStatsJob = launch {
            val dailyStatsResult = dailyStatDeferred.await()
            dailyStatsResult.getOrNull()?.let { dailyStats ->
                AppLog.d(TAG, "按日统计加载完成：${dailyStats.size} 天，推送对应卡片与图表")
                publish {
                    // 按日统计用于非今日 UV；PV 不使用按日结果，只读取 dashboard 的历史总量。
                    boundedRanges.forEach { (period, range) ->
                        val aggregate = aggregateDailyStats(dailyStats, range)
                        if (period != CardPeriod.Today) {
                            aggregate.uv?.let { set(CardMetric.ProfileUv, period, CardValue.Count(it)) }
                        }
                    }
                    // 图表卡复用同一次抓取的按日序列生成：缺失数据源的图不加入结果，
                    // UI 对应展示“暂无数据”。
                    charts[ChartType.IncomeDaily30.storageValue] =
                        buildDailyIncomeChart(dailyStats, today, CHART_DAILY_WINDOW_DAYS)
                    charts[ChartType.UvDaily30.storageValue] =
                        buildUvChart(dailyStats, today, CHART_DAILY_WINDOW_DAYS)
                }
            }
        }

        val monthlyIncomeJob = launch {
            val monthlyIncomeResult = monthlyIncomeDeferred.await()
            val monthlyIncome = monthlyIncomeResult.getOrNull()
            if (monthlyIncome != null) {
                AppLog.d(TAG, "月度账单加载完成：${monthlyIncome.size} 个月，推送对应卡片与图表")
                publish {
                    // 月度账单提供官方口径的「抽成后」金额，优先级高于 dashboard 帧的 94% 估算。
                    // 注意账单按「次月初」生成，当月条目通常不存在，这张卡当月一般走估算分支。
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
                    monthlyIncome.takeIf { it.isNotEmpty() }?.let {
                        charts[ChartType.IncomeMonthly.storageValue] = buildMonthlyIncomeChart(it)
                    }
                }
            }
        }

        // 等待全部数据源阶段结束（结构化并发：外部取消时这里会随父作用域一起取消）。
        listOf(ordersJob, dailyStatsJob, monthlyIncomeJob).joinAll()
        AppLog.d(
            TAG,
            "创作者数据流式加载完成：卡片=${values.size}，图表=${charts.size}",
        )
        frameMutex.withLock { buildSnapshot() }
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

    /** 拉取月度账单；抽成后金额只用接口明确返回的 creator_amount。解析逻辑与「每月汇总」页共用。 */
    private fun fetchMonthlyIncome(): List<MonthlyBillRecord> {
        val data = getData("/api/my/income")
        return parseMonthlyBills(data["monthly_bill"])
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
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$pathForLog -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("创作者数据加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, pathForLog, "创作者数据加载失败", requireEc = true)
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

        /**
         * 创作者到手比例：爱发电平台抽成 6%。
         *
         * 当月月度账单尚未出账，取不到官方的 creator_amount，因此用这个比例把
         * 「本月收入」（抽成前）折算成「本月收入（抽成后）」。
         * 平台调整抽成比例时只需改这里；若哪天改成按上月账单实测费率推算，
         * 也应替换本常量而不是在别处再写一个系数。
         */
        private val CREATOR_SHARE_RATIO = BigDecimal("0.94")

        /** 金额保留到分；估算值按 [RoundingMode.FLOOR] 向下取整，与卡片展示的小数位保持一致。 */
        const val MONEY_SCALE = 2
    }
}
