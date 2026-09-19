package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.content.Affiliate
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.CopyButton
import com.hrsthrt74.afdian.ui.components.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.EmptyStateView
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.AffiliateViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 有效期的统一展示格式：yyyy-MM-dd HH:mm（DateTimeFormatter 线程安全，可全局复用） */
private val affiliateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

/**
 * 优惠码页（创作者后台 creator/list-affiliate）。
 *
 * 列表展示每个优惠码的折扣、适用范围、已使用量、有效期与启用状态；
 * 编辑与创建入口后续再补，本页仅提供读取与复制优惠码。
 *
 * @param onBack 返回
 * @param backStack 供返回按钮读取来源做过渡动画
 */
@Composable
fun AffiliateScreen(onBack: () -> Unit, backStack: NavBackStack) {
    val app = LocalContext.current.applicationContext as Application
    // AndroidViewModel 走 AndroidViewModelFactory（项目规则，参考 ManagementScreen）
    val viewModel: AffiliateViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app),
    )
    val affiliates by viewModel.affiliates.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    // 正在提交开关请求的优惠码集合，用于禁用对应开关防连点
    val pendingAffiliateCodes by viewModel.pendingAffiliateCodes.collectAsState()
    // 当前账号 slug，用于拼接优惠码分享链接
    val creatorSlug by viewModel.creatorSlug.collectAsState()
    // 创建面板相关状态
    val selectablePlans by viewModel.selectablePlans.collectAsState()
    val plansLoading by viewModel.plansLoading.collectAsState()
    val submitting by viewModel.isSubmitting.collectAsState()
    // 编辑面板相关状态
    val editingAffiliate by viewModel.editingAffiliate.collectAsState()
    val deleting by viewModel.isDeleting.collectAsState()
    // 下拉刷新状态
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var pullRefreshing by remember { mutableStateOf(false) }
    // 是否显示创建 / 编辑优惠码面板
    var showSheet by remember { mutableStateOf(false) }

    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    // 列表滚动状态：双击顶栏回顶 + 滚动条共用
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current
    // 剪贴板：生成优惠码链接时写入
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val haptic = LocalHapticFeedback.current

    // 下拉刷新：触发刷新请求
    LaunchedEffect(pullRefreshing) {
        if (pullRefreshing) viewModel.refresh()
    }
    // 刷新完成后重置状态
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullRefreshing = false
    }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
            // 顶栏实时模糊：与其他页面同款写法（BlurredBar + blurBarColor 半透明底色）
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                TopAppBar(
                    title = "优惠码",
                    largeTitle = "优惠码",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                    actions = {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearEditingAffiliate()
                            showSheet = true
                        }) {
                            Icon(imageVector = MiuixIcons.Add, contentDescription = "新优惠码")
                        }
                    },
                )
            }
            }
        }
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = pullRefreshing,
            onRefresh = { pullRefreshing = true },
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新成功"),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            Box {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                        .scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = contentBottomPadding(),
                    )
                ) {
                    // 说明卡片
                    item(key = "intro") {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "优惠码可让获得码的用户购买指定方案或商品时，享受规定的折扣。\n" +
                                        "如果你有分销需求，通过利用优惠码，可以知道分销渠道的销量。",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }

                    when {
                        // 首次加载：列表为空且正在请求
                        loading && affiliates.isEmpty() -> item(key = "loading") {
                            LoadingComponent(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
                        }
                        // 加载失败且无缓存：空状态 + 重试
                        error != null && affiliates.isEmpty() -> item(key = "error") {
                            EmptyStateView(
                                title = "加载失败",
                                summary = error,
                                modifier = Modifier.fillParentMaxSize(),
                                action = {
                                    Button(onClick = viewModel::load, modifier = Modifier.fillMaxWidth()) {
                                        Text("重试")
                                    }
                                },
                            )
                        }
                        // 请求成功但没有优惠码
                        affiliates.isEmpty() -> item(key = "empty") {
                            EmptyStateView(
                                title = "还没有优惠码",
                                summary = "在爱发电创作者后台创建优惠码后，这里就会显示啦～",
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                        else -> items(affiliates, key = { it.id }) { affiliate ->
                            AffiliateCard(
                                affiliate = affiliate,
                                // 生成链接：https://ifdian.net/a/{slug}/plan?affiliate_code={code}
                                link = affiliateLink(creatorSlug, affiliate.affiliate_code),
                                pending = affiliate.affiliate_code in pendingAffiliateCodes,
                                onToggle = { enable ->
                                    // 切换结果用 Toast 提示（成功/失败文案由 ViewModel 回传）
                                    viewModel.switchAffiliate(affiliate.affiliate_code, enable) { _, message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onGenerateLink = { url ->
                                    // 生成链接即写入剪贴板，方便直接粘贴分享
                                    clipboard.setPrimaryClip(ClipData.newPlainText("优惠码链接", url))
                                    Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                                },
                                onEdit = {
                                    // 编辑：拉取详情并打开面板（先列表秒开，详情到达后覆盖）
                                    viewModel.loadAffiliateDetail(affiliate.affiliate_code)
                                    showSheet = true
                                },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 12.dp),
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // 创建 / 编辑优惠码面板：成功后由内部关闭，列表由 ViewModel 自动刷新
    AffiliateCreateSheet(
        show = showSheet,
        editAffiliate = editingAffiliate,
        plans = selectablePlans,
        plansLoading = plansLoading,
        submitting = submitting,
        deleting = deleting,
        onDismiss = {
            showSheet = false
            // 关闭时清掉编辑态，避免下次创建面板带着旧数据
            viewModel.clearEditingAffiliate()
        },
        onLoadPlans = { viewModel.loadSelectablePlans() },
        onGenerateCode = { onResult -> viewModel.generateCode(onResult) },
        onSubmit = { form, onResult ->
            // 编辑态走更新，否则走创建（表单里已带 id）
            if (editingAffiliate != null) {
                viewModel.updateAffiliate(form, onResult)
            } else {
                viewModel.createAffiliate(form, onResult)
            }
        },
        onDelete = {
            val target = editingAffiliate
            if (target != null) {
                viewModel.deleteAffiliate(target.affiliate_code) { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (success) showSheet = false
                }
            }
        },
        onToast = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
    )
}

/**
 * 单个优惠码卡片：优惠码 + 复制、折扣、适用范围、已使用 / 有效期、生效开关、生成链接。
 *
 * 手机端改为纵向堆叠，避免网页版横向排布在小屏上被压缩。
 * 「生效」开关会调用 affiliate-switch 接口；「生成链接」在 slug 可用时才可点击。
 *
 * @param link 优惠码分享链接，slug 缺失时为 null（入口置灰）
 * @param pending 是否正在提交开关请求，为 true 时禁用开关防连点
 * @param onToggle 开关切换回调，参数为目标启用状态
 * @param onEdit 编辑回调（打开编辑面板）
 * @param onGenerateLink 生成链接回调，参数为拼好的链接
 */
@Composable
private fun AffiliateCard(
    affiliate: Affiliate,
    link: String?,
    pending: Boolean,
    onToggle: (Boolean) -> Unit,
    onGenerateLink: (String) -> Unit,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 点击操作栏时的轻触觉反馈，手感与 PostActionBar 保持一致
    val haptic = LocalHapticFeedback.current
    // 先震动再触发动作（表达式体的这层大括号就是返回的 lambda，不要再包一层）
    fun hapticThen(action: () -> Unit): () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            // 头部：优惠码本体 + 复制按钮；右上角是生效开关与状态文案
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            ) {
                Text(
                    text = affiliate.affiliate_code.ifBlank { "未命名优惠码" },
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                )
                // 复制优惠码：震动 → 写剪贴板 → 图标切 Ok + Toast，全部由 CopyButton 内部处理
                CopyButton(
                    text = affiliate.affiliate_code,
                    toastText = "已复制 ${affiliate.affiliate_code}",
                    enabled = affiliate.affiliate_code.isNotBlank(),
                    contentDescription = "复制优惠码",
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                // 状态文案：跟随开关显示「生效中 / 未生效」
                Text(
                    text = affiliate.periodStatusLabel(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (affiliate.switch == 1) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
                Spacer(Modifier.width(8.dp))
                // 提交中禁用开关，避免重复请求
                Switch(
                    checked = affiliate.switch == 1,
                    onCheckedChange = { enable -> onToggle(enable) },
                    enabled = !pending,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(16.dp, 8.dp))

            // 信息区
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp, 8.dp)) {
                AffiliateInfoRow(label = "折扣", value = affiliate.discountLabel())
                AffiliateInfoRow(label = "适用范围", value = affiliate.planLabel())
                AffiliateInfoRow(label = "已使用", value = affiliate.usedLabel())
                AffiliateInfoRow(label = "有效期", value = affiliate.periodRangeLabel())
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))

            // 底部操作栏（照 PostActionBar）：编辑 / 生成链接，两列均分
            Row(modifier = Modifier.fillMaxWidth()) {
                // ── 第 1 列：编辑 ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = hapticThen(onEdit))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MiuixIcons.Demibold.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "编辑",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                // ── 第 2 列：生成链接（slug 未就绪时置灰且不可点）──
                val linkTint = if (link != null) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            enabled = link != null,
                            onClick = hapticThen { link?.let(onGenerateLink) },
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MiuixIcons.Demibold.Link,
                            contentDescription = "生成链接",
                            modifier = Modifier.size(18.dp),
                            tint = linkTint,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "生成链接",
                            style = MiuixTheme.textStyles.footnote1,
                            color = linkTint,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 优惠码信息行：左侧固定宽度的标签 + 右侧占满剩余空间的内容。
 *
 * @param label 字段名（如「适用范围」）
 * @param value 字段内容
 */
@Composable
private fun AffiliateInfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body2,
        )
    }
}

/** 拼接优惠码分享链接：https://ifdian.net/a/{slug}/plan?affiliate_code={code}；任一为空返回 null */
private fun affiliateLink(slug: String, affiliateCode: String): String? =
    if (slug.isBlank() || affiliateCode.isBlank()) null
    else "https://ifdian.net/a/$slug/plan?affiliate_code=$affiliateCode"

/** 折扣文案：discount_type=1 折扣（0.5 → 5折），=2 显示直减金额，其余回退原始折扣值 */
private fun Affiliate.discountLabel(): String = when (discount_type) {
    1 -> discount.toBigDecimalOrNull()
        ?.multiply(BigDecimal.TEN)
        ?.stripTrailingZeros()
        ?.toPlainString()
        ?.let { "${it}折" }
        ?: "${discount}折"
    2 -> amount_off.takeIf { it.toBigDecimalOrNull()?.signum() == 1 }?.let { "减￥$it" } ?: "直减"
    else -> if (discount.isNotBlank()) "${discount}折" else "优惠"
}

/** 已使用文案：限量时显示「已用 / 上限」，不限量显示「已用 / 不限」 */
private fun Affiliate.usedLabel(): String =
    if (has_count_limit == 1) "$used_count / $count_limit" else "$used_count / 不限"

/** 适用范围：指定方案取方案名拼接，否则视为全部方案 */
private fun Affiliate.planLabel(): String = when {
    plan_range == "plans" && plans.isNotEmpty() ->
        plans.joinToString("、") { it.name.ifBlank { "未命名方案" } }
    plan_range == "plans" -> "指定方案"
    else -> "全部方案"
}

/** 状态文案：开关开启且处于有效期内显示「生效中」，否则「未生效」 */
private fun Affiliate.periodStatusLabel(): String =
    if (switch == 1 && (has_time_limit != 1 || in_valid_period == 1)) "生效中" else "未生效"

/** 有效期时间范围文案：无时限显示「不限」，有时限则拼起止时间 */
private fun Affiliate.periodRangeLabel(): String =
    if (has_time_limit != 1) "不限"
    else "${formatAffiliateTime(begin_time)}  至\n${formatAffiliateTime(end_time)}"

/** Unix 秒转本地时区的展示时间；缺失（<=0）时用 "--" 占位 */
private fun formatAffiliateTime(seconds: Long): String =
    if (seconds <= 0) "--"
    else Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).format(affiliateTimeFormatter)
