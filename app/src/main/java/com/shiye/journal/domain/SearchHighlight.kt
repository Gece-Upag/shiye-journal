package com.shiye.journal.domain

/**
 * 搜索结果的片段截取与高亮定位。
 *
 * 规格 §4.6 的要求落到这里的几条：
 * - **忽略英文字母大小写** → 所有比较先转小写（[normalize]）。
 * - **中文不做分词，直接包含匹配** → 就是 indexOf 子串查找。
 * - **显示匹配到的文本片段并高亮** → [excerpt] 截一段上下文，[ranges] 给出关键词区间。
 * - **空格或没有匹配内容时显示"无结果"** → 空关键词由调用方判定，这里对空关键词返回空区间。
 *
 * 纯 Kotlin，不碰 SQL，所以大小写与边界情况都能单测覆盖。
 */
object SearchHighlight {

    /** 片段最大长度；超出时两侧用 … 省略 */
    const val EXCERPT_MAX_LENGTH = 60

    /** 关键词命中时，向前保留的上下文字数 */
    private const val LEAD_CONTEXT = 12

    private fun normalize(text: String): String = text.lowercase()

    /**
     * 从 [text] 里截出包含 [keyword] 的片段。
     *
     * - 关键词为空或没命中 → 从头截取（调用方拿它当摘要用）。
     * - 命中位置靠后 → 从命中前 [LEAD_CONTEXT] 个字开始，保证关键词不会出现在最末尾看不见。
     * - 压平换行：片段要在一行里显示，换行符换成空格。
     *
     * @return 截断后的片段；原文为空则返回空串。
     */
    fun excerpt(text: String, keyword: String, maxLength: Int = EXCERPT_MAX_LENGTH): String {
        if (text.isEmpty()) return ""
        val flat = text.replace('\n', ' ').replace('\r', ' ').trim()
        if (flat.isEmpty()) return ""

        val needle = normalize(keyword.trim())
        val start = if (needle.isEmpty()) {
            0
        } else {
            val index = normalize(flat).indexOf(needle)
            if (index < 0) 0 else (index - LEAD_CONTEXT).coerceAtLeast(0)
        }

        val end = (start + maxLength).coerceAtMost(flat.length)
        val head = if (start > 0) "…" else ""
        val tail = if (end < flat.length) "…" else ""
        return head + flat.substring(start, end) + tail
    }

    /**
     * 找出 [text] 中全部 [keyword] 的区间，供 UI 高亮。
     *
     * 大小写不敏感；关键词为空时返回空列表（不做全篇高亮，那没有意义）。
     *
     * @return 按出现顺序排列的区间列表，互不重叠。
     */
    fun ranges(text: String, keyword: String): List<IntRange> {
        val needle = normalize(keyword.trim())
        if (needle.isEmpty()) return emptyList()

        val haystack = normalize(text)
        val result = ArrayList<IntRange>()
        var from = 0
        while (from <= haystack.length - needle.length) {
            val index = haystack.indexOf(needle, from)
            if (index < 0) break
            result.add(index until (index + needle.length))
            from = index + needle.length
        }
        return result
    }
}
