package com.hrsthrt74.afdian.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 时间炸弹过期后的全屏阻断页。 */
@Composable
fun ExpiredScreen(
    buildTimestampMs: Long,
    expiresAtMs: Long,
    onExit: () -> Unit,
) {
    val formatter = DateFormat.getDateTimeInstance()
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "内测版本已过期",
            style = MiuixTheme.textStyles.title1,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Text(
            "请联系作者 hrsthrt74 获取最新版本~",
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Text(
            "构建时间：${formatter.format(Date(buildTimestampMs))}",
            modifier = Modifier.padding(top = 96.dp),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            )

        Text(
            "截止时间：${formatter.format(Date(expiresAtMs))}",
            modifier = Modifier.padding(top = 4.dp),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )

        Button(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
            Text("退出")
        }
    }
}
