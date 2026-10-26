package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.update.UpdateChannel
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.components.update.UpdateDialog
import com.hrsthrt74.afdian.ui.components.update.openUpdateUrl
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.navigation.openExternalLink
import com.hrsthrt74.afdian.ui.navigation.rememberCustomTabsBranding
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import com.hrsthrt74.afdian.ui.viewmodel.UpdateDownloadState
import com.hrsthrt74.afdian.ui.viewmodel.UpdateViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 项目 GitHub 仓库。
 *
 * 源码、Issue 反馈与 Release 安装包都在这里；正式版检查更新读的也是同一个仓库
 * （见 `data/update/GithubReleaseService.kt`）。
 */
private const val GITHUB_REPO_SLUG = "hrsthrt74/afdian-compose"

/** GitHub 仓库网页地址。 */
private const val GITHUB_REPO_URL = "https://github.com/$GITHUB_REPO_SLUG"

/**
 * 交流群入群链接（QQ 群）。
 *
 * 群号在 [QQ_GROUP_NUMBER]，点击入口时一并复制，方便用户直接粘贴到 QQ 里搜索加群。
 */
private const val QQ_GROUP_JOIN_URL = "https://qm.qq.com/q/YVBgTJLi26"

/** 交流群群号：点击入口时复制到剪贴板。 */
private const val QQ_GROUP_NUMBER = "1108445904"

// 更新通道：下标必须与 UpdateChannel 的声明顺序（Stable / Actions）严格一致，
// 下拉直接拿 enum 的 ordinal 当下标用。
private val updateChannelNames = listOf("正式版", "Actions 构建")

/**
 * 爱发电官方文档站域名。
 *
 * **刻意不跟随「优先线路」**：另外两个线路域名（ifdian.net / www.afdian.com）下
 * 并没有 guide 子域，把 host 跟着线路改写会直接 404。
 */
private const val AFDIAN_GUIDE_HOST = "guide.afdian.com"

/** 爱发电官方文档站地址（写死域名，不走 `afdianWebUrl()`）。 */
private const val AFDIAN_GUIDE_URL = "https://$AFDIAN_GUIDE_HOST"

/**
 * 设置 → 关于。
 *
 * 页内含原设置页「关于」分组与「检查更新」卡片：
 * - 站外入口：交流群、GitHub、开源许可、免责声明、功能完成度；
 * - 更新：检查更新、更新通道；
 * - 页脚：版本号、构建类型（Actions 包额外带构建号与 commit）。
 *
 * 页面标题已是「关于」，因此不再显示同名小标题。版本页脚从旧设置页底部搬到这里，
 * README 里「反馈请附设置页底部版本号」的说法也随之改成「设置 → 关于」。
 */
