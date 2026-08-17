package com.hrsthrt74.afdian.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun DisclaimerDialog(onAgree: () -> Unit, onDisagree: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("免责声明")
            Text("本应用仅供学习和参考使用，与爱发电官方无隶属、授权或合作关系。请遵守平台规则和当地法律法规。")
            Button(onClick = onAgree) { Text("同意") }
            Button(onClick = onDisagree) { Text("不同意") }
        }
    }
}
