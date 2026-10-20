package com.hrsthrt74.afdian

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.hrsthrt74.afdian.data.auth.AuthSession
import com.hrsthrt74.afdian.data.auth.AuthState
import com.hrsthrt74.afdian.data.auth.CurrentAccount
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_DEFAULT
import com.hrsthrt74.afdian.data.settings.KeyColorPreset
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import com.hrsthrt74.afdian.data.storage.buildImageDiskCache
import com.hrsthrt74.afdian.ui.components.LocalRollingNumberEnabled
import com.hrsthrt74.afdian.ui.screens.auth.DisclaimerScreen
import com.hrsthrt74.afdian.ui.screens.auth.ExpiredScreen
import com.hrsthrt74.afdian.ui.screens.auth.LoginScreen
import com.hrsthrt74.afdian.ui.screens.auth.WrongEditionScreen
import com.hrsthrt74.afdian.ui.screens.main.MainScreen
import com.hrsthrt74.afdian.ui.theme.AfdianBrandColor
import com.hrsthrt74.afdian.ui.theme.AfdianTheme
import com.hrsthrt74.afdian.ui.theme.LocalDataCardFontFamily
import com.hrsthrt74.afdian.ui.theme.rememberMiSansCondensedFontFamily
import com.hrsthrt74.afdian.ui.util.rememberSessionViewModelStoreOwner
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // 界面是否已可显示：SplashScreen 依据它决定是否继续停留在启动画面上。
    // 用 Activity 级字段（而非 Compose state）让 keepOnScreenCondition 直接读取。
    private var appReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()  // 启动界面大肥鱼
        super.onCreate(savedInstanceState)
        // 关闭 enableEdgeToEdge 默认给系统导航栏垫的半透明白色 scrim（三键导航下尤其明显），
        // 显式传全透明样式，让内容真正延伸到导航栏底下
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        // API 29+ 系统默认强制「导航栏对比度」：即使导航栏全透明，三键导航模式下
        // 系统也会自己垫一层半透明白 scrim（手势条不受影响）。这里显式关掉它。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // 首个真正可见的界面（主页布局就绪 / 登录页 / 免责声明）出现前不放行，
        // 冷启动已登录时直接从启动画面无缝进入主界面，不再闪「登录中」
        splash.setKeepOnScreenCondition { !appReady.value }

        // 「装错版本」拦截：legacy 兼容版跑在 API >= standard 的 minSdk 上时，
        // 属于装错包，直接阻断。判定早于时间炸弹并立即 return ——
        // 被拦的包不必再算过期时间，也不会初始化 AfdianApp 里的 DataStore / ImageLoader。
        // 这是全工程唯一的 Activity（Manifest 里没有第二个入口、没有 deep link），拦在此处即无绕过路径。
        if (EditionGuard.isBlocked()) {
            AppLog.w(
                "EditionGuard",
                "legacy 包运行在 API ${Build.VERSION.SDK_INT}" +
                    "（阈值 ${EditionGuard.STANDARD_MIN_SDK}），阻断启动；" +
                    "variant=${BuildConfig.APP_VARIANT_NAME} package=${BuildConfig.APPLICATION_ID}",
            )
            setContent {
                // 阻断页不能等主题 DataStore，传 false 让它首帧就组合出来
                AfdianTheme(waitForThemeSettings = false) {
                    // 页面真正组合后才撤掉启动画面，避免深色模式下先露一帧白底
                    SideEffect { appReady.value = true }
                    WrongEditionScreen(onExit = ::finishAndRemoveTask)
                }
            }
            return
        }

        // 内测版「时间炸弹」逻辑
        val expired = BuildConfig.DEBUG || BuildConfig.INTERNAL_BUILD
        val now = System.currentTimeMillis()
        val expiresAt = TimeBomb.expiresAt(BuildConfig.BUILD_TIMESTAMP_MS)
        if (expired && TimeBomb.isExpired(BuildConfig.BUILD_TIMESTAMP_MS, now)) {
            AppLog.w("TimeBomb", "时间炸弹已过期，阻断应用启动 expiresAt=$expiresAt")
        } else {
            AppLog.d("TimeBomb", "时间炸弹检查通过 enabled=$expired expiresAt=$expiresAt")
        }
        setContent {
            if (expired && TimeBomb.isExpired(BuildConfig.BUILD_TIMESTAMP_MS, System.currentTimeMillis())) {
                appReady.value = true
                // 时间炸弹触发
                AfdianTheme {
                    ExpiredScreen(
                        buildTimestampMs = BuildConfig.BUILD_TIMESTAMP_MS,
                        expiresAtMs = expiresAt,
                        onExit = ::finishAndRemoveTask,
                    )
                }
        } else {
            AfdianApp(appReady, expiresAtMs = expiresAt)
        }
        }
    }
}

