package com.hrsthrt74.afdian.data.stats

import com.hrsthrt74.afdian.AppLog
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 图表卡的业务类型。
 *
 * 图表卡是布局中的一种整行条目（[com.hrsthrt74.afdian.data.settings.ManagementLayoutType.CHART]），
 * 它的 value 使用这里的 [ChartType.storageValue]，由 [ChartCatalog] 负责解析和注册，
 * 与只显示单个数值的数字卡（DataCardCatalog）是两套独立的目录。
 */
enum class ChartType(val storageValue: String, val title: String, val description: String) {
    /** 近 30 天每日实收金额柱状图，数据来自 /api/my/stat 按日统计；不含今天/昨天（接口为空）。 */
    IncomeDaily30("income_daily_30", "近30天收入", "每日实收金额柱状图（最近30天，不含今昨）"),

    /** 月度收入柱状图，数据来自 /api/my/income 月度账单。 */
    IncomeMonthly("income_monthly", "月度收入", "每月收入柱状图（自开通以来）"),

    /** 近 30 天每日访问人数折线，数据来自 /api/my/stat 按日统计；不含今天/昨天（接口为空）。 */
    UvDaily30("uv_pv_daily_30", "访问人数趋势", "每日访问人数折线（最近30天，不含今昨）");
}

/** 图表卡目录：管理页与首页编辑面板共用，仿 DataCardCatalog。 */
object ChartCatalog {
    val all: List<ChartType> = ChartType.entries

    fun find(storageValue: String): ChartType? =
        all.firstOrNull { it.storageValue == storageValue }

    /** 未知的图表键统一回退到“暂无数据”卡片标题，避免 UI 抛异常。 */
    fun titleFor(storageValue: String): String =
        find(storageValue)?.title ?: "暂无数据"
}

/** 月度账单里单月的金额摘要，供收入卡与月度收入图共用。 */
data class MonthlyBillRecord(
    val month: LocalDate,
    /** 当月总金额（口径与 dashboard “收入”一致）。 */
    val totalAmount: BigDecimal,
    /** 当月抽成后实收金额，用于“收入（抽成后）”类卡片。 */
    val creatorAmount: BigDecimal,
    val sponsorCount: Long,
)

/** 图表里的一条序列（一条柱/折线）。数值用 Double 便于直接交给 Vico。 */
@Serializable
data class ChartSeries(
    val name: String,
    val values: List<Double>,
)

/** 可渲染的图表数据：横轴标签 + 一至多条序列。数据缺失/加载失败时为全空。 */
@Serializable
data class ChartDataItem(
    val key: String,
    val title: String,
    val categories: List<String>,
    val series: List<ChartSeries>,
)

/** 横轴日期/月份标签统一格式，方便在图表与 marker 中复用。 */
private val dayLabel = DateTimeFormatter.ofPattern("M/d")
private val monthLabel = DateTimeFormatter.ofPattern("yy/M")

/** 按日图表默认展示的窗口点数（近 30 天）。 */
const val CHART_DAILY_WINDOW_DAYS = 30

/**
 * 按日图表在窗口末端额外多抓的天数。
 *
 * 接口对今天/昨天返回空值（按天结算需服务端计算），需要把窗口末端这些空/0 天裁掉，
 * 同时保证裁剪后仍有 [CHART_DAILY_WINDOW_DAYS] 个有效点，因此向前多取 [CHART_TRAILING_TRIM_BUFFER_DAYS] 天。
 */
const val CHART_TRAILING_TRIM_BUFFER_DAYS = 7

/**
 * 用按日统计记录生成“近 n 天每日收入”柱状图。
 *
 * 接口对今天/昨天返回空值，所以从窗口末端裁掉连续的空/0 天（只裁边界，中间为 0 的天保留）。
 * 若整段都是 0/无数据，则回退为“到今天为止”的完整窗口，避免出现空序列。
 */
fun buildDailyIncomeChart(
    dailyStats: List<DailyStatRecord>,
    now: LocalDate,
    days: Int,
): ChartDataItem {
    val byDate = dailyStats.associateBy { it.date }
    val trimmed = trimTrailingBlankDays(
        values = buildRawDailyValues(days) { offset ->
            (byDate[now.minusDays(offset.toLong())]?.income ?: BigDecimal.ZERO).toDouble()
        },
        days = days,
        tag = "收入",
    )
    return ChartDataItem(
        key = ChartType.IncomeDaily30.storageValue,
        title = ChartType.IncomeDaily30.title,
        categories = trimmed.offsets.map { offset -> dayLabel.format(now.minusDays(offset.toLong())) },
        series = listOf(ChartSeries("收入", trimmed.values)),
    )
}

