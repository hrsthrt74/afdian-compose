package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.CreatorSmallCard
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollHorizontal

/** 已发电创作者模块。 */
@Composable
fun SponsoringModule(context: HomeModuleContentContext) {
    Column {
        ClickableSmallTitle(
            title = "我发电的",
            onClick = context.onOpenSponsoring,
        )

        if (context.sponsoring.isEmpty()) {
            if (context.loading) LoadingComponent(Modifier.padding(horizontal = 12.dp)) else Text(
                "还没有发电记录", style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
            )
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(context.sponsoring, key = { it.id }) { item ->
                    val origin = rememberCreatorOrigin()
                    CreatorSmallCard(item.user.avatar, item.user.cover, item.user.name.ifBlank { "创作者" },
                        origin.onClick { context.onOpenCreator(item.user) }, origin.trackModifier().width(164.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
