package com.hrsthrt74.afdian.ui.navigation

// 滑动返回手势的运行时闸门。
//
// miuix-nav 的 NavDisplay 把「任意位置滑动返回」手势写死在显示容器上
// （navSwipeDismissImpl 的 enabled 只看「非根 entry」），没有运行时开关；
// 且 NavDisplay 内部对滑动方向的记忆（topDismissDirection）以 transition
// 实例为缓存键，gestureBack 这类内部状态变化不会触发重算——这就是设置里
// 「滑动返回」开关对已打开页面不实时生效的原因。
//
// 破局点：NavDisplay 的 transition 参数是项目可控的，且参与缓存键——
// MainScreen 按需把过渡替换为「无滑动」实例（[AppNavTransitionWithoutSwipe]，
// 其余成员全部委托默认过渡），缓存随之失效，滑动方向实时重算为 None，
// 手势层直接不再注册。两类场景由此实时生效：
// 1. 气泡操作菜单等浮层打开期间（[NavSwipeGate.blockedByPopup]）临时禁滑，
//    避免页面平移导致浮层的挖洞遮罩与气泡错位；
// 2. 设置里「滑动返回」开关切换后，当前已打开的页面立即生效。

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition

/** 滑动返回手势的全局闸门状态（跨页面共享，由浮层负责置位/复位） */
object NavSwipeGate {
    /**
     * 气泡操作菜单等浮层打开时置 true：MainScreen 据此把 NavDisplay 的过渡
     * 替换为 [AppNavTransitionWithoutSwipe]，临时禁止滑动返回。
     * 浮层关闭时复位 false（浮层组件内部负责维护，含离场兜底）。
     */
    var blockedByPopup by mutableStateOf(false)
}

/**
 * 视觉与其他行为完全委托 [AppNavTransition]、仅把滑动关闭方向恒置为 None 的
 * 过渡实例。NavDisplay 以 transition 实例为缓存键解析滑动方向，因此
 * 「换实例 = 立即重算」，无需重新进入页面。
 */
val AppNavTransitionWithoutSwipe = object : NavTransition by AppNavTransition {
    override val dismissDirection: NavSwipeDirection
        get() = NavSwipeDirection.None
}
