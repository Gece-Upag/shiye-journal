package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 一篇日记 + 它的全部字段。Room 用两条 SQL 组装，对调用方是一个整体。
 */
data class DiaryWithFields(
    @Embedded val entry: com.shiye.journal.data.local.entity.DiaryEntryEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "entry_id"
    )
    val fields: List<com.shiye.journal.data.local.entity.DiaryFieldEntity>
)

@Dao
interface DiaryDao {

    // ---------- 读取 ----------

    /** 全部日记，按日期倒序。列表页直接观察它。 */
    @Transaction
    @Query("SELECT * FROM diary_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<DiaryWithFields>>

    /** 观察某一篇（用于编辑页实时反映自动保存的结果） */
    @Transaction
    @Query("SELECT * FROM diary_entries WHERE id = :id")
    fun observeById(id: String): Flow<DiaryWithFields?>

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DiaryWithFields?

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DiaryWithFields?

    /**
     * 关键词 + 日期区间搜索。
     *
     * 用子查询先在 diary_fields 里匹配，再回到主表，这样一条日记只出现一次。
     *
     * 匹配范围按规格 §4.6：**字段内容 + 字段标签**。日期由独立的区间条件处理，
     * 不参与关键词匹配（把日期也塞进关键词会给"2026-10"这种输入带来误命中）。
     *
     * 大小写：SQLite 的 LIKE 对 ASCII 默认不区分大小写，中文没有大小写概念，
     * 因此直接 LIKE 即可满足"忽略英文字母大小写 + 中文直接包含"，不需要额外转小写。
     *
     * 排序走 CASE 表达式而不是拼接 SQL 字符串，避免注入且能复用同一条语句；
     * 末尾再按 id 兜底，保证规格要求的"排序规则必须稳定"。
     */
    @Transaction
    @Query(
        """
        SELECT * FROM diary_entries
        WHERE (:keyword = '' OR id IN (
            SELECT entry_id FROM diary_fields
            WHERE content LIKE '%' || :keyword || '%'
               OR label LIKE '%' || :keyword || '%'
        ))
        AND (:from = '' OR date >= :from)
        AND (:to = '' OR date <= :to)
        ORDER BY
            CASE WHEN :sort = 'asc' THEN date END ASC,
            CASE WHEN :sort = 'desc' THEN date END DESC,
            id ASC
        """
    )
    suspend fun search(keyword: String, from: String, to: String, sort: String): List<DiaryWithFields>

    /** 已写日记的总数；用于判断是不是全新安装（配合模板数一起判断） */
    @Query("SELECT COUNT(*) FROM diary_entries")
    suspend fun countAll(): Int

    // ---------- 写入 ----------

    @Upsert
    suspend fun upsertEntry(entry: com.shiye.journal.data.local.entity.DiaryEntryEntity)

    @Upsert
    suspend fun upsertFields(fields: List<com.shiye.journal.data.local.entity.DiaryFieldEntity>)

    /**
     * 只改一个字段的内容。自动保存走这条路，写入量最小。
     * updatedAt 由调用方传入，保证"最后一次编辑时间"跟着内容一起变。
     */
    @Query("UPDATE diary_fields SET content = :content WHERE id = :fieldId")
    suspend fun updateFieldContent(fieldId: String, content: String)

    /** 删除某篇的全部字段；切换模板时先清掉旧快照再写入新的 */
    @Query("DELETE FROM diary_fields WHERE entry_id = :entryId")
    suspend fun deleteFieldsByEntry(entryId: String)

    /** 删除日记本身；字段靠外键 onDelete=CASCADE 一起删掉 */
    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 写入 AI 建议。
     *
     * 只动这一列，正文一个字都不碰 —— AI 的输出绝不能悄悄改写用户写的东西。
     * 传空串就是清空（用户不要这条建议了）。
     */
    @Query("UPDATE diary_entries SET ai_summary = :text WHERE id = :entryId")
    suspend fun updateAiSummary(entryId: String, text: String)
}
