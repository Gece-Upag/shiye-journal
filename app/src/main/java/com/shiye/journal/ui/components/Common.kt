package com.shiye.journal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiye.journal.domain.SearchHighlight
import androidx.compose.foundation.layout.ColumnScope
import com.shiye.journal.ui.theme.ShiyeExtras
import com.shiye.journal.ui.theme.ShiyeType

/**
 * 圆角。原型里统一是 20px，这里沿用；
 * 卡片内部的小元素用 14dp，避免大圆角套小圆角显得笨重。
 */
val CardRadius = 20.dp
val InnerRadius = 14.dp

/**
 * 让用户输入一个名字（灵感页的新建 / 重命名）。
 *
 * 空名字不给过：一个没有名字的页在切换列表里就是一个空白行，
 * 看不出是哪一页，等于白建。
 */
@Composable
fun NameInputDialog(
    title: String,
    initial: String,
    confirmText: String,
    label: String = "名称",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = label,
                    style = ShiyeType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(InnerRadius)),
                    singleLine = true,
                    textStyle = ShiyeType.input.copy(color = MaterialTheme.colorScheme.onSurface),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ShiyeExtras.surface2,
                        unfocusedContainerColor = ShiyeExtras.surface2,
                        disabledContainerColor = ShiyeExtras.surface2,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isBlank()) {
                        error = "名称不能为空"
                    } else {
                        onConfirm(text.trim())
                    }
                }
            ) { Text(confirmText, color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CardRadius)
    )
}

/**
 * 通用卡片。
 *
 * 用 Surface + 手动描边而不是 elevation：原型的卡片是"纸压在纸上"的感觉 ——
 * 靠 1dp 的浅色描边区分层次，而不是靠投影。深色模式下投影看不见，描边依然有效。
 */
@Composable
fun ShiyeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(CardRadius)
    // combinedClickable 要求 onClick 必传，所以只想响应长按时传一个空实现。
    // 这样点击仍会有一点反馈（涟漪），但不会误跳页面。
    val clickableModifier = when {
        onClick != null && onLongClick != null ->
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)

        onClick != null -> Modifier.clickable(onClick = onClick)
        onLongClick != null -> Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)

        else -> Modifier
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(clickableModifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * 空状态。
 *
 * 规格明确禁止"看起来像有内容其实是假的"，所以空状态要**直白说明现在什么都没有**，
 * 并在 [hint] 里告诉用户下一步能做什么，而不是放一堆占位卡片。
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = ShiyeType.cardTitle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 二次确认弹窗。
 *
 * 删除类操作一律走它 —— 日记和灵感删掉就找不回来了（规格要求永久删除），
 * 所以必须在删除前再拦一次，并说清"不可恢复"。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDanger: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = ShiyeType.cardTitle,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = message,
                style = ShiyeType.bodySecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CardRadius)
    )
}

/**
 * 分段控件（支出/收入/借入/借出 这类切换）。
 *
 * 选中态用强调色浅底 + 强调色文字，不用实心块：
 * 实心块在深色模式下会太抢眼，跟整体克制的调子冲突。
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnerRadius))
            .background(ShiyeExtras.surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = ShiyeType.label,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * 页面大标题。原型里每个页面顶部是"英文小标 + 中文大标"的组合。
 */
@Composable
fun PageHead(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = eyebrow,
                style = ShiyeType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = ShiyeType.pageTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(bottom = 4.dp)) { trailing() }
        }
    }
}

/**
 * 一行"标签 + 数值"的统计条目。
 */
@Composable
fun StatRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = ShiyeType.bodySecondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = ShiyeType.cardTitle, color = valueColor)
    }
}

/**
 * 小圆角标签，用于"草稿""已完成"这类状态标记。
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ShiyeExtras.accentSoft,
    contentColor: Color = ShiyeExtras.accentStrong
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, style = ShiyeType.caption, color = contentColor)
    }
}

/**
 * 主操作按钮（实心强调色）。
 *
 * 文字色用 onPrimary 而不是写死白色：深色主题下强调色本身是浅绿，
 * 写死白色会变成"浅绿底白字"，对比度根本不够。
 */
