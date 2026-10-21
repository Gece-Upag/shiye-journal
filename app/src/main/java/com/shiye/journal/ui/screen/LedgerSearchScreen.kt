package com.shiye.journal.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.ui.components.BulkDeleteBar
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.DatePickerField
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.SegmentedControl
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeTextField
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.LedgerSearchViewModel
import com.shiye.journal.util.MoneyUtil

/**
 * 记账搜索页（规格 §5.1 的搜索入口）+ 批量删除。
 *
 * 条件：关键词 + 开始/结束日期 + 排序，任一变化自动重查（ViewModel 里防抖 250ms）。
 * 关键词命中**备注、分类名、账户名**三处（DAO 里定的），
 * 所以「餐饮」改名成「吃喝」之后，旧账单按当时的名字照样搜得到。
 *
 * 一个条件都不填时列出**全部**流水 —— 这正好覆盖了"想批量删一批"的场景：
 * 打开搜索、勾几条、删掉，不需要先想关键词。
 *
 * 行尾日期显示**完整年月日**（`2026-10-21 14:30`），与首页那种只给 "10-21" 的短格式区分开：
 * 查询结果的天数范围由用户自己定，横跨几年时没有年份就得靠猜。
 */
@Composable
fun LedgerSearchScreen(
    viewModel: LedgerSearchViewModel,
    onOpenRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyword by viewModel.keyword.collectAsStateWithLifecycle()
    val from by viewModel.fromDate.collectAsStateWithLifecycle()
    val to by viewModel.toDate.collectAsStateWithLifecycle()
    val sortDesc by viewModel.sortDesc.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val categoriesById by viewModel.categoriesById.collectAsStateWithLifecycle()
    val accountsById by viewModel.accountsById.collectAsStateWithLifecycle()
    val bulkMode by viewModel.bulkMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val bulkMessage by viewModel.bulkMessage.collectAsStateWithLifecycle()

    // 用已收集的状态现算，而不是读 viewModel.hasNoCondition：
    // 那个属性直接读 StateFlow.value，不在 Compose 快照体系里，
    // 结果列表恰好没变时（比如输入的关键词命中全部现有记录）标题不会跟着刷
    val hasNoCondition = keyword.isBlank() && from.isBlank() && to.isBlank()

    // 长按单条的删除确认；独立于弹层，两个叠在一起会抢焦点
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    // 批量删除的结果提示过几秒自己消失
    LaunchedEffect(bulkMessage) {
        if (bulkMessage != null) {
            kotlinx.coroutines.delay(2_500)
            viewModel.clearBulkMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    PageHead(
                        eyebrow = "FIND A RECORD",
                        title = "搜索账单",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    ShiyeCard {
                        ShiyeTextField(
                            value = keyword,
                            onValueChange = { viewModel.keyword.value = it },
                            label = "关键词",
                            placeholder = "备注 / 分类 / 账户……",
                            singleLine = true
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DatePickerField(
                                label = "开始日期",
                                value = from,
                                onPick = { viewModel.fromDate.value = it },
                                onClear = { viewModel.fromDate.value = "" }
                            )
                            DatePickerField(
                                label = "结束日期",
                                value = to,
                                onPick = { viewModel.toDate.value = it },
                                onClear = { viewModel.toDate.value = "" }
                            )
                        }

                        Text(
                            text = "清空条件",
                            style = ShiyeType.label,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clickable { viewModel.clearConditions() }
                                .padding(vertical = 2.dp)
                        )

                        SegmentedControl(
                            options = listOf(true to "时间从新到旧", false to "时间从旧到新"),
                            selected = sortDesc,
                            onSelect = { viewModel.setSortDesc(it) },
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                item {
                    BulkDeleteBar(
                        bulkMode = bulkMode,
                        selectedCount = selectedIds.size,
                        totalCount = results.size,
                        title = if (hasNoCondition) "全部流水" else "搜索结果",
                        confirmTitle = "删除选中的 ${selectedIds.size} 笔？",
                        confirmMessage = "删除后无法恢复，也不会包含在备份中。",
                        onEnterBulk = { viewModel.setBulkMode(true) },
                        onSelectAll = viewModel::selectAll,
                        onDelete = viewModel::deleteSelected,
                        onCancel = { viewModel.setBulkMode(false) },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (bulkMode) {
                    item {
                        Text(
                            text = "勾选要删除的流水，再点左边「删除」。",
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (ready && results.isEmpty()) {
                    item {
                        EmptyState(
                            title = "无结果",
                            hint = "试试其他关键词或日期范围。"
                        )
                    }
                }

                items(items = results, key = { it.id }) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        categoryName = transaction.categoryLabel(categoriesById),
                        categoryIcon = categoriesById[transaction.categoryId]?.icon,
                        accountName = transaction.accountLabel(accountsById),
                        // 搜索结果横跨好几年，行尾带年份（"2026-10-21 14:30"）——
                        // 只写 "10-21" 的话哪一年得靠猜
                        fullDate = true,
                        selectable = bulkMode,
                        selected = transaction.id in selectedIds,
                        onClick = {
                            if (bulkMode) {
                                viewModel.toggleSelected(transaction.id)
                            } else {
                                onOpenRecord(transaction.id)
                            }
                        },
                        onLongClick = {
                            if (!bulkMode) pendingDelete = transaction
                        }
                    )
                }
            }
        }

        bulkMessage?.let { message ->
            Text(
                text = message,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }

    pendingDelete?.let { transaction ->
        ConfirmDialog(
            title = "删除这笔流水？",
            message = "${
                transaction.categoryLabel(categoriesById)
            } ${MoneyUtil.format(transaction.amount)}\n删除后不可恢复，本月结余和统计会一起变。",
            confirmText = "永久删除",
            onConfirm = {
                viewModel.deleteSingle(transaction.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
