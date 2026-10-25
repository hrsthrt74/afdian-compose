package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 「售卖商品管理」领域服务（创作者后台 /setting/shop）。
 *
 * 接口语义（2026-10 抓包 + 小白鼠商品实测，与网页端一致）：
 * - 商品列表 = GET /api/creator/all-plans 的 data.sale_list；status 筛选：
 *   "" = 全部、1 = 上架中、3 = 已隐藏（2 是已删除，网页端不提供筛选）
 * - 上下架 = POST hide-plan / show-plan，请求体 {plan_id}（JSON）
 * - 改库存 = POST edit-sku-stock（普通商品，{skus:[…]}）/ edit-bundle-stock（捆绑包）；
 *   stock_type: 1 = 限量、2 = 无限；限量时 stock 是「当前库存」，配合 incr/decr 增减；
 *   从无限改限量时 stock 直接传目标值、incr/decr 留空
 * - 排序 = POST edit-plan-rank，{plan_ids: "id1,id2,…"}（全量提交，不是单项移动）
 * - 写接口必须用 application/json 请求体：表单编码会得到 ec=200 的假成功、数据实际未变
 */
class ShopService(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient = sharedAfdianHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val http = AfdianHttpClient(client)

    private companion object {
        const val HOST = "https://ifdian.net"
        const val TAG = "ShopService"
        // 与网页端一致的 Referer：部分后台接口会校验来源页
        const val SETTING_REFERER = "$HOST/setting/shop"
    }

    /** 拉取售卖商品列表；[status] 传 [SHOP_STATUS_ALL] / [SHOP_STATUS_ON] / [SHOP_STATUS_HIDDEN]。 */
    suspend fun fetchProducts(status: String = SHOP_STATUS_ALL): List<ShopProduct> = withContext(Dispatchers.IO) {
        val path = "/api/creator/all-plans"
        val url = (HOST + path).toHttpUrl().newBuilder()
            // post_only/sale_only 照抄网页端：留空表示同时包含赞助方案与商品，
            // 服务端按 status 过滤后 sale_list 只返回售卖商品
            .addQueryParameter("post_only", "")
            .addQueryParameter("sale_only", "")
            .addQueryParameter("status", status)
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", SETTING_REFERER)
            .build()
        val start = System.currentTimeMillis()
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("商品列表加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, "商品列表加载失败", requireEc = true)
            val products = parseShopProducts(root).also {
                AppLog.d(TAG, "$path status=$status -> ${it.size} 件商品（${System.currentTimeMillis() - start}ms）")
            }
            products
        }
    }

    /** 上架 / 下架（隐藏）商品，返回服务端更新后的商品名（用于日志与提示）。 */
    suspend fun setVisible(planId: String, visible: Boolean): String = withContext(Dispatchers.IO) {
        // show-plan / hide-plan 响应的 data.plan 里带更新后的 status，这里只需要提示语
        postJson(
            path = if (visible) "/api/creator/show-plan" else "/api/creator/hide-plan",
            body = buildJsonObject { put("plan_id", planId) },
            logPath = "${if (visible) "show" else "hide"}-plan $planId",
            fallbackMessage = if (visible) "上架失败" else "下架失败",
        )
    }

    /**
     * 拉取单个商品的 SKU 明细（含实时库存），供改库存弹层使用。
     * 捆绑包的库存读 data.plan.bundle_stock；普通商品读 data.list[].stock。
     */
    suspend fun fetchPlanSkus(planId: String): ShopPlanSkus = withContext(Dispatchers.IO) {
        val path = "/api/creator/get-plan-skus"
        val url = (HOST + path).toHttpUrl().newBuilder()
            // is_ext=1 与网页端改库存弹层一致：返回带库存与限购信息的完整结构
            .addQueryParameter("plan_id", planId)
            .addQueryParameter("is_ext", "1")
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder().url(url).get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", SETTING_REFERER)
            .build()
        val start = System.currentTimeMillis()
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$path -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("库存信息加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, "库存信息加载失败", requireEc = true)
            parseShopPlanSkus(root).also {
                AppLog.d(TAG, "$path -> ${it.skus.size} 个型号（${System.currentTimeMillis() - start}ms）")
            }
        }
    }

    /** 保存普通商品的 SKU 库存（一次提交整单所有改动的型号）。 */
    suspend fun saveSkuStock(edits: List<SkuStockEdit>) = withContext(Dispatchers.IO) {
        // skus 是数组字段：逐项手工构建 JSON 数组（模型保持纯数据类，不加序列化注解）
        val body = buildJsonObject {
            put(
                "skus",
                kotlinx.serialization.json.buildJsonArray {
                    edits.forEach { edit ->
                        add(
                            buildJsonObject {
                                put("sku_id", edit.skuId)
                                put("stock_type", edit.stockType)
                                put("stock", edit.stock)
                                put("stock_incr", edit.stockIncr)
                                put("stock_decr", edit.stockDecr)
                            },
                        )
                    }
                },
            )
        }
        postJsonUnit(
            path = "/api/creator/edit-sku-stock",
            body = body,
            logPath = "edit-sku-stock ${edits.size} 项",
            fallbackMessage = "库存修改失败",
        )
    }

    /** 保存捆绑包库存。 */
    suspend fun saveBundleStock(edit: BundleStockEdit) = withContext(Dispatchers.IO) {
        postJsonUnit(
            path = "/api/creator/edit-bundle-stock",
            body = buildJsonObject {
                put("plan_id", edit.planId)
                put("stock", edit.stock)
                put("stock_type", edit.stockType)
                put("stock_incr", edit.stockIncr)
                put("stock_decr", edit.stockDecr)
            },
            logPath = "edit-bundle-stock ${edit.planId}",
            fallbackMessage = "库存修改失败",
        )
    }

    /** 保存商品排序：全量提交当前顺序的 plan_id 列表。 */
    suspend fun saveRank(planIds: List<String>) = withContext(Dispatchers.IO) {
        postJsonUnit(
            path = "/api/creator/edit-plan-rank",
            body = buildJsonObject { put("plan_ids", planIds.joinToString(",")) },
            logPath = "edit-plan-rank ${planIds.size} 项",
            fallbackMessage = "排序保存失败",
        )
    }

    /** POST JSON 请求体的公共实现，返回服务端提示语 em。 */
    private fun postJson(path: String, body: JsonObject, logPath: String, fallbackMessage: String): String {
        val request = Request.Builder()
            .url(HOST + path)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", SETTING_REFERER)
            .build()
        val start = System.currentTimeMillis()
        // 写请求只在「连接没建立」时才换线路（见 AfdianHttpClient），不会因超时重复提交
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$logPath -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("$fallbackMessage (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, fallbackMessage, requireEc = true)
            val message = root["em"]?.jsonPrimitive?.contentOrNull.orEmpty()
            AppLog.d(TAG, "$logPath -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），$message")
            return message
        }
    }

    /** postJson 的 Unit 返回版，语义更清晰。 */
    private fun postJsonUnit(path: String, body: JsonObject, logPath: String, fallbackMessage: String) {
        postJson(path, body, logPath, fallbackMessage)
    }
}

