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
import com.shiye.journal.domain.AiSummaryEntry
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.util.DateTimeUtil

/**
 * AI 建议的取舍对话框：原文 → 这次的结果 → 以前的结果，**一起摆出来让用户挑**。
 *
 * 为什么要有它：同一篇日记反复要建议时，上一次的结果会被下一次盖掉，
 * 用户根本没法比较"这次说得好还是上次说得好"。
 * 现在他把两次的都看到，勾着留、不勾的丢，自己说了算。
 *
 * 两条刻意的默认：
 * 1. **默认全勾**（这次的和以前的一起留），要舍弃得主动取消 ——
 *    一路点下来忘勾就把旧总结弄丢，太可惜了；
 * 2. 排序**从晚到早**，最新的在最前面，用户最想看的就是刚拿到的那条。
 */
@Composable
fun AiSummaryPickerDialog(
    original: String,
    current: String,
    previous: List<AiSummaryEntry>,
    keepCurrent: Boolean,
    kept: Set<String>,
    onToggleCurrent: () -> Unit,
    onToggleKept: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onCopy: () -> Unit = {}
) {
    val keptCount = (if (keepCurrent) 1 else 0) + previous.count { it.text in kept }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "选择要保留的建议",
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "勾着的会留下，按从晚到早排。日记正文不会动。",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 原文放最上面当参照：不看原文没法判断哪条建议更贴题
                Text(
                    text = "原文",
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(InnerRadius))
                        .background(ShiyeExtras.surface2)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = original,
                        style = ShiyeType.bodySecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6
                    )
                }

                Text(
                    text = "这次的结果",
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
                PickableSummary(
                    text = current,
                    checked = keepCurrent,
                    onToggle = onToggleCurrent,
                    highlight = true
                )

                if (previous.isNotEmpty()) {
                    Text(
                        text = "以前的结果（共 ${previous.size} 条）",
                        style = ShiyeType.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    previous.forEach { item ->
                        PickableSummary(
                            text = item.text,
                            checked = item.text in kept,
                            onToggle = { onToggleKept(item.text) },
                            time = DateTimeUtil.formatDateTime(item.createdAt)
                        )
                    }
                }

                Text(
                    text = "复制这次的结果",
                    style = ShiyeType.caption,
                    color = ShiyeExtras.accentStrong,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable(onClick = onCopy)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(
                    text = if (keptCount == 0) "清空" else "保留 $keptCount 条",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("丢弃这次的", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CardRadius)
    )
}

@Composable
private fun PickableSummary(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    time: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(InnerRadius))
            .background(if (highlight) ShiyeExtras.accentSoft else ShiyeExtras.surface2)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = text,
                style = ShiyeType.bodySecondary,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (time != null) {
                Text(
                    text = time,
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 卡片式的多条建议展示（编辑页用），带时间和单条删除 */
@Composable
fun AiSummaryCard(
    entries: List<AiSummaryEntry>,
    onClear: () -> Unit,
    onRemove: (AiSummaryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(ShiyeExtras.accentSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI 总结",
                style = ShiyeType.label,
                color = ShiyeExtras.accentStrong
            )
            Text(
                text = "清空",
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onClear)
            )
        }
        // 从晚到早：最新的在最上面
        entries.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 8.dp else 10.dp)
            ) {
                if (entries.size > 1) {
                    Text(
                        text = if (index == 0) "最新" else DateTimeUtil.formatDateTime(item.createdAt),
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = item.text,
                    style = ShiyeType.bodySecondary,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "移除这条",
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { onRemove(item) }
                )
            }
        }
    }
}
