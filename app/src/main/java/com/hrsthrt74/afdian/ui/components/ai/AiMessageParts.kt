package com.hrsthrt74.afdian.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * AI 对话版式的轻零件：用户气泡、胶囊 chip、灰色提示文案。
 *
 * 这些组件原本都私有在 AiResultSheet 里，AI 记录页要「原样回看」当时的排版，
 * 所以整体放开到 `internal`，让生成时与回看时共用同一套样式——
 * 两处各写一份的话，字号/配色迟早会漂移，回看就失真了。
 *
 * 重件已拆出：思考块见 `AiThinkingBlock.kt`，Markdown 渲染见 `AiMarkdown.kt`，
 * 间距/状态卡 token 见 `AiSheetTheme.kt`。
 */

/**
 * 用户侧气泡：右对齐、圆角，里面放一枚静态 chip。
 *
 * 只用于「已经问出口的那句话」（现场生成的摘要 chip / 回看时的历史提问）；
 * 还没发车的条件选择器**不进气泡**——表单就该长得像表单，不是一条聊天消息。
 *
 * 配色全部取 Miuix 色板里明暗两套都成立的一对：
 * 气泡用 `tertiaryContainer`（浅 #EAF2FF / 深 #2B3B54），里面的 chip 用 `surfaceVariant`
 * （浅白 / 深 #242424）——两者在各自主题下都有明显层次，不依赖固定色值。
 */
@Composable
internal fun AiQueryBubble(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        // 气泡胶囊底
        Box(
            modifier = Modifier
                .clip(Capsule())
                .background(MiuixTheme.colorScheme.tertiaryContainer)
                .padding(5.dp),
        ) {
            content()
        }
    }
}

/** 气泡里的静态 chip（无时间选择的场景，如历史回看）。 */
@Composable
internal fun AiChip(text: String) {
    Box(
        modifier = Modifier
            .clip(Capsule())
            .padding(AiSheetSpacing.ChipPadding),
    ) {
        // chip 文案
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 页面里的灰色提示文案（准备数据 / 等待首包 / 未发送说明），统一样式避免各处各写一遍。
 * 默认定 body2 一档——旧实现 body1/body2 三处漂移，现只保留这一档。
 *
 * @param textAlign 对齐方式；null = 跟随默认（左对齐）。居中时调用方记得同时给 `fillMaxWidth`
 */
@Composable
internal fun HintText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MiuixTheme.textStyles.body2,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = style,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = textAlign,
        modifier = modifier,
    )
}
