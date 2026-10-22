package com.hrsthrt74.afdian.ui.screens.main.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.content.KnownLimitations
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 功能完成度页：列出当前**尚未完成**的功能。
 *
 * 文案与 README 的「已知限制」同源（[KnownLimitations]），只在这里常驻展示：
 * 用户随时可以回来看完整清单，不用等弹窗提醒。
 *
 * 之所以不把它做成「已实现功能列表」：那会随每次发版大改，而「还没做完什么」
 * 才是用户真正需要的信息 —— 避免用户点了个功能发现是空的却没人告诉他。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun FeatureStatusScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
) {
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "功能完成度",
                        largeTitle = "功能完成度",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
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
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = contentBottomPadding(),
                ),
            ) {
                item(key = "intro_card") {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        // 引导文案：与冷启动弹窗的措辞保持一致
                        BasicComponent(
                            title = KnownLimitations.NOTICE_SUMMARY,
                            summary = "这些都是已知问题，不影响其余功能的正常使用。",
                        )
                    }
                }

                item(key = "issues_title") {
                    SmallTitle(text = "尚未完成的功能", modifier = Modifier.padding(start = 4.dp))
                }
                items(KnownLimitations.issues, key = { it.title }) { issue ->
                    // 每条一个 Card：标题是功能名，summary 是当前状态
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        BasicComponent(
                            title = issue.title,
                            summary = issue.detail,
                        )
                    }
                }

                item(key = "version_footer") {
                    // 页脚：版本与构建变体，方便用户报问题时直接报这两串
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                            " · ${BuildConfig.APP_VARIANT_NAME}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
            // 右侧滚动条
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                ),
            )
        }
    }
}
