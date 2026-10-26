package com.hrsthrt74.afdian

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.hrsthrt74.afdian.data.auth.AccountSwitchPhase
import com.hrsthrt74.afdian.data.auth.AuthSession
import com.hrsthrt74.afdian.data.auth.AuthState
import com.hrsthrt74.afdian.data.auth.CurrentAccount
import com.hrsthrt74.afdian.data.auth.SavedAccountSummary
import com.hrsthrt74.afdian.data.settings.DATACARD_FONT_WIDTH_DEFAULT
import com.hrsthrt74.afdian.data.settings.KeyColorPreset
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import com.hrsthrt74.afdian.data.storage.buildImageDiskCache
import com.hrsthrt74.afdian.ui.components.LocalRollingNumberEnabled
import com.hrsthrt74.afdian.ui.components.account.AccountSwitchOverlay
import com.hrsthrt74.afdian.ui.components.account.AccountSwitcherActions
import com.hrsthrt74.afdian.ui.components.account.LocalAccountSwitcher
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.components.update.UpdateDialog
import com.hrsthrt74.afdian.ui.components.update.openUpdateUrl
import com.hrsthrt74.afdian.ui.navigation.AppNavTransition
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.screens.auth.AddAccountOverlay
import com.hrsthrt74.afdian.ui.screens.auth.DisclaimerScreen
import com.hrsthrt74.afdian.ui.screens.auth.LoginScreen
import com.hrsthrt74.afdian.ui.screens.auth.WebLoginPurpose
import com.hrsthrt74.afdian.ui.screens.auth.WebLoginSheet
import com.hrsthrt74.afdian.ui.screens.auth.WrongEditionScreen
import com.hrsthrt74.afdian.ui.screens.main.MainScreen
import com.hrsthrt74.afdian.ui.screens.main.OssLicensesScreen
import com.hrsthrt74.afdian.ui.screens.main.account.ApiProbeScreen
import com.hrsthrt74.afdian.ui.screens.main.account.FeatureStatusScreen
import com.hrsthrt74.afdian.ui.screens.main.account.LogScreen
import com.hrsthrt74.afdian.ui.screens.main.account.SettingsAboutScreen
import com.hrsthrt74.afdian.ui.screens.main.account.SettingsAppearanceScreen
import com.hrsthrt74.afdian.ui.screens.main.account.SettingsGeneralScreen
import com.hrsthrt74.afdian.ui.screens.main.account.SettingsScreen
import com.hrsthrt74.afdian.ui.screens.main.account.StorageScreen
import com.hrsthrt74.afdian.ui.theme.AfdianBrandColor
import com.hrsthrt74.afdian.ui.theme.AfdianTheme
import com.hrsthrt74.afdian.ui.theme.LocalDataCardFontFamily
import com.hrsthrt74.afdian.ui.theme.rememberMiSansCondensedFontFamily
import com.hrsthrt74.afdian.ui.util.rememberSessionViewModelStoreOwner
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import com.hrsthrt74.afdian.ui.viewmodel.UpdateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius

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
        // 属于装错包，直接阻断并立即 return —— 被拦的包不会初始化 AfdianApp 里的 DataStore / ImageLoader。
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

        // 拦截通过后进入正常界面：只有一条路径，不再按过期状态二选一
        setContent {
            AfdianApp(appReady)
        }
    }
}

