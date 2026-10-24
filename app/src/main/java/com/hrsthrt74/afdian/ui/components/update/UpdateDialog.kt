package com.hrsthrt74.afdian.ui.components.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.storage.formatBytes
import com.hrsthrt74.afdian.data.update.UpdateChannel
import com.hrsthrt74.afdian.data.update.UpdateInfo
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.DialogState
import com.hrsthrt74.afdian.ui.viewmodel.UpdateDownloadState
import com.hrsthrt74.afdian.ui.viewmodel.UpdateUiState
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一个弹窗的文案与动作配置。
 *
 * 把「不同状态下弹窗长什么样」收成一份配置，而不是在各个分支里给同一批 val 反复赋值 ——
 * 后者要么得声明成 var（可被误改），要么得在每个分支里都赋一遍（漏一个就编译不过）。
 */
private data class UpdateDialogConfig(
    val title: String,
    val summary: String?,
    /** 取消按钮文案；null = 不渲染取消按钮（单键弹窗） */
    val cancelText: String?,
    val confirmText: String,
    /** 第三个动作的文案；null = 不渲染 */
    val extraActionText: String?,
    val onExtraAction: (() -> Unit)?,
    /** 点击确认后是否关闭弹窗；下载 / 安装要留在原地看进度 */
    val dismissOnConfirm: Boolean,
    val onConfirm: () -> Unit,
)

/**
 * 检查更新弹窗。
 *
 * 一个组件覆盖「检查」与「下载安装」两条状态流：
 * - 检查：Checking / UpToDate / NoBuild / Failed / Available；
 * - 下载：Downloading / Ready / NeedPermission / Failed。
 *
 * 两条通道的差别只在 Available 那一档：
 * - 正式版：附件是直链，「下载安装」在应用内下载并拉起系统安装器；
 * - Actions 构建：artifact 必须登录 GitHub 才能下载，「查看构建」跳对应的运行页面。
 *
 * @param state 弹窗显示状态
 * @param uiState 检查结果
 * @param downloadState 下载 / 安装进度
 * @param onIgnore 「忽略此版本 / 此构建」回调
 * @param onOpenUrl 「查看构建」/ 降级打开网页的回调
 * @param onDownload 「下载安装」回调
 * @param onInstall 「安装」回调
 * @param onCancelDownload 取消下载
 * @param onOpenInstallPermission 打开「安装未知应用」授权页
 * @param onDismissed 关闭动画完成后的回调（复位状态、丢弃未完成的下载）
 */
