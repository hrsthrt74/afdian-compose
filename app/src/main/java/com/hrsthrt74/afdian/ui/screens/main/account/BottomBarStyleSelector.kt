package com.hrsthrt74.afdian.ui.screens.main.account

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.screens.main.mainTabs
import com.hrsthrt74.afdian.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

/**
 * 底栏样式档位的取值，与 [com.hrsthrt74.afdian.data.settings.SettingsStore.bottomBarStyle] 的约定一致。
 * 只有两档，直接用 Int 与存储层对齐，不额外造枚举（如无必要勿增实体）。
 */
private const val BottomBarStyleRegular = 0
private const val BottomBarStyleFloating = 1

/** 预览卡上的档位标签，顺序即从左到右的排列顺序。 */
private val bottomBarStyleOptions = listOf(
    BottomBarStyleRegular to "常规",
    BottomBarStyleFloating to "悬浮",
)

/** 预览卡高度。宽度由 Row 的 weight 决定（约 180dp），差不多是截图里那种「一块壁纸」的比例。 */
private val PreviewCardHeight = 96.dp

/** 预览卡圆角（squircle 连续圆角）。 */
private val PreviewCardCorner = 16.dp

/** 选中环的描边粗细。 */
private val PreviewRingWidth = 3.dp

/**
 * 选中环与卡片之间的空隙。
 *
 * 描边本身要吃掉 [PreviewRingWidth]，所以留 8dp 时真正看得见的白缝约 6dp —— 环明显离卡片远一截。
 */
private val PreviewRingGap = 6.dp

/**
 * 预览卡的背景渐变：上 `#4170FF` → 下 `#5ABAFE`。
 *
 * 固定色而非按主题色推导 —— 这一块纯粹是给 navbar 当「壁纸」看的底，
 * 固定下来两种明暗模式、任何主题色下都长一个样，比较起来才有意义。
 */
private val PreviewCardBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFF4170FF), Color(0xFF5ABAFE)),
)

/**
 * 虚拟视口宽度：预览里的 navbar 按「360dp 宽的屏幕」排版，物理上只占卡片那点宽度。
 *
 * 取 360dp 是为了接近常见手机的屏幕宽度，于是「卡片宽 / 360dp」得到的缩放比
 * 也让 navbar 的图标、字号比例与真机上的视觉关系一致。
 */
private val PreviewVirtualWidth = 360.dp

/** 常规档 navbar 的虚拟高度：正好是 [NavigationBarItem] 的行高，底部不留缝隙，做成贴底。 */
private val RegularBarVirtualHeight = 64.dp

/**
 * 悬浮档 navbar 的虚拟高度：胶囊 52dp + 底部留白 26dp。
 *
 * miuix 的 [FloatingNavigationBar] 在内部写死了 `26dp + 系统导航栏 insets` 的底部间距，
 * 这个值会跟着设备手势条变。这里把虚拟高度卡死在 78dp 并裁剪溢出部分，
 * 于是预览里的悬浮间距恒为 26dp，不会在不同机器上跳来跳去。
 */
private val FloatingBarVirtualHeight = 78.dp

/** 标签与卡片之间的间距。 */
private val PreviewLabelSpacing = 6.dp

/**
 * 「底栏样式」选择器：两张实时预览卡并排，卡片里画的是**真正的 navbar**（等比缩小、不可交互）。
 *
 * @param style 当前档位（[BottomBarStyleRegular] / [BottomBarStyleFloating]）
 * @param onSelect 选中某档时回调，直接透传 [SettingsViewModel.setBottomBarStyle]
 */
