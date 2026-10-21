package com.shiye.journal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.shiye.journal.data.backup.BackupManager
import com.shiye.journal.data.local.AppDatabase
import com.shiye.journal.data.local.MIGRATION_1_2
import com.shiye.journal.data.local.MIGRATION_2_3
import com.shiye.journal.data.local.MIGRATION_3_4
import com.shiye.journal.data.local.MIGRATION_4_5
import com.shiye.journal.data.repository.AiNoteRepository
import com.shiye.journal.data.repository.DiaryRepository
import com.shiye.journal.data.repository.InspirationRepository
import com.shiye.journal.data.repository.LedgerRepository
import com.shiye.journal.data.repository.SettingsRepository
import com.shiye.journal.data.repository.TemplateRepository
import com.shiye.journal.domain.RecordKind
import com.shiye.journal.domain.SaveFlushRegistry
import com.shiye.journal.features.ai.AiClient
import com.shiye.journal.features.ai.AiConfigStore
import com.shiye.journal.features.ai.OkHttpEngine
import com.shiye.journal.ui.viewmodel.AccountManageViewModel
import com.shiye.journal.ui.viewmodel.AiNotesViewModel
import com.shiye.journal.ui.viewmodel.AiSettingsViewModel
import com.shiye.journal.ui.viewmodel.CategoryManageViewModel
import com.shiye.journal.ui.viewmodel.DiaryEditorViewModel
import com.shiye.journal.ui.viewmodel.DiarySearchViewModel
import com.shiye.journal.ui.viewmodel.DiaryViewModel
import com.shiye.journal.ui.viewmodel.InspirationSearchViewModel
import com.shiye.journal.ui.viewmodel.InspirationViewModel
import com.shiye.journal.ui.viewmodel.LedgerSearchViewModel
import com.shiye.journal.ui.viewmodel.LedgerStatsViewModel
import com.shiye.journal.ui.viewmodel.LedgerViewModel
import com.shiye.journal.ui.viewmodel.LoanViewModel
import com.shiye.journal.ui.viewmodel.RecordEditorViewModel
import com.shiye.journal.ui.viewmodel.SettingsViewModel
import com.shiye.journal.ui.viewmodel.TemplateEditorViewModel
import com.shiye.journal.ui.viewmodel.TemplateViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 手写依赖容器。
 *
 * 项目规模不需要 Hilt 那套注解处理器：依赖只有"一个数据库 + 五个仓库"，
 * 手写容器能省掉一整套编译期代码生成，排查问题时调用栈也更短。
 *
 * 生命周期跟进程走（由 [ShiyeApplication] 持有），
 * 因此数据库实例全应用唯一，不会因为 Activity 重建而反复打开关闭。
 */
class AppContainer(private val context: Context) {

    /** 后台任务用的作用域；用 SupervisorJob 让一个任务失败不影响其他任务 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            // 故意不加 fallbackToDestructiveMigration：
            // 它会让 Room 在版本不匹配时静默清空数据库，等于悄悄删掉用户全部日记。
            // 宁可抛异常被看见，也不能让数据无声消失。
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    val diaryRepository: DiaryRepository by lazy { DiaryRepository(database) }

    val aiNoteRepository: AiNoteRepository by lazy { AiNoteRepository(database) }

    val templateRepository: TemplateRepository by lazy { TemplateRepository(database) }

    val ledgerRepository: LedgerRepository by lazy { LedgerRepository(database) }

    val inspirationRepository: InspirationRepository by lazy { InspirationRepository(database) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context.applicationContext) }

    // ---------------- V2 可选 AI（规格 §8） ----------------
    //
    // 配置存本机偏好、不进备份；客户端只在用户主动触发时才发请求。
    // 关闭状态下这些对象也会被创建，但它们不会做任何事 —— 不联网、不读 Key。

    val aiConfigStore: AiConfigStore by lazy { AiConfigStore(context.applicationContext) }

    /** 全进程共用一个 HTTP 客户端（OkHttp 自己管连接池） */
    val aiClient: AiClient by lazy { AiClient(OkHttpEngine()) }

    /** 备份/恢复/导出执行层。生命周期跟进程走，与数据库实例同寿。 */
    val backupManager: BackupManager by lazy {
        BackupManager(context.applicationContext, database, diaryRepository, settingsRepository)
    }