// ===== 伴随常量与模型 =====

/** all-plans 的 status 筛选参数取值。 */
const val SHOP_STATUS_ALL = ""
const val SHOP_STATUS_ON = "1"
const val SHOP_STATUS_HIDDEN = "3"

/** 商品状态值：与接口 plan_status 字典一致（normal=1 / delete=2 / hide=3）。 */
const val SHOP_PLAN_STATUS_NORMAL = 1
const val SHOP_PLAN_STATUS_HIDDEN = 3

/** 库存类型：与 edit-*-stock 接口的 stock_type 字段一致。 */
const val SHOP_STOCK_TYPE_LIMITED = 1
const val SHOP_STOCK_TYPE_INFINITE = 2

/** 商品类型：1 = 普通商品，2 = 捆绑包。 */
const val SHOP_PRODUCT_TYPE_NORMAL = 1
const val SHOP_PRODUCT_TYPE_BUNDLE = 2

/**
 * 售卖商品列表条目（来自 all-plans 的 sale_list，只保留列表展示需要的字段）。
 *
 * @param planId 商品 id（item 页与编辑页链接都用它）
 * @param name 商品名
 * @param pic 封面图；为空时可退回 [ShopSku.pic]
 * @param price 售价（字符串金额，如 "7.99"）
 * @param status 上架状态：[SHOP_PLAN_STATUS_NORMAL] / [SHOP_PLAN_STATUS_HIDDEN]
 * @param productType 商品类型：[SHOP_PRODUCT_TYPE_NORMAL] / [SHOP_PRODUCT_TYPE_BUNDLE]
 * @param bundleStock 捆绑包库存原值（空串 = 无限）；非捆绑包无意义
 * @param soldTotal 全部累计销量（接口 plan.sponsor_count）
 * @param incomeTotal 全部累计收入（接口 plan.total_amount，字符串金额）
 * @param needAddress 是否需要收货地址（实物商品）
 * @param skus 型号（SKU）摘要列表；捆绑包的 sku 是包内子商品
 */
data class ShopProduct(
    val planId: String,
    val name: String,
    val pic: String?,
    val price: String,
    val status: Int,
    val productType: Int,
    val bundleStock: String?,
    val soldTotal: Int,
    val incomeTotal: String,
    val needAddress: Boolean,
    val skus: List<ShopSku>,
) {
    val isBundle: Boolean get() = productType == SHOP_PRODUCT_TYPE_BUNDLE

    /**
     * 库存汇总文案，规则与网页端卡片一致：
     * - 捆绑包读 bundle_stock
     * - 单型号读该型号库存
     * - 多型号无法用一个数字概括，显示 "-"
     * 空库存值表示「无限」。
     */
    val stockSummary: String
        get() = when {
            isBundle -> stockLabel(bundleStock)
            skus.size == 1 -> stockLabel(skus[0].stock)
            else -> "-"
        }

    /** 卡片副标题：只有捆绑包显示（与官方一致）；多型号商品的型号数由统计表格表达 */
    val subtitle: String?
        get() = if (isBundle) "捆绑包 · 包含 ${skus.size} 件商品" else null
}

