package com.hrsthrt74.afdian.ui.viewmodel

import android.content.Context
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.update.GithubActionsRun
import com.hrsthrt74.afdian.data.update.GithubRelease
import com.hrsthrt74.afdian.data.update.GithubReleaseService
import com.hrsthrt74.afdian.data.update.UpdateChannel
import com.hrsthrt74.afdian.data.update.UpdateDownloader
import com.hrsthrt74.afdian.data.update.UpdateInfo
import com.hrsthrt74.afdian.data.update.UpdatePolicy
import com.hrsthrt74.afdian.data.update.isActionsRunNewer
import com.hrsthrt74.afdian.data.update.isNewerVersion
import com.hrsthrt74.afdian.data.update.parseIsoInstantMs
import com.hrsthrt74.afdian.data.update.parseReleaseVersion
import com.hrsthrt74.afdian.data.update.pickApkAsset
import com.hrsthrt74.afdian.ui.components.update.UpdateInstaller
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 检查更新的界面状态。
 *
 * - [Idle]：没在查，也没结果；
 * - [Checking]：正在查；
 * - [UpToDate]：查完了，没有可用更新（已是最新 / 仓库还没有 Release / 用户忽略了该版本）；
 * - [NoBuild]：通道能查通，但还没有可用的构建（Actions 还没跑成功过）；
 * - [Available]：有可用更新；
 * - [Failed]：查失败了。
 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val message: String) : UpdateUiState
    data class NoBuild(val message: String) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/**
 * 下载 / 安装进度。
 *
 * 与 [UpdateUiState] 分成两条流：进度每秒会变几十次，塞进 [UpdateUiState.Available]
 * 会让整个 UpdateInfo 跟着重建；分开后「检查结果」与「下载过程」各管各的，
 * 弹窗同时收集两条流即可。
 */
sealed interface UpdateDownloadState {

    data object Idle : UpdateDownloadState

    /** 下载中；[totalBytes] 为 0 表示服务端没给长度，UI 用不确定进度。 */
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : UpdateDownloadState {
        val progress: Float?
            get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }

    /** 已下载并通过包名校验，等待用户点「安装」。 */
    data class Ready(val file: File) : UpdateDownloadState

    /** 已下载但缺「安装未知应用」权限。 */
    data class NeedPermission(val file: File) : UpdateDownloadState

    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * 检查更新的进程级状态中心。
 *
 * 为什么不是一个共享 ViewModel：
 * - 两处入口的生命周期不同（根部随 Activity、设置页随路由进出），ViewModel 被 clear 时
 *   正在进行的下载就断了；
 * - 合并成一个实例后，设置页的手动检查会顺带把根部的自动弹窗拉起来 —— 所以「弹窗要不要弹」
 *   单独用一个一次性信号 [autoPrompt] 表示，只有冷启动自动检查会置位。
 *
 * 两个 `UpdateViewModel` 实例都只是这个对象的薄委托，状态天然一致。
 */
object UpdateCenter {

    private const val TAG = "UpdateCenter"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val service = GithubReleaseService()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    /** 一次性信号：冷启动发现新版本时为 true，消费后复位。手动检查永远不会置位。 */
    private val _autoPrompt = MutableStateFlow(false)
    val autoPrompt: StateFlow<Boolean> = _autoPrompt.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    // ── 检查 ──────────────────────────────────────────────────────────────

