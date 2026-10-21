package com.shiye.journal

import com.shiye.journal.domain.DateInput
import com.shiye.journal.domain.DateInputResult
import com.shiye.journal.domain.LedgerPeriod
import com.shiye.journal.domain.PeriodKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 手输日期的解析规则：[DateInput.parseDate]（通用日期框）与
 * [DateInput.parsePeriod]（统计页，带粒度与未来拦截）。
 *
 * 这里覆盖的都是"只在手机上点点看"容易漏掉的边界：
 * 位数与粒度的对应、非法日期（2 月 30 日）必须报错而不是被悄悄改掉、
 * 通用日期框**不拦未来**（到期日天然在未来）、统计页**要拦未来**。
 */
class DateInputTest {

    private val today = LocalDate.of(2026, 10, 15)

    // ---------- 输入过程中的自动整理 ----------

    @Test
    fun formatDigits_自动补分隔线() {
        assertEquals("2026-10-15", DateInput.formatDigits("20261015"))
        assertEquals("2026-10", DateInput.formatDigits("202610"))
        assertEquals("2026", DateInput.formatDigits("2026"))
    }

    @Test
    fun formatDigits_滤掉非数字_并截断到8位() {
        assertEquals("2026-10-15", DateInput.formatDigits("2026-10-15"))
        assertEquals("2026-10-15", DateInput.formatDigits("2026年10月15日"))
        assertEquals("2026-10-15", DateInput.formatDigits("2026-10-15  20:30"))
        assertEquals("2026-10-15", DateInput.formatDigits("2026101566"))
    }

    @Test
    fun placeholder_随粒度变化() {
        assertEquals("2026-10-15", DateInput.placeholder(PeriodKind.DAY, today))
        assertEquals("2026-10-15", DateInput.placeholder(PeriodKind.WEEK, today))
        assertEquals("2026-10", DateInput.placeholder(PeriodKind.MONTH, today))
        assertEquals("2026", DateInput.placeholder(PeriodKind.YEAR, today))
    }

    // ---------- 解析成功 ----------

    @Test
    fun parse_日粒度_完整日期落到当天() {
        val result = DateInput.parsePeriod("2026-10-15", PeriodKind.DAY, today)
        assertTrue(result is DateInputResult.Ok)
        val period = LedgerPeriod(PeriodKind.DAY, (result as DateInputResult.Ok).date)
        assertEquals("2026-10-15", period.fromDate)
        assertEquals("2026-10-15", period.toDate)
    }

    @Test
    fun parse_周粒度_敲任意一天落到那一周() {
        // 2026-10-15 是周四，所在周是周一 10-12 到周日 10-18
        val date = (DateInput.parsePeriod("20261015", PeriodKind.WEEK, today) as DateInputResult.Ok).date
        val period = LedgerPeriod(PeriodKind.WEEK, date)
        assertEquals("2026-10-12", period.fromDate)
        assertEquals("2026-10-18", period.toDate)
    }

    @Test
    fun parse_月粒度_可以只敲年月() {
        val date = (DateInput.parsePeriod("202603", PeriodKind.MONTH, today) as DateInputResult.Ok).date
        val period = LedgerPeriod(PeriodKind.MONTH, date)
        assertEquals("2026-03-01", period.fromDate)
        assertEquals("2026-03-31", period.toDate)
    }

    @Test
    fun parse_月粒度_敲完整日期则落到该月() {
        val date = (DateInput.parsePeriod("2026-03-09", PeriodKind.MONTH, today) as DateInputResult.Ok).let { it.date }
        val period = LedgerPeriod(PeriodKind.MONTH, date)
        assertEquals("2026-03-01", period.fromDate)
        assertEquals("2026-03-31", period.toDate)
    }

    @Test
    fun parse_年粒度_可以只敲年份() {
        val period = LedgerPeriod(
            PeriodKind.YEAR,
            (DateInput.parsePeriod("2019", PeriodKind.YEAR, today) as DateInputResult.Ok).date
        )
        assertEquals("2019-01-01", period.fromDate)
        assertEquals("2019-12-31", period.toDate)
    }

    @Test
    fun parse_闰年2月29日可以() {
        val period = LedgerPeriod(
            PeriodKind.DAY,
            (DateInput.parsePeriod("20240229", PeriodKind.DAY, today) as DateInputResult.Ok).date
        )
        assertEquals("2024-02-29", period.fromDate)
    }

    // ---------- parseDate：通用日期框，只看日期本身，不判断未来 ----------

    @Test
    fun parseDate_返回日期本身() {
        val date = (DateInput.parseDate("2026-10-15") as DateInputResult.Ok).date
        assertEquals(LocalDate.of(2026, 10, 15), date)
    }

    @Test
    fun parseDate_允许未来日期() {
        // 借款的到期日天然在未来；记一笔也可能预记。日期框不该在这里拦。
        val date = (DateInput.parseDate("2030-01-01") as DateInputResult.Ok).date
        assertEquals(LocalDate.of(2030, 1, 1), date)
    }

    @Test
    fun parseDate_位数不全就报错() {
        assertTrue(DateInput.parseDate("202610") is DateInputResult.Error)
        assertTrue(DateInput.parseDate("2026") is DateInputResult.Error)
    }

    @Test
    fun parseDate_非法日期报错而不是修正() {
        val result = DateInput.parseDate("20260230")
        assertTrue(result is DateInputResult.Error)
        assertTrue((result as DateInputResult.Error).message.contains("2026-02-30"))
    }

