package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shiye.journal.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /** 某段时间的全部流水，按时间倒序。记账首页与统计页共用。 */
    @Query(
        """
        SELECT * FROM transactions
        WHERE date_time >= :fromMillis AND date_time < :toMillis
        ORDER BY date_time DESC
        """
    )
    fun observeInRange(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>

    /** 最近若干笔，用于首页"最近记录" */
    @Query("SELECT * FROM transactions ORDER BY date_time DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    /**
     * 关键词 + 日期区间搜索（记账搜索页）。
     *
     * 关键词命中**备注、分类名、账户名**三处：一笔账事后记得住的通常不是"支出 38 元"，
     * 而是"吃饭那笔""用花呗付的"，只搜备注会漏。
     * 名称用的是流水上的**快照字段**（category_name / account_name），
     * 所以后来把「餐饮」改叫「吃喝」，旧账单按当时那个名字照样搜得到。
     *
     * 返回 Flow 而不是一次性列表：**库里一变结果就跟着变**。
     * 一次性查询会留下"刚记的一笔在搜索里搜不到"的窗口 —— 灵感的搜索就栽在这上面。
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:keyword = '' OR note LIKE '%' || :keyword || '%'
                OR category_name LIKE '%' || :keyword || '%'
                OR account_name LIKE '%' || :keyword || '%')
        AND (:fromDate = '' OR date >= :fromDate)
        AND (:toDate = '' OR date <= :toDate)
        ORDER BY
            CASE WHEN :sort = 'asc' THEN date_time END ASC,
            CASE WHEN :sort = 'desc' THEN date_time END DESC
        """
    )
    fun observeSearch(
        keyword: String,
        fromDate: String,
        toDate: String,
        sort: String
    ): Flow<List<TransactionEntity>>

    /**
     * 全部流水。
     *
     * 账户余额要跟着流水变，所以余额是"监听这一个 Flow + 监听账户表"算出来的（见 Repository）：
     * 如果只在账户表变化时重算，记一笔之后余额会停在上一个值。
     */
    @Query("SELECT * FROM transactions ORDER BY date_time DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** 某个分类下有多少笔流水 —— 决定这个分类能不能被真正删除（有流水就只能归档） */
    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId")
    suspend fun countByCategory(categoryId: String): Int

    /** 某个账户下有多少笔流水 —— 同删除保护 */
    @Query("SELECT COUNT(*) FROM transactions WHERE account_id = :accountId")
    suspend fun countByAccount(accountId: String): Int

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransactionEntity?

    /**
     * 某段时间内某个方向的金额合计，单位**分**。
     * COALESCE 保证一笔都没有时返回 0 而不是 null，调用方不用判空。
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE date >= :fromDate AND date <= :toDate AND type = :type
        """
    )
    suspend fun sumByTypeInDateRange(fromDate: String, toDate: String, type: String): Long

    /** 按分类汇总，统计页的分类占比用；只统计非零分类 */
    @Query(
        """
        SELECT category_id AS categoryId, COALESCE(SUM(amount), 0) AS total
        FROM transactions
        WHERE date >= :fromDate AND date <= :toDate AND type = :type
        GROUP BY category_id
        """
    )
    suspend fun sumByCategoryInDateRange(
        fromDate: String,
        toDate: String,
        type: String
    ): List<CategoryTotal>

    /** 某账户的收支合计，用于算账户余额（收入 − 支出由调用方相减） */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE account_id = :accountId AND type = :type
        """
    )
    suspend fun sumByAccountAndType(accountId: String, type: String): Long

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAll(): Int

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 批量删除（批量删除模式用） */
    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

/** 分类汇总结果。Room 要求列名与字段名对齐，所以用 AS 重命名。 */
data class CategoryTotal(
    val categoryId: String,
    val total: Long
)
