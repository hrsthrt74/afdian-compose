package com.hrsthrt74.afdian.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.NumberPickerDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 日期 badge 的展示格式 */
private val dateBadgeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

/** 时间 badge 的展示格式 */
private val timeBadgeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 当前展开的选择器：日期与时间互斥，同一时刻只展开其中一个 */
enum class DateTimePart { Date, Time }

/**
 * 日期时间选择组件（参考系统「全天事件」的样式）：
 * 左侧文案 + 右侧「日期 / 时间」两个 badge，点 badge 动画展开对应滚轮，日期与时间不同时出现。
 *
 * @param label 左侧文案（如「开始」「结束」）
 * @param millis 当前值（Unix 毫秒）
 * @param onChanged 值变化回调（毫秒）
 * @param initialExpandedPart 初始展开的部分（仅预览用），默认 null 表示收起
 */
@Composable
fun DateTimeField(
    label: String,
    millis: Long,
    onChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialExpandedPart: DateTimePart? = null,
) {
    val zone = remember { ZoneId.systemDefault() }
    val dateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    // 当前展开的部分；null 表示都收起
    var expandedPart by remember { mutableStateOf<DateTimePart?>(initialExpandedPart) }
    // 记住最后一次展开的部分：收起动画期间 expandedPart 已为 null，
    // 若直接按它渲染会导致内容瞬间清空、收起高度突变；用 lastPart 保住内容让 shrink 正常播放
    var lastPart by remember { mutableStateOf(DateTimePart.Date) }
    LaunchedEffect(expandedPart) {
        expandedPart?.let { lastPart = it }
    }
    // 展开时立即取新值，收起时保留上一次的内容
    val displayPart = expandedPart ?: lastPart

    /** 用改动后的字段重建时间戳，并把「日」钳制到目标月份的有效天数内 */
    fun emit(
        year: Int = dateTime.year,
        month: Int = dateTime.monthValue,
        day: Int = dateTime.dayOfMonth,
        hour: Int = dateTime.hour,
        minute: Int = dateTime.minute,
    ) {
        val safeDay = minOf(day, YearMonth.of(year, month).lengthOfMonth())
        onChanged(LocalDateTime.of(year, month, safeDay, hour, minute).atZone(zone).toInstant().toEpochMilli())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 文案 + 日期 / 时间 badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.weight(1f),
            )
            // 日期 badge
            PickerBadge(
                text = dateTime.format(dateBadgeFormatter),
                selected = expandedPart == DateTimePart.Date,
                onClick = {
                    expandedPart = if (expandedPart == DateTimePart.Date) null else DateTimePart.Date
                },
            )
            Spacer(Modifier.width(8.dp))
            // 时间 badge
            PickerBadge(
                text = dateTime.format(timeBadgeFormatter),
                selected = expandedPart == DateTimePart.Time,
                onClick = {
                    expandedPart = if (expandedPart == DateTimePart.Time) null else DateTimePart.Time
                },
            )
        }

        // 展开区：日期与时间互斥，且带纵向展开动画
        AnimatedVisibility(
            visible = expandedPart != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                // 日期 / 时间之间切换：用 AnimatedContent 默认预设做淡入缩放过渡
                AnimatedContent(
                    targetState = displayPart,
                    modifier = Modifier.fillMaxWidth(),
                ) { part ->
                    when (part) {
                        DateTimePart.Date -> DateWheels(
                            dateTime = dateTime,
                            onChange = { year, month, day -> emit(year = year, month = month, day = day) },
                        )
                        DateTimePart.Time -> TimeWheels(
                            dateTime = dateTime,
                            onChange = { hour, minute -> emit(hour = hour, minute = minute) },
                        )
                    }
                }
            }
        }
    }
}

/** 可点击的圆角 badge：选中时主色底反白，未选中时灰色底 */
@Composable
private fun PickerBadge(text: String, selected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.secondary
    }
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSecondaryVariant
    }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .pressable(interactionSource = null, indication = SinkFeedback(), delay = null)
            .clip(RoundedCornerShape(48.dp))
            .background(backgroundColor)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 14.sp,
                fontFeatureSettings = "tnum"
            ),
            color = contentColor
        )
    }
}

