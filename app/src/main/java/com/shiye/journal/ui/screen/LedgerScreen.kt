package com.shiye.journal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.ui.components.AmountText
import com.shiye.journal.ui.components.BackfillDateDialog
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.HairlineDivider
import com.shiye.journal.ui.components.InnerRadius
import com.shiye.journal.ui.components.LedgerEntryButton
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.PlainAmountText
import com.shiye.journal.ui.components.SectionHead
import com.shiye.journal.ui.components.SelectMark
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.StatusChip
import com.shiye.journal.ui.components.TodayEntryMode
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.LedgerViewModel
import com.shiye.journal.util.DateTimeUtil
import com.shiye.journal.util.MoneyUtil

/**
 * 记账首页（规格 §5.1）。
 *
 * 显示：本月收入 / 支出 / 结余、今日流水、最近 [LedgerViewModel.RECENT_WINDOW_DAYS] 天流水
 * + 右下角「记一笔」。顶部菜单（在应用壳 TopBarActions 里）进入统计、分类管理、账户管理、借入借出。
 *
 * 右下角入口有两种模式（与日记页的「补写回忆」同一套做法）：
 * - 「＋ 记一笔」直接开今天的表单，最常用的路径不加任何一步；
 * - 「补账单」先问哪一天，再把那个日期带进表单。补的账会落到所选日期，
 *   本月结余与统计随之计算 —— 跨月补账时它计入**账单所在**的那个月，不是当前月。
 *
 * 删一条：长按弹出确认。删一批：去**搜索页**（顶栏 ⌕），那里的批量删除
 * 可以按条件先筛出来再勾 —— 首页只放"看"，破坏性入口不挂在顶栏。
 *
 * 一条贯穿全程的规矩：**借入借出不计入这里的任何一组数字**。
 * 借来的钱不是收入、还回去也不是支出，混进来结余就会算重。
 */
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    onAddRecord: () -> Unit,
    onBackfillRecord: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val todayItems by viewModel.todayTransactions.collectAsStateWithLifecycle()
    val recentItems by viewModel.recentTransactions.collectAsStateWithLifecycle()
    val categoriesById by viewModel.categoriesById.collectAsStateWithLifecycle()
    val accountsById by viewModel.accountsById.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    // 记一笔入口的模式（记今天 / 补账单）与补账单时的日期弹窗。
    // 用 rememberSaveable 而不是 remember：旋转屏幕后不该悄悄退回"记今天"
    var entryMode by rememberSaveable { mutableStateOf(TodayEntryMode.TODAY) }
    var backfillOpen by remember { mutableStateOf(false) }
    val today = remember { DateTimeUtil.todayString() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                // 底部留出右下角入口那一排的高度，否则最后一条流水会被压在按钮下面
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PageHead(eyebrow = "MONEY, SIMPLY", title = "记账")

                        MonthSummaryCard(
                            monthLabel = viewModel.monthLabel,
                            net = summary.net,
                            income = summary.income,
                            expense = summary.expense
                        )
                    }
                }

                item {
                    SectionHead(
                        title = "今日流水",
                        count = todayItems.size,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
                    )
                }

                if (todayItems.isEmpty()) {
                    item {
                        EmptyState(
                            title = "今天还没有记账",
                            hint = "点右下角「记一笔」开始。"
                        )
                    }
                } else {
                    items(items = todayItems, key = { "today-${it.id}" }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            categoryName = transaction.categoryLabel(categoriesById),
                            categoryIcon = categoriesById[transaction.categoryId]?.icon,
                            accountName = transaction.accountLabel(accountsById),
                            modifier = Modifier.padding(horizontal = 22.dp),
                            onClick = { onOpenRecord(transaction.id) },
                            onLongClick = { pendingDelete = transaction }
                        )
                    }
                }

                item {
                    SectionHead(
                        title = "最近 ${LedgerViewModel.RECENT_WINDOW_DAYS} 天",
                        count = recentItems.size,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
                    )
                }

                if (recentItems.isEmpty()) {
                    item {
                        Text(
                            text = "最近 ${LedgerViewModel.RECENT_WINDOW_DAYS} 天没有记账",
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    items(items = recentItems, key = { "recent-${it.id}" }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            categoryName = transaction.categoryLabel(categoriesById),
                            categoryIcon = categoriesById[transaction.categoryId]?.icon,
                            accountName = transaction.accountLabel(accountsById),
                            modifier = Modifier.padding(horizontal = 22.dp),
                            onClick = { onOpenRecord(transaction.id) },
                            onLongClick = { pendingDelete = transaction }
                        )
                    }
                }
            }
        }

        LedgerEntryButton(
            mode = entryMode,
            onWrite = {
                // 「补账单」不直接进表单：先问清是哪一天，选好再把日期带进去
                if (entryMode == TodayEntryMode.BACKFILL) {
                    backfillOpen = true
                } else {
                    onAddRecord()
                }
            },
            onToggleMode = {
                entryMode = if (entryMode == TodayEntryMode.TODAY) {
                    TodayEntryMode.BACKFILL
                } else {
                    TodayEntryMode.TODAY
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 24.dp)
        )
    }

    if (backfillOpen) {
        BackfillDateDialog(
            initialDate = today,
            title = "补哪一天的账单？",
            confirmText = "开始记",
            futureError = "补账单只能选今天或更早的日子",
            onDismiss = { backfillOpen = false },
            onConfirm = { date ->
                backfillOpen = false
                onBackfillRecord(date)
            }
        )
    }

    pendingDelete?.let { transaction ->
        ConfirmDialog(
            title = "删除这笔流水？",
            message = "${
                transaction.categoryLabel(categoriesById)
            } ${MoneyUtil.format(transaction.amount)}\n删除后不可恢复，本月结余和统计会一起变。",
            confirmText = "永久删除",
            onConfirm = {
                viewModel.deleteTransaction(transaction.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun MonthSummaryCard(
    monthLabel: String,
    net: Long,
    income: Long,
    expense: Long
) {
    ShiyeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "本月结余",
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PlainAmountText(
                    cents = net,
                    large = true,
                    color = when {
                        net < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Text(
                text = monthLabel,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HairlineDivider(modifier = Modifier.padding(vertical = 14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryItem(
                label = "收入",
                cents = income,
                color = ShiyeExtras.income,
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                label = "支出",
                cents = expense,
                color = ShiyeExtras.expense,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    cents: Long,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = ShiyeType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = MoneyUtil.format(cents), style = ShiyeType.amount, color = color)
    }
}

/**
 * 一条流水。
 *
 * 点击进入编辑，长按弹出删除确认。备注为空时显示分类名而不是"支出"这两个字 ——
 * 列表里一排"支出"对用户没有任何信息量。
 *
 * 行尾一律是**日期 + 时间**（如 "10-21 14:30"），今日流水那一段也一样：
 * 最近几天里同一笔会同时在两段出现，一边显示时间、一边显示日期时间会被当成数据不一致。
 *
 * [fullDate] 给搜索结果用：那里的结果可能横跨好几年，行尾显示 "2026-10-21 14:30"。
 * 默认关着 —— 首页只看最近几天，加年份是纯噪声。
 */
@Composable
fun TransactionItem(
    transaction: Transaction,
    categoryName: String?,
    accountName: String?,
    modifier: Modifier = Modifier,
    categoryIcon: String? = null,
    fullDate: Boolean = false,
    selectable: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 多选态下左边多一个勾选圈，点整行都是切换勾选
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectable) {
                SelectMark(
                    selected = selected,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = buildString {
                        if (!categoryIcon.isNullOrBlank()) append("$categoryIcon ")
                        append(transaction.note.ifBlank { categoryName ?: "未分类" })
                    },
                    style = ShiyeType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (fullDate) {
                            DateTimeUtil.formatFullDateTime(transaction.date, transaction.time)
                        } else {
                            DateTimeUtil.formatShortDateTime(transaction.date, transaction.time)
                        },
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (transaction.note.isNotBlank() && categoryName != null) {
                        StatusChip(text = categoryName)
                    }
                    if (!accountName.isNullOrBlank()) {
                        Text(
                            text = accountName,
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            AmountText(cents = transaction.amount, type = transaction.type)
        }
    }

    ShiyeCard(modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
        content()
    }
}

