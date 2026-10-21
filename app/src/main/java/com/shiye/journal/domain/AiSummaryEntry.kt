package com.shiye.journal.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一篇日记的 AI 建议的**一条**记录。
 *
 * 为什么要带时间戳：同一篇日记可以反复要建议，
 * 用户要能看出哪条是刚才那次、哪条是上周的，才好做取舍。
 *
 * 为什么是"多条"：模型每次给的建议不一样，
 * 覆盖掉上一次等于逼用户当场做决定；留着让他慢慢挑，更符合记日记的节奏。
 */
@Serializable
data class AiSummaryEntry(
    val text: String,

    /** epoch 毫秒 */
    val createdAt: Long
)

/**
 * AI 建议列表的读写。**纯 Kotlin，可单测。**
 *
 * 存的是 JSON 数组，塞在 diary_entries 的 ai_summary 这一列里
 * （规格边界：它不属于模板字段，换模板不该把它弄丢）。
 */
object AiSummaryList {

    private val json = Json { ignoreUnknownKeys = true }

    /** 空列表的落盘形式。用空数组而不是空串，省得每次读取都要特判 */
    const val EMPTY = "[]"

    /**
     * 解析。**必须兼容旧数据**：早期版本这一列存的是纯文本，
     * 不是 JSON。解析不出来就当成"一条没有时间的旧记录"，而不是丢弃 ——
     * 用户以前要过的建议凭空消失比格式不统一严重得多。
     */
    fun parse(raw: String, fallbackTime: Long = 0L): List<AiSummaryEntry> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val parsed = runCatching {
            json.decodeFromString<List<AiSummaryEntry>>(trimmed)
        }.getOrNull()
        if (parsed != null) return parsed.filter { it.text.isNotBlank() }
        // 旧格式：整段就是建议内容
        return listOf(AiSummaryEntry(text = trimmed, createdAt = fallbackTime))
    }

    fun write(entries: List<AiSummaryEntry>): String =
        json.encodeToString(entries)

    /**
     * 按**从晚到早**排序后落盘。
     *
     * 用户口径：新要的建议排在最前面（他最想看的就是刚拿到的那条），
     * 旧的依次往后。
     */
    fun writeNewestFirst(entries: List<AiSummaryEntry>): String =
        write(entries.sortedByDescending { it.createdAt })

    /**
     * 把这次的结果并进已有列表。
     *
     * @param existing 已有的建议
     * @param kept 用户勾选要保留的那些（含本次的，也可能不含）
     * @param newResult 本次的结果；为 null 表示用户没勾本次
     * @param now 本次的时间戳
     */
    fun merge(
        existing: List<AiSummaryEntry>,
        kept: Set<String>,
        newResult: String?,
        now: Long
    ): List<AiSummaryEntry> {
        val result = mutableListOf<AiSummaryEntry>()
        if (!newResult.isNullOrBlank()) {
            result += AiSummaryEntry(text = newResult, createdAt = now)
        }
        // 保留的旧条目沿用各自原来的时间，不会被记成"刚生成的"
        result += existing.filter { it.text in kept }
        return result
    }
}
