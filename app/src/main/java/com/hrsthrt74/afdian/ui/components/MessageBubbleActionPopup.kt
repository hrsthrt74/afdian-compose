package com.hrsthrt74.afdian.ui.components

// 气泡操作菜单弹层：点击消息气泡后弹出 ListPopup 风格的操作菜单。
//
// 菜单分两区：顶部 inline 快捷行（图标上、文字下，每行至多 2 个，如「复制全文」「转发」），
// 与下方普通列表区（DropdownImpl），两区之间用分割线隔开。
//
// 压暗层为「挖洞遮罩」：全屏 scrim 用 Path(EvenOdd) 在被选气泡位置挖一个
// 与气泡同形（squircle）的透明洞，原气泡直接透出，实现「背景压暗（含顶栏）
// 但选中气泡豁免」的观感。
//
// 菜单与遮罩经 MiuixPopupUtils.PopupLayout 渲染在根 Scaffold 的
// MiuixPopupHost（全窗口最顶层），因此能覆盖顶栏与输入栏。
// 不用库自带的 OverlayListPopup / WindowListPopup 封装：它们的 dim 画在
// 库内部，无法在气泡处挖洞，故此处直接用公开的 PopupLayout 自组装。
// 注意：PopupLayout 的 content 里 LocalDismissState 为 null（那是 Window
// 系封装专供），菜单项回调里直接置 visible.value = false 关闭即可。

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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.ui.navigation.NavSwipeGate
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupContent
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupLayoutPosition
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.squircle.isSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

private const val TAG = "MessageBubblePopup"

/** inline 行内首/末动作的水平内缩边距（仅多动作行生效；单动作行保持居中不加） */
private val InlineEdgePadding = 8.dp

/**
 * 气泡操作菜单的动作项。
 *
 * @param label 菜单展示文案
 * @param icon inline 操作的图标；普通列表项为 null
 * @param isInline 是否为 inline 操作：渲染在菜单顶部「图标上、文字下」的快捷行
 *   （每行至多 2 个，与普通列表项之间用分割线隔开）
 */
enum class BubbleAction(
    val label: String,
    val icon: ImageVector? = null,
    val isInline: Boolean = false,
) {
    /**
     * 复制全文：复制消息完整文本，仅文本类消息显示。
     * 特殊卡片消息的「复制部分」类 inline 操作见 [CopyOrderNo]（订单卡片的「复制单号」）。
     */
    CopyFullText("复制全文", icon = MiuixIcons.Copy, isInline = true),

    /**
     * 复制单号：复制发电订单卡片的订单号（out_trade_no），仅订单卡片显示。
     * 与「复制全文」共用 Copy 图标——两者按消息类型互斥，不会同时出现在菜单里。
     */
    CopyOrderNo("复制单号", icon = MiuixIcons.Copy, isInline = true),

    /** 查看主页：跳转所选气泡发出者（会话对方）的主页，仅对方气泡显示 */
    ViewHomePage("查看主页"),

    /** 转发：UI 占位，点击暂不执行真实功能 */
    Forward("转发", icon = MiuixIcons.Forward, isInline = true),
}

/**
 * 气泡操作菜单弹层。
 *
 * @param visible PopupLayout 的显示开关（Screen 层持有，PopupLayout 要求 MutableState）
 * @param bubbleBounds 被选气泡的窗口坐标快照（菜单打开瞬间冻结；null 时不画遮罩与菜单）
 * @param alignEnd 气泡是否右缘对齐（isSentByMe）——菜单 x 方向贴气泡对应边缘
 * @param actions 菜单项集合（顺序即展示顺序）：[BubbleAction.isInline] 的项渲染为
 *   顶部「图标上、文字下」快捷行，其余渲染为下方下拉列表，两区之间用分割线隔开
 * @param sendTimeText 底部时间行：所选消息的具体发送时间（已格式化）；null 不显示
 * @param onAction 菜单项点击回调（关闭动作已由本组件负责，回调只管执行动作）
 */
