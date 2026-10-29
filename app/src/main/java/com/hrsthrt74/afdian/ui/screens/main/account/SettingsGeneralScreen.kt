package com.hrsthrt74.afdian.ui.screens.main.account

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.R
import com.hrsthrt74.afdian.data.network.AfdianRoute
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.SettingItemIcon
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 「通用」分组里启动页的下拉选项
private val startupPageNames = listOf(
    DropdownItem(
        icon = { Icon(ImageVector.vectorResource(R.drawable.home_outlined), null, modifier = Modifier.padding(end = 12.dp)) },
        text = "首页"
    ),
    DropdownItem(
        icon = { Icon(MiuixIcons.Search, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "发现"
    ),
    DropdownItem(
        icon = { Icon(MiuixIcons.Community, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "消息"
    ),
    DropdownItem(
        icon = { Icon(MiuixIcons.Contacts, null, modifier = Modifier.padding(end = 12.dp)) },
        text = "我的"
    ),
)

// 链接打开方式选项，索引与底层 preferOfficialClient 布尔值的约定一致：
// 0 = Custom Tabs（默认）、1 = 优先官方客户端
private val linkOpenModeNames = listOf("系统浏览器", "官方客户端")

// 优先线路选项：下标与 AfdianRoute 的声明顺序一致，文案直接用域名本身，不给它另起名字
private val networkRouteNames = AfdianRoute.entries.map { it.host }

/**
 * 设置 → 通用。
 *
 * 原「更多」分类已并入本页，所以这里同时承担通用偏好与杂项入口：
 * - **通用**：打开 App 后默认进入、记录历史；
 * - **网络**：优先线路、链接打开方式；
 * - **管理**：存储空间管理、日志管理、接口调试；
 * - **重置**：两个布局重置入口。
 *
 * 所有写操作仍交给 [SettingsViewModel]，DataStore 键不变。
 */
@Composable
fun SettingsGeneralScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val startupPage by viewModel.startupPage.collectAsState()
    val historyEnabled by viewModel.historyEnabled.collectAsState()
    val preferOfficialClient by viewModel.preferOfficialClient.collectAsState()
    val networkRoute by viewModel.networkRoute.collectAsState()
    val haptic = LocalHapticFeedback.current

    // 两个布局重置确认弹窗：都会丢掉用户当前的自定义顺序，必须二次确认
    val resetManagementDialogState = rememberDialogState()
    val resetHomeCreatorDialogState = rememberDialogState()

    SettingsPageScaffold(
        title = "通用",
        onBack = onBack,
        backStack = backStack,
    ) {
        item { SmallTitle(text = "通用", modifier = Modifier.padding(start = 4.dp)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                // 启动页：打开 App 后默认落到哪个 Tab
                OverlaySpinnerPreference(
                    title = "打开 App 后默认进入",
                    items = startupPageNames,
                    startAction = { SettingItemIcon(MiuixIcons.Forward) },
                    selectedIndex = startupPage,
                    onSelectedIndexChange = viewModel::setStartupPage
                )
                // 记录历史：最近访问、最近搜索等，只保存在本地
                SwitchPreference(
                    checked = historyEnabled,
                    onCheckedChange = viewModel::setHistoryEnabled,
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_history)) },
                    title = "记录历史",
                    summary = "最近访问、最近搜索等" +
                            "（仅保存在本地）",
                )
            }
        }

        item { SmallTitle(text = "网络", modifier = Modifier.padding(start = 4.dp)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                // 优先网络线路：所选项不可用时按固定有序链回退
                OverlayDropdownPreference(
                    title = "优先线路",
                    summary = "所选项不可用时，会回退到其他线路",
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_network)) },
                    items = networkRouteNames,
                    selectedIndex = networkRoute.ordinal.coerceIn(0, networkRouteNames.lastIndex),
                    onSelectedIndexChange = { index ->
                        AfdianRoute.entries.getOrNull(index)?.let(viewModel::setNetworkRoute)
                    },
                )
                // 链接打开方式：0=Custom Tabs、1=优先官方客户端（失败仍回退 Custom Tabs）
                OverlayDropdownPreference(
                    title = "链接打开方式",
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_link)) },
                    items = linkOpenModeNames,
                    selectedIndex = if (preferOfficialClient) 1 else 0,
                    onSelectedIndexChange = { index ->
                        viewModel.setPreferOfficialClient(index == 1)
                    },
                )
            }
        }

        item { SmallTitle(text = "管理", modifier = Modifier.padding(start = 4.dp)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                // 本地存储与排查工具：三者都属于「本机数据」这一类，放同一张卡
                ArrowPreference(
                    title = "存储空间管理",
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_storage)) },
                    onClick = { if (backStack.none { it == Route.Storage }) backStack.add(Route.Storage) },
                )
                // 日志页：运行日志与崩溃报告的记录开关、占用、导出与清空
                ArrowPreference(
                    title = "日志管理",
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_log)) },
                    onClick = { if (backStack.none { it == Route.Log }) backStack.add(Route.Log) },
                )
                // 接口调试页：主动探针逐个运行核心接口，用于肉眼核对各接口当前的返回值
                ArrowPreference(
                    title = "接口调试",
                    startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_debug)) },
                    onClick = { if (backStack.none { it == Route.ApiProbe }) backStack.add(Route.ApiProbe) },
                )
            }
        }

        item { SmallTitle(text = "重置", modifier = Modifier.padding(start = 4.dp)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)){
                BasicComponent(
                    title = "重置管理页卡片布局",
                    titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        resetManagementDialogState.show() },
                )
                BasicComponent(
                    title = "重置首页创作者数据卡片布局",
                    titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        resetHomeCreatorDialogState.show()
                    },
                )
            }
        }
    }

    // 重置管理页卡片布局确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = resetManagementDialogState,
        title = "重置管理页卡片布局",
        confirmText = "重置",
        destructive = true,
        onConfirm = viewModel::resetManagementLayout,
    ) {
        Text(
            "确定要恢复默认布局吗？当前自定义的卡片顺序将丢失。",
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }

    // 重置首页创作者数据卡片布局确认弹窗（破坏性：确认按钮变红）
    AppDialog(
        state = resetHomeCreatorDialogState,
        title = "重置首页创作者数据卡片布局",
        confirmText = "重置",
        destructive = true,
        onConfirm = viewModel::resetHomeCreatorDataLayout,
    ) {
        Text(
            "确定要恢复默认布局吗？当前自定义的卡片顺序将丢失。",
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}
