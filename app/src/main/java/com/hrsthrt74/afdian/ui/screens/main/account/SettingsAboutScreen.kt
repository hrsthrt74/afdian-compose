package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.R
import com.hrsthrt74.afdian.data.content.Changelog
import com.hrsthrt74.afdian.data.content.ReleaseNote
import com.hrsthrt74.afdian.data.update.UpdateChannel
import com.hrsthrt74.afdian.ui.components.background.WaterBackground
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.components.update.UpdateDialog
import com.hrsthrt74.afdian.ui.components.update.openUpdateUrl
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.navigation.openExternalLink
import com.hrsthrt74.afdian.ui.navigation.rememberCustomTabsBranding
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.theme.isDarkColorScheme
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import com.hrsthrt74.afdian.ui.viewmodel.UpdateDownloadState
import com.hrsthrt74.afdian.ui.viewmodel.UpdateViewModel
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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

/** 应用图标边长（跟 miuix demo 的 88dp 对齐）。 */
private val HeroIconSize = 88.dp

/** 图标圆角：取边长的 1/4，接近系统启动器图标的观感。 */
private val HeroIconCorner = 22.dp

/**
 * hero 距顶栏的间距。
 *
 * 直接照抄 miuix demo 的算法：它的 `AboutPage` 里 hero 的 top padding 是
 * `extraTop(40.dp) + CollapsedHeight(52.dp)`。demo 的顶栏不含状态栏，
 * 所以那 52dp 是「让开顶栏」的量；我们的顶栏高度已经含在 `paddingValues` 里，
 * 这里再把两段都补上，得到与 demo 相同的呼吸感（图标落在屏幕约 1/5 处）。
 */
private val HeroTopGap = 92.dp

/**
 * hero 与第一张卡片之间的留白（demo 里是 126dp）。
 *
 * 比 demo 略收：hero 的上边距已经按 demo 放大，这里再全给 126 的话，
 * 一屏就只剩两张卡片了。
 */
private val HeroBottomGap = 96.dp

/**
 * hero 高度的估计值，只在首帧用：真实高度要等布局测量完才知道，
 * 而列表的透明占位在第一次布局时就得有个数，否则会先挤在一起再跳开。
 */
private val HeroHeightEstimate = 190.dp

/**
 * flavor 标记：S = standard（完整版），L = legacy（低配兼容版）。
 *
 * 两个包能并存在同一台机器上，桌面名字却只差一个后缀；版本号后面缀一个字母，
 * 用户截图报 issue 时一眼就能看出装的是哪个包。按「包身份」判断，
 * 不去解析 [BuildConfig.APP_VARIANT_NAME] 那种字符串。
 */
private val HeroFlavorBadge: String = if (BuildConfig.LEGACY_EDITION) "L" else "S"

/** 列表里 hero 透明占位的 item key，滚动进度也靠它定位。 */
private const val HeroSpacerKey = "hero-spacer"

/** 吸附前的静默期：这段时间内只要还有滚动位移，就取消这次吸附。 */
private const val SnapSettleMillis = 120L

/**
 * 「松手即吸附」的速度上限（px/s）。
 *
 * 低于它的算轻划，手指抬起就立刻吸附；高于它的算重甩，交给惯性自己飞——
 * 否则用力一甩会被吸附动画掐断，手感很憋。
 */
private const val ReleaseSnapMaxVelocity = 3000f


/**
 * 设置 → 关于。
 *
 * 版式参照 miuix demo 的 About 页：顶部是「品牌 logo + 应用名 + 版本号」的 hero，
 * 下面是分组卡片，整页背后铺一层动态海水 shader（只对 standard 包生效）。
 *
 * 页内含：
 * - hero：应用名、版本号、构建号（Actions 包额外带 commit）；
 * - 分组「关于」：交流群、GitHub、开源许可、免责声明、功能完成度
 *   （爱发电文档入口已收进「我的」页 → 文档）；
 * - 分组「更新」：检查更新、更新通道；
 * - 页脚：构建类型（版本号已上移到 hero，不再重复）。
 *
 * 刻意**不用** `SettingsPageScaffold`：那套骨架是「大标题顶栏」形态，
 * 与本页「hero 承担标题、顶栏只有一个返回键」的版式冲突。其余四个设置页仍走它。
 */
