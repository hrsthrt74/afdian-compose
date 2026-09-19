package com.hrsthrt74.afdian.data.stats

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.auth.AuthTokenStore
import com.hrsthrt74.afdian.data.network.AfdianHttpClient
import com.hrsthrt74.afdian.data.network.requireAfdianSuccess
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 创作者后台「每月汇总」数据仓库。
 *
 * 数据源：GET https://afdian.com/api/my/income，Cookie auth_token 认证，
 * 一次返回自开通以来的全部月度账单（按年份分组）。
 *
 * 与 [CreatorStatsRepository] 的是同一个接口，但那边的结果只服务数据卡与月度收入图表；
 * 这里按页面职责单独成仓库，解析逻辑复用 [parseMonthlyBills]，避免两处各写一套字段映射。
 */
class MonthlySummaryRepository(
    private val tokenStore: AuthTokenStore,
    private val client: OkHttpClient = sharedAfdianHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = AfdianHttpClient(client)

    /**
     * 拉取月度账单列表（未排序，调用方用 [monthlyBillSections] 分组）。
     *
     * 网络请求放在 Dispatchers.IO；业务失败（ec != 200）抛异常交由 ViewModel 呈现错误态。
     */
    suspend fun fetchMonthlyBills(): List<MonthlyBillRecord> = withContext(Dispatchers.IO) {
        val url = (AFDIAN_HOST + PATH_INCOME).toHttpUrl().newBuilder()
            .addQueryParameter("auth_token", tokenStore.token)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            // 查询参数与 Cookie 双重认证，同站内其他接口做法
            .header("Cookie", "auth_token=${tokenStore.token}")
            .build()
        val start = System.currentTimeMillis()
        http.execute(request).use { response ->
            if (!response.isSuccessful) {
                AppLog.e(TAG, "$PATH_INCOME -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms）")
                error("每月汇总加载失败 (${response.code})")
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            root.requireAfdianSuccess(TAG, PATH_INCOME, "每月汇总加载失败", requireEc = true)
            val bills = parseMonthlyBills(root["data"]?.jsonObject?.get("monthly_bill"))
            AppLog.d(
                TAG,
                "$PATH_INCOME -> HTTP ${response.code}（${System.currentTimeMillis() - start}ms），" +
                    "月份数=${bills.size}，年份数=${bills.map { it.month.year }.distinct().size}",
            )
            bills
        }
    }

    private companion object {
        const val TAG = "MonthlySummaryRepository"
        const val AFDIAN_HOST = "https://afdian.com"
        const val PATH_INCOME = "/api/my/income"
    }
}
