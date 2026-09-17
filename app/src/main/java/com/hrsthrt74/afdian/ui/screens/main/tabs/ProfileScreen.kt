package com.hrsthrt74.afdian.ui.screens.main

// Miuix 提供的「滚动到边界触觉反馈」修饰符：列表快速滑动到顶/底边界时震动一下
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import java.time.YearMonth
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Th1
import top.yukonga.miuix.kmp.icon.extended.Th2
import top.yukonga.miuix.kmp.icon.extended.Th3
import top.yukonga.miuix.kmp.icon.extended.Th4
import top.yukonga.miuix.kmp.icon.extended.Th5
import top.yukonga.miuix.kmp.icon.extended.Th6
import top.yukonga.miuix.kmp.icon.extended.Th7
import top.yukonga.miuix.kmp.icon.extended.Th8
import top.yukonga.miuix.kmp.icon.extended.Th9
import top.yukonga.miuix.kmp.icon.extended.Th10
import top.yukonga.miuix.kmp.icon.extended.Th11
import top.yukonga.miuix.kmp.icon.extended.Th12
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 「我的」tab 的内容页。二级路由（收藏/我发电的/订单/管理/设置）不再在这里嵌套
 * NavDisplay，而是通过 [onNavigate] 提升到 MainScreen 外层栈全屏覆盖，
 * 避免 Pager 页内过渡层平移溢出泄漏到相邻 tab 并触发重复 contentKey 闪退。
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
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    val profile by viewModel.profile.collectAsState()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                // 捕获头像边界，作为「主页展开」过渡动画的起点
                val avatarOrigin = rememberCreatorOrigin()
                TopAppBar(
                    title = profile?.user?.name ?: "爱发电用户",
                    color = blurBarColor(backdrop),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        Avatar(
                            model = profile?.user?.avatar,
                            size = 32.dp,
                            contentDescription = "头像",
                            modifier = Modifier.padding(8.dp).then(avatarOrigin.trackModifier()),
                            onClick = avatarOrigin.onClick {
                                profile?.user?.takeIf { it.user_id.isNotBlank() }?.let(onOpenCreator)
                            },
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = contentBottomPadding()
                )
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                // 快捷入口行
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ProfileQuickEntry(
                            title = "我发电的",
                            icon = MiuixIcons.Demibold.Favorites,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.Sponsoring) },
                        )
                        ProfileQuickEntry(
                            title = "我的收藏",
                            icon = Icons.Filled.BookmarkBorder,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.Favorites) },
                        )
                        ProfileQuickEntry(
                            title = "我的订单",
                            icon = MiuixIcons.Medium.Notes,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.Orders) },
                        )
                        ProfileQuickEntry(
                            title = "浏览历史",
                            icon = Icons.Filled.History,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.History) },
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }

                item { SmallTitle(text = "创作者", modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ProfileQuickEntry(
                            title = "管理面板",
                            icon = MiuixIcons.Demibold.All,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.Management) },
                        )
                        ProfileQuickEntry(
                            title = "收入明细",
                            icon = MiuixIcons.Demibold.ListView,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.IncomeDetail) },
                        )
                        ProfileQuickEntry(
                            title = "收到发电",
                            icon = MiuixIcons.Demibold.Store,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.ReceivedSponsors) },
                        )
                        ProfileQuickEntry(
                            title = "感谢页",
                            // 图标跟随感谢页默认展示的年月（上个月），本月 9 月 → 上月 8 → Th8
                            icon = thankSponsorsEntryIcon(),
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.ThankSponsors) },
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ProfileQuickEntry(
                            title = "优惠码",
                            icon = MiuixIcons.Demibold.Rename,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.Affiliates) },
                        )
                        ProfileQuickEntry(
                            title = "创作者指南",
                            icon = MiuixIcons.Demibold.MindMap,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Route.PostDetailById("2da755247a8911e8ad6d52540025c377")) },
                        )
                        ProfileQuickEntry(
                            title = "",
                            modifier = Modifier.weight(1f),
                            onClick = {  },
                        )
                        ProfileQuickEntry(
                            title = "",
                            modifier = Modifier.weight(1f),
                            onClick = {  },
                        )
                    }
                }

                item { Spacer(Modifier.height(36.dp)) }

                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        ArrowPreference(
                            title = "设置",
                            summary = "偏好、安全和关于",
                            onClick = { onNavigate(Route.Settings) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「感谢页」快捷入口的图标：取 Miuix 的 ThN 数字图标。
 *
 * 因打开是「上月」的感谢页，故数字 -1
 *
 */
private fun thankSponsorsEntryIcon(): ImageVector =
    when (YearMonth.now().minusMonths(1).monthValue) {
        1 -> MiuixIcons.Th1
        2 -> MiuixIcons.Th2
        3 -> MiuixIcons.Th3
        4 -> MiuixIcons.Th4
        5 -> MiuixIcons.Th5
        6 -> MiuixIcons.Th6
        7 -> MiuixIcons.Th7
        8 -> MiuixIcons.Th8
        9 -> MiuixIcons.Th9
        10 -> MiuixIcons.Th10
        11 -> MiuixIcons.Th11
        // 剩下只可能是 12 月
        else -> MiuixIcons.Th12
    }

/**
 * 「我的」页的圆形快捷入口：上面是圆形底 + 图标（或等大占位），下面一行标题文字。
 * 底色 / 图标色都留了可传参数（默认 surfaceContainer 底 + primary 图标），
 * 方便以后加新入口时按需换色。图标可选，未传时显示同尺寸 Spacer 占位。
 */
@Composable
private fun ProfileQuickEntry(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    // 圆形底的背景色
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    // 图标的着色，默认主色
    iconTint: Color = MiuixTheme.colorScheme.primary,
) {
    // 触觉反馈：点击入口时轻震一下
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 圆形裁切的 Box：图标坐里面居中；点击范围限定在圆形内（clip 在前，水波纹也被裁成圆形）
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Spacer(modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
    }
}
