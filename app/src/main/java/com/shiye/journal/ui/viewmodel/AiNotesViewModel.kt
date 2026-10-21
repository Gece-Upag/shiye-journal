package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.data.local.entity.AiNoteEntity
import com.shiye.journal.data.model.DiaryEntry
import com.shiye.journal.data.model.Loan
import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.data.repository.AiNoteRepository
import com.shiye.journal.data.repository.DiaryRepository
import com.shiye.journal.data.repository.LedgerRepository
import com.shiye.journal.domain.AiPrompts
import com.shiye.journal.features.ai.AiClient
import com.shiye.journal.features.ai.AiConfigStore
import com.shiye.journal.features.ai.AiMode
import com.shiye.journal.features.ai.AiDigest
import com.shiye.journal.features.ai.AiResult
import com.shiye.journal.features.ai.AiStage
import com.shiye.journal.features.ai.AiTask
import com.shiye.journal.features.ai.AiTaskKind
import com.shiye.journal.util.DateTimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 栏目页：存档列表 + 「最近七天建议」（规格 §8.5 / §8.6）。
 *
 * 建议这条链路有两条边界必须守住：
 * 1. **只发摘要不发全文**（§8.5）：每篇日记截取前若干字，够模型抓住大意即可；
 * 2. **借款绝不当收支**（§8.5）：借入借出单独成段，并在摘要里写明"不计入收支"，
 *    否则模型会把它当收入，得出"这个月很宽裕"这种完全错误的结论。
 */
class AiNotesViewModel(
    private val noteRepository: AiNoteRepository,
    private val diaryRepository: DiaryRepository,
    private val ledgerRepository: LedgerRepository,
    private val aiClient: AiClient,
    private val aiConfigStore: AiConfigStore
) : ViewModel() {

    /** 存档列表 */
    val notes: StateFlow<List<AiNoteEntity>> = noteRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** AI 是否可用 */
    val aiEnabled: StateFlow<Boolean> = aiConfigStore.activeConfig
        .map { it.isUsable }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 进行中的任务（建议） */
    private val _task = MutableStateFlow<AiTask?>(null)
    val task: StateFlow<AiTask?> = _task.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * 准备生成建议：把最近七天的数据整理成摘要，进入"待确认"阶段。
     *
     * **这时还没发出去** —— 摘要会先摆给用户看（§8.2 的发送前告知）。
     */
    fun prepareAdvice() {
        viewModelScope.launch {
            val digest = withContext(Dispatchers.IO) { buildDigest() }
            if (AiDigest.isEmpty(digest.entries, digest.income, digest.expense, digest.loans)) {
                _message.value = "最近七天还没有日记或记账，没什么可分析的"
                return@launch
            }
            _task.value = AiTask(
                kind = AiTaskKind.ADVICE,
                snapshot = AiDigest.build(
                    digest.entries, digest.income, digest.expense, digest.topExpenses, digest.loans
                ),
                stage = AiStage.CONFIRM_SEND
            )
        }
    }

    fun cancelTask() {
        _task.value = null
    }

    /** 用户确认后真正发出请求 */
    fun send() {
        val current = _task.value ?: return
        val config = aiConfigStore.currentActiveConfig()
        if (!config.isUsable) {
            _task.value = current.copy(stage = AiStage.FAILED, error = "AI 还没配置好")
            return
        }
        _task.value = current.copy(stage = AiStage.RUNNING)
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = aiClient.chat(config, AiPrompts.advice(current.snapshot, config.adviceRule))
            _task.value = when (outcome) {
                is AiResult.Success -> current.copy(stage = AiStage.RESULT, result = outcome.text)
                is AiResult.Failure -> current.copy(stage = AiStage.FAILED, error = outcome.failure.message(localTarget = config.mode == AiMode.LOCAL))
            }
        }
    }

    /** 把结果存进 AI 栏目（用户主动决定，规格 §8.5「需要用户主动决定是否保存」） */
    fun saveResult() {
        val result = _task.value?.result ?: return
        viewModelScope.launch {
            noteRepository.save(
                kind = AiNoteEntity.Kind.ADVICE,
                content = result,
                sourceLabel = "最近七天"
            )
            _task.value = null
            _message.value = "已存入 AI 栏目"
        }
    }

    fun discardResult() {
        _task.value = null
    }

    fun deleteNote(id: String) {
        viewModelScope.launch { noteRepository.delete(id) }
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---------- 内部：整理最近七天 ----------

    private data class DigestParts(
        val entries: List<Pair<String, String>>,
        val income: Long,
        val expense: Long,
        val topExpenses: List<Pair<String, Long>>,
        val loans: List<Triple<String, String, Long>>
    )

    private suspend fun buildDigest(): DigestParts {
        val to = DateTimeUtil.today()
        val from = to.minusDays(6)
        val fromDate = DateTimeUtil.formatDate(from)
        val toDate = DateTimeUtil.formatDate(to)

        // 仓库只有 Flow 接口（页面要用实时刷新），这里只需要一次快照，
        // 用 first() 取第一次发射即可，不必为一次性统计再加一套 suspend 方法
        val allEntries = diaryRepository.observeAll().first()
        val recent = allEntries
            .filter { it.date >= fromDate && it.date <= toDate }
            .sortedByDescending { it.date }
            .map { entry ->
                entry.date to textOf(entry)
            }

        val transactions = ledgerRepository.observeTransactionsInRange(fromDate, toDate).first()
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val topExpenses = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryName.ifBlank { "未分类" } }
            .map { (name, list) -> name to list.sumOf { it.amount } }
            .sortedByDescending { it.second }

        // 借款单独列出：它是债务提醒，不是收支
        val loans = ledgerRepository.observeLoans().first().map { loan ->
            Triple(
                if (loan.direction == LoanDirection.BORROW) "借入" else "借出",
                loan.person,
                loan.amount
            )
        }
        return DigestParts(recent, income, expense, topExpenses, loans)
    }

    /** 一篇日记的纯文本（只取有内容的字段） */
    private fun textOf(entry: DiaryEntry): String =
        AiPrompts.entryText(entry.fields.map { it.label to it.content })
}
