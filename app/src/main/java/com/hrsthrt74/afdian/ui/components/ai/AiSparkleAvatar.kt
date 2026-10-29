package com.hrsthrt74.afdian.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * AI 的身份标识：圆形底 + sparkle 图标。
 *
 * 用在两处，且必须是同一个组件：
 * - 消息页里那条虚拟会话「AI 记录」的头像位（[AiHistoryAvatarSize]）；
 * - AI 记录页里每一条记录左侧的图标块（[AiEntryIconSize]，缩在卡片内所以小一档）。
 *
 * 抽出来的理由很实际：这两处原本各写了一份 Box + clip + background + Icon，
 * 改配色或形状时漏掉一处，就会出现「列表里一个样、卡片上另一个样」的分裂。
 * 现在配色只在这里定义一次（第三容器 / 其上内容，与 Sheet 里的 AI 摘要 chip 同一档），
 * 形状也只有一个来源——外面是圆形，里面就跟着圆形。
 *
 * 图标大小按边长的一半推导，所以 48dp 出 24dp 图标、40dp 出 20dp，两处观感同源。
 *
 * @param size 直径；图标大小按它等比推导
 */
@Composable
fun AiSparkleAvatar(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_ai_sparkle),
            // 装饰性图标：旁边的文字已经说明了这是「AI 记录」，读屏不必重复
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** 消息页「AI 记录」会话头像的直径（与真人头像一致）。 */
val AiHistoryAvatarSize: Dp = 48.dp

/** AI 记录页列表条目里图标块的直径（缩在卡片内，比会话头像小一档）。 */
val AiEntryIconSize: Dp = 40.dp
