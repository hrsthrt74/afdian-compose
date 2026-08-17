package com.hrsthrt74.afdian.feature.main

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import com.hrsthrt74.afdian.data.content.ManagementSection

@Composable
fun ManagementScreen(onBack: () -> Unit, providedViewModel: ManagementViewModel? = null) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = providedViewModel ?: viewModel<ManagementViewModel>(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val section by viewModel.section.collectAsState()
    val result by viewModel.result.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Button(onClick = onBack) { Text("返回我的") }
        Text("管理")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                ManagementSection.values().forEach { item ->
                    Button(
                        onClick = { viewModel.load(item) },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) { Text(if (item == section) "✓ ${item.title}" else item.title) }
                }
            }
            item {
                when {
                    loading -> Text("正在加载…")
                    error != null -> {
                        Text(error!!)
                        Button(onClick = { viewModel.load(section) }) { Text("重试") }
                    }
                    result == null -> Text("暂无数据")
                }
            }
            if (!loading && error == null) {
                items(result?.rows.orEmpty(), key = { it.first }) { (title, value) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(title)
                        Text(value)
                    }
                }
            }
        }
    }
}
