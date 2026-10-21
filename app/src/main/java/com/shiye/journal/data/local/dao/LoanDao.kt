package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shiye.journal.data.local.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {

    @Query("SELECT * FROM loans ORDER BY date_time DESC")
    fun observeAll(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE status = :status ORDER BY date_time DESC")
    fun observeByStatus(status: String): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LoanEntity?

    /**
     * 未结清借款合计，按方向分别统计。
     * 借入与借出分开看：前者是我欠别人的，后者是别人欠我的，不能相互抵消。
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM loans
        WHERE direction = :direction AND status = 'pending'
        """
    )
    suspend fun sumPendingByDirection(direction: String): Long

    @Query("SELECT COUNT(*) FROM loans")
    suspend fun countAll(): Int

    @Upsert
    suspend fun upsert(loan: LoanEntity)

    /**
     * 结清。只改状态与时间，**不生成收支流水、不动账户余额**。
     * 结清前已存在的数据保持不变，所以结清后可以再改回去。
     */
    @Query("UPDATE loans SET status = 'settled', settled_at = :settledAt, updated_at = :settledAt WHERE id = :id")
    suspend fun markSettled(id: String, settledAt: Long)

    @Query("UPDATE loans SET status = 'pending', settled_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun markPending(id: String, now: Long)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteById(id: String)
}
