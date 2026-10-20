package com.hrsthrt74.afdian.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.seconds
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.squircle.squircleClip

private const val DISCLAIMER_COUNTDOWN_SECONDS = 5

/**
 * 首次启动免责声明。
 *
 * 这是认证前的强制页面，不使用 Dialog，避免正文过长时被弹层尺寸截断。
 * 页面不提供返回入口；用户必须明确选择退出或等待倒计时结束后继续。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun DisclaimerScreen(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
    onBack: (() -> Unit)? = null,
    backStack: NavBackStack? = null,
) {
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val scope = rememberCoroutineScope()
    var hasReachedBottom by remember { mutableStateOf(false) }

    // 到底状态只允许从 false 变为 true。底栏出现等待文案后高度会变化，
    // 如果根据当前 canScrollForward 反推状态，就会在底部反复切换按钮文案并造成抖动。
    LaunchedEffect(listState, onBack) {
        if (onBack == null) {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                layoutInfo.totalItemsCount > 0 &&
                    layoutInfo.visibleItemsInfo.lastOrNull()?.index == layoutInfo.totalItemsCount - 1 &&
                    !listState.canScrollForward
            }.collectLatest { reachedBottom ->
                if (reachedBottom) {
                    hasReachedBottom = true
                }
            }
        }
    }
    var remainingSeconds by remember {
        mutableIntStateOf(if (onBack == null) DISCLAIMER_COUNTDOWN_SECONDS else 0)
    }

    // 首次进入免责声明页就开始计时；设置页查看模式不参与确认倒计时。
    LaunchedEffect(onBack) {
        if (onBack == null) {
            repeat(DISCLAIMER_COUNTDOWN_SECONDS) {
                delay(1.seconds)
                remainingSeconds--
            }
            AppLog.d("Disclaimer", "免责声明倒计时结束，允许继续")
        }
    }

    Scaffold(
        topBar = {
            // 与其他完整页面保持一致：模糊顶栏、可收缩大标题、双击回顶。
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "免责声明",
                        largeTitle = "免责声明",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            if (onBack != null) {
                                BackButton(onBack = onBack, backStack = requireNotNull(backStack))
                            }
                        },
                    )
                }
            }
        },
        bottomBar = {
            if (onBack == null) {
                // 底栏作为 Scaffold 的 bottomBar 参与布局，才能完整铺到屏幕底部并正确避让导航栏。
                // 与顶栏一样由外层 BlurredBar 负责采样，固定关闭渐进模糊。
                BlurredBar(backdrop, themeSettings.enableBlur, progressive = false) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val canConfirm = hasReachedBottom && remainingSeconds == 0
                        Button(
                            onClick = {
                                if (!hasReachedBottom) {
                                    val nextItem = (listState.firstVisibleItemIndex + 3)
                                        .coerceAtMost((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                                    scope.launch {
                                        listState.animateScrollToItem(nextItem)
                                    }
                                } else if (remainingSeconds == 0) {
                                    AppLog.d("Disclaimer", "用户同意免责声明")
                                    onAgree()
                                }
                            },
                            enabled = !hasReachedBottom || remainingSeconds == 0,
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (canConfirm) {
                                ButtonDefaults.buttonColorsPrimary()
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Text(
                                when {
                                    !hasReachedBottom -> "继续阅读"
                                    remainingSeconds > 0 -> "确定（${remainingSeconds}s）"
                                    else -> "确定"
                                },
                            )
                        }
                        Button(
                            onClick = {
                                AppLog.d("Disclaimer", "用户不同意免责声明，退出应用")
                                onDisagree()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("不同意并退出")
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                    item(key = "intro") {
                        DisclaimerSection(
                            icon = MiuixIcons.Demibold.Info,
                            title = "请在使用前阅读",
                            text = "本应用是由个人开发者制作的爱发电第三方客户端，仅用于提供另一种访问方式和学习交流。继续使用前，请确认你已理解以下说明。",
                        )
                    }
                    item(key = "third-party") {
                        DisclaimerSection(
                            icon = MiuixIcons.Demibold.GridView,
                            title = "第三方客户端声明",
                            text = "本应用与爱发电（Afdian）及其运营主体不存在隶属、代理、授权、赞助或合作关系。爱发电的名称、商标、网站、接口和相关内容归其权利人所有。本应用不代表爱发电，也不保证爱发电会持续支持本应用。",
                        )
                    }
                    item(key = "account") {
                        DisclaimerSection(
                            icon = MiuixIcons.Demibold.Contacts,
                            title = "账号与凭据",
                            text = "登录账号、密码、验证码、登录凭据及你在平台中的操作均由你本人负责。请勿在非本人设备上登录，也不要向任何人泄露密码或验证码。若你发现账号异常，请立即通过爱发电官方渠道处理。",
                        )
                    }
                    item(key = "network") {
                        DisclaimerSection(
                            icon = MiuixIcons.Demibold.UploadCloud,
                            title = "网络与接口",
                            text = "本应用通过网络访问爱发电及其相关服务。服务端接口、页面结构、访问限制和可用性可能随时变化，导致部分功能异常、延迟或不可用。本应用不保证数据实时、完整或准确，也不承诺任何功能永久可用。",
                        )
                    }
                    item(key = "advanced-features") {
                        DisclaimerSection(
                            icon = MiuixIcons.Demibold.Album,
                            title = "高级功能说明",
                            text = "本应用提供原客户端未提供的高级功能，包括创作者数据图表、收入明细中的问 AI 功能、扩展创作者数据以及创作者提现提醒等。这些功能的数据一部分来自爱发电官方已有接口，另一部分来自未直接提供的接口，经由其他接口返回的数据计算、整理或推导而来。我们已尽量对数据来源和含义进行消歧义，但由于部分数据并非通过对应的直接接口获取，计算结果不可避免可能存在误差，不能视为官方数据或财务依据，请以爱发电官方网页或官方客户端展示的结果为准。",
                        )
                    }
                    item(key = "content") {
                        DisclaimerSection(
                            icon = MiuixIcons.Demibold.Notes,
                            title = "内容与平台交易",
                            text = "创作者发布的文章、评论、私信、商品和其他内容由相应用户或平台提供，不代表本应用立场。本应用内不提供提现、发电（赞助创作者）、购买、退款或其他任何与金钱有关的操作，也不参与、不经手或担保相关交易。需要进行赞助、购买、提现等操作时，本应用会引导你前往爱发电官方网页或官方客户端继续；相关交易和内容权益问题请直接联系创作者或爱发电官方。",
                        )
                    }
                    item(key = "data") {
                        DisclaimerSection(
                            icon = MiuixIcons.Demibold.Folder,
                            title = "数据处理与风险",
                            text = "除实现功能所必需的数据外，本第三方客户端不会额外进行追踪或收集信息。为改进稳定性，应用可能收集崩溃日志；日志中的账号、令牌、Cookie、私信内容等敏感信息会先脱敏，不会以明文记录。应用还会在设备本地保存实现登录和功能所需的凭据、缓存、历史记录及设置；部分功能会将请求发送至爱发电或图片等内容服务。请在使用前了解设备安全、网络环境和平台隐私政策，并自行承担因设备丢失、系统环境、网络攻击或平台策略变化造成的风险。",
                        )
                    }
                    item(key = "responsibility") {
                        DisclaimerSection(
                            icon = MiuixIcons.Demibold.ConvertFile,
                            title = "使用责任",
                            text = "你应遵守爱发电平台规则、适用法律法规及创作者内容的授权范围，不得利用本应用进行绕过限制、批量滥用、侵犯他人权益或其他违法违规行为。因你的账号、设备或使用行为造成的后果，由你自行承担。",
                        )
                    }
                    item(key = "last") {
                        DisclaimerSection(
                            icon = MiuixIcons.Demibold.Ok,
                            title = "你的选择",
                            text = "如果你不同意上述说明，请点击“不同意并退出”并停止使用本应用。点击“同意并继续”表示你已阅读并理解本页内容，但不影响你依法享有的消费者、隐私或其他法定权利。",
                        )
                    }
            }
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
}

@Composable
private fun DisclaimerSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .squircleClip(8.dp)
                        .background(MiuixTheme.colorScheme.primary)
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
