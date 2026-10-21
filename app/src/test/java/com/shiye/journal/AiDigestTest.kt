package com.shiye.journal

import com.shiye.journal.features.ai.AiDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「AI 建议」用的最近七天摘要。
 *
 * 这里守住两条会直接导致结论错误的规则：
 * 日记只发摘要不发全文、借款绝不能混进收支。
 */
class AiDigestTest {

    private fun digestWithLoans(): String = AiDigest.build(
        entries = listOf("2026-10-17" to "今天写了很长很长的一段日记".repeat(20)),
        incomeCents = 500_00,
        expenseCents = 120_00,
        topExpenses = listOf("餐饮" to 80_00, "交通" to 40_00),
        loans = listOf(Triple("借入", "老王", 1000_00))
    )

    @Test
    fun 日记只发摘要不发全文() {
        val digest = digestWithLoans()
        val longEntry = "今天写了很长很长的一段日记".repeat(20)
        // 原文整段都在就说明没截断，等于把日记全文搬出去了
        assertEquals(false, digest.contains(longEntry))
        assertTrue(digest.contains("…"))
    }

    @Test
    fun 摘要里明确写出借款不计入收支() {
        val digest = digestWithLoans()
        assertTrue(digest.contains("借入借出"))
        assertTrue(digest.contains("不是收入也不是支出"))
        assertTrue(digest.contains("老王"))
    }

    @Test
    fun 收支数字来自传入的金额_借款不并入收入() {
        val digest = digestWithLoans()
        assertTrue(digest.contains("500.00"))
        assertTrue(digest.contains("120.00"))
        // 借入的 1000 元不能出现在"收入"那一行
        val incomeLine = digest.lines().first { it.contains("收入") }
        assertEquals(false, incomeLine.contains("1000.00"))
    }

    @Test
    fun 没有日记时也要说清楚() {
        val digest = AiDigest.build(
            entries = emptyList(),
            incomeCents = 0,
            expenseCents = 0,
            topExpenses = emptyList(),
            loans = emptyList()
        )
        assertTrue(digest.contains("没有写日记"))
        assertTrue(digest.contains("（无）"))
    }

    @Test
    fun 什么都没有时判为空_不必去问模型() {
        assertTrue(
            AiDigest.isEmpty(
                entries = emptyList(),
                incomeCents = 0,
                expenseCents = 0,
                loans = emptyList()
            )
        )
        // 只要有任意一项就不算空
        assertEquals(
            false,
            AiDigest.isEmpty(emptyList(), 0, 0, listOf(Triple("借入", "老王", 1)))
        )
        assertEquals(
            false,
            AiDigest.isEmpty(listOf("2026-10-17" to "写了点东西"), 0, 0, emptyList())
        )
    }
}
