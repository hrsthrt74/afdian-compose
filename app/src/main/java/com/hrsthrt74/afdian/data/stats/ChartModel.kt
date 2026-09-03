package com.hrsthrt74.afdian.data.stats

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
data class ChartSeries(
    val name: String,
    val values: List<Double>,
)

/** 可渲染的图表数据：横轴标签 + 一至多条序列。数据缺失/加载失败时为全空。 */
data class ChartDataItem(
    val key: String,
    val title: String,
    val categories: List<String>,
    val series: List<ChartSeries>,
)

/** 横轴日期/月份标签统一格式，方便在图表与 marker 中复用。 */
private val dayLabel = DateTimeFormatter.ofPattern("M/d")
private val monthLabel = DateTimeFormatter.ofPattern("yy/M")

/**
 * 用按日统计记录生成“近 n 天每日收入”柱状图。
 *
 * 与访问统计同源接口对今天/昨天返回空值（按天结算需服务端计算），
 * 因此同样跳过今昨，从“前天”往前取足 [days] 天；缺失天仍补零保证连续。
 */
fun buildDailyIncomeChart(
    dailyStats: List<DailyStatRecord>,
    now: LocalDate,
    days: Int,
): ChartDataItem {
    val byDate = dailyStats.associateBy { it.date }
    // offset 取 2..days+1：对应 now-2（前天）往前数 days 天。
    val categories = (days + 1 downTo 2).map { offset ->
        dayLabel.format(now.minusDays(offset.toLong()))
    }
    val values = (days + 1 downTo 2).map { offset ->
        (byDate[now.minusDays(offset.toLong())]?.income ?: BigDecimal.ZERO).toDouble()
    }
    return ChartDataItem(
        key = ChartType.IncomeDaily30.storageValue,
        title = ChartType.IncomeDaily30.title,
        categories = categories,
        series = listOf(ChartSeries("收入", values)),
    )
}

/**
 * 用按日统计记录生成“近 n 天访问人数”单折线。
 *
 * 接口对今天/昨天返回空值（访问统计需服务端按天结算），所以跳过今昨，
 * 从“前天”往前取足 [days] 天，避免曲线末端被补成 0 往下掉。
 */
fun buildUvChart(
    dailyStats: List<DailyStatRecord>,
    now: LocalDate,
    days: Int,
): ChartDataItem {
    val byDate = dailyStats.associateBy { it.date }
    // offset 取 2..days+1：对应 now-2（前天）往前数 days 天。
    val categories = (days + 1 downTo 2).map { offset ->
        dayLabel.format(now.minusDays(offset.toLong()))
    }
    val uv = (days + 1 downTo 2).map { offset ->
        byDate[now.minusDays(offset.toLong())]?.uv?.toDouble() ?: 0.0
    }
    return ChartDataItem(
        key = ChartType.UvDaily30.storageValue,
        title = ChartType.UvDaily30.title,
        categories = categories,
        series = listOf(ChartSeries("访问人数", uv)),
    )
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
