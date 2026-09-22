package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.ui.components.BackButton
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 开源许可页。
 *
 * 数据来自 AboutLibraries 构建期生成的 `res/raw/aboutlibraries.json`，
 * 本页只负责用 Miuix 风格渲染：每个依赖一张 Card + ArrowPreference，
 * summary 为「作者 · 许可证」，点击直接用系统浏览器打开仓库/主页。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun OssLicensesScreen(onBack: () -> Unit, backStack: NavBackStack) {
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // 解析 aboutlibraries.json（本地 raw 资源，体积小，remember 内同步解析即可）
    val libraries = remember {
        runCatching {
            Libs.Builder().withContext(context).build().libraries
        }.onSuccess {
            // 关键路径日志：加载成功时记录条数，便于排查生成是否被裁剪
            AppLog.d("OssLicenses", "加载开源许可数据成功，共 ${it.size} 项")
        }.onFailure {
            AppLog.e("OssLicenses", "加载开源许可数据失败", it)
        }.getOrDefault(emptyList())
    }

    Scaffold(
        topBar = {
            // 双击顶栏回顶包装层：内层照旧是模糊栏 + 标准顶栏
            DoubleTapToTop(listState, scrollBehavior) {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
            TopAppBar(
                title = "开源许可",
                largeTitle = "开源许可",
                color = blurBarColor(backdrop),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton(onBack = onBack, backStack = backStack)
                }
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
                    // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = (paddingValues.calculateTopPadding() + 8.dp),
                    bottom = contentBottomPadding()
                )
            ) {
                if (libraries.isEmpty()) {
                    // 解析失败或资源缺失时的占位提示
                    item {
                        Text(
                            text = "暂无开源许可信息",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                        )
                    }
                } else {
                    // 每个依赖单独一张 Card + ArrowPreference，样式对齐设置页
                    items(libraries, key = { it.uniqueId }) { library ->
                        val repoUrl = libraryRepoUrl(library)
                        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                            ArrowPreference(
                                title = library.name,
                                summary = librarySummary(library),
                                // 没有仓库/主页链接时禁用点击，避免跳到空地址
                                enabled = repoUrl != null,
                                onClick = repoUrl?.let { url ->
                                    {
                                        // 关键路径：跳转外部浏览器打开依赖仓库
                                        AppLog.d("OssLicenses", "打开仓库: ${library.name} -> $url")
                                        runCatching { uriHandler.openUri(url) }
                                            .onFailure { e -> AppLog.e("OssLicenses", "打开仓库失败: $url", e) }
                                    }
                                },
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
            // 右侧滚动条
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = paddingValues.calculateBottomPadding()),
            )
        }
    }
}

/**
 * 拼 ArrowPreference 的 summary：「作者 · 许可证」。
 * 作者优先取 pom 的 developers，其次 organization；许可证优先 SPDX id，其次名称。
 * 任一段缺失时只保留有值的部分，避免出现孤立的分隔圆点。
 */
private fun librarySummary(library: Library): String {
    val author = library.developers
        .mapNotNull { it.name?.takeIf(String::isNotBlank) }
        .ifEmpty { listOfNotNull(library.organization?.name?.takeIf(String::isNotBlank)) }
        .joinToString(", ")
    val license = library.licenses
        .map { it.spdxId?.takeIf(String::isNotBlank) ?: it.name }
        .distinct()
        .joinToString(", ")
    return listOf(author, license)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
}

/**
 * 仓库地址：优先 scm.url（源码仓库），回退 website（项目主页）。
 * scm.connection 这类 `scm:git:...` 协议串不适合直接给浏览器，不采用。
 */
private fun libraryRepoUrl(library: Library): String? =
    library.scm?.url?.takeIf { it.isNotBlank() && !it.startsWith("scm:") }
        ?: library.website?.takeIf { it.isNotBlank() }
