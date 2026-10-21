package com.shiye.journal.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 一个模板 + 它的字段定义，按 sort_order 排好序。 */
data class TemplateWithFields(
    @Embedded val template: com.shiye.journal.data.local.entity.TemplateEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "template_id"
    )
    val fields: List<com.shiye.journal.data.local.entity.TemplateFieldEntity>
)

@Dao
interface TemplateDao {

    @Transaction
    @Query("SELECT * FROM templates ORDER BY sort_order ASC, created_at ASC")
    fun observeAll(): Flow<List<TemplateWithFields>>

    /** 一次性取全部，不走 Flow；用于重名校验这类"查一下就完事"的场景 */
    @Transaction
    @Query("SELECT * FROM templates ORDER BY sort_order ASC, created_at ASC")
    suspend fun getAllOnce(): List<TemplateWithFields>

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TemplateWithFields?

    /** 默认模板。新建日记时用它；一个都没有时返回 null，由调用方兜底取第一个。 */
    @Transaction
    @Query("SELECT * FROM templates WHERE is_default = 1 LIMIT 1")
    suspend fun getDefault(): TemplateWithFields?

    @Transaction
    @Query("SELECT * FROM templates ORDER BY sort_order ASC LIMIT 1")
    suspend fun getFirst(): TemplateWithFields?

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun countAll(): Int

    @Upsert
    suspend fun upsertTemplate(template: com.shiye.journal.data.local.entity.TemplateEntity)

    @Upsert
    suspend fun upsertFields(fields: List<com.shiye.journal.data.local.entity.TemplateFieldEntity>)

    @Query("DELETE FROM template_fields WHERE template_id = :templateId")
    suspend fun deleteFieldsByTemplate(templateId: String)

    /** 删除模板；字段靠外键级联删除，已写日记的字段快照不受影响 */
    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 先把所有模板置为非默认，再单独设一个默认，避免出现两个默认模板 */
    @Query("UPDATE templates SET is_default = 0 WHERE is_default = 1")
    suspend fun clearDefaultFlag()

    @Query("UPDATE templates SET is_default = 1, updated_at = :now WHERE id = :id")
    suspend fun markAsDefault(id: String, now: Long)
}
