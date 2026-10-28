package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.ui.components.account.LocalAccountSwitcher
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.CategoryIconTile
import com.hrsthrt74.afdian.ui.components.basic.TooltipInfoButton
import com.hrsthrt74.afdian.ui.components.basic.TooltipInfoButtonStyle
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置总览页的分类入口。
 *
 * 分类页本身在各自文件里：通用 / 外观 / 关于。这里只描述「图标 + 标题 + 目标路由」，
 * 新增分类时只要往 [settingsCategories] 追加一项，并在两处导航栈注册对应路由即可。
 *
 * @param title 入口标题
 * @param icon 白色单色图标
 * @param iconBackground 图标底色（沿用参考截图里的 MIUI 彩色方块）；
 *        Monet 配色开启时会被主题主色统一覆盖
 * @param route 点击后要推入的路由
 */
private data class SettingsCategoryItem(
    val title: String,
    val icon: ImageVector,
    val iconBackground: Color,
    val route: Route,
)

/**
 * 设置总览的偏好分类入口（通用 / 外观），顺序即展示顺序，共用同一张卡。
 *
 * 配色取参考截图里的 MIUI 风格：蓝（通用）、紫（外观）。
 * 图标统一用单色描边，压在 32dp 圆角方块上，与「账号」分区的头像列左缘对齐。
 * 若用户开启了 Monet 配色，方块统一改用主题主色（见 [CategoryIconTile]），
 * 把配色决定权交回动态色板。
 */
private val settingsCategories = listOf(
    SettingsCategoryItem("通用", MiuixIcons.Tune, Color(0xFF3D8BFD), Route.SettingsGeneral),
    SettingsCategoryItem("外观", MiuixIcons.Theme, Color(0xFF8B7BF7), Route.SettingsAppearance),
)

/**
 * 「关于」分类：与偏好分类（通用 / 外观）性质不同，单独一张卡在视觉上分开。
 */
private val aboutCategory = SettingsCategoryItem("关于", MiuixIcons.Info, Color(0xFF8A8F98), Route.SettingsAbout)

/**
 * 设置总览页。
 *
 * 页面只保留两块：
 * 1. **账号**：整块沿用改造前的实现（账号列表、添加账号、首页账号切换器、删除/退出），
 *    逻辑与间距都不动，未登录栈里 `LocalAccountSwitcher` 为 null 时整块不渲染；
 * 2. **分类入口**：通用 / 外观 同一张卡，关于 单独一张卡，点击 push 到对应二级页。
 *
 * 原先铺在本页的偏好项已按分类拆到 [SettingsGeneralScreen]、[SettingsAppearanceScreen]、
 * [SettingsAboutScreen]；原「更多」页内容已并入「通用」，版本号与构建类型页脚在「关于」页底部。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    // 登出回调：由外层（MainScreen）传入，点击账号行尾的退出按钮并确认后触发
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val homeAccountSwitcherEnabled by viewModel.homeAccountSwitcherEnabled.collectAsState()
    val haptic = LocalHapticFeedback.current
    // 「退出当前账号」确认弹窗：登出会清掉凭证并回到登录页，误触即掉线，必须二次确认
    val logoutDialogState = rememberDialogState()

    SettingsPageScaffold(
        title = "设置",
        onBack = onBack,
        backStack = backStack,
        // 账号分区保持改造前的顶部间距，不加额外 8dp
        topContentPadding = 0.dp,
    ) {
        // ── 账号管理（整块原样保留）──
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
                    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
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

        // ── 分类入口 ──
        item {
            Column {
                // 偏好分类（通用 / 外观）：同一张卡；下边距 12dp 是它与「关于」卡之间的分隔
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                        .padding(bottom = 12.dp),
                ) {
                    settingsCategories.forEach { category ->
                        ArrowPreference(
                            title = category.title,
                            startAction = { CategoryIconTile(icon = category.icon, iconBackground = category.iconBackground) },   // 左侧图标
                            onClick = {
                                // 去重：转场期间连点不会重复入栈
                                //（miuix-nav 栈内 contentKey 重复会直接抛异常闪退）
                                if (backStack.none { it == category.route }) backStack.add(category.route)
                            },
                        )
                    }
                }
                // 「关于」单独一张卡：不加上边距，与上一张卡的间距全靠它的 12dp 下边距
                Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ArrowPreference(
                        title = aboutCategory.title,
                        startAction = { CategoryIconTile(icon = aboutCategory.icon, iconBackground = aboutCategory.iconBackground) },
                        onClick = {
                            if (backStack.none { it == aboutCategory.route }) backStack.add(aboutCategory.route)
                        },
                    )
                }
            }
        }
    }

    // 退出当前账号确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = logoutDialogState,
        title = "退出当前账号",
        summary = "退出后需要重新输入账号密码登录；该账号会从账号列表中移除，其他已保存的账号不受影响。",
        confirmText = "退出",
        destructive = true,
        onConfirm = onLogout,
    )
}
