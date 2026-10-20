package com.hrsthrt74.afdian.ui.components.account

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.auth.SavedAccountSummary
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import com.hrsthrt74.afdian.ui.components.basic.InlineActionItem
import com.hrsthrt74.afdian.ui.components.basic.InlineActionRow
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * 账号切换弹层需要的数据与动作。
 *
 * 由根层（MainActivity）注入：账号列表和切换/删除/添加的调度都在 AuthSession 手里，
 * 而弹层挂在「我的」页头像旁边。用组合局部传下去，避免从 MainActivity 一路
 * 把 6 个参数透传到 ProfileScreen。
 *
 * 删除不在菜单里做，统一收到设置页的「账号管理」：菜单只负责快速切换，
 * 破坏性操作放到有完整上下文的地方，减少误触。
 */
data class AccountSwitcherActions(
    val accounts: List<SavedAccountSummary>,
    val currentUserId: String?,
    val invalidUserIds: Set<String>,
    val onSwitch: (String) -> Unit,
    val onDelete: (SavedAccountSummary) -> Unit,
    val onAddAccount: () -> Unit,
    val onRefresh: () -> Unit,
)

/** 账号切换弹层的入口；null 表示当前不可用（未登录 / 根层未注入）。 */
val LocalAccountSwitcher = compositionLocalOf<AccountSwitcherActions?> { null }

/** 菜单底部的 inline 快捷操作：图标在上、文字在下，与消息气泡菜单同一套视觉。 */
private enum class AccountMenuAction(
    override val label: String,
    override val icon: ImageVector,
) : InlineActionItem {
    Add("添加", MiuixIcons.Add),
    Manage("管理", MiuixIcons.Settings),
}

/**
 * 头像旁弹出的账号菜单。
 *
 * 容器用 Miuix 标准的 [WindowListPopup]（下拉菜单窗口），账号行用 [DropdownImpl] + [DropdownItem]：
 * 头像走 `item.icon`、昵称走 `text`、失效提示走 `summary`，
 * 当前账号靠 `isSelected` 高亮，选中态的对号由组件自己画 —— 不需要再手写行布局。
 *
 * 注意：`WindowListPopup` 以「调用处的父布局」作为锚点，所以调用方必须把它和头像
 * 放进同一个 Box，菜单才会贴着头像下沿弹出。
 *
 * @param visible 显隐开关；点遮罩/返回键时由本组件置 false
 * @param onManage 打开设置页的「账号管理」
 */
@Composable
fun AccountSwitchPopup(
    visible: MutableState<Boolean>,
    actions: AccountSwitcherActions,
    onManage: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    WindowListPopup(
        show = visible.value,
        // 头像在左上角，菜单往右下展开
        alignment = PopupPositionProvider.Align.BottomStart,
        onDismissRequest = { visible.value = false },
        // 昵称 + 状态两行，太窄会挤成省略号
        minWidth = 220.dp,
    ) {
        // 菜单内部的关闭入口：点条目后先收起菜单，再执行动作
        val dismiss = LocalDismissState.current
        ListPopupColumn {
            // 账号列表在上：标准下拉项，点击即切换
            actions.accounts.forEachIndexed { index, account ->
                val isCurrent = account.userId == actions.currentUserId
                DropdownImpl(
                    item = DropdownItem(
                        text = account.name.ifBlank { "爱发电用户" },
                        // 预检失败的账号：提示只能去设置页删掉后重新添加
                        summary = if (account.userId in actions.invalidUserIds) "登录已失效" else null,
                        // 头像作为行首图标；图标槽的尺寸/间距由组件传入的 modifier 决定
                        icon = { cellModifier ->
                            Avatar(
                                model = account.avatar,
                                size = 32.dp,
                                contentDescription = "账号头像",
                                modifier = cellModifier,
                            )
                        },
                    ),
                    optionSize = actions.accounts.size,
                    isSelected = isCurrent,
                    index = index,
                    isFirst = index == 0,
                    // 末项按「菜单最后一行」处理：底部留出首末项的大间距，
                    // 与下方分割线之间不会贴得太紧
                    isLast = index == actions.accounts.lastIndex,
                    onSelectedIndexChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dismiss?.invoke()
                        if (!isCurrent) actions.onSwitch(account.userId)
                    },
                )
            }
            if (actions.accounts.isEmpty()) {
                // 防御：账号表损坏时不至于弹出空菜单
                Text(
                    text = "还没有保存的账号",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            // 账号列表与操作区之间的分割线
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 操作区在下：与消息气泡菜单同款 inline 行
            InlineActionRow(
                actions = AccountMenuAction.entries,
                onSelected = { action ->
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    dismiss?.invoke()
                    when (action) {
                        AccountMenuAction.Add -> actions.onAddAccount()
                        AccountMenuAction.Manage -> onManage()
                    }
                },
            )
        }
    }
}
