package com.hrsthrt74.afdian.data.stats

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 数据卡的业务指标。
 *
 * 指标只描述“要看什么”，时间范围由 [CardPeriod] 单独描述，避免把几十个组合全部写成枚举项。
 */
enum class CardMetric(val storageValue: String, val title: String, val group: String) {
    Income("income", "收入", "收入"),
    IncomeAfterFee("income_after_fee", "收入（抽成后）", "收入"),
    SponsorCount("sponsor_count", "发电人数", "发电人数"),
    SponsorOrderCount("sponsor_order_count", "发电次数", "发电次数"),
    ProfileUv("profile_uv", "访问人数", "访问数据"),
    ProfilePv("profile_pv", "访问次数", "访问数据"),
    Balance("balance", "可提现（抽成前）", "提现"),
    BalanceAfterTax("balance_after_tax", "可提现（抽成后）", "提现"),
    WithdrawalRemainingDays("withdrawal_remaining_days", "距离下次提现还有", "提现"),
}

/**
 * 统计卡支持的时间范围。
 *
 * “近 n 天”按自然日计算，并且包含今天，不是滚动的 24*n 小时。
 */
sealed interface CardPeriod {
    val storageValue: String
    val title: String

    data object Today : CardPeriod {
        override val storageValue: String = "today"
        override val title: String = "今日"
    }

    data object Yesterday : CardPeriod {
        override val storageValue: String = "yesterday"
        override val title: String = "昨天"
    }

    data class LastDays(val days: Int) : CardPeriod {
        init {
            require(days > 0) { "近 n 天中的 n 必须大于 0" }
        }

        override val storageValue: String = "last_days_$days"
        override val title: String = "近${days}天"
    }

    data object ThisMonth : CardPeriod {
        override val storageValue: String = "this_month"
        override val title: String = "本月"
    }

    data object LastMonth : CardPeriod {
        override val storageValue: String = "last_month"
        override val title: String = "上月"
    }

    data object History : CardPeriod {
        override val storageValue: String = "history"
        override val title: String = "历史"
    }

    companion object {
        fun fromStorage(value: String): CardPeriod? = when {
            value == Today.storageValue -> Today
            value == Yesterday.storageValue -> Yesterday
            value.startsWith("last_days_") -> value.removePrefix("last_days_")
                .toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let(::LastDays)
            value == ThisMonth.storageValue -> ThisMonth
            value == LastMonth.storageValue -> LastMonth
            value == History.storageValue -> History
            else -> null
        }
    }
}

/** 统一的日期范围，endExclusive 是不包含的结束日期。历史范围可以没有起点。 */
data class CardDateRange(
    val start: LocalDate?,
    val endExclusive: LocalDate?,
)

/** 按本地日历把预置时间范围转换为明确的 [start, endExclusive)。 */
fun CardPeriod.toDateRange(today: LocalDate): CardDateRange = when (this) {
    CardPeriod.Today -> CardDateRange(today, today.plusDays(1))
    CardPeriod.Yesterday -> CardDateRange(today.minusDays(1), today)
    is CardPeriod.LastDays -> CardDateRange(today.minusDays(days.toLong() - 1), today.plusDays(1))
    CardPeriod.ThisMonth -> {
        val month = today.withDayOfMonth(1)
        CardDateRange(month, month.plusMonths(1))
    }
    CardPeriod.LastMonth -> {
        val month = today.withDayOfMonth(1).minusMonths(1)
        CardDateRange(month, month.plusMonths(1))
    }
    CardPeriod.History -> CardDateRange(null, null)
}

/** 卡片元数据。网络层只返回值，标题和分组统一由这里提供。 */
data class DataCardDefinition(
    val key: CardKey,
    val title: String,
    val group: String,
)

/**
 * 卡片稳定键。
 *
 * 时间卡保存为 metric:period，例如 income:today；不带时间范围的当前状态卡只保存 metric。
 */
data class CardKey(
    val metric: CardMetric,
    val period: CardPeriod? = null,
) {
    val storageValue: String
        get() = period?.let { "${metric.storageValue}:${it.storageValue}" } ?: metric.storageValue

    companion object {
        fun fromStorage(value: String): CardKey? {
            val parts = value.split(":", limit = 2)
            val metric = CardMetric.entries.firstOrNull { it.storageValue == parts[0] } ?: return null
            val period = parts.getOrNull(1)?.let(CardPeriod::fromStorage)
            if (parts.size == 2 && period == null) return null
            return CardKey(metric, period)
        }
    }
}

