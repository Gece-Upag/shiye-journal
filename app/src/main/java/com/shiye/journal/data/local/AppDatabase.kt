package com.shiye.journal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shiye.journal.data.local.dao.AccountDao
import com.shiye.journal.data.local.dao.AiNoteDao
import com.shiye.journal.data.local.dao.BackupDao
import com.shiye.journal.data.local.dao.CategoryDao
import com.shiye.journal.data.local.dao.DiaryDao
import com.shiye.journal.data.local.dao.InspirationDao
import com.shiye.journal.data.local.dao.InspirationPageDao
import com.shiye.journal.data.local.dao.LoanDao
import com.shiye.journal.data.local.dao.TemplateDao
import com.shiye.journal.data.local.dao.TransactionDao
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
 * 全库唯一入口。
 *
 * ## 为什么到了 version 2
 *
 * 阶段 1 把 9 张表一次性建齐，本来打算 V1 全程不改结构。
 * 但阶段 3 实现记账时发现漏了两个规格明确要求的字段：
 * 流水要存**分类名称快照**（§5.3）和**账户名称快照**（§5.5），
 * 否则把分类改个名，历史流水显示的名字就跟着变了 —— 这是对不上的账，必须补。
 *
 * 于是有了 1 → 2 这次迁移。**加列 + 回填**，不做任何删除或重建，
 * 老数据一行不丢（见 [MIGRATION_1_2]）。
 *
 * 之后再改结构：
 * 1. version +1；
 * 2. 写 Migration，并补上 app/schemas 下新版本的 json；
 * 3. 走 Room 的迁移测试，不要靠 fallbackToDestructiveMigration 蒙混过去
 *    （那会静默清空用户数据，与"本地优先、数据是你的"这条底线冲突）。
 */
@Database(
    entities = [
        DiaryEntryEntity::class,
        DiaryFieldEntity::class,
        TemplateEntity::class,
        TemplateFieldEntity::class,
        TransactionEntity::class,
        LoanEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        InspirationEntity::class,
        InspirationPageEntity::class,
        AiNoteEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao

    abstract fun templateDao(): TemplateDao

    abstract fun transactionDao(): TransactionDao

    abstract fun loanDao(): LoanDao

    abstract fun categoryDao(): CategoryDao

    abstract fun accountDao(): AccountDao

    abstract fun inspirationDao(): InspirationDao

    abstract fun inspirationPageDao(): InspirationPageDao

    abstract fun aiNoteDao(): AiNoteDao

    /** 备份/恢复专用的全表读写，只在设置页的备份链路里用 */
    abstract fun backupDao(): BackupDao

    companion object {
        /** 数据库文件名。备份恢复时读的就是它（Room 的 -wal / -shm 一并处理）。 */
        const val DATABASE_NAME = "shiye.db"
    }
}

/**
 * 1 → 2：给 transactions 加分类名与账户名两列快照，并把已有流水回填。
 *
 * 三件事，顺序不能乱：
 * 1. `ALTER TABLE ... ADD COLUMN` 加两列（NOT NULL 必须带默认值，否则 SQLite 拒绝）；
 * 2. 用子查询把现存流水的名字回填 —— **这一步不能省**，
 *    否则升级后老流水会显示成"未分类"，用户以为数据丢了；
 * 3. 只动这两列，别的什么都不碰。
 */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE transactions ADD COLUMN category_name TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "ALTER TABLE transactions ADD COLUMN account_name TEXT NOT NULL DEFAULT ''"
        )
        // 回填现存流水：按当前分类表/账户表把名字补进去，对象已经不存在的留空串
        db.execSQL(
            """
            UPDATE transactions SET category_name =
                COALESCE((SELECT name FROM categories WHERE categories.id = transactions.category_id), '')
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE transactions SET account_name =
                COALESCE((SELECT name FROM accounts WHERE accounts.id = transactions.account_id), '')
            """.trimIndent()
        )
    }
}

/**
 * 2 → 3：把灵感拆成"多个灵感页"，页之间内容互不相通。
 *
 * 三件事：
 * 1. 新建 `inspiration_pages` 表；
 * 2. `inspirations` 加 `page_id` 列，**NOT NULL 必须带默认值**，否则 SQLite 拒绝；
 * 3. 建一个默认页，并把默认页的 id 作为已有灵感的归属 —— 默认值直接写死成
 *    [InspirationPageEntity.DEFAULT_ID]，老灵感一行不丢，全都落在「随手记」里。
 *
 * 顺序不能乱：先建页再改灵感表，否则页 id 无处可指。
 */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inspiration_pages` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sort_order` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "ALTER TABLE inspirations ADD COLUMN page_id TEXT NOT NULL DEFAULT '${InspirationPageEntity.DEFAULT_ID}'"
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO inspiration_pages (id, name, sort_order, created_at)
            VALUES ('${InspirationPageEntity.DEFAULT_ID}', '${InspirationPageEntity.DEFAULT_NAME}', 0, ${System.currentTimeMillis()})
            """.trimIndent()
        )
    }
}

/**
 * 3 → 4：加 `ai_notes` 表，存放用户主动保留下来的 AI 结果（规格 §8.4 / §8.6）。
 *
 * 纯建表，不碰任何已有数据：这是新功能，没有历史数据需要迁移。
 * 用 `CREATE TABLE IF NOT EXISTS` 是为了幂等 ——
 * 万一迁移被重试（例如上次执行到一半进程被杀），不会因"表已存在"而失败。
 */
/**
 * 4 → 5：diary_entries 加 `ai_summary` 列，放 AI 给这篇日记的建议。
 *
 * 用 ALTER TABLE 只加一列，不重建表、不动已有数据：
 * 这是给已有日记开一个新栏位，那些日记本来就没有 AI 建议，默认值空串即可。
 */
val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `diary_entries` ADD COLUMN `ai_summary` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * 3 → 4：加 `ai_notes` 表，存放用户主动保留下来的 AI 结果（规格 §8.4 / §8.6）。
 *
 * 纯建表，不碰任何已有数据：这是新功能，没有历史数据需要迁移。
 * 用 `CREATE TABLE IF NOT EXISTS` 是为了幂等 ——
 * 万一迁移被重试（例如上次执行到一半进程被杀），不会因"表已存在"而失败。
 */
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ai_notes` (
                `id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `source_date` TEXT NOT NULL,
                `source_label` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}
