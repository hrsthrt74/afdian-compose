package com.hrsthrt74.afdian.ui.screens.main

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.hrsthrt74.afdian.data.content.AfadianUser
import com.hrsthrt74.afdian.data.content.CreatorDetail
import com.hrsthrt74.afdian.ui.components.Avatar
import com.hrsthrt74.afdian.ui.components.LoadingComponent
import com.hrsthrt74.afdian.ui.components.PostCard
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
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.hrsthrt74.afdian.ui.viewmodel.CreatorDetailViewModel
import com.hrsthrt74.afdian.ui.components.BlurredBar
import com.hrsthrt74.afdian.ui.components.blurBarColor
import com.hrsthrt74.afdian.ui.components.contentBottomPadding
import com.hrsthrt74.afdian.ui.components.rememberBlurBackdrop
import com.hrsthrt74.afdian.ui.theme.LocalThemeSettings
import top.yukonga.miuix.kmp.blur.layerBackdrop
// Miuix 提供的「滚动到边界触觉反馈」修饰符：列表快速滑动到顶/底边界时震动一下
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private enum class CreatorSection { Plans, Products, Albums, Posts, Sponsors }

@Composable
fun CreatorDetailScreen(user: AfadianUser, onBack: () -> Unit, onOpenPost: (com.hrsthrt74.afdian.data.content.RecommendedPost) -> Unit = {}) {
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
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.enableBlur)

    val title = detail?.profile?.name ?: user.name.ifBlank { "创作者主页" }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, themeSettings.enableBlur, themeSettings.progressiveBlur) {
            TopAppBar(
                title = title,
                color = blurBarColor(backdrop),
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
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            val current = detail
            when {
                current == null && loading -> LoadingComponent(
                    modifier = Modifier.padding(top = paddingValues.calculateTopPadding() + 32.dp, start = 12.dp)
                )
                current == null && error != null -> Column(
                    modifier = Modifier.padding(top = paddingValues.calculateTopPadding() + 32.dp, start = 12.dp),
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
                    onOpenPost = onOpenPost,
                    onSponsor = {
                        val plansIndex = current.sections().indexOf(CreatorSection.Plans)
                        val productsIndex = current.sections().indexOf(CreatorSection.Products)
                        val target = if (plansIndex >= 0) plansIndex else productsIndex
                        if (target >= 0) coroutineScope.launch { listState.animateScrollToItem(1 + target) }
                    }
                )
                else -> Text(
                    "暂无内容",
                    modifier = Modifier.padding(top = paddingValues.calculateTopPadding() + 32.dp, start = 12.dp)
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
    onOpenPost: (com.hrsthrt74.afdian.data.content.RecommendedPost) -> Unit,
    onSponsor: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // 放在 nestedScroll 之后（最贴近列表），fling 到边界的剩余速度最先被它捕获
            .scrollEndHaptic(),
        contentPadding = PaddingValues(
            top = topPadding,
                     bottom = contentBottomPadding()
        )
    ) {
        item(key = "header") {
            CreatorHeader(detail = detail, fallback = user)
        }
        val sections = detail.sections()
        sections.forEachIndexed { index, section ->
            item(key = section.name) {
                when (section) {
                    CreatorSection.Plans -> CreatorPlansSection(detail)
                    CreatorSection.Products -> CreatorProductsSection(detail)
                    CreatorSection.Albums -> CreatorAlbumsSection(detail)
                    CreatorSection.Posts -> CreatorPostsSection(detail, onOpenPost = onOpenPost)
                    CreatorSection.Sponsors -> CreatorSponsorsSection(detail)
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

private fun CreatorDetail.sections(): List<CreatorSection> = buildList {
    if (plans.isNotEmpty()) add(CreatorSection.Plans)
    if (products.isNotEmpty()) add(CreatorSection.Products)
    if (albums.isNotEmpty()) add(CreatorSection.Albums)
    if (posts.isNotEmpty()) add(CreatorSection.Posts)
    if (topSponsors.isNotEmpty()) add(CreatorSection.Sponsors)
}

@Composable
private fun CreatorHeader(detail: CreatorDetail, fallback: AfadianUser) {
    val profile = detail.profile
    Column(modifier = Modifier.fillMaxWidth()) {
        // 头图
        if (!profile.cover.isNullOrBlank()) {
            AsyncImage(
                model = profile.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .aspectRatio(2f)
            )
        }
        // 信息
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            // 头像
            Avatar(
                model = profile.avatar ?: fallback.avatar,
                size = 48.dp,
                contentDescription = profile.name,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            // 创作者信息
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                // 名字
                Text(profile.name.ifBlank { "创作者" })

                // 正在创作
                val doing = profile.creator?.doing
                if (!doing.isNullOrBlank()) {
                    Text(
                        text = "正在创作 ${doing}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }

//        Button(onClick = onSponsor, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
//            Text("为 Ta 发电")
//        }
//
//        SmallTitle(text = "简介", modifier = Modifier.padding(start = 4.dp))

        val intro = profile.creator?.detail
        if (!intro.isNullOrBlank()) {
            CollapsibleText(
                text = intro,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun CollapsibleText(
    text: String,
    modifier: Modifier = Modifier,
    // 最大行数
    collapsedMaxLines: Int = 7,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var isOverflow by rememberSaveable { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.animateContentSize(
                alignment = Alignment.TopStart
            )
        ) {
            // 折叠/展开文本切换（独立 Composable 避免 ColumnScope 冲突）
            Box( Modifier.padding(16.dp) ) {
                CollapsibleTextContent(
                    text = text,
                    expanded = expanded,
                    collapsedMaxLines = collapsedMaxLines,
                    onOverflowChanged = { isOverflow = it },
                )
            }


            // 展开/收起按钮
            AnimatedVisibility(visible = isOverflow) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (expanded) "收起" else "展开全部",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 折叠/展开文本内容：两个文本叠在 Box 里，AnimatedVisibility 淡入淡出切换，
 * 外层 animateContentSize 驱动高度平滑变化。
 */
@Composable
private fun CollapsibleTextContent(
    text: String,
    expanded: Boolean,
    collapsedMaxLines: Int,
    onOverflowChanged: (Boolean) -> Unit,
) {
    Box {
        // 折叠文本：展开时淡出
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layoutResult ->
                    val lastVisibleLine = (collapsedMaxLines - 1)
                        .coerceAtMost(layoutResult.lineCount - 1)
                    val overflow = if (lastVisibleLine >= 0) {
                        layoutResult.isLineEllipsized(lastVisibleLine)
                    } else {
                        false
                    }
                    onOverflowChanged(overflow)
                },
            )
        }

        // 展开文本：折叠时淡入
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun CreatorPlansSection(detail: CreatorDetail) {
    SmallTitle(text = "订阅方案", modifier = Modifier.padding(start = 4.dp))
    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        detail.plans.forEach { plan ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!plan.pic.isNullOrBlank()) {
                    AsyncImage(
                        model = plan.pic,
                        contentDescription = plan.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .squircleClip(cornerRadius = 8.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .squircleClip(cornerRadius = 8.dp)
                            .background(MiuixTheme.colorScheme.disabledSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Demibold.Favorites,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column( verticalArrangement = Arrangement.spacedBy(2.dp) ) {
                    Text(plan.displayName)
                    Text("¥${plan.priceText}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CreatorProductsSection(detail: CreatorDetail) {

    SmallTitle(text = "商品", modifier = Modifier.padding(start = 4.dp))

    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {

        Spacer(Modifier.height(8.dp))

        detail.products.forEach { product ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 商品图
                AsyncImage(
                    model = product.pic,
                    contentDescription = product.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .squircleClip(cornerRadius = 8.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column( verticalArrangement = Arrangement.spacedBy(2.dp) ) {
                    Text(product.displayName)
                    Text("¥${product.priceText}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CreatorSponsorsSection(detail: CreatorDetail) {
    SmallTitle(text = "赞助者", modifier = Modifier.padding(start = 4.dp))
    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        detail.topSponsors.forEach { sponsor ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Avatar(
                    model = sponsor.avatarUrl,
                    size = 48.dp,
                    contentDescription = sponsor.displayName,
                )
                Text(sponsor.displayName)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CreatorAlbumsSection(detail: CreatorDetail) {
    SmallTitle(text = "合集", modifier = Modifier.padding(start = 4.dp))
    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        detail.albums.forEach { album ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = album.cover,
                    contentDescription = album.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .squircleClip(cornerRadius = 8.dp)
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
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CreatorPostsSection(detail: CreatorDetail, onOpenPost: (com.hrsthrt74.afdian.data.content.RecommendedPost) -> Unit) {
    // 把该创作者动态里的服务端点赞状态（has_like）播种进全局互动状态，
    // 否则这里看到的「已赞」高亮会不准（操作栏读的是全局单例）
    val context = androidx.compose.ui.platform.LocalContext.current
    val interactions = com.hrsthrt74.afdian.ui.viewmodel.PostInteractionController.get(context)
    androidx.compose.runtime.LaunchedEffect(detail.posts) {
        interactions.seedLiked(detail.posts.filter { it.has_like == 1 }.map { it.id }.toSet())
    }
    SmallTitle(text = "动态", modifier = Modifier.padding(start = 4.dp))
    detail.posts.forEach { post ->
        PostCard(
            post = post,
            profile = detail.profile,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            onClick = { onOpenPost(post) },
        )
    }
}