    /**
     * 冷启动静默检查：只有确认有新版才置 [UpdateUiState.Available] 并拉一次弹窗。
     * 失败、没更新、已忽略全部静默。
     */
    fun checkInBackground(context: Context) {
        if (checkJob?.isActive == true) return
        val app = context.applicationContext
        checkJob = scope.launch {
            val store = SettingsStore(app)
            val now = System.currentTimeMillis()
            val lastSuccess = store.updateLastCheckAt.first()
            val lastFailure = store.updateLastCheckFailureAt.first()
            if (!UpdatePolicy.shouldAutoCheck(lastSuccess, lastFailure, now)) {
                AppLog.d(TAG, "跳过自动检查更新：24 小时内已成功检查过，或刚失败不到 1 小时")
                return@launch
            }
            when (val outcome = runCheck(store)) {
                is CheckOutcome.Available -> {
                    // 成功才推进 24 小时窗口
                    store.setUpdateLastCheckAt(now)
                    AppLog.d(TAG, "发现新版本：${outcome.info.versionName}（${outcome.info.channel.storageValue}）")
                    // 冷启动时顺手清掉上一次遗留的安装包（装完重启后 files/updates 里还会留一份）
                    cancelDownload(app)
                    _state.value = UpdateUiState.Available(outcome.info)
                    _autoPrompt.value = true
                }

                is CheckOutcome.Nothing -> {
                    store.setUpdateLastCheckAt(now)
                    AppLog.d(TAG, "无更新：${outcome.message}")
                }

                is CheckOutcome.Failed -> {
                    // 失败不占 24 小时：推进失败时间戳，1 小时后仍可重试
                    store.setUpdateLastCheckFailureAt(now)
                    AppLog.w(TAG, "自动检查更新失败：${outcome.message}")
                }
            }
        }
    }

    /** 手动检查（设置 → 关于 →「检查更新」）：不看节流，失败要如实告诉用户。 */
    fun checkManually(context: Context) {
        // 手动检查优先：正在跑的自动检查直接让位，保证用户一定看到「正在检查…」并拿到新结果
        checkJob?.cancel()
        val app = context.applicationContext
        // 重新查之前丢弃上一次的下载（可能是用户中途离开页面留下的半截包）
        cancelDownload(app)
        _state.value = UpdateUiState.Checking
        checkJob = scope.launch {
            val store = SettingsStore(app)
            val now = System.currentTimeMillis()
            when (val outcome = runCheck(store)) {
                is CheckOutcome.Available -> {
                    store.setUpdateLastCheckAt(now)
                    _state.value = UpdateUiState.Available(outcome.info)
                }

                is CheckOutcome.Nothing -> {
                    store.setUpdateLastCheckAt(now)
                    _state.value = if (outcome.noBuild) {
                        UpdateUiState.NoBuild(outcome.message)
                    } else {
                        UpdateUiState.UpToDate(outcome.message)
                    }
                }

                is CheckOutcome.Failed -> {
                    store.setUpdateLastCheckFailureAt(now)
                    _state.value = UpdateUiState.Failed(outcome.message)
                }
            }
        }
    }

    /** 把一次检查的结果统一成三种内部形态，自动/手动共用同一套判定。 */
    private sealed interface CheckOutcome {
        data class Available(val info: UpdateInfo) : CheckOutcome
        data class Nothing(val message: String, val noBuild: Boolean = false) : CheckOutcome
        data class Failed(val message: String) : CheckOutcome
    }

    private suspend fun runCheck(store: SettingsStore): CheckOutcome =
        when (store.updateChannel.first()) {
            UpdateChannel.Stable -> checkStable(store)
            UpdateChannel.Actions -> checkActions(store)
        }

    private suspend fun checkStable(store: SettingsStore): CheckOutcome {
        val result = service.fetchLatest()
        if (result.isFailure) {
            return CheckOutcome.Failed(result.failureMessage("网络异常，检查失败，请稍后再试"))
        }
        val release = result.getOrNull() ?: return CheckOutcome.Nothing("暂无可用更新")
        // /releases/latest 本身会跳过 draft/prerelease，这里再挡一次（换数据源时不会漏）
        if (release.draft || release.prerelease) return CheckOutcome.Nothing("暂无可用更新")
        val ignoredTag = store.updateIgnoredVersion.first()
        if (release.tagName.isNotBlank() && release.tagName == ignoredTag) {
            return CheckOutcome.Nothing("你已忽略该版本")
        }
        val info = release.toStableUpdate() ?: return CheckOutcome.Nothing("当前已是最新版本")
        return CheckOutcome.Available(info)
    }

