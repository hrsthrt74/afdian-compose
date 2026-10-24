package com.hrsthrt74.afdian.ui.screens.auth

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.BuildConfig
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「装错版本」全屏阻断页。
 *
 * 触发条件见 `EditionGuard`：legacy 兼容版跑在 API 34 及以上的设备上。
 * 该页只有「退出」一条出路 —— 换装完整版是唯一正确的解法，所以不提供任何继续使用的入口。
 *
 * 视觉与其他全屏提示页保持一致（title1 标题 + 正文 + footnote2 注脚 + 全宽按钮），
 * 区别只有两点：
 * 1. 外层用 [Box] 居中、内层 [Column] 可滚动 —— 本页文案较长，
 *    横屏/小窗下内容超出视口时能滚动，不会把「退出」按钮顶出屏幕；
 * 2. 系统返回键显式对齐「退出」按钮（[BackHandler]）。
 *
 * @param onExit 退出应用的回调，调用方传 `finishAndRemoveTask`
 */
@Composable
fun WrongEditionScreen(onExit: () -> Unit) {
    // 返回键与「退出」按钮行为一致：都结束任务并从最近任务移除，
    // 避免用户按返回后从最近任务回来又看到同一页（默认返回只是 finish，会留下任务卡片）
    BackHandler(onBack = onExit)

    Box(
        // 撑满全屏并让出状态栏/导航栏/挖孔，配合 edge-to-edge 使用
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // 内容不超高时由外层 Box 居中；超高时这一层滚动，保证按钮始终可达
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 页面标题：直接给出唯一的出路，避免用户以为装坏了
            Text(
                text = "请换用完整版",
                style = MiuixTheme.textStyles.title1,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "请从 GitHub Releases 下载文件名包含「standard」的安装包~",
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center,
            )


            // 注脚：当前系统版本，便于用户回报问题时定位
            Text(
                text = "当前系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
                modifier = Modifier.padding(top = 48.dp),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )

            // 注脚：当前包名。用 BuildConfig.APPLICATION_ID 拼，不要写死 .legacy ——
            // legacy 变体的实际包名是 com.hrsthrt74.afdian.legacy
            Text(
                text = "当前安装包：${BuildConfig.APPLICATION_ID}",
                modifier = Modifier.padding(top = 4.dp),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )

            // 唯一操作：退出
            Button(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            ) {
                Text("退出")
            }
        }
    }
}