@Composable
fun ShiyeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val background = if (enabled) MaterialTheme.colorScheme.primary else ShiyeExtras.surface2
    val content = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnerRadius))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = ShiyeType.cardTitle, color = content)
    }
}

/** 次级按钮（描边、透明底），用于"取消"这类不希望抢注意力的操作 */
@Composable
fun ShiyeOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnerRadius))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 右下角记一笔 / 写日记入口的两种模式。
 *
 * 日记叫"补写回忆"、记账叫"补账单"，模式本身是同一套，所以共用这一个枚举。
 */
enum class TodayEntryMode {
    /** 记 / 写今天：当天已写过就是"继续写今天" */
    TODAY,

    /** 补以前：先选一个过去的日期，再写那一天 */
    BACKFILL
}

/**
 * 日记列表页右下角的写日记入口（规格 §4.2 + 补写回忆）。
 *
 * 主按钮文案随当天是否已写而变化：还没写是"写下今天"，写过了是"继续写今天"——
 * 让用户点之前就知道会打开新的一篇还是已有的那篇。
 */
@Composable
fun TodayEntryButton(
    mode: TodayEntryMode,
    hasTodayEntry: Boolean,
    onWrite: () -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    EntryButton(
        mainText = when (mode) {
            TodayEntryMode.TODAY -> if (hasTodayEntry) "继续写今天" else "✦ 写下今天"
            TodayEntryMode.BACKFILL -> "✎ 补写回忆"
        },
        toggleText = if (mode == TodayEntryMode.TODAY) "补写回忆" else "写下今天",
        onWrite = onWrite,
        onToggleMode = onToggleMode,
        modifier = modifier
    )
}

/**
 * 记账首页右下角的记一笔入口（规格 §5.1 + 补账单）。
 *
 * 和日记那个按钮同一副样子，区别只在文案和日期上限：
 * 补账单只允许选**今天或更早**（记明天的账没有意义），日记的补写回忆同样是这条限制。
 *
 * 补账单先进日期弹窗、再进表单，**不动**「记一笔」的默认行为 ——
 * 记今天的账不该为了补账多一步。
 */
@Composable
fun LedgerEntryButton(
    mode: TodayEntryMode,
    onWrite: () -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    EntryButton(
        mainText = if (mode == TodayEntryMode.TODAY) "＋ 记一笔" else "✎ 补账单",
        toggleText = if (mode == TodayEntryMode.TODAY) "补账单" else "记一笔",
        onWrite = onWrite,
        onToggleMode = onToggleMode,
        modifier = modifier
    )
}

/**
 * 「主按钮 + 模式切换小按钮」的骨架，日记与记账共用。
 *
 * 主按钮左边紧挨着一个小按钮，**上面写的是"切过去会变成什么"** ——
 * 当前是"写下今天"时它显示"补写回忆"，反之显示"写下今天"。
 * 用目标态而不是一个 ⇄ 图标，是为了不看说明也知道点下去会发生什么。
 */
@Composable
private fun EntryButton(
    mainText: String,
    toggleText: String,
    onWrite: () -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 小切换按钮：显示"切过去会变成什么"
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(CardRadius))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    RoundedCornerShape(CardRadius)
                )
                .clickable(onClick = onToggleMode)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = toggleText,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(CardRadius))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onWrite)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mainText,
                style = ShiyeType.cardTitle,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * 批量删除模式下的勾选圈。
 *
 * 空心 = 未选，实心 + ✓ = 已选。刻意不用系统 CheckBox：
 * 它的默认配色是主题强调色之外的另一套颜色，深浅色下都和卡片对不上。
 */
