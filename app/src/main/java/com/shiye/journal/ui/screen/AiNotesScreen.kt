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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.local.entity.AiNoteEntity
import com.shiye.journal.features.ai.AiTaskKind
import com.shiye.journal.ui.components.AiTaskDialog
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.HairlineDivider
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.ShiyeButton
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.AiNotesViewModel
import com.shiye.journal.util.DateTimeUtil

/**
 * AI 栏目（规格 §8.4「存入独立 AI 栏目」/ §8.6）。
 *
 * 两个作用：
 * 1. 翻以前存下来的 AI 结果；
 * 2. 生成「最近七天的建议」（§8.5）。
 *
 * 这里明确告诉用户"AI 说的不是你写的" —— 每条都挂着来源标签，
 * 免得几个月后回头看，把模型的发挥当成自己的记忆。
 */
@Composable
fun AiNotesScreen(
    viewModel: AiNotesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<AiNoteEntity?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                PageHead(eyebrow = "AI NOTES", title = "AI 栏目")
                Text(
                    text = "这里只放你自己决定留下的 AI 结果，和日记分开存。",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    EmptyState(
                        title = "还没有保存过 AI 结果",
                        hint = if (aiEnabled) {
                            "点下面「生成最近七天的建议」试试。"
                        } else {
                            "先在「设置 → AI 辅助」里开启并配置。"
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 22.dp,
                        end = 22.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = notes, key = { it.id }) { note ->
                        AiNoteCard(
                            note = note,
                            onLongClick = { pendingDelete = note }
                        )
                    }
                }
            }
        }

        if (aiEnabled) {
            ShiyeButton(
                text = "生成最近七天的建议",
                onClick = viewModel::prepareAdvice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
                    .fillMaxWidth()
            )
        }
    }

    task?.let { current ->
        AiTaskDialog(
            task = current,
            target = "",
            onConfirmSend = viewModel::send,
            onCancel = viewModel::cancelTask,
            onAdopt = viewModel::saveResult,
            onDiscard = viewModel::discardResult,
            onRetry = viewModel::send
        )
    }

    pendingDelete?.let { note ->
        ConfirmDialog(
            title = "删除这条 AI 结果？",
            message = "删除后无法恢复，也不会包含在备份中。",
            confirmText = "删除",
            onConfirm = {
                viewModel.deleteNote(note.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun AiNoteCard(
    note: AiNoteEntity,
    onLongClick: () -> Unit
) {
    ShiyeCard(onClick = {}, onLongClick = onLongClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AiNoteEntity.Kind.labelOf(note.kind),
                style = ShiyeType.label,
                color = ShiyeExtras.accentStrong
            )
            Text(
                text = DateTimeUtil.formatDateTime(note.createdAt),
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HairlineDivider(modifier = Modifier.padding(vertical = 10.dp))

        Text(
            text = note.content,
            style = ShiyeType.bodySecondary,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (note.sourceLabel.isNotBlank()) {
            Text(
                text = "来源：${note.sourceLabel}",
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
