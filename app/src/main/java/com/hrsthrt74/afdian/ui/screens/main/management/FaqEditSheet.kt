package com.hrsthrt74.afdian.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.FaqItem
import com.hrsthrt74.afdian.data.content.timeLabel
import com.hrsthrt74.afdian.ui.components.basic.AppDialog
import com.hrsthrt74.afdian.ui.components.basic.SaveButton
import com.hrsthrt74.afdian.ui.components.basic.rememberDialogState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** 答案正文最多行数：超出的部分在弹层内滚动，避免单个 FAQ 撑爆整屏 */
private const val FaqContentMaxLines = 9

/**
 * 新增 / 编辑自动回复弹层（/api/faq/edit-faq + /api/faq/delete-faq）。
 *
 * 字段只有「问题标题」和「自动回复内容」两项，与网页版一致；
 * 编辑模式下额外在底部提供删除按钮，破坏性操作统一走二次确认弹窗。
 *
 * @param show 是否显示
 * @param editFaq 非空表示编辑模式（列表项数据直接带入，无二次详情请求）；null 表示新增
 * @param submitting 正在提交保存
 * @param deleting 正在删除
 * @param onDismiss 关闭弹层
 * @param onSubmit 提交保存（新增或编辑），结果通过回调返回
 * @param onDelete 确认删除（仅编辑模式）
 * @param onToast 轻提示
 */
@Composable
fun FaqEditSheet(
    show: Boolean,
    editFaq: FaqItem? = null,
    submitting: Boolean,
    deleting: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (id: Int, title: String, content: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onDelete: () -> Unit = {},
    onToast: (String) -> Unit,
) {
    // 编辑模式：隐藏删除按钮走创建路径
    val isEdit = editFaq != null

    // ── 表单状态 ──
    // 问题标题（粉丝看到的提问按钮文案）
    var title by remember { mutableStateOf("") }
    // 自动回复内容（粉丝提问后服务端自动发出的答案）
    var content by remember { mutableStateOf("") }

    // 删除确认弹窗
    val deleteDialogState = rememberDialogState()

    val haptic = LocalHapticFeedback.current

    // 打开 / 切换编辑对象时初始化表单：编辑用列表项填充，新增则清空
    LaunchedEffect(show, editFaq) {
        if (!show) return@LaunchedEffect
        title = editFaq?.title.orEmpty()
        content = editFaq?.content.orEmpty()
    }

    // 点击保存：先本地校验标题与内容非空，再提交
    fun save() {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty()) {
            onToast("请填写问题标题")
            return
        }
        if (cleanContent.isEmpty()) {
            onToast("请填写自动回复内容")
            return
        }
        onSubmit(editFaq?.id ?: 0, cleanTitle, cleanContent) { success, message ->
            onToast(message)
            if (success) onDismiss()
        }
    }

    WindowBottomSheet(
        show = show,
        title = if (isEdit) "编辑自动回复" else "新增自动回复",
        onDismissRequest = onDismiss,
        // 左上角：关闭
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭")
            }
        },
        // 右上角：保存；提交中自动换成进度指示器并禁止点击（SaveButton 内部统一处理）
        endAction = {
            SaveButton(saving = submitting, onClick = { save() })
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().scrollEndHaptic(),
            contentPadding = PaddingValues(bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 问题标题：粉丝在会话页看到的提问按钮文案 ──
            item(key = "title") {
                TextField(
                    value = title,
                    onValueChange = { input -> title = input },
                    label = "问题标题",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── 自动回复内容：多行文本，限制行数后内部滚动 ──
            item(key = "content") {
                TextField(
                    value = content,
                    onValueChange = { input -> content = input },
                    label = "自动回复内容",
                    useLabelAsPlaceholder = true,
                    minLines = 3,
                    maxLines = FaqContentMaxLines,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── 时间信息（仅编辑模式）：未编辑过的条目 update_time 为全零串，只显示创建时间 ──
            if (isEdit && editFaq != null) {
                item(key = "time") {
                    Text(
                        text = editFaq.timeLabel,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // ── 删除（仅编辑模式）──
            if (isEdit) {
                item(key = "delete") {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                            deleteDialogState.show()
                        },
                        colors = ButtonDefaults.buttonColors(contentColor = MiuixTheme.colorScheme.error),
                        enabled = !deleting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // 垃圾桶图标 + 「删除」文案，与优惠码弹层的破坏性按钮同款
                        Icon(MiuixIcons.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("删除")
                    }
                }
            }
        }
    }

    // 删除确认：破坏性操作，确认按钮变红
    AppDialog(
        state = deleteDialogState,
        title = "删除自动回复",
        summary = "确定删除「${editFaq?.title.orEmpty()}」吗？删除后不可恢复。",
        confirmText = "删除",
        destructive = true,
        onConfirm = { onDelete() },
    )
}
