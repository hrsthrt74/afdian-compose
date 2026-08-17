package com.hrsthrt74.afdian.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun DisclaimerDialog(onAgree: () -> Unit, onDisagree: () -> Unit) {
    WindowDialog(
        show = true,
        onDismissRequest = { },
        title = "免责声明",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("本应用仅供学习和参考使用，与爱发电官方无隶属、授权或合作关系。请遵守平台规则和当地法律法规。")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAgree, modifier = Modifier.fillMaxWidth()) {
                    Text("同意")
                }
                Button(onClick = onDisagree, modifier = Modifier.fillMaxWidth()) {
                    Text("不同意")
                }
            }
        }
    }
}
