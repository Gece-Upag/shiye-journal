package com.shiye.journal

import com.shiye.journal.domain.LedgerPeriod
import com.shiye.journal.domain.PeriodKind
import com.shiye.journal.domain.canGoForward
import com.shiye.journal.domain.shift
import com.shiye.journal.domain.title
import com.shiye.journal.util.DateTimeUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 统计周期（规格 §5.7）。
 *
 * 重点覆盖三件容易算错的事：周是周一开头、翻next月的长度不固定会跳月、以及跨年。
 */
class LedgerPeriodTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 15) // 周四

    @Test
    fun day_period_is_single_day() {
        val period = LedgerPeriod(PeriodKind.DAY, day)

        assertEquals("2026-10-15", period.fromDate)
        assertEquals("2026-10-15", period.toDate)
        assertTrue(period.contains(day))
        assertFalse(period.contains(day.plusDays(1)))
    }

    @Test
    fun week_starts_on_monday() {
        val period = LedgerPeriod(PeriodKind.WEEK, day)

        // 2026-10-15 是周四，所属周应回到周一 10-12，到周日 10-18
        assertEquals("2026-10-12", period.fromDate)
        assertEquals("2026-10-18", period.toDate)
    }

    @Test
    fun week_crossing_month_boundary() {
        // 2026-11-01（周日）所属周：10-26 ~ 11-01
        val period = LedgerPeriod(PeriodKind.WEEK, LocalDate.of(2026, 11, 1))

        assertEquals("2026-10-26", period.fromDate)
        assertEquals("2026-11-01", period.toDate)
    }

    @Test
    fun month_uses_actual_month_length() {
        val february = LedgerPeriod(PeriodKind.MONTH, LocalDate.of(2026, 2, 17))

        assertEquals("2026-02-01", february.fromDate)
        assertEquals("2026-02-28", february.toDate)

        val leap = LedgerPeriod(PeriodKind.MONTH, LocalDate.of(2028, 2, 1))
        assertEquals("2028-02-29", leap.toDate)
    }

    @Test
    fun year_boundaries() {
        val period = LedgerPeriod(PeriodKind.YEAR, day)

        assertEquals("2026-01-01", period.fromDate)
        assertEquals("2026-12-31", period.toDate)
    }

    @Test
    fun shift_forward_and_back() {
        val period = LedgerPeriod(PeriodKind.MONTH, day)

        assertEquals("2026年11月", period.shift(1).title())
        assertEquals("2026-11-01", period.shift(1).fromDate)
        assertEquals("2026-09-30", period.shift(-1).toDate)
    }

    @Test
    fun shifting_repeatedly_does_not_skip_month() {
        // 从 1 月 31 日出发往前翻 3 个月：1 → 2 → 3 → 4，不能跳到 5 月
        var period = LedgerPeriod(PeriodKind.MONTH, LocalDate.of(2026, 1, 31))
        repeat(3) { period = period.shift(1) }

        assertEquals("2026-04-01", period.fromDate)
    }

    @Test
    fun shifting_day_crosses_year() {
        var period = LedgerPeriod(PeriodKind.DAY, LocalDate.of(2026, 12, 31))
        period = period.shift(1)
        assertEquals("2027-01-01", period.fromDate)

        period = period.shift(-1)
        assertEquals("2026-12-31", period.fromDate)
    }

    @Test
    fun forward_is_limited_by_today() {
        val today = LocalDate.of(2026, 10, 15)
        val thisMonth = LedgerPeriod(PeriodKind.MONTH, today)
        val nextMonth = thisMonth.shift(1)

        // 本月还没过完，但末日（10-31）已经不早于今天所在周期的末日，所以不能再往后翻
        assertFalse(thisMonth.canGoForward(today))
        assertFalse(nextMonth.canGoForward(today))
        assertTrue(thisMonth.shift(-1).canGoForward(today))
    }

    @Test
    fun titles_are_readable() {
        assertEquals("2026年10月15日 周四", LedgerPeriod(PeriodKind.DAY, day).title())
        assertEquals("10月12日 - 10月18日", LedgerPeriod(PeriodKind.WEEK, day).title())
        assertEquals("2026年10月", LedgerPeriod(PeriodKind.MONTH, day).title())
        assertEquals("2026年", LedgerPeriod(PeriodKind.YEAR, day).title())
    }

    @Test
    fun current_period_contains_today() {
        val today = DateTimeUtil.today()

        PeriodKind.entries.forEach { kind ->
            val period = LedgerPeriod.current(kind, today)
            assertTrue("$kind 应包含今天", period.contains(today))
        }
    }
}
