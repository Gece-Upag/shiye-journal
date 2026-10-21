package com.shiye.journal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.domain.CategoryStat
import com.shiye.journal.domain.DateInput
import com.shiye.journal.domain.PeriodKind
import com.shiye.journal.ui.components.DateJumpField
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.HairlineDivider
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.RatioBar
import com.shiye.journal.ui.components.SectionHead
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.LedgerStatsViewModel
import com.shiye.journal.util.MoneyUtil

/**
 * 统计与结算（规格 §5.7）。
 *
 * 支持日 / 周 / 月 / 年，周按周一至周日。切换周期有两条路：
 * 左右箭头挪一格，或者**点一下日期直接手输**（如 2026-10 / 2026）。
 * 两条路都拦未来（未来的账一定是空的，翻过去只会让人以为是数据没加载）。
 *
 * 分两块，**严格分开**：
 * - 上面：普通收入 / 支出 / 结余 + 分类占比条形（分母是本周期自己的合计）；
 * - 下面：借入借出概况，单独一块，用借款金色标识，绝不混进上面的数。
 */
@Composable
fun LedgerStatsScreen(
    viewModel: LedgerStatsViewModel,
    modifier: Modifier = Modifier
) {
    val periodTitle by viewModel.periodTitle.collectAsStateWithLifecycle()
    val canForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val expenseStats by viewModel.expenseStats.collectAsStateWithLifecycle()
    val incomeStats by viewModel.incomeStats.collectAsStateWithLifecycle()
    val overview by viewModel.loanOverview.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    // 顶部日期框是否处于"正在手输"状态。提到这里是因为手输时要收起左右箭头腾出宽度。
    var editing by remember { mutableStateOf(false) }
    val errorText by viewModel.dateInputError.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PageHead(eyebrow = "WHERE IT WENT", title = "统计")

                ShiyeCard {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 手输时收起箭头，把宽度让给输入框
                            if (!editing) {
                                TextButton(onClick = { viewModel.move(-1) }) {
                                    Text("‹", style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DateJumpField(
                                kind = period.kind,
                                title = periodTitle,
                                errorMessage = errorText,
                                onSubmit = { viewModel.jumpTo(it) },
                                onErrorShown = { viewModel.consumeDateInputError() },
                                onEditingChange = { editing = it },
                                modifier = Modifier.weight(1f)
                            )
                            if (!editing) {
                                if (canForward) {
                                    TextButton(onClick = { viewModel.move(1) }) {
                                        Text("›", style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    // 不能往后翻时留一个同宽占位，避免标题左右跳动
                                    Text(
                                        text = "›",
                                        style = ShiyeType.cardTitle,
                                        color = Color.Transparent,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            // 手输跳远之后，一步回到今天比一路点箭头回来快
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { viewModel.goToday() }) {
                    Text(
                        text = "回到今天",
                        style = ShiyeType.label,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            // 粒度切换：日 / 周 / 月 / 年，切成新粒度后回到"今天所在的那个周期"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodKind.entries.forEach { kind ->
                    val selected = kind == period.kind
                    TextButton(
                        onClick = { viewModel.setKind(kind) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = kind.label,
                                style = ShiyeType.cardTitle,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            ShiyeCard {
                Text(
                    text = "结余",
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = MoneyUtil.format(summary.net),
                    style = ShiyeType.amountLarge,
                    color = if (summary.net < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                HairlineDivider(modifier = Modifier.padding(vertical = 14.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryCell(
                        label = "收入",
                        cents = summary.income,
                        color = ShiyeExtras.income,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCell(
                        label = "支出",
                        cents = summary.expense,
                        color = ShiyeExtras.expense,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (summary.income == 0L && summary.expense == 0L) {
            item {
                EmptyState(
                    title = "这个${period.kind.label}没有收支",
                    hint = "换个周期看看，或者先记一笔。"
                )
            }
        }

        item {
            SectionHead(title = "支出分类", modifier = Modifier.padding(top = 4.dp))
        }
        items(items = expenseStats, key = { "expense-${it.categoryId}" }) { stat ->
            CategoryStatItem(stat = stat, color = ShiyeExtras.expense, total = summary.expense)
        }

        item {
            SectionHead(title = "收入分类", modifier = Modifier.padding(top = 8.dp))
        }
        items(items = incomeStats, key = { "income-${it.categoryId}" }) { stat ->
            CategoryStatItem(stat = stat, color = ShiyeExtras.income, total = summary.income)
        }

        item {
            SectionHead(title = "借入借出概况", modifier = Modifier.padding(top = 8.dp))
        }

        item {
            ShiyeCard {
                Text(
                    text = "借款不计入上面的收入与支出，这里单独看。",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    SummaryCell(
                        label = "本${period.kind.label}借入",
                        cents = overview.periodBorrow,
                        color = ShiyeExtras.loan,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCell(
                        label = "本${period.kind.label}借出",
                        cents = overview.periodLend,
                        color = ShiyeExtras.loan,
                        modifier = Modifier.weight(1f)
                    )
                }
                HairlineDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryCell(
                        label = "待还（借入未结清）",
                        cents = overview.pendingBorrow,
                        color = ShiyeExtras.loan,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCell(
                        label = "待收（借出未结清）",
                        cents = overview.pendingLend,
                        color = ShiyeExtras.loan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(
    label: String,
    cents: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = ShiyeType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = MoneyUtil.format(cents), style = ShiyeType.amount, color = color)
    }
}

/**
 * 一条分类统计：名字 + 金额 + 占比条 + 百分数。
 *
 * @param total 这一组（收入或支出）的合计；为 0 时占比显示 0%，不做除法。
 */
@Composable
private fun CategoryStatItem(
    stat: CategoryStat,
    color: Color,
    total: Long
) {
    val percent = if (total == 0L) 0 else (stat.ratio * 100).toInt()
    ShiyeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stat.icon} ${stat.name}",
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$percent%",
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = MoneyUtil.format(stat.cents),
                style = ShiyeType.amount,
                color = color
            )
        }
        RatioBar(ratio = stat.ratio, color = color, modifier = Modifier.padding(top = 6.dp))
    }
}
