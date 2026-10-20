package com.hrsthrt74.afdian.ui.components.basic

// 通用「锚定弹层」骨架：把一个 MutableState<Boolean> + 锚点矩形，渲染成 miuix ListPopup 风格的菜单。
//
// 由会话页的气泡操作菜单（ui/components/MessageBubbleActionPopup.kt）抽取而来：
// 原先外壳（PopupLayout + 三轨动画 + 挖洞压暗 + ListPopupContent 展开 + 摆放算法）
// 与业务内容（inline 行 / 下拉列表 / 时间行）写在同一文件里，消息列表页需要同样的
// 观感（长按卡片弹出置顶菜单）时无法复用，故把外壳与 inline 单元抽到这里。
//
// 关键点：
// - 外壳经 MiuixPopupUtils.PopupLayout 渲染在根 Scaffold 的 MiuixPopupHost（全窗口最顶层），
//   因此能覆盖顶栏与输入栏/底栏。
// - 压暗层为「挖洞遮罩」：全屏 scrim 用 Path(EvenOdd) 在锚点处挖一个与卡片同形（squircle）
//   的透明洞，原内容直接透出，实现「背景压暗但选中项豁免」的观感。
// - 不用库自带的 OverlayListPopup / WindowListPopup：它们的 dim 画在库内部，无法挖洞。
// - PopupLayout 的 content 里 LocalDismissState 为 null（那是 Window 系封装专供），
//   菜单项回调里直接置 visible.value = false 关闭即可。
//
// 注意：本文件只负责「外壳 + inline 单元」。导航滑动闸门（NavSwipeGate）属于会话页的业务
// 副作用，仍留在 MessageBubbleActionPopup 内，不要放进来——否则列表页弹层关闭时也会去
// 复位闸门，两处 onDispose 会互相干扰。

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupContent
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupLayoutPosition
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.squircle.isSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

private const val TAG = "InlinePopup"

/** inline 行内首/末动作的水平内缩边距（仅多动作行生效；单动作行保持居中不加） */
internal val InlineEdgePadding: Dp = 8.dp

/**
 * 一条可渲染为 inline 操作的动作：图标在上、文字在下。
 *
 * 做成接口而不是「label + icon 两个 lambda 参数」的原因：调用方多是枚举
 * （[com.hrsthrt74.afdian.ui.components.BubbleAction]、会话长按菜单的 ConversationAction），
 * 让枚举直接实现本接口就能整表传进 [InlineActionPopup]，回调里也能拿回枚举本身做分支。
 *
 * 必须是 public：public 的枚举不能暴露 internal 超类型，否则编译失败。
 */
interface InlineActionItem {
    /** 菜单展示文案 */
    val label: String

    /** inline 操作图标；为 null 的项不会被渲染成格子（见 [InlineActionRow]） */
    val icon: ImageVector?
}

/**
 * 通用锚定弹层外壳。
 *
 * 「点某处 → 在它旁边弹出一块内容」的通用骨架，与业务无关：
 * 消息气泡菜单、会话长按菜单、账号头像菜单都复用它。
 * 内部负责遮罩挖洞、三轨动画（展开/淡入/压暗）、锚点摆放与收回手势，
 * 调用方只关心 [content] 里放什么。
 *
 * @param visible PopupLayout 的显示开关（调用方持有，PopupLayout 要求 MutableState）
 * @param anchorBounds 锚点（被选中的卡片/气泡/头像）的窗口坐标快照；null 时不画遮罩与菜单
 * @param alignEnd 菜单是否贴锚点右缘（true）或左缘（false）——同时决定展开动画的缩放原点
 * @param content 菜单内容：直接写列内子项即可，等宽规则由内部的 ListPopupColumn 统一提供
 */
