package com.shiye.journal.data.repository

import androidx.room.withTransaction
import com.shiye.journal.data.SeedData
import com.shiye.journal.data.local.AppDatabase
import com.shiye.journal.data.local.entity.DiaryEntryEntity
import com.shiye.journal.data.model.DiaryEntry
import com.shiye.journal.data.model.DiaryStatus
import com.shiye.journal.data.model.toDomain
import com.shiye.journal.data.model.toDomainEntry
import com.shiye.journal.data.model.toEntity
import com.shiye.journal.domain.planTemplateSwitch
import com.shiye.journal.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 日记仓库。
 *
 * 同时协调两张表：diary_entries（主体）和 diary_fields（字段快照），
 * 外加读模板来创建新日记。UI 不直接碰 DAO。
 */
class DiaryRepository(private val database: AppDatabase) {

    private val diaryDao = database.diaryDao()
    private val templateDao = database.templateDao()

    /** 全部日记，日期倒序 */
    fun observeAll(): Flow<List<DiaryEntry>> =
        diaryDao.observeAll().map { list -> list.map { it.toDomainEntry() } }

    fun observeById(id: String): Flow<DiaryEntry?> =
        diaryDao.observeById(id).map { it?.toDomainEntry() }

    suspend fun getByDate(date: String): DiaryEntry? =
        diaryDao.getByDate(date)?.toDomainEntry()

    suspend fun getById(id: String): DiaryEntry? =
        diaryDao.getById(id)?.toDomainEntry()

    /**
     * 取某天的日记，没有就用默认模板新建一篇（草稿、字段全空）。
     *
     * 编辑页只需要调用这一个方法，不用先查再建，
     * 也避免两处并发创建同一天日记导致唯一约束冲突。
     */
    suspend fun getOrCreateByDate(date: String): DiaryEntry {
        diaryDao.getByDate(date)?.let { return it.toDomainEntry() }

        val template = templateDao.getDefault() ?: templateDao.getFirst()
        checkNotNull(template) { "没有任何模板，无法创建日记；请检查首次启动的预置数据是否写入成功" }

        val now = DateTimeUtil.nowMillis()
        val entryId = java.util.UUID.randomUUID().toString()
        val entry = DiaryEntryEntity(
            id = entryId,
            date = date,
            templateId = template.template.id,
            templateName = template.template.name,
            templateVersion = template.template.version,
            status = SeedData.newEntryStatus(),
            createdAt = now,
            updatedAt = now
        )
        val fields = SeedData.buildEmptyFields(entryId, template.template, template.fields)

        diaryDao.upsertEntry(entry)
        diaryDao.upsertFields(fields)

        return entry.toDomainEntry(fields.map { it.toDomain() })
    }

    /**
     * 写一个字段的内容，并同步刷新日记状态与更新时间。
     *
     * 状态规则：**全部必填字段都有内容 → complete，否则 draft**。
     * 放在仓库层而不是 UI 层，保证不管是自动保存还是手动保存都走同一套判定。
     */
    suspend fun saveFieldContent(entryId: String, fieldId: String, content: String) {
        diaryDao.updateFieldContent(fieldId, content)

        val entry = diaryDao.getById(entryId) ?: return
        val now = DateTimeUtil.nowMillis()
        val status = if (entry.fields.all { !it.required || it.content.isNotBlank() }) {
            DiaryEntryEntity.Status.COMPLETE
        } else {
            DiaryEntryEntity.Status.DRAFT
        }
        diaryDao.upsertEntry(entry.entry.copy(status = status, updatedAt = now))
    }

    /**
     * 写一批字段内容（自动保存走这条路），并刷新日记状态。
     *
     * 一次传整张"字段 id → 内容"表，而不是逐字逐字段地调用：
     * 自动保存本来就攒着一批改动，合并成一次写入更快，也避免中间态被别的查询看到。
     *
     * @param contents 字段 id → 最新内容。**只传当前存在的字段**，已删字段会被忽略。
     * @return 日记仍然存在并写入成功时返回 true；日记已被删除返回 false，
     *         调用方据此让保存队列失效（避免记录复活）。
     */
    suspend fun saveContents(entryId: String, contents: Map<String, String>): Boolean {
        val entry = diaryDao.getById(entryId) ?: return false
        // 只写有变化且确实属于这篇日记的字段：跳过没动的字段能省掉大量无意义的 UPDATE
        val existing = entry.fields.associateBy { it.id }
        contents.forEach { (fieldId, content) ->
            val before = existing[fieldId] ?: return@forEach
            if (before.content != content) diaryDao.updateFieldContent(fieldId, content)
        }
        refreshStatus(entryId)
        return true
    }

