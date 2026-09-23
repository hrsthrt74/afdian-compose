package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.CreatorPlanSku
import com.hrsthrt74.afdian.data.content.CreatorPlanSkus
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.HtmlContent
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.viewmodel.PlanDetailViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun PlanDetailSheet(
    show: Boolean,
    planId: String?,
    viewModel: PlanDetailViewModel,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(show, planId) {
        if (show) planId?.let(viewModel::load)
    }
    val detail by viewModel.detail.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val purchaseDialogState = rememberDialogState()
    val uriHandler = LocalUriHandler.current

    WindowBottomSheet(
        show = show,
        title = "详情",
        onDismissRequest = onDismiss,
    ) {
        when {
            loading && detail == null -> LoadingComponent(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
            detail == null -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(error ?: "暂无详情")
                Button(onClick = viewModel::retry) { Text("重试") }
            }
            else -> {
                val loadedDetail = detail!!
                PlanDetailContent(
                    detail = loadedDetail,
                    onPurchase = purchaseDialogState::show,
                )
                AppDialog(
                    state = purchaseDialogState,
                    title = "跳转官方网页",
                    summary = "出于安全考虑，本应用不提供直接购买。点击确定后将跳转到官方网页。",
                    confirmText = "确定",
                    onConfirm = {
                        val plan = loadedDetail.plan
                        val path = if (plan?.product_type == 2) "bundle" else "item"
                        uriHandler.openUri("https://ifdian.net/$path/${plan?.plan_id.orEmpty()}")
                    },
                )
            }
        }
    }
}

@Composable
private fun PlanDetailContent(
    detail: CreatorPlanSkus,
    onPurchase: () -> Unit,
) {
    val listState = rememberLazyListState()
    val plan = detail.plan
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .scrollEndHaptic(),
            // 给悬浮按钮留出滚动安全区，最后一项仍可完整滚到按钮上方。
            contentPadding = PaddingValues(bottom = 128.dp),
        ) {
            // 第一部分：封面、标题和金额使用紧凑的 8dp 内部间距。
            item(key = "overview") {
                Column{
                    // 封面
                    if (!plan?.pic.isNullOrBlank()) {
                        AsyncImage(
                            model = plan.pic,
                            contentDescription = plan.name.ifBlank { "商品封面" },
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .squircleClip(16.dp),
                        )
                    }
                    // 名字
                    Text(
                        text = plan?.name.orEmpty().ifBlank { "商品 / 计划" },
                        style = MiuixTheme.textStyles.title2,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    // 金额
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        // 金额数字
                        Text(
                            text = "￥${plan?.show_price ?: plan?.price ?: detail.floor_price ?: "--"}",
                            style = MiuixTheme.textStyles.title1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        // 「/ 月」
                        // 商品类型 1/2 是一次性商品，不显示订阅周期。
                        if (plan?.product_type !in setOf(1, 2) && (plan?.pay_month ?: 0) > 0) {
                            Text(
//                                text = " / ${plan?.pay_month}月",
                                text = " /月",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }
            // 第二部分：HTML 独立成块，不和概览区共用间距。
            if (!plan?.desc.isNullOrBlank()) {
                item(key = "html_title") {
                    SmallTitle("详情", insideMargin = PaddingValues(start = 16.dp, top = 28.dp, bottom = 8.dp))
                }
                item(key = "html") {
                    Card(
                        colors = CardColors(
                            MiuixTheme.colorScheme.secondaryContainer,
                            MiuixTheme.colorScheme.onSurfaceContainerHighest,
                        )
                    ) {
                        HtmlContent(
                            html = plan.desc,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
            // 普通商品只有一个「默认型号」时，型号只是接口占位数据，不单独展示。
            val visibleSkus = detail.list.filterNot { sku ->
                plan?.product_type == 1 &&
                    detail.list.size == 1 &&
                    sku.name == "默认型号"
            }
            if (visibleSkus.isNotEmpty()) {
                item(key = "sku_title") {
                    SmallTitle("型号", insideMargin = PaddingValues(start = 16.dp, top = 28.dp, bottom = 8.dp))
                }
                items(visibleSkus, key = { it.sku_id }) { sku ->
                    // 捆绑包的型号通常没有自己的封面，依次回退到型号关联方案、
                    // 返回的关联方案列表，最后才使用当前详情方案封面。
                    val fallbackPlanPic = sku.plan?.pic
                        ?: detail.vip_all_plans.firstOrNull { it.plan_id == sku.plan_id }?.pic
                        ?: plan?.pic
                    SkuItem(
                        sku = sku,
                        fallbackPic = fallbackPlanPic,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
        // 购买（发电）按钮
        Button(
            // 0 表示售罄；-1 表示不限库存，仍可购买。
            enabled = plan?.can_buy_count != 0,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onPurchase()},
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
        ) {
            Text("发电 ￥${plan?.show_price ?: plan?.price ?: detail.floor_price ?: "--"}")
            Icon(MiuixIcons.Forward, null, Modifier.padding(start = 8.dp))
        }
    }
}

// 「可选型号」项目
@Composable
private fun SkuItem(
    sku: CreatorPlanSku,
    fallbackPic: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardColors(MiuixTheme.colorScheme.secondaryContainer, MiuixTheme.colorScheme.onSurfaceContainerHighest),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val cover = sku.pic?.takeIf { it.isNotBlank() } ?: fallbackPic?.takeIf { it.isNotBlank() }
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = sku.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .squircleClip(8.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(MiuixTheme.colorScheme.disabledSecondary)
                        .squircleClip(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Demibold.Favorites,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sku.name.ifBlank { "默认型号" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                sku.desc?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("￥${sku.price ?: "--"}", color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.title2, modifier = Modifier.padding(end = 8.dp))
        }
    }
}
