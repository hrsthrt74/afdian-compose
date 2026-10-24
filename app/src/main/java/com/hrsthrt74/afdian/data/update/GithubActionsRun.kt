package com.hrsthrt74.afdian.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Actions 的一次工作流运行（只保留更新检查用得到的字段）。
 *
 * 为什么要走 Runs API 而不是 artifact 直链：GitHub 的 workflow artifact 下载必须带
 * 认证信息（公开仓库也一样），App 里没有用户的 GitHub 凭据，只能把运行页面交给浏览器。
 *
 * @param runNumber 工作流内的自增编号，是本通道「谁更新」的唯一依据
 * @param headSha 触发本次运行的 commit，用来判断「本地装的就是这条构建」
 * @param displayTitle 运行标题（默认是 commit message），展示在更新说明里
 * @param htmlUrl 运行页面地址，也就是「查看构建」要打开的地址
 * @param status queued / in_progress / completed
 * @param conclusion success / failure / cancelled …（仅 completed 时有值）
 */
@Serializable
data class GithubActionsRun(
    @SerialName("id") val id: Long = 0,
    @SerialName("run_number") val runNumber: Int = 0,
    @SerialName("run_attempt") val runAttempt: Int = 1,
    @SerialName("head_sha") val headSha: String = "",
    @SerialName("head_branch") val headBranch: String = "",
    @SerialName("display_title") val displayTitle: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("conclusion") val conclusion: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("event") val event: String = "",
) {

    /**
     * 这次运行是否真的成功产出了安装包。
     *
     * 查询时已经带了 `status=success`，这里再判一次是防御性的：服务端过滤语义
     * 与字段实际取值一旦有出入，宁可少提示也不要给用户一个打不开的页面。
     */
    val isSuccess: Boolean
        get() = status == "completed" && conclusion == "success"
}

/** 运行列表响应。`total_count` 只用于日志，业务只关心第一条。 */
@Serializable
internal data class GithubActionsRunList(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<GithubActionsRun> = emptyList(),
)
