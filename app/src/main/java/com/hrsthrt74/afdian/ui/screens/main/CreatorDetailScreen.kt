package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.CreatorDetail
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class CreatorSection { Plans, Products, Goals, Sponsors, Albums, Posts }

@Composable
fun CreatorDetailScreen(user: AfadianUser, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: CreatorDetailViewModel = viewModel(
        key = "creator-${user.user_id}",
        factory = remember(user.user_id) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CreatorDetailViewModel(app, user) as T
            }
        }
    )
    val detail by vm.detail.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val isMarking by vm.isMarking.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)

    val title = detail?.profile?.name ?: user.name.ifBlank { "创作者主页" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = vm::toggleMark, enabled = !isMarking) {
                            Icon(
                                imageVector = if (detail!!.isMarked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                contentDescription = if (detail!!.isMarked) "取消收藏" else "收藏"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            val current = detail
            when {
                current == null && loading -> Text(
                    "正在加载创作者主页…",
                    modifier = Modifier.padding(top = paddingValues.calculateTopPadding() + 32.dp, start = 20.dp)
                )
                current == null && error != null -> Column(
                    modifier = Modifier.padding(top = paddingValues.calculateTopPadding() + 32.dp, start = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(error!!)
                    Button(onClick = vm::load) { Text("重试") }
                }
                current != null -> CreatorDetailContent(
                    detail = current,
                    user = user,
                    listState = listState,
                    scrollBehavior = scrollBehavior,
                    topPadding = paddingValues.calculateTopPadding(),
                    onSponsor = {
                        val plansIndex = current.sections().indexOf(CreatorSection.Plans)
                        val productsIndex = current.sections().indexOf(CreatorSection.Products)
                        val target = if (plansIndex >= 0) plansIndex else productsIndex
                        if (target >= 0) coroutineScope.launch { listState.animateScrollToItem(1 + target) }
                    }
                )
                else -> Text(
                    "暂无内容",
                    modifier = Modifier.padding(top = paddingValues.calculateTopPadding() + 32.dp, start = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun CreatorDetailContent(
    detail: CreatorDetail,
    user: AfadianUser,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: ScrollBehavior,
    topPadding: Dp,
    onSponsor: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = topPadding + 12.dp,
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp
        ),
    ) {
        item(key = "header") {
            CreatorHeader(detail = detail, fallback = user, onSponsor = onSponsor)
        }
        val sections = detail.sections()
        sections.forEachIndexed { index, section ->
            item(key = section.name) {
                when (section) {
                    CreatorSection.Plans -> CreatorPlansSection(detail)
                    CreatorSection.Products -> CreatorProductsSection(detail)
                    CreatorSection.Goals -> CreatorGoalsSection(detail)
                    CreatorSection.Sponsors -> CreatorSponsorsSection(detail)
                    CreatorSection.Albums -> CreatorAlbumsSection(detail)
                    CreatorSection.Posts -> CreatorPostsSection(detail)
                }
            }
        }
    }
}

private fun CreatorDetail.sections(): List<CreatorSection> = buildList {
    if (plans.isNotEmpty()) add(CreatorSection.Plans)
    if (products.isNotEmpty()) add(CreatorSection.Products)
    if (goals.isNotEmpty()) add(CreatorSection.Goals)
    if (topSponsors.isNotEmpty()) add(CreatorSection.Sponsors)
    if (albums.isNotEmpty()) add(CreatorSection.Albums)
    if (posts.isNotEmpty()) add(CreatorSection.Posts)
}

@Composable
private fun CreatorHeader(detail: CreatorDetail, fallback: AfadianUser, onSponsor: () -> Unit) {
    val profile = detail.profile
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!profile.cover.isNullOrBlank()) {
            AsyncImage(
                model = profile.cover,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row {
            AsyncImage(
                model = profile.avatar ?: fallback.avatar,
                contentDescription = profile.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.name.ifBlank { "创作者" })
                val category = profile.creator?.category?.name
                if (!category.isNullOrBlank()) {
                    Text(category, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if ((profile.is_verified ?: 0) == 1) {
                    Text("已认证", color = MiuixTheme.colorScheme.primary)
                }
            }
        }
        val doing = profile.creator?.doing
        if (!doing.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(doing)
        }
        val intro = profile.creator?.detail
        if (!intro.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(intro, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSponsor, modifier = Modifier.fillMaxWidth()) {
            Text("为 Ta 发电")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CreatorPlansSection(detail: CreatorDetail) {
    SmallTitle(text = "发电方案")
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        detail.plans.forEach { plan ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = plan.pic,
                    contentDescription = null,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(plan.displayName)
                    Text("¥${plan.priceText}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable
private fun CreatorProductsSection(detail: CreatorDetail) {
    SmallTitle(text = "商品")
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        detail.products.forEach { product ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = product.pic,
                    contentDescription = null,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(product.displayName)
                    Text("¥${product.priceText}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable
private fun CreatorGoalsSection(detail: CreatorDetail) {
    SmallTitle(text = "目标")
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        detail.goals.forEach { goal ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(goal.displayTitle)
                if (goal.amountText.isNotBlank()) {
                    Text(goal.amountText, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable
private fun CreatorSponsorsSection(detail: CreatorDetail) {
    SmallTitle(text = "赞助者")
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        detail.topSponsors.forEach { sponsor ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = sponsor.avatarUrl,
                    contentDescription = sponsor.displayName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(sponsor.displayName)
                    Text("累计 ¥${sponsor.totalAmountText}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable
private fun CreatorAlbumsSection(detail: CreatorDetail) {
    SmallTitle(text = "合集")
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        detail.albums.forEach { album ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = album.cover,
                    contentDescription = null,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(album.displayTitle)
                    if (album.postCountText.isNotBlank()) {
                        Text(album.postCountText, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorPostsSection(detail: CreatorDetail) {
    SmallTitle(text = "动态")
    detail.posts.forEach { post ->
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(post.title, maxLines = 2)
                if (post.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(post.summary, maxLines = 3, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "点赞 ${post.like_count ?: 0}   评论 ${post.comment_count ?: 0}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}