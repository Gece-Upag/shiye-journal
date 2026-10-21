package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shiye.journal.data.local.entity.AccountEntity
import com.shiye.journal.data.local.entity.AiNoteEntity
import com.shiye.journal.data.local.entity.CategoryEntity
import com.shiye.journal.data.local.entity.DiaryEntryEntity
import com.shiye.journal.data.local.entity.DiaryFieldEntity
import com.shiye.journal.data.local.entity.InspirationEntity
import com.shiye.journal.data.local.entity.InspirationPageEntity
import com.shiye.journal.data.local.entity.LoanEntity
import com.shiye.journal.data.local.entity.TemplateEntity
import com.shiye.journal.data.local.entity.TemplateFieldEntity
import com.shiye.journal.data.local.entity.TransactionEntity

/**
 * 备份与恢复专用的全表读写。
 *
 * 为什么不塞进各个业务 DAO：备份要的是"整表快照 + 整表替换"，
 * 与业务 DAO 的"按条件读写"关注点完全不同；集中在一起也方便对照
 * 9 张表是否都覆盖了，避免出现"备份漏了一张表"这种灾难。
 *
 * ## 恢复的顺序（BackupManager 的 restore 事务里执行）
 *
 * - 清空：**先子表后父表**（diary_fields → diary_entries），否则外键约束报错；
 * - 插入：**先父表后子表**（categories/accounts/templates → 流水/字段/灵感）。
 *
 * 全程在一个 Room 事务里，任何一步失败自动整体回滚 —— 恢复失败不会留下半库数据。
 */
@Dao
interface BackupDao {

    // ---------- 读取（备份用）：直接读表，保留 id，恢复时原样写回 ----------

    @Query("SELECT * FROM diary_entries")
    suspend fun diaryEntries(): List<DiaryEntryEntity>

    @Query("SELECT * FROM diary_fields")
    suspend fun diaryFields(): List<DiaryFieldEntity>

    @Query("SELECT * FROM templates")
    suspend fun templates(): List<TemplateEntity>

    @Query("SELECT * FROM template_fields")
    suspend fun templateFields(): List<TemplateFieldEntity>

    @Query("SELECT * FROM transactions")
    suspend fun transactions(): List<TransactionEntity>

    @Query("SELECT * FROM loans")
    suspend fun loans(): List<LoanEntity>

    @Query("SELECT * FROM categories")
    suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM accounts")
    suspend fun accounts(): List<AccountEntity>

    @Query("SELECT * FROM inspirations")
    suspend fun inspirations(): List<InspirationEntity>

    @Query("SELECT * FROM inspiration_pages")
    suspend fun inspirationPages(): List<InspirationPageEntity>

    @Query("SELECT * FROM ai_notes")
    suspend fun aiNotes(): List<AiNoteEntity>

    // ---------- 清空（恢复用）：先子后父 ----------

    @Query("DELETE FROM diary_fields")
    suspend fun clearDiaryFields()

    @Query("DELETE FROM diary_entries")
    suspend fun clearDiaryEntries()

    @Query("DELETE FROM template_fields")
    suspend fun clearTemplateFields()

    @Query("DELETE FROM templates")
    suspend fun clearTemplates()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM loans")
    suspend fun clearLoans()

    @Query("DELETE FROM inspirations")
    suspend fun clearInspirations()

    @Query("DELETE FROM inspiration_pages")
    suspend fun clearInspirationPages()

    @Query("DELETE FROM ai_notes")
    suspend fun clearAiNotes()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    // ---------- 插入（恢复用）：先父后子；REPLACE 处理 id 冲突 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(items: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(items: List<TemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateFields(items: List<TemplateFieldEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaryEntries(items: List<DiaryEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaryFields(items: List<DiaryFieldEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoans(items: List<LoanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspirations(items: List<InspirationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspirationPages(items: List<InspirationPageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiNotes(items: List<AiNoteEntity>)
}
