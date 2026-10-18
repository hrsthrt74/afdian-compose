package com.hrsthrt74.afdian.ui.screens.auth

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 登录方式：密码 / 验证码。 */
private const val MODE_PASSWORD = 0
private const val MODE_CODE = 1

/**
 * 登录页。
 *
 * 支持两种登录方式，用顶部 Tab 切换：
 * - 密码登录：账号 + 密码（AES/RSA 加密后提交）；
 * - 验证码登录：账号 + 短信/邮件验证码，发码前可能需要先通过图形验证码。
 *
 * 布局用可滚动 Column + safeDrawing insets，适配 edge-to-edge 与软键盘弹起：
 * - 密码默认掩码，trailing 图标切换显示/隐藏；
 * - 验证码输入框右侧是「获取验证码」按钮，发送成功后原地变成 60 秒倒计时；
 * - 服务端要求人机校验时，在验证码下方就地展开图形验证码（点图片可换一张），
 *   不再弹二级弹窗，避免弹窗关闭/重开带来的输入丢失；
 * - 登录失败时输入框 label/边框变红，并在下方给出红字提示；
 * - 若传入 buildExpiresAtMs，则在底部显示「本构建将于 XXX 过期」提示。
 *
 * @param onLogin 密码登录回调（账号, 密码）
 * @param onCodeLogin 验证码登录回调（账号, 验证码）
 * @param onSendCode 发送验证码回调（账号, 图形验证码）；首次发送图形码传空串
 */
