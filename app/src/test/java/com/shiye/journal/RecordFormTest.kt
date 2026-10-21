package com.shiye.journal

import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.domain.RecordDraft
import com.shiye.journal.domain.RecordKind
import com.shiye.journal.domain.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「记一笔」的表单校验（规格 §5.2）。
 *
 * 金额必须大于零、借款要填对方、普通收支必须选分类 —— 这三条是规格明写的，
 * 剩下的日期校验是为了别把脏数据写进库里（后面统计要按日期字符串做区间比较）。
 */
class RecordFormTest {

    private val today = "2026-10-15"

    private fun draft(
        kind: RecordKind = RecordKind.EXPENSE,
        amountCents: Long = 1234,
        categoryId: String = "c-food",
        accountId: String = "a-1",
        person: String = "",
        date: String = today,
        time: String = "12:30",
        dueDate: String = ""
    ) = RecordDraft(
        kind = kind,
        amountCents = amountCents,
        categoryId = categoryId,
        accountId = accountId,
        person = person,
        note = "",
        date = date,
        time = time,
        dueDate = dueDate
    )

    @Test
    fun valid_expense_passes() {
        assertTrue(draft().validate(today).isEmpty())
    }

    @Test
    fun amount_must_be_positive() {
        val zero = draft(amountCents = 0).validate(today)
        val negative = draft(amountCents = -500).validate(today)

        assertTrue(zero.contains("金额要大于 0"))
        assertTrue(negative.contains("金额要大于 0"))
    }

    @Test
    fun expense_requires_category() {
        val errors = draft(categoryId = "").validate(today)

        assertTrue(errors.contains("请选择一个分类"))
    }

    @Test
    fun expense_does_not_require_person() {
        assertTrue(draft(person = "").validate(today).isEmpty())
    }

    @Test
    fun loan_does_not_require_category_but_requires_person() {
        val borrow = draft(kind = RecordKind.BORROW, categoryId = "")

        val given = borrow.copy(person = "张三").validate(today)
        assertTrue(given.isEmpty())

        assertTrue(borrow.validate(today).contains("请填写对方"))
    }

    @Test
    fun loan_due_date_cannot_be_before_occurrence() {
        val errors = draft(
            kind = RecordKind.LEND,
            person = "李四",
            date = "2026-10-15",
            dueDate = "2026-10-10"
        ).validate(today)

        assertTrue(errors.contains("到期日不能早于发生日期"))
    }

    @Test
    fun loan_due_date_equal_to_occurrence_is_allowed() {
        val errors = draft(
            kind = RecordKind.LEND,
            person = "李四",
            date = today,
            dueDate = today
        ).validate(today)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun malformed_date_and_time_are_rejected() {
        assertTrue(draft(date = "2026/10/15").validate(today).contains("日期格式不对"))
        assertTrue(draft(time = "25:70").validate(today).contains("时间格式应为 HH:mm"))
        assertTrue(draft(time = "中午").validate(today).contains("时间格式应为 HH:mm"))
    }

    @Test
    fun future_date_is_allowed_but_too_far_is_not() {
        assertTrue(draft(date = "2026-12-01").validate(today).isEmpty())
        assertTrue(draft(date = "2028-01-01").validate(today).contains("日期太远了，确认一下年份"))
    }

    @Test
    fun several_errors_are_reported_together() {
        val errors = draft(amountCents = 0, categoryId = "", date = "bad").validate(today)

        assertEquals(3, errors.size)
    }

    @Test
    fun kind_maps_to_transaction_type_or_loan_direction() {
        assertEquals(TransactionType.EXPENSE, RecordKind.EXPENSE.transactionType())
        assertEquals(TransactionType.INCOME, RecordKind.INCOME.transactionType())
        assertNull(RecordKind.BORROW.transactionType())

        assertEquals(LoanDirection.BORROW, RecordKind.BORROW.loanDirection())
        assertEquals(LoanDirection.LEND, RecordKind.LEND.loanDirection())
        assertNull(RecordKind.EXPENSE.loanDirection())

        assertTrue(RecordKind.BORROW.isLoan())
        assertTrue(RecordKind.LEND.isLoan())
        assertTrue(!RecordKind.INCOME.isLoan())
    }

    @Test
    fun storage_keys_round_trip() {
        RecordKind.entries.forEach { kind ->
            assertEquals(kind, RecordKind.fromStorageKey(kind.storageKey))
        }
        // 认不出来的值退回支出，而不是抛异常让页面白屏
        assertEquals(RecordKind.EXPENSE, RecordKind.fromStorageKey("???"))
        assertEquals(RecordKind.EXPENSE, RecordKind.fromStorageKey(null))
    }
}
