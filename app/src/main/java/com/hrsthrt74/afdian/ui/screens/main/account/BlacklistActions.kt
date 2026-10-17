package com.hrsthrt74.afdian.ui.screens.main.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.BlackedType
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.DialogState
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「加入屏蔽列表 / 加入拒绝列表」操作区：分割线 + 竖排两个按钮。
 *
 * 私信会话页的赞助详情弹层与赞助者管理的详情弹层共用同一实现，
 * 保证两处的按钮文案、间距、防连点行为保持一致。
 *
 * @param blackPending 是否正在提交屏蔽/拒绝请求，为 true 时禁用按钮防连点
 * @param onBlackRequest 点击加入按钮（参数为名单类型），由调用方弹确认弹窗
 */
@Composable
internal fun BlackActionsContent(
    blackPending: Boolean,
    onBlackRequest: (BlackedType) -> Unit,
) {
    // 分割线：与上方内容区分隔（边距对齐两个分区之间的分割线样式）
    HorizontalDivider(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .padding(top = 16.dp, bottom = 4.dp),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(
            text = "加入屏蔽列表",
            enabled = !blackPending,
            onClick = { onBlackRequest(BlackedType.Block) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            text = "加入拒绝列表",
            enabled = !blackPending,
            onClick = { onBlackRequest(BlackedType.Reject) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 加入屏蔽 / 拒绝名单的确认弹窗（破坏性：确认按钮变红）。
 *
 * 标题、确认文案与效果说明按 [type] 切换；说明文字与屏蔽页顶部的说明卡片共用同一份数据
 * （不含「长按卡片可移除」——那是名单页的操作提示）。拒绝类型额外渲染
 * 「如需取消权益，请联系管理员 @Lain音酱」，其中「@Lain音酱」可点进入客服会话。
 *
 * @param state 弹窗显示状态（由 rememberDialogState 创建）
 * @param type 待确认的名单类型；关闭动画期间保持非空，避免标题闪空
 * @param onDismissed 关闭动画完成后的清理入口（调用方在此清空 pending 状态）
 * @param onConfirm 点击确认（参数为当前名单类型；AppDialog 随后自动关闭）
 * @param onOpenCustomerService 点击「@Lain音酱」链接的回调
 */
@Composable
internal fun BlackRequestDialog(
    state: DialogState,
    type: BlackedType?,
    onDismissed: () -> Unit,
    onConfirm: (BlackedType) -> Unit,
    onOpenCustomerService: () -> Unit,
) {
    AppDialog(
        state = state,
        title = type?.let {
            if (it == BlackedType.Block) "加入屏蔽列表" else "加入拒绝列表"
        }.orEmpty(),
        confirmText = type?.let {
            if (it == BlackedType.Block) "加入屏蔽" else "加入拒绝"
        } ?: "确认",
        destructive = true,
        onDismissed = onDismissed,
        // 只有 type 非空时才允许提交，避免关闭动画期间误触空类型
        onConfirm = { type?.let(onConfirm) },
    ) {
        Column(
            // 底部留 12dp：说明文字与「取消 / 加入」按钮区拉开距离（AppDialog 自身还有 4dp 间隔）
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 效果说明：按动作类型取对应文案
            val introLines = if (type == BlackedType.Reject) REJECT_INTRO_LINES else BLOCK_INTRO_LINES
            introLines.forEach { line ->
                Text(
                    text = "• $line",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            // 拒绝弹窗多一条客服指引：「@Lain音酱」可点进入客服会话
            if (type == BlackedType.Reject) {
                Text(
                    text = AnnotatedString("• ") + customerServiceContactText(
                        linkColor = MiuixTheme.colorScheme.primary,
                        onOpenCustomerService = onOpenCustomerService,
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
