package com.shiye.journal.domain

import com.shiye.journal.data.model.Loan
import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.LoanStatus
import com.shiye.journal.util.DateTimeUtil
import java.time.temporal.ChronoUnit

/**
 * 借入借出的到期提示（规格 §5.6）。
 *
 * 规格原话："到期记录应突出显示到期日期和未结清状态"。
 * 这里把"突出"落实成一个明确的等级 [DueTone]，由 UI 决定用哪种颜色，
 * 业务规则本身不碰颜色。
 */
enum class DueTone {
    /** 已结清，不需要提醒 */
    SETTLED,

    /** 正常：还没到期或没约定日期 */
    NORMAL,

    /** 需要注意：今天到期或已逾期 */
    ALERT
}

data class DueStatus(
    val text: String,
    val tone: DueTone
)

/**
 * 一条借款的到期提示。
 *
 * @param today 今天，yyyy-MM-dd。作为参数传入而不是直接取当前时间，
 *   是为了让"跨天"这类场景在单元测试里可验证。
 */
fun dueStatus(loan: Loan, today: String): DueStatus {
    if (loan.status == LoanStatus.SETTLED) {
        return DueStatus("已结清", DueTone.SETTLED)
    }
    val due = loan.dueDate?.takeIf { it.isNotBlank() } ?: return DueStatus("未约定还款日", DueTone.NORMAL)

    val dueDate = DateTimeUtil.parseDate(due) ?: return DueStatus("到期 $due", DueTone.NORMAL)
    val todayDate = DateTimeUtil.parseDate(today) ?: return DueStatus("到期 $due", DueTone.NORMAL)

    val days = ChronoUnit.DAYS.between(todayDate, dueDate)
    return when {
        days < 0 -> DueStatus("逾期 ${-days} 天", DueTone.ALERT)
        days == 0L -> DueStatus("今天到期", DueTone.ALERT)
        days <= 3 -> DueStatus("$days 天后到期", DueTone.ALERT)
        else -> DueStatus("$days 天后到期", DueTone.NORMAL)
    }
}

/**
 * 某个方向的**未结清**金额合计，单位分。
 *
 * 借入与借出**分开算**：前者是我欠别人的，后者是别人欠我的。
 * 两者相抵消会得出一个"净额"，但这两个方向本来就不是同一回事。
 */
fun pendingAmount(loans: List<Loan>, direction: LoanDirection): Long =
    loans.filter { it.direction == direction && it.status == LoanStatus.PENDING }
        .sumOf { it.amount }

/**
 * 未结清的记录：有到期日的按到期日升序列前（逾期自然排在前面），
 * 没约定到期日的沉到最后，再按发生时间由新到旧。
 */
fun pendingLoansSorted(loans: List<Loan>): List<Loan> =
    loans.filter { it.status == LoanStatus.PENDING }
        .sortedWith(
            compareBy<Loan> { dueKey(it) }
                .thenByDescending { it.dateTime }
        )

/**
 * 排序键：没有到期日的用远未来日期兜底，保证它们一定排在最后。
 * 用字符串键是因为 [Loan.dueDate] 可空且需要参与多次比较，压平后最简单。
 */
private fun dueKey(loan: Loan): String = loan.dueDate?.takeIf { it.isNotBlank() } ?: "9999-99-99"
