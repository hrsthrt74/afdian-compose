package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.content.BundleStockEdit
import com.hrsthrt74.afdian.data.content.SHOP_PRODUCT_TYPE_BUNDLE
import com.hrsthrt74.afdian.data.content.SHOP_STATUS_ALL
import com.hrsthrt74.afdian.data.content.SHOP_STATUS_HIDDEN
import com.hrsthrt74.afdian.data.content.SHOP_STATUS_ON
import com.hrsthrt74.afdian.data.content.SHOP_STOCK_TYPE_INFINITE
import com.hrsthrt74.afdian.data.content.SHOP_STOCK_TYPE_LIMITED
import com.hrsthrt74.afdian.data.content.SkuStockEdit
import com.hrsthrt74.afdian.data.content.ShopPlanSkus
import com.hrsthrt74.afdian.data.content.ShopProduct
import com.hrsthrt74.afdian.data.content.ShopSku
import com.hrsthrt74.afdian.data.content.ShopService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单个筛选 tab 的列表状态（DESIGN.md 约定：加载/错误显式建模）。 */
data class ShopListUi(
    val items: List<ShopProduct> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** 改库存弹层状态；null = 弹层关闭。 */
data class ShopStockSheetUi(
    /** 发起改库存的商品（弹层标题、当前商品回显用） */
    val product: ShopProduct,
    /** SKU 明细是否加载中 */
    val loading: Boolean = false,
    /** 明细加载失败原因；非空时弹层内显示错误与重试 */
    val error: String? = null,
    /** 加载成功的 SKU 明细（null = 尚未加载成功） */
    val data: ShopPlanSkus? = null,
    /** 保存中：阻止重复提交与关闭 */
    val saving: Boolean = false,
)

/**
 * 售卖商品管理页状态。
 *
 * 三个筛选 tab（全部/上架中/已隐藏）对应同一接口的不同 status 参数，
 * 进入页面时并行加载三份，切换 tab 不重新请求；任一 tab 失败互不影响。
 */
class ShopViewModel(application: Application) : AndroidViewModel(application) {
    private val service = ShopService(AuthTokenStore(application))

    /** 当前 tab 下标：0 = 全部，1 = 上架中，2 = 已隐藏 */
    private val _tabIndex = MutableStateFlow(0)
    val tabIndex = _tabIndex.asStateFlow()

    // 三个 tab 的列表状态各自独立
    private val _allUi = MutableStateFlow(ShopListUi())
    val allUi = _allUi.asStateFlow()
    private val _onUi = MutableStateFlow(ShopListUi())
    val onUi = _onUi.asStateFlow()
    private val _hiddenUi = MutableStateFlow(ShopListUi())
    val hiddenUi = _hiddenUi.asStateFlow()

    /** 正在执行写操作（上下架等）的商品 id：按钮置灰防重复提交 */
    private val _busyPlanIds = MutableStateFlow<Set<String>>(emptySet())
    val busyPlanIds = _busyPlanIds.asStateFlow()

    /** 写操作失败的提示；非空时页面弹窗，关闭后 consumeActionError 清空 */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    /** 改库存弹层状态 */
    private val _stockSheet = MutableStateFlow<ShopStockSheetUi?>(null)
    val stockSheet = _stockSheet.asStateFlow()

    /** 排序保存中：弹层的确定按钮据此转圈并禁止关闭 */
    private val _sortSaving = MutableStateFlow(false)
    val sortSaving = _sortSaving.asStateFlow()

    init {
        // 进入页面并行加载三个 tab，切换时无需等待
        listOf(
            SHOP_STATUS_ALL to _allUi,
            SHOP_STATUS_ON to _onUi,
            SHOP_STATUS_HIDDEN to _hiddenUi,
        ).forEach { (status, flow) -> loadTab(status, flow) }
    }

    /** 切换筛选 tab（数据 init 时已全部发起加载，这里只切选中态）。 */
    fun selectTab(index: Int) {
        _tabIndex.value = index
    }

    /** 当前 tab 的列表状态（页面绑定用）。 */
    fun currentUi(index: Int): MutableStateFlow<ShopListUi> = when (index) {
        1 -> _onUi
        2 -> _hiddenUi
        else -> _allUi
    }

    /** 刷新当前 tab（下拉刷新 / 顶栏刷新按钮共用）。 */
    fun refresh() {
        val index = _tabIndex.value
        loadTab(statusFor(index), currentUi(index))
    }

    /** 当前 tab 首屏失败后的重试入口。 */
    fun retry() = refresh()

    /**
     * 上架 / 下架商品：服务端成功后本地同步三个 tab（同一商品可能出现在多个 tab），
     * 失败时列表不变，写入 [actionError] 由页面弹窗提示。
     */
    fun toggleVisible(product: ShopProduct) {
        if (product.planId in _busyPlanIds.value) return
        val visible = product.status != 1
        viewModelScope.launch {
            _busyPlanIds.value = _busyPlanIds.value + product.planId
            try {
                withContext(Dispatchers.IO) { service.setVisible(product.planId, visible) }
                // 本地把该商品在所有 tab 里的状态改掉：上架(1) ↔ 隐藏(3)
                val newStatus = if (visible) 1 else 3
                listOf(_allUi, _onUi, _hiddenUi).forEach { flow ->
                    flow.value = flow.value.copy(
                        items = flow.value.items.map {
                            if (it.planId == product.planId) it.copy(status = newStatus) else it
                        },
                    )
                }
                AppLog.d(TAG, "toggleVisible ${product.name} -> ${if (visible) "上架" else "下架"}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "toggleVisible ${product.name} failed", e)
                _actionError.value = e.message ?: "操作失败，请稍后重试"
            } finally {
                _busyPlanIds.value = _busyPlanIds.value - product.planId
            }
        }
    }

    /** 打开改库存弹层并加载 SKU 明细。 */
    fun openStockSheet(product: ShopProduct) {
        if (_stockSheet.value != null) return
        _stockSheet.value = ShopStockSheetUi(product = product, loading = true)
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { service.fetchPlanSkus(product.planId) }
                _stockSheet.value = _stockSheet.value?.copy(loading = false, data = data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "openStockSheet ${product.name} failed", e)
                _stockSheet.value = _stockSheet.value?.copy(loading = false, error = e.message ?: "库存信息加载失败")
            }
        }
    }

    /** 明细加载失败后的重试。 */
    fun retryStockSheet() {
        _stockSheet.value?.let { openStockSheet(it.product) }
    }

    /** 关闭改库存弹层。 */
    fun dismissStockSheet() {
        // 保存中不允许关闭，避免重复提交
        if (_stockSheet.value?.saving == true) return
        _stockSheet.value = null
    }

    /**
     * 保存普通商品的库存。
     *
     * @param targets 每个型号的目标库存；null = 无限。与当前库存的差异在本地
     *   换算成 incr/decr（接口语义：stock 是「当前库存」基数，增减量单独传），
     *   只提交有变化的型号。
     */
    fun saveSkuStock(targets: Map<String, String?>) {
        val sheet = _stockSheet.value ?: return
        val data = sheet.data ?: return
        if (sheet.saving) return
        val edits = buildSkuEdits(data.skus, targets)
        if (edits.isEmpty()) {
            // 没有任何变化直接关闭，不发请求
            _stockSheet.value = null
            return
        }
        viewModelScope.launch {
            _stockSheet.value = sheet.copy(saving = true)
            try {
                withContext(Dispatchers.IO) { service.saveSkuStock(edits) }
                // 本地同步库存展示，避免整页刷新
                applyStockLocally(sheet.product.planId, data, targets)
                _stockSheet.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "saveSkuStock ${sheet.product.name} failed", e)
                _actionError.value = e.message ?: "库存修改失败"
                _stockSheet.value = _stockSheet.value?.copy(saving = false)
            }
        }
    }

    /** 保存捆绑包库存：[target] null = 无限。 */
    fun saveBundleStock(target: String?) {
        val sheet = _stockSheet.value ?: return
        val data = sheet.data ?: return
        if (sheet.saving) return
        val current = data.bundleStock
        // 与当前值一致（含同为无限）时直接关闭
        if (labelNormalized(current) == labelNormalized(target)) {
            _stockSheet.value = null
            return
        }
        val edit = if (target == null) {
            BundleStockEdit(data.planId, SHOP_STOCK_TYPE_INFINITE, "", "", "")
        } else if (current.isNullOrBlank()) {
            // 无限 → 限量：stock 直接传目标值，增减量留空（与网页端一致）
            BundleStockEdit(data.planId, SHOP_STOCK_TYPE_LIMITED, target, "", "")
        } else {
            // 限量 → 限量：stock 传原值，差值走 incr/decr
            val delta = (target.toIntOrNull() ?: 0) - (current.toIntOrNull() ?: 0)
            BundleStockEdit(
                planId = data.planId,
                stockType = SHOP_STOCK_TYPE_LIMITED,
                stock = current,
                stockIncr = if (delta > 0) delta.toString() else "",
                stockDecr = if (delta < 0) (-delta).toString() else "",
            )
        }
        viewModelScope.launch {
            _stockSheet.value = sheet.copy(saving = true)
            try {
                withContext(Dispatchers.IO) { service.saveBundleStock(edit) }
                applyStockLocally(sheet.product.planId, data, mapOf(BUNDLE_TARGET_KEY to target))
                _stockSheet.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "saveBundleStock ${sheet.product.name} failed", e)
                _actionError.value = e.message ?: "库存修改失败"
                _stockSheet.value = _stockSheet.value?.copy(saving = false)
            }
        }
    }

    /** 操作失败的提示弹窗关闭后清空。 */
    fun consumeActionError() {
        _actionError.value = null
    }

    // ===== 排序 =====

    /**
     * 保存商品排序：把弹层里排好的全量顺序作为 plan_ids 发给服务端，成功后刷新三个 tab。
     *
     * 排序草稿由弹层自己持有（拖拽只改本地顺序），本方法只负责「提交 + 收尾」：
     * 成功回调 [onSuccess] 给页面用来关闭弹层；失败写入 [actionError] 走统一错误弹窗，
     * 弹层保持打开，用户可以再点一次确定重试。
     *
     * @param orderedItems 排好序的全量商品（服务端按全量顺序保存）
     * @param onSuccess 保存成功回调
     */
    fun saveRank(orderedItems: List<ShopProduct>, onSuccess: () -> Unit) {
        if (_sortSaving.value) return
        viewModelScope.launch {
            _sortSaving.value = true
            try {
                withContext(Dispatchers.IO) { service.saveRank(orderedItems.map { it.planId }) }
                AppLog.d(TAG, "saveRank ok: ${orderedItems.size} items")
                // 顺序已变，三个 tab 都重新拉取
                listOf(
                    SHOP_STATUS_ALL to _allUi,
                    SHOP_STATUS_ON to _onUi,
                    SHOP_STATUS_HIDDEN to _hiddenUi,
                ).forEach { (status, flow) -> loadTab(status, flow) }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "saveRank failed", e)
                _actionError.value = e.message ?: "排序保存失败"
            } finally {
                _sortSaving.value = false
            }
        }
    }

    // ===== 内部实现 =====

    /** 拉取单个 tab 的整页列表。 */
    private fun loadTab(status: String, flow: MutableStateFlow<ShopListUi>) {
        if (flow.value.loading) return
        viewModelScope.launch {
            flow.value = flow.value.copy(loading = true, error = null)
            try {
                val items = withContext(Dispatchers.IO) { service.fetchProducts(status) }
                flow.value = flow.value.copy(items = items, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "loadTab status=$status failed", e)
                flow.value = flow.value.copy(loading = false, error = e.message ?: "商品列表加载失败")
            }
        }
    }

    private fun statusFor(index: Int): String = when (index) {
        1 -> SHOP_STATUS_ON
        2 -> SHOP_STATUS_HIDDEN
        else -> SHOP_STATUS_ALL
    }

    /**
     * 把「目标库存」换算成接口的增量语义，只保留有变化的型号。
     * 规则与网页端改库存弹层一致：
     * - 目标无限 → stock_type=2，其余留空
     * - 当前无限 → stock 直接传目标值
     * - 限量 → 限量 → stock 传原库存，差值走 incr/decr（增传 incr、减传 decr）
     */
    internal fun buildSkuEdits(skus: List<ShopSku>, targets: Map<String, String?>): List<SkuStockEdit> =
        skus.mapNotNull { sku ->
            val target = targets[sku.skuId] ?: return@mapNotNull null
            val current = sku.stock
            when {
                target.isNullOrBlank() && current.isNullOrBlank() -> null
                target.isNullOrBlank() -> SkuStockEdit(sku.skuId, SHOP_STOCK_TYPE_INFINITE, "", "", "")
                current.isNullOrBlank() ->
                    SkuStockEdit(sku.skuId, SHOP_STOCK_TYPE_LIMITED, target, "", "")
                else -> {
                    val delta = (target.toIntOrNull() ?: 0) - (current.toIntOrNull() ?: 0)
                    if (delta == 0) null
                    else SkuStockEdit(
                        skuId = sku.skuId,
                        stockType = SHOP_STOCK_TYPE_LIMITED,
                        stock = current,
                        stockIncr = if (delta > 0) delta.toString() else "",
                        stockDecr = if (delta < 0) (-delta).toString() else "",
                    )
                }
            }
        }

    /** 保存成功后本地同步库存展示（三个 tab 共用同一份商品对象，按 planId 替换）。 */
    private fun applyStockLocally(planId: String, data: ShopPlanSkus, targets: Map<String, String?>) {
        // 接口是「基数 + 增减」语义，服务端算完的结果就是传入的目标值，
        // 本地展示直接写目标值即可（无限在展示层用空串表示）
        fun newStock(target: String?): String? =
            if (target.isNullOrBlank()) "" else target
        val stockFor: (ShopSku) -> String? = { sku ->
            targets[sku.skuId]?.let(::newStock) ?: sku.stock
        }
        listOf(_allUi, _onUi, _hiddenUi).forEach { flow ->
            flow.value = flow.value.copy(
                items = flow.value.items.map { product ->
                    if (product.planId != planId) product
                    else if (product.productType == SHOP_PRODUCT_TYPE_BUNDLE) {
                        // 捆绑包：库存展示读 bundleStock
                        val target = targets[BUNDLE_TARGET_KEY]
                        product.copy(bundleStock = target?.let(::newStock) ?: product.bundleStock)
                    } else {
                        // 普通商品：按型号逐个更新
                        product.copy(skus = product.skus.map { it.copy(stock = stockFor(it)) })
                    }
                },
            )
        }
    }

    /** 「无限」与空值在本地展示层统一为空串。 */
    private fun labelNormalized(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "Shop"

        /** 捆绑包目标库存放在 targets map 里的约定 key。 */
        const val BUNDLE_TARGET_KEY = "__bundle__"
    }
}