/** 所有可以在添加面板中选择的卡片组合。无法准确取得的组合不放入目录。 */
object DataCardCatalog {
    private val commonPeriods = listOf(
        CardPeriod.Today,
        CardPeriod.Yesterday,
        CardPeriod.LastDays(7),
        CardPeriod.LastDays(30),
        CardPeriod.ThisMonth,
        CardPeriod.LastMonth,
    )

    /**
     * 第一版只注册有明确数据口径的组合：
     * - 抽成后收入：上月/历史用月度账单的 creator_amount（官方口径），本月因账单未出账，
     *   由「本月收入」乘 94%（平台抽成 6%）估算，见 `CreatorStatsRepository.CREATOR_SHARE_RATIO`；
     * - 发电次数没有可信的全历史摘要，因此不伪造“历史”卡片；UV 历史总量使用 dashboard 摘要。
     */
    val all: List<DataCardDefinition> = buildList {
        addMetric(CardMetric.Income, commonPeriods + CardPeriod.History)
        addMetric(CardMetric.IncomeAfterFee, listOf(CardPeriod.ThisMonth, CardPeriod.LastMonth, CardPeriod.History))
        addMetric(CardMetric.SponsorCount, commonPeriods + CardPeriod.History)
        addMetric(CardMetric.SponsorOrderCount, commonPeriods)
        // 今日 UV 没有可靠的独立筛选口径，历史总量使用 dashboard.summary.uv。
        addMetric(CardMetric.ProfileUv, commonPeriods.drop(1) + CardPeriod.History)
        // PV 只有 dashboard.summary.pv 的总量，不能伪造时间段卡片。
        addMetric(CardMetric.ProfilePv, listOf(CardPeriod.History))
        addSnapshot(CardMetric.Balance)
        addSnapshot(CardMetric.BalanceAfterTax)
        addSnapshot(CardMetric.WithdrawalRemainingDays)
        // 其他分类预留：以后新增非统计类卡片时在这里注册。
    }

    val groups: List<String> = all.map { it.group }.distinct()

    fun find(storageValue: String): DataCardDefinition? = all.firstOrNull { it.key.storageValue == storageValue }

    private fun MutableList<DataCardDefinition>.addMetric(metric: CardMetric, periods: List<CardPeriod>) {
        periods.forEach { period ->
            add(DataCardDefinition(CardKey(metric, period), "${period.title}${metric.title}", metric.group))
        }
    }

    private fun MutableList<DataCardDefinition>.addSnapshot(metric: CardMetric) {
        add(DataCardDefinition(CardKey(metric), metric.title, metric.group))
    }
}

/**
 * 创作者到手比例：爱发电平台抽成 6%。
 *
 * 当月与结算窗口内的「抽成后」金额取不到官方月度账单时，用该比例折算；
 * CreatorStatsRepository 与首页提现提醒卡共用，平台调整抽成比例时只需改这里。
 */
val CREATOR_SHARE_RATIO = BigDecimal("0.94")

/** 卡片值保留业务类型，最终展示文本只在 UI 边界格式化。 */
sealed interface CardValue {
    data class Money(val amount: BigDecimal) : CardValue
    data class Count(val value: Long) : CardValue
    data class DurationDays(val value: Long) : CardValue
    data class Text(val value: String) : CardValue
    data object Unavailable : CardValue
}

/** 金额、数量、倒计时统一在一个地方格式化，避免每个卡片组件各写一套规则。 */
fun CardValue.displayText(): String = when (this) {
    is CardValue.Money -> amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
    is CardValue.Count -> value.toString()
    // 只给纯数字，单位交给 UI 以弱化样式渲染（见 [unitText]）
    is CardValue.DurationDays -> value.toString()
    is CardValue.Text -> value
    CardValue.Unavailable -> "--"
}

/**
 * 指标对应的后导单位，未开启对应开关时返回 null。
 *
 * 收入类和人次类分别由「显示收入单位」「显示人次单位」两个设置开关控制；
 * 倒计时天数不属于这两类，始终显示。
 */
fun CardMetric.unitText(showIncomeUnit: Boolean, showCountUnit: Boolean): String? = when (this) {
    CardMetric.Income, CardMetric.IncomeAfterFee, CardMetric.Balance, CardMetric.BalanceAfterTax ->
        "元".takeIf { showIncomeUnit }
    CardMetric.SponsorCount, CardMetric.ProfileUv -> "人".takeIf { showCountUnit }
    CardMetric.SponsorOrderCount, CardMetric.ProfilePv -> "次".takeIf { showCountUnit }
    CardMetric.WithdrawalRemainingDays -> "天"
}

/**
 * 去掉旧版本缓存里被拼进数值的单位后缀（例如「12天」）。
 *
 * 旧快照的单位写在 value 里，现在单位改由指标单独提供，保留后缀会显示成「12天 天」。
 */
