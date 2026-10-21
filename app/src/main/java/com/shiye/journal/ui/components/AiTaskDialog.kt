package com.shiye.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shiye.journal.features.ai.AiStage
import com.shiye.journal.features.ai.AiTask
import com.shiye.journal.features.ai.AiTaskKind
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType

/**
 * AI 任务的弹窗：确认发送 → 结果 → 采纳/复制/丢弃。
 *
 * **"确认发送"这一屏不是走过场**（规格 §8.2）：
 * 日记内容要离开设备了，用户必须有机会看清"要发什么、发到哪里"，然后才决定。
 * 所以这里把目标地址和内容预览原样摆出来，而不是一句"是否使用 AI"。
 */
@Composable
fun AiTaskDialog(
    task: AiTask,
    target: String,
    onConfirmSend: () -> Unit,
    onCancel: () -> Unit,
    onAdopt: () -> Unit,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit = {},
    onSaveToNotes: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = { if (task.stage != AiStage.RUNNING) onCancel() },
        title = {
            Text(
                text = task.title,
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (task.stage) {
                    // 选字段由 AiFieldPickerDialog 单独呈现，走不到这里
                    AiStage.PICK_FIELDS -> Unit

                    AiStage.CONFIRM_SEND -> {
                        Text(
                            text = "将把下面这段文字发送到 $target，用于${task.title}。",
                            style = ShiyeType.bodySecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ContentPreview(task.payloadPreview)
                        Text(
                            text = "只发送这一段，不会上传其它日记。",
                            style = ShiyeType.caption,
                            color = ShiyeExtras.accentStrong
                        )
                    }

                    AiStage.RUNNING -> {
                        Text(
                            text = "正在请求 $target …",
                            style = ShiyeType.bodySecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AiStage.RESULT -> {
                        Text(
                            text = "原文",
                            style = ShiyeType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ContentPreview(task.payloadPreview)
                        Text(
                            text = task.title + "结果",
                            style = ShiyeType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ContentPreview(
                            task.result.orEmpty(),
                            background = ShiyeExtras.accentSoft
                        )
                        // 复制与"存入 AI 栏目"放在正文里：AlertDialog 只有两个按钮位，
                        // 采纳/丢弃已经占了，这两条作为旁路更合适
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "复制结果",
                                style = ShiyeType.caption,
                                color = ShiyeExtras.accentStrong,
                                modifier = Modifier.clickable(onClick = onCopy)
                            )
                            if (onSaveToNotes != null) {
                                Text(
                                    text = "存入 AI 栏目（不改日记）",
                                    style = ShiyeType.caption,
                                    color = ShiyeExtras.accentStrong,
                                    modifier = Modifier.clickable(onClick = onSaveToNotes)
                                )
                            }
                        }
                    }

                    AiStage.FAILED -> {
                        Text(
                            text = task.error.orEmpty(),
                            style = ShiyeType.bodySecondary,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (task.stage) {
                AiStage.PICK_FIELDS -> Unit

                AiStage.CONFIRM_SEND -> TextButton(onClick = onConfirmSend) {
                    Text("发送", color = MaterialTheme.colorScheme.primary)
                }

                AiStage.RESULT -> TextButton(onClick = onAdopt) {
                    Text(
                        // 润色是"替换这个字段"，建议是"存进 AI 总结栏"，说清楚区别
                        if (task.kind == AiTaskKind.POLISH) "采纳" else "存入 AI 总结",
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AiStage.FAILED -> TextButton(onClick = onRetry) {
                    Text("重试", color = MaterialTheme.colorScheme.primary)
                }

                AiStage.RUNNING -> Unit
            }
        },
        dismissButton = {
            when (task.stage) {
                AiStage.PICK_FIELDS -> Unit

                AiStage.CONFIRM_SEND -> TextButton(onClick = onCancel) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                AiStage.RESULT -> TextButton(onClick = onDiscard) {
                    Text("丢弃", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                AiStage.FAILED -> TextButton(onClick = onCancel) {
                    Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                AiStage.RUNNING -> Unit
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CardRadius)
    )
}

@Composable
private fun ContentPreview(
    text: String,
    background: androidx.compose.ui.graphics.Color = ShiyeExtras.surface2
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnerRadius))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = ShiyeType.bodySecondary,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// 「AI 总结」卡片已经搬到 AiSummaryPickerDialog.kt ——
// 那里还多了"一次生成多条、逐条取舍"需要的复数版本与单条移除入口。

/** 字段标签行右端的小按钮（「AI 润色」） */
@Composable
fun FieldAiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ShiyeExtras.accentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = ShiyeType.caption, color = ShiyeExtras.accentStrong)
    }
}

/**
 * AI 替换后的提示条，带"撤销"。
 *
 * 采纳是**可回滚**的（规格 §8.3）：替换前存了原文快照，
 * 这里给用户一个明确的退路，而不是让他自己按 Ctrl+Z 去猜。
 */
@Composable
fun AiNoticeBar(
    text: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnerRadius))
            .background(ShiyeExtras.accentSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = ShiyeType.caption,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (canUndo) {
            Text(
                text = "撤销",
                style = ShiyeType.caption,
                color = ShiyeExtras.accentStrong,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clickable(onClick = onUndo)
            )
        }
        Text(
            text = "✕",
            style = ShiyeType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 10.dp)
                .clickable(onClick = onDismiss)
        )
    }
}
