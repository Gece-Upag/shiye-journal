package com.shiye.journal.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.FieldType
import com.shiye.journal.data.model.Template
import com.shiye.journal.data.model.TemplateField
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.InnerRadius
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.SegmentedControl
import com.shiye.journal.ui.components.ShiyeButton
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeOutlineButton
import com.shiye.journal.ui.components.ShiyeTextField
import com.shiye.journal.ui.components.StatusChip
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.TemplateEditorViewModel
import com.shiye.journal.ui.viewmodel.TemplateViewModel
import kotlinx.coroutines.launch

/**
 * 模板管理列表（规格 §4.5）。
 *
 * 每个模板可以设为默认、编辑、删除。删除前二次确认，
 * 并且明确写出"不会删除使用过它的旧日记"——这是用户最担心的一点，
 * 不说清楚的话很多人不敢删模板。
 */
@Composable
fun TemplateListScreen(
    viewModel: TemplateViewModel,
    onOpenEditor: (String?) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Template?>(null) }

    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let {
            onShowMessage(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
            PageHead(
                eyebrow = "TEMPLATE STUDIO",
                title = "模板",
                trailing = {
                    Text(
                        text = "＋ 新建",
                        style = ShiyeType.label,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenEditor(null) }
                    )
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = templates, key = { it.id }) { template ->
                TemplateItem(
                    template = template,
                    onSetDefault = { viewModel.setDefault(template.id) },
                    onEdit = { onOpenEditor(template.id) },
                    onDelete = { pendingDelete = template }
                )
            }
        }
    }

    pendingDelete?.let { template ->
        ConfirmDialog(
            title = "删除模板「${template.name}」？",
            message = "只删除模板本身，不会删除使用过它的日记 —— 每篇日记都保存了自己的字段快照。",
            confirmText = "删除",
            onConfirm = {
                viewModel.delete(template.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun TemplateItem(
    template: Template,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ShiyeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = template.name,
                        style = ShiyeType.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (template.isDefault) {
                        StatusChip(text = "默认")
                    }
                }
                Text(
                    text = template.fields.joinToString(" / ") { it.label.ifBlank { "未命名" } },
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!template.isDefault) {
                SmallAction(text = "设为默认", onClick = onSetDefault)
            }
            SmallAction(text = "编辑", onClick = onEdit)
            SmallAction(text = "删除", onClick = onDelete, isDanger = true)
        }
    }
}

@Composable
private fun SmallAction(
    text: String,
    onClick: () -> Unit,
    isDanger: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(InnerRadius))
            .background(ShiyeExtras.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = ShiyeType.label,
            color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 模板编辑 / 新建页。
 *
 * 字段可以增删改、上下移、切换单行多行、勾选必填。
 * 校验只在点保存时做（规格：不要在每次输入时强制拦截用户），
 * 错误以文字形式出现在顶部，不弹窗打断。
 */
@Composable
fun TemplateEditorScreen(
    viewModel: TemplateEditorViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        PageHead(
            eyebrow = "TEMPLATE STUDIO",
            title = if (viewModel.isNew) "新建模板" else "编辑模板"
        )

        if (loading) {
            Text(
                text = "正在打开…",
                style = ShiyeType.bodySecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            return
        }

        error?.let {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(InnerRadius))
                    .background(ShiyeExtras.dangerSoft)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = it,
                    style = ShiyeType.bodySecondary,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        ShiyeCard(modifier = Modifier.padding(top = 14.dp)) {
            ShiyeTextField(
                value = name,
                onValueChange = { viewModel.setName(it) },
                label = "模板名称",
                placeholder = "例如：旅行、每日复盘",
                singleLine = true
            )
        }

        Text(
            text = "字段 ${fields.size} 个",
            style = ShiyeType.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
        )

        fields.forEachIndexed { index, field ->
            FieldEditorRow(
                field = field,
                canMoveUp = index > 0,
                canMoveDown = index < fields.lastIndex,
                canRemove = fields.size > 1,
                onLabelChange = { viewModel.setLabel(field.id, it) },
                onTypeChange = { viewModel.setType(field.id, it) },
                onRequiredChange = { viewModel.setRequired(field.id, it) },
                onMoveUp = { viewModel.moveUp(field.id) },
                onMoveDown = { viewModel.moveDown(field.id) },
                onRemove = { viewModel.removeField(field.id) }
            )
        }

        ShiyeOutlineButton(
            text = "＋ 添加字段",
            onClick = { viewModel.addField() },
            modifier = Modifier.padding(top = 12.dp)
        )

        ShiyeButton(
            text = "保存模板",
            onClick = {
                scope.launch {
                    val failure = viewModel.save()
                    if (failure == null) onSaved() else error = failure
                }
            },
            modifier = Modifier.padding(top = 14.dp)
        )

        ShiyeOutlineButton(
            text = "取消",
            onClick = onCancel,
            modifier = Modifier.padding(top = 10.dp, bottom = 32.dp)
        )
    }
}

@Composable
private fun FieldEditorRow(
    field: TemplateField,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onLabelChange: (String) -> Unit,
    onTypeChange: (FieldType) -> Unit,
    onRequiredChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    ShiyeCard(modifier = Modifier.padding(bottom = 10.dp)) {
        ShiyeTextField(
            value = field.label,
            onValueChange = onLabelChange,
            label = "字段标签",
            placeholder = "例如：今天学到什么",
            singleLine = true
        )

        SegmentedControl(
            options = listOf(FieldType.SINGLE to "单行文本", FieldType.MULTI to "多行文本"),
            selected = field.type,
            onSelect = onTypeChange,
            modifier = Modifier.padding(top = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 必填：做成一个可点的小标签，比复选框省地方也更符合整体调性
            val requiredBackground = if (field.required) ShiyeExtras.accentSoft else ShiyeExtras.surface2
            val requiredColor = if (field.required) {
                ShiyeExtras.accentStrong
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(requiredBackground)
                    .clickable { onRequiredChange(!field.required) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (field.required) "✓ 必填" else "必填",
                    style = ShiyeType.label,
                    color = requiredColor
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniIconButton(text = "↑", enabled = canMoveUp, onClick = onMoveUp)
                MiniIconButton(text = "↓", enabled = canMoveDown, onClick = onMoveDown)
                MiniIconButton(text = "×", enabled = canRemove, onClick = onRemove, isDanger = true)
            }
        }
    }
}

@Composable
private fun MiniIconButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDanger: Boolean = false
) {
    val background = ShiyeExtras.surface2
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isDanger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = ShiyeType.label, color = color)
    }
}
