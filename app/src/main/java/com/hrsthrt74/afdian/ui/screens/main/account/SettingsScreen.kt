package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.settings.KeyColorPreset
import com.hrsthrt74.afdian.data.settings.resolveForSdk
import com.hrsthrt74.afdian.ui.components.DataCard
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.theme.ThemeColorTransition
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// 「启动」页 下拉菜单选项
private val startupPageNames = listOf(
    DropdownItem(
        icon = { Icon(MiuixIcons.Home, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "首页"
    ),
    DropdownItem(
        icon = { Icon(MiuixIcons.Search, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "发现"
    ),
    DropdownItem(
        icon = { Icon(MiuixIcons.Community, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "消息"
    ),
    DropdownItem(
        icon = { Icon(MiuixIcons.Contacts, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "我的"
    ),
)
// 配色模式选项，索引与 SettingsStore 的 0/1/2 约定一致
private val colorModeNames = listOf("跟随系统", "浅色", "深色")

/**
 * 「跟随系统壁纸」那一项的颜色球用的色轮。
 *
 * 这一档没有固定色值（颜色由系统壁纸决定），用一圈彩虹来区别于其它纯色球，
 * 暗示「这里出来的是任意颜色」；直径与纯色球一致，保证下拉各行左侧对齐。
 */
private val SystemWallpaperBrush = Brush.sweepGradient(
    colors = listOf(
        Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047),
        Color(0xFF00ACC1), Color(0xFF3949AB), Color(0xFF8E24AA), Color(0xFFE53935),
    ),
)

/** 下拉里颜色球的直径；比 Miuix 图标格的最小尺寸（26dp）小一圈，能居中留出呼吸感。 */
private val KeyColorBallSize = 20.dp

/**
 * 下拉项前面的「颜色球」。
 *
 * [color] 为 null 表示「跟随系统壁纸」，此时画 [SystemWallpaperBrush] 色轮。
 * [modifier] 由 Miuix 传入（最小 26dp 的图标格 + 末尾 12dp 间距），直接套在外层 Box 上即可，
 * 不需要自己再补与文字的间距。
 */
@Composable
private fun KeyColorBall(color: Color?, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val ballModifier = Modifier.size(KeyColorBallSize)
        // 颜色球本体：固定种子色铺纯色，跟随系统壁纸铺色环
        if (color != null) {
            Box(modifier = ballModifier.background(color = color, shape = CircleShape))
        } else {
            Box(modifier = ballModifier.background(brush = SystemWallpaperBrush, shape = CircleShape))
        }
    }
}

/**
 * 把种子色预设转成 Spinner 的下拉项，每项前面带一个该预设的颜色球。
 *
 * 用 Spinner 而不是普通 Dropdown 就是为了这个 icon 槽位：它的 [DropdownItem] 支持
 * 在选项文字前面插内容（见 Miuix `Dropdown.kt` 里的 `item.icon`）。
 */
private fun buildKeyColorItems(presets: List<KeyColorPreset>): List<DropdownItem> =
    presets.map { preset ->
        DropdownItem(
            text = preset.label,
            icon = { cellModifier -> KeyColorBall(color = preset.color, modifier = cellModifier) },
        )
    }

/**
 * 设置页
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    // 登出回调：由外层（MainScreen）传入，点击底部「退出登录」时触发
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    /**
     * 内容整体包在 [ThemeColorTransition] 里：本页是唯一能改配色的地方，切「配色模式」
     * 或「使用爱发电主题色」时整页颜色渐变过去，而不是瞬间换色。模糊取色也在包裹范围内，
     * 顶栏才能跟着一起渐变。其他页面保持原来的瞬间生效（切设置时它们看不见，没必要每帧重算）。
     */
    ThemeColorTransition {
        SettingsScreenContent(
            onBack = onBack,
            backStack = backStack,
            onLogout = onLogout,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun SettingsScreenContent(
    onBack: () -> Unit,
    backStack: NavBackStack,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel,
) {
    // 设置
    val startupPage by viewModel.startupPage.collectAsState()
    val useAfdianColor by viewModel.useAfdianColor.collectAsState()
    val colorMode by viewModel.colorMode.collectAsState()
    val keyColor by viewModel.keyColor.collectAsState()
    // Monet 相关 UI（开关勾选态、「使用爱发电主题色」的显隐）一律以「全局主题真正生效的模式」为准，
    // 而不是读页面自己的 SettingsViewModel 流：VM 是进入设置页时才创建的，初值固定是「Monet 关闭」，
    // DataStore 读回来之前会先按这个值渲染一帧，于是「使用爱发电主题色」先冒出来再被隐藏 —— 就是那个「闪一下」。
    // 全局主题在 App 启动时就按真值建好了，抄它的结果最稳。
    // 注意 ThemeColorTransition 里那层 MiuixTheme(colors = ...) 只换色板、不下发 colorSchemeMode，
    // 所以这里读到的仍是外层全局主题的真实模式，不会被过渡色板顶替。
    val monetActive = MiuixTheme.isDynamicColor
    // 系统壁纸取色需要 Android 12 / API 31 以上；低版本要在下拉里藏掉「跟随系统壁纸」
    val systemWallpaperSupported = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val advancedTransition by viewModel.advancedTransition.collectAsState()
    val gestureBack by viewModel.gestureBack.collectAsState()
    val predictiveBack by viewModel.predictiveBack.collectAsState()
    val historyEnabled by viewModel.historyEnabled.collectAsState()
    val showIncomeUnit by viewModel.showIncomeUnit.collectAsState()
    val showCountUnit by viewModel.showCountUnit.collectAsState()
    val themeSettings = LocalThemeSettings.current

    // 页面
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val haptic = LocalHapticFeedback.current

    // 「重置卡片布局」确认弹窗状态
    val resetManagementDialogState = rememberDialogState()
    val resetHomeCreatorDialogState = rememberDialogState()


    Scaffold(
        topBar = {
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "设置",
                        largeTitle = "设置",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack = onBack, backStack = backStack) }
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
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = (contentBottomPadding() + 48.dp)
                )
            ) {
                // 设置项目
                item { SmallTitle(text = "启动", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        OverlaySpinnerPreference(
                            title = "打开 App 后默认进入",
                            items = startupPageNames,
                            selectedIndex = startupPage,
                            onSelectedIndexChange = viewModel::setStartupPage
                        )
                    }
                }

                item { SmallTitle(text = "外观", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        // 配色模式：只决定明暗（跟随系统/浅色/深色），与下面 Monet 开关正交
                        OverlayDropdownPreference(
                            title = "配色模式",
                            items = colorModeNames,
                            selectedIndex = colorMode.coerceIn(0, colorModeNames.lastIndex),
                            onSelectedIndexChange = viewModel::setColorMode
                        )
                        // 使用爱发电主题色：Monet 开启时由种子色接管，这里隐藏避免两个开关语义打架；
                        // 它的持久化值不清除，关闭 Monet 后会自动恢复原来的行为
                        AnimatedVisibility(visible = !monetActive) {
                            SwitchPreference(
                                checked = useAfdianColor,
                                onCheckedChange = viewModel::setUseAfdianColor,
                                title = "使用爱发电主题色",
                            )
                        }
                        // Monet 配色总开关：开启后色板改由系统壁纸或指定种子色生成
                        SwitchPreference(
                            checked = monetActive,
                            onCheckedChange = viewModel::setMonetEnabled,
                            title = "Monet 配色",
                        )
                        // Key Color：仅 Monet 开启时显示，对应官方 demo 的 Key Color 下拉。
                        // 用 Spinner（而不是普通 Dropdown）是为了在选项文字前放颜色球
                        AnimatedVisibility(visible = monetActive) {
                            // 低版本过滤掉「跟随系统壁纸」，并把已选中的它解析成真正生效的档位，
                            // 保证下拉显示的颜色与主题实际用的颜色一致
                            val selectable = remember(systemWallpaperSupported) {
                                KeyColorPreset.selectable(systemWallpaperSupported)
                            }
                            // dropdown item 带 lambda 字段，每次重组都新建会让 Spinner 内部的 remember 失效
                            val items = remember(selectable) { buildKeyColorItems(selectable) }
                            val resolved = keyColor.resolveForSdk(systemWallpaperSupported)
                            OverlaySpinnerPreference(
                                title = "主题色",  // 还是改成中文合适一点。TODO: 实现 i18n 时，此处英文为 Key Color
                                items = items,
                                selectedIndex = selectable.indexOf(resolved).coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    selectable.getOrNull(index)?.let(viewModel::setKeyColor)
                                }
                            )
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)){
                        // legacy 低配版不支持模糊（SUPPORT_BLUR=false），开关直接隐藏，
                        // 避免用户打开一个永远不会生效的选项
                        if (BuildConfig.SUPPORT_BLUR) {
                            SwitchPreference(
                                checked = themeSettings.enableBlur,
                                onCheckedChange = viewModel::setEnableBlur,
                                title = "模糊效果",
                                summary = "顶栏、底栏、动画中的模糊效果",
                            )
                            SwitchPreference(
                                checked = themeSettings.progressiveBlur,
                                onCheckedChange = viewModel::setProgressiveBlur,
                                title = "渐进模糊",
                            )
                        }
                    }
                }

                item { SmallTitle(text = "动画", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            checked = advancedTransition,
                            onCheckedChange = viewModel::setAdvancedTransition,
                            title = "高级过渡动画（实验性）",
                            summary = "创作者主页以卡片展开的方式打开和返回",
                        )
                        SwitchPreference(
                            checked = gestureBack,
                            onCheckedChange = viewModel::setGestureBack,
                            title = "手势返回",
                            summary = "向右滑动即可返回（退出设置后生效）",
                        )
                        // legacy 低配版不支持系统预测性返回（SUPPORT_PREDICTIVE_BACK=false），
                        // 开关直接隐藏，避免出现永不生效的选项
                        if (BuildConfig.SUPPORT_PREDICTIVE_BACK) {
                            SwitchPreference(
                                checked = predictiveBack,
                                onCheckedChange = viewModel::setPredictiveBack,
                                title = "预测性返回",
                            )
                        }
                    }
                }

                item { SmallTitle(text = "数据卡片", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    // 开关的实时预览：切换下面两个开关时，预览卡的单位会立即出现/消失
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DataCard(
                            title = "收入预览",
                            value = "1280.00",
                            unit = "元".takeIf { showIncomeUnit },
                            modifier = Modifier.weight(1f),
                        )
                        DataCard(
                            title = "人数预览",
                            value = "256",
                            unit = "人".takeIf { showCountUnit },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            checked = showIncomeUnit,
                            onCheckedChange = viewModel::setShowIncomeUnit,
                            title = "显示收入单位",
//                            summary = "收入、可提现等金额卡片在数值后显示「元」",
                        )
                        SwitchPreference(
                            checked = showCountUnit,
                            onCheckedChange = viewModel::setShowCountUnit,
                            title = "显示人次单位",
//                            summary = "发电人数、发电次数、访问人次等卡片显示「人」「次」",
                        )
                    }
                }

                item { SmallTitle(text = "其他", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)){
                        SwitchPreference(
                            checked = historyEnabled,
                            onCheckedChange = viewModel::setHistoryEnabled,
                            title = "记录历史",
                            summary = "最近访问、最近搜索等" +
                                    "（仅保存在本地）",
                        )
                    }
                }

                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "存储空间管理",
                            onClick = { if (backStack.none { it == Route.Storage }) backStack.add(Route.Storage) },
                        )
                        // 日志页：运行日志与崩溃报告的记录开关、占用、导出与清空
                        ArrowPreference(
                            title = "日志管理",
                            onClick = { if (backStack.none { it == Route.Log }) backStack.add(Route.Log) },
                        )
                    }
                }

                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)){
                        ArrowPreference(
                            title = "重置管理页卡片布局",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                resetManagementDialogState.show() },
                        )
                        ArrowPreference(
                            title = "重置首页创作者数据卡片布局",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                resetHomeCreatorDialogState.show()
                            },
                        )
                    }
                }

                // 退出登录
                item {
                    TextButton(
                        text = "退出登录",
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }

                item { SmallTitle(text = "关于", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "开源许可",
                            onClick = { if (backStack.none { it == Route.OssLicenses }) backStack.add(Route.OssLicenses) },
                        )
                        // 「调试日志」开关已迁移至「其他 → 日志」页：它与日志落盘/导出
                        // 是同一套排查工作流，放一起才有完整的心智模型
                    }
                }

                // 构建类型提示：居中小字显示当前打包变体（如 productionStandardDebug），
                // 便于区分测试包来源；样式沿用 DESIGN.md「列表内小字」的统一约定
                item {
                    Text(
                        text = "构建类型：" + BuildConfig.APP_VARIANT_NAME,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
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

    // 重置管理页卡片布局确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = resetManagementDialogState,
        title = "重置管理页卡片布局",
        confirmText = "重置",
        destructive = true,
        onConfirm = viewModel::resetManagementLayout,
    ) {
        Text(
            "确定要恢复默认布局吗？当前自定义的卡片顺序将丢失。",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }

    // 重置首页创作者数据卡片布局确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = resetHomeCreatorDialogState,
        title = "重置首页创作者数据卡片布局",
        confirmText = "重置",
        destructive = true,
        onConfirm = viewModel::resetHomeCreatorDataLayout,
    ) {
        Text(
            "确定要恢复默认布局吗？当前自定义的卡片顺序将丢失。",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }
}
