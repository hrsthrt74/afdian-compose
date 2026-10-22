package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.R
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_MAX
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_MIN
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_SLIDER_STEPS
import com.hrsthrt74.afdian.data.settings.KeyColorPreset
import com.hrsthrt74.afdian.data.settings.resolveForSdk
import com.hrsthrt74.afdian.ui.components.DataCard
import com.hrsthrt74.afdian.ui.components.account.LocalAccountSwitcher
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.TooltipInfoButton
import com.hrsthrt74.afdian.ui.components.basic.TooltipInfoButtonStyle
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.components.update.UpdateDialog
import com.hrsthrt74.afdian.ui.components.update.openUpdateUrl
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.theme.DATACARD_VALUE_FONT_FEATURE_SETTINGS
import com.hrsthrt74.afdian.ui.theme.LocalDataCardFontFamily
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.theme.ThemeColorTransition
import com.hrsthrt74.afdian.ui.theme.rememberMiSansCondensedFontFamily
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import com.hrsthrt74.afdian.ui.viewmodel.UpdateViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

// 「通用」分组里启动页的下拉选项
private val startupPageNames = listOf(
    DropdownItem(
        icon = { Icon(ImageVector.vectorResource(R.drawable.home_outlined), null, modifier = Modifier.padding(end = 12.dp)) },
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

// 底栏样式选项，索引与 SettingsStore 的 bottomBarStyle（0/1）约定一致
private val bottomBarStyleNames = listOf("常规", "悬浮")

// 链接打开方式选项，索引与底层 preferOfficialClient 布尔值的约定一致：
// 0 = Custom Tabs（默认）、1 = 优先官方客户端
private val linkOpenModeNames = listOf("系统浏览器", "官方客户端")

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
    val bottomBarStyle by viewModel.bottomBarStyle.collectAsState()
    val historyEnabled by viewModel.historyEnabled.collectAsState()
    val homeAccountSwitcherEnabled by viewModel.homeAccountSwitcherEnabled.collectAsState()
    val showIncomeUnit by viewModel.showIncomeUnit.collectAsState()
    val showCountUnit by viewModel.showCountUnit.collectAsState()
    val rollingNumberEnabled by viewModel.rollingNumberEnabled.collectAsState()
    val dataCardCustomFontEnabled by viewModel.dataCardCustomFontEnabled.collectAsState()
    val dataCardFontWidth by viewModel.dataCardFontWidth.collectAsState()
    val preferOfficialClient by viewModel.preferOfficialClient.collectAsState()
    val themeSettings = LocalThemeSettings.current

    // 字体宽度拖动的本地草稿：只在松手时落盘，避免拖动过程每一帧都写一次 DataStore。
    // 以已落盘的 dataCardFontWidth 为 key：首次读出或别处改动时重新同步到真实值。
    var draftFontWidth by remember(dataCardFontWidth) {
        mutableFloatStateOf(dataCardFontWidth.toFloat())
    }
    // 预览卡专用字体族：直接跟随草稿宽度构建，于是拖动滑块时两张预览卡会实时变宽变窄。
    // 它只覆盖设置页这一小片子树（见下方嵌套 Provider），不影响全局下发值。
    val previewFontFamily = if (dataCardCustomFontEnabled) {
        rememberMiSansCondensedFontFamily(draftFontWidth.roundToInt())
    } else {
        null
    }
    // 「数值窄字体」这一项 summary 专用的字体族：**恒用** MiSans Condensed、恒跟随草稿宽度，
    // 与上面的开关状态无关 —— 于是这段描述本身就是一条活字体样本：
    // 开关关着也能提前看到字体长什么样，拖动滑块时样例同步变宽变窄。
    // 必须和 previewFontFamily 分开构建：后者在开关关闭时得是 null（预览卡要回到系统字体），
    // 而这里要的正是「始终」。
    // 宽度相同时 remember 会命中复用，与 previewFontFamily 各持一份对象也不会重复解析字体，
    // 因为 Typeface 缓存在字体解析器里按 (resId, weight, style, variationSettings) 命中。
    val summaryFontFamily = rememberMiSansCondensedFontFamily(draftFontWidth.roundToInt())

    // 预览卡的重播令牌：0 = 首次渲染不播放，数字直接落位。
    // 自增会让 key(replayToken) 重建预览卡，从而重新触发一次完整滚动
    //（滚轮用 rememberSaveable 记住「已播过」，只有重建才能重播）。
    var replayToken by remember { mutableStateOf(0) }
    // 唯一的触发源是「把开关从关打到开」：让用户立刻看到刚打开的效果，
    // 而刚进设置页时不会自己播（动画只有约 460ms，自动播很容易被错过）。
    // 与「上一次的值」比较而不是直接触发，正是为了滤掉首次组合。
    var lastRollingNumberEnabled by remember { mutableStateOf(rollingNumberEnabled) }
    LaunchedEffect(rollingNumberEnabled) {
        if (rollingNumberEnabled && !lastRollingNumberEnabled) replayToken++
        lastRollingNumberEnabled = rollingNumberEnabled
    }

    // 页面
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val haptic = LocalHapticFeedback.current

    // 「重置卡片布局」确认弹窗状态
    val resetManagementDialogState = rememberDialogState()
    val resetHomeCreatorDialogState = rememberDialogState()
    // 「退出当前账号」确认弹窗：登出会清掉凭证并回到登录页，
    // 属于不可撤销的操作，必须二次确认（误触即掉线的代价太高）
    val logoutDialogState = rememberDialogState()

    // 检查更新：手动检查用的 ViewModel 与弹窗状态。
    // key 刻意与冷启动自动检查的那个实例区分开 —— 手动检查要如实反馈失败，
    // 自动检查要求全程静默，两者共用一个实例会互相干扰状态。
    val updateViewModel: UpdateViewModel = viewModel(
        key = "update-manual",
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
    val updateState by updateViewModel.uiState.collectAsState()
    val updateDialogState = rememberDialogState()
    // 打开下载链接用的 Context
    val context = LocalContext.current


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
                // 账号管理
                item {
                    val accountSwitcher = LocalAccountSwitcher.current
                    if (accountSwitcher != null) {
                        Column {
                            Row(    // 带tooltip的小标题
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SmallTitle(text = "账号", modifier = Modifier.padding(start = 4.dp))
                                Spacer(Modifier.weight(1f))
                                TooltipInfoButton(
                                    tooltipText = "也可以长按「我的」页的头像来快速切换",
                                    style = TooltipInfoButtonStyle.Small,
                                    iconTint = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.padding(end = 28.dp)
                                )
                            }
                            Card(modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)) {
                                accountSwitcher.accounts.forEach { account ->
                                    val isCurrent = account.userId == accountSwitcher.currentUserId
                                    // 失效账号给出提示：点击仍会走切换流程，由根层预检失败后再提示，
                                    // 这样「设置页点它」与「弹层点它」的表现完全一致
                                    val isInvalid = account.userId in accountSwitcher.invalidUserIds
                                    BasicComponent(
                                        title = account.name.ifBlank { "爱发电用户" },
                                        summary = when {
                                            isCurrent -> "当前账号"
                                            isInvalid -> "登录已失效"
                                            else -> null
                                        },
                                        insideMargin = PaddingValues(12.dp),    // 紧凑点
                                        startAction = {
                                            Avatar(
                                                model = account.avatar,
                                                size = 32.dp,
                                                contentDescription = "账号头像",
                                                modifier = Modifier.padding(start = 4.dp)   // 16-12
                                            )
                                        },
                                        endActions = {
                                            if (isCurrent) {
                                                // 自己的账号不叫「删除」，走的是退出登录：
                                                // 清掉当前凭证与本条账号记录，其他账号保留，回到登录页。
                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                                        // 先确认再登出：误触一次就掉线、还要重新登录
                                                        logoutDialogState.show()
                                                    }) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Remove,
                                                        contentDescription = "退出当前账号",
                                                    )
                                                }
                                            } else {
                                                // 删除其他账号：根层弹确认框，不动当前会话
                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                                        accountSwitcher.onDelete(account)
                                                    }) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Delete,
                                                        contentDescription = "删除该账号",
                                                    )
                                                }
                                            }
                                        },
                                        // 点账号即切换；当前账号本身不触发（避免无谓的一次会话重建）
                                        onClick = if (isCurrent) {
                                            null
                                        } else {
                                            {
                                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                                accountSwitcher.onSwitch(account.userId)
                                            }
                                        },
                                    )
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                // 添加账号：与切换菜单里的「添加」是同一个动作
                                BasicComponent(
                                    title = "添加账号",
                                    insideMargin = PaddingValues(16.dp, 12.dp),
                                    startAction = {
                                        Icon(
                                            imageVector = MiuixIcons.Add,
                                            contentDescription = null,
                                            modifier = Modifier.padding(4.dp),
                                        )
                                    },
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        accountSwitcher.onAddAccount()
                                    },
                                )
                            }
                            // 「首页账号切换器」开关：单账号没有可切换对象，整块不渲染
                            if (accountSwitcher.accounts.size > 1) {
                                Card(modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp)) {
                                    // 控制首页顶栏起始侧是否显示当前账号头像
                                    SwitchPreference(
                                        checked = homeAccountSwitcherEnabled,
                                        onCheckedChange = viewModel::setHomeAccountSwitcherEnabled,
                                        title = "首页账号切换器",
                                        summary = "位于首页顶栏左侧，点击即可切换账号",
                                    )
                                }
                            }
                        }
                    }
                }

                item { SmallTitle(text = "通用", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)) {
                        // 启动页
                        OverlaySpinnerPreference(
                            title = "打开 App 后默认进入",
                            items = startupPageNames,
                            selectedIndex = startupPage,
                            onSelectedIndexChange = viewModel::setStartupPage
                        )
                        // 记录历史
                        SwitchPreference(
                            checked = historyEnabled,
                            onCheckedChange = viewModel::setHistoryEnabled,
                            title = "记录历史",
                            summary = "最近访问、最近搜索等" +
                                    "（仅保存在本地）",
                        )
                        // 链接打开方式：0=Custom Tabs、1=优先官方客户端（失败仍回退 Custom Tabs）
                        OverlayDropdownPreference(
                            title = "链接打开方式",
                            items = linkOpenModeNames,
                            selectedIndex = if (preferOfficialClient) 1 else 0,
                            onSelectedIndexChange = { index ->
                                viewModel.setPreferOfficialClient(index == 1)
                            },
                        )
                    }
                }

                item { SmallTitle(text = "外观", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)) {
                        // 底栏样式：常规（贴底整条）/ 悬浮（胶囊），MainScreen 订阅后立即生效
                        OverlayDropdownPreference(
                            title = "底栏样式",
                            items = bottomBarStyleNames,
                            selectedIndex = bottomBarStyle.coerceIn(0, bottomBarStyleNames.lastIndex),
                            onSelectedIndexChange = viewModel::setBottomBarStyle
                        )
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
                    Card(modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)){
                        // legacy 低配版不支持模糊（SUPPORT_BLUR=false），开关直接隐藏，
                        // 避免用户打开一个永远不会生效的选项
                        if (BuildConfig.SUPPORT_BLUR) {
                            SwitchPreference(
                                checked = themeSettings.enableBlur,
                                onCheckedChange = viewModel::setEnableBlur,
                                title = "模糊效果",
                                summary = "顶栏、底栏、数字滚动等动画中的模糊效果",
                            )
                            // 渐进模糊是模糊效果的子选项，总开关关掉时一起隐藏，避免出现无效选项
                            AnimatedVisibility(visible = themeSettings.enableBlur) {
                                SwitchPreference(
                                    checked = themeSettings.progressiveBlur,
                                    onCheckedChange = viewModel::setProgressiveBlur,
                                    title = "渐进模糊",
                                )
                            }
                        }
                    }
                }

                item { SmallTitle(text = "动画", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)) {
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
                            summary = "页面任意位置向右滑动以返回",
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
                    // 这两张预览卡身兼三职：
                    //   1) 切换下面两个单位开关时，单位会立即出现/消失；
                    //   2) 兼作数字滚动动画的预览 —— 进页面时直接落位、不播放（动画只有约 460ms，
                    //      自动播很容易被错过），只有把「数字滚动动画」开关从关打到开时才播一次。
                    //      令牌一变就重建预览卡，滚轮随之从 0 重新滚一遍。
                    //   3) 兼作数值字体的预览 —— 开着字体开关时，下面拖宽度滑块能实时看到变化。
                    // 字体 Provider 刻意套在 key(replayToken) 外面：宽度变化只该让数字「重新量算」，
                    // 不该触发滚轮重播（重播只属于「数字滚动动画」开关从关到开那一下）。
                    CompositionLocalProvider(LocalDataCardFontFamily provides previewFontFamily) {
                        key(replayToken) {
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
                                    // 开关关掉时这里回退成普通文本，顺带预览了「关闭后」的效果
                                    animateValue = rollingNumberEnabled,
                                    autoPlay = replayToken > 0,
                                )
                                DataCard(
                                    title = "人数预览",
                                    value = "256",
                                    unit = "人".takeIf { showCountUnit },
                                    modifier = Modifier.weight(1f),
                                    animateValue = rollingNumberEnabled,
                                    autoPlay = replayToken > 0,
                                )
                            }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)) {
                        // 数字动画总开关：关闭后所有数据卡的数字回退为普通文本显示
                        SwitchPreference(
                            checked = rollingNumberEnabled,
                            onCheckedChange = viewModel::setRollingNumberEnabled,
                            title = "数字滚动动画",
                            // 尾部补一个「重播」按钮：自增 replayToken 会让上方两张预览卡重建
                            // （见 key(replayToken)），从而重新滚一遍，方便反复看效果
                            endActions = {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        replayToken++
                                    },
                                    minWidth = 28.dp,
                                    minHeight = 28.dp,
                                    enabled = rollingNumberEnabled,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Demibold.Play,
                                        contentDescription = "重播数字滚动动画",
                                        tint = if (rollingNumberEnabled) { MiuixTheme.colorScheme.onSurfaceVariantActions }
                                               else { MiuixTheme.colorScheme.disabledOnSecondaryVariant },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                        )
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
                        // 数值窄体字体：默认关闭，关闭时数值完全沿用系统默认字体。
                        // 用自写的 CondensedFontSwitchPreference（而不是 miuix 的 SwitchPreference）：
                        // 只有它能把 MiSans Condensed 注入到 summary 上，让描述文字自己变成字体样本。
                        CondensedFontSwitchPreference(
                            checked = dataCardCustomFontEnabled,
                            onCheckedChange = viewModel::setDataCardCustomFontEnabled,
                            title = "数值窄字体",
                            summary = "MiSans Condensed 2T",
                            summaryFontFamily = summaryFontFamily,
                        )
                        // 宽度滑块只在开关打开时出现，避免留一个拖了没反应的无效选项。
                        // 用 AnimatedVisibility（而不是裸 if）让它随开关展开/收起，
                        // 与上面「渐进模糊」「主题色」几处的显隐动画保持一致。
                        // 拖动走本地草稿（预览卡实时变宽），松手才落盘。
                        AnimatedVisibility(visible = dataCardCustomFontEnabled) {
                            SliderPreference(
                                title = "字体宽度",
                                value = draftFontWidth,
                                onValueChange = { draftFontWidth = it },
                                onValueChangeFinished = {
                                    val picked = draftFontWidth.roundToInt()
                                    if (picked != dataCardFontWidth) viewModel.setDataCardFontWidth(picked)
                                },
                                valueRange = DATACARD_FONT_WIDTH_MIN.toFloat()..DATACARD_FONT_WIDTH_MAX.toFloat(),
                                // 每 10 个单位一档（10–100 共 10 档）。steps 是「两端点之间的档位数」，
                                // 所以是 档位数 - 2 = 8，换算见常量注释。
                                steps = DATACARD_FONT_WIDTH_SLIDER_STEPS,
                                // 当前档位显示在标题行右侧，拖动时能直接读出数值
                                valueText = draftFontWidth.roundToInt().toString(),
                                // 10 个档位全部画点、逐档震动。
                                //
                                // **这里刻意不传 keyPoints**：miuix 只要拿到非空 keyPoints，就会把震动用的
                                // 「关键点集合」（allKeyPointFractions）整个换成 keyPoints。而 keyPoints 一旦
                                // 覆盖了全部档位，边沿触发（"从非关键点进入关键点才震"）就再也触发不了 ——
                                // 吸附后的值每一帧都停在关键点上，Step 会整体失效、只剩两端 Edge。
                                // 「每档都震」因此要走 steps + showKeyPoints，而不是 keyPoints。
                                showKeyPoints = true,
                                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                            )
                        }
                    }
                }

                item { SmallTitle(text = "其他", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "存储空间管理",
                            onClick = { if (backStack.none { it == Route.Storage }) backStack.add(Route.Storage) },
                        )
                        // 日志页：运行日志与崩溃报告的记录开关、占用、导出与清空
                        ArrowPreference(
                            title = "日志管理",
                            onClick = { if (backStack.none { it == Route.Log }) backStack.add(Route.Log) },
                        )
                        // 接口调试页：主动探针逐个运行核心接口，用于肉眼核对各接口当前的返回值
                        ArrowPreference(
                            title = "接口调试",
                            onClick = { if (backStack.none { it == Route.ApiProbe }) backStack.add(Route.ApiProbe) },
                        )
                    }
                }

                item {
                    Card(modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)){
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

                item { SmallTitle(text = "关于", modifier = Modifier.padding(start = 4.dp)) }

                item {
                    Card( modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp) ) {
                        // 功能完成度：常驻的「当前尚未完成的功能」清单入口。
                        // 应用内唯一展示这份清单的地方，想知道哪些还没做完从这里进。
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
                        ArrowPreference(
                            title = "开源许可",
                            onClick = { if (backStack.none { it == Route.OssLicenses }) backStack.add(Route.OssLicenses) },
                        )
                        ArrowPreference(
                            title = "免责声明",
                            onClick = { if (backStack.none { it == Route.Disclaimer }) backStack.add(Route.Disclaimer) },
                        )
                        // 「调试日志」开关已迁移至「其他 → 日志」页：它与日志落盘/导出
                        // 是同一套排查工作流，放一起才有完整的心智模型
                    }
                }

                // 版本号：用户报问题时最常被问到的信息，直接显示在设置页底部。
                // 样式沿用 DESIGN.md「列表内小字」的统一约定
                item {
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp),
                    )
                }

                // 构建类型提示：居中小字显示当前打包变体（如 productionStandardRelease），
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }

    // 退出当前账号确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = logoutDialogState,
        title = "退出当前账号",
        confirmText = "退出",
        destructive = true,
        onConfirm = onLogout,
    ) {
        Text(
            "退出后需要重新输入账号密码登录；该账号会从账号列表中移除，其他已保存的账号不受影响。",
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }

    // 检查更新弹窗：手动检查的四种状态（检查中 / 无更新 / 有更新 / 失败）都在这一个弹窗里呈现
    UpdateDialog(
        state = updateDialogState,
        uiState = updateState,
        onIgnore = { tag -> updateViewModel.ignoreVersion(tag) },
        onOpenDownload = { url -> openUpdateUrl(context, url) },
        // 关闭后把「有更新」这个状态复位：它是「结果」不是「事件」，
        // 不复位的话旋转屏幕等重组会再次把弹窗拉起来
        onDismissed = { updateViewModel.consumeAvailable() },
    )
}

