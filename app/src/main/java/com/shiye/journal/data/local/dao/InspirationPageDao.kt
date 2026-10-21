package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shiye.journal.data.local.entity.InspirationPageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 灵感页。
 *
 * 注意**没有外键级联**：删页时页里的灵感由 [com.shiye.journal.data.repository.InspirationRepository]
 * 在同一个事务里手动清掉。不靠外键是因为 Room 的级联删发生在 DELETE 之后，
 * 想把"删页 + 删内容"做成一步、失败能整体回滚，自己写事务更可控。
 */
@Dao
interface InspirationPageDao {

    /** 全部页，按排序位再按创建时间 */
    @Query("SELECT * FROM inspiration_pages ORDER BY sort_order ASC, created_at ASC")
    fun observeAll(): Flow<List<InspirationPageEntity>>

    @Query("SELECT * FROM inspiration_pages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): InspirationPageEntity?

    @Query("SELECT COUNT(*) FROM inspiration_pages")
    suspend fun countAll(): Int

    @Upsert
    suspend fun upsert(entity: InspirationPageEntity)

    /** 重命名：只改名字，其余不动 */
    @Query("UPDATE inspiration_pages SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM inspiration_pages WHERE id = :id")
    suspend fun deleteById(id: String)
}
