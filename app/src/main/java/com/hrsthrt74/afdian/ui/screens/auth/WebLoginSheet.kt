package com.hrsthrt74.afdian.ui.screens.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.network.afdianWebUrl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * 网页登录的用途。
 *
 * 网页登录可以在两个地方发起，抓到凭据后的收尾不同：
 * - [Login]：未登录时的登录页 → 提交后直接进入主页；
 * - [AddAccount]：「添加账号」弹层 → 提交后保存为账号记录并切换过去。
 */
internal enum class WebLoginPurpose {
    Login,
    AddAccount,
}

/**
 * 「网页登录」弹层：在 App 内嵌一个 WebView 打开爱发电网页版登录页，
 * 用户在网页上完成登录后，从 WebView 的 Cookie 里取出 `auth_token` 交给上层。
 *
 * ## 为什么必须用 WebView 而不是 Custom Tabs
 *
 * 项目已有的 `AppLinkNavigator.openExternal()` 会用 Custom Tabs 把网页丢给系统浏览器，
 * 但浏览器跑在**另一个进程**里，它的 Cookie 与本 App 的 `CookieManager` 完全隔离，
 * 登录成功后拿不到 `auth_token`。而 App 走接口登录时用的正是这个名字
 * （`AuthService` 解析响应里的 `data.auth_token`），所以只有把网页装在本进程里
 * 才能把凭据接回现有的登录流程。
 *
 * ## 凭据从哪来
 *
 * 网页端 `app.js` 的 `set_auth_token()` 在登录成功后用 js-cookie 写入
 * `auth_token`（有效期 360 天）。本 App 不用 OkHttp 的 `CookieJar`，
 * 而是在每个请求上手动塞 `Cookie: auth_token=<值>` 头，所以只要拿到这个**字符串值**，
 * 域名作用域完全不影响复用。
 *
 * ## 域名跟随「优先线路」
 *
 * 加载地址由 [afdianWebUrl] 拼出，与网络层读的是同一个 `AfdianRouteState.preferred`，
 * 保证「接口打哪条线路，登录页就开哪条线路」。
 *
 * @param show 是否显示
 * @param onDismiss 用户关闭弹层（未登录成功也会走这里）
 * @param onToken 已抓到凭据时回调，参数是 `auth_token` 的值
 */
