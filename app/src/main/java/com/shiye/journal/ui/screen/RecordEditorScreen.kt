package com.shiye.journal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.domain.RecordKind
import com.shiye.journal.ui.components.ChipSelector
import com.shiye.journal.ui.components.DatePickerField
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.SegmentedControl
import com.shiye.journal.ui.components.ShiyeButton
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeTextField
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.RecordEditorViewModel

/**
 * 记一笔 / 编辑一条记录（规格 §5.2、§5.3、§5.6）。
 *
 * 一张表单管四件事：收入、支出、借入、借出。切换类型的按钮分成两组要看懂：
 *
 * ```
 * 普通收支：[收入] [支出]   ← 要选分类、可选账户
 * 借款提醒：[借入] [借出]   ← 要填对方、可填到期日，**没有分类和账户**
 * ```
 *
 * 借款**不会**生成任何流水、不会影响账户余额，结清与否只改它自己的状态。
 *
 * 标题与按钮文案会跟着当前类型走，用户不用猜自己正在记什么。
 */
@Composable
fun RecordEditorScreen(
    viewModel: RecordEditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val amountText by viewModel.amountText.collectAsStateWithLifecycle()
    val categoryId by viewModel.categoryId.collectAsStateWithLifecycle()
    val accountId by viewModel.accountId.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val time by viewModel.time.collectAsStateWithLifecycle()
    val person by viewModel.person.collectAsStateWithLifecycle()
    val dueDate by viewModel.dueDate.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val categories by viewModel.selectableCategories.collectAsStateWithLifecycle()
    val allCategories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.selectableAccounts.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()

    // 保存成功后退出页面。收集的是"一次性事件流"而不是状态：
    // 用状态的话，只要页面重新挂上（旋转、ViewModel 复用）就会被误判成"刚保存完"而立刻退出。
    LaunchedEffect(Unit) {
        viewModel.savedEvent.collect { onBack() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 自带头部的页面：返回 + 当前在记什么
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) {
                Text(text = "‹ 返回", style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headTitle(viewModel.isEdit, kind),
                    style = ShiyeType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = headHint(kind),
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            missing -> EmptyState(
                title = "这条记录已经不在了",
                hint = "它可能刚被删除。返回后列表会自动刷新。"
            )

            !ready -> EmptyState(title = "正在载入…")

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // 键盘弹起时把可视区压到键盘上方，配合输入框的 bringIntoView，
                    // 正在敲的那个字段才会被顶上来（原理同 DiaryEditorScreen）
                    .imePadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // 编辑时只给"同一类"的两个选项：跨类切换会让一条记录跑到另一张表里，不允许
                    SegmentedControl(
                        options = if (viewModel.isEdit) {
                            if (kind.isLoan()) {
                                listOf(RecordKind.BORROW to RecordKind.BORROW.label, RecordKind.LEND to RecordKind.LEND.label)
                            } else {
                                listOf(RecordKind.EXPENSE to RecordKind.EXPENSE.label, RecordKind.INCOME to RecordKind.INCOME.label)
                            }
                        } else {
                            RecordKind.entries.map { it to it.label }
                        },
                        selected = kind,
                        onSelect = { viewModel.setKind(it) }
                    )
                }

                item {
                    ShiyeCard {
                        ShiyeTextField(
                            value = amountText,
                            onValueChange = { viewModel.setAmount(it) },
                            label = "金额（元）",
                            placeholder = "0.00",
                            singleLine = true,
                            required = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }

                if (kind.isLoan()) {
                    item {
                        ShiyeCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ShiyeTextField(
                                    value = person,
                                    onValueChange = { viewModel.setPerson(it) },
                                    label = "对方",
                                    placeholder = if (kind == RecordKind.BORROW) "向谁借的" else "借给谁了",
                                    singleLine = true,
                                    required = true
                                )
                                DatePickerField(
                                    label = "到期日（可不填）",
                                    value = dueDate,
                                    emptyText = "不约定",
                                    onPick = { viewModel.setDueDate(it) },
                                    onClear = { viewModel.setDueDate("") }
                                )
                            }
                        }
                    }
                } else {
                    item {
                        ShiyeCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "分类",
                                        style = ShiyeType.label,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    // 正在编辑的流水可能挂在已归档分类上，那一类也要显示出来，
                                    // 否则用户会看到一个"什么都没选"的分类栏，却又保存成功了
                                    val archivedCategory = allCategories.firstOrNull { it.id == categoryId && !it.active }
                                    val categoryOptions = buildList {
                                        archivedCategory?.let { add(it.id to "${it.icon} ${it.name}（已归档）") }
                                        addAll(categories.map { it.id to "${it.icon} ${it.name}" })
                                    }
                                    ChipSelector(
                                        options = categoryOptions,
                                        selectedId = categoryId,
                                        onSelect = { viewModel.setCategory(it) },
                                        emptyHint = "这个方向还没有启用的分类，去「分类管理」添加一个"
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "账户（可不选）",
                                        style = ShiyeType.label,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val archivedAccount = allAccounts.firstOrNull { it.id == accountId && !it.active }
                                    ChipSelector(
                                        options = buildList {
                                            add("" to "未指定账户")
                                            archivedAccount?.let { add(it.id to "${it.name}（已归档）") }
                                            accounts.forEach { add(it.id to it.name) }
                                        },
                                        selectedId = accountId,
                                        onSelect = { viewModel.setAccount(it) },
                                        emptyHint = "还没有账户。不指定账户也能记，只是不计入某个账户的余额"
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    ShiyeCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    DatePickerField(
                                        label = "日期",
                                        value = date,
                                        emptyText = "选择日期",
                                        onPick = { viewModel.setDate(it) }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ShiyeTextField(
                                        value = time,
                                        onValueChange = { viewModel.setTime(it) },
                                        label = "时间",
                                        singleLine = true
                                    )
                                }
                            }
                            ShiyeTextField(
                                value = note,
                                onValueChange = { viewModel.setNote(it) },
                                label = "备注",
                                placeholder = "可不填",
                                singleLine = false,
                                minLines = 3
                            )
                        }
                    }
                }

                if (errors.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            errors.forEach { message ->
                                Text(
                                    text = "• $message",
                                    style = ShiyeType.caption,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                item {
                    ShiyeButton(
                        text = if (saving) "保存中…" else saveLabel(viewModel.isEdit, kind),
                        enabled = !saving,
                        onClick = { viewModel.save() }
                    )
                }

                if (kind.isLoan()) {
                    item {
                        Text(
                            text = "借款只是一条提醒：不计入收入支出，也不会动账户余额。真还了钱，请另外记一笔收支。",
                            style = ShiyeType.caption,
                            color = ShiyeExtras.accentStrong
                        )
                    }
                }
            }
        }
    }
}

private fun headTitle(isEdit: Boolean, kind: RecordKind): String =
    if (isEdit) "编辑${kind.label}记录" else "记一笔 · ${kind.label}"

private fun headHint(kind: RecordKind): String = when (kind) {
    RecordKind.EXPENSE -> "花的钱，会计入本月支出"
    RecordKind.INCOME -> "进的钱，会计入本月收入"
    RecordKind.BORROW -> "别人借给我，我有天要还"
    RecordKind.LEND -> "我借给别人，别人有天要还"
}

private fun saveLabel(isEdit: Boolean, kind: RecordKind): String =
    if (isEdit) "保存修改" else "保存${kind.label}"
