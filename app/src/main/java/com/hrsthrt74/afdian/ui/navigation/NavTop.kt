package com.hrsthrt74.afdian.ui.navigation

import androidx.compose.runtime.compositionLocalOf

/**
 * 根 Tab 容器（[Route.MainTabs]）当前是否在返回栈栈顶。
 *
 * 首页「编辑态」与发现页「搜索层」的返回拦截走的是 Activity 的 `BackHandler`：
 *
 * - 不能用 `NavigationBackHandler` / miuix 的 `PredictiveBackHandler` —— 它们注册到
 *   miuix-nav 给每个 entry 建的子 dispatcher 上，那个 dispatcher 会随 entry 宿主回收被
 *   `dispose()`；而 `Scaffold` / `HorizontalPager` 这类 `SubcomposeLayout` 的子组合可能在同一帧
 *   之后才应用组合内容，此时再 `addHandler` 就会抛
 *   `This NavigationEventDispatcher has already been disposed`（release 崩溃）。
 * - `BackHandler` 注册在 Activity 的 dispatcher 上（只在 Activity 销毁时释放），不会撞上这个竞态。
 *
 * 代价是丢了 miuix 的 entry 作用域：被上层页面盖住时，entry 的子 dispatcher 会被禁用，
 * 里面的 handler 自然不响应；换成 Activity 的 dispatcher 后就必须自己判「根 Tab 是不是栈顶」，
 * 否则在创作者主页按返回会去收起搜索层 / 退出编辑态，而不是出栈。
 */
val LocalMainTabsOnTop = compositionLocalOf { true }
