package com.shiye.journal.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.shiye.journal.data.local.AppDatabase
import com.shiye.journal.data.local.entity.InspirationPageEntity
import com.shiye.journal.data.repository.DiaryRepository
import com.shiye.journal.data.repository.SettingsRepository
import com.shiye.journal.domain.DiaryMarkdown
import com.shiye.journal.util.DateTimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 备份 / 恢复 / Markdown 导出的执行层。
 *
 * 校验与打包规则全部在 [BackupCodec]（可单测），这里只做三件"必须碰 Android"的事：
 * 1. 通过 SAF（contentResolver）读写用户用系统文件选择器选中的位置 —— 全程不申请存储权限；
 * 2. 在 Room 事务里原子替换数据 —— 任何一步失败自动回滚，恢复前的数据原封不动；
 * 3. 上报进度，让 UI 能显示"已完成/总数"。
 *
 * Markdown 导出与完整备份是两条独立链路（规格 §7.3/§7.5）：
 * 前者给人阅读，后者用于恢复，恢复只认完整备份里的结构化 JSON。
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val diaryRepository: DiaryRepository,
    private val settingsRepository: SettingsRepository
) {

    // ---------------- Markdown 导出 ----------------

    /**
     * 把全部现存日记导出为 Markdown，写到 [treeUri] 指向的文件夹。
     *
     * @param onProgress 已完成 / 总数；导出在 IO 线程分批回调，UI 层显示进度
     * @return 成功写出的文件数
     * @throws BackupException 写入失败时抛出（带原因），**保证不会留下"导出了一半却说成功"**
     */
    suspend fun exportMarkdown(
        treeUri: Uri,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        // 日期升序：同一天多篇时序号分配稳定（见 DiaryMarkdown.fileNameFor）
        val entries = diaryRepository.search(keyword = "", from = "", to = "", sort = "asc")
        if (entries.isEmpty()) return@withContext 0

        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            ?: throw BackupException("打不开所选文件夹，请重新选择")
        if (!root.canWrite()) throw BackupException("所选文件夹不可写入，请换一个位置")

        val sameDateCount = mutableMapOf<String, Int>()
        var done = 0
        try {
            entries.forEach { entry ->
                val index = sameDateCount.getOrDefault(entry.date, 0)
                sameDateCount[entry.date] = index + 1
                val name = DiaryMarkdown.fileNameFor(entry, index)

                // 重名覆盖规则：同一天第 1 篇用基础名，已存在的同名文件直接覆盖写入
                // （这是可预测的：同一天第 1 篇永远叫 YYYY-MM-DD.md）
                val target = root.findFile(name) ?: root.createFile("text/markdown", name)
                    ?: throw BackupException("无法在所选文件夹创建 $name")
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                    out.write(DiaryMarkdown.render(entry).toByteArray(Charsets.UTF_8))
                } ?: throw BackupException("无法写入 $name（目标可能被其他应用占用）")
                done++
                onProgress(done, entries.size)
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("导出中断：${e.message}", e)
        }
        done
    }

    // ---------------- 完整备份 ----------------

    /**
     * 打包全部数据写到用户选定的位置。
     *
     * 只在**成功写完并校验可读**之后才更新"上次备份时间"（规格 §7.3：
     * 打开页面、开始备份、备份失败都不能更新时间）。
     */
    suspend fun createBackup(targetUri: Uri) = withContext(Dispatchers.IO) {
        val bytes = packCurrentData()
        try {
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(bytes)
            } ?: throw BackupException("无法写入所选文件（目标可能被其他应用占用）")
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("备份写入失败：${e.message}", e)
        }

        // 写完回读一次，确认落盘的文件能被完整解析 —— 能通过恢复校验的备份才算备份成功
        runCatching { readBackup(targetUri) }.onFailure {
            throw BackupException("备份已写入但校验失败，请重新备份后再删除旧备份", it)
        }

        settingsRepository.setLastBackupAt(DateTimeUtil.nowMillis())
    }

    /** 当前全部数据打包成 ZIP 字节流。 */
    private suspend fun packCurrentData(): ByteArray {
        val dao = database.backupDao()
        val payload = BackupCodec.Payload(
            formatVersion = BackupCodec.FORMAT_VERSION,
            dbVersion = BackupCodec.DB_VERSION,
            exportedAt = DateTimeUtil.nowMillis(),
            diaryEntries = dao.diaryEntries(),
            diaryFields = dao.diaryFields(),
            templates = dao.templates(),
            templateFields = dao.templateFields(),
            transactions = dao.transactions(),
            loans = dao.loans(),
            categories = dao.categories(),
            accounts = dao.accounts(),
            inspirations = dao.inspirations(),
            inspirationPages = dao.inspirationPages(),
            aiNotes = dao.aiNotes()
        )
        return BackupCodec.pack(payload)
    }

    // ---------------- 恢复 ----------------

    /**
     * 读取并校验备份文件，返回解析出的内容（供 UI 显示摘要，确认后再执行 [restore]）。
     *
     * 只读不写 —— 真正替换数据要等用户在预览确认后发生。
     */
    suspend fun readBackup(sourceUri: Uri): BackupCodec.Payload = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: throw BackupException("无法读取所选文件")
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("读取备份文件失败：${e.message}", e)
        }
        BackupCodec.unpack(bytes)
    }

    /**
     * 执行恢复：在一个 Room 事务里清空 9 张表并按外键顺序写回备份数据。
     *
     * 事务的原子性就是回滚机制 —— 中途任何一步失败，整个事务回滚，
     * 数据库保持恢复前的状态，不存在"恢复了一半"的中间态。
     */
    suspend fun restore(payload: BackupCodec.Payload) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val dao = database.backupDao()
            // 清空：先子表后父表
            dao.clearDiaryFields()
            dao.clearDiaryEntries()
            dao.clearTemplateFields()
            dao.clearTemplates()
            dao.clearTransactions()
            dao.clearLoans()
            dao.clearInspirations()
            dao.clearInspirationPages()
            dao.clearAiNotes()
            dao.clearAccounts()
            dao.clearCategories()
            // 插入：先父表后子表
            dao.insertCategories(payload.categories)
            dao.insertAccounts(payload.accounts)
            dao.insertTemplates(payload.templates)
            dao.insertTemplateFields(payload.templateFields)
            dao.insertDiaryEntries(payload.diaryEntries)
            dao.insertDiaryFields(payload.diaryFields)
            dao.insertTransactions(payload.transactions)
            dao.insertLoans(payload.loans)
            dao.insertInspirations(payload.inspirations)
            dao.insertAiNotes(payload.aiNotes)
            // 旧备份没有灵感页这一块：补一个默认页把老灵感接住。
            // 不补的话恢复完一页都不存在，灵感列表会显示不出任何内容，
            // 看起来就像"恢复把灵感弄丢了"
            dao.insertInspirationPages(
                payload.inspirationPages.ifEmpty {
                    listOf(
                        InspirationPageEntity(
                            id = InspirationPageEntity.DEFAULT_ID,
                            name = InspirationPageEntity.DEFAULT_NAME,
                            sortOrder = 0,
                            createdAt = payload.exportedAt
                        )
                    )
                }
            )
        }
    }

    /** 备份文件的默认文件名：shiye-backup-YYYYMMDD-HHmm.zip */
    fun suggestedBackupName(): String {
        val dt = java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(DateTimeUtil.nowMillis()),
            java.time.ZoneId.systemDefault()
        )
        return "shiye-backup-%04d%02d%02d-%02d%02d.zip".format(
            dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute
        )
    }
}
