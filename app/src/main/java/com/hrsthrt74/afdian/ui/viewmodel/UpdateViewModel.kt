package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hrsthrt74.afdian.data.update.UpdateInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * 检查更新的 ViewModel。
 *
 * 这里**不持有任何状态**：全部状态在进程级单例 [UpdateCenter] 里。
 * 原因是同一个功能有两个入口（MainActivity 根部的冷启动检查、设置页的手动检查），
 * 各自 `viewModel()` 会得到两个互不相干的实例 —— 手动检查的结果根部看不到，
 * 自动检查的弹窗又会被设置页的状态干扰。收敛到 [UpdateCenter] 之后，
 * 两个实例只是同一个状态中心的两根手柄；ViewModel 只负责提供 Application Context。
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    /** 检查结果（Idle / Checking / UpToDate / NoBuild / Available / Failed）。 */
    val uiState: StateFlow<UpdateUiState> get() = UpdateCenter.state

    /** 下载与安装进度，与检查结果分开两条流。 */
    val downloadState: StateFlow<UpdateDownloadState> get() = UpdateCenter.downloadState

    /** 一次性信号：只有冷启动自动检查发现新版本时才置位，用于根部决定要不要弹窗。 */
    val autoPrompt: StateFlow<Boolean> get() = UpdateCenter.autoPrompt

    /** 冷启动静默检查（24 小时节流；失败 1 小时内不重试）。 */
    fun checkInBackground() = UpdateCenter.checkInBackground(app)

    /** 手动检查：不看节流，失败如实提示。 */
    fun checkManually() = UpdateCenter.checkManually(app)

    /** 忽略当前提示的版本 / 构建（按通道分别记录）。 */
    fun ignoreCurrent() = UpdateCenter.ignoreCurrent(app)

    /** 下载正式版安装包（只有正式版通道有直链）。 */
    fun download(info: UpdateInfo) = UpdateCenter.download(app, info)

    /** 拉起系统安装器；缺权限时先带去授权页。 */
    fun install() = UpdateCenter.install(app)

    /** 打开「安装未知应用」授权页。 */
    fun openInstallPermission() = UpdateCenter.openInstallPermission(app)

    /** 取消下载并清掉已落盘的安装包。 */
    fun cancelDownload() = UpdateCenter.cancelDownload(app)

    /**
     * 弹窗关闭后把「有更新」这个状态复位。
     *
     * 状态是「结果」而不是「事件」，不复位的话旋转屏幕等重组会再次把弹窗拉起来。
     * 同时会丢弃未完成的下载（关掉弹窗即视为放弃本次下载）。
     */
    fun consumeAvailable() = UpdateCenter.consume(app)
}
