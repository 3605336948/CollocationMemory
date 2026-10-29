package com.example.collocationmemory.util

import android.content.Context
import android.util.Log
import com.example.collocationmemory.data.remote.RemoteFetch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 单词配图的加载与缓存。
 *
 * 配图放在更新仓库的 `library/word_images/` 下（与词库同一条发布链路），
 * App 端**按需下载**：学到哪个词才拉哪张图，拉到就进本地缓存
 * （filesDir/word_images/），之后离线也能看。没有配图的词不占位，
 * 界面保持干净。
 *
 * 文件命名：英文短语规范化成小写 + 下划线，例如
 * "make great progress" → make_great_progress.png。
 * 生成图片时按这个命名上传到 word_images/ 即可被 App 找到。
 */
object WordImageStore {

    private const val TAG = "WordImageStore"

    /** 配图下载源（与词库同源，多镜像容错） */
    private val BASE_URLS = listOf(
        "https://gitee.com/bz360/collocation-update/raw/master/library/word_images",
        "https://raw.githubusercontent.com/bz360/collocation-update/master/library/word_images"
    )

    /** 英文短语 → 文件名（小写、非字母数字折叠成下划线） */
    fun keyFor(english: String): String =
        english.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_') + ".png"

    /** 本地缓存文件（不管存不存在） */
    fun cachedFile(context: Context, english: String): File =
        File(context.filesDir, "word_images/${keyFor(english)}")

    /**
     * 取这个词的配图：优先本地缓存，没有就依次尝试下载源。
     *
     * @return 本地图片文件；没有配图（或下载失败）返回 null。
     *         调用方据此决定「显示图片」还是「不占位」。
     */
    suspend fun load(context: Context, english: String): File? = withContext(Dispatchers.IO) {
        if (english.isBlank()) return@withContext null
        val dest = cachedFile(context, english)
        if (dest.exists() && dest.length() > 0) return@withContext dest
        for (base in BASE_URLS) {
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            val ok = runCatching {
                RemoteFetch.download("$base/${keyFor(english)}", tmp) { _, _ -> }
            }.getOrDefault(false)
            if (ok && tmp.length() > 0 && tmp.renameTo(dest)) {
                return@withContext dest
            }
            tmp.delete()
        }
        Log.i(TAG, "没有配图：$english")
        null
    }
}
