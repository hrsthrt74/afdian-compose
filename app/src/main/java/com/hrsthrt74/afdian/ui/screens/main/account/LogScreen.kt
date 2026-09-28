package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.storage.formatBytes
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.ErrorStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.LogUiState
import com.hrsthrt74.afdian.ui.viewmodel.LogViewModel
import java.io.File
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 日志页：运行日志与崩溃报告的「记录开关 → 占用查看 → 导出 → 清空」一站式管理。
 *
 * 分工：
 * - 「写入日志文件」控制运行日志（app-*.log）是否落盘，按天保留 3 天；
 * - 崩溃报告（crash-*.log）**始终记录**、不受开关控制——它是闪退排查的最后防线；
 * - 「调试日志」开关从设置页「关于」分组迁移至此：它与日志导出是同一套排查工作流。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun LogScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    // AndroidViewModel 需要 Application，因此不能用默认工厂
    viewModel: LogViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 页面状态与开关
    val uiState by viewModel.uiState.collectAsState()
    val fileLogging by viewModel.fileLogging.collectAsState()
    val debugLogging by viewModel.debugLogging.collectAsState()

    // 导出进行中标志：分享 / 保存共用，防连点重复生成导出文件
    var exporting by remember { mutableStateOf(false) }
    // 清空确认弹窗状态
    val clearDialogState = rememberDialogState()

    // 「保存到本地」：SAF 另存为（对齐 IncomeDetailScreen 的 CSV 导出实现），
    // 用户选定位置后在回调里合并日志并写入 uri；无需任何存储权限
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        // 用户取消了文件选择器时 uri 为 null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val ok = viewModel.saveTo(uri)
            exporting = false
            Toast.makeText(context, if (ok) "已保存" else "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "日志",
                        largeTitle = "日志",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = contentBottomPadding(),
                ),
            ) {
                when (val state = uiState) {
                    is LogUiState.Loading -> item {
                        // 首次统计中：整屏居中，避免展示半截数字
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingComponent(text = "正在统计日志…")
                        }
                    }

                    is LogUiState.Error -> item {
                        // 统计失败：预设样式（「加载失败」标题 + 重试按钮已内置）
                        ErrorStateView(
                            onRetry = viewModel::refresh,
                            modifier = Modifier.fillParentMaxSize(),
                            summary = state.message,
                        )
                    }

                    is LogUiState.Content -> {
                        val hasLogs = state.fileCount > 0

                        item(key = "record_title") {
                            SmallTitle(text = "记录", modifier = Modifier.padding(start = 4.dp))
                        }
                        item(key = "record_card") {
                            // 记录开关组：两个开关同属「记什么」这一个主题
                            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                                SwitchPreference(
                                    checked = fileLogging,
                                    onCheckedChange = viewModel::setFileLogging,
                                    title = "写入日志文件",
                                    summary = "运行日志按天写入本地，保留最近 3 天",
                                )
                                SwitchPreference(
                                    checked = debugLogging,
                                    onCheckedChange = { enabled ->
                                        viewModel.setDebugLogging(enabled)
                                        // 开关状态仅在进程启动时读取一次，运行中切换不会即时生效，切换后主动提醒
                                        Toast.makeText(context, "重启应用后生效", Toast.LENGTH_SHORT).show()
                                    },
                                    title = "调试日志",
                                    summary = "额外记录网络请求、耗时等详细日志，重启应用后生效",
                                )
                            }
                        }

                        item(key = "status_title") {
                            SmallTitle(text = "状态", modifier = Modifier.padding(start = 4.dp))
                        }
                        item(key = "status_card") {
                            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                                // 占用大小：右侧数值与存储页明细行同一格式
                                BasicComponent(
                                    title = "占用空间",
                                    summary = "日志文件总大小",
                                    endActions = {
                                        Text(
                                            text = formatBytes(state.totalBytes),
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        )
                                    },
                                )
                                // 文件数：运行日志 + 崩溃报告 + 导出文件
                                BasicComponent(
                                    title = "日志文件",
                                    summary = "崩溃报告始终记录，不受开关控制",
                                    endActions = {
                                        Text(
                                            text = "${state.fileCount} 个",
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        )
                                    },
                                )
                            }
                        }

                        item(key = "export_title") {
                            SmallTitle(text = "导出", modifier = Modifier.padding(start = 4.dp))
                        }
                        item(key = "export_card") {
                            // 无日志时禁用导出：灰色不可点比「点了弹 Toast」更能说明状态
                            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                                ArrowPreference(
                                    title = "分享日志",
                                    summary = "合并全部日志，通过分享面板发送",
                                    enabled = hasLogs && !exporting,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch {
                                            exporting = true
                                            // 在 IO 线程合并日志并生成导出文件（带变体名与时间戳）
                                            val file = viewModel.buildExportFile()
                                            exporting = false
                                            if (file == null) {
                                                Toast.makeText(context, "导出失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                            } else {
                                                shareLogFile(context, file)
                                            }
                                            // 分享会在 logs/ 下留下导出文件，占用变了，重扫一次
                                            viewModel.refreshAfterExport()
                                        }
                                    },
                                )
                                ArrowPreference(
                                    title = "保存到本地",
                                    summary = "合并全部日志，另存为 log 文件",
                                    enabled = hasLogs && !exporting,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // 拉起系统文件选择器；写入在回调里完成
                                        saveLauncher.launch(viewModel.suggestedExportName())
                                    },
                                )
                            }
                        }

                        item(key = "clear_all") {
                            TextButton(
                                text = if (exporting) "正在导出…" else "清空日志",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                    clearDialogState.show()
                                },
                                enabled = hasLogs && !exporting,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }

                        item(key = "hint") {
                            // 说明小字：交代用户猜不到的规则（保留策略 / 一键清理的影响 / 清空后重建 / 脱敏）
                            Text(
                                text = "崩溃报告始终记录（最多保留 10 份）。" +
                                    "「清理全部缓存」也会一并清掉日志。" +
                                    "开关开启期间，清空后新日志会立即重新记录。" +
                                    "日志写入前会自动抹掉敏感信息（auth_token、Cookie 等）。",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 12.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            }

            // 右侧滚动条
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                ),
            )
        }
    }

    // 清空确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = clearDialogState,
        title = "清空日志",
        summary = "将删除全部运行日志与崩溃报告，无法恢复。",
        confirmText = "清空",
        destructive = true,
        onConfirm = viewModel::clearLogs,
    )
}

/**
 * 通过系统分享面板发送导出文件。
 *
 * 细节：mimeType 用 text/plain（octet-stream 会被微信等拒收）；
 * ClipData 携带 uri + FLAG_GRANT_READ_URI_PERMISSION 双保险——
 * createChooser 场景下部分系统不会把 intent 的 grant 传递给目标 app，缺 ClipData 会 Permission Denial。
 */
private fun shareLogFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        // 关键：chooser 传递读权限的兜底通道
        clipData = ClipData.newRawUri("log", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "导出日志"))
}