@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onCodeLogin: (String, String) -> Unit = { _, _ -> },
    onSendCode: (String, String) -> Unit = { _, _ -> },
    isLoggingIn: Boolean = false,
    isSendingCode: Boolean = false,
    codeCooldownSeconds: Int = 0,
    captchaBytes: ByteArray? = null,
    errorMessage: String? = null,
    buildExpiresAtMs: Long? = null,
    onClearError: () -> Unit = {},
) {
    // 表单草稿：rememberSaveable 保证进程重建后不丢用户已输入的内容
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    // 用户填写的图形验证码；只在服务端要求校验时才有意义
    var captchaInput by rememberSaveable { mutableStateOf("") }
    // 当前登录方式：默认密码登录，切到验证码后账号内容保留
    var mode by rememberSaveable { mutableStateOf(MODE_PASSWORD) }
    // 密码可见性属于纯展示态，用 remember 即可；默认掩码显示
    var passwordVisible by remember { mutableStateOf(false) }

    // 用于「上一项回车 → 下一项」的焦点控制器
    val passwordFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val captchaFocus = remember { FocusRequester() }
    // 提交登录后收起软键盘
    val focusManager = LocalFocusManager.current
    // 切登录方式时的触觉反馈，与收藏页 Tab 切换保持一致
    val haptic = LocalHapticFeedback.current

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

    // 图形验证码：数据层给的是 PNG 字节，这里在后台线程解码成 ImageBitmap，
    // 避免在组合阶段做解码阻塞主线程。captchaBytes 一变就重新解码。
    val captchaBitmap by produceState<ImageBitmap?>(initialValue = null, captchaBytes) {
        value = captchaBytes?.let { bytes ->
            withContext(Dispatchers.Default) {
                runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    // 图形验证码出现时自动聚焦输入框；消失（发送成功）时清空已填内容
    LaunchedEffect(captchaBytes) {
        if (captchaBytes != null) {
            captchaFocus.requestFocus()
        } else {
            captchaInput = ""
        }
    }

    // 统一的提交入口：按当前登录方式校验必填项
    val submitEnabled = when (mode) {
        MODE_CODE -> account.isNotBlank() && code.isNotBlank() && !isLoggingIn
        else -> account.isNotBlank() && password.isNotBlank() && !isLoggingIn
    }

    fun submit() {
        if (!submitEnabled) return
        focusManager.clearFocus(force = true)
        if (mode == MODE_CODE) {
            onCodeLogin(account.trim(), code.trim())
        } else {
            onLogin(account.trim(), password)
        }
    }

    // 发送验证码：有图形验证码时必须先填写，没有则只提交账号
    val sendEnabled = account.isNotBlank() && !isSendingCode && codeCooldownSeconds == 0 &&
        (captchaBytes == null || captchaInput.isNotBlank())
    fun sendCode() {
        if (!sendEnabled) return
        onSendCode(account.trim(), if (captchaBytes != null) captchaInput.trim() else "")
    }

    // 可滚动根布局：外层用 Miuix Scaffold 铺 colorScheme.background，
    // 跟随明暗模式（否则裸 Column 透出的是系统白色窗口底）；内层滚动 + safeDrawing insets
    // 一次性处理状态栏、导航栏、刘海和 IME 的避让（其中已含 IME，无需再叠 imePadding）
    Scaffold { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // 顶部留白约屏高 1/4，让标题落在视觉舒适区（纯 Spacer 撑开，不引入额外组件）
            Spacer(Modifier.weight(1f))

            // 横向内边距下沉到各组件：页面本身铺满宽度，转场才能滑到屏幕边缘，
            // 不会被外层 padding 提前裁掉（这也是其他页面的做法）。
            Text(
                text = "爱发电",
                style = MiuixTheme.textStyles.title1,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )
            Text(
                text = "登录以继续",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 24.dp, end = 24.dp),
            )

            // 登录方式切换：透明底融入页面，不额外铺一层 surface
            TabRow(
                tabs = listOf("密码登录", "验证码登录"),
                selectedTabIndex = mode,
                onTabSelected = { index ->
                    if (index != mode) {
                        // 先震后切：触觉反馈不等转场动画，和收藏页 Tab 的手感一致
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // 旧输入框马上会被转场移除，先收键盘清焦点，避免 IME 跟着一起闪
                        focusManager.clearFocus(force = true)
                        mode = index
                        // 切方式时清掉上一种方式的错误提示，避免红字挂在无关表单上
                        onClearError()
                    }
                },
                colors = TabRowDefaults.tabRowColors(Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 24.dp, end = 24.dp),
            )

            // 标题与表单之间拉开一段距离
            Spacer(Modifier.height(20.dp))

            // 密码 / 验证码两种表单的切换：复用收藏页 Tab 的转场语言
            // （淡入淡出 + 1/8 横向位移，方向由 Tab 顺序决定）。
            // 整个输入区（账号 + 当前方式的输入项）作为一个整体参与转场；
            // 横向内边距放在转场内部，AnimatedContent 本身铺满宽度，滑动才不会被裁。
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    // 密码(0) → 验证码(1) 视为「向后」：新表单从右侧滑入，旧表单往左退
                    val forward = targetState > initialState
                    (fadeIn(animationSpec = tween(220)) +
                        slideInHorizontally(animationSpec = tween(220)) { if (forward) it / 8 else -it / 8 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(150)) +
                                slideOutHorizontally(animationSpec = tween(150)) { if (forward) -it / 8 else it / 8 }
                        )
                },
                label = "loginModeForm",
            ) { currentMode ->
                // 每个分支都是「账号 + 当前方式的输入项」的完整表单：
                // 切换时整个输入区一起滑动，而不是只有下半截在动。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 横向留白放在转场内部：AnimatedContent 本身铺满宽度，
                        // 内容自带内边距，滑动才能一直滑到屏幕边缘而不被外层 padding 提前裁住
                        .padding(horizontal = 24.dp),
                ) {
                    // 账号输入框：单行，回车跳到下一个输入项（密码或验证码）
                    TextField(
                        value = account,
                        onValueChange = { account = it },
                        label = "手机号或邮箱",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                // 用 currentMode 判断：转场期间旧表单仍按自己的输入方式跳焦点
                                if (currentMode == MODE_CODE) codeFocus.requestFocus() else passwordFocus.requestFocus()
                            }
                        ),
                        colors = fieldColors,
                    )

                    if (currentMode == MODE_PASSWORD) {
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
                    } else {
                        // 验证码输入框：右侧内嵌「获取验证码」，发送后原地显示倒计时
                        TextField(
                            value = code,
                            onValueChange = { code = it },
                            label = "验证码",
                            useLabelAsPlaceholder = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(codeFocus)
                                .padding(top = 12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            trailingIcon = {
                                // 可点击的 Primary 色文本：不用 TextButton，避免它自带的最小尺寸把输入框撑高。
                                // clickable 写在 padding 之前，让 12×10dp 的留白也算进点击热区。
                                Text(
                                    text = when {
                                        codeCooldownSeconds > 0 -> "${codeCooldownSeconds}s"
                                        isSendingCode -> "发送中…"
                                        // 已经在填图形码时，这个按钮的语义变成「确认并发送」
                                        captchaBytes != null -> "确认发送"
                                        else -> "获取验证码"
                                    },
                                    style = MiuixTheme.textStyles.body2,
                                    color = if (sendEnabled) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        // 倒计时 / 发送中：降到次要色，明确「现在点不了」
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    },
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = sendEnabled) { sendCode() }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            },
                            colors = fieldColors,
                        )

                        // 图形验证码：服务端要求人机校验时就地展开，不弹二级弹窗。
                        // 左侧输入、右侧图片，点图片可换一张（重新发码会带回新图）。
                        if (captchaBytes != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) {
                                TextField(
                                    value = captchaInput,
                                    onValueChange = { captchaInput = it },
                                    label = "图形验证码",
                                    useLabelAsPlaceholder = true,
                                    modifier = Modifier.weight(1f).focusRequester(captchaFocus),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { sendCode() }),
                                    colors = fieldColors,
                                )
                                // 图形验证码图片：白底 PNG，按高度等比缩放；点击换一张。
                                // 解码完成前用占位底色撑住尺寸，避免图片出现时布局跳动。
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .width(100.dp)
                                        .height(52.dp)
                                        .background(MiuixTheme.colorScheme.disabledSecondary)
                                        .clickable { onSendCode(account.trim(), "") },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    captchaBitmap?.let { bitmap ->
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "图形验证码，点击换一张",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 登录失败提示：红字置于表单下方；不只靠颜色传达，附明确文案
            if (hasError) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                )
            }

            // 主操作按钮：占满宽度 + 主色；登录中内嵌旋转指示器并禁用防重复提交
            Button(
                onClick = { submit() },
                enabled = submitEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp),
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

            // 时间炸弹过期提示：仅在内测/调试版本且传入截止时间时显示
            if (buildExpiresAtMs != null) {
                val formatter = DateFormat.getDateTimeInstance()
                Text(
                    text = "本构建将于 ${formatter.format(Date(buildExpiresAtMs))} 过期",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                )
            }

            // 底部留白约屏高 1/3，整体内容略偏上、符合登录页视觉习惯
            Spacer(Modifier.weight(1.3f))
        }
    }
}
