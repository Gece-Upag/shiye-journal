package com.shiye.journal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.DiaryEntry
import com.shiye.journal.data.model.DiaryStatus
import com.shiye.journal.ui.components.BackfillDateDialog
import com.shiye.journal.ui.components.ConfirmDialog
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.HairlineDivider
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeDropdownMenu
import com.shiye.journal.ui.components.ShiyeMenuItem
import com.shiye.journal.ui.components.StatusChip
import com.shiye.journal.ui.components.TodayEntryButton
import com.shiye.journal.ui.components.TodayEntryMode
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.DiaryViewModel
import com.shiye.journal.util.DateTimeUtil

/**
 * 日记列表页。
 *
 * 规格 §4.2：标题「日记」+ 右侧搜索图标（在 [com.shiye.journal.ShiyeApp] 的顶部栏），
 * 列表按日期倒序，右下角「今天」按钮，点击进编辑页，长按出操作菜单（永久删除）。
 */
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    onOpenEntry: (String) -> Unit,
    onOpenToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    // 长按后待操作的那一条；非 null 时展开操作菜单
    var menuEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    // 二次确认独立于菜单：菜单收起后弹窗才出现，两个叠在一起会互相抢焦点
    var pendingDelete by remember { mutableStateOf<DiaryEntry?>(null) }

    val today = remember { DateTimeUtil.todayString() }
    val hasTodayEntry = entries.any { it.date == today }

    // 写日记入口的模式（写今天 / 补写回忆）与补写时的日期弹窗。
    // 用 rememberSaveable 而不是 remember：旋转屏幕后不该悄悄退回"写今天"，
    // 否则用户正要补写、转了一下屏就变成新建今天的日记
    var entryMode by rememberSaveable { mutableStateOf(TodayEntryMode.TODAY) }
    var backfillOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
                PageHead(
                    eyebrow = "DIARY",
                    title = "日记",
                    trailing = {
                        Text(
                            text = "共 ${entries.size} 篇",
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            if (entries.isEmpty()) {
                EmptyState(
                    title = "还没有日记",
                    hint = "点右下角「写下今天」，开始第一篇。"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = entries, key = { it.id }) { entry ->
                        Box {
                            DiaryListItem(
                                entry = entry,
                                onClick = { onOpenEntry(entry.date) },
                                onLongClick = { menuEntry = entry }
                            )
                            ShiyeDropdownMenu(
                                expanded = menuEntry?.id == entry.id,
                                onDismissRequest = { menuEntry = null }
                            ) {
                                ShiyeMenuItem(
                                    text = "永久删除",
                                    isDanger = true,
                                    onClick = {
                                        pendingDelete = entry
                                        menuEntry = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        TodayEntryButton(
            mode = entryMode,
            hasTodayEntry = hasTodayEntry,
            onWrite = {
                // 「补写回忆」不直接进编辑页：先问清是哪一天，选好再开写
                if (entryMode == TodayEntryMode.BACKFILL) {
                    backfillOpen = true
                } else {
                    onOpenToday()
                }
            },
            onToggleMode = {
                entryMode = if (entryMode == TodayEntryMode.TODAY) {
                    TodayEntryMode.BACKFILL
                } else {
                    TodayEntryMode.TODAY
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 20.dp)
        )
    }

    if (backfillOpen) {
        BackfillDateDialog(
            initialDate = today,
            onDismiss = { backfillOpen = false },
            onConfirm = { date ->
                backfillOpen = false
                onOpenEntry(date)
            }
        )
    }

    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = "删除这篇日记？",
            message = "${DateTimeUtil.formatDateChinese(entry.date)} 的日记将被永久删除，无法恢复。",
            confirmText = "删除",
            onConfirm = {
                viewModel.delete(entry.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun DiaryListItem(
    entry: DiaryEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ShiyeCard(onClick = onClick, onLongClick = onLongClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateTimeUtil.formatDateShortWithWeekday(entry.date),
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (entry.status == DiaryStatus.DRAFT) {
                    StatusChip(
                        text = "草稿",
                        backgroundColor = ShiyeExtras.surface2,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = entry.templateName,
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 摘要：一个字都没写时给出明确提示，而不是留一片空白让人以为加载失败
        Text(
            text = if (entry.isBlank) "这一天还没有记录" else entry.summary,
            style = ShiyeType.bodySecondary,
            color = if (entry.isBlank) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            },
            modifier = Modifier.padding(top = 8.dp)
        )

        HairlineDivider(modifier = Modifier.padding(vertical = 10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DateTimeUtil.formatDateTime(entry.updatedAt),
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 删除藏在长按里，这里给一句提示，否则用户根本不知道有这个入口
            Text(
                text = "长按可删除",
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
