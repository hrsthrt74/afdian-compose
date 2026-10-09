package com.hrsthrt74.afdian.ui.screens.main.management

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.SHOP_PLAN_STATUS_NORMAL
import com.hrsthrt74.afdian.data.content.SHOP_PRODUCT_TYPE_BUNDLE
import com.hrsthrt74.afdian.data.content.SHOP_PRODUCT_TYPE_NORMAL
import com.hrsthrt74.afdian.data.content.ShopPlanSkus
import com.hrsthrt74.afdian.data.content.ShopProduct
import com.hrsthrt74.afdian.data.content.ShopSku
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.util.AfdianCardDefaults
import com.hrsthrt74.afdian.ui.viewmodel.ShopStockSheetUi
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 改库存底部弹层。
 *
 * 交互与网页端弹层一致但做了简化：不提供「增 / 减」两个输入框，改为直接输入
 * 目标库存；ViewModel 里再按接口语义换算成「基数 + 增减量」提交。
 * - 普通商品：每个型号一行，「无限」开关关闭后可输入目标库存
 * - 捆绑包：整单一行，读 bundle_stock
 *
 * @param state 弹层数据（null = 关闭）；product 提供标题回显，data 为 SKU 明细
 * @param onDismiss 关闭（保存中被 ViewModel 忽略）
 * @param onRetry 明细加载失败重试
 * @param onSaveSkus 保存普通商品库存：skuId → 目标库存（null = 无限）
 * @param onSaveBundle 保存捆绑包库存：目标库存（null = 无限）
 */
@Composable
fun ShopStockSheet(
    state: ShopStockSheetUi?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSaveSkus: (Map<String, String?>) -> Unit,
    onSaveBundle: (String?) -> Unit,
) {
    WindowBottomSheet(
        show = state != null,
        title = "改库存",
        onDismissRequest = onDismiss,
    ) {
        // WindowBottomSheet 的内容 lambda 在关闭动画期间仍会执行，
        // state 为 null 时直接返回（对应「已关闭」的退场帧）
        val sheet = state ?: return@WindowBottomSheet
        ShopStockSheetContent(
            sheet = sheet,
            onRetry = onRetry,
            onSaveSkus = onSaveSkus,
            onSaveBundle = onSaveBundle,
        )
    }
}

/**
 * 弹层内容本体：商品名回显 + 三态（加载中 / 失败 / 编辑区）。
 * 单独抽出是为了让 @Preview 可以直接渲染——WindowBottomSheet 是 Dialog 独立窗口，
 * 预览环境渲染不出窗口内容，只能预览内容本体。
 */
@Composable
private fun ShopStockSheetContent(
    sheet: ShopStockSheetUi,
    onRetry: () -> Unit,
    onSaveSkus: (Map<String, String?>) -> Unit,
    onSaveBundle: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 商品名回显：确认正在改哪个商品
        SmallTitle(
            sheet.product.name,
            insideMargin = PaddingValues(16.dp, 0.dp, 8.dp, 8.dp)
        )
        when {
            // SKU 明细加载中
            sheet.loading -> Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) { LoadingComponent() }

            // 明细加载失败：统一错误态视图（图标 + 标题 + 原因 + 重试按钮）
            sheet.error != null -> ErrorStateView(
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                summary = sheet.error,
            )

            // 明细就绪：按商品类型渲染编辑行
            sheet.data != null -> StockEditor(
                isBundle = sheet.data.isBundle,
                bundleStock = sheet.data.bundleStock,
                skus = sheet.data.skus,
                saving = sheet.saving,
                onSaveSkus = onSaveSkus,
                onSaveBundle = onSaveBundle,
            )
        }
    }
}

/**
 * 库存编辑区：普通商品逐型号一行，捆绑包整单一行。
 * 输入状态用 mutableStateMap 保存，skuId → 目标库存文本；「无限」开关单独记一份。
 */
