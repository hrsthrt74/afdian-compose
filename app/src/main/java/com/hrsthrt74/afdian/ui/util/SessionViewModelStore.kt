package com.hrsthrt74.afdian.ui.util

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hrsthrt74.afdian.ui.viewmodel.PostInteractionController

/**
 * 会话级 ViewModel 作用域：把所有「已登录」界面里的 ViewModel 从 Activity 作用域挪到登录会话上。
 *
 * 为什么需要它：MainActivity 是全进程唯一的 Activity，挂在它身上的 ViewModelStore
 * 只在 Activity finish() 时清空。登出/切号只是把 MainScreen 从组合树里移除，
 * ViewModel 实例仍会留在 Activity 的 ViewModelStore 里被下次复用 ——
 * 表现为「换账号后数据还是上一个人的」。
 *
 * 把作用域换成这个 owner 后，一个会话内的 ViewModel 要么随会话结束整批销毁，
 * 要么随 [SessionStoreHost.storeFor] 的代次切换整批重建。
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

    /** 销毁作用域内的全部 ViewModel；登出时由调用方的 DisposableEffect 触发。 */
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
 * 配置变更时它整棵存活，只有真正登出、切号或 Activity 结束时才销毁。
 *
 * 同时它也是「按会话代次隔离」的落点：[storeFor] 发现代次变了，就清掉旧 Store
 * （触发旧 ViewModel 的 onCleared 与 viewModelScope 取消），再换一个全新的。
 */
class SessionStoreHost : ViewModel() {
    private var currentEpoch: Long? = null
    private var store: ViewModelStore = ViewModelStore()

    /**
     * 取指定代次的 Store。
     *
     * - 代次变化：先 clear 旧 Store（旧 ViewModel 全部销毁），再新建一个；
     * - 代次不变（旋转屏幕、普通重组）：返回同一个 Store，数据不重拉。
     */
    fun storeFor(epoch: Long): ViewModelStore {
        if (currentEpoch != epoch) {
            // 首次（currentEpoch == null）时 Store 本来就是空的，不必 clear
            if (currentEpoch != null) {
                AppLog.d("SessionScope", "会话代次变化，重建会话级 ViewModel")
                store.clear()
            }
            store = ViewModelStore()
            currentEpoch = epoch
        }
        return store
    }

    override fun onCleared() {
        // Activity 真正结束（退出应用）：连带清空会话内所有 ViewModel
        store.clear()
    }
}

/**
 * 取得指定会话代次的 ViewModel 作用域。
 *
 * 宿主通过 `viewModel()` 落在**当前**作用域（调用点尚未 provide 会话 owner，因此是 Activity），
 * 同一个 Activity 内多次登录/切号复用同一个宿主，只是每次代次变化会换掉里面的 Store。
 *
 * 注意：必须在 provide `LocalViewModelStoreOwner` **之前**调用。
 *
 * @param epoch 会话代次（`AuthState.LoggedIn.epoch`），由 AuthSession 在登录/切号时递增
 */
@Composable
fun rememberSessionViewModelStoreOwner(epoch: Long): SessionViewModelStoreOwner {
    val application = LocalContext.current.applicationContext as Application
    val host: SessionStoreHost = viewModel()
    val owner = remember(host, epoch) {
        SessionViewModelStoreOwner(application, host.storeFor(epoch))
    }
    // 会话换了（登录 / 切号 / 重新登录）：点赞与收藏是进程级单例，必须清零，
    // 否则新账号会带上一位用户的高亮状态。放在 LaunchedEffect 里而不是 remember 内，
    // 避免在组合过程中直接改 StateFlow 触发「组合期状态变更」。
    LaunchedEffect(epoch) {
        PostInteractionController.get(application).reset()
    }
    return owner
}
