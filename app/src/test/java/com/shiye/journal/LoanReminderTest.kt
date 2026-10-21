package com.shiye.journal

import com.shiye.journal.data.model.Loan
import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.LoanStatus
import com.shiye.journal.domain.DueTone
import com.shiye.journal.domain.dueStatus
import com.shiye.journal.domain.pendingAmount
import com.shiye.journal.domain.pendingLoansSorted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 借入借出提醒（规格 §5.6）。
 *
 * 规格要求"到期记录应突出显示到期日期和未结清状态"，所以 [dueStatus] 的音量档位
 * （普通 / 需要注意 / 已结清）也要进断言，不能只测文案。
 */
class LoanReminderTest {

    private val today = "2026-10-15"

    private fun loan(
        id: String,
        direction: LoanDirection,
        amount: Long,
        dueDate: String? = null,
        status: LoanStatus = LoanStatus.PENDING
    ): Loan = Loan(
        id = id,
        direction = direction,
        person = "某人",
        amount = amount,
        note = "",
        date = "2026-10-01",
        dateTime = 0L,
        dueDate = dueDate,
        status = status,
        settledAt = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun settled_loan_is_not_reminded() {
        val status = dueStatus(
            loan("a", LoanDirection.LEND, 1000, dueDate = "2026-10-10", status = LoanStatus.SETTLED),
            today
        )

        assertEquals("已结清", status.text)
        assertEquals(DueTone.SETTLED, status.tone)
    }

    @Test
    fun no_due_date_is_normal() {
        val status = dueStatus(loan("a", LoanDirection.BORROW, 1000), today)

        assertEquals("未约定还款日", status.text)
        assertEquals(DueTone.NORMAL, status.tone)
    }

    @Test
    fun overdue_counts_days() {
        val status = dueStatus(loan("a", LoanDirection.BORROW, 1000, dueDate = "2026-10-12"), today)

        assertEquals("逾期 3 天", status.text)
        assertEquals(DueTone.ALERT, status.tone)
    }

    @Test
    fun due_today_is_alert() {
        val status = dueStatus(loan("a", LoanDirection.LEND, 1000, dueDate = today), today)

        assertEquals("今天到期", status.text)
        assertEquals(DueTone.ALERT, status.tone)
    }

    @Test
    fun due_soon_is_alert_but_far_is_not() {
        val soon = dueStatus(loan("a", LoanDirection.LEND, 1000, dueDate = "2026-10-17"), today)
        assertEquals("2 天后到期", soon.text)
        assertEquals(DueTone.ALERT, soon.tone)

        val later = dueStatus(loan("b", LoanDirection.LEND, 1000, dueDate = "2026-11-20"), today)
        assertEquals("36 天后到期", later.text)
        assertEquals(DueTone.NORMAL, later.tone)
    }

    @Test
    fun borrow_and_lend_are_summed_separately() {
        val loans = listOf(
            loan("a", LoanDirection.BORROW, 10000),
            loan("b", LoanDirection.BORROW, 5000),
            loan("c", LoanDirection.LEND, 2000),
            loan("d", LoanDirection.BORROW, 3000, status = LoanStatus.SETTLED)
        )

        // 已结清的那笔不计入
        assertEquals(15000L, pendingAmount(loans, LoanDirection.BORROW))
        assertEquals(2000L, pendingAmount(loans, LoanDirection.LEND))
    }

    @Test
    fun pending_sorted_puts_due_date_first() {
        val loans = listOf(
            loan("no-due", LoanDirection.LEND, 100),
            loan("late", LoanDirection.LEND, 100, dueDate = "2026-10-10"),
            loan("later", LoanDirection.LEND, 100, dueDate = "2026-10-25")
        )

        assertEquals(listOf("late", "later", "no-due"), pendingLoansSorted(loans).map { it.id })
    }

    @Test
    fun pending_sorted_excludes_settled() {
        val loans = listOf(
            loan("settled", LoanDirection.BORROW, 100, dueDate = "2026-01-01", status = LoanStatus.SETTLED),
            loan("open", LoanDirection.BORROW, 100)
        )

        assertEquals(listOf("open"), pendingLoansSorted(loans).map { it.id })
    }

    @Test
    fun no_loans_means_zero_pending() {
        assertTrue(pendingLoansSorted(emptyList()).isEmpty())
        assertEquals(0L, pendingAmount(emptyList(), LoanDirection.BORROW))
    }
}
