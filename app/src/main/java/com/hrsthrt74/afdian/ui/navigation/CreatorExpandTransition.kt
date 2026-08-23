package com.hrsthrt74.afdian.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

/**
 * 「创作者主页展开动画」的全局起点记录。
 *
 * 点击任意创作者入口时，先把被点控件的窗口坐标矩形写进来，
 * 紧接着 push [Route.CreatorDetail]；自定义过渡 [CreatorExpandTransition]
 * 在每帧绘制时读取它作为「卡片收起后的目标位置」。
 *
 * 每次点击都会先覆盖旧值，所以不需要额外的清理逻辑；
 * 进程恢复后为 null，过渡自动回退到屏幕中心的占位卡片。
 */
object CreatorExpandOrigin {
    /** 起点矩形（窗口根坐标系），null 表示没有可用起点 */
    var rect by mutableStateOf<Rect?>(null)

    /**
     * 「高级过渡动画」总开关。由 MainScreen 从设置订阅后写入这里；
     * 过渡对象在每次变换时读取它动态切换风格，而不是在注册路由时写死，
     * 这样设置页切换开关后无需重新入栈就能立即生效。
     */
    var enabled by mutableStateOf(true)

    /**
     * 页面内边缘滑动返回手势开关。由 MainScreen 从设置订阅后写入；
     * 各路由不再在 entry(swipeDismiss = ...) 上写死方向，而是统一由
     * 过渡对象的 dismissDirection 动态声明，切换立即生效。
     */
    var gestureBack by mutableStateOf(true)

    /**
     * 设备屏幕圆角半径，全屏停稳时的对齐目标。
     * 由 MainScreen 在组合期用 rememberNavSystemCornerRadius() 写入；
     * 未写入时为 0.dp（直角），不影响动画。
     */
    var screenCorner by mutableStateOf(Dp.Unspecified)
}

/**
 * 单个列表项级别的起点捕获器。
 *
 * 用法（在 LazyColumn 等列表项里）：
 * ```
 * val origin = rememberCreatorOrigin()
 * XxxCard(
 *     onClick = origin.onClick { onOpenCreator(user) },
 *     modifier = origin.trackModifier().padding(...)
 * )
 * ```
 * [trackModifier] 持续记录控件最新边界（滚动后自动更新），
 * [onClick] 在原回调执行前把最新边界上报为动画起点。
 */
class CreatorOrigin internal constructor() {
    /** 控件当前边界（窗口根坐标系） */
    private var bounds by mutableStateOf<Rect?>(null)

    /** 挂到组件 modifier 上，持续跟踪自身边界 */
    fun trackModifier(): Modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }

    /**
     * 包装点击回调：先把最新边界写入全局起点，再执行原有逻辑，
     * 保证同一帧内「先记起点、后 push」，过渡首帧就能拿到正确位置。
     */
    fun onClick(block: () -> Unit): () -> Unit = {
        bounds?.let { CreatorExpandOrigin.rect = it }
        block()
    }
}

/** 在组合中记住一个 [CreatorOrigin]（每个列表项各一个即可） */
@Composable
fun rememberCreatorOrigin(): CreatorOrigin = remember { CreatorOrigin() }

/** miuix Card 的默认圆角，收起状态对齐它，展开到全屏时插值到 0 */
private val CollapsedCornerRadius = 16.dp

/** 无真实起点时的占位卡片尺寸（屏幕居中显示） */
private val FallbackCardSize = IntSize(720, 320)/**
 * 创作者主页的「卡片撑满」过渡：
 * 详情页整层从点击位置的卡片矩形放大到全屏，返回时按原路缩回。
 *
 * 基于 miuix-nav 的 navGraphicsTransition 扩展点实现——整个变换发生在
 * 延迟读取的 graphicsLayer 块里（每帧只改绘制属性、零重组），因此
 * 系统预测性返回手势和边缘侧滑会天然地驱动这个缩放跟随手指。
 *
 * 几何原理：以左上角为缩放锚点（TransformOrigin 左上），
 * 全屏层缩放 scaleX/scaleY 后再平移，正好映射到起点矩形；
 * 两个仿射状态共用锚点线性插值，路径严格正确。
 */
