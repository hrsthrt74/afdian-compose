package com.hrsthrt74.afdian.ui.screens.main.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.SHOP_PLAN_STATUS_NORMAL
import com.hrsthrt74.afdian.data.content.ShopProduct
import com.hrsthrt74.afdian.data.content.ShopSku
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.CardActionBar
import com.hrsthrt74.afdian.ui.components.basic.CardActionBarItem
import com.hrsthrt74.afdian.ui.components.basic.TooltipInfoButton
import com.hrsthrt74.afdian.ui.components.basic.TooltipInfoButtonStyle
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 库存/销量/收入三列的固定宽度：型号列占剩余宽度（weight）。
 * 数值列定宽 + 左对齐，与官方表格的网格观感一致，多行时纵向成列。
 */
private val StockColWidth = 56.dp
private val SalesColWidth = 44.dp
private val RevenueColWidth = 72.dp

/**
 * 售卖商品卡片：封面 + 名称/价格 + 官方同款统计表格 + 底部操作栏。
 *
 * 结构对应网页端 /setting/shop 的商品块，压缩成适合手机的纵向布局：
 * - 头部：64dp 封面（占位色 disabledSecondary）+ 名称（右缘挂状态标签）+ 价格
 * - 统计表格（官方同款）：表头（型号 | 库存 | 销量 | ¥收入）→ 合计数行 → 型号行；
 *   型号行的收入列与官方一致留空；合计数的销量/收入来自接口汇总字段
 * - 操作栏：上下架 / 改库存 / 查看 / 编辑（复用 CardActionBar 均分一行）
 *
 * @param busy 该商品正在执行上下架等写操作：涉及服务端的动作置灰防重复提交
 * @param onToggleVisible 上架 / 下架（按当前状态切换）
 * @param onEditStock 打开改库存弹层
 * @param onView 打开 app 内商品详情弹层
 * @param onEdit 打开网页端编辑器（App 内暂无商品编辑器）
 */
@Composable
fun ShopProductCard(
    product: ShopProduct,
    busy: Boolean,
    onToggleVisible: () -> Unit,
    onEditStock: () -> Unit,
    onView: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        // 上下架确认弹窗：上下架直接影响用户能否购买，先让用户确认再执行
        val toggleDialogState = rememberDialogState()
        Column {
            // ── 头部：封面 + 信息 ──
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // 商品封面：加载前/失败显示统一占位底色
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.disabledSecondary),
                ) {
                    product.pic?.let { pic ->
                        AsyncImage(
                            model = pic,
                            contentDescription = "商品封面",
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // 商品名 + 状态标签（挂在标题行右缘）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.name,
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                        ShopStatusTag(product.status == SHOP_PLAN_STATUS_NORMAL)
                    }
                    Spacer(Modifier.height(4.dp))
                    // 价格 + 类型副标题（捆绑包 / 多型号）
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "¥ ${product.price}",
                            style = MiuixTheme.textStyles.headline1,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        product.subtitle?.let { subtitle ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = subtitle,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                    }
                }
            }

            // ── 统计表格（官方同款）：表头 → 合计数 → 型号行 ──
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // 表头：浅色小字
                SalesTableRow(
                    label = { Text("型号", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) },
                    stock = "库存",
                    sales = "销量",
                    revenue = "收入",
                    valueStyle = MiuixTheme.textStyles.footnote2,
                    valueColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    showDivider = true,
                )
                // 合计数行：库存汇总 / 全部累计销量 / 全部累计收入
                SalesTableRow(
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("合计数", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            // ⓘ 讲清楚合计数的口径与精度（汇总来源、剔除了哪些单据）
                            // 点按/长按都弹提示，点击本身只切换气泡，无额外动作；
                            // 用 Tiny 档而不是 Small：热区与图标同大（16.dp），这行行高不被顶高、表格节奏保持整齐
                            TooltipInfoButton(
                                tooltipText = "数据展示功能还在完善中，最准确数据请前往「收到发电」查看订单明细。\n" +
                                    "数据均已去掉退款订单、兑换码、各类优惠价和折扣等",
                                style = TooltipInfoButtonStyle.Tiny,
                                contentDescription = "合计数口径说明",
                            )
                        }
                    },
                    stock = product.stockSummary,
                    sales = product.soldTotal.toString(),
                    revenue = "¥${product.incomeTotal}",
                    valueStyle = MiuixTheme.textStyles.footnote1,
                    valueColor = MiuixTheme.colorScheme.onSurface,
                    showDivider = true,
                )
                // 型号行：型号名 + 型号价 | 型号库存 | 型号销量 | 收入列留空（与官方一致）
                product.skus.forEach { sku ->
                    SalesTableRow(
                        label = { SkuLabel(sku) },
                        stock = sku.stockLabel,
                        sales = sku.soldCount.toString(),
                        revenue = "",
                        valueStyle = MiuixTheme.textStyles.footnote1,
                        valueColor = MiuixTheme.colorScheme.onSurface,
                        // 官方最后一行型号行不带分隔线
                        showDivider = sku !== product.skus.last(),
                    )
                }
            }
            if (product.needAddress) {
                Text(
                    text = "实物商品 · 需要收货地址",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }

            // ── 操作栏 ──
            val onShelf = product.status == SHOP_PLAN_STATUS_NORMAL
            CardActionBar(
                showDivider = true,
                items = listOf(
                    // 上架 / 下架：按当前状态切换，先弹确认窗再执行，写请求期间置灰
                    CardActionBarItem(
                        icon = if (onShelf) MiuixIcons.Demibold.Hide else MiuixIcons.Demibold.Show,
                        label = if (onShelf) "下架" else "上架",
                        onClick = if (busy) null else toggleDialogState::show,
                    ),
                    // TODO: 实际逻辑有问题，先注释了，后面记得改
                    // 改库存：打开改库存弹层
//                    CardActionBarItem(
//                        icon = MiuixIcons.Demibold.Tune,
//                        label = "改库存",
//                        onClick = if (busy) null else onEditStock,
//                    ),
                    // 查看：打开 app 内商品详情弹层（复用创作者主页同款）
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.Search,
                        label = "查看",
                        onClick = onView,
                    ),
                    // 编辑走网页端：App 内暂未实现商品编辑器
                    CardActionBarItem(
                        icon = MiuixIcons.Demibold.Edit,
                        label = "编辑",
                        onClick = onEdit,
                    ),
                ),
            )

            // ── 上下架确认弹窗 ──
            AppDialog(
                state = toggleDialogState,
                title = if (onShelf) "下架商品" else "上架商品",
                summary = if (onShelf)
                    "确定要下架「${product.name}」吗？下架后商品将不再对外展示。"
                else
                    "确定要上架「${product.name}」吗？上架后商品会恢复对外展示。",
                confirmText = if (onShelf) "下架" else "上架",
                // 确认后关闭弹窗并执行上下架（AppDialog 会自动 dismiss）
                onConfirm = onToggleVisible,
            )
        }
    }
}

