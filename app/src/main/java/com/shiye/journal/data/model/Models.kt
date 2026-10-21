package com.shiye.journal.data.model

import com.shiye.journal.data.local.dao.DiaryWithFields
import com.shiye.journal.data.local.dao.TemplateWithFields
import com.shiye.journal.data.local.entity.AccountEntity
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
 * 领域模型。
 *
 * 为什么不让 UI 直接用 Entity：Entity 里存的是 String（"expense"/"draft" 之类），
 * 交给 UI 就到处是魔法字符串，写错一个字母要到运行时才炸。
 * 这里统一转成枚举和不可变数据类，Repository 是唯一转换点。
 *
 * 转换函数统一写成 `toDomain()` / `toEntity()`，成对出现，便于核对。
 */

// ---------------- 枚举 ----------------

/** 应用主题模式。存 SharedPreferences，取值即存储值。 */
enum class ThemeMode(val storageKey: String) {
    /** 跟随系统 */
    SYSTEM("system"),

    /** 始终浅色 */
    LIGHT("light"),

    /** 始终深色 */
    DARK("dark");

    companion object {
        fun fromStorageKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}

/** 日记完成状态 */
enum class DiaryStatus {
    DRAFT,
    COMPLETE;

    companion object {
        fun fromStorage(value: String): DiaryStatus =
            if (value == DiaryEntryEntity.Status.COMPLETE) COMPLETE else DRAFT
    }
}

/** 模板字段的输入类型 */
enum class FieldType {
    SINGLE,
    MULTI;

    companion object {
        fun fromStorage(value: String): FieldType =
            if (value == DiaryFieldEntity.Type.MULTI) MULTI else SINGLE
    }
}

/** 收支方向 */
enum class TransactionType {
    EXPENSE,
    INCOME;

    companion object {
        fun fromStorage(value: String): TransactionType =
            if (value == TransactionEntity.Type.INCOME) INCOME else EXPENSE
    }
}

/** 借款方向 */
enum class LoanDirection {
    /** 借入：我欠别人 */
    BORROW,

    /** 借出：别人欠我 */
    LEND;

    companion object {
        fun fromStorage(value: String): LoanDirection =
            if (value == LoanEntity.Direction.LEND) LEND else BORROW
    }
}

enum class LoanStatus {
    PENDING,
    SETTLED;