@Composable
private fun StockEditor(
    isBundle: Boolean,
    bundleStock: String?,
    skus: List<ShopSku>,
    saving: Boolean,
    onSaveSkus: (Map<String, String?>) -> Unit,
    onSaveBundle: (String?) -> Unit,
) {
    // ── 本表单的临时输入状态（关闭弹层即丢弃，业务状态在 ViewModel） ──
    // 每行的「无限」开关：初始值取当前库存是否为空（空 = 无限）
    val infiniteMap = remember(isBundle, skus, bundleStock) {
        mutableStateMapOf<String, Boolean>().apply {
            if (isBundle) {
                put(BUNDLE_ROW_KEY, bundleStock.isNullOrBlank())
            } else {
                skus.forEach { put(it.skuId, it.stock.isNullOrBlank()) }
            }
        }
    }
    // 每行的目标库存文本：初始值取当前库存（无限行初始为空，输入后视为「设为限量」）
    val textMap = remember(isBundle, skus, bundleStock) {
        mutableStateMapOf<String, String>().apply {
            if (isBundle) {
                put(BUNDLE_ROW_KEY, bundleStock.orEmpty())
            } else {
                skus.forEach { put(it.skuId, it.stock.orEmpty()) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 「限量但没填数字」的行数：存在时不允许保存，避免空值被当成「无限」误提交
        val invalidRows = textMap.count { (key, text) ->
            infiniteMap[key] != true && text.isBlank()
        }
        if (isBundle) {
            // ── 捆绑包：整单一行 ──
            StockRow(
                title = "捆绑包库存",
                subtitle = "包内 ${skus.size} 件商品共享此库存",
                infinite = infiniteMap[BUNDLE_ROW_KEY] == true,
                text = textMap[BUNDLE_ROW_KEY].orEmpty(),
                enabled = !saving,
                onInfiniteChange = { infiniteMap[BUNDLE_ROW_KEY] = it },
                onTextChange = { textMap[BUNDLE_ROW_KEY] = it.filter(Char::isDigit) },
            )
            Button(
                onClick = {
                    // null = 无限；有限时把文本原样交给 ViewModel 换算增量
                    val target = if (infiniteMap[BUNDLE_ROW_KEY] == true) null else textMap[BUNDLE_ROW_KEY]
                    onSaveBundle(target)
                },
                enabled = !saving && invalidRows == 0,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 48.dp),
            ) {
                Text(if (saving) "保存中…" else "保存")
            }
        } else {
            // ── 普通商品：每个型号一行 ──
            skus.forEach { sku ->
                StockRow(
                    title = sku.name,
                    subtitle = sku.price?.let { "¥ $it" },
                    infinite = infiniteMap[sku.skuId] == true,
                    text = textMap[sku.skuId].orEmpty(),
                    enabled = !saving,
                    onInfiniteChange = { infiniteMap[sku.skuId] = it },
                    onTextChange = { textMap[sku.skuId] = it.filter(Char::isDigit) },
                )
            }
            Button(
                onClick = {
                    // 组装 skuId → 目标库存（无限为 null）交给 ViewModel 做差量提交
                    val targets = skus.associate { sku ->
                        sku.skuId to if (infiniteMap[sku.skuId] == true) null else textMap[sku.skuId]
                    }
                    onSaveSkus(targets)
                },
                enabled = !saving && invalidRows == 0,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 48.dp),
            ) {
                Text(if (saving) "保存中…" else "保存")
            }
        }
    }
}

/**
 * 单行库存编辑：miuix SwitchPreference（型号名 + 摘要 + 「无限」开关），
 * 「无限」关闭时通过 bottomAction 位置动画展开目标库存输入框。
 */
@Composable
private fun StockRow(
    title: String,
    subtitle: String?,
    infinite: Boolean,
    text: String,
    enabled: Boolean,
    onInfiniteChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
) {
    Card(
        colors = AfdianCardDefaults.overlayColors(),
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Column{
            SwitchPreference(
                checked = infinite,
                onCheckedChange = onInfiniteChange,
                title = title,
                summary = subtitle,
                enabled = enabled,
                // 「无限」字样：渲染在开关前面（miuix endActions 位于 Switch 之前）
                endActions = {
                    Text(
                        text = "无限",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
            )
            // 目标库存输入框：仅「无限」关闭时显示，纵向展开 + 淡入淡出动画
            AnimatedVisibility(
                visible = !infinite,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = "目标库存",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(
                        MiuixTheme.colorScheme.surfaceContainerHighest,
                        MiuixTheme.colorScheme.onSurfaceContainerHighest
                    ),
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 8.dp),
                )
            }
        }
    }
}

/** 捆绑包单行在输入状态 map 里的约定 key。 */
private const val BUNDLE_ROW_KEY = "__bundle__"

// ===== 预览 =====
// 注意：WindowBottomSheet 是 Dialog 独立窗口，@Preview 渲染不出来，
// 所以这里预览的是内容本体（ShopStockSheetContent），包一层主题背景色模拟弹层底。

/** 构造一个最小可用的预览商品：name 回显用，其余字段填安全默认值。 */
private fun previewProduct(name: String, productType: Int, bundleStock: String? = null) =
    ShopProduct(
        planId = "preview-plan",
        name = name,
        pic = null,
        price = "68.00",
        status = SHOP_PLAN_STATUS_NORMAL,
        productType = productType,
        bundleStock = bundleStock,
        soldTotal = 12,
        incomeTotal = "816.00",
        needAddress = false,
        skus = emptyList(),
    )

/** 预览用的弹层状态：明细就绪的普通商品（多型号） */
private fun previewSkuSheet() = ShopStockSheetUi(
    product = previewProduct("亚克力立牌 · 预售", SHOP_PRODUCT_TYPE_NORMAL),
    data = ShopPlanSkus(
        planId = "preview-plan",
        name = "亚克力立牌 · 预售",
        isBundle = false,
        bundleStock = null,
        skus = listOf(
            ShopSku(skuId = "sku-1", name = "单人款", price = "68.00", stock = "120", soldCount = 10),
            ShopSku(skuId = "sku-2", name = "双人款", price = "118.00", stock = "", soldCount = 2),
        ),
    ),
)

/** 预览：普通商品（多型号），第二个型号为「无限」——输入框收起 */
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360)
@Composable
private fun ShopStockSheetSkusPreview() {
    Box(Modifier.background(MiuixTheme.colorScheme.surface).padding(16.dp)) {
        ShopStockSheetContent(
            sheet = previewSkuSheet(),
            onRetry = {},
            onSaveSkus = {},
            onSaveBundle = {},
        )
    }
}