    /**
     * 应用级"强制保存"登记处。
     * 日记编辑页在创建时登记，Activity 退到后台（ON_STOP）时统一触发，
     * 保证按 Home / 切后台时最新内容已经提交。
     */
    val saveFlushRegistry: SaveFlushRegistry by lazy { SaveFlushRegistry() }

    // ---------------- ViewModel 工厂 ----------------
    //
    // 手写工厂而不是引入 Hilt：本项目每个 ViewModel 只依赖一两个仓库，
    // 引一整套注解处理器不划算。工厂随容器一起创建一次，之后复用。

    val diaryViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { DiaryViewModel(diaryRepository) }
    }

    val ledgerViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { LedgerViewModel(ledgerRepository) }
    }

    val ledgerStatsViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { LedgerStatsViewModel(ledgerRepository) }
    }

    val categoryManageViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { CategoryManageViewModel(ledgerRepository) }
    }

    val accountManageViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { AccountManageViewModel(ledgerRepository) }
    }

    val loanViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { LoanViewModel(ledgerRepository) }
    }

    /** 记一笔 / 编辑页。按记录 id 取实例，同一次编辑在旋转屏幕后仍是同一个 ViewModel */
    fun recordEditorViewModelFactory(
        recordId: String?,
        kind: RecordKind,
        initialDate: String? = null
    ): ViewModelProvider.Factory =
        viewModelFactory { RecordEditorViewModel(recordId, kind, ledgerRepository, initialDate) }

    val inspirationViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { InspirationViewModel(inspirationRepository, settingsRepository) }
    }

    /**
     * 灵感搜索页。
     *
     * 当前灵感页由 ViewModel **订阅**偏好得到，不在构造时取一次：
     * 这个 ViewModel 是按类复用的（没有 key），换了页再进搜索拿到的还是同一个实例，
     * 构造时取一次就等于永远搜第一次进入时那一页。
     */
    val inspirationSearchViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { InspirationSearchViewModel(inspirationRepository, settingsRepository) }
    }

    val ledgerSearchViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { LedgerSearchViewModel(ledgerRepository) }
    }

    val settingsViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { SettingsViewModel(settingsRepository, backupManager) }
    }

    val aiSettingsViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { AiSettingsViewModel(aiConfigStore, aiClient) }
    }

    val aiNotesViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory {
            AiNotesViewModel(aiNoteRepository, diaryRepository, ledgerRepository, aiClient, aiConfigStore)
        }
    }

    /**
     * 日记编辑页的工厂。**按日期传参，不能做成单例**：
     * 同一时刻栈里可能有多篇日记的编辑页（虽然本项目目前只有一个），
     * 而且不同日期必须拿到各自的 ViewModel。
     */
    fun diaryEditorViewModelFactory(date: String): ViewModelProvider.Factory =
        viewModelFactory {
            DiaryEditorViewModel(
                date,
                diaryRepository,
                templateRepository,
                saveFlushRegistry,
                aiClient,
                aiConfigStore,
                aiNoteRepository
            )
        }

    val diarySearchViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { DiarySearchViewModel(diaryRepository) }
    }

    val templateViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory { TemplateViewModel(templateRepository) }
    }

    fun templateEditorViewModelFactory(templateId: String?): ViewModelProvider.Factory =
        viewModelFactory { TemplateEditorViewModel(templateRepository, templateId) }

    private inline fun <VM : ViewModel> viewModelFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        }

    /** 是否已写入过预置数据；避免每次启动都查一遍表 */
    @Volatile
    private var seeded = false

    /**
     * 首次启动写入预置数据（模板 + 分类）。
     *
     * 放在后台线程，不阻塞启动。UI 观察的是 Flow，
     * 数据到位后界面会自动刷新，不需要额外的等待逻辑。
     */
    fun ensureSeededAsync() {
        if (seeded) return
        appScope.launch {
            ensureSeeded()
        }
    }

    suspend fun ensureSeeded() {
        if (seeded) return
        withContext(Dispatchers.IO) {
            templateRepository.ensureSeeded()
            ledgerRepository.ensureSeeded()
            inspirationRepository.ensureSeeded()
            seeded = true
        }
    }

    /**
     * 关闭数据库。备份恢复前需要先关掉，否则拷贝文件时可能有未落盘的 WAL 内容。
     * 阶段 5 的完整备份会用到。
     */
    suspend fun closeDatabase() = withContext(Dispatchers.IO) {
        if (database.isOpen) database.close()
    }
}
