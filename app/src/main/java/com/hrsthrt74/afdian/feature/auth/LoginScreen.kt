package com.hrsthrt74.afdian.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    isLoggingIn: Boolean = false,
    errorMessage: String? = null,
) {
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("爱发电")
        Text("登录")
        if (errorMessage != null) Text(errorMessage)
        TextField(value = account, onValueChange = { account = it }, label = "账号", modifier = Modifier.fillMaxWidth())
        TextField(value = password, onValueChange = { password = it }, label = "密码", modifier = Modifier.fillMaxWidth())
        Button(onClick = { onLogin(account, password) }, enabled = account.isNotBlank() && password.isNotBlank() && !isLoggingIn) {
            Text(if (isLoggingIn) "登录中…" else "登录")
        }
    }
}
