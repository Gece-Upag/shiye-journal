package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shiye.journal.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY type ASC, sort_order ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** 只取启用的（未归档），记账时选择用 */
    @Query("SELECT * FROM categories WHERE active = 1 AND type = :type ORDER BY sort_order ASC")
    fun observeActiveByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countAll(): Int

    /**
     * 某个方向当前最大的排序位；一个都没有时返回 -1，
     * 调用方 +1 就能得到"排在最后"的序号，不用先判空。
     */
    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM categories WHERE type = :type")
    suspend fun maxSortOrder(type: String): Int

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    /**
     * 归档。保留行本身，历史流水仍指向它，统计照常能算。
     * 与"删除"的区别就在这里：删除会让历史流水失去分类归属。
     */
    @Query("UPDATE categories SET active = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    /** 只有自建分类能被真正删除；预置分类走归档，由调用方判断 */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)
}
