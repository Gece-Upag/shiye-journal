package com.shiye.journal.data.repository

import com.shiye.journal.data.SeedData
import com.shiye.journal.data.local.AppDatabase
import com.shiye.journal.data.local.entity.LoanEntity
import com.shiye.journal.data.local.entity.TransactionEntity
import com.shiye.journal.data.model.Account
import com.shiye.journal.data.model.AccountBalance
import com.shiye.journal.data.model.Category
import com.shiye.journal.data.model.LedgerSummary
import com.shiye.journal.data.model.Loan
import com.shiye.journal.data.model.LoanDirection
import com.shiye.journal.data.model.Transaction
import com.shiye.journal.data.model.TransactionType
import com.shiye.journal.data.model.toDomain
import com.shiye.journal.data.model.toEntity
import com.shiye.journal.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 记账仓库。
 *
 * 覆盖四张表：transactions（收支）、loans（借入借出）、categories（分类）、accounts（账户）。
 *
 * 两条容易踩的规矩，写在这里统一守住：
 * 1. **借款不是收支**：借入借出只进 loans 表，绝不计入收支合计，也不动账户余额。
 * 2. **余额不落库**：账户余额 = 期初 + 收入 − 支出，每次实时算，
 *    这样流水和余额不可能对不上。
 */
class LedgerRepository(private val database: AppDatabase) {

    private val transactionDao = database.transactionDao()
    private val loanDao = database.loanDao()
    private val categoryDao = database.categoryDao()
    private val accountDao = database.accountDao()

    // ---------------- 流水 ----------------

    /** 某个日期区间内的流水（含首尾两天），按时间倒序 */
    fun observeTransactionsInRange(fromDate: String, toDate: String): Flow<List<Transaction>> {
        val from = DateTimeUtil.parseDate(fromDate) ?: DateTimeUtil.today()
        val to = DateTimeUtil.parseDate(toDate) ?: DateTimeUtil.today()
        return transactionDao
            .observeInRange(
                fromMillis = DateTimeUtil.startOfDayMillis(from),
                toMillis = DateTimeUtil.startOfNextDayMillis(to)
            )
            .map { list -> list.map { it.toDomain() } }
    }

    fun observeRecentTransactions(limit: Int = 20): Flow<List<Transaction>> =
        transactionDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    /**
     * 搜索流水（记账搜索页）。
     *
     * 返回 Flow：结果跟着数据库走，记一笔 / 删一笔之后搜索页不用重进也会更新。
     * 日期用**字符串比较**（date 列是 yyyy-MM-dd，字典序即时间序），
     * 与 [observeTransactionsInRange] 那边的毫秒区间是两套口径，别混用。
     */
    fun observeSearch(
        keyword: String,
        fromDate: String,
        toDate: String,
        sort: String
    ): Flow<List<Transaction>> = transactionDao
        .observeSearch(keyword.trim(), fromDate.trim(), toDate.trim(), sort)
        .map { list -> list.map { it.toDomain() } }

    suspend fun getTransaction(id: String): Transaction? = transactionDao.getById(id)?.toDomain()

    suspend fun saveTransaction(transaction: Transaction) {
        val now = DateTimeUtil.nowMillis()
        transactionDao.upsert(
            transaction.copy(
                updatedAt = now,
                createdAt = transaction.createdAt.takeIf { it > 0 } ?: now
            ).toEntity()
        )
    }

    suspend fun deleteTransaction(id: String) = transactionDao.deleteById(id)

    /** 批量删除流水。单个事务里完成，中途失败一行都不会少 */
    suspend fun deleteTransactions(ids: Collection<String>) {
        if (ids.isEmpty()) return
        transactionDao.deleteByIds(ids.toList())
    }

    /** 一段时间的收入/支出合计。结余在 [LedgerSummary.net] 里。 */
    suspend fun summary(fromDate: String, toDate: String): LedgerSummary = LedgerSummary(
        income = transactionDao.sumByTypeInDateRange(fromDate, toDate, TransactionEntity.Type.INCOME),
        expense = transactionDao.sumByTypeInDateRange(fromDate, toDate, TransactionEntity.Type.EXPENSE)
    )

    // ---------------- 分类 ----------------

    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveCategories(type: TransactionType): Flow<List<Category>> =
        categoryDao.observeActiveByType(
            if (type == TransactionType.INCOME) TransactionEntity.Type.INCOME else TransactionEntity.Type.EXPENSE
        ).map { list -> list.map { it.toDomain() } }

    suspend fun getCategory(id: String): Category? = categoryDao.getById(id)?.toDomain()

    suspend fun saveCategory(category: Category) = categoryDao.upsert(category.toEntity())

    /** 归档而非删除：历史流水仍指向它，统计照常能算 */
    suspend fun setCategoryActive(id: String, active: Boolean) = categoryDao.setActive(id, active)

