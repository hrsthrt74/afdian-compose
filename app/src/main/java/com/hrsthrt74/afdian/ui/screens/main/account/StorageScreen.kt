package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.storage.HistoryPart
import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import com.hrsthrt74.afdian.data.storage.StorageCategory
import com.hrsthrt74.afdian.data.storage.StorageSnapshot
import com.hrsthrt74.afdian.data.storage.deviceUsageFraction
import com.hrsthrt74.afdian.data.storage.formatBytes
import com.hrsthrt74.afdian.data.storage.formatBytesParts
import com.hrsthrt74.afdian.data.storage.formatPercent
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
import com.hrsthrt74.afdian.ui.components.data.DonutChart
import com.hrsthrt74.afdian.ui.components.data.DonutSlice
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.StorageUiState
import com.hrsthrt74.afdian.ui.viewmodel.StorageViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

/**
 * 存储空间管理页。
 *
 * 顶部展示应用总占用与设备可用空间，下方按类列出各类占用大小：
 * 可清理的分类点进去弹破坏性确认框，不可清理的分类（登录凭证、设置项）只读展示。
 * 底部提供「清理全部缓存」，清理完成后自动重新扫描并回填数字。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    // AndroidViewModel 需要 Application，因此不能用默认工厂
    viewModel: StorageViewModel = viewModel(
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

    // 页面状态与「正在清理」集合
    val uiState by viewModel.uiState.collectAsState()
    val clearingIds by viewModel.clearingIds.collectAsState()
    // 图片缓存的体积上限（改动重启后生效，见 ImageCacheLimitCard 的说明文字）
    val imageCacheLimit by viewModel.imageCacheLimit.collectAsState()

    // 待清理的分类：整类清理走破坏性确认框，带可勾选子项的分类（「历史记录」）走勾选弹窗。
    // 两者共用这一个「待处理分类」状态，同一时刻只会有一个弹窗在显示。
    var pendingCategory by remember { mutableStateOf<StorageCategory?>(null) }
    val clearCategoryDialogState = rememberDialogState()
    val clearPartsDialogState = rememberDialogState()
    // 勾选弹窗里选中的子项；每次打开都重置为「全选」
    var selectedParts by remember { mutableStateOf<Set<HistoryPart>>(emptySet()) }
    // 一键清理弹窗状态
    val clearAllDialogState = rememberDialogState()

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "存储空间",
                        largeTitle = "存储空间",
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
                    is StorageUiState.Loading -> item {
                        // 首次扫描中：整屏居中，避免展示半截数字
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingComponent(text = "正在扫描存储…")
                        }
                    }

                    is StorageUiState.Error -> item {
                        // 扫描失败：预设样式（「加载失败」标题 + 重试按钮已内置）
                        ErrorStateView(
                            onRetry = viewModel::refresh,
                            modifier = Modifier.fillParentMaxSize(),
                            summary = state.message,
                        )
                    }

                    is StorageUiState.Content -> {
                        val snapshot = state.snapshot

                        // 总览：应用总占用 + 设备可用空间
                        item(key = "overview") { StorageOverviewCard(snapshot) }

                        item(key = "detail_title") {
                            SmallTitle(text = "占用明细", modifier = Modifier.padding(start = 4.dp))
                        }

                        // 明细：可清理行可点，不可清理行只读
                        item(key = "detail_card") {
                            StorageDetailCard(
                                snapshot = snapshot,
                                clearingIds = clearingIds,
                                onClearCategory = { category ->
                                    pendingCategory = category
                                    if (category.selectableParts.isEmpty()) {
                                        clearCategoryDialogState.show()
                                    } else {
                                        // 默认全选，用户可以取消勾选、只清其中一项
                                        selectedParts = category.selectableParts.toSet()
                                        clearPartsDialogState.show()
                                    }
                                },
                            )
                        }

                        item(key = "clear_all") {
                            // 一键清理：本页主操作，用填充主色的 Button 而非 TextButton。
                            // 全部可清理项都已为 0 或正在清理时禁用，避免无意义点击与并发。
                            val hasCleanableBytes = snapshot.categories
                                .any { it.category.cleanable && it.bytes > 0L }
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                    clearAllDialogState.show()
                                },
                                enabled = hasCleanableBytes && clearingIds.isEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp),
                            ) {
                                Text(if (clearingIds.isEmpty()) "清理全部缓存" else "正在清理…")
                            }
                        }

                        item(key = "policy_title") {
                            SmallTitle(text = "缓存策略", modifier = Modifier.padding(start = 4.dp))
                        }

                        item(key = "policy_card") {
                            ImageCacheLimitCard(
                                limit = imageCacheLimit,
                                onLimitChange = viewModel::setImageCacheLimit,
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

    // 单分类清理确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = clearCategoryDialogState,
        title = pendingCategory?.let { "清理「${it.title}」" }.orEmpty(),
        summary = pendingCategory?.let { "将清除${it.title}，清理后无法恢复。" }.orEmpty(),
        confirmText = "清理",
        destructive = true,
        // 关闭动画结束后再清掉临时选中态，避免标题在退场过程中被提前清空
        onDismissed = { pendingCategory = null },
        onConfirm = { pendingCategory?.let(viewModel::clearCategory) },
    )

    // 带可勾选子项的分类（「历史记录」）的勾选清理弹窗（破坏性：确认按钮变红）。
    // 打开时默认全选，用户可以取消勾选、只清其中一项。
    AppDialog(
        state = clearPartsDialogState,
        title = pendingCategory?.let { "清理「${it.title}」" }.orEmpty(),
        confirmText = "清理",
        destructive = true,
        // 一项都没勾时禁用确认：按钮变灰比「点了没反应」更能说明问题
        confirmEnabled = selectedParts.isNotEmpty(),
        onDismissed = { pendingCategory = null },
        onConfirm = { viewModel.clearHistoryParts(selectedParts) },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "选择要清理的内容，清理后无法恢复。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            // 勾选项包在 Card 里：与页面上的设置项观感一致，也把这一组从上面的说明文字里拎出来。
            // Card 自身不加 padding / 边距（DESIGN.md 约定），内容缩进交给勾选行自带的 16dp 内边距。
            Card(
                colors = CardColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                ),
            ) {
                pendingCategory?.selectableParts.orEmpty().forEach { part ->
                    CheckboxPreference(
                        title = part.label,
                        checked = part in selectedParts,
                        insideMargin = PaddingValues(16.dp, 12.dp),
                        onCheckedChange = { checked ->
                            selectedParts = if (checked) selectedParts + part else selectedParts - part
                        },
                    )
                }
            }
            // 与下方按钮留一点距离（AppDialog 自己还会再补 4dp）
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // 一键清理确认弹窗（破坏性：确认按钮变红）。
    // 文案明确承诺边界：不会退出登录、不会动设置项，避免用户顾虑。
    AppDialog(
        state = clearAllDialogState,
        title = "清理全部缓存",
        summary = "将清除图片缓存、历史记录与快照缓存，清理后无法恢复。",
        confirmText = "清理",
        destructive = true,
        onConfirm = viewModel::clearAllCaches,
    )
}

/** 环形图外径。 */
private val DonutDiameter = 180.dp