    companion object {
        fun fromStorage(value: String): LoanStatus =
            if (value == LoanEntity.Status.SETTLED) SETTLED else PENDING
    }
}

// ---------------- 日记 ----------------

data class DiaryField(
    val id: String,
    val entryId: String,
    val fieldKey: String,
    val label: String,
    val type: FieldType,
    val required: Boolean,
    val sortOrder: Int,
    val content: String
)

data class DiaryEntry(
    val id: String,
    val date: String,
    val templateId: String,
    val templateName: String,
    val templateVersion: Int,
    val status: DiaryStatus,
    val fields: List<DiaryField>,
    val createdAt: Long,
    val updatedAt: Long,

    /** AI 给这篇日记的建议（V2）。单独存，不属于任何模板字段 */
    val aiSummary: String = ""
) {
    /**
     * 列表页摘要：取第一个有内容的字段，压平换行后截断到 [SUMMARY_MAX_LENGTH] 个字。
     * 全部为空时返回空串，UI 据此显示"这一天还没有记录"。
     */
    val summary: String
        get() {
            val first = fields.firstOrNull { it.content.isNotBlank() }?.content ?: return ""
            val flattened = first.replace('\n', ' ').replace('\r', ' ').trim()
            return if (flattened.length <= SUMMARY_MAX_LENGTH) {
                flattened
            } else {
                flattened.take(SUMMARY_MAX_LENGTH) + "…"
            }
        }

    /** 是否一个字都没写 */
    val isBlank: Boolean get() = fields.all { it.content.isBlank() }

    companion object {
        const val SUMMARY_MAX_LENGTH = 30
    }
}

// ---------------- 模板 ----------------

data class TemplateField(
    val id: String,
    val templateId: String,
    val fieldKey: String,
    val label: String,
    val type: FieldType,
    val required: Boolean,
    val sortOrder: Int
)

data class Template(
    val id: String,
    val name: String,
    val version: Int,
    val isDefault: Boolean,
    val sortOrder: Int,
    val fields: List<TemplateField>,
    val createdAt: Long,
    val updatedAt: Long
)

// ---------------- 记账 ----------------

data class Transaction(
    val id: String,
    val type: TransactionType,
    /** 单位：分 */
    val amount: Long,
    val categoryId: String,
    /** 分类名称快照：记账当时的分类名，分类改名不影响历史流水显示 */
    val categoryName: String = "",
    /** 账户 id；空串 = 未指定账户 */
    val accountId: String,
    /** 账户名称快照；未指定账户时为空串 */
    val accountName: String = "",
    val note: String,
    val date: String,
    val time: String,
    val dateTime: Long,
    val createdAt: Long,
    val updatedAt: Long
) {

    /**
     * 列表/详情显示的分类名。
     *
     * 优先用**快照**，快照为空（例如迁移前存的旧数据、或分类被删了）才退回到实时查名字；
     * 连实时也查不到就显示"未分类" —— 宁可给一个明确说法，也不要让这一行的数据看起来缺失。
     */
    fun categoryLabel(categories: Map<String, Category>): String =
        categoryName.ifBlank { categories[categoryId]?.name ?: "未分类" }

    /** 同 [categoryLabel]：优先快照，未指定账户时返回"未指定账户"。 */
    fun accountLabel(accounts: Map<String, Account>): String {
        if (accountId.isBlank() && accountName.isBlank()) return "未指定账户"
        return accountName.ifBlank { accounts[accountId]?.name ?: "未指定账户" }
    }
}

data class Loan(
    val id: String,
    val direction: LoanDirection,
    val person: String,
    /** 单位：分 */
    val amount: Long,
    val note: String,
    val date: String,
    val dateTime: Long,
    val dueDate: String?,
    val status: LoanStatus,
    val settledAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** 是否已逾期（有约定日期、未结清、且已过约定日） */
    fun isOverdue(today: String): Boolean =
        status == LoanStatus.PENDING && dueDate != null && dueDate < today
}

data class Category(
    val id: String,
    val name: String,
    val type: TransactionType,
    val icon: String,
    val sortOrder: Int,
    val active: Boolean,
    val isPreset: Boolean,
    val createdAt: Long
)

data class Account(
    val id: String,
    val name: String,
    /** 期初余额，单位：分 */
    val openingBalance: Long,
    val sortOrder: Int,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/** 账户余额 = 期初 + 收入 − 支出。借款不参与。 */
data class AccountBalance(
    val account: Account,
    /** 单位：分，可为负 */
    val balance: Long
)

/** 一段时间的收支概览 */
data class LedgerSummary(
    /** 收入合计，单位：分 */
    val income: Long,
    /** 支出合计，单位：分 */
    val expense: Long
) {
    /** 结余 = 收入 − 支出，单位：分，可为负 */
    val net: Long get() = income - expense
}

// ---------------- 灵感 ----------------

data class Inspiration(
    val id: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 所属灵感页 */
    val pageId: String = InspirationPageEntity.DEFAULT_ID
)

/** 一个灵感页。页之间内容完全隔离。 */
data class InspirationPage(
    val id: String,
    val name: String,
    val sortOrder: Long = 0,
    val createdAt: Long = 0
)

// ---------------- 映射函数 ----------------

fun DiaryFieldEntity.toDomain(): DiaryField = DiaryField(
    id = id,
    entryId = entryId,
    fieldKey = fieldKey,
    label = label,
    type = FieldType.fromStorage(type),
    required = required,
    sortOrder = sortOrder,
    content = content
)

fun DiaryField.toEntity(): DiaryFieldEntity = DiaryFieldEntity(
    id = id,
    entryId = entryId,
    fieldKey = fieldKey,
    label = label,
    type = if (type == FieldType.MULTI) DiaryFieldEntity.Type.MULTI else DiaryFieldEntity.Type.SINGLE,
    required = required,
    sortOrder = sortOrder,
    content = content
)

fun TemplateFieldEntity.toDomain(): TemplateField = TemplateField(
    id = id,
    templateId = templateId,
    fieldKey = fieldKey,
    label = label,
    type = FieldType.fromStorage(type),
    required = required,
    sortOrder = sortOrder
)

fun TemplateField.toEntity(): TemplateFieldEntity = TemplateFieldEntity(
    id = id,
    templateId = templateId,
    fieldKey = fieldKey,
    label = label,
    type = if (type == FieldType.MULTI) DiaryFieldEntity.Type.MULTI else DiaryFieldEntity.Type.SINGLE,
    required = required,
    sortOrder = sortOrder
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    type = TransactionType.fromStorage(type),
    amount = amount,
    categoryId = categoryId,
    categoryName = categoryName,
    accountId = accountId,
    accountName = accountName,
    note = note,
    date = date,
    time = time,
    dateTime = dateTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = if (type == TransactionType.INCOME) TransactionEntity.Type.INCOME else TransactionEntity.Type.EXPENSE,
    amount = amount,
    categoryId = categoryId,
    categoryName = categoryName,
    accountId = accountId,
    accountName = accountName,
    note = note,
    date = date,
    time = time,
    dateTime = dateTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun LoanEntity.toDomain(): Loan = Loan(
    id = id,
    direction = LoanDirection.fromStorage(direction),
    person = person,
    amount = amount,
    note = note,
    date = date,
    dateTime = dateTime,
    dueDate = dueDate,
    status = LoanStatus.fromStorage(status),
    settledAt = settledAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Loan.toEntity(): LoanEntity = LoanEntity(
    id = id,
    direction = if (direction == LoanDirection.LEND) LoanEntity.Direction.LEND else LoanEntity.Direction.BORROW,
    person = person,
    amount = amount,
    note = note,
    date = date,
    dateTime = dateTime,
    dueDate = dueDate,
    status = if (status == LoanStatus.SETTLED) LoanEntity.Status.SETTLED else LoanEntity.Status.PENDING,
    settledAt = settledAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = TransactionType.fromStorage(type),
    icon = icon,
    sortOrder = sortOrder,
    active = active,
    isPreset = isPreset,
    createdAt = createdAt
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = if (type == TransactionType.INCOME) TransactionEntity.Type.INCOME else TransactionEntity.Type.EXPENSE,
    icon = icon,
    sortOrder = sortOrder,
    active = active,
    isPreset = isPreset,
    createdAt = createdAt
)

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    openingBalance = openingBalance,
    sortOrder = sortOrder,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    openingBalance = openingBalance,
    sortOrder = sortOrder,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun InspirationEntity.toDomain(): Inspiration = Inspiration(
    id = id,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pageId = pageId
)

fun Inspiration.toEntity(): InspirationEntity = InspirationEntity(
    id = id,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pageId = pageId
)

fun InspirationPageEntity.toDomain(): InspirationPage = InspirationPage(
    id = id,
    name = name,
    sortOrder = sortOrder,
    createdAt = createdAt
)

fun InspirationPage.toEntity(): InspirationPageEntity = InspirationPageEntity(
    id = id,
    name = name,
    sortOrder = sortOrder,
    createdAt = createdAt
)

// ---------------- Room 关系对象的映射 ----------------

/**
 * [DiaryWithFields] → [DiaryEntry]。
 *
 * 字段在这里统一按 sort_order 排序：Room 的 @Relation 不保证子查询结果的顺序，
 * 依赖它会出现字段顺序随机跳动，所以排序放在映射这一层做，只做一次。
 */
fun DiaryWithFields.toDomainEntry(): DiaryEntry = entry.toDomainEntry(
    fields.sortedBy { it.sortOrder }.map { it.toDomain() }
)

fun DiaryEntryEntity.toDomainEntry(fields: List<DiaryField>): DiaryEntry = DiaryEntry(
    id = id,
    date = date,
    templateId = templateId,
    templateName = templateName,
    templateVersion = templateVersion,
    status = DiaryStatus.fromStorage(status),
    fields = fields,
    createdAt = createdAt,
    updatedAt = updatedAt,
    aiSummary = aiSummary
)

fun TemplateWithFields.toDomainTemplate(): Template = Template(
    id = template.id,
    name = template.name,
    version = template.version,
    isDefault = template.isDefault,
    sortOrder = template.sortOrder,
    fields = fields.sortedBy { it.sortOrder }.map { it.toDomain() },
    createdAt = template.createdAt,
    updatedAt = template.updatedAt
)