/**
 * 「数值窄字体」那一项专用的开关偏好项。
 *
 * **为什么自己写一个**：miuix 的 `SwitchPreference` 只收 `summary: String`，
 * 内部固定用主题字体渲染，没有任何入口能把 `FontFamily` 注入到 summary；
 * 而这里要的正是「让描述文字自己变成 MiSans Condensed 的样本」。
 *
 * **怎么写的**：结构完全照抄 miuix 源码 `miuix-preference/.../SwitchPreference.kt` ——
 * 同一个 `BasicComponent` + 右侧 `Switch` + `Role.Switch` + 整行点击翻转，
 * 连 `rememberUpdatedState` 包一层回调的细节也一致。
 * 差别只有一处：改用 `BasicComponent` 的 content 插槽重载
 * （`miuix-ui/.../basic/Component.kt` 里 `content: @Composable ColumnScope.() -> Unit` 那个版本），
 * title / summary 由我们自己画，于是 summary 想用什么 TextStyle 都行。
 * 行高、内边距、右侧区域宽度上限（60% 硬上限）、无障碍语义仍由 BasicComponent 负责，
 * 所以外观与相邻的原生偏好项不会有任何偏差。
 *
 * 不做成通用组件的理由：只有设置页这一处调用，多一层参数化只是噪音。
 *
 * @param checked 开关状态
 * @param onCheckedChange 开关状态变化回调
 * @param title 标题（字号/字重与 miuix 一致：headline1 字号 + Medium）
 * @param summary 描述文字，用 [summaryFontFamily] 渲染
 * @param summaryFontFamily summary 使用的字体族（调用方按当前宽度构建，随滑块变化）
 * @param modifier 修饰符
 * @param enabled 是否可用
 */