/** 环形图环厚。与 [DonutDiameter] 一起决定圆孔大小，也是圆心内容的可用宽度上限。 */
private val DonutThickness = 18.dp

/** 圆孔直径：圆心内容被约束在这个宽度内，避免超大字号时长数字压到圆环上。 */
private val DonutHoleWidth = DonutDiameter - DonutThickness * 2

/**
 * 分类标识色：环形图扇区与明细行圆点共用同一份，颜色按**分类**（而非扇区下标）绑定。
 */
private val StorageCategory.chartColor: Color
    get() = when (this) {
        StorageCategory.ImageCache -> Color(0xFF3B82F6)      // 蓝
        StorageCategory.History -> Color(0xFF22C55E)         // 绿
        StorageCategory.Snapshot -> Color(0xFFFFAB02)        // 橙
        StorageCategory.Logs -> Color(0xFF8B5CF6)            // 紫（日志，与蓝/绿/橙/灰都不撞色）
        StorageCategory.AppData -> Color(0xFF64748B)         // 石板灰蓝
        StorageCategory.Other -> Color(0xFF94A3B8)           // 中性灰
    }

/** 分类标识点：颜色与环形图扇区一致，用来建立「图表 ↔ 明细列表」的对应关系。 */
@Composable
private fun CategoryDot(category: StorageCategory) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(category.chartColor),
    )
}

