package com.shiye.journal.domain

import com.shiye.journal.util.DateTimeUtil
import java.time.LocalDate

/**
 * 统计周期（规格 §5.7）。
 *
 * 四种粒度，边界按自然区间；周按**周一至周日**（国内习惯），
 * 不用 Locale 决定，免得同一份数据在不同设备上算出不同的"本周"。
 */
enum class PeriodKind(val label: String) {
    /** 自然日 */
    DAY("日"),

    /** 周一至周日 */
    WEEK("周"),

    /** 自然月 */
    MONTH("月"),

    /** 自然年 */
    YEAR("年")
}

/**
 * 一个具体的统计周期。**不可变**：前后切换返回新实例，不在原地改。
 *
 * @param anchor 落在目标周期内的任意一天。具体是哪天不重要 ——
 *   所有换算都先折算到周期首日再做，避免"同月不同天"被当成两个周期。
 */
data class LedgerPeriod(val kind: PeriodKind, val anchor: LocalDate) {

    /** 周期首日（含） */
    val from: LocalDate get() = when (kind) {
        PeriodKind.DAY -> anchor
        PeriodKind.WEEK -> DateTimeUtil.weekStart(anchor)
        PeriodKind.MONTH -> DateTimeUtil.monthStart(anchor)
        PeriodKind.YEAR -> DateTimeUtil.yearStart(anchor)
    }

    /** 周期末日（含） */
    val to: LocalDate get() = when (kind) {
        PeriodKind.DAY -> anchor
        PeriodKind.WEEK -> DateTimeUtil.weekStart(anchor).plusDays(6)
        PeriodKind.MONTH -> DateTimeUtil.monthEnd(anchor)
        PeriodKind.YEAR -> DateTimeUtil.yearEnd(anchor)
    }

    /** 供 SQL 查询的左端点，yyyy-MM-dd（含） */
    val fromDate: String get() = DateTimeUtil.formatDate(from)

    /** 供 SQL 查询的右端点，yyyy-MM-dd（含） */
    val toDate: String get() = DateTimeUtil.formatDate(to)

    /** 是否包含某一天 */
    fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)

    companion object {
        /** 取今天所在的同类周期 */
        fun current(
            kind: PeriodKind,
            today: LocalDate = DateTimeUtil.today()
        ): LedgerPeriod = LedgerPeriod(kind, today)
    }
}

/** 周期标题，如「2026年10月15日 周四」「10月12日 - 10月18日」「2026年10月」「2026年」 */
fun LedgerPeriod.title(): String = when (kind) {
    PeriodKind.DAY -> DateTimeUtil.formatDateChinese(fromDate) + " " + weekdayOf(from)
    PeriodKind.WEEK -> "${short(from)} - ${short(to)}"
    PeriodKind.MONTH -> "${from.year}年${from.monthValue}月"
    PeriodKind.YEAR -> "${from.year}年"
}

/**
 * 前后切换一个周期。
 *
 * 直接对 [LedgerPeriod.anchor] 做位移而不是"从 boundary 再算一次"：
 * 月和年的长度不固定，若从边界推（例如 1 月 31 日 +1 个月 → 3 月 3 日）会跳月，
 * 用 anchor 位移则可以保证连续翻页时不会漏掉或重复某个月。
 */
fun LedgerPeriod.shift(delta: Int): LedgerPeriod = copy(
    anchor = when (kind) {
        PeriodKind.DAY -> anchor.plusDays(delta.toLong())
        PeriodKind.WEEK -> anchor.plusWeeks(delta.toLong())
        PeriodKind.MONTH -> anchor.plusMonths(delta.toLong())
        PeriodKind.YEAR -> anchor.plusYears(delta.toLong())
    }
)

/**
 * 是否允许往后翻。
 *
 * 未来的周期查出来一定是空的，与其让用户翻出一堆空页，不如向前留到"今天所在周期"为止。
 * 往回翻不限制 —— 翻到用户还没记账的年份，看到空也比看不到强。
 */
fun LedgerPeriod.canGoForward(today: LocalDate = DateTimeUtil.today()): Boolean {
    val currentEnd = LedgerPeriod(kind, today).to
    return to.isBefore(currentEnd)
}

/** "2026-10-15" → "10月15日" */
private fun short(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

private fun weekdayOf(date: LocalDate): String =
    WEEKDAY_NAMES[(date.dayOfWeek.value - 1).coerceIn(0, 6)]

private val WEEKDAY_NAMES = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