/**
 * 统计表格的单行：型号列（weight 占满剩余）+ 库存/销量/收入三个定宽列。
 * 行高约 36dp（vertical 8dp + 一行文字），行间浅分隔线与官方一致。
 *
 * @param label 型号列内容（表头/合计数行是浅色标签，型号行是名称+价格）
 * @param showDivider 行底是否画分隔线
 */
@Composable
private fun SalesTableRow(
    label: @Composable () -> Unit,
    stock: String,
    sales: String,
    revenue: String,
    valueStyle: TextStyle,
    valueColor: Color,
    showDivider: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) { label() }
            SalesCell(stock, StockColWidth, valueStyle, valueColor)
            SalesCell(sales, SalesColWidth, valueStyle, valueColor)
            SalesCell(revenue, RevenueColWidth, valueStyle, valueColor)
        }
        if (showDivider) HorizontalDivider()
    }
}

/** 定宽的数值单元格：左对齐（与官方一致），超长省略；数字启用 tnum 等宽特性，多行纵向对齐。 */
@Composable
private fun SalesCell(text: String, width: Dp, style: TextStyle, color: Color) {
    Text(
        text = text,
        // tnum（tabular numbers）：数字等宽，库存/销量/收入多行时各位数字严格成列
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width),
    )
}

/** 型号行的型号列内容：型号名（最多两行省略）+ 型号价（浅色小字），纵向排列。 */
@Composable
private fun SkuLabel(sku: ShopSku) {
    // 注意外层是 Box（会堆叠子元素），这里必须用 Column 垂直排，否则名称和价格重叠
    Column {
        Text(
            text = sku.name,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        sku.price?.let { price ->
            Text(
                text = "¥ $price",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 商品状态标签：上架中 = 主色胶囊，已隐藏 = 次要色胶囊。
 * 挂在标题行右缘，一眼区分在售状态。
 */
@Composable
private fun ShopStatusTag(onShelf: Boolean) {
    val container = if (onShelf) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.secondaryContainer
    val content = if (onShelf) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = if (onShelf) "上架中" else "已隐藏",
            style = MiuixTheme.textStyles.footnote2,
            color = content,
        )
    }
}
