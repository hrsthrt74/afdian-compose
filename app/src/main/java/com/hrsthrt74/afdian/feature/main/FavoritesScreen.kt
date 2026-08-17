package com.hrsthrt74.afdian.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
fun FavoritesScreen(viewModel: FavoritesViewModel, onBack: () -> Unit) {
    val kind by viewModel.kind.collectAsState(); val items by viewModel.items.collectAsState(); val loading by viewModel.loading.collectAsState(); val error by viewModel.error.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onBack) { Text("返回") }; Text("我的收藏") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("创作者", "动态", "众筹").forEachIndexed { index, title -> Button(onClick = { viewModel.select(index) }, enabled = kind != index) { Text(title) } } }
        if (loading) Text("加载中…") else if (error != null) { Text(error!!); Button(onClick = viewModel::load) { Text("重试") } } else if (items.isEmpty()) Text("还没有收藏内容") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(items, key = { it.id }) { item -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { AsyncImage(model = item.image, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape)); Column { Text(item.title); Text(item.subtitle) } } } }
    }
}
