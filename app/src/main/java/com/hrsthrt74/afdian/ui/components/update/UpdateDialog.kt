package com.hrsthrt74.afdian.ui.components.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.DialogState
import com.hrsthrt74.afdian.ui.viewmodel.UpdateUiState
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
    val onConfirm: () -> Unit,
)

/**
 * 检查更新弹窗。
 *
 * 一个组件覆盖全部状态（[UpdateUiState] 的五种形态），因为它们只在
 * 「标题 / 文案 / 按钮」上有差别，拆成多个弹窗反而会重复一堆样板：
 * - [UpdateUiState.Checking]：正在查，按钮是「取消」；
 * - [UpdateUiState.UpToDate]：无需更新，单键「好」；
 * - [UpdateUiState.Failed]：检查失败，单键「好」；
 * - [UpdateUiState.Available]：有新版本，三个出口 —— 忽略此版本 / 稍后 / 去下载。
 *
 * @param state 弹窗显示状态
 * @param uiState 当前状态
 * @param onIgnore 「忽略此版本」回调，参数是版本 tag
 * @param onOpenDownload 「去下载」回调，参数是要打开的地址
 * @param onDismissed 关闭动画完成后的回调（用于把「有更新」这个状态复位）
 */
@Composable
fun UpdateDialog(
    state: DialogState,
    uiState: UpdateUiState,
    onIgnore: (String) -> Unit,
    onOpenDownload: (String) -> Unit,
    onDismissed: (() -> Unit)? = null,
) {
    val available = uiState as? UpdateUiState.Available

    // 标题 / 摘要 / 按钮随状态变化，集中在这里算一次
    val config = when (uiState) {
        is UpdateUiState.Checking -> UpdateDialogConfig(
            title = "检查更新",
            summary = "正在检查…",
            cancelText = null,
            confirmText = "取消",
            extraActionText = null,
            onExtraAction = null,
            onConfirm = { state.dismiss() },
        )

        is UpdateUiState.UpToDate -> UpdateDialogConfig(
            title = "检查更新",
            summary = uiState.message,
            cancelText = null,
            confirmText = "好",
            extraActionText = null,
            onExtraAction = null,
            onConfirm = { state.dismiss() },
        )

        is UpdateUiState.Failed -> UpdateDialogConfig(
            title = "检查更新失败",
            summary = uiState.message,
            cancelText = null,
            confirmText = "好",
            extraActionText = null,
            onExtraAction = null,
            onConfirm = { state.dismiss() },
        )

        is UpdateUiState.Available -> UpdateDialogConfig(
            title = "发现新版本 ${uiState.info.versionName}",
            summary = null,
            cancelText = "稍后",
            confirmText = "去下载",
            // 第三个出口：忽略这个版本，以后不再提示
            extraActionText = "忽略此版本",
            onExtraAction = { onIgnore(uiState.info.tagName) },
            onConfirm = {
                // 优先给安装包直链；挑不到匹配当前变体的包时退到 Releases 页面
                onOpenDownload(uiState.info.downloadUrl ?: uiState.info.releaseUrl)
            },
        )

        // Idle：没有结果要展示，调用方不应该把弹窗打开
        is UpdateUiState.Idle -> UpdateDialogConfig(
            title = "检查更新",
            summary = null,
            cancelText = null,
            confirmText = "好",
            extraActionText = null,
            onExtraAction = null,
            onConfirm = { state.dismiss() },
        )
    }

    AppDialog(
        state = state,
        title = config.title,
        summary = config.summary,
        cancelText = config.cancelText,
        confirmText = config.confirmText,
        extraActionText = config.extraActionText,
        onExtraAction = config.onExtraAction,
        onConfirm = config.onConfirm,
        onDismissed = onDismissed,
    ) {
        // 更新说明正文：可能有很长的一串版本记录，限高 + 滚动，避免弹窗顶出屏幕
        val notes = available?.info?.notes.orEmpty()
        if (notes.isNotBlank()) {
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
    }
}
