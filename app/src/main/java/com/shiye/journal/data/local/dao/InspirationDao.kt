package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shiye.journal.data.local.entity.InspirationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspirationDao {

    /** 全部灵感，新的在最上面 */
    @Query("SELECT * FROM inspirations ORDER BY created_at DESC")
    fun observeAll(): Flow<List<InspirationEntity>>

    /** 某一页的灵感，新的在最上面 */
    @Query("SELECT * FROM inspirations WHERE page_id = :pageId ORDER BY created_at DESC")
    fun observeByPage(pageId: String): Flow<List<InspirationEntity>>

    /** 整页清空（删页时用）；返回删掉的条数 */
    @Query("DELETE FROM inspirations WHERE page_id = :pageId")
    suspend fun deleteByPage(pageId: String): Int

    /** 批量删除（批量删除模式用） */
    @Query("DELETE FROM inspirations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int

    @Query("SELECT * FROM inspirations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): InspirationEntity?

    /**
     * 关键词 + 时间区间搜索，排序同样用 CASE 表达式避免拼 SQL。
     * [pageId] 为空表示跨页搜索全部。
     *
     * 返回 **Flow 而不是一次性列表**：Room 会在表变化时重新发射，
     * 于是"刚记下的一条马上就能搜到"。
     * 一次性查询做不到这件事 —— 它只在条件变化那一刻打一次数据库，
     * 之后库里再变，页面上的结果就是一份快照，要改个条件才肯更新。
     */
    @Query(
        """
        SELECT * FROM inspirations
        WHERE (:keyword = '' OR content LIKE '%' || :keyword || '%')
        AND (:pageId = '' OR page_id = :pageId)
        AND created_at >= :fromMillis AND created_at < :toMillis
        ORDER BY
            CASE WHEN :sort = 'asc' THEN created_at END ASC,
            CASE WHEN :sort = 'desc' THEN created_at END DESC
        """
    )
    fun observeSearch(
        keyword: String,
        fromMillis: Long,
        toMillis: Long,
        sort: String,
        pageId: String = ""
    ): Flow<List<InspirationEntity>>

    @Query("SELECT COUNT(*) FROM inspirations")
    suspend fun countAll(): Int

    @Upsert
    suspend fun upsert(entity: InspirationEntity)

    @Query("DELETE FROM inspirations WHERE id = :id")
    suspend fun deleteById(id: String)
}
