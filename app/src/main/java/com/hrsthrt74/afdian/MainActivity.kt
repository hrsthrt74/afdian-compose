package com.hrsthrt74.afdian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.data.auth.AuthSession
import com.hrsthrt74.afdian.feature.auth.LoginScreen
import com.hrsthrt74.afdian.feature.auth.DisclaimerDialog
import com.hrsthrt74.afdian.feature.main.MainScreen
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
    AfdianTheme {
        val context = androidx.compose.ui.platform.LocalContext.current
        var accepted by remember { mutableStateOf(context.getSharedPreferences("app", 0).getBoolean("disclaimer", false)) }
        if (!accepted) {
            DisclaimerDialog(
                onAgree = {
                    context.getSharedPreferences("app", 0).edit().putBoolean("disclaimer", true).apply()
                    accepted = true
                },
                onDisagree = { (context as? android.app.Activity)?.finishAndRemoveTask() },
            )
            return@AfdianTheme
        }
        val authenticated by session.authenticated.collectAsState()
        if (authenticated) MainScreen(onLogout = session::logout)
        else {
            val isLoggingIn by session.isLoggingIn.collectAsState()
            val error by session.error.collectAsState()
            LoginScreen(onLogin = session::login, isLoggingIn = isLoggingIn, errorMessage = error)
        }
    }
}
