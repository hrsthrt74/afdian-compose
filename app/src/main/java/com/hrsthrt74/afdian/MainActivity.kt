package com.hrsthrt74.afdian

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.hrsthrt74.afdian.data.auth.AuthSession
import com.hrsthrt74.afdian.data.auth.AuthState
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_DEFAULT
import com.hrsthrt74.afdian.data.settings.KeyColorPreset
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import com.hrsthrt74.afdian.data.storage.buildImageDiskCache
import com.hrsthrt74.afdian.ui.components.LocalRollingNumberEnabled
import com.hrsthrt74.afdian.ui.screens.auth.DisclaimerDialog
import com.hrsthrt74.afdian.ui.screens.auth.ExpiredScreen
import com.hrsthrt74.afdian.ui.screens.auth.LoginScreen
import com.hrsthrt74.afdian.ui.screens.auth.WrongEditionScreen
import com.hrsthrt74.afdian.ui.screens.main.MainScreen
import com.hrsthrt74.afdian.ui.theme.AfdianBrandColor
import com.hrsthrt74.afdian.ui.theme.AfdianTheme
import com.hrsthrt74.afdian.ui.theme.LocalDataCardFontFamily
import com.hrsthrt74.afdian.ui.theme.rememberMiSansCondensedFontFamily
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
                    // 主页首帧布局就绪前，继续把登录页（「登录中…」转圈态）盖在上面，
                    // 避免切屏瞬间露出空白背景闪一下；MainScreen 两帧后回调 onReady 再撤掉。
                    // 注意组合顺序：先 MainScreen 后 LoginScreen，后者才能绘制在上层。
                    var mainReady by rememberSaveable { mutableStateOf(false) }
                    if (mainReady) appReady.value = true
                    // 数字滚动动画总开关在这里下发为组合局部（而不是让 MainScreen 往全局对象里写）：
                    // 数据卡读到它就会订阅，开关一变必然重组并切换渲染方式，
                    // 不会再出现「开关关了、卡片还在播动画」。
                    CompositionLocalProvider(
                        LocalRollingNumberEnabled provides rollingNumberEnabled,
                        // 数值窄体字体同样以组合局部下发：卡片读到它就订阅，开关/宽度一变必然重组
                        LocalDataCardFontFamily provides providedDataCardFontFamily,
                    ) {
                        MainScreen(onReady = { mainReady = true }, onLogout = session::logout)
                    }
                    if (!mainReady) {
                        LoginScreen(onLogin = session::login, isLoggingIn = true, errorMessage = null)
                    }
                }

                // 未登录：先确认免责声明（读取未完成前按未同意处理）
                AuthState.LoggedOut -> {
                    // 登录页/免责声明本身就是首个界面，组合即放行 SplashScreen
                    SideEffect { appReady.value = true }
                    val accepted by settingsStore.disclaimerAccepted.collectAsState(initial = false)
                    if (!accepted) {
                        DisclaimerDialog(
                            onAgree = { scope.launch { settingsStore.acceptDisclaimer() } },
                            onDisagree = { (context as? Activity)?.finishAndRemoveTask() },
                        )
                    } else {
                        val isLoggingIn by session.isLoggingIn.collectAsState()
                        val error by session.error.collectAsState()
                        LoginScreen(
                            onLogin = session::login,
                            isLoggingIn = isLoggingIn,
                            errorMessage = error,
                            buildExpiresAtMs = expiresAtMs,
                        )
                    }
                }
            }
        }
    }
}
