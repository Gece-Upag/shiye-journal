package com.shiye.journal

import com.shiye.journal.data.model.Account
import com.shiye.journal.data.model.Category
import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 流水上的**名称快照**（规格 §5.3 / §5.5）该怎么显示。
 *
 * 这里测的是 [Transaction.categoryLabel] / [Transaction.accountLabel] 两条规则：
 * 快照优先，快照缺失才退回到实时查表，两者都没有时给一个明确说法。
 *
 * 之所以要为两行取值写测试：这是"分类改名了，上个月那笔账显示什么"这类问题，
 * 错了不会崩溃、只会悄悄显示错的名字，属于最难被发现的那一类 bug。
 */
class TransactionSnapshotTest {

    private val categories = mapOf(
        "c-food" to Category(
            id = "c-food", name = "餐饮", type = TransactionType.EXPENSE,
            icon = "\u2615", sortOrder = 0, active = true, isPreset = true, createdAt = 0L
        )
    )
    private val accounts = mapOf(
        "a-cash" to Account(
            id = "a-cash", name = "现金", openingBalance = 0L,
            sortOrder = 0, active = true, createdAt = 0L, updatedAt = 0L
        )
    )

    private fun transaction(
        categoryId: String = "c-food",
        categoryName: String = "",
        accountId: String = "a-cash",
        accountName: String = ""
    ) = Transaction(
        id = "t-1",
        type = TransactionType.EXPENSE,
        amount = 1_000L,
        categoryId = categoryId,
        categoryName = categoryName,
        accountId = accountId,
        accountName = accountName,
        note = "",
        date = "2026-10-15",
        time = "12:00",
        dateTime = 0L,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `有快照时以快照为准，分类改名不影响历史流水`() {
        val old = transaction(categoryId = "c-food", categoryName = "饭钱")
        assertEquals("饭钱", old.categoryLabel(categories.plus("c-food" to categories["c-food"]!!.copy(name = "餐饮"))))
    }

    @Test
    fun `没有快照时退回当前分类名`() {
        assertEquals("餐饮", transaction().categoryLabel(categories))
    }

    @Test
    fun `快照为空且分类已删除时显示未分类`() {
        assertEquals("未分类", transaction(categoryId = "c-gone").categoryLabel(categories))
    }

    @Test
    fun `账户快照优先`() {
        assertEquals("零钱罐", transaction(accountName = "零钱罐").accountLabel(accounts))
    }

    @Test
    fun `没有指定账户时显示未指定账户`() {
        assertEquals("未指定账户", transaction(accountId = "", accountName = "").accountLabel(accounts))
    }

    @Test
    fun `账户被删后仍有快照时照样能读出名字`() {
        assertEquals("工资卡", transaction(accountId = "a-gone", accountName = "工资卡").accountLabel(emptyMap()))
    }

    @Test
    fun `快照为空且账户已删除时退化到未指定账户`() {
        assertEquals("未指定账户", transaction(accountId = "a-gone").accountLabel(accounts))
    }

    /** 顺手确认借款方向常量没有写反：借入是我欠别人 */
    @Test
    fun `借款方向与枚举取值一致`() {
        assertEquals(2, LoanDirection.entries.size)
        assertEquals(LoanDirection.BORROW, LoanDirection.entries.first { it.name == "BORROW" })
    }
}
