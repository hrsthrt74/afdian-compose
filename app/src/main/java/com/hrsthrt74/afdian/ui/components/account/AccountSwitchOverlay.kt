package com.hrsthrt74.afdian.ui.components.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.auth.AccountSwitchPhase
import com.hrsthrt74.afdian.data.auth.SavedAccountSummary
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 切号遮罩：半透明 dim + 居中卡片，整个切换期间不可取消。
 *
 * 用 [WindowDialog]（独立窗口）而不是 Compose 内联遮罩：切换时整棵已登录子树会按 epoch
 * 重建，内联遮罩会跟着一起被销毁；独立窗口不依赖页面组合，能稳定盖住重建过程，
 * 也能压住账号菜单这类 Popup 层。
 *
 * 显隐完全由 [phase] 驱动，`onDismissRequest` 传空实现：
 * 点遮罩、按返回键都不会关闭，只有新会话首屏就绪或超时兜底才会消失。
 */
@Composable
fun AccountSwitchOverlay(phase: AccountSwitchPhase) {
    // 始终渲染、由 show 驱动显隐，这样关窗动画能正常播放
    val visible = phase !is AccountSwitchPhase.Idle
    val target: SavedAccountSummary? = when (phase) {
        is AccountSwitchPhase.Preparing -> phase.target
        is AccountSwitchPhase.Waiting -> phase.target
        AccountSwitchPhase.Idle -> null
    }

    WindowDialog(
        show = visible,
        onDismissRequest = { /* 空实现：切换中禁止任何方式关闭 */ },
        largeScreen = true,
    ) {
        if (target != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 目标账号头像：让用户确认「切的是哪个号」
                Avatar(
                    model = target.avatar,
                    size = 56.dp,
                    contentDescription = "目标账号头像",
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = target.name.ifBlank { "爱发电用户" },
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 与登录按钮内同款进度指示器，视觉语言一致
                    InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (phase is AccountSwitchPhase.Waiting) {
                            "正在加载账号数据…"
                        } else {
                            "正在切换账号…"
                        },
                        style = MiuixTheme.textStyles.body2,
//                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        color = Color.Gray,
                    )
                }
            }
        }
    }
}