@Composable
fun SettingsAboutScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    // 检查更新：手动检查用的 ViewModel 与弹窗状态。
    // 状态实际存在进程级 UpdateCenter 里，两个入口（根部自动检查 / 这里的手动检查）
    // 看到的是同一份结果；key 仍然保留，只是不再用来隔离状态了。
    val updateViewModel: UpdateViewModel = viewModel(
        key = "update-manual",
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
    val updateState by updateViewModel.uiState.collectAsState()
    val updateDownloadState by updateViewModel.downloadState.collectAsState()
    // 更新通道：下拉选择后立即落盘，下一次检查（含冷启动自动检查）就按新通道走
    val updateChannel by viewModel.updateChannel.collectAsState()
    val preferOfficialClient by viewModel.preferOfficialClient.collectAsState()
    val updateDialogState = rememberDialogState()
    // 打开下载链接 / Actions 运行页面用的 Context
    val context = LocalContext.current
    // 品牌化 Custom Tabs 参数：本页不在 ThemeColorTransition 内，直接构建即可
    val customTabsBranding = rememberCustomTabsBranding()

    // 版本页脚：Actions 包额外显示构建号与 commit，方便用户报 issue 时直接截图
    val versionDetail = buildString {
        if (BuildConfig.BUILD_NUMBER > 0) append(" · 构建 #${BuildConfig.BUILD_NUMBER}")
        if (BuildConfig.GIT_SHA.isNotBlank()) append(" · ${BuildConfig.GIT_SHA}")
    }

    // 离开关于页时丢弃「正在下载」的进度：更新弹窗是页面级的，页面没了就没有任何进度入口。
    // 已下载完 / 待授权的包不能删 —— 系统安装器正通过 FileProvider 读它，
    // 在「不保留活动」的设备上删掉会让安装莫名失败。
    DisposableEffect(Unit) {
        onDispose {
            if (updateViewModel.downloadState.value is UpdateDownloadState.Downloading) {
                updateViewModel.cancelDownload()
            }
        }
    }

    SettingsPageScaffold(
        title = "关于",
        onBack = onBack,
        backStack = backStack,
    ) {
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                // 爱发电官方文档：地址写死 guide.afdian.com，**不跟「优先线路」走** ——
                // 另外两个域名（ifdian.net / www.afdian.com）下并没有 guide 子域，
                // 跟着线路改写 host 会直接 404，所以这里刻意不走 afdianWebUrl()。
                ArrowPreference(
                    title = "爱发电文档",
                    onClick = { openExternalLink(context, AFDIAN_GUIDE_URL, preferOfficialClient, customTabsBranding) },
                )
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                // 交流群：一次点击干两件事 —— 打开入群链接，同时把群号复制到剪贴板。
                // 群号单独复制是为了照顾已经在 QQ 里、懒得切到网页再点「加入群聊」的用户。
                ArrowPreference(
                    title = "交流群",
                    summary = "本第三方客户端的交流群",
                    onClick = {
                        AppLog.d("Settings", "打开交流群并复制群号: $QQ_GROUP_JOIN_URL")
                        copyTextToClipboard(context, QQ_GROUP_NUMBER, "群号", "群号已复制")
                        openExternalLink(context, QQ_GROUP_JOIN_URL, preferOfficialClient, customTabsBranding)
                    },
                )
                // 项目源码仓库
                ArrowPreference(
                    title = "GitHub",
                    summary = GITHUB_REPO_SLUG,
                    onClick = { openExternalLink(context, GITHUB_REPO_URL, preferOfficialClient, customTabsBranding) },
                )
            }
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                ArrowPreference(
                    title = "开源许可",
                    onClick = { if (backStack.none { it == Route.OssLicenses }) backStack.add(Route.OssLicenses) },
                )
                ArrowPreference(
                    title = "免责声明",
                    onClick = { if (backStack.none { it == Route.Disclaimer }) backStack.add(Route.Disclaimer) },
                )
                // 功能完成度：常驻的「当前尚未完成的功能」清单入口。
                ArrowPreference(
                    title = "功能完成度",
                    summary = "当前尚未完成的功能清单",
                    onClick = { if (backStack.none { it == Route.FeatureStatus }) backStack.add(Route.FeatureStatus) },
                )
            }
        }

        item {
            Card( modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp) ) {
                // 检查更新：手动查一次，有新版本会给出「忽略此版本 / 稍后 / 去下载」三个出口
                ArrowPreference(
                    title = "检查更新",
                    onClick = {
                        updateViewModel.checkManually()
                        updateDialogState.show()
                    },
                )
                // 更新通道：正式版走 Release 直链（应用内下载安装）；
                // Actions 构建追 master 的 CI 产物（http 页面跳转，artifact 需登录才能下）
                OverlayDropdownPreference(
                    title = "更新通道",
                    items = updateChannelNames,
                    selectedIndex = updateChannel.ordinal,
                    onSelectedIndexChange = { index ->
                        viewModel.setUpdateChannel(UpdateChannel.entries[index])
                    },
                )
            }
        }

        // 版本号：用户报问题时最常被问到的信息，显示在「关于」页底部。
        // 样式沿用 DESIGN.md「列表内小字」的统一约定
        item {
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})$versionDetail",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
            )
        }

        // 构建类型提示：居中小字显示当前打包变体（如 standardRelease），
        // 便于区分测试包来源；样式沿用 DESIGN.md「列表内小字」的统一约定
        item {
            Text(
                text = "构建类型：" + BuildConfig.APP_VARIANT_NAME,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }

    // 检查更新弹窗：检查结果与下载进度两条流都在这一个弹窗里呈现
    UpdateDialog(
        state = updateDialogState,
        uiState = updateState,
        downloadState = updateDownloadState,
        onIgnore = { updateViewModel.ignoreCurrent() },
        onOpenUrl = { url -> openUpdateUrl(context, url) },
        onDownload = { info -> updateViewModel.download(info) },
        onInstall = { updateViewModel.install() },
        onCancelDownload = { updateViewModel.cancelDownload() },
        onOpenInstallPermission = { updateViewModel.openInstallPermission() },
        // 关闭后把「有更新」这个状态复位：它是「结果」不是「事件」，
        // 不复位的话旋转屏幕等重组会再次把弹窗拉起来
        onDismissed = { updateViewModel.consumeAvailable() },
    )
}

/**
 * 把文本写入系统剪贴板，失败时只记日志、不打断调用方。
 *
 * 抄 `MessageBubbleMenu.copyToClipboard` 的写法：`setPrimaryClip` 在部分机型上会抛异常，
 * 这里兜住避免崩到顶层；复制成功才弹 Toast，失败时给用户一点提示也没有。
 *
 * @param context 上下文
 * @param text 要复制的文本
 * @param clipLabel 剪贴板 ClipData 的描述标签（系统「剪贴板历史」里显示的来源名）
 * @param toastText 复制成功的 Toast 文案
 */
private fun copyTextToClipboard(
    context: Context,
    text: String,
    clipLabel: String,
    toastText: String,
) {
    val copiedOk = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
    }.onFailure { throwable ->
        AppLog.w("Settings", "复制到剪贴板失败（$clipLabel）", throwable)
    }.isSuccess
    if (copiedOk) {
        AppLog.d("Settings", "复制成功（$clipLabel 长度=${text.length}）")
        Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
    }
}
