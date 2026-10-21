package com.shiye.journal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiye.journal.data.model.Account
import com.shiye.journal.data.model.Category
import com.shiye.journal.data.model.DiaryEntry
import com.shiye.journal.data.model.Inspiration
import com.shiye.journal.data.model.InspirationPage
import com.shiye.journal.data.model.LedgerSummary
import com.shiye.journal.data.model.ThemeMode
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.data.repository.DiaryRepository
import com.shiye.journal.data.backup.BackupCodec
import com.shiye.journal.data.backup.BackupException
import com.shiye.journal.data.backup.BackupManager
import com.shiye.journal.data.repository.InspirationRepository
import com.shiye.journal.data.repository.LedgerRepository
import com.shiye.journal.data.repository.SettingsRepository
import com.shiye.journal.domain.InspirationDraft
import com.shiye.journal.util.DateTimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri

/**
 * 各页面的 ViewModel。四个放在同一个文件里，因为它们都很短，
 * 拆成四个文件反而要在文件之间来回跳才能看清"每个页面到底依赖什么"。
 *
 * 生命周期：由 Activity 持有（在 [com.shiye.journal.ui.ShiyeApp] 里用 viewModel() 取），
 * 所以旋转屏幕不会丢数据。
 */

// ---------------- 日记 ----------------

class DiaryViewModel(private val repository: DiaryRepository) : ViewModel() {

    /** 全部日记，日期倒序。数据库一有变化就会自动刷新。 */
    val entries: StateFlow<List<DiaryEntry>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 删除一篇日记；字段靠数据库外键级联一起删掉 */
    fun delete(id: String) {
        viewModelScope.launch { repository.deleteById(id) }
    }
}

// ---------------- 记账 ----------------

/**
 * 记账首页（规格 §5.1）。
 *
 * 只有一个数据来源（本月流水），今日流水、最近 3 天、本月概览全部由它派生：
 * 三个位置如果各自查一次库，就会出现"底下列表变了但顶上结余没跟上"的错位。
 *
 * 借款**完全不进**这里任何一组数字 —— 借来的钱不是收入（规格 §5.1 明确）。
 */
class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {

    companion object {

        /**
         * 首页"最近 N 天"的窗口天数，**含今天**（往前取 N-1 天）。
         *
         * 页面标题也读这个常数，免得改了过滤范围忘了改那句"最近 N 天"。
         */
        const val RECENT_WINDOW_DAYS = 3
    }

    private val monthStart = DateTimeUtil.monthStart()
    private val monthEnd = DateTimeUtil.monthEnd()

