package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.navigation.Route
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.hrsthrt74.afdian.ui.viewmodel.MainViewModel
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import top.yukonga.miuix.kmp.blur.layerBackdrop
// Miuix 提供的「滚动到边界触觉反馈」修饰符：列表快速滑动到顶/底边界时震动一下
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 「我的」tab 的内容页。二级路由（收藏/我发电的/订单/管理/设置）不再在这里嵌套
 * NavDisplay，而是通过 [onNavigate] 提升到 MainScreen 外层栈全屏覆盖，
 * 避免 Pager 页内过渡层平移溢出泄漏到相邻 tab 并触发重复 contentKey 闪退。
 */
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    onOpenCreator: (AfadianUser) -> Unit,
    onNavigate: (Route) -> Unit,
) {
    ProfileMainScreen(
        viewModel = viewModel,
        onLogout = onLogout,
        onNavigate = onNavigate,
        onOpenCreator = onOpenCreator,
    )
}

@Composable
private fun ProfileMainScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    onNavigate: (Route) -> Unit,
    onOpenCreator: (AfadianUser) -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    val profile by viewModel.profile.collectAsState()
    val sponsoring by viewModel.sponsoring.collectAsState()

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
                // 我的
                item { SmallTitle(text = "我的", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        ArrowPreference(
                            title = "我发电的",
                            summary = "${sponsoring.size} 位创作者",
                            onClick = { onNavigate(Route.Sponsoring) }
                        )
                    }
                }

                // 我的订单
                item { SmallTitle(text = "我的订单", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        ArrowPreference(
                            title = "全部订单",
                            summary = "查看赞助和商品订单",
                            onClick = { onNavigate(Route.Orders) }
                        )
                    }
                }

                // 管理
                item { SmallTitle(text = "管理", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        ArrowPreference(
                            title = "管理中心",
                            summary = "概况、收入、订单和赞助者",
                            onClick = { onNavigate(Route.Management) }
                        )
                    }
                }

                // 我的收藏
                item { SmallTitle(text = "我的收藏", modifier = Modifier.padding(start = 4.dp)) }
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        ArrowPreference(
                            title = "创作者 / 动态 / 众筹",
                            summary = "查看我的收藏",
                            onClick = { onNavigate(Route.Favorites) }
                        )
                        ArrowPreference(
                            title = "设置",
                            summary = "偏好、安全和关于",
                            onClick = { onNavigate(Route.Settings) }
                        )
                    }
                }

                // 退出登录
                item {
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("退出登录")
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
