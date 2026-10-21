package com.shiye.journal.domain

import com.shiye.journal.util.DateTimeUtil
import java.time.LocalDate

/**
 * 左闭右开的毫秒区间，与 SQL 的 `>= from AND < to` 一一对应。
 *
 * 右端点**开区间**是刻意的：结束日期那天 23:59:59.999 的灵感也必须能被搜到，
 * 用闭区间就得手工去凑"当天最后 1 毫秒"，那是 bug 温床。
 */
data class MillisRange(
    val fromInclusive: Long,
    val toExclusive: Long
) {

    operator fun contains(millis: Long): Boolean =
        millis >= fromInclusive && millis < toExclusive

    /** 两端都没限制（此时列出全部） */
    fun isUnbounded(): Boolean = fromInclusive <= 0L && toExclusive >= Long.MAX_VALUE
}

/**
 * 灵感搜索条件（规格 §6.3）。
 *
 * 关键语义：**只有全部条件都匹配时才显示记录**（AND，不是 OR）。
 * 关键词、开始日期、结束日期任意组合，缺哪个就不约束哪个。
 */
object InspirationQuery {

    /** 关键词只做去空白；大小写交给匹配时的 ignoreCase，不在这里改大小写 */
    fun normalizeKeyword(keyword: String?): String = keyword?.trim().orEmpty()

    /**
     * 日期区间 → 毫秒区间。
     *
     * 任一端为空表示"不限"：开始用 0，结束用 [Long.MAX_VALUE]
     * （因为 DAO 的右端点是开区间，这里直接给 Long.MAX 即可覆盖全部）。
     *
     * **端点顺序颠倒时按"不限"处理**，而不是返回一个查不出东西的空区间 ——
     * 用户把开始日期填得比结束日期晚，看到的是"无结果"，
     * 但真实原因不是"没有这条灵感"，而是条件本身矛盾，按无条件列出更符合预期。
     *
     * 日期非法（比如 `2026-02-30`）同样降级为不限，不抛异常打断输入。
     */
    fun range(from: String?, to: String?, today: LocalDate = DateTimeUtil.today()): MillisRange {
        val start = parseDay(from)
        val end = parseDay(to)

        val safeStart = start?.let { DateTimeUtil.startOfDayMillis(it) } ?: 0L
        val safeEnd = end?.let { DateTimeUtil.startOfNextDayMillis(it) } ?: Long.MAX_VALUE

        return if (start != null && end != null && safeEnd <= safeStart) {
            MillisRange(0L, Long.MAX_VALUE)
        } else {
            MillisRange(safeStart, safeEnd)
        }
    }

    /**
     * 解析一个日期端点；空值或非法值都返回 null（= 这一端不限制）。
     *
     * 用 [DateInput.parseDate] 而不是 [DateTimeUtil.parseDate]：
     * 后者是宽松解析，会把 2 月 30 日悄悄变成 2 月 28 日，
     * 用户在搜索页敲错一天却查出一个"看起来正常"的结果，比报错更难发现。
     */
    private fun parseDay(value: String?): LocalDate? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return when (val result = DateInput.parseDate(raw)) {
            is DateInputResult.Ok -> result.date
            is DateInputResult.Error -> null
        }
    }

    /**
     * 单条灵感是否命中全部条件。
     *
     * 与 DAO 里的 SQL 是同一套语义，这里用于在单测里钉住它 ——
     * SQL 改了而这里没改（或反过来）时测试会红，避免"列表和搜索结果对不上"。
     */
    fun matches(
        content: String,
        createdAt: Long,
        keyword: String = "",
        range: MillisRange = MillisRange(0L, Long.MAX_VALUE)
    ): Boolean {
        val k = normalizeKeyword(keyword)
        val hitKeyword = k.isEmpty() || content.contains(k, ignoreCase = true)
        return hitKeyword && createdAt in range
    }

    /** 是否一个筛选条件都没填（此时列出全部，标题写"全部灵感"） */
    fun hasNoCondition(keyword: String, from: String, to: String): Boolean =
        normalizeKeyword(keyword).isEmpty() && from.isBlank() && to.isBlank()
}
