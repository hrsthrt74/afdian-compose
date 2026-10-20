package com.hrsthrt74.afdian.ui.screens.auth

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 「添加账号」弹窗。
 *
 * 用 [WindowDialog] 承载登录表单，而不是一张全屏页面：
 * 添加账号是当前会话里的一个附属操作，弹窗更符合「临时、可取消」的语义，
 * 也不会把用户从「我的」页整个带走。
 *
 * 登录表单复用 [LoginScreen] 的 `embedded` 模式：不套全屏 Scaffold、不叠加系统栏 insets，
 * 高度由内容决定。`largeScreen = true` 强制居中缩放/淡入，避免在窄屏上退化成底部滑入。
 *
 * @param show 显隐由外层 `isAddingAccount` 驱动；关闭时保留组合以播放退出动画
 */
@Composable
fun AddAccountOverlay(
    show: Boolean,
    isLoggingIn: Boolean,
    isSendingCode: Boolean,
    codeCooldownSeconds: Int,
    captchaBytes: ByteArray?,
    errorMessage: String?,
    initialAccount: String,
    onLogin: (String, String) -> Unit,
    onCodeLogin: (String, String) -> Unit,
    onSendCode: (String, String) -> Unit,
    onClearError: () -> Unit,
    onCancel: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "添加账号",
        // 居中缩放/淡入：大屏形态在手机上同样是对话框观感
        largeScreen = true,
        // 点遮罩、按返回键都视为取消添加，不会改动当前账号
        onDismissRequest = onCancel,
    ) {
        // 只在展开时组合表单：关闭后（含退出动画）内容为空，
        // 下次打开是全新的表单，不会残留上次输入的账号密码
        if (show) {
            LoginScreen(
                onLogin = onLogin,
                onCodeLogin = onCodeLogin,
                onSendCode = onSendCode,
                isLoggingIn = isLoggingIn,
                isSendingCode = isSendingCode,
                codeCooldownSeconds = codeCooldownSeconds,
                captchaBytes = captchaBytes,
                errorMessage = errorMessage,
                initialAccount = initialAccount,
                onClearError = onClearError,
                // 关键：嵌入弹层模式
                embedded = true,
            )
        }
    }
}