@Composable
fun WebLoginSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onToken: (String) -> Unit,
) {
    // 加载中的进度条状态：WebView 首次加载完成前盖一层进度条，避免白屏
    var loading by remember { mutableStateOf(true) }
    // 已经拿到凭据后置位：防止多个信号（页面跳转 + 历史更新 + 手动按钮）
    // 连续命中时重复回调，导致上层提交两次登录
    var tokenDelivered by remember { mutableStateOf(false) }

    // 登录页地址。
    // 每次弹层从「不显示」变成「显示」时重算一次：用户可能在设置页改了优先线路，
    // 线路是进程级状态，这里必须读当前值而不是首次组合时的旧值。
    var loginUrl by remember { mutableStateOf("") }
    LaunchedEffect(show) {
        if (show) {
            loginUrl = afdianWebUrl("/login")
            loading = true
            tokenDelivered = false
            AppLog.d("WebLogin", "打开网页登录弹层，地址=$loginUrl")
        }
    }

    // 用 rememberUpdatedState 持有最新的回调：WebViewClient 是在 factory 里创建的，
    // 拿不到最新的 lambda 引用，直接捕获会用到首帧的那份旧闭包
    val currentOnToken by rememberUpdatedState(onToken)

    /**
     * 尝试从 WebView 里取出登录凭据。
     *
     * 两条路径互为兜底：
     * 1. Cookie（`auth_token`）：正常路径，登录成功后 js-cookie 写入；
     * 2. URL 的 `atn` 参数：网页端跨站跳转时用它携带凭据，
     *    落地页再写回 cookie —— 跳转与写 cookie 之间存在时间差，
     *    只查 cookie 会漏掉这个瞬间。
     *
     * 用 `CookieManager.getCookie()` 而不是 `document.cookie`（需要 evaluateJavascript）：
     * 前者不依赖页面 JS 执行完毕、不受页面 CSP 与同源策略限制，对 HttpOnly 也有效。
     *
     * 三个域名都要查：用户在 `ifdian.net` 登录，cookie 就落在 `ifdian.net` 上；
     * 但网页端登录成功后可能跳到 `afdian.com`（跨站同步机制），
     * 而 App 的接口可能打的是另一条线路 —— 凭据本身是通用的，哪儿有就从哪儿取。
     *
     * @param currentUrl 当前页面地址，用于查 `atn` 参数
     */
    fun readToken(currentUrl: String?): String? {
        parseAuthTokenFromUrl(currentUrl)?.let { return it }
        val cookieManager = CookieManager.getInstance()
        for (host in WEB_LOGIN_COOKIE_HOSTS) {
            val cookieHeader = cookieManager.getCookie("https://$host")
            val token = parseAuthToken(cookieHeader)
            if (!token.isNullOrBlank()) return token
        }
        return null
    }

    WindowBottomSheet(
        show = show,
        insideMargin = DpSize(0.dp, 0.dp),
        title = "网页登录",
        // 左侧：关闭按钮。用户没登录成功就点这个走 onDismiss，不视为错误
        startAction = {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.padding(start = 24.dp)
            ) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭")
            }
        },
        // 右侧：兜底按钮。自动判定偶尔会漏（见下方 onPageFinished 的注释），
        // 给用户一个手动确认「我已在网页上登录完成」的出口
        endAction = {
            IconButton(
                onClick = {
                    val token = readToken(null)
                    if (token.isNullOrBlank()) {
                        AppLog.w("WebLogin", "手动确认时未读到登录凭据")
                        return@IconButton
                    }
                    if (!tokenDelivered) {
                        tokenDelivered = true
                        AppLog.d("WebLogin", "手动确认拿到登录凭据，长度=${token.length}")
                        currentOnToken(token)
                    }
                },
                modifier = Modifier.padding(end = 24.dp)
            ) {
                Icon(imageVector = MiuixIcons.Ok, contentDescription = "已完成登录")
            }
            Spacer(Modifier.padding(end = 24.dp))
        },
        onDismissRequest = onDismiss,
        // ★ WebView 场景必须关掉嵌套滚动：
        // WebView 自己消费触摸事件，不通过 Compose 的 NestedScrollDispatcher 上报，
        // 开着这个开关时 Sheet 的下拉关闭手势行为不可预期。
        // 关闭后 WebView 内部滚动照常，用户靠顶部拖拽条 / 关闭按钮 / 返回键退出。
        enableNestedScroll = false,
    ) {
        // content 的接收者是普通 @Composable () -> Unit（不是 ColumnScope），
        // 所以这里自己套一层 Column 才能用 weight。
        Column(
            modifier = Modifier.fillMaxWidth()
                // Sheet 没有高度参数：内部是 wrapContentHeight + heightIn(max = 窗口高 - 顶部 inset)。
                // 登录页要能滚动，这里给一个明确的高度区间而不是让它自适应。
//                .heightIn(min = 320.dp, max = 640.dp),
        ) {
            // 加载进度条：不确定态（progress = null 表示循环动画）
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // WebView 本体
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (loginUrl.isNotEmpty()) {
                    // key 绑定 url：地址一变就重建 WebView。
                    // 重建的意义有两层 ——
                    // 1. 换线路后必须重新加载新域名的登录页（WebView 不会自己换地址）；
                    // 2. 每次重建都会重新走一遍「清 cookie + 加载」，
                    //    保证不会复用上一个会话残留的登录态。
                    // 旧实例在离开组合时由 onRelease 销毁，不会泄漏。
                    key(loginUrl) {
                        WebLoginWebView(
                            url = loginUrl,
                            onPageLoaded = { loading = false },
                            onTokenFound = { token ->
                                loading = false
                                if (!tokenDelivered) {
                                    tokenDelivered = true
                                    AppLog.d("WebLogin", "网页登录成功，已取得登录凭据，长度=${token.length}")
                                    currentOnToken(token)
                                }
                            },
                            onTokenCheck = { currentUrl ->
                                // 页面跳转后主动查一次凭据（cookie + URL 的 atn 参数）
                                readToken(currentUrl)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 查找登录凭据时要遍历的域名。
 *
 * 覆盖爱发电的三个可用域名（与设置页「优先线路」的三档一致）：
 * 用户在哪个域名上完成登录，cookie 就落在哪个域名上。
 */
private val WEB_LOGIN_COOKIE_HOSTS = listOf("ifdian.net", "afdian.com", "www.afdian.com")

/**
 * 承载爱发电登录页的 WebView。
 *
 * 单独抽出来的原因：`AndroidView` 的 factory 只在首次组合时执行，
 * 把 WebView 的创建与配置收在这里，`WebLoginSheet` 只管状态与回调。
 *
 * @param url 要加载的登录页地址
 * @param onPageLoaded 页面加载完成（首次加载或跳转后）
 * @param onTokenFound 页面跳转后检查到了登录凭据
 * @param onTokenCheck 页面跳转时触发一次凭据检查，入参是当前页面地址，返回查到的值（可能为 null）
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebLoginWebView(
    url: String,
    onPageLoaded: () -> Unit,
    onTokenFound: (String) -> Unit,
    onTokenCheck: (String?) -> String?,
) {
    // 回调的最新引用：WebViewClient 在 factory 里创建，只能拿到这一份
    val currentOnPageLoaded by rememberUpdatedState(onPageLoaded)
    val currentOnTokenFound by rememberUpdatedState(onTokenFound)
    val currentOnTokenCheck by rememberUpdatedState(onTokenCheck)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // 登录页是 Vue 单页应用，靠 JS 渲染与路由，必须开 JS 与 DOM 存储
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (newProgress >= 100) currentOnPageLoaded()
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        super.onPageFinished(view, finishedUrl)
                        currentOnPageLoaded()
                        // 登录成功后网页端会跳走（跳到 /feed 等），
                        // 每次页面加载完都查一次凭据：这是最可靠的判定时机
                        checkToken(finishedUrl)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, visitedUrl: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, visitedUrl, isReload)
                        // 单页应用内部路由切换（不触发 onPageFinished）也要查一次
                        checkToken(visitedUrl)
                    }

                    /**
                     * 查一次凭据，命中就通知上层。
                     *
                     * 单独抽成方法是因为两个回调都要用，且「查到就上报、没查到就什么都不做」
                     * 这套逻辑要保证只有一处。当前地址一并传下去：
                     * 跨站跳转时凭据可能先出现在 URL 的 `atn` 参数里，cookie 稍后才写入。
                     */
                    private fun checkToken(currentUrl: String?) {
                        val token = currentOnTokenCheck(currentUrl) ?: return
                        currentOnTokenFound(token)
                    }
                }

                // ★ 先清 cookie，再加载页面 —— 顺序不能反。
                // 上一次网页登录留下的 auth_token 还在的话，网页端会直接判为「已登录」
                // 并跳过登录表单，用户没输任何密码就被登录成上次的账号，
                // 与「网页登录」的语义完全相反。
                // 清 cookie 必须在 loadUrl 之前发起：WebView 读 cookie 是在导航时，
                // 只要清理请求已经发出，本次的导航就不会带上旧 cookie。
                clearWebLoginCookies()
                loadUrl(url)
            }
        },
        onRelease = { webView ->
            // 组合离开时销毁 WebView：它持有独立的渲染进程与缓存，不销毁会一直泄漏
            webView.destroy()
        },
    )
}

/**
 * 清掉 WebView 的全部 cookie。
 *
 * 只影响 WebView 自己的 cookie jar（与系统浏览器、App 的 DataStore 都没关系）。
 *
 * 存在的理由：网页端看到 `auth_token` cookie 就会认为「已登录」，
 * 直接跳过登录表单。上一次网页登录留下的 cookie 会让这次的登录页直接被跳过，
 * 用户没输任何密码就被登录成上次的账号 —— 与「网页登录」这个功能的语义完全相反。
 */
private fun clearWebLoginCookies() {
    val cookieManager = CookieManager.getInstance()
    // removeAllCookies 是异步的（回调在 CookieManager 的线程上触发），
    // 这里不等待：清 cookie 与页面加载的先后由 WebView 自己保证，
    // 真正把关的是「加载前先清」这个顺序。
    cookieManager.removeAllCookies { count ->
        AppLog.d("WebLogin", "已清理 WebView cookie，共 $count 条")
    }
    // flush 把内存里的改动立刻落盘，防止进程被杀后 cookie 又回来
    cookieManager.flush()
}
