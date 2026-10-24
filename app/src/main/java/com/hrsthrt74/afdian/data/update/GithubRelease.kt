package com.hrsthrt74.afdian.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Release 的一条附件资源（安装包就是以附件形式挂上去的）。
 *
 * @param name 附件文件名（用于判断 edition，如 `Afdian-1.0.0-standard-release.apk`）
 * @param downloadUrl 直接下载地址
 * @param size 文件字节数
 */
@Serializable
data class GithubAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    @SerialName("size") val size: Long = 0L,
)

/**
 * GitHub Release 的最小字段集。
 *
 * 只取用得到的字段，其余字段一律忽略（解析时开了 `ignoreUnknownKeys`），
 * 这样 GitHub 将来给 Release 加字段也不会把解析搞崩。
 *
 * @param tagName 版本标签，如 `v1.0.0`；「忽略此版本」就是记这个值
 * @param name 标题
 * @param body 更新说明（Markdown 原文）
 * @param htmlUrl Releases 页面地址
 * @param draft 是否是草稿（草稿对外不可见，直接跳过）
 * @param prerelease 是否是预发布（预发布不打扰正式用户，直接跳过）
 * @param publishedAt 发布时间
 * @param assets 附件列表
 */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("assets") val assets: List<GithubAsset> = emptyList(),
)

/**
 * 一次「有可用更新」的结果。
 *
 * 两条通道共用一个模型：正式版填 [downloadUrl]，Actions 构建只填 [releaseUrl]。
 *
 * @param channel 这次结果来自哪条通道（决定弹窗按钮是「下载安装」还是「查看构建」）
 * @param versionName 展示用的版本描述：正式版 `1.2.0`；Actions 通道 `构建 #128`
 * @param identifier 忽略用的唯一标识：正式版是 tag（`v1.2.0`），Actions 是 `run:128`
 *   —— 旧实现只有 tag，而 Actions 通道没有 tag，忽略一次就会永久静默
 * @param releaseUrl Release 页面 / Actions 运行页面（拿不到安装包时降级跳这里）
 * @param downloadUrl 安装包直链；只有正式版可能非空
 * @param downloadSize 安装包字节数，0 表示未知（用于进度与完整性校验）
 * @param notes 更新说明正文 / 构建信息
 * @param runNumber Actions 构建编号，正式版为 0
 * @param headSha Actions 构建对应的 commit，正式版可能为空
 */
data class UpdateInfo(
    val channel: UpdateChannel,
    val versionName: String,
    val identifier: String,
    val releaseUrl: String,
    val downloadUrl: String?,
    val downloadSize: Long,
    val notes: String,
    val runNumber: Int = 0,
    val headSha: String = "",
)
