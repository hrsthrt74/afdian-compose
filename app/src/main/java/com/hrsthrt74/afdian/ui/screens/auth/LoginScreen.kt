package com.hrsthrt74.afdian.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 登录页。
 *
 * 对外接口与旧版保持一致（onLogin/isLoggingIn/errorMessage），MainActivity 无需改动：
 * - 布局用可滚动 Column + safeDrawing insets，适配 edge-to-edge 与软键盘弹起；
 * - 密码默认掩码，trailing 图标切换显示/隐藏（MiuixIcons.Show / Hide）；
 * - 登录失败时输入框 label/边框变红，并在下方给出红字提示；
 * - 账号框回车跳密码框，密码框回车直接提交。
 */
@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    isLoggingIn: Boolean = false,
    errorMessage: String? = null,
) {
    // 表单草稿：rememberSaveable 保证进程重建后不丢用户已输入的内容
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // 密码可见性属于纯展示态，用 remember 即可；默认掩码显示
    var passwordVisible by remember { mutableStateOf(false) }

    // 用于「账号回车 → 焦点跳到密码框」的焦点控制器
    val passwordFocus = remember { FocusRequester() }
    // 提交登录后收起软键盘
    val focusManager = LocalFocusManager.current

    // 是否处于错误态：由上层 session.error 驱动，重新点击登录前会一直保持红显
    val hasError = errorMessage != null

    // 错误态下把输入框的 label 和边框染成 error 色；正常态走主题默认色
    val fieldColors = if (hasError) {
        TextFieldDefaults.textFieldColors(
            labelColor = MiuixTheme.colorScheme.error,
            borderColor = MiuixTheme.colorScheme.error,
        )
    } else {
        TextFieldDefaults.textFieldColors()
    }

    // 统一的提交入口：账号/密码都非空且不在登录中才允许提交
    val submitEnabled = account.isNotBlank() && password.isNotBlank() && !isLoggingIn
    fun submit() {
        if (!submitEnabled) return
        focusManager.clearFocus(force = true)
        onLogin(account.trim(), password)
    }

    // 可滚动根布局：外层用 Miuix Scaffold 铺 colorScheme.background，
    // 跟随明暗模式（否则裸 Column 透出的是系统白色窗口底）；内层滚动 + safeDrawing insets
    // 一次性处理状态栏、导航栏、刘海和 IME 的避让（其中已含 IME，无需再叠 imePadding）
    Scaffold { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
        ) {
        // 顶部留白约屏高 1/4，让标题落在视觉舒适区（纯 Spacer 撑开，不引入额外组件）
        Spacer(Modifier.weight(1f))

        // 头部标题区：大标题 + 摘要色副标题，形成文字层级
        Text(
            text = "爱发电",
            style = MiuixTheme.textStyles.title1,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Text(
            text = "登录以继续",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 8.dp),
        )

        // 标题与表单之间拉开一段距离
        Spacer(Modifier.height(32.dp))

        // 账号输入框：单行，回车键为「下一项」，触发焦点跳转到密码框
        TextField(
            value = account,
            onValueChange = { account = it },
            label = "账号",
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            colors = fieldColors,
        )
        // 密码输入框：默认掩码，回车键为「完成」直接提交；trailing 图标切换可见性
        TextField(
            value = password,
            onValueChange = { password = it },
            label = "密码",
            useLabelAsPlaceholder = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocus)
                .padding(top = 12.dp),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                // 点击在 Show（当前隐藏，点它显示）与 Hide（当前显示，点它隐藏）之间切换
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) MiuixIcons.Hide else MiuixIcons.Show,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                    )
                }
            },
            colors = fieldColors,
        )

        // 登录失败提示：红字置于表单下方；不只靠颜色传达，附明确文案
        if (hasError) {
            Text(
                text = errorMessage.orEmpty(),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        // 主操作按钮：占满宽度 + 主色；登录中内嵌旋转指示器并禁用防重复提交
        Button(
            onClick = { submit() },
            enabled = submitEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 登录中的进度反馈：复用全局 Loading 同款指示器
                if (isLoggingIn) {
                    InfiniteProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.padding(start = 8.dp))
                }
                Text(if (isLoggingIn) "登录中…" else "登录")
            }
        }

        // 底部留白约屏高 1/3，整体内容略偏上、符合登录页视觉习惯
            Spacer(Modifier.weight(1.3f))
        }
    }
}
