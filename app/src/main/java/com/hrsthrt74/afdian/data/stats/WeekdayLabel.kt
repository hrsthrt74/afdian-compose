package com.hrsthrt74.afdian.data.stats

import java.time.LocalDate

/**
 * 星期标签：索引 0..6 依次对应 [java.time.DayOfWeek.value] 的 1..7（周一..周日）。
 * 用固定中文表而不是 DayOfWeek.getDisplayName()——后者跟随系统 Locale，
 * 英文设备会返回「Fri」，而本项目 UI 全中文，只需要一个汉字（`26/10/16 五`）。
 */
private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 取日期对应的星期汉字（一..日）。
 *
 * 收入明细表格的日期列、图表卡的指示器（`9/6 五 • ¥12.34`）共用这一份映射；
 * 图表横轴标签**不放**星期——轴标签只有 10sp、一行最多 6 个，塞不下。
 */
fun weekdayLabelOf(date: LocalDate): String = WEEKDAY_LABELS[date.dayOfWeek.value - 1]