/** 日期滚轮：年 / 月 / 日 */
@Composable
private fun DateWheels(dateTime: LocalDateTime, onChange: (Int, Int, Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 列标题
        Row(modifier = Modifier.fillMaxWidth()) {
            WheelLabel("年", Modifier.weight(1.4f))
            WheelLabel("月", Modifier.weight(1f))
            WheelLabel("日", Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            NumberPicker(
                value = dateTime.year,
                onValueChange = { onChange(it, dateTime.monthValue, dateTime.dayOfMonth) },
                range = 2018..2100,
                visibleItemCount = 3,
                textStyle = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1.4f),
                colors = NumberPickerDefaults.colors(selectedTextColor = MiuixTheme.colorScheme.primary)
            )
            NumberPicker(
                value = dateTime.monthValue,
                onValueChange = { onChange(dateTime.year, it, dateTime.dayOfMonth) },
                range = 1..12,
                visibleItemCount = 3,
                textStyle = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1f),
                colors = NumberPickerDefaults.colors(selectedTextColor = MiuixTheme.colorScheme.primary)
            )
            NumberPicker(
                value = dateTime.dayOfMonth,
                onValueChange = { onChange(dateTime.year, dateTime.monthValue, it) },
                range = 1..YearMonth.of(dateTime.year, dateTime.monthValue).lengthOfMonth(),
                visibleItemCount = 3,
                textStyle = MiuixTheme.textStyles.title3,
                modifier = Modifier.weight(1f),
                colors = NumberPickerDefaults.colors(selectedTextColor = MiuixTheme.colorScheme.primary)
            )
        }
    }
}

/** 时间滚轮：时 / 分（两列居中） */
@Composable
private fun TimeWheels(dateTime: LocalDateTime, onChange: (Int, Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            WheelLabel("时", Modifier.width(100.dp))
            WheelLabel("分", Modifier.width(100.dp))
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            NumberPicker(
                value = dateTime.hour,
                onValueChange = { onChange(it, dateTime.minute) },
                range = 0..23,
                visibleItemCount = 3,
                textStyle = MiuixTheme.textStyles.title3,
                modifier = Modifier.width(100.dp),
                colors = NumberPickerDefaults.colors(selectedTextColor = MiuixTheme.colorScheme.primary)
            )
            NumberPicker(
                value = dateTime.minute,
                onValueChange = { onChange(dateTime.hour, it) },
                range = 0..59,
                visibleItemCount = 3,
                textStyle = MiuixTheme.textStyles.title3,
                modifier = Modifier.width(100.dp),
                colors = NumberPickerDefaults.colors(selectedTextColor = MiuixTheme.colorScheme.primary)
            )
        }
    }
}

/** 滚轮列标题（宽度/权重由调用方通过 modifier 指定） */
@Composable
private fun WheelLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** DateTimeField 预览：展示默认值 */
@Preview(showBackground = true)
@Composable
private fun DateTimeFieldPreview() {
    var millis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DateTimeField(
            label = "开始",
            millis = millis,
            onChanged = { millis = it },
        )
        Spacer(Modifier.width(16.dp))
        DateTimeField(
            label = "结束",
            millis = millis + 30L * 24 * 60 * 60 * 1000,
            onChanged = { },
        )
    }
}

/** DateTimeField 预览：日期滚轮展开状态 */
@Preview(showBackground = true)
@Composable
private fun DateTimeFieldDateExpandedPreview() {
    var millis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    Column(modifier = Modifier.padding(16.dp)) {
        DateTimeField(
            label = "开始",
            millis = millis,
            onChanged = { millis = it },
            initialExpandedPart = DateTimePart.Date,
        )
    }
}

/** DateTimeField 预览：时间滚轮展开状态 */
@Preview(showBackground = true)
@Composable
private fun DateTimeFieldTimeExpandedPreview() {
    var millis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    Column(modifier = Modifier.padding(16.dp)) {
        DateTimeField(
            label = "结束",
            millis = millis,
            onChanged = { millis = it },
            initialExpandedPart = DateTimePart.Time,
        )
    }
}
