package com.hrsthrt74.afdian.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsSession
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.AfadianContentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Custom Tabs 的品牌化参数，让浏览器地址栏看起来像 App 的一部分。
 *
 * @param toolbarColor 地址栏（工具栏）底色，取当前 Miuix 主题的 surface。
 * @param closeButtonIcon 关闭按钮位图，用 Miuix 的返回箭头替代浏览器默认的「×」；
 *        生成失败时为 null，此时沿用浏览器自带图标。
 */
data class CustomTabsBranding(
    val toolbarColor: Int,
    val closeButtonIcon: Bitmap?,
)

/** 关闭按钮图标按 24dp 出图，和浏览器工具栏里按钮的实际显示尺寸对齐。 */
private val CloseIconSize = 24.dp

/**
 * 按当前 Miuix 主题生成 Custom Tabs 的品牌化参数。
 *
 * 主题切换（深浅色、Monet 换色）后本函数会产出新的参数，
 * 调用方需把它作为 key 重建 [AppLinkNavigator]，否则浏览器里仍是旧配色。
 */
@Composable
fun rememberCustomTabsBranding(): CustomTabsBranding {
    val colors = MiuixTheme.colorScheme
    // 关闭按钮图标按 onSurface 着色：Miuix 图标矢量本身是黑色，
    // Custom Tabs 不会像 App 内的 Icon 那样自动反色，深色主题下不染色就看不见了
    val closeButtonIcon = rememberCloseButtonBitmap(colors.onSurface)
    return remember(colors.surface, closeButtonIcon) {
        val toolbarColor = colors.surface.toArgb()
        AppLog.d("AppLinkNavigator", "生成 Custom Tabs 品牌参数: 工具栏色=$toolbarColor 换关闭图标=${closeButtonIcon != null}")
        CustomTabsBranding(
            toolbarColor = toolbarColor,
            closeButtonIcon = closeButtonIcon,
        )
    }
}

/**
 * 把 Miuix 返回图标光栅化成 Bitmap。
 *
 * Custom Tabs 的关闭按钮只接受 Bitmap，没有 ImageVector 参数；
 * 这里用 [rememberVectorPainter] 把矢量重绘到一张 [ImageBitmap]，再取回平台位图。
 *
 * @param tint 图标前景色，一般传当前主题的 onSurface。
 * @return 生成成功返回位图；失败（Compose 绘制异常）返回 null，由调用方退回浏览器默认图标。
 */
@Composable
private fun rememberCloseButtonBitmap(tint: Color): Bitmap? {
    // 矢量图标内部是纯黑填充，交给 onSurface 着色，深色主题下才不会与工具栏撞成一片黑
    val painter = rememberVectorPainter(MiuixIcons.Back)
    val density = LocalDensity.current
    return remember(painter, tint, density) {
        runCatching {
            val sizePx = with(density) { CloseIconSize.roundToPx() }.coerceAtLeast(1)
            val image = ImageBitmap(sizePx, sizePx)
            // Compose 没有「拿 ImageBitmap 当 DrawScope」的便捷函数，这里手工搭一个绘制作用域
            CanvasDrawScope().draw(
                density = density,
                layoutDirection = LayoutDirection.Ltr,
                canvas = Canvas(image),
                size = Size(sizePx.toFloat(), sizePx.toFloat()),
            ) {
                // Painter.draw 是 DrawScope 的成员扩展，必须在 DrawScope 接收者里调用
                with(painter) { draw(size, colorFilter = ColorFilter.tint(tint)) }
            }
            image.asAndroidBitmap()
        }.onFailure { error ->
            AppLog.w("AppLinkNavigator", "生成 Custom Tabs 关闭按钮图标失败，沿用浏览器默认图标", error)
        }.getOrNull()
    }
}

