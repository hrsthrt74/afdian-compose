package com.hrsthrt74.afdian.ui.screens.main.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.content.Changelog
import com.hrsthrt74.afdian.data.content.ReleaseNote
import com.hrsthrt74.afdian.ui.components.basic.BackButton
import com.hrsthrt74.afdian.ui.components.basic.BlurredBar
import com.hrsthrt74.afdian.ui.components.basic.DoubleTapToTop
import com.hrsthrt74.afdian.ui.components.basic.blurBarColor
import com.hrsthrt74.afdian.ui.components.basic.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.basic.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 正式发布包（release.yml 用 `-PbuildChannel=stable` 构建）的通道标识。
 * 用于过滤「未发布」小节 —— 那一节属于还没发出去的内容，stable 包的用户看了只会困惑。
 */
private const val StableBuildChannel = "stable"

/**
 * 更新日志页：设置 → 关于 → 更新日志。
 *
 * 数据来自打包进 assets 的 `CHANGELOG.md`（构建时从仓库根复制，见 app/build.gradle.kts），
 * 解析交给 [Changelog]，本文件只负责排版 —— 这样「应用里显示的日志」和「仓库里的 markdown」
 * 永远是同一份，发版时只改 markdown 一处。
 *
 * 版式：每个版本一张卡片，行内是「版本号 + 日期 + 展开箭头」，点一下展开条目。
 * 手风琴式（同时只展开一个），当前版本默认展开 —— 用户点进来最想看的多半是「我这版改了什么」。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun ChangelogScreen(
    onBack: () -> Unit,
    backStack: NavBackStack,
) {
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val context = LocalContext.current

    // 几 KB 的文本，remember 内同步读一次即可（与「开源许可」页解析 aboutlibraries.json 的做法一致）。
    // 读取失败时 Changelog.load 返回空列表，页面显示「暂无记录」而不是崩掉。
    val notes = remember(context) {
        Changelog.load(context).filterNot { note ->
            note.isUnreleased && BuildConfig.BUILD_CHANNEL == StableBuildChannel
        }
    }
    // 默认展开当前版本；CHANGELOG 里还没写这一版时（本地开发中）退化成展开第一条。
    // 用 remember(notes) 而不是 remember(Unit)：数据变了要重新定位到新的当前版本
    var expandedVersion by remember(notes) {
        mutableStateOf(
            notes.firstOrNull { it.version == BuildConfig.VERSION_NAME }?.version
                ?: notes.firstOrNull()?.version,
        )
    }

    Scaffold(
        topBar = {
            DoubleTapToTop(listState, scrollBehavior) {
                BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
                    TopAppBar(
                        title = "更新日志",
                        largeTitle = "更新日志",
                        color = blurBarColor(backdrop),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack = onBack, backStack = backStack) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (notes.isEmpty()) {
                // assets 里读不到 / 解析不出任何版本时的兜底。正常构建不会走到这里
                Text(
                    text = "暂无更新记录",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 64.dp),
                )
            } else {
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
                    items(notes, key = { it.version }) { note ->
                        ReleaseNoteCard(
                            note = note,
                            expanded = note.version == expandedVersion,
                            isCurrent = note.version == BuildConfig.VERSION_NAME,
                            // 手风琴：点已展开的版本 = 收起；点别的版本 = 它展开、上一个自动收起
                            onToggle = {
                                expandedVersion = if (note.version == expandedVersion) null else note.version
                            },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
                // 右侧滚动条：top 让出顶栏高度
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
}

/**
 * 一个版本的卡片。
 *
 * @param note 该版本的数据
 * @param expanded 是否展开条目
 * @param isCurrent 是否是当前安装的版本（行内给一个小标记）
 * @param onToggle 点击版本行的回调
 */
@Composable
private fun ReleaseNoteCard(
    note: ReleaseNote,
    expanded: Boolean,
    isCurrent: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 箭头：收起时朝上、展开时朝下（与参考版式一致）。
    // rotate 用 0/180 之外的 270/90：ChevronForward 默认朝右，转 90° 朝下、270° 朝上
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 270f,
        label = "changelogChevron",
    )

    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 版本号：v 前缀是约定俗成的写法，也避免和正文数字混在一起。
                // 「未发布」那一节不加 —— 「v未发布」读起来很怪
                Text(
                    text = if (note.isUnreleased) note.version else "v${note.version}",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = if (note.isUnreleased) scheme.onSurfaceVariantSummary else scheme.onSurface,
                )
                if (isCurrent) {
                    // 胶囊样式照商品管理的「上架中」标签（ShopProductCard.ShopStatusTag）：
                    // primaryContainer 底 + 6dp 圆角，比纯文字更醒目，也不至于抢过版本号本身
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "当前",
                            style = MiuixTheme.textStyles.footnote2,
                            color = scheme.onPrimaryContainer,
                        )
                    }
                }
                // 日期靠右、紧挨箭头。没日期（未发布）时留空，不占位
                Spacer(modifier = Modifier.weight(1f))
                note.date?.let { date ->
                    Text(
                        text = date,
                        style = MiuixTheme.textStyles.footnote1,
                        color = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = scheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(16.dp)
                        .rotate(chevronRotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                ReleaseNoteItems(
                    items = note.items,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/**
 * 一组更新条目（圆点 + 正文）。
 *
 * 关于页的「本版更新」摘要也用它 —— 两处的条目样式必须一致，
 * 各画一份的话，某天调了行距就会变成「摘要和详情里同一个条目长得不一样」。
 *
 * @param items 条目文案（已由 [Changelog] 剥掉 markdown 标记）
 * @param modifier 外部修饰符，调用方负责四周留白
 */
@Composable
internal fun ReleaseNoteItems(
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    // 行高比默认 body2 松一档：条目多是「一句话一个功能」，行距太挤会糊成一整段
    val itemStyle = MiuixTheme.textStyles.body2.copy(lineHeight = 22.sp)
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            Row(
                // 最后一条不带尾部间距：卡片底边的留白交给调用方控制，
                // 否则条目间距会和卡片内边距叠加，底部看着比顶部空一大截
                modifier = if (index == items.lastIndex) Modifier else Modifier.padding(bottom = 10.dp),
            ) {
                // 圆点单独占一列：条目换行时第二行会和文字左对齐，
                // 而不是绕到圆点下面（直接拼 "• " 字符串就会这样）
                Text(
                    text = "•",
                    style = itemStyle,
                    color = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.width(14.dp),
                )
                Text(
                    text = item,
                    style = itemStyle,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
