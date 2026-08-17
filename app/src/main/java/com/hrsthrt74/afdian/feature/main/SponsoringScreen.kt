package com.hrsthrt74.afdian.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun SponsoringScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val sponsoring by viewModel.sponsoring.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Button(onClick = onBack) { Text("返回我的") }
        Text("我发电的")
        Text("共 ${sponsoring.size} 位创作者")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (loading && sponsoring.isEmpty()) item { Text("正在加载…") }
            else if (error != null) {
                item {
                    Text(error!!)
                    Button(onClick = viewModel::refresh) { Text("重试") }
                }
            } else if (sponsoring.isEmpty()) item { Text("还没有发电记录") }
            else items(sponsoring, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    AsyncImage(
                        model = item.user.avatar,
                        contentDescription = item.user.name,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)),
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(item.user.name.ifBlank { "创作者" })
                        Text(item.planName)
                        if ((item.new_post_count ?: 0) > 0) Text("${item.new_post_count} 条新动态")
                    }
                }
            }
        }
    }
}
