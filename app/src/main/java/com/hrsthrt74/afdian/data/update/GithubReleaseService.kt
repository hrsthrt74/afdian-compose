package com.hrsthrt74.afdian.data.update

import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.network.sharedAfdianHttpClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GitHub Releases 服务：拉取本项目最新的一个 Release。
 *
 * 为什么用 GitHub Releases 作为更新源：
 * - 仓库在 GitHub 上，安装包作为 Release 附件发布，用户点一下就能下；
 * - Release 自带版本号（tag）、更新说明（body）与附件列表，够用；
 * - 不需要额外的服务器，也不占用第三方的额度。
 *
 * 前置条件：仓库必须**公开**且至少发布过一个 Release。
 * 仓库私有时 GitHub 对匿名请求返回 404 —— 这里把它当作「没有可用更新」处理，
 * 而不是错误：仓库还没公开时检查更新功能理应静默。
 */
internal class GithubReleaseService(
    private val client: OkHttpClient = sharedAfdianHttpClient,
) {

    private val json = Json {
        // GitHub 随时可能给 Release 加字段，忽略未知字段避免解析失败
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private companion object {
        const val TAG = "UpdateService"
        const val API_BASE = "https://api.github.com"
        const val REPO = "hrsthrt74/afdian-compose"

        /**
         * GitHub API 强制要求带 User-Agent，否则返回 403。
         * 这里带上项目名与版本，便于对方在日志里识别调用方。
         */
        val USER_AGENT = "afdian-compose-android-client"
    }

    /**
     * 拉取最新 Release。
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
                if (response.code == 404) {
                    // 仓库私有 / 尚未发布任何 Release：不是错误，静默当作「没有更新」
                    AppLog.d(TAG, "GitHub 未找到 Release（HTTP 404）：仓库可能尚未公开发布")
                    null
                } else if (!response.isSuccessful) {
                    throw IOException("GitHub API 返回 HTTP ${response.code}")
                } else {
                    val body = response.body?.string().orEmpty()
                    json.decodeFromString<GithubRelease>(body)
                }
            }
        }.onFailure { error ->
            // 失败只记警告：自动检查是静默的，不该用错误级别刷屏
            AppLog.w(TAG, "检查更新失败：${error.javaClass.simpleName}", error)
        }
    }
}
