package com.shiye.journal

import com.shiye.journal.util.DateTimeUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 日期工具测试。
 *
 * 重点验证两件事：
 * 1. 日期字符串格式稳定（"yyyy-MM-dd"），因为字典序即时间序，
 *    数据库里的区间筛选全靠这个性质；
 * 2. 月初月末、区间端点的换算正确，否则"本月统计"会漏掉首尾两天。
 */
class DateTimeUtilTest {

    @Test
    fun `日期格式化为 yyyy-MM-dd`() {
        assertEquals("2026-10-15", DateTimeUtil.formatDate(LocalDate.of(2026, 10, 15)))
        // 月和日必须补零，否则字典序会乱
        assertEquals("2026-01-05", DateTimeUtil.formatDate(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `解析合法与非法日期`() {
        assertEquals(LocalDate.of(2026, 10, 15), DateTimeUtil.parseDate("2026-10-15"))
        assertNull(DateTimeUtil.parseDate("2026/10/15"))
        assertNull(DateTimeUtil.parseDate(""))
        assertNull(DateTimeUtil.parseDate("不是日期"))
    }

    @Test
    fun `日期字符串可直接比较大小`() {
        // 这是"date >= :from AND date <= :to"这种 SQL 能成立的前提
        assertTrue("2026-01-01" < "2026-02-01")
        assertTrue("2026-01-09" < "2026-01-10")
        assertTrue("2025-12-31" < "2026-01-01")
    }

    @Test
    fun `中文日期格式`() {
        assertEquals("2026年10月15日", DateTimeUtil.formatDateChinese("2026-10-15"))
        assertEquals("2026年1月5日", DateTimeUtil.formatDateChinese("2026-01-05"))
    }

    @Test
    fun `流水行尾的日期加时间`() {
        assertEquals("10-21 14:30", DateTimeUtil.formatShortDateTime("2026-10-21", "14:30"))
        // 月和日都要补零：只补日的话 "3-05" 和 "10-15" 宽度不一样，列表会参差
        assertEquals("01-05 09:07", DateTimeUtil.formatShortDateTime("2026-01-05", "09:07"))
        // 时间戳取自流水自己的日期与时间，不跨时区回算
        assertEquals("03-01 00:00", DateTimeUtil.formatShortDateTime("2024-03-01", "00:00"))
    }

    @Test
    fun `流水行尾遇到非法日期退回只显示时间`() {
        // 宁可少一段日期也不要把乱码端到用户面前
        assertEquals("14:30", DateTimeUtil.formatShortDateTime("不是日期", "14:30"))
        assertEquals("14:30", DateTimeUtil.formatShortDateTime("", "14:30"))
    }

    @Test
    fun `搜索结果行尾带年份`() {
        assertEquals("2026-10-21 14:30", DateTimeUtil.formatFullDateTime("2026-10-21", "14:30"))
        // 搜索结果横跨好几年，年份是区分的关键，月和日照样补零
        assertEquals("2024-01-05 09:07", DateTimeUtil.formatFullDateTime("2024-01-05", "09:07"))
    }

    @Test
    fun `带年份格式遇到非法日期也退回只显示时间`() {
        assertEquals("14:30", DateTimeUtil.formatFullDateTime("不是日期", "14:30"))
        assertEquals("14:30", DateTimeUtil.formatFullDateTime("", "14:30"))
    }

    @Test
    fun `月份起止`() {
        val oct = LocalDate.of(2026, 10, 15)
        assertEquals(LocalDate.of(2026, 10, 1), DateTimeUtil.monthStart(oct))
        assertEquals(LocalDate.of(2026, 10, 31), DateTimeUtil.monthEnd(oct))

        // 二月闰年：2024 是闰年，2 月应有 29 天
        val feb = LocalDate.of(2024, 2, 10)
        assertEquals(LocalDate.of(2024, 2, 29), DateTimeUtil.monthEnd(feb))
        // 平年 28 天
        assertEquals(LocalDate.of(2026, 2, 28), DateTimeUtil.monthEnd(LocalDate.of(2026, 2, 10)))
    }

    @Test
    fun `区间端点左闭右开`() {
        val day = LocalDate.of(2026, 10, 15)
        val start = DateTimeUtil.startOfDayMillis(day)
        val next = DateTimeUtil.startOfNextDayMillis(day)
        val end = DateTimeUtil.endOfDayMillis(day)

        assertTrue(next > start)
        // 一天的毫秒数
        assertEquals(24 * 60 * 60 * 1000L, next - start)
        // 右端点（不含）比当天最后一毫秒多 1
        assertEquals(1L, next - end - 1 + 1)
        assertTrue(end >= start)
    }

    @Test
    fun `日期加时间换算毫秒`() {
        val at10 = DateTimeUtil.millisOf("2026-10-15", "10:30")
        val at00 = DateTimeUtil.millisOf("2026-10-15", "00:00")
        assertEquals(10 * 60 * 60 * 1000L + 30 * 60 * 1000L, at10 - at00)

        // 非法时间退回午夜，不抛异常打断输入流程
        assertEquals(at00, DateTimeUtil.millisOf("2026-10-15", "乱码"))
    }

    @Test
    fun `与 Material3 日期选择器的毫秒值往返一致`() {
        // DatePicker 用的是 UTC 毫秒，用系统时区转会差一天（东八区尤其明显），
        // 所以这两个函数的往返必须严格不变，否则搜索的日期边界会整体偏移一天
        listOf(
            LocalDate.of(2026, 10, 15),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2024, 2, 29)
        ).forEach { date ->
            assertEquals(date, DateTimeUtil.fromPickerMillis(DateTimeUtil.toPickerMillis(date)))
        }
    }

    @Test
    fun `日期选择器毫秒值取 UTC 当天零点`() {
        val millis = DateTimeUtil.toPickerMillis(LocalDate.of(2026, 10, 15))
        val date = DateTimeUtil.fromPickerMillis(millis)
        assertEquals(LocalDate.of(2026, 10, 15), date)
        // 相邻两天相差正好一天
        assertEquals(
            24 * 60 * 60 * 1000L,
            DateTimeUtil.toPickerMillis(LocalDate.of(2026, 10, 16)) - millis
        )
        assertEquals(null, DateTimeUtil.fromPickerMillis(null))
    }

    @Test
    fun `天数差用于上次备份提示`() {
        val from = LocalDate.of(2026, 10, 10)
        val to = LocalDate.of(2026, 10, 15)
        assertEquals(5L, DateTimeUtil.daysBetween(from, to))
        assertEquals(0L, DateTimeUtil.daysBetween(to, to))
        assertEquals(-5L, DateTimeUtil.daysBetween(to, from))
    }
}
