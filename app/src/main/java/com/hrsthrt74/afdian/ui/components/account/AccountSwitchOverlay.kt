package com.hrsthrt74.afdian.ui.components.account

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hrsthrt74.afdian.data.auth.AccountSwitchPhase
import com.hrsthrt74.afdian.data.auth.SavedAccountSummary
import com.hrsthrt74.afdian.ui.components.basic.Avatar
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.time.Duration.Companion.milliseconds

/**
 * 切号遮罩：半透明 dim + 居中卡片，整个切换期间不可取消。
 *
 * 用 [WindowDialog]（独立窗口）而不是 Compose 内联遮罩：切换时整棵已登录子树会按 epoch
 * 重建，内联遮罩会跟着一起被销毁；独立窗口不依赖页面组合，能稳定盖住重建过程，
 * 也能压住账号菜单这类 Popup 层。
 *
 * 显隐的**开场**完全由 [phase] 驱动，`onDismissRequest` 传空实现：
 * 点遮罩、按返回键都不会关闭，只有新会话首屏就绪或超时兜底才会进入收尾。
 *
 * 换脸动画：`Preparing` 阶段仍是旧会话，遮罩先显示**原账号**头像与昵称；
 * 等 `Waiting`（新 token 已提交）且过了 [SWITCH_AVATAR_HOLD_MS] 的展示期后，
 * 原账号向左滑出淡出、目标账号从右侧滑入淡入 —— 让用户看清「从谁换到了谁」。
 *
 * 收尾（关窗）**比 [phase] 慢半拍**：首屏就绪只代表数据到了，不代表画面该消失。
 * 关窗时间统一压到「换脸动画结束 + [SWITCH_OVERLAY_TAIL_MS] 余韵」，
 * 避免加载很快时出现「刚换完脸弹窗就没了」的突兀感。
 */
