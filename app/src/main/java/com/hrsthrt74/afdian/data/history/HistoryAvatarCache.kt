package com.hrsthrt74.afdian.data.history

import com.hrsthrt74.afdian.AppLog
import java.io.File
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 浏览历史专属头像缓存。
 *
 * 缓存放在单独的 filesDir 子目录中，不使用 Coil 的全局磁盘缓存，
 * 这样设置页可以精确删除历史记录缓存而不会误删其他图片。
 */
class HistoryAvatarCache(
    private val root: File,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val avatarDirectory = File(root, "avatars")

    fun modelFor(url: String?): Any? {
        if (url.isNullOrBlank()) return null
        val file = fileFor(url)
        return if (file.isFile && file.length() > 0L) file else url
    }

    /** 下载头像到历史专属目录；已有文件直接复用。 */
    fun ensureCached(url: String?): File? {
        if (url.isNullOrBlank() || !url.startsWith("http")) return null
        avatarDirectory.mkdirs()
        val target = fileFor(url)
        if (target.isFile && target.length() > 0L) return target

        val temporary = File(avatarDirectory, "${target.name}.tmp")
        return runCatching {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body.byteStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            AppLog.d("HistoryAvatarCache", "历史记录头像缓存成功: ${target.name}")
            target
        }.onFailure {
            temporary.delete()
            AppLog.w("HistoryAvatarCache", "历史记录头像缓存失败: ${it.message}", it)
        }.getOrNull()
    }

    fun clear() {
        runCatching {
            if (root.exists() && !root.deleteRecursively()) error("目录删除失败")
            AppLog.d("HistoryAvatarCache", "历史记录头像缓存已清除")
        }.onFailure { AppLog.w("HistoryAvatarCache", "清除历史记录头像缓存失败", it) }
    }

    private fun fileFor(url: String): File = File(avatarDirectory, "${sha256(url)}.img")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
