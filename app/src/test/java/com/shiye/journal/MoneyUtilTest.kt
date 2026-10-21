package com.shiye.journal

import com.shiye.journal.util.MoneyUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 金额换算测试。
 *
 * 这些用例针对的是"记账里最容易悄悄出错"的地方：元与分的来回换算。
 * 全应用金额一律用 Long 分存储，一旦这里算错，账目就会对不上。
 */
class MoneyUtilTest {

    @Test
    fun `分转展示字符串`() {
        assertEquals("¥0.00", MoneyUtil.format(0))
        assertEquals("¥0.01", MoneyUtil.format(1))
        assertEquals("¥12.34", MoneyUtil.format(1234))
        assertEquals("¥1,234.56", MoneyUtil.format(123456))
        assertEquals("¥1,000,000.00", MoneyUtil.format(100_000_000))
    }

    @Test
    fun `负数显示负号在前`() {
        assertEquals("-¥12.34", MoneyUtil.format(-1234))
    }

    @Test
    fun `带符号金额用于收支区分`() {
        assertEquals("+¥12.34", MoneyUtil.formatSigned(1234, positive = true))
        // 支出用 U+2212 减号，与加号视觉对齐
        assertEquals("−¥12.34", MoneyUtil.formatSigned(1234, positive = false))
    }

    @Test
    fun `输入元字符串转分`() {
        assertEquals(1234L, MoneyUtil.parseToCents("12.34"))
        assertEquals(1200L, MoneyUtil.parseToCents("12"))
        // 一位小数要补零成两位，不能当成 1.2 分
        assertEquals(1250L, MoneyUtil.parseToCents("12.5"))
        assertEquals(0L, MoneyUtil.parseToCents("0"))
        assertEquals(1234L, MoneyUtil.parseToCents("¥12.34"))
        assertEquals(123456L, MoneyUtil.parseToCents("1,234.56"))
    }

    @Test
    fun `非法输入返回 null 由调用方提示`() {
        assertNull(MoneyUtil.parseToCents(""))
        assertNull(MoneyUtil.parseToCents("   "))
        assertNull(MoneyUtil.parseToCents("abc"))
        assertNull(MoneyUtil.parseToCents("12.345"))
        assertNull(MoneyUtil.parseToCents("-1"))
        assertNull(MoneyUtil.parseToCents("1.2.3"))
    }

    @Test
    fun `元与分往返一致`() {
        // 往返换算：分 → 输入文本 → 分，必须回到原值
        val samples = listOf(0L, 1L, 99L, 100L, 1234L, 99999999L)
        samples.forEach { cents ->
            val text = MoneyUtil.centsToInputText(cents)
            val back = if (text.isEmpty()) 0L else MoneyUtil.parseToCents(text)
            assertEquals("往返失败: $cents -> $text", cents, back)
        }
    }
}