    /**
     * 重算完成状态：全部必填字段都有内容 → complete，否则 draft。
     *
     * 放在仓库层而不是 UI 层，保证自动保存、切换模板、标记完成走的是同一套判定。
     */
    private suspend fun refreshStatus(entryId: String) {
        val entry = diaryDao.getById(entryId) ?: return
        val status = if (entry.fields.all { !it.required || it.content.isNotBlank() }) {
            DiaryEntryEntity.Status.COMPLETE
        } else {
            DiaryEntryEntity.Status.DRAFT
        }
        // updatedAt 每次都刷新：状态没变不代表内容没变，
        // 列表页"最后编辑时间"依赖这个字段
        diaryDao.upsertEntry(
            entry.entry.copy(status = status, updatedAt = DateTimeUtil.nowMillis())
        )
    }

    /**
     * 用户主动标记完成 / 取消完成。
     *
     * 与自动保存分开：自动保存永远只存内容、不改状态，
     * 否则用户刚敲了两个字就被标成"已完成"，这个标记就没意义了。
     */
    suspend fun setStatus(entryId: String, status: DiaryStatus) {
        val entry = diaryDao.getById(entryId) ?: return
        diaryDao.upsertEntry(
            entry.entry.copy(
                status = if (status == DiaryStatus.COMPLETE) {
                    DiaryEntryEntity.Status.COMPLETE
                } else {
                    DiaryEntryEntity.Status.DRAFT
                },
                updatedAt = DateTimeUtil.nowMillis()
            )
        )
    }

    /**
     * 切换日记所用的模板。
     *
     * 合并规则在 [planTemplateSwitch]：按 fieldKey 匹配保留内容，
     * 无法对应且有内容的旧字段原样保留在日记里。
     *
     * 整体走一个事务：先删旧快照再写新快照，中途失败会回滚，
     * 不会出现"字段被清空但模板信息还没更新"的半截状态。
     *
     * @return 被保留下来的旧字段数量（>0 时 UI 必须提示用户）；日记或模板不存在返回 -1。
     */
    suspend fun switchTemplate(entryId: String, targetTemplateId: String): Int {
        val current = diaryDao.getById(entryId) ?: return -1
        val target = templateDao.getById(targetTemplateId) ?: return -1

        val plan = planTemplateSwitch(
            current = current.fields.sortedBy { it.sortOrder }.map { it.toDomain() },
            target = target.fields.sortedBy { it.sortOrder }.map { it.toDomain() },
            entryId = entryId
        )

        database.withTransaction {
            diaryDao.deleteFieldsByEntry(entryId)
            diaryDao.upsertFields(plan.fields.map { it.toEntity() })
            diaryDao.upsertEntry(
                current.entry.copy(
                    templateId = target.template.id,
                    templateName = target.template.name,
                    templateVersion = target.template.version,
                    updatedAt = DateTimeUtil.nowMillis()
                )
            )
        }
        // 状态在事务外面刷新：换模板后必填项可能变多或变少，要按新字段重算
        refreshStatus(entryId)
        return plan.retainedCount
    }

    /** 删除整篇日记，字段靠外键级联一起删掉 */
    suspend fun deleteById(id: String) = diaryDao.deleteById(id)

    /**
     * 保存 AI 给这篇日记的建议（V2）。传空串表示清空。
     *
     * 不动任何模板字段：AI 的话单独存一栏，正文保持原样。
     */
    suspend fun saveAiSummary(entryId: String, text: String) {
        diaryDao.updateAiSummary(entryId, text)
    }

    /**
     * 搜索。空关键词 + 空日期区间 = 返回全部。
     * sort 传 "asc" / "desc"。
     */
    suspend fun search(keyword: String, from: String, to: String, sort: String): List<DiaryEntry> =
        diaryDao.search(keyword.trim(), from.trim(), to.trim(), sort)
            .map { it.toDomainEntry() }

    suspend fun countAll(): Int = diaryDao.countAll()
}
