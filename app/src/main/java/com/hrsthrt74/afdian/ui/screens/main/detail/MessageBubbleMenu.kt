package com.hrsthrt74.afdian.ui.screens.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.MessageContent
import com.hrsthrt74.afdian.data.content.MessageItem
import com.hrsthrt74.afdian.ui.components.BubbleAction
import com.hrsthrt74.afdian.ui.components.MessageBubbleActionPopup
import com.hrsthrt74.afdian.ui.components.formatExactMessageTime
import com.hrsthrt74.afdian.ui.navigation.Route
import top.yukonga.miuix.kmp.nav.core.NavBackStack

/**
 * 气泡操作菜单：状态持有 + 动作执行 + 弹层挂载，从 MessageConversationScreen 拆出。
 *
 * Screen 只负责三件事：调用 [rememberMessageBubbleMenuState] 创建状态、把
 * [MessageBubbleMenuState.onBubbleTapped] / [MessageBubbleMenuState.onBubbleLiveBounds]
 * 接到消息列表的气泡上、在 Scaffold 组合作用域内调用 [MessageBubbleMenu] 挂载弹层。
 *
 * 注意：弹层挂载必须在会话页 Scaffold 的组合作用域内 ——
 * MiuixPopupUtils.PopupLayout 会把弹层注册到 Scaffold 提供的 LocalPopupStates，
 * 由本 Scaffold 的 MiuixPopupHost（popupHost 槽位，覆盖本页顶栏与内容区）渲染；
 * 放到 Scaffold 外会注册进无人消费的孤儿列表，弹层永不显示。
 */

/**
 * 气泡操作菜单状态：选中消息 / 气泡坐标快照 / 弹层开关 + 菜单动作执行。
 * 通过 [rememberMessageBubbleMenuState] 创建。
 */