/** 商品型号（SKU）摘要。@param stock 库存原值（空串 = 无限）@param pic 型号封面，可为空 */
data class ShopSku(
    val skuId: String,
    val name: String,
    val price: String?,
    val stock: String?,
    val soldCount: Int,
    val pic: String? = null,
) {
    /** 「无限」/数字 的展示文案。 */
    val stockLabel: String get() = stockLabel(stock)
}

/** 库存展示文案：空值 = 无限，其余原样展示。 */
internal fun stockLabel(raw: String?): String =
    raw?.takeIf { it.isNotBlank() } ?: "无限"

/** 改库存弹层的单个型号编辑项。 */
data class SkuStockEdit(
    val skuId: String,
    val stockType: Int,
    /** 当前库存基数：限量时为原库存，无限 → 限量的瞬时传目标值 */
    val stock: String,
    val stockIncr: String,
    val stockDecr: String,
)

/** 捆绑包库存编辑项。 */
data class BundleStockEdit(
    val planId: String,
    val stockType: Int,
    val stock: String,
    val stockIncr: String,
    val stockDecr: String,
)

/** 改库存弹层的数据：方案信息 + 型号列表。 */
data class ShopPlanSkus(
    val planId: String,
    val name: String,
    val isBundle: Boolean,
    /** 捆绑包库存原值（空串 = 无限）；普通商品为 null */
    val bundleStock: String?,
    val skus: List<ShopSku>,
)

// ===== 解析（顶层 internal，可单测） =====

/** 从 all-plans 响应解析售卖商品列表；单条脏数据跳过而不是让整页失败。 */
internal fun parseShopProducts(root: JsonObject): List<ShopProduct> {
    val data = root["data"] as? JsonObject ?: return emptyList()
    val list = data["sale_list"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return list.mapNotNull { element ->
        (element as? JsonObject)?.toShopProduct()
    }
}

private fun JsonObject.toShopProduct(): ShopProduct? {
    // plan_id 是列表稳定 key，缺失时无法定位商品，直接丢弃
    val planId = string("plan_id")?.takeIf { it.isNotBlank() } ?: return null
    val skus = (this["sku"] as? kotlinx.serialization.json.JsonArray)
        .orEmpty()
        .mapNotNull { (it as? JsonObject)?.toShopSku() }
    return ShopProduct(
        planId = planId,
        name = string("name")?.takeIf { it.isNotBlank() } ?: "未命名商品",
        // 封面：商品图缺失时退回首个型号的图（网页端同款回退规则）
        pic = string("pic")?.takeIf { it.isNotBlank() }
            ?: skus.firstNotNullOfOrNull { it.pic?.takeIf(String::isNotBlank) },
        price = string("show_price")?.takeIf { it.isNotBlank() } ?: string("price") ?: "0",
        status = int("status") ?: SHOP_PLAN_STATUS_NORMAL,
        productType = int("product_type") ?: SHOP_PRODUCT_TYPE_NORMAL,
        // bundle_stock 可能是空串（无限）、数字（限量）或缺失（非捆绑包），统一转字符串
        bundleStock = primitiveContent("bundle_stock"),
        soldTotal = int("sponsor_count") ?: 0,
        incomeTotal = string("total_amount") ?: "0.00",
        needAddress = int("need_address") == 1,
        skus = skus,
    )
}

private fun JsonObject.toShopSku(): ShopSku? {
    val skuId = string("sku_id")?.takeIf { it.isNotBlank() } ?: return null
    return ShopSku(
        skuId = skuId,
        name = string("name")?.takeIf { it.isNotBlank() } ?: "默认型号",
        price = string("price"),
        stock = primitiveContent("stock"),
        // sponsor_count 数字/字符串混用，统一走字符串再转 int
        soldCount = int("sponsor_count") ?: 0,
        pic = string("pic")?.takeIf { it.isNotBlank() },
    )
}

/** 从 get-plan-skus 响应解析改库存弹层所需的数据。 */
internal fun parseShopPlanSkus(root: JsonObject): ShopPlanSkus {
    val data = root["data"] as? JsonObject ?: return ShopPlanSkus("", "", false, null, emptyList())
    val plan = data["plan"] as? JsonObject
    val skus = (data["list"] as? kotlinx.serialization.json.JsonArray)
        .orEmpty()
        .mapNotNull { (it as? JsonObject)?.toShopSku() }
    val productType = plan?.int("product_type") ?: SHOP_PRODUCT_TYPE_NORMAL
    return ShopPlanSkus(
        planId = plan?.string("plan_id").orEmpty(),
        name = plan?.string("name").orEmpty(),
        isBundle = productType == SHOP_PRODUCT_TYPE_BUNDLE,
        bundleStock = plan?.primitiveContent("bundle_stock"),
        skus = skus,
    )
}

// ===== JSON 安全取值 helper：接口字段数字/字符串混用，统一先判型再取 =====

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

/** 取原始内容：数字、字符串统一转成字符串（空串原样返回，调用方自行判定「无限」）。 */
private fun JsonObject.primitiveContent(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
