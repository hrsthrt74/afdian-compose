package com.hrsthrt74.afdian.data.update

import com.hrsthrt74.afdian.AppLog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GitHub 更新源：拉取最新的正式 Release，或 master 上最近一次成功的 Actions 构建。
 *
 * 为什么用 GitHub 而不是自建更新服务器：
 * - 仓库本来就在 GitHub 上，安装包作为 Release 附件 / CI artifact 产出，不需要额外服务；
 * - Release 自带版本号（tag）、更新说明（body）与附件列表，够用；
 * - 不占第三方额度，也不引入需要维护的鉴权体系。
 *
 * 前置条件：仓库必须**公开**。仓库私有时 GitHub 对匿名请求返回 404 —— 这里把它当作
 * 「没有可用更新」处理而不是错误：仓库还没公开时检查更新理应静默。
 */
internal class GithubReleaseService(
    private val client: OkHttpClient = updateHttpClient,
) {

    private val json = Json {
        // GitHub 随时可能给 Release / 运行对象加字段，忽略未知字段避免解析失败
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private companion object {
        const val TAG = "UpdateService"
        const val API_BASE = "https://api.github.com"
        const val REPO = "hrsthrt74/afdian-compose"

        /**
         * Actions 通道对应的 workflow 文件名。
         *
         * ⚠️ 必须与 `.github/workflows/build.yml` 完全一致：改名会让该 workflow 的
         * run_number 从 1 重新计数，已安装 Actions 包的用户会认为「没有更新」。
         */
        const val WORKFLOW_FILE = "build.yml"

        /** 只看主干的构建，PR / 其他分支的产物不进入更新通道。 */
        const val BRANCH = "master"

        /**
         * GitHub API 强制要求带 User-Agent，否则返回 403。
         * 这里带上项目名与版本，便于对方在日志里识别调用方。
         */
        val USER_AGENT = "afdian-compose-android-client"
    }

    /**
     * 拉取最新正式 Release（GitHub 的 `/releases/latest` 本身就会跳过 draft 与 prerelease）。
     *
     * @return 成功时返回 Release 对象；仓库还没有任何 Release（或不可见）时返回 null；
     *   网络异常、HTTP 错误、JSON 解析失败时返回失败的结果
     */
    suspend fun fetchLatest(): Result<GithubRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$API_BASE/repos/$REPO/releases/latest")
                // GitHub 新版 API 的推荐 Accept
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    // 仓库私有 / 尚未发布任何 Release：不是错误，静默当作「没有更新」
                    response.code == 404 -> {
                        AppLog.d(TAG, "GitHub 未找到 Release（HTTP 404）：仓库可能尚未公开发布")
                        null
                    }

                    response.code == 403 || response.code == 429 -> {
                        AppLog.w(TAG, "GitHub API 触发限流（HTTP ${response.code}）")
                        throw IOException("GitHub API 请求过于频繁，请稍后再试")
                    }

                    !response.isSuccessful -> {
                        throw IOException("GitHub API 返回 HTTP ${response.code}")
                    }

                    else -> {
                        val body = response.body.string()
                        json.decodeFromString<GithubRelease>(body)
                    }
                }
            }
        }.onFailure { error ->
            // 失败只记警告：自动检查是静默的，不该用错误级别刷屏
            AppLog.w(TAG, "检查更新失败：${error.javaClass.simpleName}", error)
        }
    }

    /**
     * 拉取 master 上**最近一次成功**的 Actions 构建。
     *
     * 用 `status=success` 让服务端过滤掉还在跑/失败的运行：更新通道里只能出现
     * 真能下载到包的运行。客户端仍会再用 [GithubActionsRun.isSuccess] 校验一次。
     *
     * @return 没有可用运行（404 / 空列表）时返回 null；网络或解析失败返回失败结果
     */
    suspend fun fetchLatestActionsRun(): Result<GithubActionsRun?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$API_BASE/repos/$REPO/actions/workflows/$WORKFLOW_FILE/runs" +
                "?branch=$BRANCH&status=success&per_page=1"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    // 仓库私有 / workflow 还没跑过 / 文件名改了：都按「暂无可用的构建」处理
                    response.code == 404 -> {
                        AppLog.d(TAG, "未找到 Actions workflow（HTTP 404）：可能尚未运行过或仓库未公开")
                        null
                    }

                    response.code == 403 || response.code == 429 -> {
                        AppLog.w(TAG, "Actions Runs API 触发限流（HTTP ${response.code}）")
                        throw IOException("GitHub API 请求过于频繁，请稍后再试")
                    }

                    !response.isSuccessful -> {
                        throw IOException("GitHub API 返回 HTTP ${response.code}")
                    }

                    else -> {
                        val body = response.body.string()
                        val runs = json.decodeFromString<GithubActionsRunList>(body)
                        // per_page=1 + status=success 理论上最多就一条，这里按列表处理更稳
                        runs.workflowRuns.firstOrNull { it.isSuccess }
                    }
                }
            }
        }.onFailure { error ->
            AppLog.w(TAG, "查询 Actions 构建失败：${error.javaClass.simpleName}", error)
        }
    }
}
