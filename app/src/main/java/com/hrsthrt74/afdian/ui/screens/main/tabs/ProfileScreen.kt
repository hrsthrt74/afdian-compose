package com.hrsthrt74.afdian.ui.screens.main.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.R
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.components.account.AccountSwitchPopup
import com.hrsthrt74.afdian.ui.components.account.LocalAccountSwitcher
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.QuickEntry
import com.hrsthrt74.afdian.ui.components.basic.SettingItemIcon
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.QuickEntryGroup
import com.hrsthrt74.afdian.ui.navigation.QuickEntryPreset
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.navigation.presets
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.navigation.resolveIcon
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 一行快捷入口放几个；与首页「快捷方式」模块的槽位数一致。 */
private const val QUICK_ENTRY_PER_ROW = 4

/**
 * 「我的」tab 的内容页。
 * 包括「我发电的」「浏览历史」，以及面向创作者的「管理」等功能。
 */
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onOpenCreator: (AfadianUser) -> Unit,
    onNavigate: (Route) -> Unit,
) {
    ProfileMainScreen(
        viewModel = viewModel,
        onNavigate = onNavigate,
        onOpenCreator = onOpenCreator,
    )
}

@Composable
private fun ProfileMainScreen(
    viewModel: MainViewModel,
    onNavigate: (Route) -> Unit,
    onOpenCreator: (AfadianUser) -> Unit,
) {
    // 账号切换菜单的数据由根层注入；未登录 / 未注入时为 null，长按不响应
    val accountSwitcher = LocalAccountSwitcher.current
    // WindowListPopup 要求 Boolean show，这里用 MutableState 方便内外共同读写
    val showAccountPopup = remember { mutableStateOf(false) }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val listState = rememberLazyListState()
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    val profile by viewModel.profile.collectAsState()

    Scaffold(
        topBar = {
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                // 捕获头像边界，作为「主页展开」过渡动画的起点
                val avatarOrigin = rememberCreatorOrigin()
                TopAppBar(
                    title = profile?.user?.name ?: "爱发电用户",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        // 菜单容器 WindowListPopup 以「调用处的父布局」为锚点，
                        // 必须和头像同处一个 Box，菜单才会贴着头像下沿弹出
                        Box {
                            Avatar(
                                model = profile?.user?.avatar,
                                size = 32.dp,
                                contentDescription = "头像",
                                modifier = Modifier.then(avatarOrigin.trackModifier()).padding(8.dp),
                                onClick = avatarOrigin.onClick {
                                    profile?.user?.takeIf { it.user_id.isNotBlank() }?.let(onOpenCreator)
                                },
                                // 长按切换账号：与单击（打开创作者主页）互不干扰
                                onLongClick = {
                                    if (accountSwitcher != null) {
                                        // 打开前刷新一次：昵称/头像与最近使用顺序保持最新
                                        accountSwitcher.onRefresh()
                                        showAccountPopup.value = true
                                    }
                                },
                            )
                            if (accountSwitcher != null) {
                                AccountSwitchPopup(
                                    visible = showAccountPopup,
                                    actions = accountSwitcher,
                                    onManage = { onNavigate(Route.Settings) },
                                )
                            }
                        }
                    }
                )
            }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = (paddingValues.calculateTopPadding() + 8.dp),
                    bottom = contentBottomPadding()
                )
            ) {
                // 快捷入口：直接铺预设目录，「我的」页与首页「快捷方式」模块共用同一份，
                // 以后新增入口这里自动多出一格，不用再回来补页面
                item {
                    QuickEntryRows(
                        presets = QuickEntryGroup.COMMON.presets(),
                        onNavigate = onNavigate,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                item {
                    SmallTitle(
                        text = QuickEntryGroup.CREATOR.title,
                        modifier = Modifier.padding(bottom = 4.dp, top = 24.dp),
                    )
                }
                item {
                    QuickEntryRows(
                        presets = QuickEntryGroup.CREATOR.presets(),
                        onNavigate = onNavigate,
                    )
                }

                item { Spacer(Modifier.height(36.dp)) }

                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        Column{
                            ArrowPreference(
                                title = "设置",
                                startAction = { SettingItemIcon(MiuixIcons.Settings) },
                                onClick = { onNavigate(Route.Settings) }
                            )
                            ArrowPreference(
                                title = "屏蔽与拒绝名单",
                                startAction = { SettingItemIcon(MiuixIcons.Blocklist) },
                                onClick = { onNavigate(Route.Blocklist) }
                            )
                            // 开发者页：查看 user_id、配置 Webhook URL、生成 API Token
                            ArrowPreference(
                                title = "开发者",
                                startAction = { SettingItemIcon(ImageVector.vectorResource(R.drawable.ic_debug)) },
                                onClick = { onNavigate(Route.Developer) }
                            )
                            // 文档页：官网文档站、创作者指南、开发者文档的统一收录
                            ArrowPreference(
                                title = "文档",
                                startAction = { SettingItemIcon(MiuixIcons.File) },
                                onClick = { onNavigate(Route.Docs) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 快捷入口区：按预设目录的顺序铺成每行 [QUICK_ENTRY_PER_ROW] 个圆形入口。
 *
 * 末行凑不满时补等宽空位，否则那一行的入口会挤在左边、和上面几行对不齐。
 */
@Composable
private fun QuickEntryRows(
    presets: List<QuickEntryPreset>,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        presets.chunked(QUICK_ENTRY_PER_ROW).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rowPresets.forEach { preset ->
                    QuickEntry(
                        title = preset.title,
                        modifier = Modifier.weight(1f),
                        icon = preset.resolveIcon(),
                        onClick = { onNavigate(preset.route) },
                    )
                }
                repeat(QUICK_ENTRY_PER_ROW - rowPresets.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
