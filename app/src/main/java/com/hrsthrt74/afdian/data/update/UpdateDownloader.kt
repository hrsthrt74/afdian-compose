package com.hrsthrt74.afdian.data.update

import android.content.Context
import com.hrsthrt74.afdian.AppLog
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 安装包下载器：把 Release 附件流式落到 `files/updates/`。
 *
 * 为什么自己写而不是用 DownloadManager：
 * - 需要把进度直接喂给弹窗（DownloadManager 只能轮询 cursor，弹窗关掉后还得处理广播）；
 * - 安装包落在应用私有目录，交 FileProvider 给系统安装器读取，不污染公共下载目录。
 *
 * 目录约定：`files/updates/`，与 `res/xml/file_paths.xml` 里
 * `<files-path name="updates" path="updates/"/>` 必须一致。
 */
internal class UpdateDownloader(
    private val context: Context,
    private val client: OkHttpClient = updateHttpClient,
) {

    companion object {
        /** 安装包目录名（相对 `filesDir`）。 */
        const val UPDATE_DIR = "updates"

        /** 进度回调的最小间隔：每 64KB 都回调会让弹窗一秒重组几十次。 */
        private const val PROGRESS_INTERVAL_MS = 100L

        private const val BUFFER_SIZE = 64 * 1024
    }

    /**
     * 下载安装包。
     *
     * 先写 `<文件名>.part` 再原子改名：下载中断时目录里不会留下一个「看起来完整」的 APK。
     *
     * @param expectedBytes Release 附件声明的字节数（0 表示未知），用于进度与完整性校验
     * @param onProgress 已下载 / 总字节数；总长度为 0 时 UI 应退化成不确定进度
     * @return 成功时返回落盘文件；失败或取消时返回失败（取消会原样抛出 CancellationException）
     */
    suspend fun download(
        url: String,
        fileName: String,
        expectedBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, UPDATE_DIR).apply { mkdirs() }
        val target = File(dir, sanitizeFileName(fileName))
        val part = File(dir, "${target.name}.part")

        runCatching {
            // 目录里只该留一个安装包：旧包在下次下载前清掉，避免存储越积越多
            dir.listFiles()?.forEach { file ->
                if (file.name != part.name) file.delete()
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "afdian-compose-android-client")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
                val body = response.body
                // 服务端给了 Content-Length 就以它为准；没有就退回 Release 附件声明的 size
                val total = body.contentLength().takeIf { it > 0 } ?: expectedBytes

                // 写之前先确认空间够：写到一半 ENOSPC 会留下一个不可用的 .part
                if (total > 0 && dir.usableSpace < total + total / 10) {
                    throw IOException("存储空间不足，请清理后重试")
                }

                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = 0L
                        var lastEmitAt = 0L
                        while (true) {
                            // 每个缓冲块检查一次取消：OkHttp 的读操作本身阻塞，靠这里及时退出
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmitAt >= PROGRESS_INTERVAL_MS || (total > 0 && downloaded >= total)) {
                                lastEmitAt = now
                                withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                            }
                        }
                        output.flush()
                    }
                }

                // 声明了长度就必须对上，否则是代理截断 / 网络抖动，装上去会解析失败
                if (total > 0 && part.length() != total) {
                    throw IOException("下载不完整（${part.length()}/$total 字节）")
                }
                if (!part.renameTo(target)) throw IOException("重命名临时文件失败")
                target
            }
        }.onFailure { error ->
            // 取消不是错误：清理完临时文件后原样抛出，让上层协程正常结束
            part.delete()
            if (error is kotlinx.coroutines.CancellationException) throw error
            AppLog.w("UpdateDownloader", "下载安装包失败", error)
        }
    }

    /**
     * 清洗文件名：只保留字母数字与 `. _ -`。
     *
     * 文件名来自 Release 附件 / 我们自己的拼接，正常不会带奇怪字符；这里仍然收一次，
     * 避免路径分隔符之类的东西把写入位置带出 `files/updates/`。
     */
    private fun sanitizeFileName(raw: String): String {
        val base = raw.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (base.endsWith(".apk", ignoreCase = true)) base else "update.apk"
    }
}
