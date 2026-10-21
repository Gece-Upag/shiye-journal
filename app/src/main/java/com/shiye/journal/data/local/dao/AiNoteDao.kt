package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shiye.journal.data.local.entity.AiNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * AI 存档。
 *
 * 这里**故意没有"按日记查"**：AI 栏目是一个独立的地方，
 * 翻它的方式是"最近存了什么"，而不是"这篇日记当时生成了啥"。
 * 后者会让 AI 结果再一次和日记绑在一起，失去了单独存放的意义。
 */
@Dao
interface AiNoteDao {

    /** 全部存档，新的在最上面 */
    @Query("SELECT * FROM ai_notes ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AiNoteEntity>>

    @Query("SELECT * FROM ai_notes")
    suspend fun all(): List<AiNoteEntity>

    @Upsert
    suspend fun upsert(entity: AiNoteEntity)

    @Query("DELETE FROM ai_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