/** 统一的链接跳转入口：内部路由优先，无法处理时使用 Chrome Custom Tabs。 */
class AppLinkNavigator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val contentService: AfadianContentService,
    private val preferOfficialClient: Boolean,
    /** 浏览器工具栏的品牌化参数（底色 + 关闭按钮图标），由 [rememberCustomTabsBranding] 生成。 */
    private val branding: CustomTabsBranding,
    private val onNavigate: (Route) -> Unit,
    private val onOpenMainTab: (Int) -> Unit = {},
) {
    fun open(url: String) {
        when (val destination = parseAppLink(url)) {
            is AppLinkDestination.Post -> {
                AppLog.d("AppLinkNavigator", "打开 App 内动态: ${destination.postId}")
                onNavigate(Route.PostDetailById(destination.postId))
            }
            is AppLinkDestination.Creator -> {
                scope.launch {
                    AppLog.d("AppLinkNavigator", "解析 App 内创作者链接: ${destination.slug}")
                    runCatching {
                        withContext(Dispatchers.IO) {
                            contentService.fetchProfileBySlug(destination.slug)
                        }
                    }.onSuccess { user ->
                        if (user.user_id.isBlank()) {
                            AppLog.w("AppLinkNavigator", "创作者链接缺少 user_id: ${destination.slug}")
                            openExternal(url)
                        } else {
                            onNavigate(Route.CreatorDetail(user))
                        }
                    }.onFailure { error ->
                        AppLog.e("AppLinkNavigator", "创作者链接解析失败: ${destination.slug}", error)
                        openExternal(url)
                    }
                }
            }
            AppLinkDestination.Explore -> onOpenMainTab(1)
            AppLinkDestination.Favorites -> onNavigate(Route.Favorites)
            AppLinkDestination.Shop -> onNavigate(Route.Shop)
            AppLinkDestination.Management -> onNavigate(Route.Management)
            AppLinkDestination.ReceivedSponsors -> onNavigate(Route.ReceivedSponsors)
            AppLinkDestination.MonthlySummary -> onNavigate(Route.MonthlySummary)
            AppLinkDestination.Sponsoring -> onNavigate(Route.Sponsoring)
            AppLinkDestination.Orders -> onNavigate(Route.Orders)
            AppLinkDestination.Affiliates -> onNavigate(Route.Affiliates)
            AppLinkDestination.Developer -> onNavigate(Route.Developer)
            AppLinkDestination.CustomerService -> onNavigate(
                Route.MessageConversation(
                    userId = "27f7cea2370d11e8ae8852540025c377",
                    userName = "Lain音酱",
                    avatarUrl = "https://pic1.afdiancdn.com/user/27f7cea2370d11e8ae8852540025c377/avatar/2d9659585fc4798068efbb652e56c08a.jpg",
                )
            )
            is AppLinkDestination.Product -> {
                AppLog.d("AppLinkNavigator", "打开 App 内商品详情: ${destination.productId}")
                onNavigate(Route.ProductDetail(destination.productId))
            }
            null -> openExternal(url)
        }
    }

    /**
     * 绕过站内解析、强制走外部打开（官方客户端优先 → Custom Tabs → 系统浏览器）。
     *
     * 供「明确要离开 App」的动作使用（如跳转官方支付页）：
     * 这类 URL（如 /item/{id}、/bundle/{id}）会被 AppLinkRegistry 命中站内规则，
     * 必须绕过 [open] 的统一解析，避免被误拦截回 App 内页面。
     */
    fun openExternal(url: String) {
        openExternalLink(
            context = context,
            url = url,
            preferOfficialClient = preferOfficialClient,
            branding = branding,
        )
    }
}

/**
 * 打开站外网页：官方客户端优先 → Custom Tabs → 系统浏览器。
 *
 * **为什么从 [AppLinkNavigator] 里拆出来**：`AppLinkNavigator` 带着站内跳转能力
 * （`onNavigate` / `onOpenMainTab`），只有 MainScreen 提供了它（`LocalAppLinkNavigator`）；
 * 而设置页在**未登录栈**里也能进（登录页顶栏的设置入口），那儿取不到这个组合局部。
 * 于是把「打开外部 URL」这一半独立成函数：站内链接继续走 [AppLinkNavigator.open]，
 * 纯站外链接（GitHub、爱发电文档）两处共用同一套回退链。
 *
 * @param context 上下文
 * @param url 要打开的地址，必须是 http/https
 * @param preferOfficialClient 是否优先尝试拉起爱发电官方客户端
 * @param branding Custom Tabs 的品牌化参数（底色 + 关闭按钮图标）
 */
