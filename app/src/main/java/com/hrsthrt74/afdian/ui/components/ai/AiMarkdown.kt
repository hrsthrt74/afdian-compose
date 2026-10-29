package com.hrsthrt74.afdian.ui.components.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.StreamingMarkdownState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * AI 正文的 Markdown 渲染：流式（生成中逐包 append）与静态（历史回看整篇）两形态。
 *
 * 两形态共用同一份配色与字号（[aiMarkdownColors] / [aiMarkdownTypography]），
 * 保证「当时看到的」和「回看时看到的」排版完全一致。
 *
 * 注意依赖用的是 m3 变体（multiplatform-markdown-renderer-m3）：
 * m2 变体会 NoClassDefFoundError 闪退，见 CONTEXT 的红线清单。
 */

/**
 * 正文（流式）：生成过程中逐包 append 的 Markdown 直接铺在页面上（不套 Card、不加头像）。
 *
 * @param mdState 流式渲染状态，由调用方按渲染代数（attempt）重建——
 *   上一轮正文绝不残留到这一轮
 */
@Composable
internal fun AiMarkdownStreaming(mdState: StreamingMarkdownState, modifier: Modifier = Modifier) {
    Markdown(
        streamingMarkdownState = mdState,
        colors = aiMarkdownColors(),
        typography = aiMarkdownTypography(),
        imageTransformer = Coil3ImageTransformerImpl,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 正文（静态整篇）：历史回看用。
 *
 * 与 [AiMarkdownStreaming] 的区别只是走非流式的 `content` 重载，不需要 StreamingMarkdownState。
 */
@Composable
internal fun AiMarkdownText(text: String, modifier: Modifier = Modifier) {
    Markdown(
        content = text,
        colors = aiMarkdownColors(),
        typography = aiMarkdownTypography(),
        imageTransformer = Coil3ImageTransformerImpl,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Markdown 配色：必须显式给 Miuix 值。
 *
 * `markdownColor` / `markdownTypography` 的默认参数读的是 `MaterialTheme`，而项目里没有
 * MaterialTheme 提供者，未覆盖的项会落到 M3 默认亮色方案上——亮色下看不出来，切暗色就是黑字黑底。
 */
@Composable
private fun aiMarkdownColors() = markdownColor(
    text = MiuixTheme.colorScheme.onSurface,
    codeBackground = MiuixTheme.colorScheme.surfaceContainer,
    inlineCodeBackground = MiuixTheme.colorScheme.surfaceContainer,
    highlightText = MiuixTheme.colorScheme.onSurface,
    highlightBackground = MiuixTheme.colorScheme.surfaceContainer,
    dividerColor = MiuixTheme.colorScheme.dividerLine,
    tableBackground = MiuixTheme.colorScheme.surfaceContainer,
)

/** Markdown 排版：标题字号压到 Miuix 的 title1–4，Markdown 默认的 M3 标题在手机宽度下偏大。 */
@Composable
private fun aiMarkdownTypography() = markdownTypography(
    h1 = MiuixTheme.textStyles.title1,
    h2 = MiuixTheme.textStyles.title2,
    h3 = MiuixTheme.textStyles.title3,
    h4 = MiuixTheme.textStyles.title4,
    text = MiuixTheme.textStyles.main,
    paragraph = MiuixTheme.textStyles.main,
    table = MiuixTheme.textStyles.footnote1,
)
