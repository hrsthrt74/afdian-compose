package com.hrsthrt74.afdian

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.auth.AuthSession
import com.hrsthrt74.afdian.data.auth.AuthState
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.ui.screens.auth.DisclaimerDialog
import com.hrsthrt74.afdian.ui.screens.auth.LoginScreen
import com.hrsthrt74.afdian.ui.screens.main.MainScreen
import com.hrsthrt74.afdian.ui.theme.AfdianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AfdianApp() }
    }
}

@Composable
private fun AfdianApp(session: AuthSession = viewModel()) {
    val context = LocalContext.current
    // SettingsStore 内部是进程级单例 DataStore，remember 一个包装对象即可
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    // 主题色开关：订阅 DataStore，设置页改动后立即生效（替代旧 SP 监听器）
    val useAfdianColor by settingsStore.useAfdianColor.collectAsState(initial = false)
    // 配色模式：0=跟随系统 1=浅色 2=深色
    val colorMode by settingsStore.colorMode.collectAsState(initial = 0)

    AfdianTheme(primaryColor = if (useAfdianColor) Color(0xFF946CE6) else null, colorMode = colorMode) {
        // 登录态：Loading 期间不渲染任何界面，读完 token 才放行，避免登录页闪烁
        val authState by session.state.collectAsState()
        when (authState) {
            AuthState.Loading -> Unit

            is AuthState.LoggedIn -> MainScreen(onLogout = session::logout)

            // 未登录：先确认免责声明（读取未完成前按未同意处理）
            AuthState.LoggedOut -> {
                val accepted by settingsStore.disclaimerAccepted.collectAsState(initial = false)
                if (!accepted) {
                    DisclaimerDialog(
                        onAgree = { scope.launch { settingsStore.acceptDisclaimer() } },
                        onDisagree = { (context as? Activity)?.finishAndRemoveTask() },
                    )
                } else {
                    val isLoggingIn by session.isLoggingIn.collectAsState()
                    val error by session.error.collectAsState()
                    LoginScreen(onLogin = session::login, isLoggingIn = isLoggingIn, errorMessage = error)
                }
            }
        }
    }
}
