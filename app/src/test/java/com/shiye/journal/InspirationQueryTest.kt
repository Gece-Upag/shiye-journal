package com.shiye.journal

import com.shiye.journal.domain.InspirationQuery
import com.shiye.journal.domain.MillisRange
import com.shiye.journal.util.DateTimeUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 灵感搜索条件（规格 §6.3）。
 *
 * 覆盖的边界都是真会遇到的：只填开始、只填结束、开始晚于结束、日期不合法。
 */
class InspirationQueryTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    @Test
    fun `两端都空是无限区间`() {
        val range = InspirationQuery.range(from = "", to = "", today = today)
        assertTrue(range.isUnbounded())
        assertTrue(0L in range)
        assertTrue(System.currentTimeMillis() in range)
    }

    @Test
    fun `结束日期当天全天都能被搜到`() {
        val range = InspirationQuery.range(from = "2026-03-10", to = "2026-03-15", today = today)

        // 开始当天 00:00 命中
        assertTrue(DateTimeUtil.startOfDayMillis(LocalDate.of(2026, 3, 10)) in range)
        // 结束当天 23:59:59.999 也命中（右端点开区间的意义就在这）
        assertTrue(DateTimeUtil.endOfDayMillis(LocalDate.of(2026, 3, 15)) in range)
        // 结束日期的第二天 00:00 不该命中
        assertFalse(DateTimeUtil.startOfDayMillis(LocalDate.of(2026, 3, 16)) in range)
        // 开始日期前一天不该命中
        assertFalse(DateTimeUtil.endOfDayMillis(LocalDate.of(2026, 3, 9)) in range)
    }

    @Test
    fun `只填一端时另一端不限制`() {
        val onlyFrom = InspirationQuery.range(from = "2026-03-15", to = "", today = today)
        assertTrue(DateTimeUtil.endOfDayMillis(LocalDate.of(2026, 3, 20)) in onlyFrom)
        assertFalse(DateTimeUtil.endOfDayMillis(LocalDate.of(2026, 3, 14)) in onlyFrom)

        val onlyTo = InspirationQuery.range(from = "", to = "2026-03-15", today = today)
        assertTrue(DateTimeUtil.startOfDayMillis(LocalDate.of(2026, 1, 1)) in onlyTo)
        assertFalse(DateTimeUtil.startOfDayMillis(LocalDate.of(2026, 3, 16)) in onlyTo)
    }

    @Test
    fun `开始晚于结束按不限制处理`() {
        val range = InspirationQuery.range(from = "2026-03-20", to = "2026-03-01", today = today)
        assertTrue(range.isUnbounded())
    }

    @Test
    fun `日期不合法时该端不限制`() {
        val range = InspirationQuery.range(from = "2026-02-30", to = "2026-03-01", today = today)
        assertEquals(0L, range.fromInclusive)
    }

    @Test
    fun `关键词忽略英文字母大小写`() {
        assertTrue(InspirationQuery.matches("Idea for Sunday", 0L, keyword = "idea"))
        assertTrue(InspirationQuery.matches("idea", 0L, keyword = "IDEA"))
    }

    @Test
    fun `关键词为空时不约束`() {
        assertTrue(InspirationQuery.matches("任意内容", 0L, keyword = "   "))
    }

    @Test
    fun `关键词命中但日期不在区间时不显示`() {
        val day = LocalDate.of(2026, 3, 15)
        val range = InspirationQuery.range(from = "2026-03-20", to = "2026-03-25", today = today)

        assertFalse(InspirationQuery.matches("idea", DateTimeUtil.startOfDayMillis(day), "idea", range))
    }

    @Test
    fun `全部条件都命中才显示`() {
        val day = LocalDate.of(2026, 3, 15)
        val range = InspirationQuery.range(from = "2026-03-10", to = "2026-03-15", today = today)

        assertTrue(InspirationQuery.matches("idea", DateTimeUtil.startOfDayMillis(day), "idea", range))
    }

    @Test
    fun `判定是否无筛选条件`() {
        assertTrue(InspirationQuery.hasNoCondition(keyword = "  ", from = "", to = ""))
        assertFalse(InspirationQuery.hasNoCondition(keyword = "", from = "2026-03-01", to = ""))
    }

    @Test
    fun `区间默认即可覆盖全部记录`() {
        val all = MillisRange(0L, Long.MAX_VALUE)
        assertTrue(Long.MAX_VALUE - 1 in all)
    }

    @Test
    fun `toStorageKey 之外不依赖具体日期格式`() {
        // 保护区间的语义：结束端点是"下一天 00:00"，不是"当天 23:59:59"
        val range = InspirationQuery.range(from = "2026-03-01", to = "2026-03-01", today = today)
        assertEquals(
            DateTimeUtil.startOfNextDayMillis(LocalDate.of(2026, 3, 1)),
            range.toExclusive
        )
    }
}
