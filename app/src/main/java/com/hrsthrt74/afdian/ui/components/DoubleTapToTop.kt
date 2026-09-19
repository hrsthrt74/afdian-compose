package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hrsthrt74.afdian.AppLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 日志 TAG */
private const val DoubleTapToTopLogTag = "DoubleTapToTop"

/**
 * 双击顶栏回顶包装层：包住整条顶栏（`BlurredBar { TopAppBar / SmallTopAppBar }`），
 * 在栏身任意位置双击，内容平滑滚回顶部并展开大标题。
 *
 * 为什么是包装组件而不是裸 Modifier：
 * - miuix `TopAppBar` 的 `title` 是 `String` 而非槽位，标题本身挂不上点击；
 * - 根部还带 `detectTapGestures { 吞点击 }`，直接改 modifier 参数与内部 handler
 *   的链顺序不可控，所以用外层包装统一挂手势；
 * - 回顶需要同时操作 [listState] 和 [scrollBehavior.state]（复位大标题折叠态），
 *   封成组件后页面只传两个状态即可。
 *
 * 手势检测挂在**包装容器（content 的祖先）**上，而不是叠一层透明兄弟盖住全栏：
 * 兄弟叠层里上层 Box 命中后会截断命中测试，顶栏里的返回键/操作按钮收不到任何事件；
 * 祖先 handler 则必在命中路径上——事件到达所有路径节点，按钮照常可点，
 * 我们只要自己不消费，就与 miuix 根部的吞点击 handler 互不干扰。
 * 代价是「快速连点按钮」也会顺带触发回顶（低频场景，视为可接受）。
 *
 * 使用示例：
 * ```kotlin
 * topBar = {
 *     DoubleTapToTop(listState, scrollBehavior) {
 *         BlurredBar(backdrop, enableBlur, progressiveBlur) {
 *             TopAppBar(title = "发现", color = blurBarColor(backdrop), scrollBehavior = scrollBehavior)
 *         }
 *     }
 * }
 * ```
 *
 * @param listState 列表滚动状态，回顶时执行 `animateScrollToItem(0)`
 * @param scrollBehavior 顶栏折叠行为；传 `null` 表示固定标题栏，无需复位折叠态
 * @param enabled 是否启用双击回顶；`false` 时不挂手势
 * @param modifier 外部修饰符
 * @param content 顶栏本体内容
 */
@Composable
fun DoubleTapToTop(
    listState: LazyListState,
    scrollBehavior: ScrollBehavior?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // 进行中的回顶动画协程：连续双击时先取消旧的，避免两段动画互相打架
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    /** 触发一次完整回顶：震动 → 滚动 → 兜底复位折叠态 */
    val scrollToTop: () -> Unit = {
        scrollJob?.cancel()
        scrollJob = scope.launch {
            // 1. 先震动：手势确认的即时反馈，不等动画播完
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            AppLog.d(DoubleTapToTopLogTag, "双击顶栏，滚回顶部")
            // 2. 内容平滑滚回顶部；滚动增量会经 nestedScroll 同步展开大标题
            listState.animateScrollToItem(0)
            // 3. 兜底复位：防止 snap 动画/异常路径把大标题留在半折叠
            //    （与 HomeScreen 首次进入复位是同一组字段）
            scrollBehavior?.let { behavior ->
                behavior.state.heightOffset = 0f
                behavior.state.contentOffset = 0f
            }
        }
    }

    // 双击手势：挂在包装 Box 上（content 的祖先）。
    // 命中栏内任意位置时，这条链上的所有 handler（含按钮、miuix 根部吞点击）都在命中路径里，
    // 事件对全员可见——这里只监听不消费，按钮不受任何影响。
    val gestureModifier = if (enabled) {
        // listState / scrollBehavior 变化时重建手势循环，拿到新的回顶目标
        Modifier.pointerInput(listState, scrollBehavior) {
            // 上一次「完整点按」的抬起时刻；0 表示没有待配对的前一次点按
            var lastTapUpTime = 0L

            // 永久循环：每次配对一对「按下 → 抬起」，跨手势用 lastTapUpTime 衔接
            while (true) {
                awaitEachGesture {
                    // 等第一次按下：requireUnconsumed=false，被别人消费过也照常收到
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = down.uptimeMillis

                    // 跟踪到抬起：直接轮询原始事件、忽略消费标记。
                    // （不能用 waitForUpOrCancellation：miuix 根部消费后它会返回 null 中止）
                    var up: PointerInputChange? = null
                    while (up == null) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        // 指针从事件流里消失（等效抬起但拿不到收尾状态），按未完成处理
                        if (change == null) break
                        if (!change.pressed) {
                            up = change
                        } else if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            // 按住期间漂移超过 touchSlop：这是一次拖拽而非点按，配对作废
                            break
                        }
                    }

                    if (up == null) {
                        // 点按被打断/拖拽：清掉配对计数，等下一次干净的点按
                        lastTapUpTime = 0L
                        return@awaitEachGesture
                    }

                    // 双击判定：两次点按间隔不超过系统双击窗口。
                    // 只看时间不看点位——整条栏视为同一个双击目标，抬手快点任意处都能触发
                    val isDoubleTap = lastTapUpTime != 0L &&
                        downTime - lastTapUpTime <= viewConfiguration.doubleTapTimeoutMillis

                    if (isDoubleTap) {
                        lastTapUpTime = 0L // 触发后清零，避免连续三击触发两次
                        // 不消费事件：同路径上的按钮照常工作
                        scrollToTop()
                    } else {
                        // 记录本次抬起，等可能的第二次点按
                        lastTapUpTime = up.uptimeMillis
                    }
                }
            }
        }
    } else {
        Modifier
    }

    // 包装容器：尺寸完全由 content 决定，手势只挂在容器上、不加任何可见层
    Box(modifier = modifier.then(gestureModifier)) {
        // 顶栏本体（调用方传入的 BlurredBar + TopAppBar 等）
        content()
    }
}
