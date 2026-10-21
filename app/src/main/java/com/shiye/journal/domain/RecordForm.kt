package com.shiye.journal.domain

import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.util.DateTimeUtil

/**
 * 「记一笔」的四种记录类型（规格 §5.2）。
 *
 * 分组的意义：**借款提醒不是收支**。收入/支出要选分类与账户，借款要填对方与到期日，
 * 两者混在一张平铺的表里，用户很容易把"借来的钱"记成收入。
 */
enum class RecordKind(val label: String, val storageKey: String) {
    EXPENSE("支出", "expense"),
    INCOME("收入", "income"),
    BORROW("借入", "borrow"),
    LEND("借出", "lend");

    companion object {
        /** 从路由参数里恢复；认不出来的一律退回支出（最常用、也最不容易记错方向） */
        fun fromStorageKey(key: String?): RecordKind =
            entries.firstOrNull { it.storageKey == key } ?: EXPENSE
    }

    /** 借款提醒：不计收支、不动账户余额 */
    fun isLoan(): Boolean = this == BORROW || this == LEND

    fun transactionType(): TransactionType? = when (this) {
        EXPENSE -> TransactionType.EXPENSE
        INCOME -> TransactionType.INCOME
        BORROW, LEND -> null
    }

    fun loanDirection(): LoanDirection? = when (this) {
        BORROW -> LoanDirection.BORROW
        LEND -> LoanDirection.LEND
        EXPENSE, INCOME -> null
    }
}

/**
 * 记一笔表单的原始内容。
 *
 * 金额在这里已经是**分**（由 UI 侧用 [com.shiye.journal.util.MoneyUtil.parseToCents] 换算），
 * 领域层不认识"元"这个单位，避免同一笔钱在两层之间来回换算。
 *
 * @param accountId 空串表示**未指定账户**（规格 §5.5 允许流水的账户字段为空）
 */
data class RecordDraft(
    val kind: RecordKind,
    val amountCents: Long,
    val categoryId: String,
    val accountId: String,
    val person: String,
    val note: String,
    val date: String,
    val time: String,
    val dueDate: String
)

/**
 * 校验规则。返回**全部**错误而不是第一个 —— 用户改完一个再报另一个会来回折腾。
 * 返回空列表表示可以保存。
 *
 * 规则来源：规格 §5.2「金额必须大于零」、「显示借款提醒所需字段，而不是普通收支分类」。
 */
fun RecordDraft.validate(today: String = DateTimeUtil.todayString()): List<String> {
    val errors = mutableListOf<String>()

    if (amountCents <= 0) errors.add("金额要大于 0")

    if (DateTimeUtil.parseDate(date) == null) errors.add("日期格式不对")

    // 用真正的 LocalTime 解析而不是正则：正则会让 "25:70" 这种时间也通过
    if (runCatching { java.time.LocalTime.parse(time, TIME_FORMATTER) }.isFailure) {
        errors.add("时间格式应为 HH:mm")
    }

    if (kind.isLoan()) {
        if (person.isBlank()) errors.add("请填写对方")
        val due = dueDate.trim()
        if (due.isNotEmpty()) {
            val dueParsed = DateTimeUtil.parseDate(due)
            val dateParsed = DateTimeUtil.parseDate(date)
            if (dueParsed == null) {
                errors.add("到期日格式不对")
            } else if (dateParsed != null && dueParsed.isBefore(dateParsed)) {
                errors.add("到期日不能早于发生日期")
            }
        }
    } else {
        if (categoryId.isBlank()) errors.add("请选择一个分类")
    }

    // 未来日期不拦：补记和预记账都是合理场景（先记下周要交的房租），
    // 但**提前一年以上**几乎一定是按错了数字，这里做一个温和的提醒式拦截。
    val parsed = DateTimeUtil.parseDate(date)
    val todayParsed = DateTimeUtil.parseDate(today)
    if (parsed != null && todayParsed != null && parsed.isAfter(todayParsed.plusMonths(12))) {
        errors.add("日期太远了，确认一下年份")
    }

    return errors
}

private val TIME_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.US)
