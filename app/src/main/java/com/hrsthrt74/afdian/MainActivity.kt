package com.hrsthrt74.afdian

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import com.hrsthrt74.afdian.data.storage.buildImageDiskCache
import com.hrsthrt74.afdian.ui.screens.auth.DisclaimerDialog
import com.hrsthrt74.afdian.ui.screens.auth.ExpiredScreen
import com.hrsthrt74.afdian.ui.screens.auth.LoginScreen
import com.hrsthrt74.afdian.ui.screens.main.MainScreen
import com.hrsthrt74.afdian.ui.theme.AfdianBrandColor
import com.hrsthrt74.afdian.ui.theme.AfdianTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // 界面是否已可显示：SplashScreen 依据它决定是否继续停留在启动画面上。
    // 用 Activity 级字段（而非 Compose state）让 keepOnScreenCondition 直接读取。
    private var appReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()  // 启动界面大肥鱼
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 首个真正可见的界面（主页布局就绪 / 登录页 / 免责声明）出现前不放行，
        // 冷启动已登录时直接从启动画面无缝进入主界面，不再闪「登录中」
        splash.setKeepOnScreenCondition { !appReady.value }

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
    // 调试日志开关：订阅 DataStore 并写入全局 AppLog，切换后即时生效、无需重启。
    // SideEffect 在每次组合成功后执行，赋值幂等，重复执行无副作用
    val debugLogging by settingsStore.debugLogging.collectAsState(initial = false)
    SideEffect {
        if (AppLog.debugEnabled != debugLogging) {
            AppLog.debugEnabled = debugLogging
            // 开关状态本身也打一条，方便在 Logcat 里确认当前档位
            AppLog.d("AppLog", "调试日志已${if (debugLogging) "开启" else "关闭"}")
        }
    }

    AfdianTheme(primaryColor = if (useAfdianColor) AfdianBrandColor else null, colorMode = colorMode) {
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
                    MainScreen(onReady = { mainReady = true }, onLogout = session::logout)
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
