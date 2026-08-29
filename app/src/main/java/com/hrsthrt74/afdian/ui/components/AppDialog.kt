package com.hrsthrt74.afdian.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 对话框的显示状态。
 *
 * 与 [SheetState] 同构：统一管理一个对话框的显示/隐藏，并封装「请求关闭」逻辑
 * （点击遮罩 / 按下返回键）。页面中不再需要为每个对话框单独声明
 * `var showXxx by remember { mutableStateOf(false) }` 并重复编写
 * `onDismissRequest = { showXxx = false }`，改用本状态后只需：
 *
 * ```
 * val confirmDialogState = rememberDialogState()
 * // 打开
 * confirmDialogState.show()
 * // 关闭（由 AppDialog 内部自动调用，页面无需手动处理）
 * confirmDialogState.dismiss()
 * ```
 *
 * @param initialShow 初始是否显示
 */
@Stable
class DialogState(initialShow: Boolean = false) {

    /** 当前是否显示（只读，外部通过 [show]/[dismiss] 控制） */
    var show by mutableStateOf(initialShow)
        private set

    /** 打开对话框 */
    fun show() {
        show = true
    }

    /** 关闭对话框（等价于点击遮罩/返回键时的行为，供对话框内容里的按钮调用） */
    fun dismiss() {
        show = false
    }
}

/**
 * 创建并记住一个 [DialogState]。
 *
 * @param initialShow 初始是否显示
 */
@Composable
fun rememberDialogState(initialShow: Boolean = false): DialogState =
    remember { DialogState(initialShow) }

/**
 * 统一的对话框组件。
 *
 * 封装 [WindowDialog]，将项目内所有 Dialog 的重复逻辑集中到一处，默认样式为
 * 「取消 + 确认」按钮（本项目所有对话框都用这种交互）：
 * - **请求关闭统一处理**：点击遮罩 / 按下返回键 / 取消按钮时自动调用 [DialogState.dismiss]，
 *   页面不再需要重复编写 `onDismissRequest = { showXxx = false }`
 * - **确认按钮统一行为**：点击确认后先执行 [onConfirm] 再自动关闭对话框
 * - **关闭后清理统一入口**：通过 [onDismissed] 在关闭动画完成后集中执行状态清理
 *
 * @param state Dialog 的显示状态（由 [rememberDialogState] 创建）
 * @param modifier 应用到 Dialog 内容上的修饰符
 * @param title 标题
 * @param summary 说明文字
 * @param cancelText 取消按钮文案（默认「取消」）
 * @param confirmText 确认按钮文案
 * @param confirmEnabled 确认按钮是否可点击（默认 true）；为 false 时置灰禁用，
 *   常用于需要等待一段时间（如倒计时）后才允许确认的场景
 * @param destructive 是否为破坏性操作；为 true 时确认按钮使用错误色（红色）
 * @param onConfirm 点击确认按钮时的回调；确认后对话框由本组件自动关闭
 * @param onDismissed 关闭动画完成后的回调（用于清理临时状态）；注意 show 为 false 但从未显示时不会触发
 * @param content 按钮区上方的额外内容（可省略）
 */
@Composable
fun AppDialog(
    state: DialogState,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    cancelText: String = "取消",
    confirmText: String,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismissed: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    WindowDialog(
        show = state.show,
        title = title,
        summary = summary,
        modifier = modifier,
        // 请求关闭（遮罩/返回）统一交给状态管理，页面无需重复编写
        onDismissRequest = { state.dismiss() },
        // 关闭动画完成后触发清理回调
        onDismissFinished = { onDismissed?.invoke() },
    ) {
        content()
        // miuix 居然没这个间距，没了看起来很奇怪哎
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 左侧固定为取消按钮
            TextButton(
                text = cancelText,
                onClick = { state.dismiss() },
                modifier = Modifier.weight(1f)
            )

            if (destructive) {
                // 破坏性操作：确认按钮用错误色填充，突出「不可撤销」的警告
                Button(
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError,
                    ),
                    enabled = confirmEnabled,
                    onClick = {
                        onConfirm()
                        state.dismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(confirmText)
                }
            } else {
                // 普通操作：确认按钮用主题主色
                TextButton(
                    text = confirmText,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = confirmEnabled,
                    onClick = {
                        onConfirm()
                        state.dismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}