fun openExternalLink(
    context: Context,
    url: String,
    preferOfficialClient: Boolean,
    branding: CustomTabsBranding,
) {
    val uri = runCatching { URI(url) }.getOrNull()
    if (uri == null || uri.scheme !in setOf("http", "https")) {
        AppLog.w("AppLinkNavigator", "拒绝打开无效链接: $url")
        return
    }
    if (preferOfficialClient && openOfficialClient(context, uri.toString())) return
    if (preferOfficialClient) {
        AppLog.w("AppLinkNavigator", "官方客户端未能处理链接，回退 Custom Tabs: $url")
    }
    runCatching {
        val customTabsPackage = CustomTabsClient.getPackageName(context, null)
            ?: error("设备没有可用的 Custom Tabs provider")
        val connection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                name: ComponentName,
                client: CustomTabsClient,
            ) {
                runCatching {
                    val session: CustomTabsSession? = client.newSession(null)
                    // 工具栏底色对齐 Miuix 的 surface：同一色值同时注册给浅色与深色方案，
                    // 浏览器按系统深浅色挑参数时拿到的都是 App 当前配色，不会退回浏览器默认色。
                    // 注意 Custom Tabs 协议不支持改状态栏色，这里只管地址栏底色。
                    val toolbarScheme = CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(branding.toolbarColor)
                        .build()
                    val builder = CustomTabsIntent.Builder(session)
                        .setDefaultColorSchemeParams(toolbarScheme)
                        .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, toolbarScheme)
                        // 地址栏显示网页标题而不是长串 URL
                        .setShowTitle(true)
                    // 关闭按钮换成 Miuix 的返回箭头，和 App 内的返回按钮保持一致
                    branding.closeButtonIcon?.let(builder::setCloseButtonIcon)
                    val customTabsIntent = builder.build().apply {
                        // 显式锁定 provider，避免被普通 ACTION_VIEW 解析流程接管。
                        intent.setPackage(customTabsPackage)
                    }

                    customTabsIntent.launchUrl(context, android.net.Uri.parse(uri.toString()))
                    AppLog.d(
                        "AppLinkNavigator",
                        "通过已绑定的 Custom Tabs 打开: provider=$customTabsPackage url=$url toolbar=${branding.toolbarColor}",
                    )
                }.onFailure { error ->
                    AppLog.e("AppLinkNavigator", "Custom Tabs session 启动失败: $url", error)
                    openSystemBrowser(context, uri)
                }.also {
                    runCatching { context.unbindService(this) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                AppLog.w("AppLinkNavigator", "Custom Tabs 服务断开: $name")
            }
        }
        if (!CustomTabsClient.bindCustomTabsService(context, customTabsPackage, connection)) {
            error("无法绑定 Custom Tabs provider=$customTabsPackage")
        }
    }.onFailure { error ->
        AppLog.e("AppLinkNavigator", "Custom Tabs 服务不可用，回退系统浏览器: $url", error)
        openSystemBrowser(context, uri)
    }
}

/**
 * 用系统浏览器兜底打开网页。
 *
 * Custom Tabs 服务不可用（设备没装支持它的浏览器）时唯一的出路，
 * 所以失败只记日志、不再往上抛 —— 否则会连带崩掉调用方（点击事件所在页面）。
 */
private fun openSystemBrowser(context: Context, uri: URI) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri.toString()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { fallbackError ->
        AppLog.e("AppLinkNavigator", "系统浏览器回退也失败: $uri", fallbackError)
    }
}

/**
 * 使用官方客户端约定的 afd scheme；返回 false 表示未安装或无法处理。
 *
 * @param context 上下文
 * @param url 要交给官方客户端打开的地址
 * @return 拉起成功返回 true；未安装或失败返回 false（调用方应回退 Custom Tabs）
 */
private fun openOfficialClient(context: Context, url: String): Boolean {
    val targetKey = "1:$url"
    val params = JSONObject()
        .put("url", url)
        .put("type", "1")
        .put("target_key", targetKey)
        .put("dedupe", "1")
        .toString()
    val officialUri = android.net.Uri.Builder()
        .scheme("afd")
        .authority("app")
        .path("openapp")
        .appendQueryParameter("url", url)
        .appendQueryParameter("type", "1")
        .appendQueryParameter("target_key", targetKey)
        .appendQueryParameter("dedupe", "1")
        .appendQueryParameter("params", params)
        .build()
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, officialUri)
                .setPackage(OFFICIAL_CLIENT_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        AppLog.d("AppLinkNavigator", "通过官方客户端打开: $url")
        true
    }.getOrElse { error ->
        AppLog.w("AppLinkNavigator", "官方客户端拉起失败: $url", error)
        false
    }
}

/** 爱发电官方客户端的包名：拉起失败时回退 Custom Tabs。 */
private const val OFFICIAL_CLIENT_PACKAGE = "net.afdian.afdian"