@Composable
fun BottomBarStyleSelector(
    style: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        bottomBarStyleOptions.forEach { (optionStyle, label) ->
            BottomBarStylePreviewCard(
                style = optionStyle,
                label = label,
                selected = optionStyle == style,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(optionStyle)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 单个档位的预览卡：渐变底 + 真 navbar + 下方标签，选中时外面套一圈主题色描边。
 */
@Composable
private fun BottomBarStylePreviewCard(
    style: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 点击层与按下反馈共用同一个交互源：selectable 负责发出按压/点击，
    // pressable 里的 SinkFeedback 订阅同一个源，于是「下沉」作用在整块卡片上（看得见）。
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            // 按下时整块下沉。indication 必须加在包住卡片本体的这一层，
            // 加在下面那层透明命中层上的话缩放的是空气，用户看不到任何反馈。
            .pressable(
                interactionSource = interactionSource,
                indication = SinkFeedback(),
                delay = null,
            ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 选中环：用 squircleBorder 画在外层盒子上，中间靠 padding 留出缝隙
            //（描边不占布局尺寸，只能靠 padding 撑开「环」与卡片之间的白）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleBorder(
                        width = PreviewRingWidth,
                        color = if (selected) MiuixTheme.colorScheme.primary else Color.Transparent,
                        cornerRadius = PreviewCardCorner + PreviewRingGap,
                    )
                    .padding(PreviewRingGap),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PreviewCardHeight)
                        // 连续圆角裁切：底栏的底角会跟着一起被裁成 squircle
                        .squircleClip(PreviewCardCorner)
                        // 背景渐变：整张卡就是一块「壁纸」，navbar 浮在上面
                        .background(PreviewCardBrush),
                ) {
                    // 预览用的 navbar 纯装饰：整棵子树清掉无障碍语义，
                    // 否则 TalkBack 会在卡片里读到四个「已禁用」的 tab
                    BottomBarPreview(style = style)

                    // 命中层：盖在真 navbar 之上，先接住所有点击。
                    // 必须这样盖一层 —— FloatingNavigationBar 内部自带 pointerInput 吞点击，
                    // 直接给外层加 clickable 会被它吃掉，点卡片就没反应。
                    // indication 传 null：下沉反馈由外层的 pressable 负责，这里不要波纹。
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            // 命中层自己没有文字，把档位名塞进无障碍描述
                            .semantics { contentDescription = label }
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.RadioButton,
                                onClick = onClick,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(PreviewLabelSpacing))

            // 档位标签：选中时用主题色，未选中用次要文字色
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
                color = if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 预览卡里的 navbar 本体：按 360dp 的虚拟视口排版，物理上缩到卡片宽度。
 *
 * 缩小用的是「缩密度」而不是 `graphicsLayer` 缩画布：把 `LocalDensity` 按比例调小后，
 * navbar 里所有 dp/sp 都会等比变小，而**布局宽度仍然是父级的真实宽度** ——
 * 于是 `fillMaxWidth` 的整条底栏照样铺满卡片、`FloatingNavigationBar` 的胶囊照样居中。
 * 早先那版是「按 360dp 排版再整体缩放」，排版尺寸比卡片宽一截、要靠裁剪收边，
 * 一旦哪里对不齐就会整条偏到左边去，不再用了。
 */
@Composable
private fun BottomBarPreview(
    style: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        // 纯装饰：清掉整棵子树的语义（也顺带盖住虚线边框之类的无意义节点）
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { },
        // 底栏贴在卡片底部，与真机上「底栏贴屏幕底」的关系一致
        contentAlignment = Alignment.BottomCenter,
    ) {
        val density = LocalDensity.current
        // 卡片实际宽 / 虚拟视口宽 = 缩放比
        val scale = maxWidth / PreviewVirtualWidth
        val floating = style == BottomBarStyleFloating
        // 底栏块的虚拟高度：贴底档正好一条，悬浮档是胶囊 + 底部留白
        val blockHeight = if (floating) FloatingBarVirtualHeight else RegularBarVirtualHeight

        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.density * scale,
                // 字号跟随系统设置照旧，只是整体一起缩小
                fontScale = density.fontScale,
            ),
        ) {
            // 这里所有 dp 都是「虚拟 dp」：blockHeight 物理上只有 blockHeight×scale 高；
            // 对齐由外层 BoxWithConstraints 的 BottomCenter 负责，贴底不跑偏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(blockHeight)
                    .clipToBounds(),
            ) {
                if (floating) {
                    // 悬浮档：真·浮动胶囊。defaultWindowInsetsPadding 关掉，避免预览再叠一层系统栏内边距。
                    // unbounded 必须开：miuix 在这个组件内部写死了「胶囊下方 26dp + 系统手势条高度」的留白，
                    // 若按 78dp 直接量，胶囊本体会被约束压扁成一条 —— 放开高度让它按自然尺寸排版，
                    // 多出来的部分由外层 clipToBounds 裁掉。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(unbounded = true, align = Alignment.Top),
                    ) {
                        FloatingNavigationBar(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                            defaultWindowInsetsPadding = false,
                        ) {
                            mainTabs.forEachIndexed { index, tab ->
                                FloatingNavigationBarItem(
                                    selected = index == 0,
                                    onClick = {},
                                    // 纯展示：禁用后不再响应点击，也读不出「可点」的无障碍语义
                                    enabled = false,
                                    icon = tab.icon(),
                                    label = tab.title,
                                )
                            }
                        }
                    }
                } else {
                    // 常规档：真·贴底整条。showDivider 关掉，卡片边缘已经裁好圆角，不需要分隔线
                    NavigationBar(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        showDivider = false,
                        defaultWindowInsetsPadding = false,
                    ) {
                        mainTabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = index == 0,
                                onClick = {},
                                enabled = false,
                                icon = tab.icon(),
                                label = tab.title,
                            )
                        }
                    }
                }
            }
        }
    }
}