@Composable
private fun AfdianApp(
    appReady: MutableState<Boolean>,
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
    // initial 跟随构建类型默认值，避免 debug 版首帧先按 false 误关一次再被 DataStore 读回 true
    val debugLogging by settingsStore.debugLogging.collectAsState(initial = BuildConfig.DEBUG)
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
            // ── 账号相关浮层 ──
            // 都挂在登录态分支之外：切换账号会按 epoch 重建整棵已登录子树，
            // 浮层如果挂进去会跟着被销毁（面板瞬间消失、遮罩闪断）。
            val accounts by session.accounts.collectAsState()
            val currentAccountId by CurrentAccount.userId.collectAsState()
            val invalidAccountIds by session.invalidAccountIds.collectAsState()
            val switchPhase by session.switchPhase.collectAsState()
            val isAddingAccount by session.isAddingAccount.collectAsState()
            val switchNotice by session.switchNotice.collectAsState()
            // 待删除账号：删除确认对话框的标题与文案要用
            var pendingDelete by remember { mutableStateOf<SavedAccountSummary?>(null) }
            val deleteAccountDialog = rememberDialogState()
            // 网页登录弹层的用途：null 表示没在展示。
            // 声明在登录态分支之外：未登录时从登录页发起，已登录时从「添加账号」弹层发起，
            // 两处共用同一个弹层实例，但抓到凭据后的收尾不同。
            var webLoginPurpose by remember { mutableStateOf<WebLoginPurpose?>(null) }
            // 待打开的网页登录用途：「添加账号」弹层退场动画播完后才真正开 Sheet，
            // 避免两个独立窗口（WindowDialog + WindowBottomSheet）同时存在。
            var pendingWebLogin by remember { mutableStateOf<WebLoginPurpose?>(null) }
            // 账号切换弹层的数据与动作：注入组合局部，由「我的」页头像旁的菜单消费。
            // 用 remember 绑定数据快照，避免每次重组都生成新对象导致菜单整棵重组。
            val accountSwitcher = remember(accounts, currentAccountId, invalidAccountIds) {
                AccountSwitcherActions(
                    accounts = accounts,
                    currentUserId = currentAccountId,
                    invalidUserIds = invalidAccountIds,
                    onSwitch = { userId -> session.switchAccount(userId) },
                    onDelete = { account ->
                        pendingDelete = account
                        deleteAccountDialog.show()
                    },
                    onAddAccount = { session.beginAddAccount() },
                    onRefresh = { session.refreshAccountList() },
                )
            }

            // 切换/删除失败的一次性提示：用系统 Toast 而不是页面内浮层。
            // Toast 是独立窗口，不受登录态切换、epoch 重建、对话框层级影响，
            // 提示发出后即使已登录子树被整棵销毁也能正常显示完。
            LaunchedEffect(switchNotice) {
                val message = switchNotice ?: return@LaunchedEffect
                // 先消费再弹：避免 Snackbar/Toast 期间旧值让重组重复触发
                session.consumeSwitchNotice()
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }

            when (authState) {
                AuthState.Loading -> Unit

                is AuthState.LoggedIn -> {
                    // 取一次快照：epoch 决定整棵已登录子树的身份。
                    // 切号时代次变化 → key 变 → 子树连同全部会话级 ViewModel 一起重建。
                    val loggedInState = authState as AuthState.LoggedIn
                    key(loggedInState.epoch) {
                        // 主页布局是否已经测量完成（MainScreen 两帧后回调）
                        var mainReady by rememberSaveable { mutableStateOf(false) }
                        // 刚提交登录的账号：过渡层继续展示登录页时用它回填输入框
                        val lastAccount by session.lastAccount.collectAsState()
                        // 切换阶段：非 Idle 时遮罩由窗口层接管，这里的登录过渡层要让位
                        val switchPhase by session.switchPhase.collectAsState()
                        // 会话级 ViewModel 作用域：每次登录/切号都会拿到一个全新的 Store
                        val sessionViewModelOwner = rememberSessionViewModelStoreOwner(loggedInState.epoch)
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
                            // 账号切换菜单的数据：由「我的」页头像旁的弹层消费
                            LocalAccountSwitcher provides accountSwitcher,
                            LocalRollingNumberEnabled provides rollingNumberEnabled,
                            // 数值窄体字体同样以组合局部下发：卡片读到它就订阅，开关/宽度一变必然重组
                            LocalDataCardFontFamily provides providedDataCardFontFamily,
                        ) {
                            // 在会话作用域内取 MainViewModel 并显式传给 MainScreen：
                            // 过渡层什么时候撤，取决于它手里的首屏数据，而不只是布局有没有量完。
                            val mainViewModel: MainViewModel = viewModel()
                            // 冷启动是否已用磁盘缓存填好首屏
                            val restoredFromCache by mainViewModel.homeRestoredFromCache.collectAsState()
                            // 本次会话是否已完成首轮刷新（ViewModel 自己记账，不受帧合并影响）
                            val firstLoadDone by mainViewModel.hasCompletedFirstLoad.collectAsState()
                            // 账号身份：首页的模块布局/顺序等设置都按 user_id 分区，
                            // 身份没绑定前读到的是空分区的默认值，此时露主页就会先闪一版错误布局
                            val currentOwner by CurrentAccount.userId.collectAsState()
                            // 必须观察到一次「正在加载」的窗口已经不再需要：
                            // ViewModel 用 hasCompletedFirstLoad 直接给出「首轮刷新结束」的事实。
                            // 兜底超时：弱网/接口异常时不能让用户永远停在过渡页
                            var waitTimedOut by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(HOME_FIRST_LOAD_TIMEOUT_MS.milliseconds)
                                waitTimedOut = true
                            }
                            // 首屏放行条件：
                            // - 冷启动有缓存：快照是上一轮的完整结果，直接放行（秒进）；
                            // - 切号 / 首次登录：必须等整轮刷新结束。
                            //   只看「创作者卡片非空」不够 —— 流式首帧只包含最先返回的那个数据源，
                            //   账单类卡片还是 Unavailable，首页会判定「创作者数据」不可显示，
                            //   于是遮罩解除后模块先消失、等账单回来再长出来，就是那 1 秒。
                            val creatorDataSettled = waitTimedOut ||
                                restoredFromCache ||
                                firstLoadDone
                            // 账号身份绑定与数据就绪必须同时满足：只等数据的话，
                            // 首页会先用空分区的默认布局渲染，等身份绑定时再整页跳一次布局。
                            // 超时兜底不看身份：弱网下宁可显示默认布局，也不能永远卡在过渡页。
                            val accountReady = currentOwner != null
                            val canShowMain = mainReady &&
                                (waitTimedOut || (creatorDataSettled && accountReady))
                            if (canShowMain) appReady.value = true
                            // 首屏就绪上报：切换遮罩只认「当前代次」就绪，旧代次的回调会被忽略
                            LaunchedEffect(canShowMain, loggedInState.epoch) {
                                if (canShowMain) session.notifyFirstScreenReady(loggedInState.epoch)
                            }
                            // 注意组合顺序：先 MainScreen 后过渡层，后者才能绘制在上层。
                            MainScreen(
                                onReady = { mainReady = true },
                                onLogout = session::logout,
                                viewModel = mainViewModel,
                            )
                            // 切号时不要弹登录过渡页：那一刻界面正在重建，
                            // 盖上来只会闪一张「用上一个账号回填」的表单，遮罩已经由窗口层负责。
                            if (!canShowMain && switchPhase is AccountSwitchPhase.Idle) {
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
                }

                // 未登录：先确认免责声明。状态还没读出来时什么都不渲染，
                // 否则会先画一帧免责声明、再跳登录页（见上方 disclaimerAccepted 的注释）
                AuthState.LoggedOut -> {
                    val accepted = disclaimerAccepted
                    if (accepted != null) {
                        // 登录页/免责声明本身就是首个界面，组合即放行 SplashScreen
                        SideEffect { appReady.value = true }
                        // 免责声明确认后使用 Compose 默认转场，避免页面从免责声明硬切到登录页。
                        // 这一步是「首次启动闸门 → 登录」的状态切换，不属于导航，所以不进返回栈。
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
                                // 未登录流程同样交给 NavDisplay 承载：登录页与免责声明页是两条真正的路由，
                                // 「推入 / 返回」的转场、系统圆角裁剪、层间遮罩、预测性返回、返回栈里保存的表单状态
                                // 全部复用与主界面完全相同的一套实现，不再在根组合里手写动画。
                                val loginBackStack = rememberNavBackStack<Route>(Route.Login)
                                NavDisplay(
                                    backStack = loginBackStack,
                                    // 栈底（登录页）时 onBack 不会被调用：NavDisplay 只在 size > 1 时接管返回
                                    onBack = { loginBackStack.removeLastOrNull() },
                                    transition = AppNavTransition,
                                    effects = NavDisplayEffects(
                                        cornerClipRadius = rememberNavSystemCornerRadius(),
                                    ),
                                ) {
                                    entry<Route.Login> {
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
                                            onClearError = session::clearError,
                                            // 已保存账号：退出当前账号后其他账号仍留在本地，
                                            // 卡片点一下即可免密切回去（预检失败会弹提示）
                                            savedAccounts = accounts,
                                            invalidAccountIds = invalidAccountIds,
                                            onPickSavedAccount = { userId ->
                                                AppLog.d("Login", "从登录页用已保存账号继续")
                                                session.switchAccount(userId)
                                            },
                                            // 底部协议提示里的免责声明入口：push 一条真正的路由
                                            onOpenDisclaimer = {
                                                AppLog.d("Login", "从登录页打开免责声明查看页")
                                                // 去重：转场期间连点不会重复入栈
                                                //（miuix-nav 栈内 contentKey 重复会直接抛异常闪退）
                                                if (loginBackStack.none { it == Route.Disclaimer }) {
                                                    loginBackStack.add(Route.Disclaimer)
                                                }
                                            },
                                            // 顶栏设置入口：配色、更新通道、免责声明都在设置页，
                                            // 未登录时也要能进去（比如深色模式刺眼，想先改主题再登录）
                                            onOpenSettings = {
                                                AppLog.d("Login", "从登录页打开设置页")
                                                // 去重理由同免责声明：转场期间连点不能重复入栈
                                                if (loginBackStack.none { it == Route.Settings }) {
                                                    loginBackStack.add(Route.Settings)
                                                }
                                            },
                                            // 网页登录入口：打开内嵌 WebView 的登录页弹层，
                                            // 登录成功后从 cookie 里取凭据走正常登录流程
                                            onOpenWebLogin = {
                                                AppLog.d("Login", "从登录页打开网页登录")
                                                webLoginPurpose = WebLoginPurpose.Login
                                            },
                                        )
                                    }
                                    entry<Route.Disclaimer> {
                                        // 只读查看形态：顶栏返回箭头与系统返回都走同一个弹栈，
                                        // backStack 传真实返回栈，返回按钮的长按菜单也就有了「回到上级」的意义
                                        DisclaimerScreen(
                                            onAgree = {},
                                            onDisagree = {},
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                        )
                                    }
                                    // ── 设置页及其二级页 ──
                                    // 设置页内部会 push 下面这些路由（三个分类页 / 存储 / 日志 / 接口调试 /
                                    // 功能完成度 / 开源许可 / 免责声明）。
                                    // miuix-nav 对「push 了未注册的路由」是直接 error() 崩溃，不是点了没反应，
                                    // 所以注册 Route.Settings 的栈必须把这些子页一起注册 ——
                                    // 以后给设置页加新入口时，这里和 MainScreen 的对应 entry 都要同步补。
                                    entry<Route.Settings> {
                                        SettingsScreen(
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                            // 未登录时设置页不渲染账号分组，「退出登录」入口不可达；
                                            // 传空实现只是为了让签名成立，不会真的触发登出
                                            onLogout = {},
                                        )
                                    }
                                    // 三个设置分类页：总览页点击分类行后 push。
                                    // 未登录栈里同样要注册，否则从登录页进设置再点分类会直接崩。
                                    entry<Route.SettingsGeneral> {
                                        SettingsGeneralScreen(
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                        )
                                    }
                                    entry<Route.SettingsAppearance> {
                                        SettingsAppearanceScreen(
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                        )
                                    }
                                    entry<Route.SettingsAbout> {
                                        SettingsAboutScreen(
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                        )
                                    }
                                    entry<Route.Storage> {
                                        StorageScreen(
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                        )
                                    }
                                    entry<Route.Log> {
                                        LogScreen(
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                        )
                                    }
                                    entry<Route.ApiProbe> {
                                        ApiProbeScreen(
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                        )
                                    }
                                    entry<Route.FeatureStatus> {
                                        FeatureStatusScreen(
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                        )
                                    }
                                    entry<Route.OssLicenses> {
                                        OssLicensesScreen(
                                            onBack = { loginBackStack.removeLastOrNull() },
                                            backStack = loginBackStack,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 添加账号：WindowDialog 承载登录表单，成功后自动保存并切过去。
            // 始终组合、用 show 控制显隐，关闭时才能播放退出动画。
            val addLoggingIn by session.isLoggingIn.collectAsState()
            val addError by session.error.collectAsState()
            val addSendingCode by session.isSendingCode.collectAsState()
            val addCooldown by session.codeCooldown.collectAsState()
            val addCaptcha by session.captchaBytes.collectAsState()
            AddAccountOverlay(
                show = isAddingAccount,
                isLoggingIn = addLoggingIn,
                isSendingCode = addSendingCode,
                codeCooldownSeconds = addCooldown,
                captchaBytes = addCaptcha,
                errorMessage = addError,
                initialAccount = "",
                onLogin = session::loginNewAccount,
                onCodeLogin = session::loginNewAccountWithCode,
                onSendCode = session::sendLoginCode,
                onClearError = session::clearError,
                onCancel = session::cancelAddAccount,
                // 网页登录入口：先关掉本弹层，退场动画播完再开网页登录弹层。
                // 「添加账号」是 WindowDialog（独立窗口），网页登录是 WindowBottomSheet
                // （又一个独立窗口），两层窗口同时存在会有 z-order 与焦点问题，
                // 所以这里用 onDismissFinished 把两者严格错开。
                onOpenWebLogin = {
                    AppLog.d("Login", "从添加账号弹层打开网页登录")
                    pendingWebLogin = WebLoginPurpose.AddAccount
                    session.cancelAddAccount()
                },
                onDismissFinished = {
                    // 退场动画结束、窗口已经销毁，此时再开网页登录 Sheet
                    val pending = pendingWebLogin
                    if (pending != null) {
                        pendingWebLogin = null
                        webLoginPurpose = pending
                    }
                },
            )

            // 网页登录弹层：内嵌 WebView 打开爱发电网页版登录页，
            // 登录成功后从 WebView 的 cookie 里取 auth_token 交给 session。
            // 放在登录态分支之外：未登录（登录页入口）与已登录（添加账号入口）两处共用。
            WebLoginSheet(
                show = webLoginPurpose != null,
                onDismiss = { webLoginPurpose = null },
                onToken = { token ->
                    val purpose = webLoginPurpose
                    webLoginPurpose = null
                    when (purpose) {
                        WebLoginPurpose.Login -> session.loginWithToken(token)
                        WebLoginPurpose.AddAccount -> session.loginNewAccountWithToken(token)
                        null -> Unit
                    }
                },
            )

            // 切换遮罩：不可取消，直到新账号首屏就绪或超时兜底
            AccountSwitchOverlay(switchPhase)

            // 删除确认：破坏性操作走统一的 AppDialog 样式
            AppDialog(
                state = deleteAccountDialog,
                title = "删除账号",
                confirmText = "删除",
                destructive = true,
                summary = "确定要删除账号「${pendingDelete?.name?.ifBlank { "该账号" } ?: "该账号"}」吗？",
                onConfirm = {
                    pendingDelete?.let { session.deleteAccount(it.userId) }
                    pendingDelete = null
                },
            )

            // ── 检查更新（冷启动静默） ──
            // 检查本身对用户完全不可见：只有确认有新版才会弹窗。
            // 状态存在进程级 UpdateCenter 里，与设置页的手动检查共享；
            // 「要不要弹窗」由 autoPrompt 这个一次性信号决定，手动检查不会置位它。
            val updateViewModel: UpdateViewModel = viewModel(
                key = "update-auto",
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as Application
                ),
            )
            val updateState by updateViewModel.uiState.collectAsState()
            val updateDownloadState by updateViewModel.downloadState.collectAsState()
            val autoPrompt by updateViewModel.autoPrompt.collectAsState()
            val updateDialog = rememberDialogState()
            // 只在已登录后检查：未登录时界面是登录页，任何弹窗都会抢焦点
            LaunchedEffect(authState) {
                if (authState is AuthState.LoggedIn) {
                    updateViewModel.checkInBackground()
                }
            }
            // 只有自动检查发现新版本时才弹窗
            LaunchedEffect(autoPrompt) {
                if (autoPrompt) updateDialog.show()
            }
            UpdateDialog(
                state = updateDialog,
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
    }
}

/**
 * 登录后等待首页首屏数据的上限。
 *
 * 超过这个时间即使创作者数据还没到也放行：弱网或接口异常时，
 * 把用户永远挡在过渡页比看到一块空模块更糟。
 */
private const val HOME_FIRST_LOAD_TIMEOUT_MS = 8_000L
