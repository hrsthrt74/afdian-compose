package com.hrsthrt74.afdian.ui.components.basic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Ok
import kotlin.time.Duration.Companion.milliseconds

/** 日志 TAG */
private const val CopyButtonLogTag = "CopyButton"

/** 剪贴板内容标签：系统「剪贴板历史」里显示的来源名 */
private const val CopyButtonClipLabel = "文本"

/** 「复制成功」图标的停留时长（毫秒），到点后自动切回复制图标 */
private const val CopyButtonCopiedDurationMs = 1400L

/** 图标切换动效时长（毫秒）：淡入淡出、缩放、模糊三者共用，保证过渡同步 */
private const val CopyButtonTransitionDurationMs = 200

/** 模糊过渡的最大半径：0.dp 表示完全清晰，此值表示过渡两端的模糊程度 */
private val CopyButtonBlurRadius = 8.dp

/**
 * 图标字重档位：与 MiuixIcons 提供的 5 档字重一一对应，由细到粗排列。
 *
 * 用法：`CopyButton(text = ..., iconWeight = MiuixIconWeight.Demibold)`。
 * 不传时取 [Regular]，与不带字重的 `MiuixIcons.Copy` 是同一个 ImageVector，行为完全一致。
 */
enum class MiuixIconWeight {
    /** 最细 */
    Light,

    /** 较细 */
    Normal,

    /** 常规（默认档） */
    Regular,

    /** 较粗 */
    Medium,

    /** 最粗 */
    Demibold,
}

/** 按字重取「复制」图标：注意 MiuixIcons.Copy 本身即 Regular 档 */
private val MiuixIconWeight.copyIcon: ImageVector
    get() = when (this) {
        MiuixIconWeight.Light -> MiuixIcons.Light.Copy
        MiuixIconWeight.Normal -> MiuixIcons.Normal.Copy
        MiuixIconWeight.Regular -> MiuixIcons.Regular.Copy
        MiuixIconWeight.Medium -> MiuixIcons.Medium.Copy
        MiuixIconWeight.Demibold -> MiuixIcons.Demibold.Copy
    }

/** 按字重取「复制成功」图标：档位必须与 [copyIcon] 一致，否则切换瞬间字重会跳变 */
private val MiuixIconWeight.okIcon: ImageVector
    get() = when (this) {
        MiuixIconWeight.Light -> MiuixIcons.Light.Ok
        MiuixIconWeight.Normal -> MiuixIcons.Normal.Ok
        MiuixIconWeight.Regular -> MiuixIcons.Regular.Ok
        MiuixIconWeight.Medium -> MiuixIcons.Medium.Ok
        MiuixIconWeight.Demibold -> MiuixIcons.Demibold.Ok
    }

/**
 * 复制按钮：把 [text] 写入系统剪贴板。
 *
 * 交互链路（点击 → 震动 → 复制 → 反馈）：
 * 1. 立刻触发一次触觉反馈（不等复制结果，保证手感跟手）；
 * 2. 写入剪贴板，失败（部分 ROM 会抛异常）时只记日志、不改图标、不弹 Toast；
 * 3. 复制成功后用 [AnimatedContent] 把图标从「复制」切换成「成功」，
 *    过渡为「淡入淡出 + 轻微缩放 + 模糊收敛」，同时弹一个 Toast；
 * 4. [CopyButtonCopiedDurationMs] 之后图标自动切回「复制」，可重复点击。
 *
 * 使用示例：
 * ```kotlin
 * CopyButton(text = "邀请码 ABC123", modifier = Modifier)
 * CopyButton(text = "邀请码 ABC123", iconWeight = MiuixIconWeight.Demibold)
 * ```
 *
 * @param text 要复制的文本（纯文本）
 * @param modifier 外部修饰符
 * @param enabled 是否可点击；false 时既不震动也不写剪贴板
 * @param toastText 复制成功后的 Toast 文案
 * @param iconWeight 图标字重，默认 [MiuixIconWeight.Regular]（等同不带字重的 MiuixIcons.Copy）
 * @param contentDescription 无障碍描述（进入「已复制」态时会自动附加状态后缀）
 */
@Composable
fun CopyButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    toastText: String = "复制成功",
    iconWeight: MiuixIconWeight = MiuixIconWeight.Regular,
    contentDescription: String = "复制",
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 是否处于「复制成功」态：驱动 Copy → Ok 的图标切换，同时也是自动复位的计时开关
    var copied by remember { mutableStateOf(false) }

    // 成功图标只停留一小段时间：key 用 copied，连点时旧协程会被取消并重新计时
    LaunchedEffect(copied) {
        if (copied) {
            delay(CopyButtonCopiedDurationMs.milliseconds)
            copied = false
        }
    }

    IconButton(
        onClick = {
            // 1. 先震动：点击反馈不等待复制结果，避免体感延迟
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            // 2. 写入系统剪贴板；setPrimaryClip 在部分机型/超大文本下会抛异常，
            //    这里兜住异常，避免直接崩到顶层
            val copiedOk = runCatching {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(CopyButtonClipLabel, text))
            }.onFailure { throwable ->
                AppLog.w(CopyButtonLogTag, "写入剪贴板失败", throwable)
            }.isSuccess

            // 3. 仅复制成功才做反馈：切图标（附带模糊过渡）+ 弹 Toast
            if (copiedOk) {
                AppLog.d(CopyButtonLogTag, "复制成功（长度=${text.length}）")
                copied = true
                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier,
        enabled = enabled,
    ) {
        // 图标区：Copy 与 Ok 之间做内容切换动画
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                // 主体动效：新图标淡入并轻微放大，旧图标淡出并轻微缩小
                val enter = fadeIn(tween(CopyButtonTransitionDurationMs)) +
                    scaleIn(
                        initialScale = 0.5f,
                        animationSpec = tween(CopyButtonTransitionDurationMs),
                    )
                val exit = fadeOut(tween(CopyButtonTransitionDurationMs)) +
                    scaleOut(
                        targetScale = 0.5f,
                        animationSpec = tween(CopyButtonTransitionDurationMs),
                    )
                // 关掉尺寸过渡的裁切：默认 clip = true 会把模糊溢出的像素裁掉
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.Center,
            label = "copyButtonIcon",
        ) { isCopied ->
            // 模糊过渡：借 AnimatedContent 内容作用域里的 transition 取值。
            // 进入的内容初始态是 PreEnter、目标态是 Visible，因此取值路径为「模糊 → 清晰」；
            // 退出的内容由 Visible 转 PostExit，取值路径自然反过来「清晰 → 模糊」。
            val blurRadius by transition.animateDp(
                transitionSpec = { tween(CopyButtonTransitionDurationMs) },
                label = "copyButtonIconBlur",
            ) { state -> if (state == EnterExitState.Visible) 0.dp else CopyButtonBlurRadius }

            Icon(
                // Copy 与 Ok 都按 iconWeight 取同一档字重，切换时不会出现粗细跳变
                imageVector = if (isCopied) iconWeight.okIcon else iconWeight.copyIcon,
                contentDescription = if (isCopied) "$contentDescription（成功）" else contentDescription,
                // Unbounded：图标带透明像素，模糊结果不裁切、边缘自然淡出
                modifier = Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded),
            )
        }
    }
}
