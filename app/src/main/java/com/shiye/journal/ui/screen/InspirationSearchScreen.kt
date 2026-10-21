package com.shiye.journal.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiye.journal.data.model.Inspiration
import com.shiye.journal.domain.InspirationQuery
import com.shiye.journal.domain.SearchHighlight
import com.shiye.journal.ui.components.BulkDeleteBar
import com.shiye.journal.ui.components.DatePickerField
import com.shiye.journal.ui.components.EmptyState
import com.shiye.journal.ui.components.HighlightedText
import com.shiye.journal.ui.components.PageHead
import com.shiye.journal.ui.components.SelectMark
import com.shiye.journal.ui.components.SegmentedControl
import com.shiye.journal.ui.components.ShiyeCard
import com.shiye.journal.ui.components.ShiyeTextField
import com.shiye.journal.ui.theme.ShiyeType
import com.shiye.journal.ui.viewmodel.InspirationSearchViewModel
import com.shiye.journal.util.DateTimeUtil

/**
 * 灵感搜索页（规格 §6.3）+ 批量删除。
 *
 * 条件：关键词 + 开始/结束日期 + 排序，任一变化自动重查（ViewModel 里防抖 250ms）。
 * 结果按日期排序，可切换新旧方向；命中文字高亮；没有结果显示"无结果"。
 * 已永久删除的灵感本来就在库里不存在，不会被搜到。
 *
 * 结果**订阅数据库**，不是一次性快照：刚记下的一条立刻出现在搜索结果里，
 * 不用改一下关键词才肯刷新。
 *
 * 批量删除原先在首页顶栏，现在收进这里（[BulkDeleteBar]，按钮在结果区头部左边）。
 */
@Composable
fun InspirationSearchScreen(
    viewModel: InspirationSearchViewModel,
    modifier: Modifier = Modifier
) {
    val keyword by viewModel.keyword.collectAsStateWithLifecycle()
    val from by viewModel.fromDate.collectAsStateWithLifecycle()
    val to by viewModel.toDate.collectAsStateWithLifecycle()
    val sortDesc by viewModel.sortDesc.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val bulkMode by viewModel.bulkMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val bulkMessage by viewModel.bulkMessage.collectAsStateWithLifecycle()

    // 用已收集的状态现算，而不是读 viewModel.hasNoCondition：
    // 那个属性直接读 StateFlow.value，不在 Compose 快照体系里，
    // 结果列表恰好没变时（比如输入的关键词命中全部现有记录）标题不会跟着刷
    val hasNoCondition = InspirationQuery.hasNoCondition(keyword, from, to)

    // 批量删除的结果提示过几秒自己消失
    LaunchedEffect(bulkMessage) {
        if (bulkMessage != null) {
            kotlinx.coroutines.delay(2_500)
            viewModel.clearBulkMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    PageHead(
                        eyebrow = "FIND A THOUGHT",
                        title = "搜索灵感",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    ShiyeCard {
                        ShiyeTextField(
                            value = keyword,
                            onValueChange = { viewModel.keyword.value = it },
                            label = "关键词",
                            placeholder = "搜索灵感内容……",
                            singleLine = true
                        )

                        // 上下排而不是并排：日期框可以敲 8 位数字，并排时每格太窄会看不全
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

                        Text(
                            text = "清空条件",
                            style = ShiyeType.label,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clickable { viewModel.clearConditions() }
                                .padding(vertical = 2.dp)
                        )

                        SegmentedControl(
                            options = listOf(true to "日期从新到旧", false to "日期从旧到新"),
                            selected = sortDesc,
                            onSelect = { viewModel.setSortDesc(it) },
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                item {
                    BulkDeleteBar(
                        bulkMode = bulkMode,
                        selectedCount = selectedIds.size,
                        totalCount = results.size,
                        title = if (hasNoCondition) "全部灵感" else "搜索结果",
                        confirmTitle = "删除选中的 ${selectedIds.size} 条？",
                        confirmMessage = "删除后无法恢复，也不会包含在备份中。",
                        onEnterBulk = { viewModel.setBulkMode(true) },
                        onSelectAll = viewModel::selectAll,
                        onDelete = viewModel::deleteSelected,
                        onCancel = { viewModel.setBulkMode(false) },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (bulkMode) {
                    item {
                        Text(
                            text = "勾选要删除的灵感，再点左边「删除」。",
                            style = ShiyeType.caption,
                            color = MaterialTheme.colorScheme.primary
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

                items(items = results, key = { it.id }) { inspiration ->
                    SearchResultItem(
                        inspiration = inspiration,
                        keyword = keyword,
                        selectable = bulkMode,
                        selected = inspiration.id in selectedIds,
                        onClick = { viewModel.toggleSelected(inspiration.id) }
                    )
                }
            }
        }

        bulkMessage?.let { message ->
            Text(
                text = message,
                style = ShiyeType.caption,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

/**
 * 一条搜索结果。
 *
 * 与首页的气泡不同：这里要**高亮命中的关键词**，所以用自己的卡片而不是
 * [InspirationBubble] —— 气泡是"整条都重要"，搜索结果是"为什么它会命中"。
 *
 * 批量删除模式下左边多一个勾选圈，点整行都是切换勾选。
 */
@Composable
private fun SearchResultItem(
    inspiration: Inspiration,
    keyword: String,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    // 灵感正文可能很长，列表里只截取命中处附近的一段
    val excerpt = remember(inspiration.content, keyword) {
        SearchHighlight.excerpt(inspiration.content, keyword)
    }

    ShiyeCard(
        modifier = modifier,
        onClick = onClick,
        onLongClick = {}
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectable) {
                SelectMark(
                    selected = selected,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = DateTimeUtil.formatDateTime(inspiration.createdAt),
                    style = ShiyeType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HighlightedText(
                    text = excerpt,
                    keyword = keyword,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (inspiration.updatedAt > inspiration.createdAt) {
                    Text(
                        text = "已编辑",
                        style = ShiyeType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
