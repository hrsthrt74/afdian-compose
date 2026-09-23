package com.hrsthrt74.afdian.ui.screens.main.management

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.stats.IncomeStatRecord
import com.hrsthrt74.afdian.data.stats.IncomeStatType
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.EmptyStateView
import com.hrsthrt74.afdian.ui.components.basic.LoadingComponent
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.IncomeDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDate

/**
 * 「收入明细」页：创作者后台 /api/my/stat 的可视化。
 * 顶部固定 按日/按周/按月 TabRow + 表头，下方是 6 列统计表格，滚到底自动翻页。
 */
@Composable
fun IncomeDetailScreen(onBack: () -> Unit, backStack: NavBackStack, providedViewModel: IncomeDetailViewModel? = null) {
    // 数据
    val application = LocalContext.current.applicationContext as Application
    val viewModel = providedViewModel ?: viewModel<IncomeDetailViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val type by viewModel.type.collectAsState()
    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    // 页面
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val listState = rememberLazyListState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val haptic = LocalHapticFeedback.current

    // 「问 AI」/「导出数据」的全量拉取进行中
    val working by viewModel.working.collectAsState()

    // 「问 AI」确认弹窗
    val aiDialogState = rememberDialogState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // CSV 导出：SAF 另存为，用户选定位置后回调；文本在回调里拉取并写入
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        // 用户取消了文件选择器时 uri 为 null
        if (uri == null) return@rememberLauncherForActivityResult
        toast("正在导出…")
        viewModel.requestCsv { result ->
            result.onSuccess { text ->
                scope.launch {
                    // 文件写在 IO 线程，完成后回主线程提示
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(text.toByteArray(Charsets.UTF_8))
                            } != null
                        }.getOrDefault(false)
                    }
                    toast(if (ok) "导出成功" else "导出失败")
                }
            }.onFailure {
                toast("导出失败：${it.message ?: "未知错误"}")
            }
        }
    }

    Scaffold(
        topBar = {
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, progressive = false) {
                Column{
                    // 顶栏
                    TopAppBar(
                        title = "收入明细",
                        largeTitle = "收入明细",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                        actions = {
                            // 右上角动作菜单：问 AI / 导出数据；全量拉取期间禁用防止重复触发
                            WindowIconDropdownMenu(
                                entry = DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "问 AI",
                                            onClick = { aiDialogState.show() },
                                        ),
                                        DropdownItem(
                                            text = "导出数据",
                                            onClick = {
                                                // 导出文件                 ↓文件名
                                                csvLauncher.launch("爱发电_收入明细_${type.title}_${LocalDate.now()}.csv")
                                            },
                                        ),
                                    ),
                                ),
                                enabled = !working && !loading,
                            ) {
                                Icon(imageVector = MiuixIcons.More, contentDescription = "更多操作")
                            }
                        },
                    )
                    // 按日/按周/按月切换 Tab，切换即换 type 重新请求第 1 页
                    TabRowWithContour(
                        tabs = IncomeStatType.entries.map { it.title },
                        selectedTabIndex = IncomeStatType.entries.indexOf(type),
                        onTabSelected = { index ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.switchType(IncomeStatType.entries[index])
                        },
                        colors = TabRowDefaults.tabRowColors(backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                    )
                    // 表头
                    TableHeader(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                }
            }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = (contentBottomPadding() + 48.dp),
                    ),
                ) {
                when {
                    // 首次加载中
                    loading -> item {
                        LoadingComponent(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    // 加载失败：空状态视图 + 重试按钮
                    error != null -> item {
                        EmptyStateView(
                            title = "加载失败",
                            summary = error!!,
                            modifier = Modifier.fillParentMaxSize(),
                            action = {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { viewModel.retry() },
                                ) { Text("重试") }
                            },
                        )
                    }
                    // 空数据
                    items.isEmpty() -> item {
                        Text(
                            text = "暂无统计数据",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    // 正常表格：整张表包在一个大 Card 里，行间用 HorizontalDivider 分割
                    else -> {
                        item(key = "table") {
                            Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Column {
                                    items.forEachIndexed { index, record ->
                                        // 每行（连同分割线）淡入出现：翻页追加的新行会有渐入效果
                                        FadeInContainer {
                                            // 第一行上方不需要分割线
                                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                            TableRow(record)
                                        }
                                    }
                                }
                            }
                        }
                        // 底部自动翻页：该占位 item 进入组合即触发 loadMore
                        if (hasMore) {
                            item(key = "load_more") {
                                LaunchedEffect(items.size) { viewModel.loadMore() }
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (loadingMore) "加载中…" else "",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    // 「问 AI」确认弹窗：确认后全量拉取 → 复制 Markdown 表格
    AppDialog(
        state = aiDialogState,
        title = "问 AI",
        summary = "将当前维度的统计数据并与已写好的提示词一起复制，可直接粘贴给 AI 分析。\n" +
                  "全量拉取数据可能需要几秒。",
        confirmText = "复制",
        // 拉取期间禁用确认按钮，防止重复触发
        confirmEnabled = !working,
        onConfirm = {
            toast("正在拉取数据…")
            viewModel.requestAiText { result ->
                result.onSuccess { text ->
                    clipboard.setPrimaryClip(ClipData.newPlainText("收入明细", text))
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    toast("已复制，去问 AI 吧~")
                }.onFailure {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    toast("拉取失败：${it.message ?: "未知错误"}")
                }
            }
        },
    )
}

/** 表格列的宽度分配 */
private val ColumnWeights = listOf(1.4f, 1f, 1f, 1f, 1f, 0.8f)

/** 固定表头行：六列统计口径标题。 */
@Composable
private fun TableHeader(modifier: Modifier = Modifier) {
    val titles = listOf("日期", "访问人数", "发电金额", "发电次数", "发电人数", "老客数")
    Row(modifier = modifier.fillMaxWidth()) {
        titles.forEachIndexed { index, title ->
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(ColumnWeights[index]),
            )
        }
    }
}

/**
 * 淡入容器：内容首次进入组合时透明度从 0 渐变到 1。
 * 表格行用它实现「新条目淡入出现」——首次加载、切 tab、翻页追加时都会生效。
 */
@Composable
private fun FadeInContainer(content: @Composable () -> Unit) {
    // Animatable 起始全透明，进入组合后动画到完全不透明
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 300))
    }
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value }) {
        content()
    }
}

/** 表格数据行：一行一条统计记录，金额保留两位小数；外层 Card 由调用方统一提供。 */
@Composable
private fun TableRow(record: IncomeStatRecord) {
    // 格式化金额：直接用 BigDecimal 的两位小数字符串（接口本身形如 13.99）
    val cells = listOf(
        record.displayDate,
        record.uv.toString(),
        record.amount.toPlainString(),
        record.orderCount.toString(),
        record.userCount.toString(),
        record.oldUserCount.toString(),
    )
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontFeatureSettings = "tnum"
                ),
                // 日期列用次级文字色弱化，其余数值列用默认前景色
                color = if (index == 0) MiuixTheme.colorScheme.onSurfaceVariantSummary else Color.Unspecified,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(ColumnWeights[index]),
            )
        }
    }
}
