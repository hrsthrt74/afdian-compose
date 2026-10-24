package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.update.GithubRelease
import com.hrsthrt74.afdian.data.update.GithubReleaseService
import com.hrsthrt74.afdian.data.update.UpdateInfo
import com.hrsthrt74.afdian.data.update.UpdatePolicy
import com.hrsthrt74.afdian.data.update.isNewerVersion
import com.hrsthrt74.afdian.data.update.parseReleaseVersion
import com.hrsthrt74.afdian.data.update.pickApkAsset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 检查更新的界面状态。
 *
 * 显式建模四态（见 DESIGN.md）：
 * - [Idle]：没在查，也没结果；
 * - [Checking]：正在查；
 * - [UpToDate]：查完了，没有可用更新（已是最新 / 仓库还没有 Release / 用户忽略了该版本）；
 * - [Available]：有可用更新；
 * - [Failed]：查失败了。
 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val message: String) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/**
 * 检查更新的 ViewModel。
 *
 * 两种检查方式：
 * - [checkInBackground]：冷启动静默检查。全程不打扰用户，只有确认有新版才把状态置为
 *   [UpdateUiState.Available]，由界面决定要不要弹窗；
 * - [checkManually]：设置页手动检查。有明确的四态反馈，包括失败。
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val service = GithubReleaseService()

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    // 并发守卫：连点「检查更新」不重复打 API
    private var checking = false

    /**
     * 冷启动静默检查。
     *
     * 全程静默：请求失败、仓库还没有 Release、版本不比现在新、用户忽略过该版本 ——
     * 这些情况一律不打扰用户，只记日志。
     */
    fun checkInBackground() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val lastCheckAt = settingsStore.updateLastCheckAt.first()
            if (!UpdatePolicy.shouldAutoCheck(lastCheckAt, now)) {
                AppLog.d(TAG, "跳过自动检查更新：距上次检查不足 24 小时")
                return@launch
            }
            // 先记账再发请求：失败也计入节流，避免弱网下反复打 GitHub API
            settingsStore.setUpdateLastCheckAt(now)

            val ignoredVersion = settingsStore.updateIgnoredVersion.first()
            val release = service.fetchLatest().getOrNull()
            if (release == null) {
                // 失败或仓库还没有 Release：静默跳过
                return@launch
            }
            val info = release.toUpdateInfo(ignoredVersion)
            if (info == null) {
                AppLog.d(TAG, "无更新：tag=${release.tagName}（草稿/预发布/已忽略/版本未变）")
                return@launch
            }
            AppLog.d(TAG, "发现新版本：${info.versionName}（tag=${info.tagName}）")
            _uiState.value = UpdateUiState.Available(info)
        }
    }

    /**
     * 手动检查（设置页「检查更新」）。
     *
     * 与自动检查的差别：不看节流、失败要如实告诉用户。
     */
    fun checkManually() {
        if (checking) return
        checking = true
        _uiState.value = UpdateUiState.Checking
        viewModelScope.launch {
            try {
                settingsStore.setUpdateLastCheckAt(System.currentTimeMillis())
                val ignoredVersion = settingsStore.updateIgnoredVersion.first()
                val result = service.fetchLatest()
                val release = result.getOrNull()
                _uiState.value = when {
                    result.isFailure ->
                        UpdateUiState.Failed("网络异常，检查失败，请稍后再试")

                    release == null ->
                        UpdateUiState.UpToDate("暂无可用更新")

                    release.draft || release.prerelease ->
                        UpdateUiState.UpToDate("暂无可用更新")

                    else -> {
                        // 「已忽略」要单独判：toUpdateInfo 对被忽略的版本同样返回 null，
                        // 混在一起的话用户看到的会是「当前已是最新版本」，与事实不符
                        if (release.tagName == ignoredVersion) {
                            UpdateUiState.UpToDate("你已忽略该版本")
                        } else {
                            val info = release.toUpdateInfo(ignoredVersion)
                            if (info == null) {
                                UpdateUiState.UpToDate("当前已是最新版本")
                            } else {
                                UpdateUiState.Available(info)
                            }
                        }
                    }
                }
            } finally {
                checking = false
            }
        }
    }

    /**
     * 忽略某个版本：记住它的 tag，同一个版本不再提示。
     * 传空串表示取消忽略。
     */
    fun ignoreVersion(tagName: String) {
        viewModelScope.launch { settingsStore.setUpdateIgnoredVersion(tagName) }
    }

    /**
     * 弹窗关闭后把「有更新」这个状态复位。
     *
     * 不复位的话，旋转屏幕等重组会再次触发弹窗 —— 状态是「结果」而不是「事件」，
     * 事件用完就该清掉。
     */
    fun consumeAvailable() {
        if (_uiState.value is UpdateUiState.Available) {
            _uiState.value = UpdateUiState.Idle
        }
    }

    private companion object {
        const val TAG = "Update"
    }
}

/**
 * 把一个 Release 转成「可用更新」信息。
 *
 * @param ignoredVersion 用户已忽略的 tag
 * @return 可用更新返回信息；不该打扰用户（草稿、预发布、已忽略、版本没变）时返回 null
 */
private fun GithubRelease.toUpdateInfo(ignoredVersion: String): UpdateInfo? {
    if (draft || prerelease) return null
    if (tagName.isBlank()) return null
    if (tagName == ignoredVersion) return null
    if (!isNewerVersion(tagName, BuildConfig.VERSION_NAME)) return null

    val asset = pickApkAsset(
        assets = assets,
        legacyEdition = BuildConfig.LEGACY_EDITION,
    )
    return UpdateInfo(
        // 展示用版本号：把 tag 的 v 前缀去掉；解析不了就原样显示 tag
        versionName = parseReleaseVersion(tagName)?.toString() ?: tagName,
        tagName = tagName,
        releaseUrl = htmlUrl,
        // 挑不到匹配当前变体的安装包时降级：只给 Releases 页面地址
        downloadUrl = asset?.downloadUrl,
        notes = body,
    )
}
