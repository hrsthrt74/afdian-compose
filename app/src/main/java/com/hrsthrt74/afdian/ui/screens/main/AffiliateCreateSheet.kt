package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.Affiliate
import com.hrsthrt74.afdian.data.content.AffiliateForm
import com.hrsthrt74.afdian.data.content.AffiliateSelectablePlan
import com.hrsthrt74.afdian.ui.components.AppDialog
import com.hrsthrt74.afdian.ui.components.DateTimeField
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.rememberDialogState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.PlainTooltip
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import kotlin.math.roundToInt

/** 适用范围选项：value 直接作为接口的 plan_range */
private val planRangeOptions = listOf(
    "subscribe" to "所有赞助方案",
    "product" to "所有商品",
    "all" to "所有赞助方案与商品",
    "plans" to "选择赞助方案与商品",
)

/** 折扣滑杆的整数刻度（折）：5~9 折 + 10 折（不打折） */
private val discountStops = listOf(5f, 6f, 7f, 8f, 9f, 10f)

/** 折扣滑杆的离散步数：5.0~10.0 每 0.1 折一档，共 51 个点，故中间 49 步 */
private const val discountSteps = 49

/** 优惠码校验：5-8 位字母或数字 */
private val affiliateCodeRegex = Regex("^[A-Za-z0-9]{5,8}$")

/**
 * 创建 / 编辑优惠码面板（generate-affiliate-code + simple-plan-list + edit-affiliate + delete-affiliate）。
 *
 * 布局参考网页版，改为手机端纵向表单；sheet 自身已有左右边距，内容不再额外加。
 * 编辑模式下仅「限制次数 / 每个用户使用次数 / 有效期」可改，其余字段置灰只读，并在底部提供删除按钮。
 *
 * @param show 是否显示
 * @param editAffiliate 非空表示编辑模式（先用列表项秒开，详情到达后由上层替换）
 * @param plans 可选赞助方案 / 商品（创建模式用）
 * @param plansLoading 方案列表加载中
 * @param submitting 正在提交
 * @param deleting 正在删除
 * @param onDismiss 关闭面板
 * @param onLoadPlans 请求方案列表
 * @param onGenerateCode 随机生成优惠码，结果通过回调返回（失败为 null）
 * @param onSubmit 提交保存（创建或编辑），结果通过回调返回
 * @param onDelete 确认删除（编辑模式）
 * @param onToast 轻提示
 */
