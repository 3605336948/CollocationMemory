package com.example.collocationmemory.data.remote

import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 一个极简的 HTTP 工具。
 *
 * 为什么不用 OkHttp：这个 App 总共只有「下载一个几 KB 的清单、一个 700KB 的词库、
 * 一个 21MB 的 APK」三件事，用 JDK 自带的 HttpURLConnection 足够了，
 * 加一个第三方库反而增大包体、多一层混淆风险。
 *
 * 所有方法都**不抛异常**：拿不到就返回 null / false，由调用方决定怎么兜底 ——
 * 更新检查失败绝不能让 App 启动不了。
 */
object RemoteFetch {

    private const val TAG = "CollocationMemory"
    private const val UA = "CollocationMemory-Android"

    /** 进度回调的触发间隔：每写满这么多字节报一次，避免主线程被刷爆 */
    private const val PROGRESS_STEP = 256 * 1024L

    /**
     * 依次尝试一组地址，返回第一个成功拿到的文本。
     *
     * 这是「多镜像」的实现：国内源放前面，挂了自动落到备用源。
     */
    fun getTextFromAny(urls: List<String>): String? {
        for (url in urls) {
            if (url.isBlank()) continue
            getText(url)?.let { return it }
        }
        return null
    }

    /** GET 一个文本资源；失败返回 null */
    fun getText(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = open(url)
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "拉取失败 HTTP $code <- $url")
                return null
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "拉取失败 $url：${e.message}")
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * 下载到本地文件。
     *
     * @param onProgress 已下载字节数、总字节数（服务器没给 Content-Length 时 total 为 -1）
     * @return 成功 true。**注意**：失败时可能留下写了一半的文件，由调用方负责删除。
     */
    fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit): Boolean {
        var conn: HttpURLConnection? = null
        var input: InputStream? = null
        return try {
            conn = open(url)
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "下载失败 HTTP $code <- $url")
                return false
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            dest.parentFile?.mkdirs()

            input = conn.inputStream
            dest.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                var lastReported = 0L
                onProgress(0L, total)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    if (downloaded - lastReported >= PROGRESS_STEP) {
                        lastReported = downloaded
                        onProgress(downloaded, total)
                    }
                }
                out.flush()
                onProgress(downloaded, total)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "下载失败 $url：${e.message}")
            false
        } finally {
            runCatching { input?.close() }
            runCatching { conn?.disconnect() }
        }
    }

    /** 算文件的 SHA-256，返回 64 位小写十六进制 */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = UpdateConfig.CONNECT_TIMEOUT_MS
        conn.readTimeout = UpdateConfig.READ_TIMEOUT_MS
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", UA)
        // 明确不要压缩：省掉手动解 gzip 的麻烦，这些资源本身也不大
        conn.setRequestProperty("Accept-Encoding", "identity")
        return conn
    }
}
