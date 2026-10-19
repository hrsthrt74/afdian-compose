package com.hrsthrt74.afdian.ui.components.image

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hrsthrt74.afdian.ui.components.post.normalizeImageUrl
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一次图片查看请求。
 *
 * @param urls 当前这组图片（正文里的单张图就是 size=1 的列表）
 * @param initialIndex 从哪一张开始看
 * @param origin 被点击缩略图在根布局中的位置。放大/收起动画从这里长到全屏，
 *   也是「下滑关闭」失败时的回归位置。坐标必须与查看器处在同一个根布局坐标系
 *   （缩略图通过 `boundsInRoot()` 采集）。
 */
data class ImageViewerRequest(
    val urls: List<String>,
    val initialIndex: Int,
    val origin: Rect,
    /**
     * 缩略图的 Coil 内存缓存 key。
     *
     * 查看器用全屏尺寸重新请求原图，和缩略图的缓存 key 不同，必然要重新解码；
     * 把这个 key 作为 `placeholderMemoryCacheKey`，打开瞬间就能先画出低清缩略图，
     * 再由 Coil crossfade 到原图，避免「白/黑一下」的断裂感。
     */
    val placeholderCacheKey: MemoryCache.Key? = null,
)

/**
 * 图片查看器的全局开关。
 *
 * 与 `LocalAppLinkNavigator` 同构：由 MainScreen 在顶层提供一份，
 * 缩略图只需要 `LocalImageViewer.current.open(...)`，不用把回调一层层往下传。
 */
@Stable
class ImageViewerController {
    var request by mutableStateOf<ImageViewerRequest?>(null)
        private set

    fun open(
        urls: List<String>,
        initialIndex: Int,
        origin: Rect,
        placeholderCacheKey: MemoryCache.Key? = null,
    ) {
        val cleaned = urls.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return
        request = ImageViewerRequest(
            urls = cleaned,
            initialIndex = initialIndex.coerceIn(0, cleaned.lastIndex),
            origin = origin,
            placeholderCacheKey = placeholderCacheKey,
        )
    }

    fun close() {
        request = null
    }
}

/**
 * 默认给一个空控制器而不是报错：Compose Preview / 单独预览卡片时没有 Provider，
 * 报错会让预览直接崩掉；空控制器在预览里只是「点了没反应」，不影响真实页面。
 */
val LocalImageViewer = staticCompositionLocalOf { ImageViewerController() }

/**
 * 展开 / 收起都用弹簧：收尾时越过目标一点点再回来，比缓动曲线更有「实体感」。
 *
 * 刚度决定快慢：`StiffnessMedium`(1500) 观感太急，这里降到 280 左右，
 * 整个展开约 0.6 秒，位移在前半程铺开，收尾是慢慢靠过去而不是「啪」一下停住。
 * 阻尼比控制过冲幅度（0.82 约 1%，只在收尾留一个几乎察觉不到的回弹）：
 * `visibilityThreshold` 收得比默认小，否则动画可能停在 0.99 之类的位置，
 * 导致手势门槛和圆角判断落在边界上。
 */
private val OPEN_SPRING = spring(
    dampingRatio = 0.82f,
    stiffness = 280f,
    visibilityThreshold = 0.001f,
)
private val CLOSE_SPRING = spring(
    dampingRatio = 0.84f,
    stiffness = 340f,
    visibilityThreshold = 0.001f,
)

/** 下滑甩出屏幕用的加速曲线：拖拽之后要果断离开，用弹簧反而拖沓 */
private val CLOSE_EASING = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * 关闭收尾淡出：等图片缩到「接近缩略图」时再启动一段固定时长的淡出。
 *
 * 为什么不用 progress 直接映射透明度：spring 末段是渐近逼近目标的，
 * 最后 18% 的进度可能要走 300ms，映射出来会变成「停在缩略图附近慢慢变透明」；
 * 用独立的时间动画，淡出始终是干脆的 140ms。
 */
private const val FADE_OUT_START = 0.22f
private const val FADE_OUT_MS = 140

/**
 * 图片查看器宿主：铺满全屏，盖在 NavDisplay 之上。
 *
 * 结构分三层：
 * 1. 黑色 scrim（同时负责吞掉下层页面的手势，防止查看器打开时还能滑列表）；
 * 2. 图片容器：尺寸/位置从缩略图 bounds 动画到全屏，内部是 HorizontalPager；
 * 3. 顶部栏：关闭按钮 + 页码。
 *
 * 手势：
 * - 双指/双击缩放、拖动由 Telephoto 处理；
 * - 未放大时向下拖 → 图片跟手下移、背景变淡，松手超过阈值就下滑关闭，否则弹回；
 * - 放大状态下不接管纵向手势，优先保证图片自身平移。
 */