@OptIn(ExperimentalScrollBarApi::class)
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

    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val colorScheme = MiuixTheme.colorScheme
    val density = LocalDensity.current

    // 动态海水背景：两种情况下都不画（连 shader 都不实例化），回落普通主题底色。
    // 1. legacy 低配包 —— 这是「包身份」，与 UI 能力开关解耦是刻意的设计
    //    （见 app/build.gradle.kts 里 LEGACY_EDITION 的注释）；
    // 2. 用户在外观设置里关掉了「模糊效果」—— 全屏逐像素的 shader 是纯装饰性开销，
    //    关模糊的人通常在意流畅度/发热，没必要还留一层动画背景。
    //    注：ThemeSettings 在 legacy 下已把 enableBlur 强制成 false，这里仍单独判包身份，
    //    是为了让「低配包不画水」这个约定不依赖别的开关。
    val themeSettings = LocalThemeSettings.current
    val showWater = !BuildConfig.LEGACY_EDITION && themeSettings.enableBlur
    // 卡片配色：画了海水就半透明（让水纹透出来），否则用主题默认的不透明卡片
    val cardColors = aboutCardColors(showWater = showWater)

    // 本版更新：直接读打包进 assets 的 CHANGELOG.md，把**当前版本**那一节外显在页面上，
    // 省得为了看一两条改动还要点进「更新日志」。
    // 找不到这一版（CHANGELOG 还没写 / 版本号对不上）时为 null，卡片退化成纯入口，不会空着。
    // 几 KB 文本，remember 内同步读一次，与「开源许可」页解析 aboutlibraries.json 的做法一致
    val currentReleaseNote = remember(context) {
        Changelog.load(context).firstOrNull { it.version == BuildConfig.VERSION_NAME }
    }

    // hero 的实测高度：列表要靠它留一块透明占位。初值给个接近的估计，
    // 实测完成后会立刻校正（差几 dp 肉眼看不出来，总比首帧闪一下强）
    var heroHeight by remember { mutableStateOf(HeroHeightEstimate) }
    // 滚动进度 0..1：0 = 还在顶部，1 = 已经滑过整块 hero 占位。
    // 分母用占位 item 的实际高度，跟 miuix demo 的算法一致
    val heroProgress = remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val spacer = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == HeroSpacerKey }
                if (spacer != null && spacer.size > 0) {
                    (listState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
        }
    }

    // ── hero 区吸附 ──
    // 滚动后只允许停在两个位置：顶部，或完全滑过 hero。中间态（水面淡到一半、标题半亮）只当过渡。
    // miuix 本身没有这个行为，是本页自己的手感处理。
    val snapScope = rememberCoroutineScope()
    // 这一次手势的起点 offset：用来判断是「把 hero 往上推（进列表）」还是「往回拉（回顶部）」。
    // 不用 listState.lastScrolledBackward —— 松手瞬间的回弹会让它翻向，轻划就判错方向。
    var gestureStartOffset by remember { mutableIntStateOf(0) }

    /** 按当前 offset 决定落点并吸附。可以重复调用：已经落在两端时会直接返回。 */
    fun snapToSettledPosition() {
        if (listState.firstVisibleItemIndex != 0) return
        val spacerPx = with(density) { (HeroTopGap + heroHeight + HeroBottomGap).toPx() }
        if (spacerPx <= 0f) return
        val current = listState.firstVisibleItemScrollOffset
        // 已经在两端：不动
        if (current <= 1 || current >= spacerPx - 1) return
        // 往列表方向：滑过 6% 就吸上去（轻轻一划也直接进列表）；
        // 往回：要拉掉 30% 以上才退回顶部，免得手指一抖就从列表掉回 hero。
        val pushedUp = current >= gestureStartOffset
        val threshold = if (pushedUp) 0.06f else 0.70f
        val target = if (current > spacerPx * threshold) 1 else 0
        // 动画丢到组合作用域：放进快照收集器里的话，动画自己产生的每一帧快照都会把上一轮取消掉
        snapScope.launch { listState.animateScrollToItem(target) }
    }

    // 松手即吸附：onPreFling 是「手指抬起、惯性开始之前」的那一拍。
    // 只接管轻划（速度低于阈值）—— 重甩的惯性应该让列表自己飞出去，不能被吸附掐断。
    val releaseSnap by rememberUpdatedState { snapToSettledPosition() }
    val releaseConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (abs(available.y) < ReleaseSnapMaxVelocity) releaseSnap()
                return Velocity.Zero
            }
        }
    }

    // 兜底：没有惯性（慢慢松手）时 onPreFling 不一定触发，这里等它彻底停稳再补一次
    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow {
            // 三个值一起观察，别只盯 isScrollInProgress：
            // 一次很快的拖拽 true → false 会被 snapshotFlow 合并，最终值仍等于上次发射的 false，
            // 于是**一次都不发射**，吸附自然就不会触发。
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }.collectLatest { (scrolling, _, offset) ->
            if (scrolling) {
                // 每次「从静止开始滚」记一次手势起点
                if (!wasScrolling) gestureStartOffset = offset
                wasScrolling = true
                return@collectLatest
            }
            wasScrolling = false
            if (listState.firstVisibleItemIndex != 0) return@collectLatest
            // 静默期：这 120ms 内只要又产生新的滚动快照，collectLatest 就会取消这次吸附
            delay(SnapSettleMillis)
            snapToSettledPosition()
        }
    }

    Scaffold(
        // 画了海水就必须让 Scaffold 透出下层，否则默认的 surface 底色会把水面整个盖住
        containerColor = if (showWater) Color.Transparent else colorScheme.surface,
        topBar = {
            DoubleTapToTop(listState, scrollBehavior) {
                AboutTopBar(
                    scrollBehavior = scrollBehavior,
                    progress = { heroProgress.value },
                    onBack = onBack,
                    backStack = backStack,
                )
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景层：铺满整个 Scaffold 内容区（Scaffold 的 content 是整屏摆放的，
            // 所以水面会一直铺到状态栏与导航栏后面）
            if (showWater) {
                // 水淡出之后露出来的底色：跟卡片同色，保证滑到底时是干净的实色页面
                Box(modifier = Modifier.matchParentSize().background(colorScheme.surface))
                WaterBackground(
                    modifier = Modifier
                        .matchParentSize()
                        // demo 的 BgEffectBackground 就是这么收尾的：alpha = 1 - scrollProgress。
                        // 上滑时整片动态背景淡出，滑过 hero 就回到实色背景 —— 卡片落在纯色上更好读，
                        // 也顺便把「水面」限定成首屏的氛围元素，而不是全程跟列表抢注意力
                        .graphicsLayer { alpha = 1f - heroProgress.value },
                )
            }

            // 品牌区固定在顶栏下方、不跟列表滚：靠 heroProgress 做淡出 + 缩小，
            // 列表的透明占位负责把卡片推到它下面。绘制顺序上 hero 在列表之前，
            // 所以卡片上滑时会从 hero 上方压过去（跟 miuix demo 的层次一致）
            AboutHero(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = paddingValues.calculateTopPadding() + HeroTopGap),
                progress = { heroProgress.value },
                versionDetail = versionDetail,
                onHeightChanged = { measuredHeight -> heroHeight = with(density) { measuredHeight.toDp() } },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // 松手检测放在最外层：onPreFling 是「手指抬起、惯性还没开始」的那一拍
                    .nestedScroll(releaseConnection)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 放在 nestedScroll 之后：fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding() + 24.dp,
                ),
            ) {
                // 透明占位：高度 = hero 自身 + 与第一张卡片之间的留白，
                // 既把内容推到 hero 下面，也给 heroProgress 当分母
                item(key = HeroSpacerKey) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HeroTopGap + heroHeight + HeroBottomGap),
                    )
                }

                item(key = "about-group") {
                    Column {
                        // 「关于」分组不再放小标题：hero 已经讲完「这是关于页」，直接铺卡片更透气
                        Card(
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                            colors = cardColors,
                        ) {
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
                        Card(
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                            colors = cardColors,
                        ) {
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
                }

                item(key = "update-group") {
                    Column {
                        SmallTitle(text = "更新", modifier = Modifier.padding(start = 4.dp))
                        Card(
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                            colors = cardColors,
                        ) {
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
                        // 本版更新：把当前版本的改动直接铺出来，不用点进去才看得到。
                        // 排在操作卡下面 —— 先给「检查更新」这些动作，再给说明性内容
                        UpdateLogCard(
                            note = currentReleaseNote,
                            colors = cardColors,
                            onShowAll = { if (backStack.none { it == Route.Changelog }) backStack.add(Route.Changelog) },
                        )
                    }
                }

                // 构建类型提示：居中小字显示当前打包变体（如 standardRelease），
                // 便于区分测试包来源；样式沿用 DESIGN.md「列表内小字」的统一约定。
                // 版本号在 hero 里，这里不再重复。
                item(key = "footer") {
                    Text(
                        text = "构建类型：" + BuildConfig.APP_VARIANT_NAME,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }

            // 右侧滚动条：top 让出顶栏高度
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
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
 * 关于页顶栏：没有大标题，只有返回键。
 *
 * 底色与标题的登场时机**照 miuix demo 的 About 页**（demo 里 `barColor` 要到
 * scrollProgress == 1 才变成 surface，标题则是从 0.35 开始按比例淡入）：
 * - 底色：滑过 hero 占位的 70% 才开始压 surface，100% 才与常规顶栏一致；
 * - 标题：同样从 35% 起淡入，滑回顶部时再退场。
 *
 * 用 [progress]（hero 占位的滚动比例）而不是 `scrollBehavior.state.contentOffset`：
 * 后者是累计像素，之前按 48dp 拉满，于是「稍微划一下就整条变白板」。
 * 另外 `SmallTopAppBar` 会把 `heightOffsetLimit` 钉成 0，
 * `collapsedFraction` / `overlappedFraction` 在这种 pinned 小栏下恒为 0，本来也读不到进度。
 *
 * 底色仍然自己画，不用 `BlurredBar`：页面顶部压着一层动态水面，模糊栏要把 shader
 * 录进采样层，而 DESIGN.md 要求采样层不能包带 `textureBlur` 的栏，链路一长容易踩崩；
 * 水面是低频渐变，压一层半透明底色与真模糊的观感几乎一致。
 *
 * @param progress hero 占位的滚动进度 0..1
 */
@Composable
private fun AboutTopBar(
    scrollBehavior: ScrollBehavior,
    progress: () -> Float,
    onBack: () -> Unit,
    backStack: NavBackStack,
) {
    val surface = MiuixTheme.colorScheme.surface
    val onSurface = MiuixTheme.colorScheme.onSurface
    // 标题：0.35 之前完全透明，1.0 时完全可读（与 demo 同一组系数）
    val titleAlpha = ((progress() - 0.35f) / 0.65f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier.drawBehind {
            // 底色：滑过 70% 才开始出现，避免早早糊上一层不透明底色
            val tint = ((progress() - 0.70f) / 0.30f).coerceIn(0f, 1f)
            if (tint > 0f) drawRect(color = surface, alpha = tint)
        },
    ) {
        SmallTopAppBar(
            title = "关于",
            // 底色交给外层蒙层：这里必须透明，否则会把水面与渐显蒙层一起盖住
            color = Color.Transparent,
            // 标题色自己算。miuix 内部的 smallTitleAlpha 弹簧在「刚开始滚动」时就会跳到 1，
            // 只靠它标题会在划过第一个像素时就蹦出来
            titleColor = onSurface.copy(alpha = titleAlpha),
            scrollBehavior = scrollBehavior,
            // 返回按钮：点击弹栈，长按可快速回到主页
            navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
        )
    }
}

/**
 * 品牌区：应用图标、应用名、版本号。
 *
 * 版式与 miuix demo 的 About 页一致：整块**固定在列表之上**，不跟着滚动，
 * 而是随 [progress] 逐级淡出 —— 版本号最先退场，然后应用名，最后图标一边缩小一边消失。
 * 列表里对应的是一块透明占位（见 [HeroSpacerKey]），卡片上滑时从 hero 上方压过去。
 *
 * @param modifier 外部修饰符（调用方负责顶栏高度的偏移与水平居中）
 * @param progress 滚动进度 0..1，**只在 graphicsLayer 里读**，避免每帧重组
 * @param versionDetail 版本号后缀（Actions 包带 `· 构建 #N · <sha>`）
 * @param onHeightChanged 实测高度回调，列表用它来定占位高度
 */
@Composable
private fun AboutHero(
    modifier: Modifier = Modifier,
    progress: () -> Float,
    versionDetail: String,
    onHeightChanged: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChanged(it.height) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 应用图标：用 drawable/icon.webp（位图，背景已经烘进图里），
        // 方形图标压在动态背景上太硬，用 squircle 裁个圆角，观感与系统启动器一致。
        // squircleClip 写在 graphicsLayer 之后（即更内层）：蒙版会被那个缩放图层一起缩放，
        // 图标缩小时圆角不会「不跟着变」
        Image(
            painter = painterResource(R.drawable.icon),
            contentDescription = null,
            modifier = Modifier
                .size(HeroIconSize)
                .graphicsLayer {
                    // 图标最后才退场：从 28% 进度开始，用 16% 的行程缩到 0.94 并淡出
                    val iconProgress = ((progress() - 0.28f) / 0.16f).coerceIn(0f, 1f)
                    alpha = 1f - iconProgress
                    scaleX = 1f - iconProgress * 0.06f
                    scaleY = 1f - iconProgress * 0.06f
                }
                .squircleClip(cornerRadius = HeroIconCorner),
        )
        // 应用名：34sp 大字必须同时给 lineHeight，
        // 否则 miuix 的 Text 只覆盖 fontSize、行高仍沿用 body 样式的值，字会被裁
        Text(
            // 用 app_display_name 而不是 app_name：debug 变体把 app_name 改成了「爱发电 Debug」
            // 给桌面图标用，关于页要的是干净的产品名
            text = stringResource(R.string.app_display_name),
            style = MiuixTheme.textStyles.title1.copy(
                fontSize = 34.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = 12.dp)
                .graphicsLayer {
                    alpha = 1f - ((progress() - 0.14f) / 0.14f).coerceIn(0f, 1f)
                },
        )
        // 版本号 + flavor 标记 + 构建标识：最先退场。
        // 字母用 onSurface + SemiBold，而不是主题色：浅色主题的 primary 是浅紫/浅蓝，
        // 压在水面上对比度太低（深浅两套主题下都要能看清），这里只靠「更实 + 更重」做区分
        val badgeColor = MiuixTheme.colorScheme.onSurface
        val versionText = remember(versionDetail, badgeColor) {
            buildAnnotatedString {
                append("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ")
                withStyle(SpanStyle(color = badgeColor, fontWeight = FontWeight.SemiBold)) {
                    append(HeroFlavorBadge)
                }
                append(versionDetail)
            }
        }
        Text(
            text = versionText,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, start = 32.dp, end = 32.dp)
                .graphicsLayer {
                    alpha = 1f - ((progress() - 0.04f) / 0.12f).coerceIn(0f, 1f)
                },
        )
    }
}

/**
 * 「本版更新」卡：当前版本的改动直接铺在关于页上，不用点进去才看得到。
 *
 * 结构：顶部「更新日志」是可点进完整页面的入口（同时充当这张卡标题）→ 版本号 + 日期
 * → 分割线 → 全部条目（不截断、不折叠：当前版本通常就几条，折叠反而多一次点击）。
 * 数据来自 [com.hrsthrt74.afdian.data.content.Changelog]（打包进 assets 的 CHANGELOG.md），
 * 与更新日志页同源；条目渲染复用 `ReleaseNoteItems`，两处样式不会各自漂移。
 *
 * @param note 当前版本的更新记录；为 null（CHANGELOG 里还没写这一版）时只剩入口那一行
 * @param colors 卡片配色（有海水时是半透明，与本页其他卡片一致）
 * @param onShowAll 打开完整更新日志
 */
@Composable
private fun UpdateLogCard(
    note: ReleaseNote?,
    colors: CardColors,
    onShowAll: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        colors = colors,
    ) {
        Column {
            ArrowPreference(title = "更新日志", onClick = onShowAll)
            if (note != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // top 10dp：ArrowPreference 自带的下边距很小，不给的话
                        // 「更新日志」和 v1.0.2 会挤成上下两行贴在一起
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "v${note.version}",
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    note.date?.let { date ->
                        Text(
                            text = date,
                            style = MiuixTheme.textStyles.footnote1,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                }
                // 把「这一版是什么」和「改了什么」分开。线从卡片边缘开始会更像分区，
                // 但和上面 ArrowPreference 的内边距对不齐，所以左右各留 16dp 跟着内容走
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ReleaseNoteItems(
                    items = note.items,
                    // 下边距只留 14dp：条目自己不再带尾部间距（见 ReleaseNoteItems），
                    // 这里就是正文到卡片底边的实际距离
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                )
            }
        }
    }
}

/**
 *
 * 画了海水时给 `surfaceContainer` 加一层透明度，让水纹从卡片里透出来——
 * 这是「整页背景」能成立的关键，卡片全不透明的话水面只在卡片缝隙里露几条边，白做。
 *
 * alpha 是「看得见水 / 文字够清楚」的折中：深色模式水面很暗，卡片可以更透一点；
 * 浅色模式水面偏亮，卡片要更实才压得住正文。
 *
 * @param showWater 当前页面是否画了动态背景
 */
@Composable
private fun aboutCardColors(showWater: Boolean): CardColors {
    if (!showWater) return CardDefaults.defaultColors()
    val scheme = MiuixTheme.colorScheme
    val alpha = if (isDarkColorScheme(scheme)) 0.58f else 0.64f
    return CardDefaults.defaultColors(color = scheme.surfaceContainer.copy(alpha = alpha))
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
