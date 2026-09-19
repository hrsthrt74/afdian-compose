package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.stats.IncomeAmountMode
import com.hrsthrt74.afdian.data.stats.MonthlyBillRecord
import com.hrsthrt74.afdian.data.stats.amountFor
import com.hrsthrt74.afdian.data.stats.moneyText
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.EmptyStateView
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.MonthlySummaryViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 「每月汇总」页：创作者后台 /api/my/income 的可视化。
 *
 * 布局：按年份分节的扁平列表，每行是「月份 + 赞助者人数 + 当月收入」，右侧「感谢页」可点进该月感谢页；
 * 列表不显示账单单据的结算时间（create_time），只看月份与金额。
 *
 * 顶栏三点菜单切换金额口径：
 * - 抽成前金额（`total_amount`，默认）
 * - 抽成后金额（`creator_amount`）
 *
 * @param onBack 返回上一页
 * @param backStack miuix-nav 返回栈（返回按钮的滑动返回手势需要）
 * @param onOpenThankSponsors 点击某一行 → 打开该年月的赞助者感谢页
 * @param providedViewModel 复用外部创建的 ViewModel（外层导航 entry 传入）
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun MonthlySummaryScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    onOpenThankSponsors: (year: Int, month: Int) -> Unit = { _, _ -> },
    providedViewModel: MonthlySummaryViewModel? = null,
) {
    // AndroidViewModel 走 AndroidViewModelFactory（项目规则，参考 ManagementScreen）
    val application = LocalContext.current.applicationContext as Application
    val viewModel = providedViewModel ?: viewModel<MonthlySummaryViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val sections by viewModel.sections.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val amountMode by viewModel.amountMode.collectAsState()

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
            // 顶栏实时模糊：与其他页面同款写法（BlurredBar + blurBarColor 半透明底色）
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                TopAppBar(
                    title = "每月汇总",
                    largeTitle = "每月汇总",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        BackButton(onBack = onBack, backStack = backStack)
                    },
                    actions = {
                        // 右上角三点菜单：切换金额口径，选中项由 DropdownItem.selected 显示勾选态
                        WindowIconDropdownMenu(
                            entry = DropdownEntry(
                                items = IncomeAmountMode.entries.map { mode ->
                                    DropdownItem(
                                        text = mode.title,
                                        selected = amountMode == mode,
                                        onClick = { viewModel.setAmountMode(mode) },
                                    )
                                },
                            ),
                            // 加载中禁用，避免数据还没到手就切口径造成视觉抖动
                            enabled = !loading,
                        ) {
                            Icon(imageVector = MiuixIcons.More, contentDescription = "切换金额口径")
                        }
                    },
                )
            }
            }
        }
    ) { paddingValues ->
        // 采样层只包列表本身：列表从屏幕 (0,0) 铺到顶端，滚动内容绘制到 TopAppBar 底下供模糊采样
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding(),
                ),
            ) {
                when {
                    // 首次加载中
                    loading -> item(key = "loading") {
                        LoadingComponent(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    // 加载失败：空状态视图 + 重试按钮
                    error != null -> item(key = "error") {
                        EmptyStateView(
                            title = "加载失败",
                            summary = error!!,
                            modifier = Modifier.fillParentMaxSize(),
                            action = {
                                Button(onClick = viewModel::load, modifier = Modifier.fillMaxWidth()) {
                                    Text("重试")
                                }
                            },
                        )
                    }
                    // 空数据
                    sections.isEmpty() -> item(key = "empty") {
                        Text(
                            text = "暂无月度账单",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    // 正常列表：每年一个分节标题，下面跟着该年的各个月份行
                    else -> sections.forEach { section ->
                        item(key = "year-${section.year}") {
                            SmallTitle("${section.year}年")
                        }
                        items(section.bills, key = { "bill-${it.month}" }) { bill ->
                            MonthlySummaryRow(
                                bill = bill,
                                amountMode = amountMode,
                                onClick = { onOpenThankSponsors(bill.month.year, bill.month.monthValue) },
                            )
                        }
                    }
                }
                item(key = "bottom") { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // 右侧滚动条
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            )
        }
    }
}

/**
 * 单个月份行：左侧大号月份（主色）+ 中部「N 赞助者，¥xx.xx 收入」+ 右侧「感谢页」入口。
 * 整行可点，右侧文案只是视觉引子（点击区域更友好）。
 *
 * 结构与项目约定一致：Card 只当容器（背景 + 圆角），
 * 按压下沉反馈加在 Card 上，clickable 放在内部 Box 上，内边距由内部 Row 负责。
 */
@Composable
private fun MonthlySummaryRow(
    bill: MonthlyBillRecord,
    amountMode: IncomeAmountMode,
    onClick: () -> Unit,
) {
    // 提前取出颜色：buildAnnotatedString 的 lambda 不是 composable，不能在里面读主题
    val primary = MiuixTheme.colorScheme.primary
    val contentColor = MiuixTheme.colorScheme.onBackground

    Card(
        // 横向 16dp 与页面其余内容对齐，纵向 8dp 作为卡片间距
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            // 按压下沉反馈只加在 Card 上
            .pressable(interactionSource = null, indication = SinkFeedback(), delay = null),
    ) {
        // Card 只做容器，点击事件放在内部 Box
        Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧：月份，大号 + 主色
                Text(
                    text = "${bill.month.monthValue}月",
                    style = MiuixTheme.textStyles.title2,
                    color = primary,
                )
                Spacer(modifier = Modifier.width(10.dp))

                // 中部：人数加粗、金额主色，拼成一行信息
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(bill.sponsorCount.toString())
                        }
                        append(" 赞助者，")
                        withStyle(SpanStyle(color = primary)) {
                            // 按当前口径取抽成前 / 抽成后金额
                            append(moneyText(bill.amountFor(amountMode)))
                        }
                        append("收入")
                    },
                    style = MiuixTheme.textStyles.body1,
                    color = contentColor,
                )

                Spacer(modifier = Modifier.weight(1f))

                // 右侧：感谢页入口（文字 + 箭头）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "感谢页",
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        // 箭头尺寸沿用 Miuix ArrowPreference 的规格（10 x 16 dp）
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(width = 10.dp, height = 16.dp),
                    )
                }
            }
        }
    }
}
