package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.FavoriteItem
import com.hrsthrt74.afdian.ui.components.basic.CreatorSmallCard
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin

/** 收藏创作者模块。 */
@Composable
fun MarkedCreatorsModule(context: HomeModuleContentContext) {
    if (context.markedCreators.isEmpty()) return
    Column {
        ClickableSmallTitle(
            title = "收藏的创作者",
            onClick = context.onOpenFavorites,
        )
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
