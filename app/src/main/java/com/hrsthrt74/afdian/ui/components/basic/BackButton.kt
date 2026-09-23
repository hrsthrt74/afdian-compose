package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.navigation.Route
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * 返回按钮：点击返回上一页，长按弹出菜单可快速回到主页。
 *
 * @param onBack 点击返回时调用（弹出栈顶）
 * @param backStack 导航栈，用于长按「主页」时 popUntil 回到根路由
 * @param modifier 外部修饰符
 * @param contentDescription 无障碍描述
 */
@Composable
fun BackButton(
    onBack: () -> Unit,
    backStack: NavBackStack,
    modifier: Modifier = Modifier,
    contentDescription: String? = "返回",
) {
    val haptic = LocalHapticFeedback.current
    var showPopup by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // 返回按钮：点击返回，长按弹出菜单
        // 不使用 IconButton（它自带 onClick 会和 combinedClickable 冲突），
        // 改用 Box + combinedClickable 实现点击/长按双行为，保持 40dp 最小触摸目标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showPopup = true
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = contentDescription,
            )
        }

        // 长按弹出的菜单：返回 / 主页
        WindowListPopup(
            show = showPopup,
            alignment = PopupPositionProvider.Align.BottomStart,
            onDismissRequest = { showPopup = false },
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                // 第一项：返回（与点击按钮行为一致）
                DropdownImpl(
                    text = "返回上级",
                    optionSize = 2,
                    isSelected = false,
                    index = 0,
                    onSelectedIndexChange = {
                        dismiss?.invoke()
                        onBack()
                    },
                )
                // 第二项：主页（弹栈直到回到根路由，与 NavController.popUntil 逻辑一致）
                DropdownImpl(
                    text = "回到首页",
                    optionSize = 2,
                    isSelected = false,
                    index = 1,
                    onSelectedIndexChange = {
                        dismiss?.invoke()
                        while (backStack.size > 1 && backStack.last() != Route.MainTabs) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    },
                )
            }
        }
    }
}