/** 预览：捆绑包（整单一行，限量 50） */
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360)
@Composable
private fun ShopStockSheetBundlePreview() {
    Box(Modifier.background(MiuixTheme.colorScheme.surface).padding(16.dp)) {
        ShopStockSheetContent(
            sheet = ShopStockSheetUi(
                product = previewProduct("徽章套装", SHOP_PRODUCT_TYPE_BUNDLE, bundleStock = "50"),
                data = ShopPlanSkus(
                    planId = "preview-plan",
                    name = "徽章套装",
                    isBundle = true,
                    bundleStock = "50",
                    skus = listOf(
                        ShopSku(skuId = "sku-b1", name = "内含商品 A", price = null, stock = null, soldCount = 0),
                        ShopSku(skuId = "sku-b2", name = "内含商品 B", price = null, stock = null, soldCount = 0),
                    ),
                ),
            ),
            onRetry = {},
            onSaveSkus = {},
            onSaveBundle = {},
        )
    }
}

/** 预览：明细加载失败（错误原因 + 重试按钮） */
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360)
@Composable
private fun ShopStockSheetErrorPreview() {
    Box(Modifier.background(MiuixTheme.colorScheme.surface).padding(16.dp)) {
        ShopStockSheetContent(
            sheet = ShopStockSheetUi(
                product = previewProduct("亚克力立牌 · 预售", SHOP_PRODUCT_TYPE_NORMAL),
                error = "加载明细失败，请稍后重试",
            ),
            onRetry = {},
            onSaveSkus = {},
            onSaveBundle = {},
        )
    }
}