@Composable
fun UpdateDialog(
    state: DialogState,
    uiState: UpdateUiState,
    downloadState: UpdateDownloadState,
    onIgnore: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onDismissed: (() -> Unit)? = null,
) {
    val info = (uiState as? UpdateUiState.Available)?.info

    // 标题 / 摘要 / 按钮随状态变化，集中在这里算一次。
    // 先判下载态：它们只会出现在 Available 之后，且需要覆盖按钮与「是否关窗」。
    val config = when (downloadState) {
        is UpdateDownloadState.Downloading -> UpdateDialogConfig(
            title = "正在下载 ${info?.versionName.orEmpty()}".trim(),
            summary = null,
            cancelText = null,
            confirmText = "取消下载",
            extraActionText = null,
            onExtraAction = null,
            dismissOnConfirm = true,
            onConfirm = onCancelDownload,
        )

        is UpdateDownloadState.Ready -> UpdateDialogConfig(
            title = "下载完成",
            summary = "版本 ${info?.versionName.orEmpty()}，点击安装完成更新。".trim(),
            cancelText = "稍后",
            confirmText = "安装",
            extraActionText = null,
            onExtraAction = null,
            // 安装要交给系统安装器，弹窗保持打开，回来还能再点一次
            dismissOnConfirm = false,
            onConfirm = onInstall,
        )

        is UpdateDownloadState.NeedPermission -> UpdateDialogConfig(
            title = "需要安装权限",
            summary = "请允许本应用安装未知来源的应用，然后返回这里继续安装。",
            cancelText = "稍后",
            confirmText = "去设置",
            extraActionText = null,
            onExtraAction = null,
            // 去设置页会离开应用，弹窗必须留着，否则回来就没法继续安装
            dismissOnConfirm = false,
            onConfirm = onOpenInstallPermission,
        )

        is UpdateDownloadState.Failed -> UpdateDialogConfig(
            title = "下载失败",
            summary = downloadState.message,
            cancelText = "稍后",
            confirmText = "重试",
            extraActionText = null,
            onExtraAction = null,
            dismissOnConfirm = false,
            onConfirm = { info?.let(onDownload) },
        )

        UpdateDownloadState.Idle -> when (uiState) {
            is UpdateUiState.Checking -> UpdateDialogConfig(
                title = "检查更新",
                summary = "正在检查…",
                cancelText = null,
                confirmText = "取消",
                extraActionText = null,
                onExtraAction = null,
                dismissOnConfirm = true,
                onConfirm = { state.dismiss() },
            )

            is UpdateUiState.UpToDate -> UpdateDialogConfig(
                title = "检查更新",
                summary = uiState.message,
                cancelText = null,
                confirmText = "好",
                extraActionText = null,
                onExtraAction = null,
                dismissOnConfirm = true,
                onConfirm = { state.dismiss() },
            )

            is UpdateUiState.NoBuild -> UpdateDialogConfig(
                title = "检查更新",
                summary = uiState.message,
                cancelText = null,
                confirmText = "好",
                extraActionText = null,
                onExtraAction = null,
                dismissOnConfirm = true,
                onConfirm = { state.dismiss() },
            )

            is UpdateUiState.Failed -> UpdateDialogConfig(
                title = "检查更新失败",
                summary = uiState.message,
                cancelText = null,
                confirmText = "好",
                extraActionText = null,
                onExtraAction = null,
                dismissOnConfirm = true,
                onConfirm = { state.dismiss() },
            )

            is UpdateUiState.Available -> when (uiState.info.channel) {
                // 正式版：应用内下载 + 系统安装器
                UpdateChannel.Stable -> UpdateDialogConfig(
                    title = "发现新版本 ${uiState.info.versionName}",
                    summary = null,
                    cancelText = "稍后",
                    // 挑不到匹配当前变体的附件时降级成打开 Releases 页面，
                    // 不能让按钮点下去毫无反应
                    confirmText = if (uiState.info.downloadUrl.isNullOrBlank()) "打开页面" else "下载安装",
                    extraActionText = "忽略此版本",
                    onExtraAction = onIgnore,
                    // 有直链时点完进入下载态，弹窗留在原地显示进度；没有直链只是开网页，直接关掉
                    dismissOnConfirm = uiState.info.downloadUrl.isNullOrBlank(),
                    onConfirm = {
                        val directUrl = uiState.info.downloadUrl
                        if (directUrl.isNullOrBlank()) {
                            onOpenUrl(uiState.info.releaseUrl)
                        } else {
                            onDownload(uiState.info)
                        }
                    },
                )

                // Actions 构建：拿不到 artifact（需要登录），只跳到运行页面
                UpdateChannel.Actions -> UpdateDialogConfig(
                    title = "发现新的${uiState.info.versionName}",
                    summary = null,
                    cancelText = "稍后",
                    confirmText = "查看构建",
                    extraActionText = "忽略此构建",
                    onExtraAction = onIgnore,
                    dismissOnConfirm = true,
                    onConfirm = { onOpenUrl(uiState.info.releaseUrl) },
                )
            }

            // Idle：没有结果要展示，调用方不应该把弹窗打开
            is UpdateUiState.Idle -> UpdateDialogConfig(
                title = "检查更新",
                summary = null,
                cancelText = null,
                confirmText = "好",
                extraActionText = null,
                onExtraAction = null,
                dismissOnConfirm = true,
                onConfirm = { state.dismiss() },
            )
        }
    }

    AppDialog(
        state = state,
        title = config.title,
        summary = config.summary,
        cancelText = config.cancelText,
        confirmText = config.confirmText,
        extraActionText = config.extraActionText,
        onExtraAction = config.onExtraAction,
        dismissOnConfirm = config.dismissOnConfirm,
        onConfirm = config.onConfirm,
        onDismissed = onDismissed,
    ) {
        when {
            // 下载中：进度环 + 已下载 / 总大小
            downloadState is UpdateDownloadState.Downloading -> DownloadProgress(downloadState)

            // 已下载 / 待授权：进度区没有信息量，留白交给摘要
            downloadState is UpdateDownloadState.Ready ||
                downloadState is UpdateDownloadState.NeedPermission -> Unit

            // 其余情况展示更新说明 / 构建信息
            info != null && info.notes.isNotBlank() -> UpdateNotes(info.notes)
        }
    }
}

/** 下载进度：环形进度 + 字节数文案（服务端没给总长度时只显示已下载）。 */
@Composable
private fun DownloadProgress(downloadState: UpdateDownloadState.Downloading) {
    val progress = downloadState.progress
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            progress = progress ?: 0f,
            size = 32.dp,
            strokeWidth = 3.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (progress != null) {
                "${formatBytes(downloadState.bytesRead)} / ${formatBytes(downloadState.totalBytes)}" +
                    "（${(progress * 100).toInt()}%）"
            } else {
                "已下载 ${formatBytes(downloadState.bytesRead)}"
            },
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 更新说明正文：可能很长，限高 + 滚动，避免弹窗顶出屏幕。 */
@Composable
private fun UpdateNotes(notes: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = notes,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
