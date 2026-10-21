package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.data.model.Account
import com.shiye.journal.data.model.AccountBalance
import com.shiye.journal.data.model.Category
import com.shiye.journal.data.model.LedgerSummary
import com.shiye.journal.data.model.Loan
import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.data.repository.LedgerRepository
import com.shiye.journal.domain.CategoryStat
import com.shiye.journal.domain.DateInput
import com.shiye.journal.domain.DateInputResult
import com.shiye.journal.domain.LedgerPeriod
import com.shiye.journal.domain.PeriodKind
import com.shiye.journal.domain.buildCategoryStats
import com.shiye.journal.domain.canGoForward
import com.shiye.journal.domain.pendingAmount
import com.shiye.journal.domain.shift
import com.shiye.journal.domain.title
import com.shiye.journal.util.DateTimeUtil
import com.shiye.journal.util.MoneyUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** 规格 §5.4：收入分类和支出分类分别管理，每种最多这么多**有效（未归档）**分类 */
const val MAX_ACTIVE_CATEGORIES_PER_TYPE = 8

/**
 * 统计页（规格 §5.7）。
 *
 * 三条口径，写死在这里避免各页面各算各的：
 * 1. 周期的收入/支出/结余**只由流水算**，借款完全不参与；
 * 2. 借款单独出一块"概况"（本区间发生额 + 全局未结清），不混进上面那组数；
 * 3. 分类占比的分母是**本页列表自己的合计**，所以各项占比加起来一定是 100%。
 */
class LedgerStatsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _period = MutableStateFlow(LedgerPeriod.current(PeriodKind.MONTH))
    val period: StateFlow<LedgerPeriod> = _period.asStateFlow()

    /** 周期按钮上的标题，如「2026年10月」 */
    val periodTitle: StateFlow<String> = _period
        .map { it.title() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _period.value.title())

    val canGoForward: StateFlow<Boolean> = _period
        .map { it.canGoForward() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 手输日期的报错。为 null 时 UI 不显示。
     *
     * 单独一条流而不是塞进 [period]：周期是"当前在哪"，错误是"上一次输入为什么不灵"，
     * 两件事混在一起的话，跳转成功后还得记得顺手清错误，很容易漏。
     */
    private val _dateInputError = MutableStateFlow<String?>(null)
    val dateInputError: StateFlow<String?> = _dateInputError.asStateFlow()

    /** 换粒度时回到今天所在的那个周期，而不是停在原日期上让人困惑 */
    fun setKind(kind: PeriodKind) {
        _period.value = LedgerPeriod.current(kind)
        _dateInputError.value = null
    }

    fun move(delta: Int) {
        _period.value = _period.value.shift(delta)
        _dateInputError.value = null
    }

    /** 回到今天所在的周期 */
    fun goToday() {
        _period.value = LedgerPeriod.current(_period.value.kind)
        _dateInputError.value = null
    }

    /**
     * 手动跳到用户敲的那一天所在的周期。
     *
     * 解析规则（位数、合法性、未来拦截）全在 [DateInput] 里，这里只负责落地：
     * **不合法就一个字都不改**，当前周期保持不动，只把原因写进 [dateInputError] ——
     * 输错了不该让人"跳到一个莫名其妙的地方"，那比不动更让人慌。
     *
     * @return true = 已跳转，调用方可以收起输入框
     */
    fun jumpTo(text: String): Boolean {
        return when (val result = DateInput.parsePeriod(text, _period.value.kind)) {
            is DateInputResult.Ok -> {
                _period.value = LedgerPeriod(_period.value.kind, result.date)
                _dateInputError.value = null
                true
            }

            is DateInputResult.Error -> {
                _dateInputError.value = result.message
                false
            }
        }
    }

    fun consumeDateInputError() {
        _dateInputError.value = null
    }

    /** 本周期内的流水。周期一变就重新取，上一次尚未完成的查询会被自动取消。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> = _period
        .flatMapLatest { period ->
            repository.observeTransactionsInRange(period.fromDate, period.toDate)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<LedgerSummary> = transactions
        .map { list ->
            LedgerSummary(
                income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerSummary(0L, 0L))

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenseStats: StateFlow<List<CategoryStat>> =
        combine(transactions, categories) { list, cats ->
            buildCategoryStats(list.filter { it.type == TransactionType.EXPENSE }, cats)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeStats: StateFlow<List<CategoryStat>> =
        combine(transactions, categories) { list, cats ->
            buildCategoryStats(list.filter { it.type == TransactionType.INCOME }, cats)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loans: StateFlow<List<Loan>> = repository.observeLoans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 借款单独出一块：金额用借款金色，UI 不得把它们与收入/支出排在一起 */
    data class LoanOverview(
        /** 本区间内发生的借入 */
        val periodBorrow: Long,
        /** 本区间内发生的借出 */
        val periodLend: Long,
        /** 全局未结清的借入（我欠别人） */
        val pendingBorrow: Long,
        /** 全局未结清的借出（别人欠我） */
        val pendingLend: Long
    )

    /** 本区间借款发生额 + 全局未结清金额 */
    val loanOverview: StateFlow<LoanOverview> =
        combine(loans, _period) { all, period ->
            val inPeriod = all.filter { it.date >= period.fromDate && it.date <= period.toDate }
            LoanOverview(
                periodBorrow = inPeriod.filter { it.direction == LoanDirection.BORROW }.sumOf { it.amount },
                periodLend = inPeriod.filter { it.direction == LoanDirection.LEND }.sumOf { it.amount },
                pendingBorrow = pendingAmount(all, LoanDirection.BORROW),
                pendingLend = pendingAmount(all, LoanDirection.LEND)
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LoanOverview(0L, 0L, 0L, 0L)
        )
}

/**
 * 分类管理（规格 §5.4）。
 *
 * 删除的三道闸门，每一道都是为了"历史账目不能因为整理分类就变样"：
 * - 预置分类只能归档，不能删除；
 * - 自建分类若**已被流水引用**，也只能归档（否则那些流水会变成没有名字的记录）；
 * - 真删除前 UI 还要弹一次二次确认。
 */
class CategoryManageViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _type = MutableStateFlow(TransactionType.EXPENSE)
    val type: StateFlow<TransactionType> = _type.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前方向的分类，启用的在前（按下沉/archived 沉底） */
    val currentCategories: StateFlow<List<Category>> =
        combine(categories, _type) { all, type ->
            all.filter { it.type == type }.sortedWith(compareBy({ !it.active }, { it.sortOrder }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 还能不能新建：每种方向的有效分类有上限 */
    val canCreate: StateFlow<Boolean> =
        combine(categories, _type) { all, type ->
            all.count { it.type == type && it.active } < MAX_ACTIVE_CATEGORIES_PER_TYPE
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setType(type: TransactionType) {
        _type.value = type
        _message.value = null
    }

    fun create(name: String, icon: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                _message.value = "分类名称不能为空"
                return@launch
            }
            val typeNow = _type.value
            if (categories.value.any { it.type == typeNow && it.name == trimmed }) {
                _message.value = "已经有同名分类了"
                return@launch
            }
            if (!canCreate.value) {
                _message.value = "每种类型的分类最多 $MAX_ACTIVE_CATEGORIES_PER_TYPE 个，先归档或删除不用的"
                return@launch
            }
            repository.saveCategory(
                Category(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    type = typeNow,
                    icon = icon,
                    sortOrder = repository.nextCategorySortOrder(typeNow),
                    active = true,
                    isPreset = false,
                    createdAt = DateTimeUtil.nowMillis()
                )
            )
            _message.value = "已添加分类「$trimmed」"
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            val target = categories.value.firstOrNull { it.id == id } ?: return@launch
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                _message.value = "分类名称不能为空"
                return@launch
            }
            if (categories.value.any { it.id != id && it.type == target.type && it.name == trimmed }) {
                _message.value = "已经有同名分类了"
                return@launch
            }
            repository.saveCategory(target.copy(name = trimmed))
            _message.value = "已重命名"
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch {
            repository.setCategoryActive(id, active)
            _message.value = if (active) "已取消归档" else "已归档，历史流水仍归在这个分类下"
        }
    }

    /**
     * 真正删除。**返回是否删成功**（false = 有流水引用），由 UI 决定怎么提示。
     */
    fun delete(id: String, onBlocked: (String) -> Unit) {
        viewModelScope.launch {
            val target = categories.value.firstOrNull { it.id == id } ?: return@launch
            if (target.isPreset) {
                onBlocked("预置分类只能归档，不能删除")
                return@launch
            }
            val used = repository.countTransactionsOfCategory(id)
            if (used > 0) {
                onBlocked("还有 $used 笔流水用着它，只能归档")
                return@launch
            }
            repository.deleteCategory(id)
            _message.value = "已删除分类「${target.name}」"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/**
 * 账户管理（规格 §5.5）。
 *
 * 余额不是账户表里的字段，而是"期初 + 收入 − 支出"实时算出来的（见 [LedgerRepository.observeAccountBalances]），
 * 所以任何一笔流水改动后余额都会跟着变，不存在"改了流水忘了改余额"。
 */
class AccountManageViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 含已归档账户，因为要能看到它们的余额与历史 */
    val balances: StateFlow<List<AccountBalance>> = repository.observeAccountBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalBalance: StateFlow<Long> = balances
        .map { list -> list.sumOf { it.balance } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun save(id: String?, name: String, openingBalanceText: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                _message.value = "账户名称不能为空"
                return@launch
            }
            val cents = MoneyUtil.parseToCents(openingBalanceText.ifBlank { "0" })
            if (cents == null) {
                _message.value = "期初余额格式不对，最多两位小数"
                return@launch
            }
            val now = DateTimeUtil.nowMillis()
            val existing = id?.let { repository.getAccount(it) }
            if (existing == null && balances.value.any { it.account.name == trimmed }) {
                _message.value = "已经有同名账户了"
                return@launch
            }
            repository.saveAccount(
                com.shiye.journal.data.model.Account(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = trimmed,
                    openingBalance = cents,
                    sortOrder = existing?.sortOrder ?: repository.nextAccountSortOrder(),
                    active = existing?.active ?: true,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
            _message.value = if (existing == null) "已添加账户「$trimmed」" else "已保存"
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch {
            repository.setAccountActive(id, active)
            _message.value = if (active) "已启用" else "已归档，记账时不再出现在选项里"
        }
    }

    /** 有流水的账户不能删除，只能归档 —— 删掉会让那些流水失去归属 */
    fun delete(id: String, onBlocked: (String) -> Unit) {
        viewModelScope.launch {
            val target = balances.value.firstOrNull { it.account.id == id }?.account ?: return@launch
            val used = repository.countTransactionsOfAccount(id)
            if (used > 0) {
                onBlocked("还有 $used 笔流水关联这个账户，只能归档")
                return@launch
            }
            repository.deleteAccount(id)
            _message.value = "已删除账户「${target.name}」"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/**
 * 借入借出提醒（规格 §5.6）。
 *
 * 一句话记住这里的边界：**结清只改状态**，不生成任何流水、不动任何账户余额。
 * 所以本页没有任何"入账到账户"的动作 —— 真要反映到余额，用户自己再去记一笔。
 */
class LoanViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val loans: StateFlow<List<Loan>> = repository.observeLoans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 我欠别人的（借入未结清） */
    val pendingBorrow: StateFlow<Long> = loans
        .map { pendingAmount(it, LoanDirection.BORROW) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 别人欠我的（借出未结清） */
    val pendingLend: StateFlow<Long> = loans
        .map { pendingAmount(it, LoanDirection.LEND) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun settle(id: String) {
        viewModelScope.launch {
            repository.settleLoan(id)
            _message.value = "已标记为结清（不会生成收支流水）"
        }
    }

    fun reopen(id: String) {
        viewModelScope.launch {
            repository.reopenLoan(id)
            _message.value = "已改回未结清"
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteLoan(id)
            _message.value = "已删除"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

// 名字怎么显示统一放在 Transaction.categoryLabel / accountLabel 里：
// 先取流水自带的名称快照，快照缺失时（迁移前的老数据、分类已被删）才退回到实时查表。
