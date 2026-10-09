package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok

/** 提交中进度圈的尺寸：与 ✓ 图标同为 24dp，切换时按钮宽度不跳 */
private val SaveButtonProgressSize = 24.dp

/**
 * 带提交态的「保存 / 确定」图标按钮：非提交态是 ✓ 图标，提交中换成无限进度圈。
 *
 * 专供 `WindowBottomSheet` 的 `endAction` 槽位使用（也是各弹层保存交互的统一实现）：
 * 提交中按钮自动不可点，避免「点一下没反应又连点几下发好几个请求」。
 *
 * 状态归属：是否提交中由调用方传入（一般接 ViewModel 的 submitting/saving），
 * 组件自身无状态。
 *
 * 使用示例：
 * ```kotlin
 * WindowBottomSheet(
 *     show = show,
 *     title = "编辑排序",
 *     onDismissRequest = onDismiss,
 *     startAction = { IconButton(onClick = onDismiss) { Icon(MiuixIcons.Close, "关闭") } },
 *     endAction = { SaveButton(saving = saving, onClick = { save() }) },
 * ) { ... }
 * ```
 *
 * @param saving 是否正在提交；true 时显示进度圈并吞掉点击（不置灰，保持「正在进行」的观感）
 * @param onClick 点击回调，触发提交
 * @param modifier 外部修饰符
 * @param enabled 是否可点；与 [saving] 共同决定最终可用性（[saving] 为 true 时一定不可点）
 * @param contentDescription 无障碍描述（仅 ✓ 档有，进度圈档由指示器自身播报「进行中」）
 */
@Composable
fun SaveButton(
    saving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = "保存",
) {
    // 点击触感反馈：先震动再回调，调用方无需各自处理
    val haptic = LocalHapticFeedback.current

    IconButton(
        onClick = {
            // 先给一次长按震动反馈，让用户确认「点到了」
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier,
        // 提交中一律不可点：调用方不必自己再判一次 saving
        enabled = enabled && !saving,
    ) {
        AnimatedContent(targetState = saving, label = "saveButtonContent") { isSaving ->
            if (isSaving) {
                // 提交中：无限进度圈替代图标，尺寸与图标一致，宽度不跳
                InfiniteProgressIndicator(modifier = Modifier.size(SaveButtonProgressSize))
            } else {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = contentDescription,
                )
            }
        }
    }
}