@Composable
fun ImageViewerHost(controller: ImageViewerController) {
    val request = controller.request ?: return

    val scope = rememberCoroutineScope()
    // 0 = 完全贴合缩略图；1 = 全屏
    val progress = remember { Animatable(0f) }
    // 关闭收尾的图片透明度：1 = 不透明。独立于 progress，保证淡出时长可控
    val exitAlpha = remember { Animatable(1f) }
    // 下滑跟手位移（px）：Y 只允许向下，X 跟随手指自由移动
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    // 一旦判定为「下滑」，立刻锁住 Pager：后续再横向移动也只跟手，不再触发翻页
    var dragging by remember { mutableStateOf(false) }
    // 当前页是否处于放大状态：放大时锁住 Pager 与下滑关闭
    var currentZoom by remember { mutableFloatStateOf(0f) }
    // 防止关闭动画期间重复触发
    var closing by remember { mutableStateOf(false) }

    // 系统栏保持 App 本来的样子（不隐藏、也不改图标颜色），
    // 查看器的黑色背景只铺在安全区内，避免状态栏/导航栏被压成黑条
    // （图标颜色与栏底色由页面自身决定，edge-to-edge 行为不变）

    // 返回键：先收回缩略图再退出，避免直接闪没
    fun closeToOrigin() {
        if (closing) return
        closing = true
        scope.launch {
            // 若用户已经下滑了一段，先把位移弹回，再沿 bounds 收回
            if (dragOffsetX != 0f || dragOffsetY != 0f) {
                launch {
                    animate(dragOffsetX, 0f, animationSpec = tween(120)) { value, _ -> dragOffsetX = value }
                }
                animate(dragOffsetY, 0f, animationSpec = tween(120)) { value, _ -> dragOffsetY = value }
            }
            // 收尾淡出与收回动画并行：等缩到接近缩略图时再启动，避免中途变半透明
            val fadeJob = launch {
                snapshotFlow { progress.value }.first { it < FADE_OUT_START }
                exitAlpha.animateTo(0f, tween(FADE_OUT_MS))
            }
            progress.animateTo(0f, CLOSE_SPRING)
            // 等淡出走完再关 Dialog：极端情况下 progress 先到 0，
            // 直接 close 会让图在半透明状态被移除，看起来像闪没
            fadeJob.join()
            controller.close()
        }
    }

    BackHandler { closeToOrigin() }

    LaunchedEffect(request) {
        progress.snapTo(0f)
        exitAlpha.snapTo(1f)
        progress.animateTo(1f, OPEN_SPRING)
    }

    // Pager 状态提到容器外面：顶部页码和容器内的翻页共用同一份
    val pagerState = rememberPagerState(initialPage = request.initialIndex) { request.urls.size }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullWidth = constraints.maxWidth.toFloat()
        val fullHeight = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        // 目标矩形就是整屏：图放大/拖动时可以一直延伸到屏幕四边。
        // 注意不能用「固定内缩的安全区容器」来做系统栏留白——那样缩放后的平移
        // 也会被容器边界裁住，图片永远到不了边框（padding 必须跟着图片移动，
        // 而不是当成一个静态遮罩）。
        val target = Rect(0f, 0f, fullWidth, fullHeight)
        // spring 的过冲不做进几何插值：容器尺寸一旦越过父约束会被 Compose 裁回全屏，
        // 观感变成「平移几像素再回来」而不是弹性放大。这里把进度截到 [0,1]，
        // 越界部分单独转成一个很小的缩放系数（见 elasticScale）
        val geometry = progress.value.coerceIn(0f, 1f)
        val frame = lerpRect(request.origin, target, geometry)
        // 收起过程中背景同步淡出；下滑时也按位移比例变淡。
        // 视觉进度按屏高的 35% 归一化：若按整屏高度算，拖 120dp 才变化 1%，
        // 缩小和淡出几乎看不出来
        val dragProgress = (dragOffsetY / (fullHeight * 0.35f)).coerceIn(0f, 1f)
        // 背景不必压到全黑：留一点页面透出来，观感更轻，也不像「切到另一个 App」
        val scrimAlpha = 0.95f * geometry * (1f - dragProgress)

        // scrim：铺满整屏（含状态栏 / 导航栏），压暗一直延伸到屏幕四边；
        // 同时负责吞掉下层列表的点击/滑动。
        // 图片的「上下留白」由容器的目标矩形负责（见 target），scrim 本身不内缩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                }
        )

        // 图片容器：位置和尺寸逐帧插值，从缩略图格子里「长」到全屏
        val dismissThreshold = with(density) { 120.dp.toPx() }

        // 松手后的归宿：过阈值就滑出屏幕关闭，否则带一点回弹地归位。
        // X / Y 同时归零，斜着拖出去也会沿原方向收回来
        fun settleDrag() {
            if (dragOffsetY > dismissThreshold) {
                if (closing) return
                closing = true
                scope.launch {
                    animate(
                        initialValue = dragOffsetY,
                        targetValue = fullHeight + 240f,
                        animationSpec = tween(220, easing = CLOSE_EASING),
                    ) { value, _ -> dragOffsetY = value }
                    controller.close()
                }
            } else {
                scope.launch {
                    launch {
                        animate(
                            initialValue = dragOffsetX,
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                        ) { value, _ -> dragOffsetX = value }
                    }
                    animate(
                        initialValue = dragOffsetY,
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                    ) { value, _ -> dragOffsetY = value }
                }
            }
        }

        val containerModifier = Modifier
            .offset {
                IntOffset(
                    (frame.left + dragOffsetX).roundToInt(),
                    (frame.top + dragOffsetY).roundToInt(),
                )
            }
            .size(
                width = with(density) { frame.width.toDp() },
                height = with(density) { frame.height.toDp() },
            )
            .graphicsLayer {
                // 下滑时明显缩小，但几乎不压暗图片（只降 6%），
                // 亮度交给背景 scrim 去变化；
                // 再叠上弹簧过冲：打开时略大于全屏、关闭时略小于缩略图，各 1% 左右
                val elasticScale = 1f + (progress.value - geometry) * 1.2f
                // 满宽单图的缩略图与全屏同宽，收回时图片内容的尺寸几乎不变，
                // 只有位置在移动，看起来就是「平移」。这里叠一个随进度递增的轻微缩小
                // （最多 10%），让收尾是「缩回去」而不是「被推回去」；
                // 最后一段本来就在淡出，不会露出缩过头后的边缘
                val closeShrink = if (closing) (1f - geometry) * 0.10f else 0f
                val shrink = (1f - dragProgress * 0.32f) * elasticScale * (1f - closeShrink)
                scaleX = shrink
                scaleY = shrink
                // 关闭收尾淡出由 exitAlpha 独立驱动（见 closeToOrigin），
                // 与下滑时的轻微压暗相乘
                alpha = (1f - dragProgress * 0.06f) * exitAlpha.value
            }
            .clip(
                // 圆角从缩略图的 8dp 平滑过渡到全屏的 0
                RoundedCornerShape((8f * (1f - geometry)).dp)
            )
            // 手势必须挂在 Pager 的父节点上：Compose 的命中测试只把事件交给最上层节点，
            // 之前用一个全屏兄弟层接「下滑关闭」，会把 Pager 和 Telephoto 的手势整个挡住。
            // 挂在父节点后，Pager / 图片先处理，没被消费的手势才会落到这里。
            .pointerInput(progress.value >= 0.99f, currentZoom, fullHeight) {
                // 展开动画期间、或图片已放大：不接管手势
                if (progress.value < 0.99f || currentZoom >= 0.01f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var vertical = false
                    var horizontal = false
                    var completed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            // 多指（缩放）不参与下滑关闭，交给 Telephoto
                            if (event.changes.size > 1) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            // 子节点已经消费（Pager 在横滑 / 图片在缩放平移）：本次手势让给它
                            if (!vertical && change.isConsumed) {
                                horizontal = true
                                break
                            }
                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            if (!vertical && !horizontal) {
                                totalDx += dx
                                totalDy += dy
                                val slop = viewConfiguration.touchSlop
                                if (abs(totalDy) > slop && abs(totalDy) > abs(totalDx)) {
                                    // 判定为下滑：立刻锁 Pager，之后横向移动只跟手、不翻页
                                    vertical = true
                                    dragging = true
                                } else if (abs(totalDx) > slop) {
                                    // 横向手势让给 Pager，本次手势不再参与
                                    horizontal = true
                                    break
                                }
                            }
                            if (vertical) {
                                change.consume()
                                // X 自由跟随（斜着拖也跟手）；Y 只允许向下
                                dragOffsetX += dx
                                // 向下拖越远阻力越大（橡皮筋）：用平方曲线，前半段几乎 1:1，
                                // 过了 1/3 屏后迅速变沉，最后只剩两成位移。
                                // 往回拉不给阻力，方便随时取消
                                val t = (dragOffsetY / (fullHeight * 0.35f)).coerceIn(0f, 1f)
                                val resistance = (1f - t * t * 0.8f).coerceIn(0.2f, 1f)
                                val dampedDy = if (dy > 0f) dy * resistance else dy
                                dragOffsetY = (dragOffsetY + dampedDy).coerceAtLeast(0f)
                            }
                        }
                        completed = true
                    } finally {
                        // 正常结束或被系统取消都要解锁 Pager；
                        // 只有正常结束才做归位/关闭，避免页面销毁时还去起动画
                        dragging = false
                        if (vertical && completed) settleDrag()
                    }
                }
            }

        Box(modifier = containerModifier) {
            HorizontalPager(
                state = pagerState,
                // 只有完全展开、未放大、且当前不是下滑手势时，才允许翻页
                userScrollEnabled = progress.value > 0.99f && currentZoom < 0.01f && !dragging,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomablePage(
                    url = request.urls[page],
                    isCurrent = pagerState.currentPage == page,
                    // 只有被点开的那一张有缩略图缓存；左右翻出来的页没有占位图
                    placeholderKey = if (page == request.initialIndex) request.placeholderCacheKey else null,
                    requestWidth = fullWidth.roundToInt(),
                    requestHeight = fullHeight.roundToInt(),
                    onZoomFractionChange = { fraction -> currentZoom = fraction },
                )
            }
        }

        // 顶部栏：关闭 + 页码。放在容器外，动画期间不跟着缩放；
        // 打开时随进度淡入，下滑时随位移淡出，避免黑底没了文字还挂着
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = geometry * (1f - dragProgress) }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(
                onClick = { closeToOrigin() },
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }
            if (request.urls.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${request.urls.size}",
                    color = Color.White,
                    // 页码压在图片上，用 body1 保证在浅色/复杂背景上也能看清
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * 单页缩放图片。
 *
 * `rememberZoomableState()` 必须放在页面内部：每一页各自维护缩放，
 * 切页回来后不会带着上一页的缩放比例。
 *
 * @param isCurrent 当前页才把缩放比例上报给外层，用于锁 Pager / 下滑关闭
 */
@Composable
private fun ZoomablePage(
    url: String,
    isCurrent: Boolean,
    placeholderKey: MemoryCache.Key?,
    requestWidth: Int,
    requestHeight: Int,
    onZoomFractionChange: (Float) -> Unit,
) {
    val zoomableState = rememberZoomableState()
    val zoomFraction = zoomableState.zoomFraction ?: 0f
    val context = LocalContext.current

    // 请求尺寸固定为整屏：容器在展开动画里每帧都在变大小，
    // 交给布局自动推导的话 Coil 会跟着尺寸反复取消/重启请求，
    // 这正是「打开时闪一下、关闭时反而顺滑」的原因（关闭时图已经解码好了）。
    val imageRequest = remember(url, placeholderKey, requestWidth, requestHeight) {
        ImageRequest.Builder(context)
            .data(normalizeImageUrl(url))
            .size(requestWidth, requestHeight)
            // 先用缩略图的内存缓存顶上，再由 crossfade 过渡到原图
            .placeholderMemoryCacheKey(placeholderKey)
            .crossfade(180)
            .build()
    }

    // 只让当前页驱动外层状态；非当前页的缩放变化不影响翻页/关闭手势
    LaunchedEffect(zoomFraction, isCurrent) {
        if (isCurrent) onZoomFractionChange(zoomFraction)
    }

    ZoomableAsyncImage(
        model = imageRequest,
        contentDescription = null,
        state = rememberZoomableImageState(zoomableState),
        // 默认按宽度铺满：长图（竖构图）会撑满屏宽、上下可拖动查看，
        // 而不是用 Fit 缩到整张塞进屏幕、两侧留黑边
        contentScale = ContentScale.FillWidth,
        // 不铺黑色底：底色交给外层 scrim。否则展开动画的第一帧会在缩略图上
        // 盖一块黑色矩形（此时图片可能还没解码出来），看起来像闪了一下
        modifier = Modifier.fillMaxSize(),
    )
}

/** Rect 逐字段线性插值；Compose 自带的 Rect lerp 不够稳定，这里自己算。 */
private fun lerpRect(start: Rect, stop: Rect, fraction: Float): Rect = Rect(
    left = start.left + (stop.left - start.left) * fraction,
    top = start.top + (stop.top - start.top) * fraction,
    right = start.right + (stop.right - start.right) * fraction,
    bottom = start.bottom + (stop.bottom - start.bottom) * fraction,
)
