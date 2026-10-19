package com.hrsthrt74.afdian.ui.screens.main

// 会话卡片的长按操作菜单：状态持有 + 动作组装 + 弹层挂载，从 MessagesScreen 拆出。
//
// Screen 只负责三件事：调用 [rememberConversationActionMenuState] 创建状态、
// 在卡片的长按回调里调 [ConversationActionMenuState.show]、在 Scaffold 组合作用域内
// 调用 [ConversationActionMenu] 挂载弹层。
//
// 弹层观感与会话页的气泡操作菜单完全一致（图标在上、文字在下 + 挖洞压暗），
// 因为两者共用 ui/components/basic/InlinePopup.kt 里的同一套骨架。
//
// 注意：弹层挂载必须在 MessagesScreen 的 Scaffold 组合作用域内 ——
// MiuixPopupUtils.PopupLayout 会把弹层注册到 Scaffold 提供的 LocalPopupStates，
// 由 MiuixPopupHost 渲染；放到 Scaffold 外会注册进无人消费的孤儿列表，弹层永不显示。

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.MessageDialog
import com.hrsthrt74.afdian.ui.components.basic.InlineActionItem
import com.hrsthrt74.afdian.ui.components.basic.InlineActionPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Unpin

private const val TAG = "ConversationMenu"

/**
 * 会话长按菜单的动作。
 *
 * 全部按 inline 样式渲染（图标在上、文字在下），[label] 即格子下方的文字。
 *
 * @param label 菜单展示文案
 * @param icon inline 图标
 */
internal enum class ConversationAction(
    override val label: String,
    override val icon: ImageVector,
) : InlineActionItem {
    /** 置顶：把会话整块挪到列表最前 */
    Pin("置顶", MiuixIcons.Pin),

    /** 取消置顶：已置顶的会话落回服务端给的位置 */
    Unpin("取消置顶", MiuixIcons.Unpin),

    /** 标为已读：单会话操作，沿用 MiuixIcons.Ok；三点菜单的「全部已读」已换成资料库的自定义图标，两者刻意区分。会话已读时**保留这一格但置灰**不可点 */
    MarkRead("标为已读", MiuixIcons.Ok),
}

/**
 * 长按瞬间冻结的会话快照。
 *
 * 为什么要冻结而不是直接引用列表里的 `MessageDialog`：菜单打开期间列表可能刷新
 * （30s 轮询 / 手动下拉），如果菜单项实时读列表数据，「取消置顶」的文案可能在退场动画
 * 期间闪变；坐标同理，冻结后遮罩洞才不会跟着列表乱跑。
 *
 * 同时这里带着**整条会话**：[ConversationActionMenuState.show] 的调用方直接把它交给
 * `togglePinned`，置顶时才能存下卡片快照（置顶的老会话可能不在已加载的分页里）。
 *
 * @param dialog 长按的会话（服务端数据或本地置顶快照还原出来的卡片都可能是它）
 * @param pinned 长按瞬间是否已置顶（决定显示「置顶」还是「取消置顶」）
 * @param bounds 卡片在窗口中的坐标快照（弹层锚点：决定菜单位置与挖洞遮罩）
 */
internal data class ConversationMenuTarget(
    val dialog: MessageDialog,
    val pinned: Boolean,
    val bounds: Rect,
) {
    /** 是否有未读：决定是否显示「标为已读」 */
    val hasUnread: Boolean get() = dialog.unread_count > 0
}

/**
 * 会话长按菜单状态：弹层开关 + 被长按的会话快照。通过 [rememberConversationActionMenuState] 创建。
 */
internal class ConversationActionMenuState {

    /** 弹层显示开关（MiuixPopupUtils.PopupLayout 要求 MutableState<Boolean>） */
    val popupVisible: MutableState<Boolean> = mutableStateOf(false)

    /** 被长按的会话快照；菜单打开期间不变 */
    var target: ConversationMenuTarget? by mutableStateOf(null)
        private set

    /** 长按卡片：冻结会话快照 → 弹出操作菜单（长按震动由 combinedClickable 默认提供） */
    fun show(target: ConversationMenuTarget) {
        // 日志只打置顶/未读状态，不打 user_id（日志会落盘导出）
        AppLog.d(TAG, "长按会话卡片，弹出操作菜单：pinned=${target.pinned}, unread=${target.hasUnread}")
        this.target = target
        popupVisible.value = true
    }

    /**
     * 收起菜单。
     *
     * 刻意**不清空 [target]**：弹层退出动画（90ms）期间仍会渲染内容，
     * 提前置空会让菜单在收起过程中突然闪空。target 只在下一次 [show] 时被覆盖。
     */
    fun dismiss() {
        popupVisible.value = false
    }
}

/** 创建会话长按菜单状态；无参数，随宿主组合存活。 */
@Composable
internal fun rememberConversationActionMenuState(): ConversationActionMenuState =
    remember { ConversationActionMenuState() }

/**
 * 会话长按菜单弹层挂载：按当前快照组装菜单项并渲染 [InlineActionPopup]。
 *
 * 必须位于 MessagesScreen 的 Scaffold 组合作用域内（原因见文件头注释）。
 *
 * @param onTogglePin 置顶 / 取消置顶（参数是整条会话：置顶时要把卡片快照存下来）
 * @param onMarkRead 标为已读（参数为对端 user_id）
 */
@Composable
internal fun ConversationActionMenu(
    state: ConversationActionMenuState,
    onTogglePin: (MessageDialog) -> Unit,
    onMarkRead: (String) -> Unit,
) {
    val target = state.target

    // 菜单项按冻结快照组装：已置顶给「取消置顶」；「标为已读」始终在列，
    // 会话已读时置灰不可点 —— 菜单项数量与位置稳定，不会因为一次操作成功就整行改版。
    // 弹层本体无条件组合（不在这里 return），保证 PopupLayout 始终挂在宿主上，
    // 首次打开不会多等一帧注册。
    val actions = buildList {
        if (target != null) {
            add(if (target.pinned) ConversationAction.Unpin else ConversationAction.Pin)
            add(ConversationAction.MarkRead)
        }
    }

    InlineActionPopup(
        visible = state.popupVisible,
        anchorBounds = target?.bounds,
        // 会话卡片是整行宽，菜单贴卡片左缘展开（与气泡菜单「贴对应边缘」的规则一致）
        alignEnd = false,
        actions = actions,
        // 「标为已读」仅在有未读时可点；其余动作恒可点
        isEnabled = { action ->
            action != ConversationAction.MarkRead || target?.hasUnread == true
        },
        onSelected = { action ->
            // 先取快照再动作：退出动画期间 target 理论上不变，但这里不依赖该假设
            val current = target ?: return@InlineActionPopup
            when (action) {
                // 置顶与取消置顶是同一条链路的两个方向，统一交给 ViewModel 切换
                ConversationAction.Pin,
                ConversationAction.Unpin -> onTogglePin(current.dialog)

                ConversationAction.MarkRead -> onMarkRead(current.dialog.id)
            }
        },
    )
}
