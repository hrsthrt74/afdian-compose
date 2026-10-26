package com.hrsthrt74.afdian.ui.screens.main.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 设置总览页与三个分类页共用的页面骨架。
 *
 * 抽出这份骨架的理由：五页的「大标题顶栏 + 模糊栏 + 双击回顶 + 右侧滚动条 + 边界触觉」
 * 完全一致，只有标题和列表内容不同。分开各写一份迟早会漂移（例如某页忘了开模糊），
 * 而设置页又是入口密集、改动频繁的地方，统一在这里最省心。
 *
 * 注意：列表项由调用方通过 [content] 提供，状态（开关、弹窗等）仍由各页自己持有，
 * 这样每页只订阅自己需要的 DataStore 流，不必把整份设置状态传进来。
 *
 * @param title 顶栏标题（小标题与大标题共用同一个文案）
 * @param onBack 点击返回按钮时调用（通常弹栈）
 * @param backStack 导航栈，交给 [BackButton] 做长按「回到主页」菜单
 * @param topContentPadding 列表内容在顶栏之外的额外顶部间距；
 *        总览页要保持账号分区的原始间距，传 0.dp，其余页面用默认 8.dp
 * @param content 页面的 LazyColumn 内容
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    backStack: NavBackStack,
    topContentPadding: Dp = 8.dp,
    content: LazyListScope.() -> Unit,
) {
    // 模糊相关设置：总开关与渐进模糊都由「设置 → 外观」写入，这里只读
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    Scaffold(
        topBar = {
            // 双击顶栏回到顶部并展开大标题
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = title,
                        largeTitle = title,
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        // 返回按钮：点击弹栈，长按可快速回到主页
                        navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // 采样层只包滚动内容，不包带 textureBlur 的顶栏
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    // 放在 nestedScroll 之后：fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + topContentPadding,
                    // 设置页底部多留一点呼吸空间，避免最后一个卡片贴住系统导航栏
                    bottom = contentBottomPadding() + 48.dp,
                ),
            ) {
                content()
            }
            // 右侧滚动条：top 让出顶栏高度，随大标题折叠自动跟随
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
            )
        }
    }
}