    private suspend fun checkActions(store: SettingsStore): CheckOutcome {
        val result = service.fetchLatestActionsRun()
        if (result.isFailure) {
            return CheckOutcome.Failed(result.failureMessage("网络异常，检查失败，请稍后再试"))
        }
        val run = result.getOrNull() ?: return CheckOutcome.Nothing(
            message = "还没有可用的 Actions 构建",
            noBuild = true,
        )
        // 只认 push / 手动重跑：PR 触发的运行不带签名包（拿不到 secrets），
        // 而 branch=master 这条过滤对「fork 里同名的 master 分支」并不可靠
        if (run.event.isNotBlank() && run.event != "push" && run.event != "workflow_dispatch") {
            AppLog.d(TAG, "最新 Actions 运行来自 ${run.event}，不作为更新来源")
            return CheckOutcome.Nothing("还没有可用的 Actions 构建", noBuild = true)
        }
        val ignoredRun = store.updateIgnoredActionsRun.first()
        if (run.runNumber == ignoredRun) return CheckOutcome.Nothing("你已忽略该构建")
        val info = run.toActionsUpdate() ?: return CheckOutcome.Nothing("当前已是 master 上最新的构建")
        return CheckOutcome.Available(info)
    }

    // ── 忽略 ──────────────────────────────────────────────────────────────

    /**
     * 忽略当前提示的版本 / 构建。
     *
     * 两条通道分开记：正式版按 tag，Actions 按构建号。共用一个键的话，
     * 切通道时会互相覆盖（忽略过正式版 1.2.0 后再切回来又被提示一次）。
     */
    fun ignoreCurrent(context: Context) {
        val info = (_state.value as? UpdateUiState.Available)?.info ?: return
        val app = context.applicationContext
        scope.launch {
            val store = SettingsStore(app)
            when (info.channel) {
                UpdateChannel.Stable -> store.setUpdateIgnoredVersion(info.identifier)
                UpdateChannel.Actions -> store.setUpdateIgnoredActionsRun(info.runNumber)
            }
            _state.value = UpdateUiState.Idle
        }
    }

    // ── 下载 / 安装 ────────────────────────────────────────────────────────

    /** 下载正式版安装包。Actions 通道没有直链，调用方不应该走到这里。 */
    fun download(context: Context, info: UpdateInfo) {
        val url = info.downloadUrl
        if (info.channel != UpdateChannel.Stable || url.isNullOrBlank()) {
            AppLog.w(TAG, "没有可用的安装包直链，已忽略下载请求")
            return
        }
        val app = context.applicationContext
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _downloadState.value = UpdateDownloadState.Downloading(0, info.downloadSize)
            val edition = if (BuildConfig.LEGACY_EDITION) "legacy" else "standard"
            val fileName = "Afdian-${info.versionName}-$edition-release.apk"
            UpdateDownloader(app)
                .download(url, fileName, info.downloadSize) { read, total ->
                    _downloadState.value = UpdateDownloadState.Downloading(read, total)
                }
                .onSuccess { file ->
                    val error = UpdateInstaller.verify(app, file)
                    _downloadState.value = if (error == null) {
                        UpdateDownloadState.Ready(file)
                    } else {
                        UpdateDownloadState.Failed(error)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _downloadState.value = UpdateDownloadState.Failed("下载失败，请检查网络后重试")
                }
        }
    }

    /** 拉起系统安装器；没授权时先去授权页。 */
    fun install(context: Context) {
        val app = context.applicationContext
        val file = when (val current = _downloadState.value) {
            is UpdateDownloadState.Ready -> current.file
            is UpdateDownloadState.NeedPermission -> current.file
            else -> return
        }
        if (!UpdateInstaller.canInstall(app)) {
            _downloadState.value = UpdateDownloadState.NeedPermission(file)
            UpdateInstaller.openPermissionSettings(app)
            return
        }
        if (!UpdateInstaller.install(app, file)) {
            _downloadState.value = UpdateDownloadState.Failed("无法拉起系统安装器，请检查系统是否禁用了应用安装")
        }
    }

    fun openInstallPermission(context: Context) {
        UpdateInstaller.openPermissionSettings(context.applicationContext)
    }

    /**
     * 取消下载并清掉已落盘的文件。
     *
     * 关闭弹窗即视为放弃本次下载：应用没有通知栏与前台服务，留在后台静默下载
     * 只会让用户以为卡住，还会在 `files/updates/` 里留下一个没人管的 30MB 包。
     */
    fun cancelDownload(context: Context) {
        downloadJob?.cancel()
        downloadJob = null
        discardDownloadedFiles(context.applicationContext)
        _downloadState.value = UpdateDownloadState.Idle
    }

