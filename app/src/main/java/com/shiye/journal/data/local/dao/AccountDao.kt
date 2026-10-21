package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shiye.journal.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, created_at ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE active = 1 ORDER BY sort_order ASC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun countAll(): Int

    /** 当前最大的排序位；还没有账户时返回 -1（用法同 CategoryDao） */
    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM accounts")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE accounts SET active = :active, updated_at = :now WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)
}
