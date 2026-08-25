package com.hrsthrt74.afdian.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.ui.components.CreatorSmallCard
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.navigation.rememberCreatorOrigin
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 已发电创作者模块。 */
@Composable
fun SponsoringModule(context: HomeModuleContentContext) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallTitle("我发电的", modifier = Modifier.padding(start = 4.dp))
            Box(Modifier.clickable(onClick = context.onOpenSponsoring, indication = null, interactionSource = remember { MutableInteractionSource() })){
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

        if (context.sponsoring.isEmpty()) {
            if (context.loading) LoadingComponent(Modifier.padding(horizontal = 12.dp)) else Text(
                "还没有发电记录", style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
            )
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(context.sponsoring, key = { it.id }) { item ->
                    val origin = rememberCreatorOrigin()
                    CreatorSmallCard(item.user.avatar, item.user.cover, item.user.name.ifBlank { "创作者" },
                        origin.onClick { context.onOpenCreator(item.user) }, origin.trackModifier().width(156.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
