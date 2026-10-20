package com.hrsthrt74.afdian.ui.components.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 圆形头像组件
 *
 * 自动将图片居中裁剪为正方形，并显示为圆形。
 * 支持自定义大小、占位符、错误状态和点击事件。
 *
 * @param model 图片URL、Uri或Coil支持的任意数据源
 * @param size 头像尺寸（宽高相等），默认48dp
 * @param contentDescription 无障碍描述
 * @param onClick 点击回调，传null则不可点击
 * @param onLongClick 长按回调（如「我的」页长按头像打开账号切换）；传 null 时只保留点击
 * @param modifier 修饰符
 */
@Composable
fun Avatar(
    model: Any?,
    size: Dp = 48.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 有长按需求时必须走 combinedClickable：普通 clickable 根本收不到长按事件
    val clickableModifier = when {
        onLongClick != null -> Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }

    // Box 负责圆形裁剪、背景填充和点击涟漪，图片只管填充
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.disabledSecondary)
            .then(clickableModifier),
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}