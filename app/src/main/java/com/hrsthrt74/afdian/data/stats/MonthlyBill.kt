package com.hrsthrt74.afdian.data.stats

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 月度账单（/api/my/income）的纯逻辑：解析、分组、金额口径与文案。
 *
 * 解析逻辑做成顶层 internal 函数，便于单元测试（项目规则，参考 MessageParser）。
 */

/** 金额固定保留两位小数。 */
private const val MONEY_SCALE = 2

/**
 * 解析 /api/my/income 的 `data.monthly_bill`。
 *
 * 响应结构：
 * ```
 * monthly_bill: [ { year: 2026, data: [ { month: 8, data: { total_amount, creator_amount, sponsor_count } } ] } ]
 * ```
 *
 * 容错策略：
 * - `monthly_bill` 不是数组（缺失 / 结构异常）→ 返回空列表
 * - 某年缺 `year`：整年丢弃（拿不到月份所属年份，无法构造 LocalDate）
 * - 某月缺 `month` 或 `data`：该月丢弃
 * - 缺 `creator_amount` 但缺不了 `total_amount`：两者都有才收（金额是这张页面的核心字段）
 * - 0 收入的月份**保留**（业务上「这个月没人发电」也是有效信息）
 */
internal fun parseMonthlyBills(monthlyBill: JsonElement?): List<MonthlyBillRecord> =
    monthlyBill?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
        .flatMap { yearElement ->
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

/** 一个年份下的月份账单分组，供列表按年分节渲染。 */
data class MonthlyBillSection(
    val year: Int,
    val bills: List<MonthlyBillRecord>,
)

/** 按年份分组并排序：年份倒序，组内月份倒序（最新在最上面）。 */
fun monthlyBillSections(bills: List<MonthlyBillRecord>): List<MonthlyBillSection> =
    bills.groupBy { it.month.year }
        .entries
        .sortedByDescending { it.key }
        .map { (year, group) -> MonthlyBillSection(year, group.sortedByDescending { it.month }) }

/**
 * 「每月汇总」页的金额显示口径。
 *
 * - [BeforeFee]：抽成前金额（`total_amount`），即赞助者实际支付总额
 * - [AfterFee]：抽成后金额（`creator_amount`），即创作者实际到手
 *
 * storageValue 用于 DataStore 持久化，勿随意改动。
 */
enum class IncomeAmountMode(val storageValue: String, val title: String) {
    BeforeFee("before_fee", "抽成前金额"),
    AfterFee("after_fee", "抽成后金额"),
    ;

    companion object {
        /** 默认口径：抽成前（与爱发电网页版月度账单一致）。 */
        val default: IncomeAmountMode = BeforeFee

        /** 反序列化持久化值，未知/空值回退到默认口径。 */
        fun fromStorage(value: String?): IncomeAmountMode =
            entries.firstOrNull { it.storageValue == value } ?: default
    }
}

/** 按当前口径取该月金额。 */
fun MonthlyBillRecord.amountFor(mode: IncomeAmountMode): BigDecimal = when (mode) {
    IncomeAmountMode.BeforeFee -> totalAmount
    IncomeAmountMode.AfterFee -> creatorAmount
}

/** 金额文案：固定两位小数带人民币符号，如 `¥261.15`、`¥40.00`、`¥0.00`。 */
fun moneyText(amount: BigDecimal): String =
    "¥" + amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? = string(key)?.let { value ->
    value.toLongOrNull() ?: value.toDoubleOrNull()?.toLong()
}

private fun JsonObject.money(key: String): BigDecimal? = string(key)?.toBigDecimalOrNull()
