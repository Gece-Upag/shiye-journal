package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.data.model.Inspiration
import com.shiye.journal.data.repository.InspirationRepository
import com.shiye.journal.data.repository.SettingsRepository
import com.shiye.journal.domain.InspirationQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 灵感搜索页（规格 §6.3）+ 批量删除。
 *
 * 与日记搜索同一套做法：关键词 / 开始日期 / 结束日期 / 排序，
 * 任一变化都自动重查，中间 250ms 防抖（拖动日期选择器时不该每次都打数据库）。
 *
 * 日期 → 毫秒的换算统一交给 [InspirationQuery.range]，
 * 这里只负责"条件变了就查"这件事，两边职责不混。
 *
 * 两个曾经真实存在的坑，改这个文件时别退回去：
 *
 * 1. **结果必须订阅数据库，不能是一次性查询。**
 *    原来 `repository.search(...)` 返回 `List`，只在条件变化那一刻打一次库，
 *    之后库里再变，页面拿到的还是那份快照 —— 表现就是"刚记下的搜不到，
 *    改一下关键词才突然出现"。现在走 Flow，表一变就重新发射。
 * 2. **当前灵感页要跟着偏好走，不能在构造时取一次。**
 *    原来 [pageId] 是构造参数，而这个 ViewModel 是按类复用的（没有 key），
 *    实例在第一次进搜索页时建好就不再重建 —— 之后换了页再进搜索，
 *    搜的还是**第一次进入时那一页**，新页里刚写的自然一条都搜不到。
 *    现在 [currentPageId] 直接订阅偏好里的"当前页"，换页会带着结果一起换。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class InspirationSearchViewModel(
    private val repository: InspirationRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val keyword = MutableStateFlow("")
    val fromDate = MutableStateFlow("")
    val toDate = MutableStateFlow("")

    /** true = 日期从新到旧（默认，规格 §6.3） */
    val sortDesc = MutableStateFlow(true)

    /**
     * 当前要搜哪一页。
     *
     * 订阅偏好而不是构造时取一次：换页之后进搜索，搜的得是**现在**这一页。
     */
    private val currentPageId: StateFlow<String> = settingsRepository.lastInspirationPageId
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            settingsRepository.currentLastInspirationPageId()
        )

    private val _results = MutableStateFlow<List<Inspiration>>(emptyList())
    val results: StateFlow<List<Inspiration>> = _results.asStateFlow()

    /** 是否已经完成过一次查询，用来区分"还在查"和"查完没有结果" */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 一个条件都没填时列出全部（此时 UI 标题写"全部灵感"） */
    val hasNoCondition: Boolean
        get() = InspirationQuery.hasNoCondition(keyword.value, fromDate.value, toDate.value)

    // ---------------- 批量删除 ----------------

    /** 是否处于批量删除模式 */
    private val _bulkMode = MutableStateFlow(false)
    val bulkMode: StateFlow<Boolean> = _bulkMode.asStateFlow()

    /** 已勾选的灵感 id */
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    /** 批量删除的结果提示（最近一次结果，旋转屏幕后仍可见） */
    private val _bulkMessage = MutableStateFlow<String?>(null)
    val bulkMessage: StateFlow<String?> = _bulkMessage.asStateFlow()

    init {
        viewModelScope.launch {
            combine(keyword, fromDate, toDate, sortDesc, currentPageId) { k, f, t, desc, page ->
                SearchConditions(k, f, t, desc, page)
            }
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                // flatMapLatest：条件一变就切到新的查询流上，旧的那条自动取消。
                // 每个查询流本身是**活的**，数据库变化会继续往下发射
                .flatMapLatest { conditions ->
                    val range = InspirationQuery.range(from = conditions.from, to = conditions.to)
                    repository.observeSearch(
                        keyword = InspirationQuery.normalizeKeyword(conditions.keyword),
                        fromMillis = range.fromInclusive,
                        toMillis = range.toExclusive,
                        sort = if (conditions.desc) "desc" else "asc",
                        pageId = conditions.pageId
                    )
                }
                .onEach { _ready.value = true }
                .collect { list ->
                    _results.value = list
                    // 结果变了，之前勾选但已经不在结果里的 id 不该再留着
                    if (_selectedIds.value.isNotEmpty()) {
                        val visible = list.map { it.id }.toSet()
                        _selectedIds.value = _selectedIds.value.intersect(visible)
                    }
                }
        }
    }

    /** 清空筛选条件；排序保留（它是显示偏好，不是筛选条件） */
    fun clearConditions() {
        keyword.value = ""
        fromDate.value = ""
        toDate.value = ""
    }

    fun setSortDesc(desc: Boolean) {
        sortDesc.value = desc
    }

    // ---------------- 批量删除 ----------------

    fun setBulkMode(enabled: Boolean) {
        _bulkMode.value = enabled
        if (!enabled) _selectedIds.value = emptySet()
    }

    fun toggleSelected(id: String) {
        _selectedIds.value = if (id in _selectedIds.value) {
            _selectedIds.value - id
        } else {
            _selectedIds.value + id
        }
    }

    /** 全选只选**当前结果里看得到**的那些，不是全库 */
    fun selectAll() {
        _selectedIds.value = _results.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** 删除勾选的灵感；完成后自动退出批量模式 */
    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val removed = repository.deleteAll(ids)
            _bulkMessage.value = "已永久删除 $removed 条灵感"
            setBulkMode(false)
        }
    }

    fun clearBulkMessage() {
        _bulkMessage.value = null
    }

    private data class SearchConditions(
        val keyword: String,
        val from: String,
        val to: String,
        val desc: Boolean,
        val pageId: String
    )

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