/**
 * 用按日统计记录生成“近 n 天访问人数”单折线。
 *
 * 接口对今天/昨天返回空值，所以从窗口末端裁掉连续的空/0 天（只裁边界，中间为 0 的天保留），
 * 避免曲线末端被补成 0 往下掉；整段都是 0/无数据时回退为完整窗口。
 */
fun buildUvChart(
    dailyStats: List<DailyStatRecord>,
    now: LocalDate,
    days: Int,
): ChartDataItem {
    val byDate = dailyStats.associateBy { it.date }
    val trimmed = trimTrailingBlankDays(
        values = buildRawDailyValues(days) { offset ->
            byDate[now.minusDays(offset.toLong())]?.uv?.toDouble() ?: 0.0
        },
        days = days,
        tag = "访问人数",
    )
    return ChartDataItem(
        key = ChartType.UvDaily30.storageValue,
        title = ChartType.UvDaily30.title,
        categories = trimmed.offsets.map { offset -> dayLabel.format(now.minusDays(offset.toLong())) },
        series = listOf(ChartSeries("访问人数", trimmed.values)),
    )
}

/** 裁剪后的按日图表数据：offset 与数值一一对应（offset 越小越接近今天，0 表示今天）。 */
private data class TrimmedDailyChart(
    val offsets: List<Int>,
    val values: List<Double>,
)

/**
 * 生成候选窗口的原始数值，升序排列（最早 → 今天）。
 *
 * 窗口长度 = [days] + [CHART_TRAILING_TRIM_BUFFER_DAYS]，offset 从大到小对应时间从早到晚。
 */
private inline fun buildRawDailyValues(days: Int, valueAt: (Int) -> Double): List<Double> {
    val lastOffset = days - 1 + CHART_TRAILING_TRIM_BUFFER_DAYS
    return (lastOffset downTo 0).map(valueAt)
}

/**
 * 裁掉窗口末端（今天方向）连续的空/0 天，保证输出 [days] 个点。
 *
 * 注意：只处理边界，中间为 0 的天必须保留（例如某天没有收入是正常业务状态）。
 * - 找到最后一个非 0 值，作为有效末端；其后的 0/缺失全部裁掉。
 * - 全为 0/无数据时回退为“到今天为止”的完整窗口，避免空序列。
 * - 若有效数据不足以凑满 [days]，则从窗口起点截断（只显示已有的部分）。
 */
private fun trimTrailingBlankDays(values: List<Double>, days: Int, tag: String): TrimmedDailyChart {
    // 最后一个非 0 值的下标；-1 表示整段都是 0/无数据。
    val lastNonZero = values.indexOfLast { it != 0.0 }
    // endExclusive：裁掉末端空天后要保留到的下标（不含）。
    val endExclusive = if (lastNonZero < 0) values.size else lastNonZero + 1
    val start = (endExclusive - days).coerceAtLeast(0)
    val trimmedValues = values.subList(start, endExclusive)
    // offsets 与 values 对齐：values 是「最早 → 今天」，offset 从大（最早）到小（今天）。
    val lastOffset = values.size - 1
    val offsets = (lastOffset - start downTo lastOffset - endExclusive + 1).toList()
    val trimmedDays = values.size - endExclusive
    AppLog.d(
        "ChartModel",
        "$tag 图表：原始 ${values.size} 天，末端裁掉 $trimmedDays 天，展示 ${trimmedValues.size} 天",
    )
    return TrimmedDailyChart(offsets = offsets, values = trimmedValues)
}

/** 用月度账单记录生成月度收入柱状图（按时间升序）。 */
fun buildMonthlyIncomeChart(monthly: List<MonthlyBillRecord>): ChartDataItem {
    val sorted = monthly.sortedBy { it.month }
    return ChartDataItem(
        key = ChartType.IncomeMonthly.storageValue,
        title = ChartType.IncomeMonthly.title,
        categories = sorted.map { monthLabel.format(it.month) },
        series = listOf(ChartSeries("月度收入", sorted.map { it.totalAmount.toDouble() })),
    )
}

/**
 * 一次数据加载的结果：数字卡 + 图表卡分开存放，避免把整段序列塞进单值卡片。
 *
 * charts 的 key 使用 [ChartType.storageValue]；某张图对应数据源失败时该键不存在，
 * 由 UI 渲染为“暂无数据”。
 */
data class OverviewData(
    val cards: Map<String, DataCardModel>,
    val charts: Map<String, ChartDataItem>,
)