@Composable
fun InlinePopupLayout(
    visible: MutableState<Boolean>,
    anchorBounds: Rect?,
    alignEnd: Boolean,
    content: @Composable () -> Unit,
) {
    // 菜单实测尺寸：尺寸回调回填；未测得前菜单 alpha = 0，不会闪现
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    // popup 内容层在窗口中的原点：理论上与页面同原点 (0,0)，
    // 但这里仍做坐标差修正，不依赖「恰好对齐」的假设（兼容 insets/转场等偏移）
    var hostOriginInWindow by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    // 系统栏 insets 对象在组合期获取（statusBars/navigationBars 是 @Composable 属性），
    // 之后在派生状态里只调用其普通方法 getTop/getBottom
    val statusBarInsets = WindowInsets.statusBars
    val navBarInsets = WindowInsets.navigationBars

    // 菜单摆放（偏移 + 是否在锚点下方）：由锚点快照/菜单实测尺寸/宿主原点推导。
    // 用 derivedStateOf 统一读取，避免在布局（offset）阶段写状态引发重组循环。
    val placement by remember(anchorBounds, alignEnd) {
        derivedStateOf {
            calculateMenuOffset(
                anchorBounds = anchorBounds,
                menuSize = menuSize,
                windowSize = windowInfo.containerSize,
                alignEnd = alignEnd,
                density = density,
                statusBarTop = statusBarInsets.getTop(density),
                navBarBottom = navBarInsets.getBottom(density),
                hostOrigin = hostOriginInWindow,
            )
        }
    }

    // 展开动画三轨，全部对齐 miuix ListPopupLayout 原版参数：
    // fraction → 菜单 scale + 方向性 clip-reveal（spring）；
    // menuAlpha → 菜单内容淡入淡出（tween 200/150）；
    // dimProgress → 压暗层透明度（tween 300/150，观感与库内 dim 一致）
    val menuFraction = remember { Animatable(0f) }
    val menuAlpha = remember { Animatable(0f) }
    val dimProgress = remember { Animatable(0f) }
    LaunchedEffect(visible.value) {
        AppLog.d(TAG, "弹层 visible=${visible.value}")
        if (visible.value) {
            menuFraction.snapTo(0f)
            menuAlpha.snapTo(0f)
            dimProgress.snapTo(0f)
            // LaunchedEffect 自身就是协程作用域，launch 并行推进三轨动画
            launch { menuFraction.animateTo(1f, ListPopupDefaults.FractionAnimationSpec) }
            launch { menuAlpha.animateTo(1f, ListPopupDefaults.AlphaEnterAnimationSpec) }
            launch { dimProgress.animateTo(1f, ListPopupDefaults.DimEnterAnimationSpec) }
        } else {
            // 退出：整体淡出由 PopupLayout 的 fadeOut 兜底，这里同步收缩回落
            launch { menuFraction.animateTo(0f, ListPopupDefaults.FractionAnimationSpec) }
            launch { menuAlpha.animateTo(0f, ListPopupDefaults.AlphaExitAnimationSpec) }
            launch { dimProgress.animateTo(0f, ListPopupDefaults.DimExitAnimationSpec) }
        }
    }

    MiuixPopupUtils.PopupLayout(
        visible = visible,
        // 不用库的整窗压暗（无法挖洞），由 content 内的挖洞遮罩代替
        enableWindowDim = false,
        // 返回键关闭由 PopupEntry 内部的 NavigationBackHandler 处理（置 visible=false）
        enableBackHandler = true,
        // 退出动画比库默认（150ms）更短：关闭后弹层仍留在组合中并拦截触摸，
        // 残留窗口越长，「视觉上已关闭、实际还没卸载」时点击被无声吃掉的概率越高。
        // 快速卸载消除这段空窗（两处调用方共用同一条手感，不要单独改长）。
        exitTransition = fadeOut(animationSpec = tween(durationMillis = 90)),
    ) {
        // 全屏容器：点击任意空白处关闭菜单；同时记录原点供坐标修正。
        // 注意 PopupEntry 在 enableWindowDim=false 时会额外铺一层消费全部指针事件的全屏
        // Box（见 MiuixPopupUtils），所以弹层打开期间下层页面既不能滚动也不能横滑。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { hostOriginInWindow = it.positionInWindow() }
                .pointerInput(Unit) {
                    detectTapGestures {
                        AppLog.d(TAG, "点击空白处，关闭弹层")
                        visible.value = false
                    }
                },
        ) {
            // 挖洞压暗层：全屏变暗、锚点位置透出（淡入淡出节奏对齐库内 dim 动画）
            if (anchorBounds != null) {
                HolePunchDimScrim(
                    anchorBoundsInWindow = anchorBounds,
                    dimAlpha = { dimProgress.value },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 菜单本体：定位容器只负责把内容摆到锚点侧（下方优先 → 上方），
            // 视觉容器用 miuix 原版 ListPopupContent——surfaceContainer 背景板 +
            // squircle 圆角 + scale/clip-reveal 方向性展开 + 淡入淡出，
            // 与全库 ListPopup（如长按返回键菜单）观感完全一致
            Box(
                modifier = Modifier.offset { placement.offset },
            ) {
                ListPopupContent(
                    // 实测尺寸回填（替代 onSizeChanged），供定位算法计算摆放位置
                    popupContentSize = menuSize,
                    onPopupContentSizeChange = { menuSize = it },
                    fractionProgress = { menuFraction.value },
                    alphaProgress = { menuAlpha.value },
                    // 展开方向：从锚点侧长出（下方弹出从顶边展开，反之从底边）
                    popupLayoutPosition = PopupLayoutPosition(
                        showBelow = placement.below,
                        showAbove = !placement.below,
                        isRightAligned = alignEnd,
                    ),
                    localTransformOrigin = TransformOrigin(
                        pivotFractionX = if (alignEnd) 1f else 0f,
                        pivotFractionY = if (placement.below) 0f else 1f,
                    ),
                ) {
                    // 菜单列：宽度 200-288dp 自适应取最宽项。ListPopupColumn 会把所有
                    // 子项强制测成同一宽度，所以内容里的行必须用 Row + weight(1f) 均分宽度
                    ListPopupColumn {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 只渲染一行 inline 动作的通用菜单（置顶 / 标为已读这类「一格一个动作」的场景）。
 *
 * 点击顺序与气泡菜单完全一致：先轻震 → 关闭弹层 → 执行动作。
 *
 * @param actions 动作集合；`icon` 为 null 的项会被跳过，`isEnabled` 为 false 的项置灰占位
 * @param isEnabled 单项是否可点击（默认全部可点）；false 的项保留位置与文案但不可点，
 *   用于「当前没有该操作可做」的状态（如已读会话的「标为已读」）
 * @param onSelected 动作点击回调（关闭动作已由本组件负责，回调只管执行动作）
 */
@Composable
fun <T : InlineActionItem> InlineActionPopup(
    visible: MutableState<Boolean>,
    anchorBounds: Rect?,
    alignEnd: Boolean = false,
    actions: List<T>,
    isEnabled: (T) -> Boolean = { true },
    onSelected: (T) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    InlinePopupLayout(
        visible = visible,
        anchorBounds = anchorBounds,
        alignEnd = alignEnd,
    ) {
        InlineActionRow(
            actions = actions,
            onSelected = { action ->
                // 与列表项一致：先轻震再关闭，最后执行动作
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                visible.value = false
                onSelected(action)
            },
            isEnabled = isEnabled,
        )
    }
}

/**
 * inline 操作行：图标上、文字下，每行至多 2 个（超出自动换到下一行）。
 *
 * 行的纵向边距不放在 Row 上，而是传入 [InlineActionCell] 内部（clickable 之后应用），
 * 让按压高亮覆盖含边距的整行高度。
 *
 * @param isEnabled 单项是否可点击：false 时该格**保留占位**（位置不塌、文案照显）但置灰不可点，
 *   用于「当前没有该操作可做」的场景（如已读会话的「标为已读」）。
 *   置灰而不是隐藏：菜单项数量与位置稳定，不会因为一次操作成功就整行消失。
 */
@Composable
fun <T : InlineActionItem> InlineActionRow(
    actions: List<T>,
    onSelected: (T) -> Unit,
    isEnabled: (T) -> Boolean = { true },
) {
    // 先剔掉没有图标的项：否则 cell 内部要提前 return，会在同行留下一个空的权重格
    val rows = actions.filter { it.icon != null }.chunked(2)
    rows.forEach { rowActions ->
        Row(modifier = Modifier.fillMaxWidth()) {
            rowActions.forEachIndexed { colIndex, action ->
                InlineActionCell(
                    action = action,
                    // 是否可点：由调用方按业务状态决定（置灰但仍占位）
                    enabled = isEnabled(action),
                    topPadding = (DropdownDefaults.MiddleVerticalPadding + 4.dp),
                    bottomPadding = DropdownDefaults.MiddleVerticalPadding,
                    // 行内水平内缩：首个动作加左、末个动作加右，
                    // 让边缘内容与分割线（16dp 内缩）的节奏更协调；
                    // 行内只有一个动作时保持居中，不加额外水平边距
                    startPadding = if (rowActions.size > 1 && colIndex == 0) {
                        InlineEdgePadding
                    } else {
                        0.dp
                    },
                    endPadding = if (rowActions.size > 1 && colIndex == rowActions.lastIndex) {
                        InlineEdgePadding
                    } else {
                        0.dp
                    },
                    onSelected = { onSelected(action) },
                )
            }
        }
    }
}

/**
 * inline 快捷操作单元：图标上、文字下，在行内均分宽度（weight(1f)）。
 * 观感对齐 [top.yukonga.miuix.kmp.basic.DropdownImpl] 列表项：同字号、同文字色
 * （onSurfaceContainer），点击整格有涟漪反馈。
 *
 * 边距均在 clickable 之后应用（属于动作本体），按压高亮覆盖含边距的整格范围。
 *
 * @param enabled 是否可点击；false 时图标与文字改用 miuix 的禁用色
 *   （`disabledOnSecondaryVariant`，与 DropdownImpl 的禁用项同款），且不再响应点击/涟漪
 * @param topPadding 上方额外边距
 * @param bottomPadding 下方额外边距
 * @param startPadding 左侧额外边距——行内首个动作用；单动作行传 0 保持居中
 * @param endPadding 右侧额外边距——行内末个动作用；单动作行传 0 保持居中
 */
@Composable
private fun RowScope.InlineActionCell(
    action: InlineActionItem,
    enabled: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
    startPadding: Dp = 0.dp,
    endPadding: Dp = 0.dp,
    onSelected: () -> Unit,
) {
    // inline 项必有图标（[InlineActionRow] 已过滤）；这里再兜一次，避免出现空壳格
    val icon = action.icon ?: return

    // 可点用正常内容色、不可点用 miuix 禁用色：与全库下拉菜单的置灰规则保持一致
    val contentColor = if (enabled) {
        MiuixTheme.colorScheme.onSurfaceContainer
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    Column(
        modifier = Modifier
            .weight(1f)
            // enabled = false 时 clickable 不接事件、也不画涟漪，但整格仍参与布局（占位不塌）
            .clickable(enabled = enabled, onClick = onSelected)
            // 四向边距都放在 clickable 之后：边距属于动作本体的一部分，
            // 按压高亮覆盖含边距的整格范围（水平与纵向同一处理）
            .padding(
                top = topPadding,
                bottom = bottomPadding,
                start = startPadding,
                end = endPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = action.label,
            modifier = Modifier.size(24.dp),
            tint = contentColor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = action.label,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
        )
    }
}

/**
 * 全屏挖洞压暗层：整层填充 windowDimming 色，但用 Path(EvenOdd) 在
 * [anchorBoundsInWindow] 处挖一个与锚点同形（squircle 圆角）的透明洞，
 * 原内容从洞中直接透出，视觉上「未被压暗」。
 *
 * @param dimAlpha 压暗层透明度提供者（0-1，随开关动画变化；洞内不受影响）
 * @param cornerRadius 洞的圆角，默认与 miuix Card 默认圆角（16dp）一致
 */
@Composable
internal fun HolePunchDimScrim(
    anchorBoundsInWindow: Rect,
    dimAlpha: () -> Float,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
) {
    val scrimColor = MiuixTheme.colorScheme.windowDimming
    // 是否启用 squircle 形状（与卡片同源开关；关闭时 addSquircleRect 自动退化为圆角矩形）
    val squircleEnabled = isSquircleEnabled()
    // 本层在窗口中的原点：锚点是窗口坐标，绘制需要本地坐标，做差值修正
    var originInWindow by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            // 压暗淡入淡出：整层 alpha 跟随 dimProgress，锚点洞同样从透明渐入
            .graphicsLayer { alpha = dimAlpha() }
            .onGloballyPositioned { originInWindow = it.positionInWindow() }
            .drawWithCache {
                // 锚点矩形换算到本层本地坐标
                val holeLeft = anchorBoundsInWindow.left - originInWindow.x
                val holeTop = anchorBoundsInWindow.top - originInWindow.y
                val holeWidth = anchorBoundsInWindow.width
                val holeHeight = anchorBoundsInWindow.height
                val radiusPx = cornerRadius.toPx()

                // 洞路径：squircle 从 (0,0) 起画，用 addPath 附带偏移并入遮罩路径
                val holePath = Path().apply {
                    addSquircleRect(
                        width = holeWidth,
                        height = holeHeight,
                        cornerRadius = radiusPx,
                        squircleEnabled = squircleEnabled,
                    )
                }
                // 遮罩路径：整层矩形 + 锚点洞（EvenOdd：两形相交处即洞内为空）
                val scrimPath = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addPath(holePath, Offset(holeLeft, holeTop))
                }
                onDrawBehind {
                    drawPath(path = scrimPath, color = scrimColor)
                }
            },
    )
}

/** 菜单摆放结果：最终偏移（宿主本地坐标）+ 是否位于锚点下方（用于展开动画方向） */
internal data class MenuPlacement(val offset: IntOffset, val below: Boolean)

/**
 * 计算菜单摆放位置（窗口坐标 → 宿主本地坐标）：
 * - y：锚点下方 8dp 优先；下方放不下（触底）→ 锚点上方 8dp；两侧都放不下 → clamp 贴底
 * - x：右对齐锚点（alignEnd）或左对齐锚点；再 coerce 到窗口内（左右各留 12dp）
 * - 状态栏/导航栏安全边界由 [statusBarTop]/[navBarBottom]（px）传入，避免菜单
 *   顶进状态栏或压到手势条
 */
internal fun calculateMenuOffset(
    anchorBounds: Rect?,
    menuSize: IntSize,
    windowSize: IntSize,
    alignEnd: Boolean,
    density: Density,
    statusBarTop: Int,
    navBarBottom: Int,
    hostOrigin: Offset,
): MenuPlacement {
    // 锚点/菜单尺寸未就绪时先放 (0,0)（此时菜单 alpha = 0，不会闪现）
    if (anchorBounds == null || menuSize == IntSize.Zero || windowSize == IntSize.Zero) {
        return MenuPlacement(IntOffset.Zero, below = true)
    }
    val marginPx = with(density) { 8.dp.roundToPx() }    // 菜单与锚点的垂直间距
    val hEdgePx = with(density) { 12.dp.roundToPx() }    // 菜单距屏幕左右的水平边距
    val windowBottom = windowSize.height - navBarBottom

    // x：贴锚点对应边缘（我方气泡右缘对齐、对方气泡左缘对齐），再夹进窗口水平范围
    val rawX = if (alignEnd) {
        anchorBounds.right.toInt() - menuSize.width
    } else {
        anchorBounds.left.toInt()
    }
    val x = rawX.coerceIn(hEdgePx, (windowSize.width - menuSize.width - hEdgePx).coerceAtLeast(hEdgePx))

    // y：下方优先 → 上方 → clamp 贴底
    val belowY = anchorBounds.bottom.toInt() + marginPx
    val fitsBelow = belowY + menuSize.height <= windowBottom
    val aboveY = anchorBounds.top.toInt() - marginPx - menuSize.height
    val fitsAbove = aboveY >= statusBarTop
    val y = when {
        fitsBelow -> belowY
        fitsAbove -> aboveY
        // 两侧都放不下：clamp 到底部安全线之上（且不低于状态栏顶）
        else -> (windowBottom - menuSize.height).coerceAtLeast(statusBarTop)
    }

    // 窗口坐标 → popup 内容层本地坐标（减去宿主原点，理论为 0，做修正以保通用）
    val localOffset = IntOffset(
        x - hostOrigin.x.toInt(),
        y - hostOrigin.y.toInt(),
    )
    return MenuPlacement(localOffset, below = fitsBelow || !fitsAbove)
}
