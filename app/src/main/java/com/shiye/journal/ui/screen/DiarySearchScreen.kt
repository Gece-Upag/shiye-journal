package com.shiye.journal.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.DiaryEntry
import com.shiye.journal.domain.SearchHighlight
import com.shiye.journal.ui.components.DatePickerField
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.HighlightedText
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.SegmentedControl
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeTextField
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.DiarySearchViewModel
import com.shiye.journal.util.DateTimeUtil

/**
 * 日记搜索页（规格 §4.6）。
 *
 * 条件：关键词 + 开始/结束日期 + 排序。任一项变化自动重查（ViewModel 里做了防抖）。
 * 两个日期端点各自可清空，互不影响。
 *
 * 结果里显示日期 + 命中片段，命中文字高亮；点结果打开原日记。
 * 关键词为空且日期为空时列出全部 —— 这时标题写"全部日记"，
 * 避免用户以为"没结果显示这么多"是 bug。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiarySearchScreen(
    viewModel: DiarySearchViewModel,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyword by viewModel.keyword.collectAsStateWithLifecycle()
    val from by viewModel.fromDate.collectAsStateWithLifecycle()
    val to by viewModel.toDate.collectAsStateWithLifecycle()
    val sortDesc by viewModel.sortDesc.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()

    // 现算而不是读 viewModel.hasNoCondition：那个属性读的是 StateFlow.value，
    // 不在快照体系里，条件变了但结果列表恰好相等时标题不会刷新
    val hasNoCondition = keyword.isBlank() && from.isBlank() && to.isBlank()

    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
            PageHead(eyebrow = "FIND A MEMORY", title = "搜索日记")
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ShiyeCard {
                    ShiyeTextField(
                        value = keyword,
                        onValueChange = { viewModel.keyword.value = it },
                        label = "关键词",
                        placeholder = "搜索正文、字段标签……",
                        singleLine = true
                    )

                    // 上下排而不是并排：日期框现在要能直接敲 8 位数字，
                    // 并排时每格只剩一半宽度，字会被挤得看不全。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DatePickerField(
                            label = "开始日期",
                            value = from,
                            onPick = { viewModel.fromDate.value = it },
                            onClear = { viewModel.fromDate.value = "" }
                        )
                        DatePickerField(
                            label = "结束日期",
                            value = to,
                            onPick = { viewModel.toDate.value = it },
                            onClear = { viewModel.toDate.value = "" }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "清空条件",
                            style = ShiyeType.label,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.clearConditions() }
                        )
                    }

                    SegmentedControl(
                        options = listOf(true to "日期从新到旧", false to "日期从旧到新"),
                        selected = sortDesc,
                        onSelect = { viewModel.setSortDesc(it) },
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasNoCondition) "全部日记" else "搜索结果",
                        style = ShiyeType.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${results.size} 篇",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (ready && results.isEmpty()) {
                item {
                    EmptyState(
                        title = "无结果",
                        hint = "试试其他关键词或日期范围。"
                    )
                }
            }

            items(items = results, key = { it.id }) { entry ->
                SearchResultItem(
                    entry = entry,
                    keyword = keyword,
                    onClick = { onOpenEntry(entry.date) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    entry: DiaryEntry,
    keyword: String,
    onClick: () -> Unit
) {
    ShiyeCard(onClick = onClick) {
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
            Text(
                text = entry.templateName,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val excerpt = remember(entry, keyword) {
            val source = entry.fields.firstOrNull { it.content.isNotBlank() }?.content.orEmpty()
            SearchHighlight.excerpt(source, keyword)
        }
        if (excerpt.isNotBlank()) {
            HighlightedText(
                text = excerpt,
                keyword = keyword,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                text = "这一天还没有记录",
                style = ShiyeType.bodySecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