@Composable
fun MessageBubbleActionPopup(
    visible: MutableState<Boolean>,
    bubbleBounds: Rect?,
    alignEnd: Boolean,
    actions: List<BubbleAction>,
    sendTimeText: String?,
    onAction: (BubbleAction) -> Unit,
) {
    // 菜单实测尺寸：onSizeChanged 回填；未测得前隐藏菜单防首帧闪跳
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    // popup 内容层在窗口中的原点：Scaffold 理论上与页面同原点 (0,0)，
    // 但这里仍做坐标差修正，不依赖「恰好对齐」的假设（兼容 insets/转场等偏移）
    var hostOriginInWindow by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val haptic = LocalHapticFeedback.current

    // 系统栏 insets 对象在组合期获取（statusBars/navigationBars 是 @Composable 属性），
    // 之后在派生状态里只调用其普通方法 getTop/getBottom
    val statusBarInsets = WindowInsets.statusBars
    val navBarInsets = WindowInsets.navigationBars

    // 菜单摆放（偏移 + 是否在气泡下方）：由气泡快照/菜单实测尺寸/宿主原点推导。
    // 用 derivedStateOf 统一读取，避免在布局（offset）阶段写状态引发重组循环。
    val placement by remember(bubbleBounds, alignEnd) {
        derivedStateOf {
            calculateMenuOffset(
                bubbleBounds = bubbleBounds,
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
        // 打开期间临时禁用「任意位置滑动返回」：页面被滑动平移会让挖洞遮罩
        // 与气泡错位。经 NavSwipeGate → MainScreen 替换 NavDisplay 的过渡实例，
        // 滑动手势层实时重新解析为禁用（见 NavSwipeGate.kt 的机制说明）
        NavSwipeGate.blockedByPopup = visible.value
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

    // 按属性把菜单项拆成两区：inline 快捷行（图标上、文字下）与普通下拉列表
    val inlineActions = actions.filter { it.isInline }
    val listActions = actions.filterNot { it.isInline }

    // 兜底复位：弹层随页面销毁（如导航直接离开）而闸门仍开着时，强制恢复滑动返回
    DisposableEffect(Unit) {
        onDispose { NavSwipeGate.blockedByPopup = false }
    }

    MiuixPopupUtils.PopupLayout(
        visible = visible,
        // 不用库的整窗压暗（无法挖洞），由 content 内的挖洞遮罩代替
        enableWindowDim = false,
        // 返回键关闭由 PopupEntry 内部的 NavigationBackHandler 处理（置 visible=false）
        enableBackHandler = true,
        // 退出动画比库默认（150ms）更短：关闭后弹层仍留在组合中并拦截触摸，
        // 残留窗口越长，「视觉上已关闭、实际还没卸载」时点击气泡被无声吃掉
        // （tap 落进弹层 → onTap 里 visible 已是 false，no-op）的概率越高，
        // 表现为「关闭后再点第一下没反应」。快速卸载消除这段空窗。
        exitTransition = fadeOut(animationSpec = tween(durationMillis = 90)),
    ) {
        // 全屏容器：点击任意空白处关闭菜单；同时记录原点供坐标修正
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { hostOriginInWindow = it.positionInWindow() }
                .pointerInput(Unit) {
                    detectTapGestures {
                        AppLog.d(TAG, "点击空白处，关闭气泡菜单")
                        visible.value = false
                    }
                },
        ) {
            // 挖洞压暗层：全屏变暗、气泡位置透出（淡入淡出节奏对齐库内 dim 动画）
            if (bubbleBounds != null) {
                HolePunchDimScrim(
                    bubbleBoundsInWindow = bubbleBounds,
                    dimAlpha = { dimProgress.value },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 菜单本体：定位容器只负责把内容摆到气泡侧（下方优先 → 上方），
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
                    // 展开方向：从气泡侧长出（下方弹出从顶边展开，反之从底边）
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
                    // 子项强制测成同一宽度，因此 inline 行用 Row + weight(1f) 均分宽度
                    ListPopupColumn {
                        // inline 快捷行：图标上、文字下，每行至多 2 个（超出自动换到下一行）。
                        // 行的纵向边距不放在 Row 上，而是传入 InlineActionCell 内部
                        // （clickable 之后应用），让按压高亮覆盖含边距的整行高度
                        if (inlineActions.isNotEmpty()) {
                            val lastRowIndex = (inlineActions.size - 1) / 2
                            inlineActions.chunked(2).forEachIndexed { rowIndex, rowActions ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    rowActions.forEachIndexed { colIndex, action ->
                                        InlineActionCell(
                                            action = action,
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
                                            onSelected = {
                                                // 与列表项一致：先轻震再执行动作
                                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                                visible.value = false
                                                onAction(action)
                                            },
                                        )
                                    }
                                }
                            }
                            // inline 区与普通列表区之间的分割线
                            if (listActions.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }

                        // 普通列表项
                        listActions.forEachIndexed { index, action ->
                            DropdownImpl(
                                // 用 DropdownItem 重载：isFirst/isLast 只在该重载上提供
                                item = DropdownItem(text = action.label),
                                optionSize = listActions.size,
                                isSelected = false,
                                index = index,
                                // 上方有 inline 区时，本行不是「首行」（首行才有 20dp 大上边距）
                                isFirst = inlineActions.isEmpty() && index == 0,
                                // 下方有时间行时，本行不是「末行」（末行才有 20dp 大下边距，
                                // 否则与时间行之间会隔出过大的空隙）
                                isLast = index == listActions.lastIndex && sendTimeText == null,
                                onSelectedIndexChange = {
                                    // 与项目其他菜单一致：先轻震再执行动作
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    visible.value = false
                                    onAction(action)
                                },
                            )
                        }

                        // 底部时间行：所选消息的具体发送时间
                        if (sendTimeText != null) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            Text(
                                text = sendTimeText,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
//                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(20.dp, 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * inline 快捷操作单元：图标上、文字下，在行内均分宽度（weight(1f)）。
 * 观感对齐 [DropdownImpl] 列表项：同字号、同字重、同文字色（onSurfaceContainer），
 * 点击整格有涟漪反馈。仅用于 [BubbleAction.isInline] 为 true 且带图标的动作。
 *
 * 边距均在 clickable 之后应用（属于动作本体），按压高亮覆盖含边距的整格范围。
 *
 * @param topPadding 上方额外边距
 * @param bottomPadding 下方额外边距
 * @param startPadding 左侧额外边距——行内首个动作用；单动作行传 0 保持居中
 * @param endPadding 右侧额外边距——行内末个动作用；单动作行传 0 保持居中
 */
@Composable
private fun RowScope.InlineActionCell(
    action: BubbleAction,
    topPadding: Dp,
    bottomPadding: Dp,
    startPadding: Dp = 0.dp,
    endPadding: Dp = 0.dp,
    onSelected: () -> Unit,
) {
    // inline 项必有图标（枚举定义保证）；缺失时直接不渲染，避免出现空格子
    val icon = action.icon ?: return

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onSelected)
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
            tint = MiuixTheme.colorScheme.onSurfaceContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = action.label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
        )
    }
}

/**
 * 全屏挖洞压暗层：整层填充 windowDimming 色，但用 Path(EvenOdd) 在
 * [bubbleBoundsInWindow] 处挖一个与气泡同形（squircle 圆角）的透明洞，
 * 原气泡从洞中直接透出，视觉上「未被压暗」。
 *
 * @param dimAlpha 压暗层透明度提供者（0-1，随开关动画变化；洞内不受影响）
 */
@Composable
private fun HolePunchDimScrim(
    bubbleBoundsInWindow: Rect,
    dimAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    // 与 miuix Card 默认圆角（CardDefaults.CornerRadius = 16dp）保持一致，洞角与卡角无缝
    val holeRadius: Dp = 16.dp
    val scrimColor = MiuixTheme.colorScheme.windowDimming
    // 是否启用 squircle 形状（与卡片同源开关；关闭时 addSquircleRect 自动退化为圆角矩形）
    val squircleEnabled = isSquircleEnabled()
    // 本层在窗口中的原点：气泡是窗口坐标，绘制需要本地坐标，做差值修正
    var originInWindow by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            // 压暗淡入淡出：整层 alpha 跟随 dimProgress，气泡洞同样从透明渐入
            .graphicsLayer { alpha = dimAlpha() }
            .onGloballyPositioned { originInWindow = it.positionInWindow() }
            .drawWithCache {
                // 气泡矩形换算到本层本地坐标
                val holeLeft = bubbleBoundsInWindow.left - originInWindow.x
                val holeTop = bubbleBoundsInWindow.top - originInWindow.y
                val holeWidth = bubbleBoundsInWindow.width
                val holeHeight = bubbleBoundsInWindow.height
                val radiusPx = holeRadius.toPx()

                // 洞路径：squircle 从 (0,0) 起画，用 addPath 附带偏移并入遮罩路径
                val holePath = Path().apply {
                    addSquircleRect(
                        width = holeWidth,
                        height = holeHeight,
                        cornerRadius = radiusPx,
                        squircleEnabled = squircleEnabled,
                    )
                }
                // 遮罩路径：整层矩形 + 气泡洞（EvenOdd：两形相交处即洞内为空）
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

/** 菜单摆放结果：最终偏移（宿主本地坐标）+ 是否位于气泡下方（用于展开动画方向） */
private data class MenuPlacement(val offset: IntOffset, val below: Boolean)

/**
 * 计算菜单摆放位置（窗口坐标 → 宿主本地坐标）：
 * - y：气泡下方 8dp 优先；下方放不下（触底）→ 气泡上方 8dp；两侧都放不下 → clamp 贴底
 * - x：右对齐气泡（alignEnd）或左对齐气泡；再 coerce 到窗口内（左右各留 12dp）
 * - 状态栏/导航栏安全边界由 [statusBarTop]/[navBarBottom]（px）传入，避免菜单
 *   顶进状态栏或压到手势条
 */
private fun calculateMenuOffset(
    bubbleBounds: Rect?,
    menuSize: IntSize,
    windowSize: IntSize,
    alignEnd: Boolean,
    density: Density,
    statusBarTop: Int,
    navBarBottom: Int,
    hostOrigin: Offset,
): MenuPlacement {
    // 气泡/菜单尺寸未就绪时先放 (0,0)（此时菜单 alpha = 0，不会闪现）
    if (bubbleBounds == null || menuSize == IntSize.Zero || windowSize == IntSize.Zero) {
        return MenuPlacement(IntOffset.Zero, below = true)
    }
    val marginPx = with(density) { 8.dp.roundToPx() }    // 菜单与气泡的垂直间距
    val hEdgePx = with(density) { 12.dp.roundToPx() }    // 菜单距屏幕左右的水平边距
    val windowBottom = windowSize.height - navBarBottom

    // x：贴气泡对应边缘（我方气泡右缘对齐、对方气泡左缘对齐），再夹进窗口水平范围
    val rawX = if (alignEnd) {
        bubbleBounds.right.toInt() - menuSize.width
    } else {
        bubbleBounds.left.toInt()
    }
    val x = rawX.coerceIn(hEdgePx, (windowSize.width - menuSize.width - hEdgePx).coerceAtLeast(hEdgePx))

    // y：下方优先 → 上方 → clamp 贴底
    val belowY = bubbleBounds.bottom.toInt() + marginPx
    val fitsBelow = belowY + menuSize.height <= windowBottom
    val aboveY = bubbleBounds.top.toInt() - marginPx - menuSize.height
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