fun String.removeLegacyUnitSuffix(id: String): String {
    val unit = CardKey.fromStorage(id)?.metric?.unitText(showIncomeUnit = true, showCountUnit = true) ?: return this
    return if (unit.isNotEmpty() && endsWith(unit)) dropLast(unit.length) else this
}

/** 供 ViewModel 和缓存使用的类型化卡片模型。 */
data class DataCardModel(
    val id: String,
    val title: String,
    val value: CardValue,
    val subtitle: String? = null,
)

/**
 * 合并流式加载的中间帧：只接受本帧真正产出的值。
 *
 * [CreatorStatsRepository.loadOverviewStreaming] 推出的每一帧都是「全量快照」：
 * 还没回来的数据源（账单 / 按日统计 / 月度账单）统一是 [CardValue.Unavailable]。
 * 调用方若整份替换，已显示出来的值就会在中途被抹成 “--”——冷启动回填的缓存数字
 * 先消失一次，真值到达后滚轮节点又要重建、动画从 0 重播一遍。
 *
 * 这里只在本帧确实是 [CardValue.Unavailable]、而上一帧已有可用值时保留旧卡，
 * 其余一律取本帧值。等流式加载全部结束后，调用方再落一次最终快照兜底，
 * 真正失败的数据源仍然回到 “--”，失败语义不变。
 *
 * @param previous 上一帧（可能是冷启动回填的缓存卡片，也可能为空）
 * @param frame 本次收到的快照
 */
fun mergeCardFrame(
    previous: Map<String, DataCardModel>,
    frame: Map<String, DataCardModel>,
): Map<String, DataCardModel> {
    if (previous.isEmpty()) return frame
    return buildMap(frame.size + previous.size) {
        putAll(frame)
        for ((id, old) in previous) {
            val incoming = frame[id]
            if (incoming?.value is CardValue.Unavailable && old.value !is CardValue.Unavailable) {
                put(id, old)
            }
        }
    }
}

/** 发电账单中参与统计的已支付订单。 */
data class PaidOrderRecord(
    val userId: String?,
    val amount: BigDecimal?,
    val updateTimeSeconds: Long,
)

/** 订单聚合结果，金额使用 BigDecimal 避免 Double 精度误差。 */
data class PaidOrderAggregate(
    val amount: BigDecimal,
    val orderCount: Long,
    val sponsorCount: Long,
)

/**
 * 对账单进行时间过滤和聚合。
 *
 * sponsorCount 是整个时间范围内去重后的用户数，不能把每天的用户数直接相加。
 */
fun aggregatePaidOrders(
    records: List<PaidOrderRecord>,
    range: CardDateRange,
    zoneId: ZoneId,
): PaidOrderAggregate {
    val startSeconds = range.start?.atStartOfDay(zoneId)?.toEpochSecond()
    val endSeconds = range.endExclusive?.atStartOfDay(zoneId)?.toEpochSecond()
    val selected = records.filter { record ->
        (startSeconds == null || record.updateTimeSeconds >= startSeconds) &&
            (endSeconds == null || record.updateTimeSeconds < endSeconds)
    }
    return PaidOrderAggregate(
        amount = selected.fold(BigDecimal.ZERO) { total, record -> total + (record.amount ?: BigDecimal.ZERO) },
        orderCount = selected.size.toLong(),
        sponsorCount = selected.mapNotNull { it.userId?.takeIf(String::isNotBlank) }.toSet().size.toLong(),
    )
}

/** 按日统计接口返回的单日记录。 */
data class DailyStatRecord(
    val date: LocalDate,
    val income: BigDecimal?,
    val orderCount: Long,
    val uv: Long?,
    val pv: Long?,
)

/** 按日期聚合访问和订单统计。 */
data class DailyStatAggregate(
    val income: BigDecimal,
    val orderCount: Long,
    val uv: Long?,
    val pv: Long?,
)

fun aggregateDailyStats(records: List<DailyStatRecord>, range: CardDateRange): DailyStatAggregate {
    val selected = records.filter { record ->
        (range.start == null || !record.date.isBefore(range.start)) &&
            (range.endExclusive == null || record.date.isBefore(range.endExclusive))
    }
    return DailyStatAggregate(
        income = selected.fold(BigDecimal.ZERO) { total, record -> total + (record.income ?: BigDecimal.ZERO) },
        orderCount = selected.sumOf { it.orderCount },
        // 接口字段可能缺失；缺失时返回 null，让卡片显示不可用而不是伪造 0。
        uv = selected.takeIf { it.any { record -> record.uv != null } }?.sumOf { it.uv ?: 0L },
        pv = selected.takeIf { it.any { record -> record.pv != null } }?.sumOf { it.pv ?: 0L },
    )
}
