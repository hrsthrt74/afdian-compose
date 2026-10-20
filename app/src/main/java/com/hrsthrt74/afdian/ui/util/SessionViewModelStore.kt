package com.hrsthrt74.afdian.ui.util

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hrsthrt74.afdian.AppLog

/**
 * 会话级 ViewModel 作用域：把所有「已登录」界面里的 ViewModel 从 Activity 作用域挪到登录会话上。
 *
 * 为什么需要它：MainActivity 是全进程唯一的 Activity，挂在它身上的 ViewModelStore
 * 只在 Activity finish() 时清空。登出只是把 MainScreen 从组合树里移除，
 * ViewModel 实例仍留在 Activity 的 ViewModelStore 里，重新登录时会被原样复用 ——
 * 表现为「换账号后数据还是上一个人的，必须杀进程重启」。
 *
 * 把作用域换成这个 owner 后：
 * - 登出 → [clear] 逐个触发 onCleared()，viewModelScope 一并取消（顺带掐掉登出瞬间还在跑的刷新）；
 * - 重新登录 → 组合树重新挂载，所有 ViewModel 重新构造，init 里的首次 refresh 重新执行。
 */
class SessionViewModelStoreOwner(
    private val application: Application,
    private val store: ViewModelStore,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    override val viewModelStore: ViewModelStore get() = store

    /**
     * 与 Activity 默认工厂保持一致，保证 `viewModel()`（不传 factory）也能构造
     * 需要 Application 的 AndroidViewModel。
     */
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    /**
     * 只补 APPLICATION_KEY：工程内没有任何 ViewModel 使用 SavedStateHandle，
     * 不引入 SavedState 相关的额外 key，避免和 Activity 的注册表所有者冲突。
     */
    override val defaultViewModelCreationExtras: CreationExtras =
        MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
        }

    /** 销毁作用域内的全部 ViewModel；登出时由 [rememberSessionViewModelStoreOwner] 的调用方触发。 */
    fun clear() {
        AppLog.d(TAG, "会话结束，销毁全部会话级 ViewModel")
        store.clear()
    }

    private companion object {
        const val TAG = "SessionScope"
    }
}

/**
 * 会话级 ViewModelStore 的宿主，挂在 Activity 作用域。
 *
 * 为什么不直接 `remember { ViewModelStore() }`：Activity 没有声明 configChanges，
 * 屏幕旋转、切换深色模式都会重建 Activity，`remember` 出来的 Store 会跟着组合一起消失，
 * 等于一次旋转就把首页数据全部清空重拉。把 Store 放进这个 Activity 级 ViewModel 里，
 * 配置变更时它整棵存活，只有真正登出或 Activity 结束时才销毁。
 */
class SessionStoreHost : ViewModel() {
    val store: ViewModelStore = ViewModelStore()

    override fun onCleared() {
        // Activity 真正结束（退出应用）：连带清空会话内所有 ViewModel
        store.clear()
    }
}

/**
 * 取得当前登录会话的 ViewModel 作用域。
 *
 * 宿主通过 `viewModel()` 落在**当前**作用域（调用点尚未 provide 会话 owner，因此是 Activity），
 * 同一个 Activity 内多次登录/登出复用同一个 Store，只是每次登出会被清空。
 *
 * 注意：必须在 provide `LocalViewModelStoreOwner` **之前**调用。
 */
@Composable
fun rememberSessionViewModelStoreOwner(): SessionViewModelStoreOwner {
    val application = LocalContext.current.applicationContext as Application
    val host: SessionStoreHost = viewModel()
    return remember(host) { SessionViewModelStoreOwner(application, host.store) }
}
