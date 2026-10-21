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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.Account
import com.shiye.journal.data.model.AccountBalance
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.PlainAmountText
import com.shiye.journal.ui.components.PrimaryFab
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeDropdownMenu
import com.shiye.journal.ui.components.ShiyeMenuItem
import com.shiye.journal.ui.components.ShiyeTextField
import com.shiye.journal.ui.components.StatusChip
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.AccountManageViewModel
import com.shiye.journal.util.MoneyUtil

/**
 * 账户管理（规格 §5.5）。
 *
 * 两个刻意的设计：
 * 1. **余额不是字段，是算出来的**：期初 + 收入 − 支出。
 *    页面上的 balances 直接来自监听流水的 Flow，记一笔之后立刻就能看到余额变化。
 * 2. **有流水关联的账户删不掉**，只能归档。删掉账户会让那些流水失去归属，
 *    而"某个账户不打算再用了"这个需求，归档已经能满足（记账时不再出现）。
 *
 * 账户本身是**可选**的：流水可以不挂账户，仍会计入收支总额，只是不进任何账户余额。
 */
@Composable
fun AccountManageScreen(
    viewModel: AccountManageViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val total by viewModel.totalBalance.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var editorTarget by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }
    var blocked by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let {
            onShowMessage(it)
            viewModel.consumeMessage()
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
                    PageHead(eyebrow = "POTS OF MONEY", title = "账户管理")

                    ShiyeCard {
                        Text(
                            text = "全部余额合计",
                            style = ShiyeType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PlainAmountText(
                            cents = total,
                            large = true,
                            color = if (total < 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = "不含借入借出：借款不算任何账户的钱。",
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            if (balances.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有账户",
                        hint = "账户是可选的：没有账户也能记账，只是不算余额。"
                    )
                }
            } else {
                items(items = balances, key = { it.account.id }) { balance ->
                    AccountRow(
                        balance = balance,
                        onEdit = { editorTarget = balance.account },
                        onToggleArchive = { viewModel.setActive(balance.account.id, !balance.account.active) },
                        onDelete = { deleteTarget = balance.account }
                    )
                }
            }
        }

        PrimaryFab(
            text = "＋ 新建账户",
            onClick = { creating = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 24.dp)
        )
    }

    if (creating) {
        AccountEditorDialog(
            title = "新建账户",
            initialName = "",
            initialOpening = "",
            confirmText = "创建",
            onDismiss = { creating = false },
            onConfirm = { name, opening ->
                viewModel.save(null, name, opening)
                creating = false
            }
        )
    }

    editorTarget?.let { account ->
        AccountEditorDialog(
            title = "编辑账户",
            initialName = account.name,
            initialOpening = MoneyUtil.centsToInputText(account.openingBalance),
            confirmText = "保存",
            onDismiss = { editorTarget = null },
            onConfirm = { name, opening ->
                viewModel.save(account.id, name, opening)
                editorTarget = null
            }
        )
    }

    deleteTarget?.let { account ->
        ConfirmDialog(
            title = "删除账户「${account.name}」？",
            message = "删除后无法恢复。只有没有任何流水关联的账户才能删除。",
            confirmText = "永久删除",
            onConfirm = {
                viewModel.delete(account.id) { reason -> blocked = reason }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    blocked?.let { reason ->
        ConfirmDialog(
            title = "删不掉",
            message = "$reason。归档之后它不再出现在记账选项里，历史流水也还能查到。",
            confirmText = "知道了",
            isDanger = false,
            onConfirm = { blocked = null },
            onDismiss = { blocked = null }
        )
    }
}

@Composable
private fun AccountRow(
    balance: AccountBalance,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val account = balance.account
    ShiyeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = account.name,
                        style = ShiyeType.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!account.active) StatusChip(text = "已归档")
                }
                Text(
                    text = "期初 ${MoneyUtil.format(account.openingBalance)}",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                PlainAmountText(
                    cents = balance.balance,
                    color = if (balance.balance < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(text = "⋯", style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.primary)
                }
                ShiyeDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ShiyeMenuItem(text = "编辑", onClick = { expanded = false; onEdit() })
                    ShiyeMenuItem(
                        text = if (account.active) "归档" else "启用",
                        onClick = { expanded = false; onToggleArchive() }
                    )
                    ShiyeMenuItem(text = "删除", onClick = { expanded = false; onDelete() }, isDanger = true)
                }
            }
        }
    }
}

/** 账户名 + 期初余额的输入弹窗 */
@Composable
private fun AccountEditorDialog(
    title: String,
    initialName: String,
    initialOpening: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var opening by remember { mutableStateOf(initialOpening) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = ShiyeType.cardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShiyeTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    singleLine = true,
                    required = true
                )
                ShiyeTextField(
                    value = opening,
                    onValueChange = { opening = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "期初余额（元）",
                    placeholder = "0.00",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text(
                    text = "余额不用手填，会按「期初 + 收入 − 支出」自动算。",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, opening) }) {
                Text(text = confirmText, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
