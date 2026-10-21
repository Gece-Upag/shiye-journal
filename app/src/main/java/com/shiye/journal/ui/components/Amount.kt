package com.shiye.journal.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.util.MoneyUtil

/**
 * 金额文本。
 *
 * 颜色规则取自原型：收入用强调色（绿），支出用危险色（红），借款用中间金。
 * 注意这是记账惯例，与股票涨跌的红绿相反，不要按后者改。
 */
@Composable
fun AmountText(
    cents: Long,
    type: TransactionType,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Text(
        text = MoneyUtil.format(cents),
        style = if (large) ShiyeType.amountLarge else ShiyeType.amount,
        color = when (type) {
            TransactionType.INCOME -> ShiyeExtras.income
            TransactionType.EXPENSE -> ShiyeExtras.expense
        },
        modifier = modifier,
        textAlign = TextAlign.End
    )
}

/** 不带方向色的金额，用于结余这类本身就是结果值的场景 */
@Composable
fun PlainAmountText(
    cents: Long,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = MoneyUtil.format(cents),
        style = if (large) ShiyeType.amountLarge else ShiyeType.amount,
        color = color,
        modifier = modifier,
        textAlign = TextAlign.End
    )
}

/** 借款金额，用中间金，既不表示收入也不表示支出 */
@Composable
fun LoanAmountText(
    cents: Long,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Text(
        text = MoneyUtil.format(cents),
        style = if (large) ShiyeType.amountLarge else ShiyeType.amount,
        color = ShiyeExtras.loan,
        modifier = modifier,
        textAlign = TextAlign.End
    )
}

/**
 * 自动保存状态。
 *
 * 规格要求编辑页明确显示保存状态，而不是让用户猜"到底存没存"。
 * 三个状态覆盖全部情况：没动过 / 正在存 / 已保存。
 */
enum class SaveStatus {
    /** 打开后还没编辑 */
    IDLE,

    /** 有改动，等待防抖时间到后写入 */
    PENDING,

    /** 正在写入 */
    SAVING,

    /** 已写入数据库 */
    SAVED,

    /** 写入失败，可重试 */
    FAILED
}

@Composable
fun SaveStatusText(status: SaveStatus) {
    val (text, color) = when (status) {
        SaveStatus.IDLE -> "" to MaterialTheme.colorScheme.onSurfaceVariant
        SaveStatus.PENDING -> "有改动" to MaterialTheme.colorScheme.onSurfaceVariant
        SaveStatus.SAVING -> "保存中…" to MaterialTheme.colorScheme.onSurfaceVariant
        SaveStatus.SAVED -> "已保存" to ShiyeExtras.income
        SaveStatus.FAILED -> "保存失败，请重试" to MaterialTheme.colorScheme.error
    }
    if (text.isNotEmpty()) {
        Text(text = text, style = ShiyeType.caption, color = color)
    }
}
