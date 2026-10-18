package com.hrsthrt74.afdian.ui.components

// 会话页特殊消息卡片合集：电圈邀请、发电订单、兑换码、发货通知、投诉通知。

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.ComplaintNotice
import com.hrsthrt74.afdian.data.content.MessageGroup
import com.hrsthrt74.afdian.data.content.MessageObjectContent
import com.hrsthrt74.afdian.data.content.ShipmentNotice
import com.hrsthrt74.afdian.ui.navigation.LocalAppLinkNavigator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 电圈卡片：上下两部分。上半是动作文案（邀请/已加入），
 * 下半是「方形封面 + 圈子标题 + 右箭头」。
 */
@Composable
fun GroupInviteCard(
    group: MessageGroup,
    userName: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column {
            // 上半区：动作文案；join_group_type=1 表示已加入，其余表示对方邀请
            val headline = if (group.join_group_type == 1) {
                "您已加入"
            } else {
                "$userName 邀请您加入"
            }
            Text(
                text = headline,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // 下半区：方形封面 + 标题 + 右箭头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable{ } // 写完「电圈」页再绑定这里
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 圈子封面（方形，裁切为直角圆角小块）
                group.cover?.let { coverUrl ->
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                // 右侧：标题 + 电圈描述（content 字段）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = group.title ?: "电圈",
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 电圈描述：content 非空时在标题下方展示，最多两行
                    group.content?.takeIf { it.isNotBlank() }?.let { desc ->
                        Text(
                            text = desc,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                // 右箭头
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

/**
 * 发电订单卡片：外层 Card 自带圆角裁切，内含金额头 + plan/型号 + 备注 + 订单号。
 */
@Composable
fun OrderCard(
    content: MessageObjectContent,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column{

            // 优先 show_amount → total_amount → per_month；
            // 使用优惠码时 show_amount 是优惠前原价，改用实付金额 total_amount
            val usedAffiliate = content.affiliateReduction != null
            val amount = if (usedAffiliate) {
                content.total_amount ?: content.show_amount ?: content.per_month
            } else {
                content.show_amount ?: content.total_amount ?: content.per_month
            }
            val month = content.month
            // ext.redeem 有值表示该发电订单使用了兑换码
            val usedRedeem = !content.redeem.isNullOrBlank()

            if (amount != null) {

                // 上半金额区
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.primary)
                        .padding(16.dp, 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {1
                    // 金额：￥
                    Text(
                        text = "¥",
                        style = MiuixTheme.textStyles.body1,
                        color = if (usedRedeem) { MiuixTheme.colorScheme.onPrimary.copy(0.8f) }
                                else            { MiuixTheme.colorScheme.onPrimary },
                        modifier = Modifier.padding(end = 2.dp).alignByBaseline()
                    )
                    // 金额：数字
                    Text(
                        text = amount,
                        color = if (usedRedeem) { MiuixTheme.colorScheme.onPrimary.copy(0.8f) }
                                else            { MiuixTheme.colorScheme.onPrimary },
                        // 使用兑换码时对发电金额加删除线，表示非实付
                        style = if (usedRedeem) { MiuixTheme.textStyles.title1.copy(textDecoration = TextDecoration.LineThrough) }
                                else            { MiuixTheme.textStyles.title1 },
                        modifier = Modifier.alignByBaseline()
                    )

                    // n个月
                    if (month != null && month > 0 && content.product_type != 1) {
                        Text(
                            text = "/ $month 个月",
                            style = MiuixTheme.textStyles.body2,
                            color = if (usedRedeem) { MiuixTheme.colorScheme.onPrimary.copy(0.8f) }
                                    else            { MiuixTheme.colorScheme.onPrimary },
                            modifier = Modifier.padding(start = 4.dp).alignByBaseline()
                        )
                    }

                    // 「使用兑换码」/「优惠了￥X」标注：两者一般不会同时出现，兑换码优先
                    if (usedRedeem) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "使用兑换码",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 4.dp).alignByBaseline()
                        )
                    } else if (usedAffiliate) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            // 优惠码减免金额小字说明（大金额已展示为实付价格）
                            text = "优惠了￥${content.affiliateReduction}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 4.dp).alignByBaseline()
                        )
                    }
                }
            }

            // 下半信息部分
            Column(
                modifier = Modifier.padding(16.dp, 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 发电了…
                val planName = content.plan?.name ?: content.plan?.title
                if (planName != null && planName.isNotBlank()) {
                    Text(
                        text = "发电了 $planName",
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 型号
                val skuName = content.sku_detail?.firstOrNull()?.name?.takeIf { it != "默认型号" }
                if (skuName != null && skuName.isNotBlank()) {
                    Text(
                        text = "型号 $skuName",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        letterSpacing = 0.5.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 优惠码：使用优惠码的订单补充展示优惠码本身
                content.affiliateCode?.let { code ->
                    if (code.isNotBlank()) {
                        Text(
                            text = "优惠码 $code",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            letterSpacing = 0.5.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 留言
                content.remark?.let { remark ->
                    if (remark.isNotBlank()) {
                        HorizontalDivider()
                        Text(
                            text = "留言：$remark",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            letterSpacing = 0.5.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 收货信息：商品需要填写收货地址时，ext.address 会带出收货人信息
                content.address?.let { addressInfo ->
                    if (!addressInfo.address.isNullOrBlank()) {
                        HorizontalDivider()
                        // 收货人 + 联系电话
                        val recipient = listOfNotNull(addressInfo.name, addressInfo.phone)
                            .joinToString("  ")
                        if (recipient.isNotBlank()) {
                            Text(
                                text = recipient,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 完整收货地址
                        Text(
                            text = addressInfo.address,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 订单号
                content.out_trade_no?.let { orderNo ->
                    if (orderNo.isNotBlank()) {
                        HorizontalDivider()
                        Text(
                            text = orderNo,
                            style = MiuixTheme.textStyles.footnote2,
                            letterSpacing = 0.5.sp, // 防止过挤
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 兑换码消息。
 * 上半展示兑换码，下半点击「复制兑换码」。
 */
@Composable
fun RedeemCodeCard(
    code: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Card(modifier = modifier) {
        Column {
            // 上半区：兑换码，等宽放大便于查看/核对
            SelectionContainer {
                Text(
                    text = code,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 12.dp))

            // 下半区：整块可点的复制区，浅容器底色区分，主色图标+文案居中
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // 点击触发轻震动反馈
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // 兑换码写入系统剪贴板
                        val clipboardManager = context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("兑换码", code))
                        // 轻提示反馈
                        Toast.makeText(context, "已复制兑换码", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 复制图标
                Icon(
                    imageVector = MiuixIcons.Demibold.Copy,
                    contentDescription = "复制兑换码",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "复制兑换码",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 发货通知卡片：外层 Miuix Card 只做圆角裁切（squircleSurface），
 * 内部不套泡沫背景。上半为主色填充头部，下半素色区放物流字段与详情入口。
 */
@Composable
fun ShipmentNoticeCard(
    notice: ShipmentNotice,
    modifier: Modifier = Modifier,
) {
    // 「查看订单详情」统一交给 AppLinkNavigator
    val appLinkNavigator = LocalAppLinkNavigator.current
    val haptic = LocalHapticFeedback.current

    Card(modifier = modifier) {
        Column {
            // 头部主色填充区：白字标题，顶部两角圆角由 Card 自身裁切
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.primary)
            ) {
                Text(
                    text = "你购买的商品已发货",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // 下半部分
            Column(
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                notice.productName?.takeIf { it.isNotBlank() }?.let { name ->
                    // 商品名称
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider()

                // 详细信息
                notice.orderNo?.let { ShipmentFieldRow("爱发电订单号", it) }
                notice.trackingNo?.let { ShipmentFieldRow("快递运单号", it) }
                notice.courier?.let { ShipmentFieldRow("快递公司", it) }

                HorizontalDivider()
            }

            notice.detailUrl?.let { url ->
                // 查看订单详情
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            appLinkNavigator.open(url)
                        }
                        .padding(16.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Demibold.Notes,
                        contentDescription = "查看订单详情",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "查看订单详情",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 发货卡片中的「标签：值」行，标签固定宽左侧灰色，值为正文可换行。
 */
@Composable
private fun ShipmentFieldRow(label: String, value: String, valueColor: Color? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = valueColor ?: MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 投诉通知卡片：顶栏用 error 红底 + onError 文字标明「微信投诉/支付宝投诉」，
 * 标题下紧跟金额（样式参考 OrderCard），下半素色区展示其余字段与详情链接。
 */
@Composable
fun ComplaintCard(
    complaint: ComplaintNotice,
    modifier: Modifier = Modifier,
) {
    // 平台识别失败时回退为「投诉」
    val complaintSuffix = if (complaint.isUpgradeComplaint) "升级投诉" else "投诉"
    val title = complaint.platform?.let { "${it}${complaintSuffix}" } ?: complaintSuffix
    val amount = complaint.amount?.takeIf { it.isNotBlank() }
    val appLinkNavigator = LocalAppLinkNavigator.current
    val haptic = LocalHapticFeedback.current
    val upgradeTextColor = if (complaint.isUpgradeComplaint) {
        MiuixTheme.colorScheme.error
    } else {
        null
    }

    Card(modifier = modifier) {
        Column {
            // 顶栏（红色强调）：标题 + 金额
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.error)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // 顶栏标题
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onError
                    )
                    // 金额（参考 OrderCard 金额区排版：¥ + 大字数字）
                    if (amount != null) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "¥",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onError,
                                modifier = Modifier.padding(end = 2.dp, bottom = 4.dp)
                            )
                            Text(
                                text = amount,
                                style = MiuixTheme.textStyles.title1,
                                color = MiuixTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }

            // 下半素色区：字段逐行展示
            Column(
                modifier = Modifier.padding(16.dp, 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                complaint.message?.takeIf { it.isNotBlank() }?.let { ShipmentFieldRow("留言", it) }
                complaint.reason?.takeIf { it.isNotBlank() }?.let {
                    ShipmentFieldRow("升级投诉原因", it, upgradeTextColor)
                }
                complaint.description?.takeIf { it.isNotBlank() }?.let {
                    ShipmentFieldRow("升级投诉描述", it, upgradeTextColor)
                }
                complaint.time?.takeIf { it.isNotBlank() }?.let { ShipmentFieldRow("投诉时间", it) }
                // 投诉单号在前、订单号在后，与通知模板的行序保持一致
                complaint.complaintNo?.takeIf { it.isNotBlank() }?.let { ShipmentFieldRow("投诉单号", it) }
                complaint.orderNo?.takeIf { it.isNotBlank() }?.let { ShipmentFieldRow("订单号", it) }
            }

//            Column{
//                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
//                complaint.detailUrl?.let { url ->
//                    // 查看用户详情：居中操作行（图标 + 文案），样式与其他带操作卡片一致
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .clickable {
//                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
//                                appLinkNavigator.open(url)
//                            }
//                            .padding(horizontal = 16.dp, vertical = 8.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.Center
//                    ) {
//                        // 用户图标
//                        Icon(
//                            imageVector = MiuixIcons.Demibold.Contacts,
//                            contentDescription = "查看用户详情",
//                            tint = MiuixTheme.colorScheme.primary,
//                            modifier = Modifier.size(16.dp)
//                        )
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Text(
//                            text = "查看用户详情",
//                            style = MiuixTheme.textStyles.body2,
//                            color = MiuixTheme.colorScheme.primary
//                        )
//                    }
//                }
//            }
        }
    }
}
