package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.data.model.Account
import com.shiye.journal.data.model.Category
import com.shiye.journal.data.model.Loan
import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.LoanStatus
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.data.repository.LedgerRepository
import com.shiye.journal.domain.RecordDraft
import com.shiye.journal.domain.RecordKind
import com.shiye.journal.domain.validate
import com.shiye.journal.util.DateTimeUtil
import com.shiye.journal.util.MoneyUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 「记一笔」（规格 §5.2 / §5.3 / §5.6）。
 *
 * 这张表单同时处理**普通收支**和**借款提醒**两种完全不同的记录：
 *
 * - 切到借款时，分类和账户的取值**留在内存里不清空**，但校验和保存都不看它们
 *   （[RecordDraft.validate] 只在非借款模式下要求分类）。来回切不会丢输入。
 * - 保存时按 [kind] 决定写哪张表。**绝不**因为用户在借款模式下买了东西
 *   就顺手写一条收入流水 —— 那是借款最大的坑。
 *
 * @param recordId 流水 id 或借款 id；null = 新建。两种表都查一遍即可确定是哪一种。
 * @param initialKind 新建时的默认类型；编辑已有记录时以记录本身为准。
 * @param initialDate 新建时的默认日期（「补账单」选中的那天）；null = 今天。
 *   编辑已有记录时一律以记录本身为准，这个参数不起作用。
 */
