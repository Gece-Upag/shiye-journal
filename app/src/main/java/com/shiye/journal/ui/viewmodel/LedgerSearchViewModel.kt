package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.data.model.Account
import com.shiye.journal.data.model.Category
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.data.repository.LedgerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 记账搜索页 + 批量删除。
 *
 * 批量删除原先挂在记账首页顶栏，现在收进这里：
 * 首页是"看"，搜索页是"找出来再处理"，要删一堆的时候本来就是先搜出来。
 * 一个条件都不填时这里列出**全部**流水，所以"想删就删"的能力没丢，只是多一步搜索。
 *
 * 结果订阅数据库（[LedgerRepository.observeSearch] 返回 Flow）而不是一次性查询：
 * 一次性查询只在条件变化那一刻打一次库，之后记一笔 / 删一笔，
 * 页面拿到的还是旧快照 —— 灵感的搜索就是这么"刚写的搜不到"的。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LedgerSearchViewModel(
    private val repository: LedgerRepository
) : ViewModel() {

    val keyword = MutableStateFlow("")
    val fromDate = MutableStateFlow("")
    val toDate = MutableStateFlow("")

    /** true = 时间从新到旧（默认，与统计页一致） */
    val sortDesc = MutableStateFlow(true)

    private val _results = MutableStateFlow<List<Transaction>>(emptyList())
    val results: StateFlow<List<Transaction>> = _results.asStateFlow()

    /** 是否已经完成过一次查询，用来区分"还在查"和"查完没有结果" */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 一个条件都没填时列出全部（此时 UI 标题写"全部流水"） */
    val hasNoCondition: Boolean
        get() = keyword.value.isBlank() && fromDate.value.isBlank() && toDate.value.isBlank()

    /** 分类 id → 分类，用于显示图标；名字仍以流水自带的快照为准 */
    val categoriesById: StateFlow<Map<String, Category>> = repository.observeCategories()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 账户 id → 账户，同上 */
    val accountsById: StateFlow<Map<String, Account>> = repository.observeAccounts()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---------------- 批量删除 ----------------

    /** 是否处于批量删除模式 */
    private val _bulkMode = MutableStateFlow(false)
    val bulkMode: StateFlow<Boolean> = _bulkMode.asStateFlow()

    /** 已勾选的流水 id */
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    /** 批量删除的结果提示（最近一次结果，旋转屏幕后仍可见） */
    private val _bulkMessage = MutableStateFlow<String?>(null)
    val bulkMessage: StateFlow<String?> = _bulkMessage.asStateFlow()

    init {
        viewModelScope.launch {
            combine(keyword, fromDate, toDate, sortDesc) { k, f, t, desc ->
                SearchConditions(k, f, t, desc)
            }
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .flatMapLatest { conditions ->
                    repository.observeSearch(
                        keyword = conditions.keyword,
                        fromDate = conditions.from,
                        toDate = conditions.to,
                        sort = if (conditions.desc) "desc" else "asc"
                    )
                }
                .onEach { _ready.value = true }
                .collect { list ->
                    _results.value = list
                    // 结果变了，勾过但已经不在结果里的 id 不该还留着
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

    /** 删除勾选的流水；完成后自动退出批量模式 */
    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteTransactions(ids)
            _bulkMessage.value = "已删除 ${ids.size} 笔流水"
            setBulkMode(false)
        }
    }

    fun clearBulkMessage() {
        _bulkMessage.value = null
    }

    /**
     * 长按单条时的删除。
     *
     * 与 [deleteSelected] 分开是因为提示语不同（"1 笔"不该走批量那条"已删除 N 笔"），
     * 而且它在非多选态下也能用 —— 搜索页不该逼人为了删一条先进入多选。
     */
    fun deleteSingle(id: String) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            _bulkMessage.value = "已删除 1 笔流水"
        }
    }

    private data class SearchConditions(
        val keyword: String,
        val from: String,
        val to: String,
        val desc: Boolean
    )

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
