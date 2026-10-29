package com.example.collocationmemory.data.local

/**
 * 笔记本里的一条记录。
 *
 * 笔记本是用户自己的小本子：学新词/复习时点卡片上的「记」，
 * 就把这个搭配抄进来；之后可以在笔记本页里随意改写 [note]。
 *
 * 只存本地（DataStore 的 JSON），不参与任何同步或上传。
 *
 * @param id        本地唯一 id（写入时的时间戳，够用且不与 Room 的 id 混淆）
 * @param english   收进来的固定搭配（作为去重键）
 * @param chinese   收进来时词库里的释义（原样保存，方便对照）
 * @param note      用户自己写的内容，想写什么写什么
 * @param createdAt 收进本子的时间戳
 */
data class NotebookEntry(
    val id: Long,
    val english: String,
    val chinese: String,
    val note: String = "",
    val createdAt: Long = 0L
)
