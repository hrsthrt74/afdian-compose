package com.hrsthrt74.afdian.data.content

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 管理领域服务。
 * 原「发电者管理」占位分区已删除（该功能独立成赞助者管理页），
 * 本服务现在负责赞助者管理列表（who-sponsored-me）的取数与解析编排。
 */
class ManagementService(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient = sharedAfdianHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = AfdianHttpClient(client)

    /**
     * 拉取赞助者管理列表（POST /api/my/who-sponsored-me，创作者后台，Cookie 鉴权）。
     *
     * 请求体结构抓自网页端 ifdian.net/dashboard/sponsors：
     * 排序 sort_field/sort_value 见 [SponsorManageSort]；
     * 筛选 filter 为条件对象数组（AND 关系），行结构与网页条件构建器一致，
     * 构建规则见 [buildSponsorFilterArray]。
     *
     * @param page 页码，从 1 开始
     * @param perPage 每页条数，移动端默认 [SPONSOR_PER_PAGE]（网页端为 10）
     * @param sort 排序方式；null 时 sort_field/sort_value 传空串（接口默认顺序）
     * @param filters 筛选条件行；不完整行自动跳过，空列表 = 不筛选
     */
    fun fetchSponsors(
        page: Int,
        perPage: Int = SPONSOR_PER_PAGE,
        sort: SponsorManageSort? = null,
        filters: List<SponsorFilterRow> = emptyList(),
    ): SponsorManagePage {
        val path = "/api/my/who-sponsored-me"
        val filterArray = buildSponsorFilterArray(filters)
        val bodyJson = buildJsonObject {
            put("page", page)
            put("sort_field", sort?.field ?: "")
            put("sort_value", sort?.direction ?: "")
            put("filter", filterArray)
            put("per_page", perPage)
        }
        val body = json.encodeToString(JsonObject.serializer(), bodyJson)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://afdian.com$path")
            .post(body)
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", "https://afdian.com/")
            .build()
        // 请求前仅记录页码/排序/筛选条数，不输出 auth_token 等凭据信息
        AppLog.d(
            TAG,
            "开始请求赞助者列表 page=$page perPage=$perPage " +
                "sort=${sort?.title ?: "默认"} filters=${filterArray.size} 条",
        )
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.w(TAG, "赞助者列表返回非 2xx $path code=${response.code}")
                error("赞助者列表加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, "赞助者列表加载失败", requireEc = true)
            val result = parseSponsorManagePage(root)
            AppLog.d(
                TAG,
                "赞助者列表响应 page=$page code=${response.code} " +
                    "cost=${System.currentTimeMillis() - start}ms " +
                    "count=${result.list.size} total=${result.totalCount}",
            )
            result
        }
    }

    /**
     * 拉取「筛选档位」popup 的方案选项（GET /api/creator/all-plans，创作者后台，Cookie 鉴权）。
     *
     * 与网页筛选面板同源：合并 list（订阅/独立方案）+ sale_list（售卖/捆绑）去重，
     * 排除 0 元方案；desc 巨型 HTML 不映射。
     */
    fun fetchFilterPlans(): List<SponsorFilterPlan> {
        val path = "/api/creator/all-plans?post_only=&sale_only=&status="
        val request = Request.Builder()
            .url("https://afdian.com$path")
            .get()
            .header("Cookie", "auth_token=${tokenStore.token}")
            .header("referer", "https://afdian.com/")
            .build()
        AppLog.d(TAG, "开始请求筛选档位方案列表")
        val start = System.currentTimeMillis()
        return http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.w(TAG, "筛选档位方案列表返回非 2xx $path code=${response.code}")
                error("方案列表加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, path, "方案列表加载失败", requireEc = true)
            val plans = parseFilterPlans(root)
            AppLog.d(
                TAG,
                "筛选档位方案响应 code=${response.code} " +
                    "cost=${System.currentTimeMillis() - start}ms count=${plans.size}",
            )
            plans
        }
    }

    companion object {
        private const val TAG = "ManagementService"

        /** 移动端列表每页条数（网页端为 10，手机上 20 更合适）；total_page 缺失时也用它做满页启发 */
        const val SPONSOR_PER_PAGE = 20
    }
}

/** 管理页分区；赞助者管理已独立成页面，这里只剩整体概况。 */
enum class ManagementSection(val title: String) {
    Overview("整体概况"),
}

data class ManagementResult(val section: ManagementSection, val rows: List<Pair<String, String>>)

/**
 * 解析 who-sponsored-me 响应根节点 → 赞助者管理分页。
 * 顶层 internal 便于单测（参考 MessageParser 的拆分方式）；
 * 计划的 desc 巨型 HTML、shipping_fee_info 等展示用不到的字段一律不映射。
 */
