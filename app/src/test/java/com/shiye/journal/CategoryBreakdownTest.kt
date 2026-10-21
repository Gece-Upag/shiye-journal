package com.shiye.journal

import com.shiye.journal.data.model.Category
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.domain.UNKNOWN_CATEGORY_ICON
import com.shiye.journal.domain.UNKNOWN_CATEGORY_NAME
import com.shiye.journal.domain.buildCategoryStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类汇总（规格 §5.7）。
 *
 * 关键是**除零保护**与"找不到分类时账不能少"这两条，其余都是排序这类常规行为。
 */
class CategoryBreakdownTest {

    private val food = Category("c-food", "餐饮", TransactionType.EXPENSE, "☕", 0, true, true, 0L)
    private val traffic = Category("c-transport", "交通", TransactionType.EXPENSE, "↗", 2, false, true, 0L)

    private fun tx(id: String, cents: Long, categoryId: String): Transaction = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amount = cents,
        categoryId = categoryId,
        accountId = "",
        note = "",
        date = "2026-10-15",
        time = "12:00",
        dateTime = 0L,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun empty_input_gives_empty_result() {
        assertTrue(buildCategoryStats(emptyList(), listOf(food)).isEmpty())
    }

    @Test
    fun same_category_is_summed_up() {
        val result = buildCategoryStats(
            transactions = listOf(tx("a", 1500, "c-food"), tx("b", 2500, "c-food")),
            categories = listOf(food)
        )

        assertEquals(1, result.size)
        assertEquals(4000L, result.first().cents)
    }

    @Test
    fun sorted_by_amount_descending() {
        val result = buildCategoryStats(
            transactions = listOf(tx("a", 100, "c-food"), tx("b", 900, "c-transport")),
            categories = listOf(food, traffic)
        )

        assertEquals(listOf("交通", "餐饮"), result.map { it.name })
    }

    @Test
    fun archived_category_still_counted() {
        // traffic 是归档分类（active=false），历史流水仍归入它
        val result = buildCategoryStats(
            transactions = listOf(tx("a", 500, "c-transport")),
            categories = listOf(food, traffic)
        )

        assertEquals("交通", result.single().name)
        assertEquals(500L, result.single().cents)
    }

    @Test
    fun missing_category_falls_back_to_unknown() {
        val result = buildCategoryStats(
            transactions = listOf(tx("a", 700, "c-deleted")),
            categories = listOf(food)
        )

        assertEquals(UNKNOWN_CATEGORY_NAME, result.single().name)
        assertEquals(UNKNOWN_CATEGORY_ICON, result.single().icon)
        assertEquals(700L, result.single().cents)
    }

    @Test
    fun ratio_is_never_divided_by_zero() {
        // 金额全是 0 的情况（理论上不该出现，但除零会让整页统计崩掉）
        val result = buildCategoryStats(
            transactions = listOf(tx("a", 0, "c-food")),
            categories = listOf(food)
        )

        assertEquals(0f, result.single().ratio, 0f)
    }

    @Test
    fun ratios_add_up_to_one() {
        val result = buildCategoryStats(
            transactions = listOf(tx("a", 100, "c-food"), tx("b", 300, "c-transport")),
            categories = listOf(food, traffic)
        )

        assertEquals(1f, result.sumOf { it.ratio.toDouble() }.toFloat(), 0.0001f)
        assertEquals(0.25f, result.first { it.categoryId == "c-food" }.ratio, 0.0001f)
    }
}
