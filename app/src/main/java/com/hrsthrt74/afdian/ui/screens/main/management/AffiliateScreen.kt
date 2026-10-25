package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import com.hrsthrt74.afdian.data.network.afdianWebUrl
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.CardActionBar
import com.hrsthrt74.afdian.ui.components.basic.CardActionBarItem
import com.hrsthrt74.afdian.ui.components.basic.CopyButton
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.AffiliateViewModel
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
 * 新增 / 编辑 / 删除走页内的创建面板（见 [AffiliateCreateSheet]），复制优惠码码值也在这里。
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
                            ErrorStateView(
                                onRetry = viewModel::load,
                                modifier = Modifier.fillParentMaxSize(),
                                summary = error,
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
                                // 生成链接：https://{当前线路}/a/{slug}/plan?affiliate_code={code}
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
                // 当前展示状态：文案与颜色共用一份判断，避免两者不同步
                val status = affiliate.displayStatus
                // 状态颜色：状态切换时做 200ms 渐变过渡（生效=主色 / 已过期=错误红 / 未生效=灰）
                val statusColor by animateColorAsState(
                    targetValue = when (status) {
                        AffiliateDisplayStatus.Active -> MiuixTheme.colorScheme.primary
                        AffiliateDisplayStatus.Expired -> MiuixTheme.colorScheme.error
                        AffiliateDisplayStatus.Inactive -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    animationSpec = tween(200),
                    label = "statusColor",
                )
                // 状态文案：「生效中 / 已过期 / 未生效」
                Text(
                    text = status.label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = statusColor,
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

            // 信息区（bottom padding 16dp：分割线改由操作栏组件内置后，在这里保住原间隔）
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)) {
                AffiliateInfoRow(label = "折扣", value = affiliate.discountLabel())
                AffiliateInfoRow(label = "适用范围", value = affiliate.planLabel())
                AffiliateInfoRow(label = "已使用", value = affiliate.usedLabel())
                AffiliateInfoRow(label = "有效期", value = affiliate.periodRangeLabel())
            }

            // 底部操作栏：编辑 / 生成链接，两列均分（通用 CardActionBar，触觉、热区、分割线由组件内置）
            // slug 就绪才有生成动作；显式声明局部变量，避免嵌套 lambda 的类型推断歧义
            val generateLinkAction: (() -> Unit)? = link?.let { url -> { onGenerateLink(url) } }
            CardActionBar(
                items = listOf(
                    // ── 编辑：打开编辑面板 ──
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.Edit,
                        label = "编辑",
                        onClick = onEdit,
                    ),
                    // ── 生成链接：slug 未就绪时置灰不可点（onClick/tint 留空 = 次要色纯展示）──
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.Link,
                        label = "生成链接",
                        tint = if (link != null) MiuixTheme.colorScheme.primary else null,
                        onClick = generateLinkAction,
                    ),
                ),
                showDivider = true,
            )
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

/** 拼接优惠码分享链接：`https://{当前线路}/a/{slug}/plan?affiliate_code={code}`；任一为空返回 null */
private fun affiliateLink(slug: String, affiliateCode: String): String? =
    if (slug.isBlank() || affiliateCode.isBlank()) null
    // 分享链接跟随优先线路，与本机实际使用的域名保持一致
    else afdianWebUrl("/a/$slug/plan?affiliate_code=$affiliateCode")

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

/**
 * 优惠码在卡片上的展示状态（三态）。
 *
 * @param label 状态文案，直接用于卡片右上角
 */
private enum class AffiliateDisplayStatus(val label: String) {
    /** 开关开启且在有效期内 */
    Active("生效中"),
    /** 开关开启但已超出有效期（文案与颜色都按异常标注） */
    Expired("已过期"),
    /** 开关关闭 */
    Inactive("未生效"),
}

/**
 * 计算展示状态：先看开关，再看是否在有效期内。
 * 未开启一律「未生效」；开启后超期标「已过期」，否则「生效中」。
 */
private val Affiliate.displayStatus: AffiliateDisplayStatus
    get() = when {
        switch != 1 -> AffiliateDisplayStatus.Inactive
        isOutOfPeriod -> AffiliateDisplayStatus.Expired
        else -> AffiliateDisplayStatus.Active
    }

/**
 * 是否「设置有效期且当前在有效期之外」（未开始或已过期）。
 * 服务端 in_valid_period：1=在有效期内，0=在有效期外；未设时限时恒为 0，故需 has_time_limit 过滤。
 */
private val Affiliate.isOutOfPeriod: Boolean
    get() = has_time_limit == 1 && in_valid_period != 1

/** 有效期时间范围文案：无时限显示「不限」，有时限则拼起止时间 */
private fun Affiliate.periodRangeLabel(): String =
    if (has_time_limit != 1) "不限"
    else "${formatAffiliateTime(begin_time)}  至\n${formatAffiliateTime(end_time)}"

/** Unix 秒转本地时区的展示时间；缺失（<=0）时用 "--" 占位 */
private fun formatAffiliateTime(seconds: Long): String =
    if (seconds <= 0) "--"
    else Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).format(affiliateTimeFormatter)
