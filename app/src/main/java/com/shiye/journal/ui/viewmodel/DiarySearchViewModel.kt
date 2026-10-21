package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.data.model.DiaryEntry
import com.shiye.journal.data.repository.DiaryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * 日记搜索页。
 *
 * 四个条件（关键词 / 开始日期 / 结束日期 / 排序）任一变化都会自动重查，
 * 中间加 250ms 防抖：日期选择器连续改动时不该每次都打一次数据库。
 *
 * 查询本身走 [DiaryRepository.search]（SQL LIKE），
 * 关键词高亮与片段截取交给 UI 层的 [com.shiye.journal.domain.SearchHighlight]，
 * 两边职责不混。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DiarySearchViewModel(private val repository: DiaryRepository) : ViewModel() {

    val keyword = MutableStateFlow("")
    val fromDate = MutableStateFlow("")
    val toDate = MutableStateFlow("")

    /** true = 日期从新到旧（默认，规格 §4.6） */
    val sortDesc = MutableStateFlow(true)

    private val _results = MutableStateFlow<List<DiaryEntry>>(emptyList())
    val results: StateFlow<List<DiaryEntry>> = _results.asStateFlow()

    /** 是否已经完成过一次查询。用来区分"还在查"和"查完没有结果" */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 当前是否一个条件都没填（此时列出全部，而不是显示"无结果"） */
    val hasNoCondition: Boolean
        get() = keyword.value.isBlank() && fromDate.value.isBlank() && toDate.value.isBlank()

    init {
        viewModelScope.launch {
            combine(keyword, fromDate, toDate, sortDesc) { k, f, t, desc ->
                SearchQuery(k, f, t, desc)
            }
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .collectLatest { query -> runSearch(query) }
        }
    }

    private suspend fun runSearch(query: SearchQuery) {
        _results.value = repository.search(
            keyword = query.keyword,
            from = query.from,
            to = query.to,
            sort = if (query.desc) "desc" else "asc"
        )
        _ready.value = true
    }

    /** 清空全部条件（排序保留，它是显示偏好不是筛选条件） */
    fun clearConditions() {
        keyword.value = ""
        fromDate.value = ""
        toDate.value = ""
    }

    fun setSortDesc(desc: Boolean) {
        sortDesc.value = desc
    }

    private data class SearchQuery(
        val keyword: String,
        val from: String,
        val to: String,
        val desc: Boolean
    )

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
