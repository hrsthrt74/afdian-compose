package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.FavoriteItem
import com.hrsthrt74.afdian.ui.components.CreatorSmallCard
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollHorizontal

/** 收藏创作者模块。 */
@Composable
fun MarkedCreatorsModule(context: HomeModuleContentContext) {
    if (context.markedCreators.isEmpty()) return
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallTitle("收藏的创作者", modifier = Modifier.padding(start = 4.dp))
            Box(Modifier.clickable(onClick = context.onOpenFavorites, indication = null, interactionSource = remember { MutableInteractionSource() })){
                Row(
                    modifier = Modifier.padding(28.dp, 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "更多",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Icon(
                        MiuixIcons.ChevronForward, contentDescription = null, tint = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        HomeMarkedCreators(context.markedCreators, context.onOpenCreator)
        Spacer(Modifier.height(16.dp))
    }
}

/** 收藏创作者横向卡片，仅供收藏创作者模块使用，避免污染通用数据卡片文件。 */
@Composable
private fun HomeMarkedCreators(
    creators: List<FavoriteItem>,
    onOpenCreator: (AfadianUser) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(creators.take(8), key = { it.id }) { item ->
            val user = item.user ?: return@items
            val origin = rememberCreatorOrigin()
            CreatorSmallCard(
                avatarUrl = user.avatar,
                coverUrl = user.cover,
                creatorName = user.name.ifBlank { item.title.ifBlank { "创作者" } },
                onClick = origin.onClick { onOpenCreator(user) },
                modifier = origin.trackModifier().width(164.dp),
            )
        }
    }
}
