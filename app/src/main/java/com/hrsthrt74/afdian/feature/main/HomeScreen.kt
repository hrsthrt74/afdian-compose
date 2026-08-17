package com.hrsthrt74.afdian.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val posts by viewModel.posts.collectAsState(); val account by viewModel.account.collectAsState(); val sponsoring by viewModel.sponsoring.collectAsState(); val error by viewModel.error.collectAsState(); val loading by viewModel.isLoading.collectAsState()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(account?.let { "你好，${it.displayName}" } ?: "正在加载账号信息") }; Button(onClick = viewModel::refresh, enabled = !loading) { Text(if (loading) "加载中…" else "刷新") } } }
        item { Text("我发电的") }
        if (sponsoring.isEmpty()) item { Text(if (loading) "正在加载发电记录…" else "还没有发电记录") } else item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(sponsoring, key = { it.id }) { item -> Column(Modifier.width(132.dp)) { AsyncImage(item.user.avatar, item.user.name, Modifier.size(56.dp).clip(RoundedCornerShape(28.dp))); Text(item.user.name.ifBlank { "创作者" }); Text(item.planName); if ((item.new_post_count ?: 0) > 0) Text("${item.new_post_count} 条新动态") } } } }
        item { Text("全站精选") }
        if (error != null) item { Text(error!!); Button(onClick = viewModel::refresh) { Text("重试") } } else if (posts.isEmpty()) item { Text(if (loading) "正在加载精选内容…" else "暂无精选内容") } else items(posts, key = { it.id }) { post -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row { AsyncImage(post.user.avatar, post.user.name, Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))); Spacer(Modifier.width(8.dp)); Column { Text(post.user.name.ifBlank { "爱发电用户" }); Text(post.title) } }; if (!post.cover.isNullOrBlank()) AsyncImage(post.cover, null, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp))); if (post.summary.isNotBlank()) Text(post.summary, maxLines = 3); Text("点赞 ${post.like_count ?: 0}   评论 ${post.comment_count ?: 0}") } }
    }
}