class MessageBubbleMenuState internal constructor(
    // 会话对方信息与导航栈：「查看主页」的跳转目标
    // （会话是 1 对 1，对方气泡的发出者就是会话对方，数据取自路由参数）
    private val userId: String,
    private val userName: String,
    private val avatarUrl: String?,
    private val backStack: NavBackStack,
    // 触觉反馈与上下文：点击震动、复制/占位动作的 Toast
    private val haptic: HapticFeedback,
    private val context: Context,
) {
    /** 弹层显示开关（MiuixPopupUtils.PopupLayout 要求 MutableState<Boolean>） */
    val popupVisible = mutableStateOf(false)

    /** 被选中的消息：点击气泡瞬间冻结，菜单打开期间不变 */
    var selectedMessage by mutableStateOf<MessageItem?>(null)
        private set

    /** 被选气泡的窗口坐标快照；弹层打开期间随实时边界上报持续刷新（防错位） */
    var selectedBubbleBounds by mutableStateOf<Rect?>(null)
        private set

    /** 点击气泡：冻结消息与气泡坐标 → 震动 → 弹出操作菜单 */
    val onBubbleTapped: (MessageItem, Rect) -> Unit = { item, bounds ->
        AppLog.d(TAG, "点击气泡 msgId=${item.id} bounds=$bounds")
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        selectedMessage = item
        selectedBubbleBounds = bounds
        popupVisible.value = true
    }

    /** 选中气泡的实时边界上报：弹层打开期间挖洞遮罩与菜单一律跟随气泡的最新
     *  窗口坐标（防错位保险层）；非选中消息的上报直接忽略 */
    val onBubbleLiveBounds: (MessageItem, Rect) -> Unit = { item, bounds ->
        if (item.id == selectedMessage?.id && selectedBubbleBounds != bounds) {
            selectedBubbleBounds = bounds
        }
    }

    /** 菜单动作执行：复制全文 / 复制单号 / 查看主页 / 转发（占位） */
    fun onAction(action: BubbleAction) {
        when (action) {
            BubbleAction.CopyFullText -> {
                val text = (selectedMessage?.message?.content as? MessageContent.Text)?.text.orEmpty()
                if (text.isNotEmpty()) {
                    copyToClipboard(text, clipLabel = "message")
                }
            }
            // 复制单号：订单卡片的「复制部分」类 inline 操作，取 out_trade_no
            BubbleAction.CopyOrderNo -> {
                val orderNo = (selectedMessage?.message?.content as? MessageContent.Object)
                    ?.content?.out_trade_no.orEmpty()
                if (orderNo.isNotBlank()) {
                    copyToClipboard(orderNo, clipLabel = "订单号", toastText = "已复制单号")
                }
            }
            // 查看主页：跳转所选气泡发出者（即会话对方）的主页。
            // pushUnique 是 MainScreen 私有扩展，这里照客服入口的写法按 user_id 去重
            BubbleAction.ViewHomePage -> {
                if (selectedMessage?.isSentByMe == false) {
                    val creatorOpen = backStack.any {
                        it is Route.CreatorDetail && it.user.user_id == userId
                    }
                    if (!creatorOpen) {
                        backStack.add(
                            Route.CreatorDetail(
                                AfadianUser(user_id = userId, name = userName, avatar = avatarUrl)
                            )
                        )
                    }
                }
            }
            // 占位动作：菜单链路已通，具体功能待后续实现
            BubbleAction.Forward -> {
                AppLog.d(TAG, "占位动作：${action.label}")
                Toast.makeText(context, "「${action.label}」功能开发中", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 复制文本到系统剪贴板：写入兜底 + 日志 + Toast 反馈。
     * 供「复制全文」「复制单号」等复制类动作共用。
     *
     * @param text 要复制的文本
     * @param clipLabel 剪贴板 ClipData 的描述标签
     * @param toastText 复制成功的 Toast 文案
     */
    private fun copyToClipboard(
        text: String,
        clipLabel: String,
        toastText: String = "已复制",
    ) {
        // setPrimaryClip 在部分机型/超大文本下会抛异常，这里兜住避免崩到顶层
        val copiedOk = runCatching {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
        }.onFailure { throwable ->
            AppLog.w(TAG, "气泡菜单复制失败（$clipLabel）", throwable)
        }.isSuccess
        if (copiedOk) {
            AppLog.d(TAG, "气泡菜单复制成功（$clipLabel 长度=${text.length}）")
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        /** 日志 TAG：与拆出前保持一致，会话页相关日志同标签过滤 */
        const val TAG = "MessageConversation"
    }
}

/** 创建气泡操作菜单状态：会话参数作为 remember 键，参数变化（理论上不会发生）时重建。 */
@Composable
fun rememberMessageBubbleMenuState(
    userId: String,
    userName: String,
    avatarUrl: String?,
    backStack: NavBackStack,
): MessageBubbleMenuState {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    return remember(userId, userName, avatarUrl, backStack) {
        MessageBubbleMenuState(userId, userName, avatarUrl, backStack, haptic, context)
    }
}

/**
 * 气泡操作菜单弹层挂载：按当前选中消息组装菜单项并渲染 MessageBubbleActionPopup。
 * 必须位于会话页 Scaffold 的组合作用域内（原因见文件头注释）。
 */
@Composable
fun MessageBubbleMenu(state: MessageBubbleMenuState) {
    val selected = state.selectedMessage
    MessageBubbleActionPopup(
        visible = state.popupVisible,
        bubbleBounds = state.selectedBubbleBounds,
        // 我方气泡右缘对齐菜单，对方气泡左缘对齐
        alignEnd = selected?.isSentByMe ?: false,
        // 菜单项（顺序即展示顺序）：文本消息提供 inline「复制全文」+ inline「转发」；
        // 发电订单卡片提供 inline「复制单号」（订单卡片才有 out_trade_no，电圈邀请等
        // 卡片不显示）；对方气泡提供「查看主页」（跳转会话对方的主页）。
        // 「转发」仅非卡片消息（文本）显示——卡片消息自带的操作入口已够用；
        // 其他卡片消息的「复制部分」类 inline 操作在此按类型追加
        actions = buildList {
            when (val content = selected?.message?.content) {
                // 文本消息：复制全文 + 转发（转发仅非卡片消息显示）
                is MessageContent.Text -> {
                    add(BubbleAction.CopyFullText)
                    add(BubbleAction.Forward)
                }
                // 订单卡片：有订单号才提供「复制单号」
                is MessageContent.Object -> {
                    if (!content.content.out_trade_no.isNullOrBlank()) {
                        add(BubbleAction.CopyOrderNo)
                    }
                }
                else -> Unit
            }
            // 「查看主页」仅对方气泡显示
            if (selected?.isSentByMe == false) {
                add(BubbleAction.ViewHomePage)
            }
        },
        // 底部时间行：所选消息的具体发送时间（无发送时间则不显示）
        sendTimeText = selected?.message?.send_time?.let(::formatExactMessageTime),
        onAction = state::onAction,
    )
}
