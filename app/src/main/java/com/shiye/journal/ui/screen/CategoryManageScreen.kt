package com.shiye.journal.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.Category
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.InnerRadius
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.PrimaryFab
import com.shiye.journal.ui.components.SectionHead
import com.shiye.journal.ui.components.SegmentedControl
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeDropdownMenu
import com.shiye.journal.ui.components.ShiyeMenuItem
import com.shiye.journal.ui.components.ShiyeTextField
import com.shiye.journal.ui.components.StatusChip
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.MAX_ACTIVE_CATEGORIES_PER_TYPE
import com.shiye.journal.ui.viewmodel.CategoryManageViewModel

/** 新建分类时可选的图标。沿用"一个字符当图标"的做法，不用图片资源。 */
private val CATEGORY_ICONS = listOf("◇", "☕", "◍", "↗", "⌂", "✦", "✚", "✎", "◔", "◉", "＋", "％", "★", "♫", "♨")

/**
 * 分类管理（规格 §5.4）。
 *
 * 收入与支出分开管理，每种方向最多 [MAX_ACTIVE_CATEGORIES_PER_TYPE] 个**有效**分类。
 *
 * 删除这件事被刻意做得不容易：
 * - 预置分类只能归档；
 * - 自建分类只要有流水在用，也只能归档（删掉会让那些流水失去名字）；
 * - 就算可以删，也还要过一道二次确认。
 * 因为"顺手删掉一个分类"的代价是历史账单变得看不懂。
 */
@Composable
fun CategoryManageScreen(
    viewModel: CategoryManageViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val categories by viewModel.currentCategories.collectAsStateWithLifecycle()
    val canCreate by viewModel.canCreate.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var editorTarget by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }
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
                    PageHead(eyebrow = "KEEP IT TIDY", title = "分类管理")

                    SegmentedControl(
                        options = listOf(TransactionType.EXPENSE to "支出分类", TransactionType.INCOME to "收入分类"),
                        selected = type,
                        onSelect = { viewModel.setType(it) }
                    )

                    val activeCount = categories.count { it.active }
                    Text(
                        text = "有效 $activeCount / $MAX_ACTIVE_CATEGORIES_PER_TYPE 个；归档的分类仍会照常出现在历史统计里。",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (categories.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有${if (type == TransactionType.EXPENSE) "支出" else "收入"}分类",
                        hint = "点右下角新建一个。"
                    )
                }
            } else {
                items(items = categories, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        onRename = { editorTarget = category },
                        onToggleArchive = { viewModel.setActive(category.id, !category.active) },
                        onDelete = { deleteTarget = category }
                    )
                }
            }
        }

        PrimaryFab(
            text = "＋ 新建分类",
            onClick = {
                if (canCreate) creating = true
                else onShowMessage("每类最多 $MAX_ACTIVE_CATEGORIES_PER_TYPE 个有效分类，先归档不用的")
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 24.dp)
        )
    }

    if (creating) {
        CategoryNameDialog(
            title = "新建${if (type == TransactionType.EXPENSE) "支出" else "收入"}分类",
            initialName = "",
            initialIcon = CATEGORY_ICONS.first(),
            confirmText = "创建",
            onDismiss = { creating = false },
            onConfirm = { name, icon ->
                viewModel.create(name, icon)
                creating = false
            }
        )
    }

    editorTarget?.let { category ->
        CategoryNameDialog(
            title = "重命名分类",
            initialName = category.name,
            initialIcon = category.icon,
            confirmText = "保存",
            onDismiss = { editorTarget = null },
            onConfirm = { name, _ ->
                viewModel.rename(category.id, name)
                editorTarget = null
            }
        )
    }

    deleteTarget?.let { category ->
        ConfirmDialog(
            title = "删除分类「${category.name}」？",
            message = "删除后无法恢复。没有被流水用过的分类才能删除。",
            confirmText = "永久删除",
            onConfirm = {
                viewModel.delete(category.id) { reason ->
                    blocked = reason
                }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    blocked?.let { reason ->
        ConfirmDialog(
            title = "删不掉",
            message = "$reason。可以先归档，这样记账时不会再出现，历史统计也不受影响。",
            confirmText = "知道了",
            isDanger = false,
            onConfirm = { blocked = null },
            onDismiss = { blocked = null }
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onRename: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    ShiyeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = category.icon,
                    style = ShiyeType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column {
                    Text(
                        text = category.name,
                        style = ShiyeType.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (category.isPreset) StatusChip(text = "预置")
                        if (!category.active) StatusChip(text = "已归档")
                    }
                }
            }

            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(text = "⋯", style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.primary)
                }
                ShiyeDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ShiyeMenuItem(text = "重命名", onClick = { expanded = false; onRename() })
                    ShiyeMenuItem(
                        text = if (category.active) "归档" else "取消归档",
                        onClick = { expanded = false; onToggleArchive() }
                    )
                    ShiyeMenuItem(text = "删除", onClick = { expanded = false; onDelete() }, isDanger = true)
                }
            }
        }
    }
}

/**
 * 分类名 + 图标的输入弹窗。
 *
 * 图标用一排字符贴片选：可选图标就这十几个，比让用户自己粘贴 emoji 更可控
 * （粘贴来的字符可能是组合 emoji，列宽会乱掉）。
 */
@Composable
private fun CategoryNameDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }

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
                Text(
                    text = "图标",
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CATEGORY_ICONS.forEach { candidate ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (candidate == icon) MaterialTheme.colorScheme.primaryContainer
                                    else ShiyeExtras.surface2,
                                    RoundedCornerShape(InnerRadius)
                                )
                                .clickable { icon = candidate }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(text = candidate, style = ShiyeType.cardTitle)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, icon) }) {
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
