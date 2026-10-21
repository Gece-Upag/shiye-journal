package com.shiye.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shiye.journal.features.ai.AiFieldOption
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType

/**
 * 发之前先选**发哪几个字段**。
 *
 * 为什么要有这一步：一篇日记可能有心情、正文、备注好几栏，
 * 一股脑全发出去既浪费（空字段白花 token），也不尊重隐私 ——
 * 有些内容用户就是不想让模型看到。选完还能在下一步的确认里
 * 看到实际要发的文字，再决定发不发。
 *
 * 空字段也列出来但不可选：让人知道"这篇还有一栏没写"，
 * 而不是悄悄略过、让他以为漏了什么。
 */
@Composable
fun AiFieldPickerDialog(
    options: List<AiFieldOption>,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    val selectable = options.filter { !it.isEmpty }
    val allPicked = selectable.isNotEmpty() && selectable.all { it.id in picked }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "选择要发给 AI 的内容",
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "只发勾选的那些。没有勾选的连一个字都不会送出这台手机。",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选 ${picked.size} / ${selectable.size}",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (allPicked) "全不选" else "全选",
                        style = ShiyeType.caption,
                        color = ShiyeExtras.accentStrong,
                        modifier = Modifier.clickable { onToggleAll(!allPicked) }
                    )
                }

                options.forEach { option ->
                    FieldOptionRow(
                        option = option,
                        checked = option.id in picked,
                        onToggle = { onToggle(option.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNext, enabled = picked.isNotEmpty()) {
                Text(
                    text = "下一步",
                    color = if (picked.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CardRadius)
    )
}

@Composable
private fun FieldOptionRow(
    option: AiFieldOption,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(InnerRadius))
            .background(ShiyeExtras.surface2)
            .clickable(enabled = !option.isEmpty, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { if (!option.isEmpty) onToggle() },
            enabled = !option.isEmpty,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = option.label + if (option.isEmpty) "（还没写）" else "",
                style = ShiyeType.label,
                color = if (option.isEmpty) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (!option.isEmpty) {
                Text(
                    text = option.content,
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