    /** 下一个可用排序位（排在同类型最后） */
    suspend fun nextCategorySortOrder(type: TransactionType): Int =
        categoryDao.maxSortOrder(entityTypeOf(type)) + 1

    /** 这个分类下有没有流水。有就只能归档，不能删除（否则历史流水失去归属） */
    suspend fun countTransactionsOfCategory(id: String): Int = transactionDao.countByCategory(id)

    /** 只有自建分类能真正删除，且必须没有流水引用它 */
    suspend fun deleteCategory(id: String) = categoryDao.deleteById(id)

    // ---------------- 账户 ----------------

    fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveAccounts(): Flow<List<Account>> =
        accountDao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun getAccount(id: String): Account? = accountDao.getById(id)?.toDomain()

    suspend fun saveAccount(account: Account) {
        val now = DateTimeUtil.nowMillis()
        accountDao.upsert(
            account.copy(
                updatedAt = now,
                createdAt = account.createdAt.takeIf { it > 0 } ?: now
            ).toEntity()
        )
    }

    suspend fun setAccountActive(id: String, active: Boolean) =
        accountDao.setActive(id, active, DateTimeUtil.nowMillis())

    /** 下一个可用排序位 */
    suspend fun nextAccountSortOrder(): Int = accountDao.maxSortOrder() + 1

    /** 这个账户下有没有流水；有流水就不能删除（规格 §5.5：优先归档） */
    suspend fun countTransactionsOfAccount(id: String): Int = transactionDao.countByAccount(id)

    suspend fun deleteAccount(id: String) = accountDao.deleteById(id)

    /**
     * 全部账户（含已归档）的余额。借款不参与计算。
     *
     * 这里**同时监听账户表与流水表**：只监听账户表的话，记一笔之后余额不会变，
     * 看上去就像"余额不准"。两个数据流 combine 之后任一侧变化都会重算，
     * 代价是每次流水变动都重算一遍余额 —— 个人账本的量级（几千笔）完全可以接受，
     * 换来的是"余额一定等于流水减出来的数"。
     */
    fun observeAccountBalances(): Flow<List<AccountBalance>> =
        combine(accountDao.observeAll(), transactionDao.observeAll()) { accounts, transactions ->
            // 一次遍历把每个账户的收支分别累计，避免对每个账户各查一次库
            val incomeByAccount = LongArray(accounts.size)
            val expenseByAccount = LongArray(accounts.size)
            val indexById = accounts.withIndex().associate { it.value.id to it.index }

            transactions.forEach { transaction ->
                val index = indexById[transaction.accountId] ?: return@forEach
                when (transaction.type) {
                    TransactionEntity.Type.INCOME -> incomeByAccount[index] += transaction.amount
                    TransactionEntity.Type.EXPENSE -> expenseByAccount[index] += transaction.amount
                }
            }

            accounts.mapIndexed { index, entity ->
                AccountBalance(
                    account = entity.toDomain(),
                    balance = entity.openingBalance + incomeByAccount[index] - expenseByAccount[index]
                )
            }
        }

    // ---------------- 借入借出 ----------------

    fun observeLoans(): Flow<List<Loan>> =
        loanDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun saveLoan(loan: Loan) {
        val now = DateTimeUtil.nowMillis()
        loanDao.upsert(
            loan.copy(
                updatedAt = now,
                createdAt = loan.createdAt.takeIf { it > 0 } ?: now
            ).toEntity()
        )
    }

    /** 结清：只改状态，不生成流水、不动余额 */
    suspend fun settleLoan(id: String) = loanDao.markSettled(id, DateTimeUtil.nowMillis())

    suspend fun reopenLoan(id: String) = loanDao.markPending(id, DateTimeUtil.nowMillis())

    suspend fun deleteLoan(id: String) = loanDao.deleteById(id)

    /** 待收待还：返回 (待还借入, 待收借出)，单位分 */
    suspend fun pendingLoanSums(): Pair<Long, Long> = Pair(
        loanDao.sumPendingByDirection(LoanEntity.Direction.BORROW),
        loanDao.sumPendingByDirection(LoanEntity.Direction.LEND)
    )

    suspend fun getLoan(id: String): Loan? = loanDao.getById(id)?.toDomain()

    // ---------------- 预置数据 ----------------

    /** 首次启动写入预置分类；账户不预置，由用户自己建。 */
    suspend fun ensureSeeded() {
        if (categoryDao.countAll() == 0) {
            categoryDao.upsertAll(SeedData.categories(DateTimeUtil.nowMillis()))
        }
    }

    private fun entityTypeOf(type: TransactionType): String =
        if (type == TransactionType.INCOME) TransactionEntity.Type.INCOME else TransactionEntity.Type.EXPENSE
}