    // ---------- 解析失败：一律不动当前周期，只给原因 ----------

    @Test
    fun parse_空输入有提示() {
        val result = DateInput.parsePeriod("", PeriodKind.DAY, today)
        assertTrue(result is DateInputResult.Error)
        assertTrue(((result as DateInputResult.Error).message).isNotEmpty())
    }

    @Test
    fun parse_非法日期不静默修正() {
        // 2026 年不是闰年，2 月没有 30 日。
        // 这里要的是**报错**，不是像 SMART 模式那样悄悄变成 2 月 28 日。
        val result = DateInput.parsePeriod("20260230", PeriodKind.DAY, today)
        assertTrue(result is DateInputResult.Error)
        assertTrue(((result as DateInputResult.Error).message).contains("2026-02-30"))
    }

    @Test
    fun parse_月份越界报错() {
        assertTrue(DateInput.parsePeriod("20261301", PeriodKind.DAY, today) is DateInputResult.Error)
    }

    @Test
    fun parse_日粒度下位数不够就报错() {
        // "202610" 在日粒度下是残缺的（不知道几号），不能猜成 1 号
        assertTrue(DateInput.parsePeriod("202610", PeriodKind.DAY, today) is DateInputResult.Error)
        assertTrue(DateInput.parsePeriod("2026", PeriodKind.DAY, today) is DateInputResult.Error)
    }

    @Test
    fun parse_月粒度下只敲年份也报错() {
        assertTrue(DateInput.parsePeriod("2026", PeriodKind.MONTH, today) is DateInputResult.Error)
    }

    @Test
    fun parse_未来日期要拦住() {
        assertTrue(DateInput.parsePeriod("2027-01-01", PeriodKind.DAY, today) is DateInputResult.Error)
        assertTrue(DateInput.parsePeriod("2027", PeriodKind.YEAR, today) is DateInputResult.Error)
    }

    @Test
    fun parse_今天所在的周期可以进() {
        // 边界：等于今天的周期不算未来
        assertTrue(DateInput.parsePeriod("2026-10-15", PeriodKind.DAY, today) is DateInputResult.Ok)
        assertTrue(DateInput.parsePeriod("2026", PeriodKind.YEAR, today) is DateInputResult.Ok)
    }

    // ---------- 边敲边补分隔线时的光标位置 ----------

    @Test
    fun caret_按顺序敲完整个日期_光标始终在末尾() {
        // 回归：第 5 位和第 7 位数字会凭空补出一个 `-`，
        // 光标若沿用字符串下标就会被留在 `-` 前面，下一个数字插成 2026-10-61
        val typed = typeDigits("20261016")
        assertEquals("2026-10-16", typed.text)
        assertEquals(typed.text.length, typed.caret)
    }

    @Test
    fun caret_自己敲横线也照样对() {
        val typed = typeDigits("2026-10-16")
        assertEquals("2026-10-16", typed.text)
        assertEquals(typed.text.length, typed.caret)
    }

    @Test
    fun caret_每一步都落在末尾() {
        var typed = DateInput.DateEdit("", 0)
        "20261016".forEach { ch ->
            typed = typeChar(typed, ch)
            assertEquals("敲了 $ch 之后：${typed.text}", typed.text.length, typed.caret)
        }
    }

    @Test
    fun caret_退格删掉的是数字() {
        assertEquals(DateInput.DateEdit("2026-10", 7), backspace("2026-10-1", 9))
        assertEquals(DateInput.DateEdit("2026-1", 6), backspace("2026-10", 7))
        assertEquals(DateInput.DateEdit("202", 3), backspace("2026", 4))
    }

    @Test
    fun caret_退格删掉分隔线时_连它前面的数字一起删() {
        // 光标在最后的 1 之前（"2026-10-|1"），按退格删到的是分隔线。
        // 只挪光标不删数字的话，这一次退格就"什么都没少"，手感像卡住
        assertEquals(DateInput.DateEdit("2026-11", 6), backspace("2026-10-1", 8))
    }

    @Test
    fun caret_超长输入被截断后光标落到末尾() {
        // 8 位数字已经满了，再敲就截掉，光标不能跑到串外面去
        val full = typeDigits("20261016")
        val extra = typeChar(full, '9')
        assertEquals("2026-10-16", extra.text)
        assertEquals(extra.text.length, extra.caret)
    }

    @Test
    fun caretOf_超出数字个数时返回末尾() {
        assertEquals(0, DateInput.caretOf("2026-10-15", 0))
        assertEquals(4, DateInput.caretOf("2026-10-15", 4)) // 第 4 个数字后面就是 `-`
        assertEquals(10, DateInput.caretOf("2026-10-15", 8))
        assertEquals(10, DateInput.caretOf("2026-10-15", 99))
    }

    /** 模拟用户在光标处敲一个字符。 */
    private fun typeChar(current: DateInput.DateEdit, ch: Char): DateInput.DateEdit {
        val raw = current.text.substring(0, current.caret) + ch + current.text.substring(current.caret)
        return DateInput.applyEdit(current.text, raw, current.caret + 1)
    }

    private fun typeDigits(digits: String): DateInput.DateEdit {
        var typed = DateInput.DateEdit("", 0)
        digits.forEach { typed = typeChar(typed, it) }
        return typed
    }

    /** 模拟用户在光标处按一次退格。 */
    private fun backspace(text: String, caret: Int): DateInput.DateEdit {
        val raw = text.substring(0, caret - 1) + text.substring(caret)
        return DateInput.applyEdit(text, raw, caret - 1)
    }
}