    /** 本月流水，按时间倒序 */
    val monthTransactions: StateFlow<List<Transaction>> =
        repository.observeTransactionsInRange(
            fromDate = DateTimeUtil.formatDate(monthStart),
            toDate = DateTimeUtil.formatDate(monthEnd)
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 本月收支概览。
     *
     * 直接从本月流水派生，而不是再查一次汇总 SQL：
     * 这样两者一定一致，不会出现"列表变了但合计没变"的错位。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: StateFlow<LedgerSummary> = monthTransactions
        .map { list ->
            LedgerSummary(
                income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerSummary(0L, 0L))

    /** 今日流水 */
    val todayTransactions: StateFlow<List<Transaction>> = monthTransactions
        .map { list -> list.filter { it.date == DateTimeUtil.todayString() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 最近 [RECENT_WINDOW_DAYS] 天流水（含今天）。
     *
     * 取自本月流水而不是单独查：跨月时月初那几天会漏（例如 10 月 1 日看不到 9 月底的），
     * 这是刻意的选择 —— 首页这块是"最近记了什么"，跨月的边界留给统计页处理。
     */
    val recentTransactions: StateFlow<List<Transaction>> = monthTransactions
        .map { list ->
            val from = DateTimeUtil.formatDate(
                DateTimeUtil.today().minusDays((RECENT_WINDOW_DAYS - 1).toLong())
            )
            list.filter { it.date >= from }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 分类 id → 分类，用于把流水上的 id 换成名字与图标（含归档分类） */
    val categoriesById: StateFlow<Map<String, Category>> = repository.observeCategories()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 账户 id → 账户，同上；没有账户的流水显示"未指定账户" */
    val accountsById: StateFlow<Map<String, Account>> = repository.observeAccounts()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 本月起止，供 UI 显示"10月" */
    val monthLabel: String = "${monthStart.monthValue}月"

    /** 删除流水：删除后首页、统计、账户余额都由 Flow 自动重算 */
    fun deleteTransaction(id: String) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }
}

// ---------------- 灵感 ----------------

/**
 * 灵感页（规格 §6）。
 *
 * 两条容易写错、这里明确钉死的规则：
 * 1. **编辑是原地编辑**：保存更新原记录、不新建重复记录（§6.2），
 *    所以编辑态只记一个 id，UI 把内容回填进底部输入框即可，不需要第二个页面。
 * 2. **删掉正在编辑的那条要退出编辑态**：否则输入框里还留着它，
 *    再点保存会往一个已不存在的 id 上写，等于把删掉的记录又变出来（§6.2）。
 */
class InspirationViewModel(
    private val repository: InspirationRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** 全部灵感页 */
    val pages: StateFlow<List<InspirationPage>> = repository.observePages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 当前停留的灵感页。切页会记进偏好，下次打开还在同一页。
     *
     * 注意它**可能指向一个已经不存在的页**（删页 / 恢复备份之后）：
     * [currentPage] 会兜底回第一个页，UI 只读 [currentPage] 不读它。
     */
    val currentPageId: StateFlow<String> = settingsRepository.lastInspirationPageId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsRepository.currentLastInspirationPageId())

    /** 当前页对象；页还没建好或指向的页已不存在时返回 null，UI 据此显示空状态 */
    val currentPage: StateFlow<InspirationPage?> = combine(pages, currentPageId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 当前页的灵感，新的在最上面 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<Inspiration>> = currentPage
        .flatMapLatest { page ->
            if (page == null) flowOf(emptyList()) else repository.observeByPage(page.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 正在原地编辑的那条 id；null = 新建模式 */
    private val _editingId = MutableStateFlow<String?>(null)
    val editingId: StateFlow<String?> = _editingId.asStateFlow()

    /**
     * 正在编辑的记录本身，供 UI 预填输入框。
     *
     * 从 [items] 里取而不是另存一份：编辑期间内容只有输入框里那一处真相，
     * 存副本的话两边会打架。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val editing: StateFlow<Inspiration?> = combine(items, _editingId) { list, id ->
        if (id == null) null else list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 发送反馈：用于输入失败（如空内容、超长）时提示，而不是静默吞掉 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    /**
     * 灵感页操作的结果提示（目前只有删页）。
     *
     * 与 [errorMessage] 分开：一个是"你填的东西不行"，一个是"刚才那件事做完了"，
     * 颜色和停留时长都不一样，混成一个会让前者显得太温和、后者显得像报错。
     */
    private val _pageMessage = MutableStateFlow<String?>(null)
    val pageMessage: StateFlow<String?> = _pageMessage.asStateFlow()

    fun clearPageMessage() {
        _pageMessage.value = null
    }

    // ---------------- 灵感页 ----------------

    fun selectPage(id: String) {
        settingsRepository.setLastInspirationPageId(id)
        // 换页时退出编辑态：编辑的是上一页的某条，留着会在新页里对不上
        _editingId.value = null
    }

    /** 新建一页并立刻切过去 */
    fun createPage(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val page = repository.createPage(trimmed)
            selectPage(page.id)
        }
    }

    fun renamePage(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repository.renamePage(id, trimmed) }
    }

    /** 删页（页内内容一并删除），返回被删内容的条数 */
    fun deletePage(id: String) {
        viewModelScope.launch {
            val removed = repository.deletePage(id)
            _pageMessage.value = "已删除该页，页内 $removed 条灵感一并删除"
            _editingId.value = null
            // 删的正是当前页时切回第一个页，否则会停在一个不存在的页上
            if (currentPageId.value == id) {
                pages.value.firstOrNull()?.let { settingsRepository.setLastInspirationPageId(it.id) }
            }
        }
    }

    fun beginEdit(id: String) {
        _editingId.value = id
        _errorMessage.value = null
    }

    /** 退出编辑但不保存，保留用户已经敲进去的内容由 UI 自己决定 */
    fun cancelEdit() {
        _editingId.value = null
        _errorMessage.value = null
    }

    /**
     * 发送（新建）或保存（编辑）。
     *
     * @return 是否已受理；false 表示被规则拦下，原因在 [errorMessage] 里
     */
    fun submit(content: String): Boolean {
        val message = InspirationDraft.messageFor(content)
        if (message != null) {
            _errorMessage.value = message
            return false
        }
        val text = InspirationDraft.normalize(content)
        val editingIdSnapshot = _editingId.value
        val pageId = currentPage.value?.id ?: return false
        viewModelScope.launch {
            if (editingIdSnapshot == null) {
                repository.add(text, pageId)
            } else {
                // 内容没实质变化就不写库：无条件 update 会刷新 updatedAt，
                // 用户明明什么都没改，列表上却冒出「已编辑」
                val original = items.value.firstOrNull { it.id == editingIdSnapshot }
                if (original == null || InspirationDraft.changed(original.content, text)) {
                    repository.update(editingIdSnapshot, text)
                }
            }
            _errorMessage.value = null
        }
        // 保存后回到新建模式，避免连点两次把同一条改两遍
        _editingId.value = null
        return true
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
        if (_editingId.value == id) _editingId.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

// ---------------- 设置 ----------------

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    /** 当前主题模式，改了会立刻生效（ShiyeTheme 读的就是它） */
    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.currentThemeMode())

    /** 上次备份时间；null 表示从未备份 */
    val lastBackupAt: StateFlow<Long?> = repository.lastBackupAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.currentLastBackupAt())

    // ---------------- 备份 / 恢复 / 导出 ----------------
    //
    // 状态机：Idle →（导出中 / 执行中 / 预览待确认）→ Idle。
    // "成功/失败"是一次性事件，走 SharedFlow；用 StateFlow 存的话
    // 页面重新挂载（旋转屏幕）会把旧事件当新的再报一遍。

    /** 备份链路的当前步骤。UI 据此显示进度圈 / 确认对话框 / 忙碌态。 */
    sealed interface BackupStep {
        data object Idle : BackupStep

        /** Markdown 导出进行中，显示 已完成/总数 */
        data class MdExporting(val done: Int, val total: Int) : BackupStep

        /** 完整备份或恢复执行中（这类操作很快，只锁住按钮防止连点） */
        data object Busy : BackupStep

        /** 备份文件解析成功，等用户看完摘要点确认 */
        data class RestorePreview(val payload: BackupCodec.Payload) : BackupStep
    }

    private val _step = MutableStateFlow<BackupStep>(BackupStep.Idle)
    val step: StateFlow<BackupStep> = _step

    /**
     * 最近一次备份操作的结果说明（成功/失败都算）。
     *
     * 用 StateFlow 而不是一次性事件：这不是"刚发生的通知"，
     * 而是"当前状态下的结论"，页面旋转重建后显示同一条结论是对的。
     */
    data class BackupMessage(val text: String, val isError: Boolean)

    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message

    /** Markdown 导出：选好文件夹后开始 */
    fun exportMarkdown(treeUri: Uri) {
        if (_step.value !is BackupStep.Idle) return
        _step.value = BackupStep.MdExporting(0, 0)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = backupManager.exportMarkdown(treeUri) { done, total ->
                    _step.value = BackupStep.MdExporting(done, total)
                }
                _message.value = BackupMessage("导出完成，共写出 $count 篇日记", false)
            } catch (e: BackupException) {
                _message.value = BackupMessage(e.message ?: "导出失败", true)
            } catch (e: Exception) {
                _message.value = BackupMessage("导出失败：${e.message}", true)
            } finally {
                _step.value = BackupStep.Idle
            }
        }
    }

    /** 完整备份：选好保存位置后开始 */
    fun createBackup(targetUri: Uri) {
        if (_step.value !is BackupStep.Idle) return
        _step.value = BackupStep.Busy
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupManager.createBackup(targetUri)
                _message.value = BackupMessage("备份完成，已记录本次备份时间", false)
            } catch (e: BackupException) {
                _message.value = BackupMessage(e.message ?: "备份失败", true)
            } catch (e: Exception) {
                _message.value = BackupMessage("备份失败：${e.message}", true)
            } finally {
                _step.value = BackupStep.Idle
            }
        }
    }

    /** 恢复第一步：解析用户选中的备份文件，成功则进入预览，失败直接报消息 */
    fun parseBackupForRestore(sourceUri: Uri) {
        if (_step.value !is BackupStep.Idle) return
        _step.value = BackupStep.Busy
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = backupManager.readBackup(sourceUri)
                _step.value = BackupStep.RestorePreview(payload)
            } catch (e: BackupException) {
                _message.value = BackupMessage(e.message ?: "备份文件无法使用", true)
                _step.value = BackupStep.Idle
            } catch (e: Exception) {
                _message.value = BackupMessage("读取备份失败：${e.message}", true)
                _step.value = BackupStep.Idle
            }
        }
    }

    /** 恢复第二步：用户看过摘要、明确确认后，原子替换全部数据 */
    fun confirmRestore() {
        val payload = (_step.value as? BackupStep.RestorePreview)?.payload ?: return
        _step.value = BackupStep.Busy
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupManager.restore(payload)
                _message.value = BackupMessage("恢复完成", false)
            } catch (e: Exception) {
                // 事务已回滚，数据停在恢复前；把原因说清楚
                _message.value = BackupMessage("恢复失败，当前数据未受影响：${e.message}", true)
            } finally {
                _step.value = BackupStep.Idle
            }
        }
    }

    fun cancelRestore() {
        if (_step.value is BackupStep.RestorePreview) _step.value = BackupStep.Idle
    }

    /** 完整备份文件的默认文件名（含时间，避免同目录多次备份互相顶掉） */
    fun suggestedBackupName(): String = backupManager.suggestedBackupName()

    fun setThemeMode(mode: ThemeMode) {
        repository.setThemeMode(mode)
    }
}
