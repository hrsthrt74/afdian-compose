package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.AppLog
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 日志 TAG */
private const val CardActionBarLogTag = "CardActionBar"

/** 单列的纵向内边距：padding 放在 clickable 之后，内边距计入点击热区，窄列也有足够的可点高度 */
private val CardActionBarColumnVerticalPadding = 14.dp

/** 动作图标边长 */
private val CardActionBarIconSize = 18.dp

/** 图标与文字的间距 */
private val CardActionBarIconLabelGap = 4.dp

/** 内置分割线的左右内边距，与卡片内容区对齐 */
private val CardActionBarDividerHorizontalPadding = 16.dp

/**
 * 操作栏单个动作条目。
 *
 * @param icon 动作图标
 * @param label 显示文本，动作词（「编辑」）或计数（「12」）均可
 * @param tint 图标与文字颜色；null 时用次要色 onSurfaceVariantSummary，
 *   激活态（如已点赞、链接已就绪）由调用方显式传 `MiuixTheme.colorScheme.primary`
 * @param contentDescription 图标的无障碍描述；null 时用 [label]
 *   （label 为纯计数时建议显式传入动作词，如「点赞」）
 * @param onClick 点击回调；null 表示纯展示不可点（如评论列、未就绪的禁用列），
 *   视觉与未激活一致（次要色），也不触发触觉
 */
data class CardActionBarItem(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val contentDescription: String? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * 卡片底部操作栏：N 个动作均分一行，每列为「图标 + 文字」居中排列，
 * 点击时先震动再执行动作。
 *
 * 统一了此前散落在 PostActionBar（动态卡片三列）、AffiliateCard（编辑/生成链接）、
 * ApiProbeCard（刷新/详情）的三套手写实现；需要点赞/收藏业务状态时
 * 请继续用 `ui/components/post/PostActionBar.kt`，它内部委托本组件渲染。
 *
 * 约定：
 * - 颜色只有两档——未激活/禁用一律次要色（tint 传 null），激活态显式传 primary；
 * - 不可点与禁用统一用 `onClick = null` 表达，视觉与未激活一致（次要色）；
 * - 触觉内置（先震后动），调用方回调里不要再做 HapticFeedback，否则会双重震动。
 *
 * 使用示例：
 * ```kotlin
 * CardActionBar(
 *     items = listOf(
 *         CardActionBarItem(MiuixIcons.Demibold.Edit, "编辑", onClick = onEdit),
 *         CardActionBarItem(
 *             icon = MiuixIcons.Demibold.Link,
 *             label = "生成链接",
 *             tint = link?.let { MiuixTheme.colorScheme.primary },
 *             onClick = link?.let { url -> { onGenerateLink(url) } },
 *         ),
 *     ),
 *     showDivider = true,
 * )
 * ```
 *
 * @param items 动作条目，按顺序从左到右均分整行宽度
 * @param modifier 外部修饰符
 * @param showDivider 是否在操作栏上方画一条分割线（左右 16dp、紧贴操作栏顶边，
 *   颜色用 Miuix 默认分隔线色）；与上方内容之间的间隔由调用方自己留
 */
@Composable
fun CardActionBar(
    items: List<CardActionBarItem>,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    // 触觉封装在组件内部：所有可点列共用同一份「先震后动」手感，调用方回调无需再处理
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier) {
        // 可选分割线：无纵向 padding，直接贴住操作栏顶边
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = CardActionBarDividerHorizontalPadding),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                // 先取局部变量：val 属性跨智能转换不稳，局部 val 才能安全地判定可空回调
                val action = item.onClick
                // ── 每个动作占一个等宽列，内容居中 ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // 纯展示列（action == null）不加 clickable，与旧实现（评论列）行为一致
                        .then(
                            if (action != null) {
                                Modifier.clickable(onClick = {
                                    // 点击链路：记日志 → 先震动（手感跟手，不等动作结果）→ 执行动作
                                    AppLog.d(CardActionBarLogTag, "操作栏点击: ${item.label}")
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    action()
                                })
                            } else {
                                Modifier
                            },
                        )
                        // padding 放在 clickable 之后：内边距也算进点击热区
                        .padding(vertical = CardActionBarColumnVerticalPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 动作图标
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.contentDescription ?: item.label,
                            modifier = Modifier.size(CardActionBarIconSize),
                            tint = item.tint ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.width(CardActionBarIconLabelGap))
                        // 动作文字（或计数）
                        Text(
                            text = item.label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = item.tint ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}
