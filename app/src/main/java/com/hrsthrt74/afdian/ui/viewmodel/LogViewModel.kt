package com.hrsthrt74.afdian.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrsthrt74.afdian.AppLog
import com.hrsthrt74.afdian.BuildConfig
import com.hrsthrt74.afdian.data.log.LogStore
import com.hrsthrt74.afdian.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志页的状态。
 *
 * 显式建模加载中 / 内容 / 错误三态（见 DESIGN.md），与存储页同一套口径。
 */
sealed interface LogUiState {
    data object Loading : LogUiState
    data class Content(val totalBytes: Long, val fileCount: Int) : LogUiState
    data class Error(val message: String) : LogUiState
}

/**
 * 日志页的 ViewModel。
 *
 * 职责：编排「占用统计 → 开关读写 → 分享 / 保存导出 → 清空 → 自动重扫」，
 * 文件 I/O 全部委托给 [LogStore]，本类不直接碰文件。
 */
class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val logStore = LogStore.get(application)
    private val settingsStore = SettingsStore(application)

    /** 「写入日志文件」开关：DataStore 流转成 StateFlow，写入后自动回推 UI；
     *  initial 跟随构建类型默认值（internal 版默认开），避免首帧显示错值 */
    val fileLogging: StateFlow<Boolean> = settingsStore.logToFile
        .stateIn(viewModelScope, SharingStarted.Eagerly, BuildConfig.INTERNAL_BUILD)

    /** 「调试日志」开关（从设置页「关于」分组迁移至此，与日志工作流归为一处） */
    val debugLogging: StateFlow<Boolean> = settingsStore.debugLogging
        .stateIn(viewModelScope, SharingStarted.Eagerly, BuildConfig.INTERNAL_BUILD)

    private val _uiState = MutableStateFlow<LogUiState>(LogUiState.Loading)
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    // 扫描去重守卫：避免连点「重试」触发并发扫描
    private var scanning = false

    init {
        refresh()
    }

    /**
     * 首次加载 / 错误重试 / 清空完成后重新统计。
     * 已有内容时不切回 Loading，让旧数字留在屏幕上，避免整页闪一下。
     */
    fun refresh() {
        if (scanning) return
        scanning = true
        viewModelScope.launch {
            if (_uiState.value !is LogUiState.Content) {
                _uiState.value = LogUiState.Loading
            }
            try {
                // 统计目录占用是轻量文件元数据读取，包 IO 线程即可
                val (bytes, count) = withContext(Dispatchers.IO) {
                    logStore.totalBytes() to logStore.fileCount()
                }
                _uiState.value = LogUiState.Content(bytes, count)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e(TAG, "日志占用统计失败", error)
                _uiState.value = LogUiState.Error(error.message ?: "日志统计失败")
            } finally {
                scanning = false
            }
        }
    }

    /** 写入「日志落盘」开关（进程内由 AfdianApplication 的订阅即时生效）。 */
    fun setFileLogging(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setLogToFile(enabled) }
    }

    /** 写入「调试日志」开关（D 级日志的 Logcat 输出与落盘都受它控制）。 */
    fun setDebugLogging(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDebugLogging(enabled) }
    }

    /**
     * 清空全部日志（运行日志 + 崩溃报告 + 导出文件），完成后自动重扫。
     * 失败给 Toast 并记警告日志，不影响页面状态。
     */
    fun clearLogs() {
        viewModelScope.launch {
            val success = try {
                // 先记日志再清空：这条「清空」记录会随旧文件一起被删掉。
                // 若放在 clear() 之后，开关开启时它自己就会立刻重建当天的日志文件，
                // 用户刚清空就看到「1 个文件」，误以为清空失败。
                AppLog.d(TAG, "清空日志")
                withContext(Dispatchers.IO) { logStore.clear() }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w(TAG, "日志清空失败", error)
                false
            }
            Toast.makeText(app, if (success) "日志已清空" else "清空失败，请稍后重试", Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    /**
     * 生成导出合并文件（供「分享日志」）。
     * 返回 null 表示合并或写入失败（如磁盘满）。
     */
    suspend fun buildExportFile(): File? {
        val file = logStore.writeExportFile(suggestedExportName())
        if (file == null) {
            AppLog.w(TAG, "日志导出文件生成失败")
        } else {
            AppLog.d(TAG, "日志导出文件已生成：${file.name}（${file.length()} 字节）")
        }
        return file
    }

    /**
     * 把合并后的全部日志写入用户通过 SAF 选定的 uri（供「保存到本地」）。
     * 返回是否成功，失败已记警告日志。
     */
    suspend fun saveTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = logStore.exportMergedText()
        runCatching {
            app.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            } != null
        }
            .onFailure { AppLog.w(TAG, "保存到本地：写入 uri 失败", it) }
            .getOrDefault(false)
    }

    /** 导出完成后重新统计（分享会在 logs/ 下留下导出文件，占用会变化）。 */
    fun refreshAfterExport() {
        refresh()
    }

    /**
     * 导出建议文件名：带变体名与时间戳，接收方一眼可辨来源与批次。
     * 也作为「保存到本地」时 SAF 文件选择器的初始名。
     */
    fun suggestedExportName(): String =
        "afdian-${BuildConfig.APP_VARIANT_NAME}-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".log"

    private companion object {
        const val TAG = "LogPage"
    }
}
