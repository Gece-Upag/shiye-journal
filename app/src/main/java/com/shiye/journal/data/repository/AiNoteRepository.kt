package com.shiye.journal.data.repository

import com.shiye.journal.data.local.AppDatabase
import com.shiye.journal.data.local.entity.AiNoteEntity
import com.shiye.journal.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow

/**
 * AI 存档仓库。
 *
 * 刻意保持很薄：这个栏目就是"把 AI 说的话留一份"，
 * 没有分类、没有标签、没有置顶 —— 那些都是让人停下来整理的东西，
 * 而整理 AI 输出并不比直接删掉更有价值。
 */
class AiNoteRepository(private val database: AppDatabase) {

    private val dao = database.aiNoteDao()

    /** 全部存档，新的在最上面 */
    fun observeAll(): Flow<List<AiNoteEntity>> = dao.observeAll()

    /** 保存一条 AI 结果，返回带 id 的对象 */
    suspend fun save(
        kind: String,
        content: String,
        sourceDate: String = "",
        sourceLabel: String = ""
    ): AiNoteEntity {
        val note = AiNoteEntity(
            id = java.util.UUID.randomUUID().toString(),
            kind = kind,
            content = content.trim(),
            sourceDate = sourceDate,
            sourceLabel = sourceLabel,
            createdAt = DateTimeUtil.nowMillis()
        )
        dao.upsert(note)
        return note
    }

    /** 永久删除，没有回收站 */
    suspend fun delete(id: String) = dao.deleteById(id)
}
