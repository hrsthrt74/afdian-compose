package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一的加载中组件
 *
 * 用于显示异步加载状态，使用 InfiniteProgressIndicator 动画。
 * 文本显示在进度条右面，支持自定义尺寸。
 *
 * @param modifier 修饰符
 * @param text 加载文本，默认为"加载中..."
 * @param showText 是否显示文本，默认为true
 * @param size 指示器尺寸，默认为40dp（大尺寸）
 * @param textStyle 文本样式，默认使用 Miuix 主题 body2 样式
 * @param fillMaxWidth 是否填充最大宽度，默认为false
 */
@Composable
fun LoadingComponent(
    modifier: Modifier = Modifier,
    text: String = "加载中...",
    showText: Boolean = true,
    size: Dp = 40.dp,
    textStyle: TextStyle = MiuixTheme.textStyles.body2,
    fillMaxWidth: Boolean = true,
) {
    // 使用 Row 水平布局，垂直居中对齐
    Row(
        modifier = modifier.then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // 使用 Miuix 默认颜色的无限旋转指示器
        InfiniteProgressIndicator(
            modifier = Modifier.size(size),
        )

        // 是否显示文本
        if (showText) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = textStyle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * LoadingComponent 预览
 */
@Preview(showBackground = true)
@Composable
private fun LoadingComponentPreview() {
    MiuixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 默认配置
            LoadingComponent()

            // 自定义文本
            LoadingComponent(text = "正在加载数据...")

            // 小尺寸，不显示文本
            LoadingComponent(size = 24.dp, showText = false)

            // 小尺寸，显示文本
            LoadingComponent(size = 24.dp, text = "请稍候")

            // 填充最大宽度
            LoadingComponent(fillMaxWidth = true)
        }
    }
}