@Composable
private fun AfdianApp(
    appReady: MutableState<Boolean>,
    expiresAtMs: Long? = null,
    session: AuthSession = viewModel(),
) {
    val context = LocalContext.current
    // SettingsStore 内部是进程级单例 DataStore，remember 一个包装对象即可
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    // 图片缓存上限：null = DataStore 还没读出来。
    // 它是 ImageLoader 的「构建期」配置（见下），所以必须先读到真实值再放行 UI ——
    // 否则首页可能先用默认值触发首次图片加载、把 ImageLoader 定型，用户设的上限要等下次启动才生效。
    val imageCacheLimitState = settingsStore.imageCacheLimit.collectAsState(initial = null)

    // 免责声明同意状态：null 表示「还没从 DataStore 读出来」。
    //
    // 必须是三态而不是 initial = false：登出会让组合从 MainScreen 切到未登录分支，
    // 那时 collectAsState 重新订阅，首帧拿到 false 就会先把免责声明页画出来，
    // 下一帧读到真实的 true 才切到登录页 —— 已同意过的用户每次登出都会看到它闪一下。
    //
    // 放在登录态分支之前收集，已登录期间值就已经就绪，登出瞬间可直接渲染正确页面。
    val disclaimerAccepted: Boolean? by settingsStore.disclaimerAccepted
        .map<Boolean, Boolean?> { it }
        .collectAsState(initial = null)

    // 全局 ImageLoader：开启 crossfade，并给磁盘缓存套上用户设置的体积上限。
    // 必须在任何 AsyncImage 首次组合前调用（setSafe 要求早于 Coil 首次 get），
    // 放在根组合 AfdianApp 顶部即满足；各调用点无需再单独包 ImageRequest。
    setSingletonImageLoaderFactory { loaderContext ->
        // 工厂是懒执行的（Coil 首次取 ImageLoader 时才调用），此时上限一定已读出（见上面的门）。
        // 这里读 State 对象的 .value 而不是直接捕获值：setSafe 只在首次记下这个工厂，
        // 直接捕获会把它永久钉在初始的 null 上。
        val limit = imageCacheLimitState.value ?: ImageCacheLimit.Default
        ImageLoader.Builder(loaderContext)
            .crossfade(true)
            .diskCache { buildImageDiskCache(limit) }
            .build()
    }

    // 主题色开关：订阅 DataStore，设置页改动后立即生效（替代旧 SP 监听器）
    val useAfdianColor by settingsStore.useAfdianColor.collectAsState(initial = true)
    // 配色模式：0=跟随系统 1=浅色 2=深色
    val colorMode by settingsStore.colorMode.collectAsState(initial = 0)
    // Monet 配色总开关；开启后 colorMode 的三档改用 Monet 色板
    val monetEnabled by settingsStore.monetEnabled.collectAsState(initial = false)
    // Monet 种子色预设；仅在 monetEnabled 为 true 时参与主题
    val keyColor by settingsStore.keyColor.collectAsState(initial = KeyColorPreset.Default)
    // 调试日志开关：订阅 DataStore 并写入全局 AppLog，切换后即时生效、无需重启。
    // initial 跟随构建类型默认值，避免 internal 版首帧先按 false 误关一次再被 DataStore 读回 true
    val debugLogging by settingsStore.debugLogging.collectAsState(initial = BuildConfig.INTERNAL_BUILD)
    // 数字滚轮日志开关：滚轮动画日志量极大（每个数字位都打一条），默认关闭 = 静音该类 D 日志。
    // initial 与 DataStore 默认值（false）一致，避免首帧按 true 误放行一次
    val odometerLogging by settingsStore.odometerLogging.collectAsState(initial = false)
    // 数据卡数字滚动动画总开关：initial 与 DataStore 里的默认值（true）保持一致，
    // 否则冷启动首帧会先按 false 渲染一次普通文本，再跳回滚轮。
    val rollingNumberEnabled by settingsStore.rollingNumberEnabled.collectAsState(initial = true)
    // 数据卡数值窄体字体：开关 + wdth 宽度。
    // initial 与 DataStore 默认值保持一致，避免冷启动首帧先按系统字体渲染一次再换字体。
    val dataCardCustomFontEnabled by settingsStore.dataCardCustomFontEnabled.collectAsState(initial = false)
    val dataCardFontWidth by settingsStore.dataCardFontWidth
        .collectAsState(initial = DATACARD_FONT_WIDTH_DEFAULT)
    // 字体族始终构建（本身只是轻量数据对象，真正派生 Typeface 只发生在数值实际排版时）；
    // 开关关闭时下发 null，语义即「数值用系统默认字体」。
    val dataCardFontFamily = rememberMiSansCondensedFontFamily(dataCardFontWidth)
    val providedDataCardFontFamily = if (dataCardCustomFontEnabled) dataCardFontFamily else null
    SideEffect {
        if (AppLog.debugEnabled != debugLogging) {
            AppLog.debugEnabled = debugLogging
            // 开关状态本身也打一条，方便在 Logcat 里确认当前档位
            AppLog.d("AppLog", "调试日志已${if (debugLogging) "开启" else "关闭"}")
        }
        // 分类日志静音集合：各分类开关默认关 = 对应 tag 静音，按需组装后一次性写入。
        // 以后新增「量大但偶发需要」的日志分类时，在此追加一行即可
        val mutedTags = buildSet {
            if (!odometerLogging) add(AppLog.TAG_ODOMETER)
        }
        if (AppLog.mutedTags != mutedTags) {
            AppLog.mutedTags = mutedTags
        }
    }

    AfdianTheme(
        primaryColor = if (useAfdianColor) AfdianBrandColor else null,
        colorMode = colorMode,
        monetEnabled = monetEnabled,
        keyColor = keyColor,
    ) {
        // 登录态：Loading 期间不渲染任何界面，读完 token 才放行，避免登录页闪烁
        val authState by session.state.collectAsState()
        // 缓存上限读出来之前也不渲染：一旦有图片开始加载，Coil 的 ImageLoader 就被定型了
        // （磁盘缓存上限是构建期配置，之后改不了），见上方 imageCacheLimitState 的说明。
        if (imageCacheLimitState.value != null) {
            when (authState) {
                AuthState.Loading -> Unit

                is AuthState.LoggedIn -> {
                    // 主页布局是否已经测量完成（MainScreen 两帧后回调）
                    var mainReady by rememberSaveable { mutableStateOf(false) }
                    // 刚提交登录的账号：过渡层继续展示登录页时用它回填输入框
                    val lastAccount by session.lastAccount.collectAsState()
                    // 会话级 ViewModel 作用域：登出时整批销毁、重新登录时整批重建，
                    // 避免复用上一个账号的 MainViewModel / MessagesViewModel / HistoryViewModel
                    val sessionViewModelOwner = rememberSessionViewModelStoreOwner()
                    // 只有真正登出（登录态已不是 LoggedIn）才销毁会话内的 ViewModel。
                    // Activity 因旋转屏幕、切换深色模式而重建时登录态仍是 LoggedIn，
                    // 此时必须保留，否则一次旋转就等于把首页数据全部清空重拉。
                    DisposableEffect(sessionViewModelOwner) {
                        onDispose {
                            if (session.state.value !is AuthState.LoggedIn) {
                                sessionViewModelOwner.clear()
                            }
                        }
                    }
                    // 数字滚动动画总开关在这里下发为组合局部（而不是让 MainScreen 往全局对象里写）：
                    // 数据卡读到它就会订阅，开关一变必然重组并切换渲染方式，
                    // 不会再出现「开关关了、卡片还在播动画」。
                    CompositionLocalProvider(
                        // ViewModel 作用域必须包在最外层：MainScreen 及其内部所有 viewModel() 都要落进会话里
                        LocalViewModelStoreOwner provides sessionViewModelOwner,
                        LocalRollingNumberEnabled provides rollingNumberEnabled,
                        // 数值窄体字体同样以组合局部下发：卡片读到它就订阅，开关/宽度一变必然重组
                        LocalDataCardFontFamily provides providedDataCardFontFamily,
                    ) {
                        // 在会话作用域内取 MainViewModel 并显式传给 MainScreen：
                        // 过渡层什么时候撤，取决于它手里的首屏数据，而不只是布局有没有量完。
                        val mainViewModel: MainViewModel = viewModel()
                        val creatorCards by mainViewModel.creatorCards.collectAsState()
                        val homeLoading by mainViewModel.isLoading.collectAsState()
                        // 账号身份：首页的模块布局/顺序等设置都按 user_id 分区，
                        // 身份没绑定前读到的是空分区的默认值，此时露主页就会先闪一版错误布局
                        val currentOwner by CurrentAccount.userId.collectAsState()
                        // 必须观察到一次「正在加载」：refresh 在协程里启动，首帧 isLoading 还是 false，
                        // 不记这个窗口就会把「还没开始」误判成「已经结束」，过渡层提前撤掉。
                        var sawHomeLoading by remember { mutableStateOf(false) }
                        LaunchedEffect(homeLoading) {
                            if (homeLoading) sawHomeLoading = true
                        }
                        // 兜底超时：弱网/接口异常时不能让用户永远停在过渡页
                        var waitTimedOut by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(HOME_FIRST_LOAD_TIMEOUT_MS)
                            waitTimedOut = true
                        }
                        // 创作者卡片是切号后最刺眼的一块：为空时继续盖着，
                        // 等它出数据、本轮刷新结束、或超时再露主页。
                        // 冷启动有缓存回填时 creatorCards 一上来就非空，因此依旧秒进。
                        val creatorDataSettled = creatorCards.isNotEmpty() ||
                            waitTimedOut ||
                            (sawHomeLoading && !homeLoading)
                        // 账号身份绑定与数据就绪必须同时满足：只等数据的话，
                        // 首页会先用空分区的默认布局渲染，等身份绑定时再整页跳一次布局。
                        // 超时兜底不看身份：弱网下宁可显示默认布局，也不能永远卡在过渡页。
                        val accountReady = currentOwner != null
                        val canShowMain = mainReady &&
                            (waitTimedOut || (creatorDataSettled && accountReady))
                        if (canShowMain) appReady.value = true
                        // 注意组合顺序：先 MainScreen 后过渡层，后者才能绘制在上层。
                        MainScreen(
                            onReady = { mainReady = true },
                            onLogout = session::logout,
                            viewModel = mainViewModel,
                        )
                        if (!canShowMain) {
                            // 过渡层继续沿用登录页：表单和输入框原地保留，按钮转「登录中…」，
                            // 而不是整页换成另一个加载页 —— 那会让用户以为自己填的内容被吞了。
                            // isLoggingIn 固定传 true：此时登录已经成功，表单只用于展示过渡。
                            LoginScreen(
                                onLogin = session::login,
                                onCodeLogin = session::loginWithCode,
                                onSendCode = session::sendLoginCode,
                                isLoggingIn = true,
                                initialAccount = lastAccount,
                            )
                        }
                    }
                }

                // 未登录：先确认免责声明。状态还没读出来时什么都不渲染，
                // 否则会先画一帧免责声明、再跳登录页（见上方 disclaimerAccepted 的注释）
                AuthState.LoggedOut -> {
                    val accepted = disclaimerAccepted
                    if (accepted != null) {
                        // 登录页/免责声明本身就是首个界面，组合即放行 SplashScreen
                        SideEffect { appReady.value = true }
                        // 免责声明确认后使用 Compose 默认转场，避免页面从免责声明硬切到登录页。
                        AnimatedContent(
                            targetState = accepted,
                            label = "disclaimerToLogin",
                        ) { isAccepted ->
                            if (!isAccepted) {
                                DisclaimerScreen(
                                    onAgree = { scope.launch { settingsStore.acceptDisclaimer() } },
                                    onDisagree = { (context as? Activity)?.finishAndRemoveTask() },
                                )
                            } else {
                                val isLoggingIn by session.isLoggingIn.collectAsState()
                                val error by session.error.collectAsState()
                                // 验证码登录的配套状态：发码进度、60 秒倒计时、图形验证码图片
                                val isSendingCode by session.isSendingCode.collectAsState()
                                val codeCooldown by session.codeCooldown.collectAsState()
                                val captchaBytes by session.captchaBytes.collectAsState()
                                LoginScreen(
                                    onLogin = session::login,
                                    onCodeLogin = session::loginWithCode,
                                    onSendCode = session::sendLoginCode,
                                    isLoggingIn = isLoggingIn,
                                    isSendingCode = isSendingCode,
                                    codeCooldownSeconds = codeCooldown,
                                    captchaBytes = captchaBytes,
                                    errorMessage = error,
                                    buildExpiresAtMs = expiresAtMs,
                                    onClearError = session::clearError,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 登录后等待首页首屏数据的上限。
 *
 * 超过这个时间即使创作者数据还没到也放行：弱网或接口异常时，
 * 把用户永远挡在过渡页比看到一块空模块更糟。
 */
private const val HOME_FIRST_LOAD_TIMEOUT_MS = 8_000L