/**
 * 总览卡片：环形占比图（圆心显示应用总占用）+ 设备可用空间。
 *
 * 环形图只表达「谁占得多」，具体名称与数值由下方明细列表给出，两边靠 [chartColor] 同色对应，
 * 因此图上不画标签也不需要图例。
 */
@Composable
private fun StorageOverviewCard(snapshot: StorageSnapshot) {
    // 扇区：按占用降序，只保留有数据的分类；颜色按分类固定，与明细行一致
    val slices = remember(snapshot) {
        snapshot.categories
            .filter { it.bytes > 0L }
            .sortedByDescending { it.bytes }
            .map { usage -> DonutSlice(value = usage.bytes.toFloat(), color = usage.category.chartColor) }
    }

    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        DonutChart(
            slices = slices,
            diameter = DonutDiameter,
            thickness = DonutThickness,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        ) {
            // 圆心：本页最重要的数字。原先独立的「应用总占用」行已合并到这里，
            // 避免同一个数字在卡片里出现两次。
            // 宽度锁在圆孔内：系统字号调大时长数字会省略号收尾，而不会盖住圆环。
            Column(
                modifier = Modifier.width(DonutHoleWidth - 12.dp).padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "应用总占用",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 数值与单位拆成两段排版：数值用大字号，单位用 footnote 弱化并对齐基线。
                // 整串按同一字号渲染会又宽、又让单位喧宾夺主（和 DataCard 的「大数值 + 小单位」一致）。
                val (totalNumber, totalUnit) = formatBytesParts(snapshot.totalBytes)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = totalNumber,
                        modifier = Modifier
                            // 给数值留出宽度上限：字号极大时它自己省略号收尾，
                            // 而不会把右边的单位挤成 0 宽（Row 里无 weight 的子项是按顺序吃剩余宽度的）
                            .widthIn(max = DonutHoleWidth - 36.dp)
                            .alignByBaseline(),
                        style = MiuixTheme.textStyles.title1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = totalUnit,
                        modifier = Modifier.alignByBaseline(),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            }
        }
        // 应用占用占设备总容量的比重：右侧是百分比 + 环形进度
        val usageFraction = deviceUsageFraction(snapshot.totalBytes, snapshot.deviceTotalBytes)
        BasicComponent(
            title = "占用设备存储",
            summary = "${formatBytes(snapshot.totalBytes)} / ${formatBytes(snapshot.deviceTotalBytes)}",
            endActions = {
                // 百分比文案
                Text(
                    text = formatPercent(usageFraction),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Spacer(modifier = Modifier.width(8.dp).align(Alignment.CenterVertically))
                // 环形进度：应用占用 / 设备总容量。颜色沿用主题主色，是「占比」而非「分类」语义
                CircularProgressIndicator(
                    progress = usageFraction,
                    size = 24.dp,
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            },
        )
    }
}

/**
 * 占用明细卡片。
 *
 * 可清理项按占用降序（最大的一项一眼可见），不可清理项固定置底。
 * 两类行的视觉区分：可清理用 [ArrowPreference]（自带右箭头，表达「点击会弹确认框」），
 * 不可清理用 [BasicComponent] 且不传 `onClick`（无箭头、不可点）。
 *
 * 每行左侧带一个与环形图扇区同色的标识点，靠颜色而非文字图例把图表和列表对应起来。
 */
@Composable
private fun StorageDetailCard(
    snapshot: StorageSnapshot,
    clearingIds: Set<String>,
    onClearCategory: (StorageCategory) -> Unit,
) {
    val cleanable = snapshot.categories
        .filter { it.category.cleanable }
        .sortedByDescending { it.bytes }
    val readonly = snapshot.categories.filterNot { it.category.cleanable }
    val haptic = LocalHapticFeedback.current

    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        cleanable.forEach { usage ->
            val clearing = usage.category.id in clearingIds
            ArrowPreference(
                title = usage.category.title,
                summary = usage.category.summary,
                // 清理进行中禁用本行，防止重复触发
                enabled = !clearing,
                // 分类标识点：颜色与环形图扇区一致
                startAction = { CategoryDot(usage.category) },
                endActions = {
                    // 占用大小右对齐贴近箭头；清理中则给出明确的过程反馈
                    Text(
                        text = if (clearing) "清理中…" else formatBytes(usage.bytes),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    onClearCategory(usage.category)
                },
            )
        }

        readonly.forEach { usage ->
            // 不可清理行：不给 onClick，因此既无箭头也无法点击
            BasicComponent(
                title = usage.category.title,
                summary = usage.category.summary,
                // 分类标识点：颜色与环形图扇区一致
                startAction = { CategoryDot(usage.category) },
                endActions = {
                    Text(
                        text = formatBytes(usage.bytes),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                },
            )
        }
    }
}

/** 拖动中的浮点值 → 档位下标。Slider 的 steps 会把值吸附到整数，这里再兜一层防止越界。 */
private fun sliderIndexToLimit(value: Float): ImageCacheLimit =
    ImageCacheLimit.entries[value.roundToInt().coerceIn(0, ImageCacheLimit.entries.lastIndex)]

/**
 * 图片缓存上限设置：Card 内一个 4 档滑块，Card 下面是说明文字。
 *
 * 说明文字是必须的，它交代两件用户猜不到的事：**只作用于图片缓存**、**重启后才生效**。
 */
@Composable
private fun ImageCacheLimitCard(
    limit: ImageCacheLimit,
    onLimitChange: (ImageCacheLimit) -> Unit,
) {
    // 拖动中的本地值：只在松手时落盘，避免每一帧都写一次 DataStore。
    // 以 limit 为 key，外部值变化（首次读出 / 别处改动）时重新同步。
    var sliderValue by remember(limit) { mutableFloatStateOf(limit.ordinal.toFloat()) }

    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column{
            SliderPreference(
                title = "缓存大小限制",
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    val picked = sliderIndexToLimit(sliderValue)
                    if (picked != limit) onLimitChange(picked)
                },
                // 4 个档位 = 值域 0..3，中间的离散值有 2 个（steps 指的是两个端点之间的档位数）
                valueRange = 0f..3f,
                // coerceAtLeast(0) 是防御：日后档位砍到 2 个以下时 steps 会变负，而它要求 >= 0
                steps = (ImageCacheLimit.entries.size - 2).coerceAtLeast(0),
                // 故意不传 keyPoints：miuix 的 Step 震动在传了 keyPoints 时走"边沿触发"逻辑
                // （只在滑块从"不在关键点"进入"关键点"的那一帧震动）。这里 4 个关键点恰好
                // 覆盖了全部可吸附刻度，吸附后的值每一帧都精确停在关键点上，边沿永远不会
                // 出现，导致 Step 失效只剩 Edge。不传 keyPoints 则走"值每变一档震一次"的
                // 分支，才是想要的逐档震动。showKeyPoints = true 时库内部改用 steps 算出的
                // 均匀刻度画点，视觉与之前传 keyPoints 完全一致。
                showKeyPoints = true,
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                insideMargin = PaddingValues(16.dp, 12.dp, 16.dp, 8.dp)
            )

            // 刻度标签
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ImageCacheLimit.entries.forEachIndexed { index, entry ->
                    val selected = index == sliderIndexToLimit(sliderValue).ordinal
                    Text(
                        text = entry.label,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        fontWeight = if (selected) FontWeight.Medium else null,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    Text(
        text = "超出上限后会自动删掉最早缓存的图片，" +
            "历史记录、快照等数据不受影响。改动需重启应用后生效。",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 28.dp).padding(top = 12.dp, bottom = 12.dp),
    )
}