class RecordEditorViewModel(
    private val recordId: String?,
    initialKind: RecordKind,
    private val repository: LedgerRepository,
    initialDate: String? = null
) : ViewModel() {

    private val _ready = MutableStateFlow(recordId == null)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 编辑已有记录时为 true（标题与按钮文案会跟着变） */
    val isEdit: Boolean get() = recordId != null

    private val _kind = MutableStateFlow(initialKind)
    val kind: StateFlow<RecordKind> = _kind.asStateFlow()

    // ---------- 金额与备注 ----------

    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()

    private val _categoryId = MutableStateFlow("")
    val categoryId: StateFlow<String> = _categoryId.asStateFlow()

    /** 空串 = 未指定账户（规格 §5.5 允许流水不挂账户） */
    private val _accountId = MutableStateFlow("")
    val accountId: StateFlow<String> = _accountId.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _date = MutableStateFlow(
        // 日期来自路由参数（补账单时是用户选的那天）。格式不对就退回今天：
        // 宁可让用户改一次日期，也不要带着一个非法值进保存流程
        initialDate?.takeIf { DateTimeUtil.parseDate(it) != null } ?: DateTimeUtil.todayString()
    )
    val date: StateFlow<String> = _date.asStateFlow()

    private val _time = MutableStateFlow(DateTimeUtil.nowTimeString())
    val time: StateFlow<String> = _time.asStateFlow()

    private val _person = MutableStateFlow("")
    val person: StateFlow<String> = _person.asStateFlow()

    private val _dueDate = MutableStateFlow("")
    val dueDate: StateFlow<String> = _dueDate.asStateFlow()

    private val _errors = MutableStateFlow<List<String>>(emptyList())
    val errors: StateFlow<List<String>> = _errors.asStateFlow()

    /** 正在写入数据库。按钮据此置灰，同时挡住连点 */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /**
     * "保存成功"是**一次性事件**，不是状态。
     *
     * 之前这里用的是 StateFlow<Boolean>：一旦置 true 就一直为 true，
     * 页面只要重新挂上（旋转屏幕、ViewModel 被复用）就会被当成"刚保存完"而立刻退出 ——
     * 修成 SharedFlow 之后，事件只在真正发生的那一次被消费，重进页面不会再触发。
     */
    private val _savedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val savedEvent: SharedFlow<Unit> = _savedEvent.asSharedFlow()

    /** 找不到要编辑的记录（例如在别处被删了） */
    private val _missing = MutableStateFlow(false)
    val missing: StateFlow<Boolean> = _missing.asStateFlow()

    /** 全部分类（含归档），用于把已有记录的分类 id 换成名字 */
    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 记账时能选的分类：**只列启用（未归档）且与当前方向一致**的 */
    val selectableCategories: StateFlow<List<Category>> = combineCategoriesAndKind()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 记账时能选的账户：只列启用（未归档）的；另外始终提供"未指定账户" */
    val selectableAccounts: StateFlow<List<Account>> = repository.observeAccounts()
        .map { list -> list.filter { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 全部账户（含归档）。
     *
     * 用途：编辑一笔旧流水时，它挂的账户可能已经归档了。
     * 这时还得把那个账户显示出来（带"已归档"标记），否则用户看到的选中项是空的，
     * 保存又会默默维持原状 —— 界面和数据对不上，最难受的那一类 bug。
     */
    val allAccounts: StateFlow<List<Account>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (recordId != null) {
            viewModelScope.launch {
                val transaction = repository.getTransaction(recordId)
                if (transaction != null) {
                    applyTransaction(transaction)
                    return@launch
                }
                val loan = repository.getLoan(recordId)
                if (loan != null) {
                    applyLoan(loan)
                    return@launch
                }
                _missing.value = true
            }
        }
    }

    // ---------- 输入 ----------

    /**
     * 切换记录类型。
     *
     * **编辑已有记录时不许在"收支"和"借款"之间横跳**：那会变成
     * "用同一条 id 在另一张表里插一行"( 旧记录还留在原表里)，数据立刻对不上账。
     * 这种情况直接提示用户先定好是哪种记录 —— 改类型等于换一类数据，不是改字段。
     */
    fun setKind(kind: RecordKind) {
        if (isEdit && kind.isLoan() != _kind.value.isLoan()) {
            _errors.value = listOf(
                if (_kind.value.isLoan()) {
                    "这是一条借款提醒，改不成收支。可以先删掉再记一笔。"
                } else {
                    "这是一笔收支，改不成借款提醒。可以先删掉再记一笔。"
                }
            )
            return
        }
        _kind.value = kind
        // 方向变了，之前选的分类可能不匹配（支出分类不能挂在收入上）
        val wantedType = kind.transactionType() ?: return
        val stillValid = categories.value.firstOrNull { it.id == _categoryId.value }
        if (stillValid != null && stillValid.type != wantedType) {
            _categoryId.value = selectableCategories.value.firstOrNull()?.id.orEmpty()
        }
        _errors.value = emptyList()
    }

    /**
     * 金额输入保存的是**原始字符串**而不是即时转成的分。
     * 一边打字一边换算会让 "12." 这种中间状态被判非法，光标也会抖。
     */
    fun setAmount(text: String) {
        // 最多两位小数 + 只允许数字和点：交给 MoneyUtil 之前先做一层宽松过滤
        _amountText.value = text.filter { it.isDigit() || it == '.' }
        _errors.value = emptyList()
    }

    fun setCategory(id: String) {
        _categoryId.value = id
        _errors.value = emptyList()
    }

    fun setAccount(id: String) {
        _accountId.value = id
    }

    fun setNote(text: String) {
        _note.value = text
    }

    fun setDate(value: String) {
        _date.value = value
        _errors.value = emptyList()
    }

    fun setTime(value: String) {
        _time.value = value
        _errors.value = emptyList()
    }

    fun setPerson(text: String) {
        _person.value = text
        _errors.value = emptyList()
    }

    fun setDueDate(value: String) {
        _dueDate.value = value
        _errors.value = emptyList()
    }

    // ---------- 保存 ----------

    fun save() {
        // 连点保护：上一笔还在写、或刚写完还没退出页面时，再点一次不该再插一条
        if (_saving.value) return

        val kindNow = _kind.value
        val cents = MoneyUtil.parseToCents(_amountText.value) ?: 0L
        val draft = RecordDraft(
            kind = kindNow,
            amountCents = cents,
            categoryId = _categoryId.value,
            accountId = if (kindNow.isLoan()) "" else _accountId.value,
            person = _person.value,
            note = _note.value,
            date = _date.value,
            time = _time.value,
            dueDate = _dueDate.value
        )

        // 金额为空时 MoneyUtil 返回 null，这里补一条明确的提示
        val problems = draft.validate().toMutableList()
        if (MoneyUtil.parseToCents(_amountText.value) == null && problems.none { it.contains("金额") }) {
            problems += "请输入金额"
        }
        if (problems.isNotEmpty()) {
            _errors.value = problems
            return
        }

        _saving.value = true
        viewModelScope.launch {
            if (kindNow.isLoan()) {
                saveLoan(kindNow, draft)
            } else {
                saveTransaction(kindNow, draft)
            }
            _errors.value = emptyList()
            _saving.value = false
            _savedEvent.emit(Unit)
        }
    }

    private suspend fun saveTransaction(kindNow: RecordKind, draft: RecordDraft) {
        val now = DateTimeUtil.nowMillis()
        val type = kindNow.transactionType() ?: TransactionType.EXPENSE
        val existing = recordId?.let { repository.getTransaction(it) }
        val transaction = Transaction(
            id = recordId ?: UUID.randomUUID().toString(),
            type = type,
            amount = draft.amountCents,
            categoryId = draft.categoryId,
            categoryName = snapshotName(
                changedId = draft.categoryId,
                unchangedValue = existing?.categoryName,
                oldId = existing?.categoryId,
                lookup = { id -> repository.getCategory(id)?.name }
            ),
            accountId = draft.accountId,
            accountName = snapshotName(
                changedId = draft.accountId,
                unchangedValue = existing?.accountName,
                oldId = existing?.accountId,
                lookup = { id -> repository.getAccount(id)?.name }
            ),
            note = draft.note.trim(),
            date = draft.date,
            time = draft.time,
            dateTime = DateTimeUtil.millisOf(draft.date, draft.time),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        repository.saveTransaction(transaction)
    }

    /**
     * 名称快照的取值规则：
     *
     * - **分类/账户没换** → 沿用原来的快照。今天把「餐饮」改成「吃喝」，不该让上个月那笔账跟着改名。
     * - **换了** → 按新的 id 抄一份当前名字。
     * - **查不到**（分类已被真正删除，只剩 id）→ 留空串，显示时统一退化成"未分类"。
     */
    private suspend fun snapshotName(
        changedId: String,
        unchangedValue: String?,
        oldId: String?,
        lookup: suspend (String) -> String?
    ): String {
        if (changedId.isBlank()) return ""
        if (oldId != null && oldId == changedId) return unchangedValue.orEmpty()
        return lookup(changedId).orEmpty()
    }

    private suspend fun saveLoan(kindNow: RecordKind, draft: RecordDraft) {
        val now = DateTimeUtil.nowMillis()
        val existing = recordId?.let { repository.getLoan(it) }
        val loan = Loan(
            id = recordId ?: UUID.randomUUID().toString(),
            direction = kindNow.loanDirection() ?: LoanDirection.BORROW,
            person = draft.person.trim(),
            amount = draft.amountCents,
            note = draft.note.trim(),
            date = draft.date,
            dateTime = DateTimeUtil.millisOf(draft.date, draft.time),
            dueDate = draft.dueDate.trim().takeIf { it.isNotEmpty() },
            status = existing?.status ?: LoanStatus.PENDING,
            settledAt = existing?.settledAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        repository.saveLoan(loan)
    }

    // ---------- 内部 ----------

    private fun combineCategoriesAndKind() =
        combine(repository.observeCategories(), _kind) { all, kindNow ->
            val type = kindNow.transactionType() ?: return@combine emptyList()
            all.filter { it.active && it.type == type }.sortedBy { it.sortOrder }
        }

    private fun applyTransaction(transaction: Transaction) {
        _kind.value = when (transaction.type) {
            TransactionType.INCOME -> RecordKind.INCOME
            TransactionType.EXPENSE -> RecordKind.EXPENSE
        }
        _amountText.value = MoneyUtil.centsToInputText(transaction.amount)
        _categoryId.value = transaction.categoryId
        _accountId.value = transaction.accountId
        _note.value = transaction.note
        _date.value = transaction.date
        _time.value = transaction.time
        _ready.value = true
    }

    private fun applyLoan(loan: Loan) {
        _kind.value = when (loan.direction) {
            LoanDirection.BORROW -> RecordKind.BORROW
            LoanDirection.LEND -> RecordKind.LEND
        }
        _amountText.value = MoneyUtil.centsToInputText(loan.amount)
        _person.value = loan.person
        _dueDate.value = loan.dueDate.orEmpty()
        _note.value = loan.note
        _date.value = loan.date
        _ready.value = true
    }
}