val CreatorExpandTransition = navGraphicsTransition(
    // 保留下方一层（列表页）在过渡期间持续渲染
    opaqueDepth = 1f,
) { scope ->
    // p：0 = 详情页全屏停稳；1 = 完全缩成起点卡片。
    // 进入（push）：relativeDepth 从 -1 → 0，|depth| 即 1 → 0，页面放大铺满；
    // 退出（返回/侧滑/预测性返回）：|depth| 从 0 → 1，缩回卡片位置；
    // 被更上层的路由盖住时（Covered，如消息会话页压在本页上）保持全屏不动。
    val progress = if (scope.role == NavRole.Covered) 0f else abs(scope.relativeDepth).coerceIn(0f, 1f)

    val screenWidth = scope.layoutSize.width.toFloat()
    val screenHeight = scope.layoutSize.height.toFloat()

    // 起点：优先用最近一次点击捕获的真实矩形；异常数据兜底为居中占位卡片
    val origin = CreatorExpandOrigin.rect
        ?.takeIf { it.width >= 1f && it.height >= 1f && it.right > 0f && it.bottom > 0f }
        ?: run {
            val fw = with(scope.density) { FallbackCardSize.width.dp.toPx() }.coerceAtMost(screenWidth * 0.8f)
            val fh = with(scope.density) { FallbackCardSize.height.dp.toPx() }.coerceAtMost(screenHeight * 0.4f)
            Rect(
                left = (screenWidth - fw) / 2f,
                top = (screenHeight - fh) / 2f,
                right = (screenWidth + fw) / 2f,
                bottom = (screenHeight + fh) / 2f,
            )
        }

    // 缩放锚定左上角：x' = x * scale + translation，
    // progress=1 时右边缘恰好落在 origin.right、下边缘落在 origin.bottom
    val sx = lerp(1f, (origin.width / screenWidth).coerceIn(0.01f, 1f), progress)
    val sy = lerp(1f, (origin.height / screenHeight).coerceIn(0.01f, 1f), progress)
    scaleX = sx
    scaleY = sy
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = origin.left * progress
    translationY = origin.top * progress

    // 透明度：单向淡入淡出——展开时从全透明渐变到不透明，收回时反向。
    // 用 FastOutSlowInEasing 缓动曲线（快出慢进），避免线性变化的机械感
    val flightT = (1f - progress).coerceIn(0f, 1f)
    alpha = FastOutSlowInEasing.transform(flightT)

    // 圆角补偿：圆角会跟随图层一起被缩放（渲染半径 = 图层半径 × 缩放比），
    // 所以把卡片端的目标 16dp 除以当前缩放比，保证屏幕上看起来始终是 16dp。
    // 非等比缩放下取几何平均作为两轴的折中；全屏端缩放比为 1，直接对齐屏幕圆角
    val collapsedRadiusPx = with(scope.density) { CollapsedCornerRadius.toPx() }
    val screenRadiusPx = with(scope.density) {
        val corner = CreatorExpandOrigin.screenCorner
        // 未写入时按直角屏处理（0 圆角）
        (if (corner == Dp.Unspecified) 0.dp else corner).toPx()
    }
    val effectiveScale = kotlin.math.sqrt(sx * sy).coerceAtLeast(0.01f)
    val radiusPx = lerp(screenRadiusPx, collapsedRadiusPx / effectiveScale, progress)
    shape = RoundedCornerShape(CornerSize(radiusPx))
    clip = true
}

/**
 * 全局默认过渡：视觉上完全等同 [NavTransitions.MiuixDefault]，
 * 仅把滑动关闭方向改为运行时读取 [CreatorExpandOrigin.gestureBack]，
 * 让「手势返回」开关对所有二级页面即时生效。
 */
val AppNavTransition = object : NavTransition by NavTransitions.MiuixDefault {
    override val dismissDirection: NavSwipeDirection
        get() = if (CreatorExpandOrigin.gestureBack) NavSwipeDirection.LeftToRight else NavSwipeDirection.None
}

/**
 * 创作者主页实际挂到路由上的过渡：按 [CreatorExpandOrigin.enabled] 开关在
 * 「卡片展开」与默认滑动之间动态切换。
 *
 * 之所以不在注册路由时用 if 选择过渡：miuix-nav 会把过渡写进 entry 的元数据，
 * 已注册的条目不会随重组更新，设置页切换开关后旧选择会一直生效；
 * 改为运行时委托，所有成员都即时读取当前开关状态，切换立即生效。
 */
val CreatorDetailTransition = object : NavTransition {
    private val active: NavTransition
        get() = if (CreatorExpandOrigin.enabled) CreatorExpandTransition else NavTransitions.MiuixDefault

    override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier =
        with(active) { transformEntry(scope) }

    override fun scrimFraction(scope: NavTransitionScope): Float = active.scrimFraction(scope)

    // 取两者较大值，保证任何风格下列表页都不会被过早裁掉
    override val opaqueDepth: Float
        get() = maxOf(CreatorExpandTransition.opaqueDepth, NavTransitions.MiuixDefault.opaqueDepth)

    override val dismissDirection: NavSwipeDirection
        get() = if (CreatorExpandOrigin.gestureBack) NavSwipeDirection.LeftToRight else NavSwipeDirection.None

    override val motion: NavMotion
        get() = active.motion
}

