package com.hrsthrt74.afdian.ui.components.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 徽标胶囊的尺寸（写死，不跟随图标走 —— 原因见 [AccountSwitcherBadge] 的说明）。
 */
private val BadgeWidth = 18.dp
private val BadgeHeight = 10.dp
private val BadgeIconSize = 12.dp

/**
 * 头像右下角的「可切换账号」徽标：主色胶囊 + 横向三点。
 * 纯装饰：不挂点击，点击动作由头像本身承担，所以 `contentDescription` 传 null。
 *
 * @param modifier 外部修饰符，调用方负责 `align` / `offset` 定位
 */
@Composable
fun AccountSwitcherBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BadgeWidth, BadgeHeight)
            .clip(Capsule())
            .background(MiuixTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Demibold.More,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onTertiaryContainer,
            // More 是竖排三点（⋮），转 90° 变成横排（⋯）
            modifier = Modifier.requiredSize(BadgeIconSize).rotate(90f),
        )
    }
}
