package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.data.content.HomeCache
import com.hrsthrt74.afdian.data.content.ProfileCache
import com.hrsthrt74.afdian.data.history.HistoryStore
import com.hrsthrt74.afdian.data.log.LogStore
import com.hrsthrt74.afdian.data.search.SearchHistoryStore
import com.hrsthrt74.afdian.data.settings.SettingsStore
import com.hrsthrt74.afdian.data.storage.HistoryPart
import com.hrsthrt74.afdian.data.storage.ImageCacheLimit
import com.hrsthrt74.afdian.data.storage.StorageCategory
import com.hrsthrt74.afdian.data.storage.StorageScanner
import com.hrsthrt74.afdian.data.storage.StorageSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 存储空间管理页的状态。
 *
 * 显式建模加载中 / 内容 / 错误三态，而不是用一个 Boolean 含糊表达（见 DESIGN.md）。
 */
sealed interface StorageUiState {
    data object Loading : StorageUiState
    data class Content(val snapshot: StorageSnapshot) : StorageUiState
    data class Error(val message: String) : StorageUiState
}

/**
 * 存储空间管理页的 ViewModel。
 *
 * 职责：编排「扫描 → 展示 → 分类清理 / 一键清理 → 自动重扫」，并给用户 Toast 反馈。
 * 所有磁盘读写都委托给 [StorageScanner] 与各数据仓库，本类不直接碰文件。
 */
class StorageViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val scanner = StorageScanner(application)
    private val settingsStore = SettingsStore(application)
    private val historyStore = HistoryStore(application)
    private val searchHistoryStore = SearchHistoryStore(application)
    private val homeCache = HomeCache(application)
    private val profileCache = ProfileCache(application)
    // 日志清理走 LogStore 统一入口：它持有 writer 线程的文件句柄，直接删文件会删不掉
    private val logStore = LogStore.get(application)

    /**
     * 图片缓存的体积上限。
     *
     * 这里只负责读写，真正生效在 ImageLoader 构建时（见 `buildImageDiskCache`）——
     * 所以是「重启后生效」，界面上有说明文字交代清楚。
     */
    val imageCacheLimit: StateFlow<ImageCacheLimit> = settingsStore.imageCacheLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, ImageCacheLimit.Default)

    fun setImageCacheLimit(limit: ImageCacheLimit) {
        viewModelScope.launch { settingsStore.setImageCacheLimit(limit) }
    }

    private val _uiState = MutableStateFlow<StorageUiState>(StorageUiState.Loading)
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    /** 正在清理的分类 id 集合：既用于把对应行置灰，也作为并发守卫。 */
    private val _clearingIds = MutableStateFlow<Set<String>>(emptySet())
    val clearingIds: StateFlow<Set<String>> = _clearingIds.asStateFlow()

    // 扫描去重守卫：避免连点「重试」触发并发遍历
    private var scanning = false

    init {
        refresh()
    }

    /**
     * 首次加载 / 错误重试 / 清理完成后重新扫描。
     *
     * 已有内容时**不切回 Loading**，让旧数字留在屏幕上，避免整页闪一下。
     */
    fun refresh() {
        if (scanning) return
        scanning = true
        viewModelScope.launch {
            if (_uiState.value !is StorageUiState.Content) {
                _uiState.value = StorageUiState.Loading
            }
            try {
                _uiState.value = StorageUiState.Content(scanner.scan())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // 网络/磁盘异常统一转成错误态，不让协程异常直接杀进程
                AppLog.e(TAG, "存储扫描失败", error)
                _uiState.value = StorageUiState.Error(error.message ?: "存储扫描失败")
            } finally {
                scanning = false
            }
        }
    }

    /** 清理单个分类（整类），完成后自动重扫。带可勾选子项的分类等同「子项全选」。 */
    fun clearCategory(category: StorageCategory) {
        if (!category.cleanable) {
            AppLog.w(TAG, "尝试清理不可清理的分类：${category.id}")
            return
        }
        launchClear(category, category.selectableParts.toSet())
    }

    /**
     * 只清理「历史记录」里用户勾选的那几项。
     *
     * 与 [clearCategory] 分开是为了让调用方语义明确：一个清整类，一个清勾选的子集。
     * 子集为空直接忽略（UI 侧也会禁用确认按钮，这里再兜一层）。
     */
    fun clearHistoryParts(parts: Set<HistoryPart>) {
        if (parts.isEmpty()) {
            AppLog.w(TAG, "历史记录的清理子集为空，已忽略")
            return
        }
        launchClear(StorageCategory.History, parts)
    }

    /** 清理动作的公共编排：置「清理中」→ 执行 → Toast → 复位 → 重扫。 */
    private fun launchClear(category: StorageCategory, parts: Set<HistoryPart>) {
        if (category.id in _clearingIds.value) return

        viewModelScope.launch {
            _clearingIds.value = _clearingIds.value + category.id
            val success = runClear("清理失败：${category.id}") { clearOne(category, parts) }
            Toast.makeText(
                app,
                if (success) clearedMessage(category, parts) else "清理失败，请稍后重试",
                Toast.LENGTH_SHORT,
            ).show()
            _clearingIds.value = _clearingIds.value - category.id
            refresh()
        }
    }

    /**
     * 清理成功的提示文案。
     * 只清了部分子项时把子项名报出来，比笼统说「已清理历史记录」更准确。
     */
    private fun clearedMessage(category: StorageCategory, parts: Set<HistoryPart>): String =
        if (parts.size == category.selectableParts.size) {
            "已清理${category.title}"
        } else {
            // 按枚举声明顺序拼接，保证文案稳定（Set 的迭代顺序不保证）
            "已清理" + parts.sorted().joinToString("、") { it.label }
        }

    /**
     * 一键清理：只覆盖所有 [StorageCategory.cleanable] 为 true 的分类。
     * 绝不触碰「设置与登录数据」，清理完仍然保持登录。
     */
    fun clearAllCaches() {
        if (_clearingIds.value.isNotEmpty()) return

        viewModelScope.launch {
            val targets = StorageCategory.entries.filter { it.cleanable }
            _clearingIds.value = targets.map { it.id }.toSet()

            // 逐个清理，单个失败不影响其余；最后按失败数量给不同文案。
            // 一键清理是「整类」语义，所以带子项的分类要传全部子项。
            val failed = targets.filterNot { category ->
                runClear("一键清理失败：${category.id}") {
                    clearOne(category, category.selectableParts.toSet())
                }
            }
            failed.forEach { AppLog.w(TAG, "一键清理部分失败：${it.id}") }

            Toast.makeText(
                app,
                if (failed.isEmpty()) "已清理全部缓存" else "部分缓存清理失败",
                Toast.LENGTH_SHORT,
            ).show()
            _clearingIds.value = emptySet()
            refresh()
        }
    }

    /**
     * 单个分类的实际清理动作。
     *
     * 全部走各仓库自己的清理 API，而不是直接删文件：DataStore 文件可能被句柄占用，
     * Coil 目录里有 LRU journal，硬删都会留下不一致状态。
     *
     * @param parts 只对带可勾选子项的分类（「历史记录」）有意义，其余分类传空集即可。
     */
    private suspend fun clearOne(category: StorageCategory, parts: Set<HistoryPart>) {
        when (category) {
            // 图片缓存：只能走 Coil 自己的 DiskCache.clear()。
            // 该方法是同步阻塞的（内部 evictAll 持锁删文件），必须放在 IO 线程。
            StorageCategory.ImageCache -> withContext(Dispatchers.IO) {
                SingletonImageLoader.get(app).diskCache?.clear()
            }
            // 历史记录：按勾选的子项只清需要的那份。
            // 这里用 clearAllAccounts 而不是 clear：存储管理面向磁盘占用，
            // 必须把所有账号的分区都清掉，否则用户点完「清理」发现空间没变化。
            // clearAllAccounts 同时会顺带回收旧版遗留的头像缓存目录，所以「浏览历史」这一项涵盖了它。
            StorageCategory.History -> {
                if (HistoryPart.Browsing in parts) historyStore.clearAllAccounts()
                if (HistoryPart.Search in parts) searchHistoryStore.clearAllAccounts()
            }
            // 快照缓存：首页与「我的」两个快照一起清，避免只清一半造成观感不一致
            StorageCategory.Snapshot -> {
                homeCache.clear()
                profileCache.clear()
            }
            // 日志：LogStore.clear() 内部提交到 writer 线程（先关句柄再删文件），
            // 带 3 秒超时；这里包 IO 线程，避免阻塞主线程等待
            StorageCategory.Logs -> withContext(Dispatchers.IO) {
                logStore.clear()
            }
            // 不可清理的分类本方法不会被调用；这里显式留空，保证 when 穷尽
            StorageCategory.AppData, StorageCategory.Other -> Unit
        }
    }

    /**
     * 统一的清理执行包装：返回是否成功。
     *
     * 不直接用 `runCatching`：它会把 [CancellationException] 一起吞掉，
     * 导致页面销毁时正在进行的清理被当成「失败」弹出 Toast。
     */
    private suspend fun runClear(message: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AppLog.w(TAG, message, error)
        false
    }

    private companion object {
        const val TAG = "Storage"
    }
}