internal fun parseSponsorManagePage(root: JsonObject): SponsorManagePage {
    val data = root["data"]?.jsonObject ?: JsonObject(emptyMap())
    val list = data["list"]?.jsonArray.orEmpty().mapNotNull { element ->
        (element as? JsonObject)?.let(::parseSponsorManageItem)
    }
    return SponsorManagePage(
        list = list,
        totalCount = data.intOrNull("total_count") ?: 0,
        totalPages = data.intOrNull("total_page") ?: 0,
    )
}

/** 解析单个赞助者条目；缺少 user.user_id 的脏数据整条丢弃。 */
internal fun parseSponsorManageItem(obj: JsonObject): SponsorManageItem? {
    val user = obj["user"]?.jsonObject ?: return null
    val userId = user.str("user_id")?.takeIf { it.isNotBlank() } ?: return null
    // current_plan 可能缺失（无生效计划），此时展示层显示「无」
    val currentPlan = obj["current_plan"] as? JsonObject
    val plans = obj["sponsor_plans"]?.jsonArray.orEmpty().mapNotNull { element ->
        val plan = element as? JsonObject ?: return@mapNotNull null
        SponsorPlanBrief(
            planId = plan.str("plan_id").orEmpty(),
            name = plan.str("name").orEmpty(),
            price = plan.str("price") ?: "0",
            payMonth = plan.intOrNull("pay_month") ?: 1,
            productType = plan.intOrNull("product_type"),
        )
    }
    return SponsorManageItem(
        userId = userId,
        name = user.str("name").orEmpty(),
        avatar = user.str("avatar"),
        urlSlug = user.str("url_slug"),
        currentPlanName = currentPlan?.str("name")?.takeIf { it.isNotBlank() },
        allSumAmount = obj.str("all_sum_amount") ?: "0",
        lastPayTime = obj.longOrNull("last_pay_time") ?: 0L,
        createTime = obj.longOrNull("create_time") ?: 0L,
        plans = plans,
    )
}

// —— 解析辅助：缺字段返回 null（金额/时间展示层再兜底），不吞成 "--" ——

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(key: String): Int? = str(key)?.toIntOrNull()

private fun JsonObject.longOrNull(key: String): Long? = str(key)?.toLongOrNull()

/**
 * 把筛选条件行构建为 who-sponsored-me 的 filter JSON 数组（AND 关系）。
 * 不完整的行（方案型未选方案 / 金额非合法数字 / 月份未选）跳过，等价官方的「全部/空值」。
 * 赞助月份按抓包协议输出：value 为逗号分隔 YYYYMM 串，并附 month_value 名值数组。
 * 顶层 internal 便于单测。
 */
internal fun buildSponsorFilterArray(filters: List<SponsorFilterRow>): JsonArray =
    buildJsonArray {
        filters.filter { it.isComplete }.forEach { row ->
            addJsonObject {
                put("type", row.type.field)
                put("action", row.action.symbol)
                if (row.type == SponsorFilterType.Month) {
                    // 多月份：value = "202610,202609"，month_value 提供展示名
                    put("value", row.months.joinToString(","))
                    put(
                        "month_value",
                        buildJsonArray {
                            row.months.forEach { yyyymm ->
                                addJsonObject {
                                    put("name", monthDisplayName(yyyymm))
                                    put("value", yyyymm)
                                }
                            }
                        },
                    )
                } else {
                    put(
                        "value",
                        if (row.type.isPlanType) row.plan!!.planId else row.number,
                    )
                }
            }
        }
    }

/**
 * 解析 all-plans 响应 → 筛选档位方案选项。
 * 合并 list（订阅/独立）+ sale_list（售卖/捆绑）按 plan_id 去重，
 * 排除 0 元与无法解析价格的方案（网页下拉同样不显示 0 元档）。
 * 顶层 internal 便于单测。
 */
internal fun parseFilterPlans(root: JsonObject): List<SponsorFilterPlan> {
    val data = root["data"]?.jsonObject ?: return emptyList()
    fun plansOf(key: String): List<SponsorFilterPlan> =
        data[key]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val planId = obj.str("plan_id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SponsorFilterPlan(
                planId = planId,
                name = obj.str("name").orEmpty(),
                // show_price 与 price 实测一致，优先 show_price 与展示口径对齐
                price = obj.str("show_price") ?: obj.str("price") ?: return@mapNotNull null,
            )
        }
    return (plansOf("list") + plansOf("sale_list"))
        .distinctBy { it.planId }
        .filter { plan ->
            plan.price.toBigDecimalOrNull()?.let { it.signum() > 0 } == true
        }
}