    /**
     * 弹窗关闭动画结束后调用：复位一次性信号与检查结果，并丢弃未完成的下载。
     *
     * 正在检查时不复位 [UpdateUiState]：结果还要回填，用户在设置页还能再点一次。
     */
    fun consume(context: Context) {
        _autoPrompt.value = false
        when (_downloadState.value) {
            is UpdateDownloadState.Downloading,
            is UpdateDownloadState.Ready,
            is UpdateDownloadState.NeedPermission,
            is UpdateDownloadState.Failed -> cancelDownload(context)

            UpdateDownloadState.Idle -> Unit
        }
        if (_state.value !is UpdateUiState.Checking) {
            _state.value = UpdateUiState.Idle
        }
    }

    /** 切换更新通道时调用：旧通道的结果已经无意义。 */
    fun reset(context: Context) {
        checkJob?.cancel()
        checkJob = null
        _autoPrompt.value = false
        _state.value = UpdateUiState.Idle
        cancelDownload(context)
    }

    private fun discardDownloadedFiles(context: Context) {
        runCatching {
            File(context.filesDir, UpdateDownloader.UPDATE_DIR).listFiles()?.forEach { it.delete() }
        }
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────

    /** 系统异常（DNS、超时）的 message 是英文，只透出我们自己构造的可读文案。 */
    private fun Result<*>.failureMessage(fallback: String): String {
        val message = exceptionOrNull()?.message.orEmpty()
        return if (message.startsWith("GitHub")) message else fallback
    }
}

/**
 * 正式版 Release → 可用更新。
 *
 * 保持原有规则：草稿 / 预发布 / 版本不比当前新 / 已忽略都不提示。
 */
private fun GithubRelease.toStableUpdate(): UpdateInfo? {
    if (tagName.isBlank()) return null
    if (!isNewerVersion(tagName, BuildConfig.VERSION_NAME)) return null
    val asset = pickApkAsset(
        assets = assets,
        legacyEdition = BuildConfig.LEGACY_EDITION,
    )
    return UpdateInfo(
        channel = UpdateChannel.Stable,
        // 展示用版本号：把 tag 的 v 前缀去掉；解析不了就原样显示 tag
        versionName = parseReleaseVersion(tagName)?.toString() ?: tagName,
        identifier = tagName,
        releaseUrl = htmlUrl,
        // 挑不到匹配当前变体的安装包时降级：只给 Releases 页面地址
        downloadUrl = asset?.downloadUrl,
        downloadSize = asset?.size ?: 0L,
        notes = body,
    )
}

/**
 * Actions 运行 → 可用更新。
 *
 * 没有安装包直链：artifact 需要登录才能下载，这里只提供运行页面。
 */
private fun GithubActionsRun.toActionsUpdate(): UpdateInfo? {
    if (htmlUrl.isBlank() || runNumber <= 0) return null
    val newer = isActionsRunNewer(
        runNumber = runNumber,
        headSha = headSha,
        createdAtMs = parseIsoInstantMs(createdAt),
        localBuildNumber = BuildConfig.BUILD_NUMBER,
        localSha = BuildConfig.GIT_SHA,
        localBuildTimeMs = BuildConfig.BUILD_TIMESTAMP_MS,
    )
    if (!newer) return null
    return UpdateInfo(
        channel = UpdateChannel.Actions,
        versionName = "构建 #$runNumber",
        identifier = "run:$runNumber",
        releaseUrl = htmlUrl,
        downloadUrl = null,
        downloadSize = 0L,
        notes = buildString {
            append(displayTitle.ifBlank { "（无提交说明）" })
            append('\n')
            if (headSha.isNotBlank()) append(headSha.take(7))
            val time = formatBuildTime(createdAt)
            if (time.isNotBlank()) {
                if (headSha.isNotBlank()) append(" · ")
                append(time)
            }
        },
        runNumber = runNumber,
        headSha = headSha,
    )
}

/** GitHub 的 ISO 时间 → 本机时区的 `yyyy-MM-dd HH:mm`；解析失败返回空串（不显示）。 */
private fun formatBuildTime(raw: String): String {
    val millis = parseIsoInstantMs(raw)
    if (millis <= 0L) return ""
    return runCatching {
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrDefault("")
}
