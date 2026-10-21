package com.shiye.journal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.Loan
import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.LoanStatus
import com.shiye.journal.domain.DueTone
import com.shiye.journal.domain.dueStatus
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.HairlineDivider
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.PrimaryFab
import com.shiye.journal.ui.components.SegmentedControl
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeDropdownMenu
import com.shiye.journal.ui.components.ShiyeMenuItem
import com.shiye.journal.ui.components.StatusChip
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.LoanViewModel
import com.shiye.journal.util.DateTimeUtil
import com.shiye.journal.util.MoneyUtil

/**
 * 借入借出提醒（规格 §5.6）。
 *
 * 页头那两块数字（待还 / 待收）**不会**出现在记账首页或统计的收支里 ——
 * 借来的钱不是收入，还回去也不是支出，这是本模块最容易做错、后果也最严重的一条规则。
 *
 * 结清只改状态：不生成流水、不动账户余额。真要反映到账户上，用户自己去记一笔。
 */
@Composable
fun LoanScreen(
    viewModel: LoanViewModel,
    onAddLoan: () -> Unit,
    onEditLoan: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val loans by viewModel.loans.collectAsStateWithLifecycle()
    val pendingBorrow by viewModel.pendingBorrow.collectAsStateWithLifecycle()
    val pendingLend by viewModel.pendingLend.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(LoanFilter.PENDING) }
    var pendingDelete by remember { mutableStateOf<Loan?>(null) }

    LaunchedEffect(message) {
        message?.let {
            onShowMessage(it)
            viewModel.consumeMessage()
        }
    }

    val today = DateTimeUtil.todayString()
    val visible = remember(loans, filter) {
        when (filter) {
            LoanFilter.ALL -> loans.sortedByDescending { it.dateTime }
            LoanFilter.PENDING -> loans
                .filter { it.status == LoanStatus.PENDING }
                .sortedByDescending { it.dateTime }
            LoanFilter.SETTLED -> loans
                .filter { it.status == LoanStatus.SETTLED }
                .sortedByDescending { it.dateTime }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PageHead(eyebrow = "WHO OWES WHOM", title = "借入借出")

                    ShiyeCard {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            LoanSummaryCell(
                                label = "待还（我借入的）",
                                cents = pendingBorrow,
                                modifier = Modifier.weight(1f)
                            )
                            LoanSummaryCell(
                                label = "待收（我借出的）",
                                cents = pendingLend,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HairlineDivider(modifier = Modifier.padding(vertical = 14.dp))

                        Text(
                            text = "这两笔数不计入收入、支出，也不会进入账户余额。",
                            style = ShiyeType.caption,
                            color = ShiyeExtras.accentStrong
                        )
                    }

                    SegmentedControl(
                        options = LoanFilter.entries.map { it to it.label },
                        selected = filter,
                        onSelect = { filter = it }
                    )
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        title = when (filter) {
                            LoanFilter.PENDING -> "没有未结清的记录"
                            LoanFilter.SETTLED -> "还没有结清过的记录"
                            LoanFilter.ALL -> "还没有借入借出记录"
                        },
                        hint = "点右下角新增一笔借款提醒。"
                    )
                }
            } else {
                items(items = visible, key = { it.id }) { loan ->
                    LoanItem(
                        loan = loan,
                        today = today,
                        onSettle = { viewModel.settle(loan.id) },
                        onReopen = { viewModel.reopen(loan.id) },
                        onEdit = { onEditLoan(loan.id) },
                        onDelete = { pendingDelete = loan }
                    )
                }
            }
        }

        PrimaryFab(
            text = "＋ 新增借款",
            onClick = onAddLoan,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 24.dp)
        )
    }

    pendingDelete?.let { loan ->
        ConfirmDialog(
            title = "删除这条借款？",
            message = "${loan.person} ${MoneyUtil.format(loan.amount)}\n删除后不可恢复。",
            confirmText = "永久删除",
            onConfirm = {
                viewModel.delete(loan.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

private enum class LoanFilter(val label: String) {
    PENDING("未结清"),
    SETTLED("已结清"),
    ALL("全部")
}

@Composable
private fun LoanSummaryCell(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = ShiyeType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = MoneyUtil.format(cents),
            style = ShiyeType.amount,
            color = ShiyeExtras.loan
        )
    }
}

@Composable
private fun LoanItem(
    loan: Loan,
    today: String,
    onSettle: () -> Unit,
    onReopen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val due = dueStatus(loan, today)
    val dueColor: Color = when (due.tone) {
        DueTone.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
        DueTone.NORMAL -> ShiyeExtras.accentStrong
        DueTone.ALERT -> MaterialTheme.colorScheme.error
    }

    ShiyeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (loan.direction == LoanDirection.BORROW) {
                            "借入 · ${loan.person}"
                        } else {
                            "借出 · ${loan.person}"
                        },
                        style = ShiyeType.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusChip(text = due.text, contentColor = dueColor, backgroundColor = ShiyeExtras.surface2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = DateTimeUtil.formatDateShortWithWeekday(loan.date),
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    loan.dueDate?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "约定 $it",
                            style = ShiyeType.caption,
                            color = dueColor
                        )
                    }
                    if (loan.note.isNotBlank()) {
                        Text(
                            text = loan.note,
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MoneyUtil.format(loan.amount),
                    style = ShiyeType.amount,
                    color = ShiyeExtras.loan
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(text = "⋯", style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.primary)
                        }
                        ShiyeDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (loan.status == LoanStatus.PENDING) {
                                ShiyeMenuItem(text = "标记为已结清", onClick = { expanded = false; onSettle() })
                            } else {
                                ShiyeMenuItem(text = "改回未结清", onClick = { expanded = false; onReopen() })
                            }
                            ShiyeMenuItem(text = "编辑", onClick = { expanded = false; onEdit() })
                            ShiyeMenuItem(text = "删除", onClick = { expanded = false; onDelete() }, isDanger = true)
                        }
                    }
                }
            }
        }
    }
}
