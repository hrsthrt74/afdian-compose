package com.hrsthrt74.afdian.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * 底栏样式档位：0=常规（贴底整条） 1=悬浮（胶囊）。
 *
 * 由 MainActivity 在根组合按持久化设置下发（与 [LocalDataCardFontFamily] 同一套做法），
 * 主界面底栏与「设置 → 外观」的预览卡都读它，全 App 只有一个数据源。
 *
 * **为什么设置页不读 `SettingsViewModel.bottomBarStyle`**：那个 ViewModel 是进入页面时才创建的，
 * `stateIn` 的初值恒为默认档（0=常规），DataStore 里的真值要晚一两帧才到 ——
 * 表现就是「明明选的是悬浮，进外观页先闪一下常规」。根组合从 App 启动就在收这个值，
 * 进设置页时早已就绪，读它不会再闪。
 */
val LocalBottomBarStyle = compositionLocalOf { 0 }
