package com.shiye.journal

import com.shiye.journal.domain.NumberInput
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 整数输入框的清洗。
 *
 * 守的核心是一条：**删掉字符之后，光标必须还在它逻辑上该在的地方**。
 * 光标跑掉的那个 bug 在日期框里出现过一次（自动补的 `-` 把光标甩到前面），
 * 这里同因同源，先把它钉住。
 */
class NumberInputTest {

    private fun san(text: String, cursor: Int): Pair<String, Int> =
        NumberInput.sanitize(text, cursor)

    @Test
    fun 非数字字符被丢掉_光标跟着走() {
        // 粘贴了 "6o"：o 在光标前，光标要退一位
        assertEquals("6" to 1, san("6o", 2))
        // 光标正好卡在非法字符和 6 之间（"o|6"）：非法字符没了之后，
        // 光标该留在 6 的前面，也就是 0 —— 而不是跟着原下标停到 1
        assertEquals("6" to 0, san("o6", 1))
    }

    @Test
    fun 单位后缀丢掉后光标不越界() {
        // 用户粘贴 "60s" 并一路删到末尾
        val (text, cursor) = san("60s", 3)
        assertEquals("60", text)
        assertEquals(2, cursor)
    }

    @Test
    fun 多余前导零被去掉() {
        assertEquals("60" to 2, san("0060", 4))
    }

    @Test
    fun 单独的零要留住() {
        // 用户想输 0 开头的数（比如准备输 05 分钟前的习惯），
        // 不能被吃成空串，否则一个字都敲不进去
        assertEquals("0" to 1, san("0", 1))
    }

    @Test
    fun 前导零被删时光标不跟着跑偏() {
        // "0060" 光标在末尾 → 去掉两个零后光标跟着左移两位，落在末尾
        assertEquals("60" to 2, san("0060", 4))
        // 光标还在零点之前，不该被推成负数
        assertEquals("60" to 0, san("0060", 0))
    }

    @Test
    fun 删空是合法的中间状态() {
        assertEquals("" to 0, san("", 0))
    }

    @Test
    fun 普通输入原地不动() {
        assertEquals("60" to 2, san("60", 2))
        assertEquals("1" to 1, san("1", 1))
    }

    @Test
    fun 光标永远不越出新文本的长度() {
        // 一堆粘贴内容全被清洗掉后，光标不能还停在原来的位置
        listOf("abc", "  ", "---60---", "0".repeat(5) + "7").forEach { input ->
            val (text, cursor) = san(input, input.length)
            assertEquals(
                "光标越界：$input",
                true,
                cursor in 0..text.length
            )
        }
    }

    @Test
    fun 空串回退到当前值而不是零() {
        // 用户删空时不该超时变成 0
        assertEquals(60, NumberInput.resolve("", min = 5, max = 600, fallback = 60))
    }

    @Test
    fun 超出范围就夹到边界() {
        assertEquals(600, NumberInput.resolve("99999", min = 5, max = 600, fallback = 60))
        assertEquals(5, NumberInput.resolve("1", min = 5, max = 600, fallback = 60))
        assertEquals(60, NumberInput.resolve("60", min = 5, max = 600, fallback = 60))
    }

    @Test
    fun 超大数字不会溢出成负数() {
        // toInt 溢出会变成负数；超时变负数会让请求立刻失败且看不出原因
        assertEquals(600, NumberInput.resolve("99999999999999", min = 5, max = 600, fallback = 60))
    }
}