@Composable
fun SelectMark(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .border(
                BorderStroke(
                    1.5.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(text = "✓", style = ShiyeType.caption, color = Color.White)
        }
    }
}

/** 细分隔线。列表项之间用它，比卡片间距更省空间。 */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

/**
 * 区块小标题，右侧带数量。
 *
 * 数量是**真实条数**：列表里有几条就写几条，不会为了"看起来有内容"写个假的。
 */
@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = ShiyeType.cardTitle, color = MaterialTheme.colorScheme.onSurface)
        if (trailing != null) {
            trailing()
        } else if (count != null) {
            Text(
                text = "$count 笔",
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 右下角主操作浮层按钮（规格 §5.1 的「记一笔」）。
 *
 * 用绝对定位而不是 Scaffold 的 FAB： screens 都嵌在同一个 Scaffold 的内容区里，
 * 由页面自己摆更直观，也不用处理"FAB 到底属于哪一层"的问题。
 */
@Composable
fun PrimaryFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CardRadius))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = ShiyeType.cardTitle,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * 搜索页结果区头部的**批量删除**入口。
 *
 * 按钮一律在这一行的**最左边**，不是右上角，也不跟标题挤在一起：
 * - 右上角是返回（应用壳的顶栏），把破坏性动作放在那儿太容易顺手点到；
 * - 它是"对下面这一整组结果做动作"，跟着结果标题走才像它的归属。
 *
 * 两种形态：
 * - 常态：左侧一个「批量删除」，右边是条数；
 * - 多选态：左侧换成「全选 / 删除 N 条 / 取消」，右边是已选条数。
 *
 * 删除要二次确认：一次勾掉十几条没有回头路，而勾错一条太容易了。
 */
@Composable
fun BulkDeleteBar(
    bulkMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    title: String,
    confirmTitle: String,
    confirmMessage: String,
    onEnterBulk: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (bulkMode) {
                TopBarTextButton(text = "全选", onClick = onSelectAll)
                TopBarTextButton(
                    text = if (selectedCount > 0) "删除 $selectedCount 条" else "删除",
                    isDanger = true,
                    enabled = selectedCount > 0,
                    onClick = { confirmDelete = true }
                )
                TopBarTextButton(text = "取消", onClick = onCancel)
            } else {
                TopBarTextButton(text = "批量删除", onClick = onEnterBulk)
                Text(
                    text = title,
                    style = ShiyeType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            text = if (bulkMode) "已选 $selectedCount 条" else "$totalCount 条",
            style = ShiyeType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = confirmTitle,
            message = confirmMessage,
            confirmText = "删除",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/**
 * 占比条（规格 §5.7 的"占比条形"）。
 *
 * 手写一条而不用图表库 —— 规格明确不要图表库，而且这里要表达的只有"相对多少"。
 *
 * @param ratio 0f~1f；总额为零时传 0f，画出来是空条而不是崩掉
 */
@Composable
fun RatioBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(ShiyeExtras.surface2)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

/**
 * 搜索结果里的命中文字高亮。
 *
 * 底色用强调色浅底 + 重色文字：深色模式下既不刺眼，也不会出现"浅底浅字"看不清的情况。
 * 日记搜索与灵感搜索共用它，保证两处命中样式一致。
 */
@Composable
fun HighlightedText(
    text: String,
    keyword: String,
    modifier: Modifier = Modifier
) {
    val ranges = remember(text, keyword) { SearchHighlight.ranges(text, keyword) }
    val highlight = ShiyeExtras.accentSoft
    val highlightText = ShiyeExtras.accentStrong
    val annotated = remember(text, ranges, highlight, highlightText) {
        buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                addStyle(
                    SpanStyle(background = highlight, color = highlightText, fontWeight = FontWeight.SemiBold),
                    range.first,
                    range.last + 1
                )
            }
        }
    }
    Text(
        text = annotated,
        style = ShiyeType.bodySecondary,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = modifier
    )
}