@Composable
fun AffiliateCreateSheet(
    show: Boolean,
    editAffiliate: Affiliate? = null,
    plans: List<AffiliateSelectablePlan>,
    plansLoading: Boolean,
    submitting: Boolean,
    deleting: Boolean = false,
    onDismiss: () -> Unit,
    onLoadPlans: () -> Unit,
    onGenerateCode: (onResult: (String?) -> Unit) -> Unit,
    onSubmit: (AffiliateForm, onResult: (Boolean, String) -> Unit) -> Unit,
    onDelete: () -> Unit = {},
    onToast: (String) -> Unit,
) {
    // 编辑模式：锁定自定义码 / 适用方案 / 折扣
    val isEdit = editAffiliate != null

    // ── 表单状态 ──
    var code by remember { mutableStateOf("") }
    var planRange by remember { mutableStateOf("subscribe") }
    var selectedPlanIds by remember { mutableStateOf(emptySet<String>()) }
    // 折扣以「0.1 折」为单位保存（50~100），100 表示不打折，避免浮点误差
    var discountTenths by remember { mutableIntStateOf(90) }
    var hasCountLimit by remember { mutableStateOf(false) }
    var countLimit by remember { mutableStateOf("") }
    var hasUserLimit by remember { mutableStateOf(false) }
    var useLimitCount by remember { mutableStateOf("") }
    var hasTimeLimit by remember { mutableStateOf(false) }
    // 起止时间用毫秒保存，提交时换算成秒
    var beginMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var endMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000) }

    // 删除确认弹窗
    val deleteDialogState = rememberDialogState()

    val haptic = LocalHapticFeedback.current

    // 打开 / 切换编辑对象时初始化表单（编辑用详情填充，创建则重置并拉方案）
    LaunchedEffect(show, editAffiliate) {
        if (!show) return@LaunchedEffect
        val source = editAffiliate
        if (source != null) {
            code = source.affiliate_code
            planRange = source.plan_range.ifBlank { "subscribe" }
            selectedPlanIds = source.plans.map { it.plan_id }.toSet()
            discountTenths = ((source.discount.toDoubleOrNull() ?: 0.9) * 100).roundToInt()
            hasCountLimit = source.has_count_limit == 1
            countLimit = if (source.count_limit > 0) source.count_limit.toString() else ""
            hasUserLimit = source.use_limit_count > 0
            useLimitCount = if (source.use_limit_count > 0) source.use_limit_count.toString() else ""
            hasTimeLimit = source.has_time_limit == 1
            beginMillis = if (source.begin_time > 0) source.begin_time * 1000 else System.currentTimeMillis()
            endMillis = if (source.end_time > 0) source.end_time * 1000
            else beginMillis + 30L * 24 * 60 * 60 * 1000
        } else {
            code = ""
            planRange = "subscribe"
            selectedPlanIds = emptySet()
            discountTenths = 90
            hasCountLimit = false
            countLimit = ""
            hasUserLimit = false
            useLimitCount = ""
            hasTimeLimit = false
            beginMillis = System.currentTimeMillis()
            endMillis = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            onLoadPlans()
        }
    }

    // 点击保存：先本地校验，再提交
    fun save() {
        val source = editAffiliate
        val effectivePlanRange = source?.plan_range?.ifBlank { "subscribe" } ?: planRange
        // 创建模式才校验自定义码与适用范围（编辑模式下这两项锁定）
        if (source == null) {
            if (!affiliateCodeRegex.matches(code.trim())) {
                onToast("请输入 5-8 位字母或数字的优惠码")
                return
            }
            if (planRange == "plans" && selectedPlanIds.isEmpty()) {
                onToast("请选择优惠码适用范围")
                return
            }
        }
        if (hasCountLimit && (countLimit.trim().toIntOrNull() ?: 0) <= 0) {
            onToast("请输入有效的可使用次数")
            return
        }
        if (hasUserLimit && (useLimitCount.trim().toIntOrNull() ?: 0) <= 0) {
            onToast("请输入每个用户可使用次数")
            return
        }
        if (hasTimeLimit && endMillis <= beginMillis) {
            onToast("结束时间需晚于开始时间")
            return
        }

        val form = AffiliateForm(
            id = source?.id?.toString() ?: "",
            affiliateCode = source?.affiliate_code ?: code.trim(),
            // 编辑时折扣锁定，原样回传详情里的值
            discount = source?.discount?.toDoubleOrNull() ?: (discountTenths / 100.0),
            hasTimeLimit = hasTimeLimit,
            beginTime = if (hasTimeLimit) beginMillis / 1000 else 0,
            endTime = if (hasTimeLimit) endMillis / 1000 else 0,
            hasCountLimit = hasCountLimit,
            countLimit = if (hasCountLimit) countLimit.trim() else "0",
            useLimitCount = if (hasUserLimit) useLimitCount.trim() else "0",
            planRange = effectivePlanRange,
            // 编辑时原样回传已选方案；创建时按勾选取值
            planIds = source?.plans?.joinToString(",") { it.plan_id }
                ?: if (planRange == "plans") selectedPlanIds.joinToString(",") else "",
        )
        onSubmit(form) { success, message ->
            onToast(message)
            if (success) onDismiss()
        }
    }

    WindowBottomSheet(
        show = show,
        title = if (isEdit) "编辑优惠码" else "创建优惠码",
        onDismissRequest = onDismiss,
        // 左上角：关闭
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭")
            }
        },
        // 右上角：保存；提交中换成进度指示器
        endAction = {
            IconButton(onClick = { save() }, enabled = !submitting) {
                AnimatedContent(targetState = submitting, label = "save") { saving ->
                    if (saving) {
                        InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(imageVector = MiuixIcons.Ok, contentDescription = "保存")
                    }
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().scrollEndHaptic(),
            contentPadding = PaddingValues(bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 自定义码（编辑模式仅查看）──
            item(key = "code") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = code,
                        onValueChange = { input -> code = input.filter { it.isLetterOrDigit() }.take(8) },
                        label = "5-8 个字母数字",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        enabled = !isEdit,
                        modifier = Modifier.weight(1f),
                    )
                    // 随机：仅创建模式可用，直接调接口生成并塞进输入框
                    if (!isEdit) {
                        Text(
                            text = "随机",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(shape = RoundedCornerShape(16.dp))
                                .clickable {
                                    onGenerateCode { generated ->
                                        if (generated != null) code = generated else onToast("生成优惠码失败")
                                    }
                                }
                                .padding(8.dp, 16.dp),
                        )
                    }
                }
            }

            // ── 适用方案（编辑模式仅查看）──
            item(key = "plan_range") {
                SmallTitle("适用方案", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 8.dp))
                SheetCard {
                    Column {
                        if (isEdit) {
                            // 编辑态：只显示当前选中的选项
                            val currentLabel = planRangeOptions.find { it.first == planRange }?.second ?: planRange
                            SheetRadioRow(
                                label = currentLabel,
                                selected = true,
                                enabled = false,
                                onClick = {},
                            )
                        } else {
                            planRangeOptions.forEach { (value, label) ->
                                SheetRadioRow(
                                    label = label,
                                    selected = planRange == value,
                                    enabled = !isEdit,
                                    onClick = { planRange = value },
                                )
                            }
                        }
                        // 选「指定方案」时动画展开方案/商品多选
                        AnimatedVisibility(
                            visible = planRange == "plans",
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(Modifier.padding(16.dp, 4.dp))
                                if (isEdit) {
                                    // 编辑：只读展示已选方案
                                    val editPlans = editAffiliate?.plans.orEmpty()
                                    if (editPlans.isEmpty()) {
                                        Text(
                                            text = "未指定具体方案",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    } else editPlans.forEach { plan ->
                                        SheetCheckboxRow(
                                            label = plan.name.ifBlank { "未命名方案" },
                                            checked = true,
                                            enabled = false,
                                            onClick = {},
                                        )
                                    }
                                } else {
                                    when {
                                        plansLoading && plans.isEmpty() ->
                                            LoadingComponent(modifier = Modifier.padding(vertical = 8.dp))
                                        plans.isEmpty() ->
                                            Text(
                                                text = "暂无可选的赞助方案或商品",
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            )
                                        else -> plans.forEach { plan ->
                                            SheetCheckboxRow(
                                                label = plan.name.ifBlank { "未命名方案" },
                                                checked = plan.plan_id in selectedPlanIds,
                                                onClick = {
                                                    selectedPlanIds = if (plan.plan_id in selectedPlanIds) {
                                                        selectedPlanIds - plan.plan_id
                                                    } else {
                                                        selectedPlanIds + plan.plan_id
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 折扣（编辑模式仅查看）──
            item(key = "discount") {
                SmallTitle("折扣", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 8.dp))
                SheetCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 折扣文案：整数折不带小数，100（10 折）显示「不打折」
                        val zheWhole = discountTenths / 10
                        val zheFrac = discountTenths % 10
                        val zheLabel = when {
                            discountTenths >= 100 -> "不打折"
                            zheFrac == 0 -> "${zheWhole}折"
                            else -> "$zheWhole.${zheFrac}折"
                        }
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = zheLabel,
                                style = MiuixTheme.textStyles.title3,
                            )
                            if (discountTenths >= 100) {
                                Spacer(Modifier.width(4.dp))
                                val tooltipState = rememberTooltipState()
                                val scope = rememberCoroutineScope()
                                TooltipBox(
                                    tooltip = {
                                        PlainTooltip {
                                            Text(
                                                "如果你有分销需求，利用无折扣优惠码，可以知道分销渠道的销量。",
                                                style = MiuixTheme.textStyles.footnote1
                                            )
                                        }
                                              },
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                                    state = tooltipState,
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch { tooltipState.show() }
                                            },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Demibold.Info,
                                            contentDescription = "信息",
                                            modifier = Modifier.size(20.dp),
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // 离散滑杆：5.0~10.0 每 0.1 折一档；整数折处有 key point，经过刻度时 Step 触觉
                        Slider(
                            value = discountTenths / 10f,
                            onValueChange = { value ->
                                // 吸附到 0.1 折并立刻转成整数档位，彻底避免浮点误差
                                discountTenths = (value * 10).roundToInt()
                            },
                            valueRange = 5f..10f,
                            steps = discountSteps,
                            enabled = !isEdit,
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                            showKeyPoints = true,
                            keyPoints = discountStops,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        // 刻度说明
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        ) {
                            listOf(" 5折 ", " 6折 ", " 7折 ", " 8折 ", " 9折 ", "不打折").forEach { label ->
                                Text(
                                    text = label,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            // ── 限制（编辑模式下可改）──
            item(key = "limit") {
                SmallTitle("限制", insideMargin = PaddingValues(16.dp, 12.dp, 8.dp, 8.dp))

                // 可使用次数
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = countLimit,
                        onValueChange = { input -> countLimit = input.filter { it.isDigit() } },
                        label = "可使用次数",
                        singleLine = true,
                        enabled = hasCountLimit,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = hasCountLimit, onCheckedChange = { hasCountLimit = it })
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.height(12.dp))
                // 每个用户可使用次数
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = useLimitCount,
                        onValueChange = { input -> useLimitCount = input.filter { it.isDigit() } },
                        label = "每个用户可使用次数",
                        singleLine = true,
                        enabled = hasUserLimit,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = hasUserLimit, onCheckedChange = { hasUserLimit = it })
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.height(12.dp))
                // 时间限制
                SheetCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SheetToggleRow(
                            label = "限制有效期",
                            checked = hasTimeLimit,
                            onCheckedChange = { hasTimeLimit = it },
                        )
                        AnimatedVisibility(
                            visible = hasTimeLimit,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column {
                                Spacer(Modifier.height(24.dp))
                                DateTimeField(
                                    label = "开始",
                                    millis = beginMillis,
                                    onChanged = { beginMillis = it },
                                )
                                Spacer(Modifier.height(12.dp))
                                DateTimeField(
                                    label = "结束",
                                    millis = endMillis,
                                    onChanged = { endMillis = it },
                                )
                            }
                        }
                    }
                }
            }

            // ── 删除（仅编辑模式）──
            if (isEdit) {
                item(key = "delete") {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                            deleteDialogState.show()
                        },
                        colors = ButtonDefaults.buttonColors(contentColor = MiuixTheme.colorScheme.error),
                        enabled = !deleting,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    ) {
                        Icon(MiuixIcons.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("删除")
                    }
                }
            }
        }
    }

    // 删除确认
    AppDialog(
        state = deleteDialogState,
        title = "删除优惠码",
        summary = "确定删除「${editAffiliate?.affiliate_code.orEmpty()}」吗？删除后不可恢复。",
        confirmText = "删除",
        destructive = true,
        onConfirm = { onDelete() },
    )
}

/** 表单分区内容卡片：surface 底色，占满 sheet 内容宽度 */
@Composable
private fun SheetCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/** 单选行：整行可点，行高加大便于点按；点击时轻震动；编辑锁定态置灰不可点 */
@Composable
private fun SheetRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 多选行：用于「指定方案」的方案/商品勾选；编辑锁定态置灰不可点 */
@Composable
private fun SheetCheckboxRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(start = 48.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = null,
            enabled = enabled,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 开关行：左文案 + 右开关（水平边距由外层卡片负责） */
@Composable
private fun SheetToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
