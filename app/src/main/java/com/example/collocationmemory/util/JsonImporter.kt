package com.example.collocationmemory.util

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

data class CollocationDto(
    val english: String,
    val chinese: String,
    val example: String,
    val tags: List<String> = emptyList(),
    /**
     * 所属词本。内置词库里可以放多个词本，靠这个字段区分；
     * 留空则用词库级的 `LibraryFile.bookName`。
     */
    val bookName: String? = null
)

/** 打包在 APK 里的内置词库格式（assets/app_library.json） */
data class LibraryFile(
    val version: Int = 0,
    val bookName: String = "内置词库",
    val items: List<CollocationDto> = emptyList()
)

object JsonImporter {
    private val gson = Gson()

    fun parse(jsonContent: String): List<CollocationDto> {
        if (jsonContent.isBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<CollocationDto>>() {}.type
            val parsed = gson.fromJson<List<CollocationDto>>(jsonContent, type)
            parsed?.filter {
                it.english.isNotBlank() && it.chinese.isNotBlank()
            } ?: emptyList()
        } catch (e: JsonSyntaxException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 解析内置词库文件；版本号或内容不合法时返回 null，由调用方决定怎么处理 */
    fun parseLibrary(jsonContent: String): LibraryFile? {
        if (jsonContent.isBlank()) {
            return null
        }
        return try {
            val parsed = gson.fromJson(jsonContent, LibraryFile::class.java)
            parsed?.takeIf { it.version > 0 && it.items.isNotEmpty() }
        } catch (e: JsonSyntaxException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
