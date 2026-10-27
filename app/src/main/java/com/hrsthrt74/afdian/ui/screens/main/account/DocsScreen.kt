package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.ui.components.basic.CategoryIconTile
import com.hrsthrt74.afdian.ui.navigation.AFDIAN_GUIDE_HOST
import com.hrsthrt74.afdian.ui.navigation.AFDIAN_GUIDE_URL
import com.hrsthrt74.afdian.ui.navigation.CREATOR_GUIDE_POST_ID
import com.hrsthrt74.afdian.ui.navigation.DEV_DOC_OVERVIEW_POST_ID
import com.hrsthrt74.afdian.ui.navigation.DEV_DOC_WEBHOOK_API_POST_ID
import com.hrsthrt74.afdian.ui.navigation.DocTileBlue
import com.hrsthrt74.afdian.ui.navigation.DocTileGreen
import com.hrsthrt74.afdian.ui.navigation.DocTileOrange
import com.hrsthrt74.afdian.ui.navigation.DocTilePurple
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.navigation.openExternalLink
import com.hrsthrt74.afdian.ui.navigation.rememberCustomTabsBranding
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 文档聚合页：全 App「看官方文档」的入口都收在这里。
 *
 * - 官网：爱发电文档站（外链打开，域名写死 guide.afdian.com，不跟「优先线路」走）；
 * - 创作者：创作者指南（官网动态长文，按 post id 进动态详情页）；
 * - 开发者：Webhook 与 API、开发者功能汇总两篇官网动态（与开发者页「文档」卡同源收录，
 *   那边是就地入口不撤，这边做全量聚合）。
 *
 * 版式沿用 [SettingsPageScaffold]（大标题顶栏 + 模糊栏 + 双击回顶 + 滚动条）；
 * 分组「SmallTitle + 卡」延续设置分类页的节奏，每篇文档一张 [DocCard]：
 * 左侧彩色图标块用共用的 [CategoryIconTile]，右侧标题 + 摘要说明来源与内容。
 */
@Composable
fun DocsScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    // 外链打开偏好：是否优先唤起爱发电官方客户端（设置 → 通用「链接打开方式」里配置）
    val preferOfficialClient by viewModel.preferOfficialClient.collectAsState()
    // 品牌化 Custom Tabs 参数：进组合时构建一次即可
    val customTabsBranding = rememberCustomTabsBranding()
    val context = LocalContext.current

    SettingsPageScaffold(
        title = "文档",
        onBack = onBack,
        backStack = backStack,
    ) {
        // ---- 官网：爱发电官方文档站 ----
        item(key = "guide") {
            Column {
                SmallTitle(text = "官网", modifier = Modifier.padding(start = 4.dp))
                DocCard(
                    title = "爱发电文档",
                    summary = AFDIAN_GUIDE_HOST,
                    icon = MiuixIcons.Help,
                    iconBackground = DocTileBlue,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    onClick = {
                        // 地址在 OfficialDocs.kt 写死：另外两条线路域名下没有 guide 子域，跟线走会 404
                        AppLog.d("Docs", "打开爱发电文档站: $AFDIAN_GUIDE_URL")
                        openExternalLink(context, AFDIAN_GUIDE_URL, preferOfficialClient, customTabsBranding)
                    },
                )
            }
        }

        // ---- 创作者：创作者指南（原「我的」页快捷入口的收录） ----
        item(key = "creator-guide") {
            Column {
                SmallTitle(text = "创作者", modifier = Modifier.padding(start = 4.dp))
                DocCard(
                    title = "创作者指南",
                    summary = "发电、支持与创作的官方说明",
                    icon = MiuixIcons.Edit,
                    iconBackground = DocTileOrange,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    onClick = { backStack.openDoc(Route.PostDetailById(CREATOR_GUIDE_POST_ID)) },
                )
            }
        }

        // ---- 开发者：两篇官方动态，与开发者页「文档」卡同源 ----
        item(key = "developer-docs") {
            Column {
                SmallTitle(text = "开发者", modifier = Modifier.padding(start = 4.dp))
                DocCard(
                    title = "Webhook 与 API 文档",
                    summary = "Webhook 推送与 API 签名调用",
                    icon = MiuixIcons.Link,
                    iconBackground = DocTileGreen,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    onClick = { backStack.openDoc(Route.PostDetailById(DEV_DOC_WEBHOOK_API_POST_ID)) },
                )
                DocCard(
                    title = "开发者功能汇总",
                    summary = "API、Webhook、OAuth2 与网页嵌入",
                    icon = MiuixIcons.Tune,
                    iconBackground = DocTilePurple,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    onClick = { backStack.openDoc(Route.PostDetailById(DEV_DOC_OVERVIEW_POST_ID)) },
                )
            }
        }
    }
}

/**
 * 单张文档卡：彩色图标块 + 标题 / 摘要 + 箭头，一篇文档一张卡。
 *
 * 走 [ArrowPreference]（内部就是 BasicComponent）：箭头、字色、按压态都是 miuix 标准，
 * 我们只负责把 [CategoryIconTile] 塞进 startAction 槽——图标块与设置总览分类共用
 * 同一个组件，Monet 适配、规格都只有一份实现。
 *
 * @param title 文档标题（短句，详情点进去看）
 * @param summary 一行摘要（来源域名 / 内容提示）
 * @param icon 图标块里的单色图标（默认字重，与设置总览分类一致）
 * @param iconBackground 图标块底色；Monet 开启时被主题主色覆盖
 * @param onClick 点击文档卡的动作
 */
@Composable
private fun DocCard(
    title: String,
    summary: String,
    icon: ImageVector,
    iconBackground: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(modifier = modifier) {
        ArrowPreference(
            title = title,
            summary = summary,
            startAction = { CategoryIconTile(icon = icon, iconBackground = iconBackground) },
            onClick = onClick,
        )
    }
}

/**
 * 按 post id 打开官网动态详情页。
 *
 * 先查重再入栈：miuix-nav 要求栈内 contentKey 互不相等，重复 push 会直接抛
 * "Duplicate contentKey" 异常闪退，顺带挡掉过渡动画期间的快速连点。
 * 与 MainScreen 的 pushUnique 同一语义，但那边是 private，页面内各自收口。
 */
private fun NavBackStack.openDoc(route: Route) {
    if (none { it == route }) add(route)
}