@Composable
fun AccountSwitchOverlay(phase: AccountSwitchPhase) {
    val switching = phase !is AccountSwitchPhase.Idle

    // 最后一帧的快照：phase 回到 Idle 后窗口还要停留一小段，那段时间已经取不到账号数据，
    // 必须沿用快照，否则卡片会先闪成空白再关闭。只在 phase 有效时更新，
    // 值稳定后赋值相等不再触发重组，最多多一次重组。
    var snapshot by remember { mutableStateOf<AccountSwitchPhase>(AccountSwitchPhase.Idle) }
    if (phase !is AccountSwitchPhase.Idle) snapshot = phase
    // 委托属性无法被智能转换到子类型，先取局部变量再解构
    val snap = snapshot

    val target: SavedAccountSummary? = when (snap) {
        is AccountSwitchPhase.Preparing -> snap.target
        is AccountSwitchPhase.Waiting -> snap.target
        AccountSwitchPhase.Idle -> null
    }
    val from: SavedAccountSummary? = when (snap) {
        is AccountSwitchPhase.Preparing -> snap.from
        is AccountSwitchPhase.Waiting -> snap.from
        AccountSwitchPhase.Idle -> null
    }

    // 时间轴锚点（都用 uptimeMillis：与 delay 同一套相对时钟，不受系统时间调整影响）
    var appearedAt by remember { mutableStateOf(0L) }
    // 换脸动画真正的起点：预检慢时 Waiting 晚于展示期满，动画从那一刻才开始
    var revealedAt by remember { mutableStateOf(0L) }
    var holdFinished by remember { mutableStateOf(false) }
    // 窗口是否还停留在收尾阶段
    var windowVisible by remember { mutableStateOf(false) }

    LaunchedEffect(switching) {
        if (switching) {
            // ── 开场 ──
            appearedAt = SystemClock.uptimeMillis()
            revealedAt = 0L
            holdFinished = false
            windowVisible = true
            // 遮罩出现后先让原账号在屏幕上停一小会，再允许换脸。
            // 预检只是一次本地解密 + 一次网络请求，常常几十毫秒就从 Preparing 走到 Waiting；
            // 不压这一段的话，遮罩刚出现就闪成新账号，动画根本来不及被看见。
            delay(SWITCH_AVATAR_HOLD_MS.milliseconds)
            holdFinished = true
        } else if (windowVisible) {
            // ── 收尾 ──
            // 加载特别快时，首屏可能在换脸动画刚播完（甚至还没开始）就绪；
            // 这一刻直接关窗就是「刚换完脸就消失」，所以把关窗时间压到动画结束 + 余韵。
            val holdDeadline = appearedAt + SWITCH_AVATAR_HOLD_MS
            if (!holdFinished) {
                // 展示期都没走完就要关：先补满，让换脸动画有机会完整播一遍
                delay((holdDeadline - SystemClock.uptimeMillis()).coerceAtLeast(0L).milliseconds)
                holdFinished = true
            }
            // 换脸起点取两者较晚：预检慢时是 Waiting 的时刻，否则是展示期满的时刻
            val revealStart = maxOf(revealedAt, holdDeadline)
            val closeAt = revealStart + SWITCH_AVATAR_ANIM_MS + SWITCH_OVERLAY_TAIL_MS
            delay((closeAt - SystemClock.uptimeMillis()).coerceAtLeast(0L).milliseconds)
            windowVisible = false
        }
    }

    // 记录换脸起点：Waiting 到达且展示期已满的那一刻，动画才真正开始
    LaunchedEffect(holdFinished, snap) {
        if (holdFinished && revealedAt == 0L && snap is AccountSwitchPhase.Waiting) {
            revealedAt = SystemClock.uptimeMillis()
        }
    }

    // 遮罩当前该显示谁（用快照而非 phase：收尾期间 phase 已是 Idle，但动画该继续播完）：
    // - Preparing：仍是旧会话，显示原账号；
    // - Waiting 且已过展示期：显示目标账号，触发换脸动画；
    // - 没有原账号信息（老用户未补建记录）：直接显示目标账号，退化成无动画
    val shownAccount = if (snap is AccountSwitchPhase.Waiting && holdFinished) {
        target
    } else {
        from ?: target
    }

    WindowDialog(
        // 收尾阶段 phase 已 Idle，靠 windowVisible 继续撑住窗口
        show = switching || windowVisible,
        onDismissRequest = { /* 空实现：切换中禁止任何方式关闭 */ },
        largeScreen = true,
    ) {
        if (target != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 头像 + 昵称作为一整组参与过渡：只换头像、名字还停在目标账号会自相矛盾
                AnimatedContent(
                    targetState = shownAccount,
                    transitionSpec = {
                        // 旧账号向左滑出并淡出，新账号从右侧滑入并淡入：整体「向左推进」的换脸观感。
                        // 滑动幅度取面板宽的 1/8，位移清晰但不会甩出画面（与登录页转场同一套比例）
                        (fadeIn(animationSpec = tween(SWITCH_AVATAR_ANIM_MS.toInt())) +
                            slideInHorizontally(
                                animationSpec = tween(SWITCH_AVATAR_ANIM_MS.toInt())
                            ) { it / 8 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(SWITCH_AVATAR_EXIT_MS.toInt())) +
                                    slideOutHorizontally(
                                        animationSpec = tween(SWITCH_AVATAR_EXIT_MS.toInt())
                                    ) { -it / 8 }
                            )
                    },
                    label = "accountSwitchAvatar",
                    modifier = Modifier.fillMaxWidth(),
                ) { account ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // 账号头像：切换中先是原账号，展示期满后左移换成目标账号
                        Avatar(
                            model = account?.avatar,
                            size = 56.dp,
                            contentDescription = "账号头像",
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = account?.name?.ifBlank { "爱发电用户" } ?: "爱发电用户",
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 与登录按钮内同款进度指示器，视觉语言一致
                    InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (snap is AccountSwitchPhase.Waiting) {
                            "正在加载账号数据…"
                        } else {
                            "正在切换账号…"
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = Color.Gray, // 为了与 InfiniteProgressIndicator 颜色一致
                    )
                }
            }
        }
    }
}

/**
 * 遮罩出现后先展示原账号的时长（毫秒）。
 *
 * 这是一个「看得清又不拖沓」的窗口：短于 300ms 眼睛几乎捕捉不到原账号，
 * 长于 500ms 会让本来很快的切换显出不必要的停顿。
 */
private const val SWITCH_AVATAR_HOLD_MS = 420L

/**
 * 换脸动画中「新账号滑入」的时长（毫秒）。
 *
 * 与 [AnimatedContent] 的 `transitionSpec` 共用同一个值：收尾时要按它推算动画何时结束，
 * 两处写死不同的数字会导致关窗时机与动画实际进度对不上。
 */
private const val SWITCH_AVATAR_ANIM_MS = 240L

/** 换脸动画中「旧账号滑出」的时长（毫秒）：比入场略短，出入错开才有推进感。 */
private const val SWITCH_AVATAR_EXIT_MS = 160L

/**
 * 换脸动画播完后的余韵（毫秒）。
 *
 * 关窗会被压到「动画结束 + 此值」，让用户看清换成了哪个账号再让遮罩消失；
 * 取 320ms 大约是「看清一个头像 + 读完一行昵称」的最短时间。
 */
private const val SWITCH_OVERLAY_TAIL_MS = 320L
