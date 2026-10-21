package com.shiye.journal.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * 一笔收支流水。
 *
 * 关键约定：
 * - [amount] 一律存**分**（整数）。金额不用浮点，避免 0.1+0.2 这类误差在反复统计后放大。
 * - 借入/借出**不进这张表**，它们在 [LoanEntity]：借款不是收入也不是支出，
 *   混进来会让"本月结余"和账户余额算错。
 * - [date] / [time] 是给人看和做日期分组用的，[dateTime] 是排序与范围筛选用的，
 *   两者冗余存是为了让查询能走索引，不用对字符串做函数运算。
 *
 * 列名约定：全库统一 snake_case，由 @ColumnInfo 显式指定，
 * 不依赖 Kotlin 属性名到列名的隐式映射，改属性名时不会悄悄改掉数据库结构。
 */
@Serializable
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["date_time"]),
        Index(value = ["date"]),
        Index(value = ["category_id"]),
        Index(value = ["account_id"])
    ]
)
data class TransactionEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    /** 收支方向，取值见 [Type] */
    val type: String,

    /** 金额，单位**分**，正整数 */
    val amount: Long,

    /** 所属分类 id */
    @ColumnInfo(name = "category_id") val categoryId: String,

    /**
     * 分类名称快照（规格 §5.3）。
     *
     * 记账瞬间把分类名抄一份存下来。以后分类改名甚至被删，这条流水显示的还是当时的名字 ——
     * 历史账目不能因为今天整理分类就跟昨天对不上。
     */
    @ColumnInfo(name = "category_name", defaultValue = "") val categoryName: String = "",

    /** 所属账户 id；空串表示未指定账户 */
    @ColumnInfo(name = "account_id") val accountId: String,

    /**
     * 账户名称快照（规格 §5.5「历史流水通过账户名称快照保留原有可读性」）。
     * 账户改名或归档之后，旧流水仍能读出当时挂在哪个账户下。未指定账户时为空串。
     */
    @ColumnInfo(name = "account_name", defaultValue = "") val accountName: String = "",

    /** 备注，可为空串 */
    val note: String,

    /** 交易日期，yyyy-MM-dd */
    val date: String,

    /** 交易时间，HH:mm */
    val time: String,

    /**
     * 日期 + 时间折算成的 epoch 毫秒（按本地时区当天 00:00 起算 + 时分）。
     * 仅用于排序与区间筛选，不做跨时区换算。
     */
    @ColumnInfo(name = "date_time") val dateTime: Long,

    @ColumnInfo(name = "created_at") val createdAt: Long,

    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    object Type {
        /** 支出 */
        const val EXPENSE = "expense"

        /** 收入 */
        const val INCOME = "income"
    }
}

/**
 * 一笔借入或借出。
 *
 * 与收支分开的理由（规格明确要求）：
 * - 借来的钱不是"收入"，还回去时也不是"支出"，计进收支会把结余算重；
 * - 借款有对方、有到期日、有结清状态，这些都是收支没有的语义。
 *
 * 结清只改 [status]，**不会**生成一条收支流水，也不会动账户余额。
 */
@Serializable
@Entity(
    tableName = "loans",
    indices = [
        Index(value = ["date_time"]),
        Index(value = ["status"]),
        Index(value = ["due_date"])
    ]
)
data class LoanEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    /** 方向，取值见 [Direction] */
    val direction: String,

    /** 对方称呼 */
    val person: String,

    /** 金额，单位**分**，正整数 */
    val amount: Long,

    /** 备注，可为空串 */
    val note: String,

    /** 发生日期，yyyy-MM-dd */
    val date: String,

    /** 排序与区间筛选用，epoch 毫秒 */
    @ColumnInfo(name = "date_time") val dateTime: Long,

    /** 约定还款日，yyyy-MM-dd；可为空表示没约时间 */
    @ColumnInfo(name = "due_date") val dueDate: String?,

    /** 状态，取值见 [Status] */
    val status: String,

    /** 结清时间，epoch 毫秒；未结清为 null */
    @ColumnInfo(name = "settled_at") val settledAt: Long?,

    @ColumnInfo(name = "created_at") val createdAt: Long,

    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    object Direction {
        /** 借入：别人借给我，我要还 */
        const val BORROW = "borrow"

        /** 借出：我借给别人，别人要还 */
        const val LEND = "lend"
    }

    object Status {
        /** 未结清 */
        const val PENDING = "pending"

        /** 已结清 */
        const val SETTLED = "settled"
    }
}

/**
 * 收支分类。
 *
 * [active] 为 false 表示已归档：归档后不再出现在"记一笔"的选择列表里，
 * 但历史流水仍指向它，所以统计能照常算 —— 这就是**归档不删除**的用意。
 */
@Serializable
@Entity(
    tableName = "categories",
    indices = [Index(value = ["type", "sort_order"])]
)
data class CategoryEntity(
    /** UUID；预置分类用固定 id（如 "c-food"），便于备份恢复时对齐 */
    @PrimaryKey
    val id: String,

    val name: String,

    /** 归属方向，取值见 [TransactionEntity.Type] */
    val type: String,

    /** 图标，直接存一个字符（沿用 UI 原型，如 "☕"） */
    val icon: String,

    @ColumnInfo(name = "sort_order") val sortOrder: Int,

    /** 是否启用（false = 已归档） */
    val active: Boolean,

    /** 是否系统预置；预置分类不允许删除，只能归档 */
    @ColumnInfo(name = "is_preset") val isPreset: Boolean,

    @ColumnInfo(name = "created_at") val createdAt: Long
)

/**
 * 账户。
 *
 * 余额**不存表**：余额 = [openingBalance] + 收入合计 − 支出合计，实时算。
 * 存余额就要在每笔增删改时同步维护，容易和流水对不上。
 *
 * 借款不参与账户：借入借出是独立表，不影响任何账户余额。
 */
@Serializable
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["sort_order"])]
)
data class AccountEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    val name: String,

    /** 期初余额，单位**分**，可为 0 */
    @ColumnInfo(name = "opening_balance") val openingBalance: Long,

    @ColumnInfo(name = "sort_order") val sortOrder: Int,

    /** 是否启用（false = 已归档），归档后不再出现在记账选择里 */
    val active: Boolean,

    @ColumnInfo(name = "created_at") val createdAt: Long,

    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
