package com.hrsthrt74.afdian.ui.components

// 气泡操作菜单弹层：点击消息气泡后弹出 ListPopup 风格的操作菜单。
//
// 菜单分两区：顶部 inline 快捷行（图标上、文字下，每行至多 2 个，如「复制全文」「转发」），
// 与下方普通列表区（DropdownImpl），两区之间用分割线隔开。
//
// 弹层外壳（PopupLayout / 三轨动画 / 挖洞压暗遮罩 / 摆放算法 / ListPopupContent 展开）
// 已抽到 ui/components/basic/InlinePopup.kt，本文件只保留「气泡菜单的业务内容」：
// inline 行 + 普通列表项 + 底部时间行，以及会话页专有的导航滑动闸门。
// 消息列表页的长按卡片菜单复用同一套骨架（InlineActionPopup），保证两处观感一致。

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.ui.components.basic.InlineActionItem
import com.hrsthrt74.afdian.ui.components.basic.InlineActionRow
import com.hrsthrt74.afdian.ui.components.basic.InlinePopupLayout
import com.hrsthrt74.afdian.ui.navigation.NavSwipeGate
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "MessageBubblePopup"

/**
 * 气泡操作菜单的动作项。
 *
 * @param label 菜单展示文案
 * @param icon inline 操作的图标；普通列表项为 null
 * @param isInline 是否为 inline 操作：渲染在菜单顶部「图标上、文字下」的快捷行
 *   （每行至多 2 个，与普通列表项之间用分割线隔开）
 */
enum class BubbleAction(
    override val label: String,
    override val icon: ImageVector? = null,
    val isInline: Boolean = false,
) : InlineActionItem {
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
    val haptic = LocalHapticFeedback.current

    // 按属性把菜单项拆成两区：inline 快捷行（图标上、文字下）与普通下拉列表
    val inlineActions = actions.filter { it.isInline }
    val listActions = actions.filterNot { it.isInline }

    // 打开期间临时禁用「任意位置滑动返回」：页面被滑动平移会让挖洞遮罩与气泡错位。
    // 经 NavSwipeGate → MainScreen 替换 NavDisplay 的过渡实例，滑动手势层实时重新解析为禁用。
    // 注意闸门刻意留在本文件而不进公共骨架：只有会话页（非根导航 entry）需要它，
    // 列表页弹层不该去改导航转场。
    LaunchedEffect(visible.value) {
        AppLog.d(TAG, "弹层 visible=${visible.value}")
        NavSwipeGate.blockedByPopup = visible.value
    }
    // 兜底复位：弹层随页面销毁（如导航直接离开）而闸门仍开着时，强制恢复滑动返回
    DisposableEffect(Unit) {
        onDispose { NavSwipeGate.blockedByPopup = false }
    }

    InlinePopupLayout(
        visible = visible,
        anchorBounds = bubbleBounds,
        alignEnd = alignEnd,
    ) {
        // inline 快捷行：图标上、文字下，每行至多 2 个（超出自动换到下一行）
        if (inlineActions.isNotEmpty()) {
            InlineActionRow(
                actions = inlineActions,
                onSelected = { action ->
                    // 与列表项一致：先轻震再执行动作
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    visible.value = false
                    onAction(action)
                },
            )
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
                modifier = Modifier.fillMaxWidth().padding(20.dp, 12.dp),
            )
        }
    }
}