@Composable
private fun CondensedFontSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    summary: String,
    summaryFontFamily: FontFamily,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 与 miuix 一致：用 rememberUpdatedState 包一层，避免每次重组都把新 lambda 捕获进 clickable
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    // 标题 / 描述配色：miuix 取的是 BasicComponentDefaults.titleColor()/summaryColor() 的默认值，
    // 这里直接读同一组色板。它们内部用的 BasicComponentColors.color(enabled) 是 internal，
    // 跨模块不可见，所以按 enabled 自己选一遍。
    val titleColor = if (enabled) MiuixTheme.colorScheme.onBackground
                     else MiuixTheme.colorScheme.disabledOnSecondaryVariant
    val summaryColor = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
                       else MiuixTheme.colorScheme.disabledOnSecondaryVariant

    BasicComponent(
        modifier = modifier,
        // 右侧：开关本体（对应 miuix 的 SwitchPreferenceEndActions）
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = currentOnCheckedChange,
                enabled = enabled,
            )
        },
        // 整行点击翻转，语义角色为 Switch
        onClick = { currentOnCheckedChange(!checked) },
        role = Role.Switch,
        enabled = enabled,
    ) {
        // 标题
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = titleColor,
        )
        // 描述：本组件存在的意义 —— 换成 MiSans Condensed。
        // fontFeatureSettings 一并关掉 calt：这个字体的 calt 会把小数点错换成冒号字形
        //（详见 DATACARD_VALUE_FONT_FEATURE_SETTINGS 的注释）。当前文案里没有小数点，
        // 但留着这行，将来把文案换成数字样例（如 1280.00）时不会踩坑。
        Text(
            text = summary,
            color = summaryColor,
            style = MiuixTheme.textStyles.body2.copy(
                fontFamily = summaryFontFamily,
                fontFeatureSettings = DATACARD_VALUE_FONT_FEATURE_SETTINGS,
            ),
        )
    }
}
