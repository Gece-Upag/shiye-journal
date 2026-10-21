package com.shiye.journal.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * 一篇日记。
 *
 * 设计要点（阶段 1 一次性定死，后续阶段只加功能、不再改表）：
 * - [date] 加唯一索引：同一天只允许一篇日记。这是规格里"按日期组织"的直接结果，
 *   也决定了列表页不需要去重合并。
 * - [templateName] / [templateVersion] 是**快照**：模板后续改名、加字段、删字段，
 *   都不会影响已经写好的日记。字段内容同理存在 [DiaryFieldEntity] 里做快照。
 * - 时间统一存 epoch 毫秒（Long），日期存 "yyyy-MM-dd" 字符串，
 *   避免时区带来的偏移问题，也让备份 JSON 可读。
 *
 * 列名约定：全库统一 snake_case，由 @ColumnInfo 显式指定，
 * 不依赖 Kotlin 属性名到列名的隐式映射，改属性名时不会悄悄改掉数据库结构。
 */
@Serializable
@Entity(
    tableName = "diary_entries",
    indices = [
        Index(value = ["date"], unique = true),
        Index(value = ["template_id"]),
        Index(value = ["updated_at"])
    ]
)
data class DiaryEntryEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    /** 日记所属日期，格式 yyyy-MM-dd，全表唯一 */
    val date: String,

    /** 创建时使用的模板 id（用于统计与"再写一篇同模板"），即使模板被删也保留 */
    @ColumnInfo(name = "template_id") val templateId: String,

    /** 模板名快照 */
    @ColumnInfo(name = "template_name") val templateName: String,

    /** 模板版本快照 */
    @ColumnInfo(name = "template_version") val templateVersion: Int,

    /** 完成状态，取值见 [Status] */
    val status: String,

    /** epoch 毫秒 */
    @ColumnInfo(name = "created_at") val createdAt: Long,

    /** epoch 毫秒 */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,

    /**
     * AI 给这篇日记的建议（V2）。
     *
     * 单独一列而不是塞进某个模板字段：模板字段是用户自己写的正文，
     * AI 说的话混进去过几个月就分不清哪句是自己写的了。
     * 它也不属于任何模板 —— 换模板不该把 AI 建议弄丢。
     */
    @ColumnInfo(name = "ai_summary", defaultValue = "")
    val aiSummary: String = ""
) {
    /** 日记状态。用 String 存库、用常量收敛取值，避免枚举改名导致历史数据读不出来。 */
    object Status {
        /** 草稿：有内容但未标记完成 */
        const val DRAFT = "draft"

        /** 已完成：必填字段均已填写 */
        const val COMPLETE = "complete"
    }
}

/**
 * 一篇日记里的一个字段（模板字段的**快照**）。
 *
 * 单独成表而不是塞 JSON 到 [DiaryEntryEntity]，是为了：
 * - 搜索可以按字段内容走 SQL LIKE，不用把整篇日记读进内存再匹配；
 * - 单字段内容可以单独 UPDATE，自动保存时写入量小；
 * - 模板字段改名/重排后，旧日记仍保持写入时的样子。
 */
@Serializable
@Entity(
    tableName = "diary_fields",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["entry_id"]),
        Index(value = ["entry_id", "sort_order"])
    ]
)
data class DiaryFieldEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    /** 所属日记 id，外键级联删除 */
    @ColumnInfo(name = "entry_id") val entryId: String,

    /** 模板字段的原始标识（如 "mood"），切换模板后仍可对齐同名旧字段 */
    @ColumnInfo(name = "field_key") val fieldKey: String,

    /** 字段标签快照（如 "心情"） */
    val label: String,

    /** 输入类型，取值见 [Type] */
    val type: String,

    /** 是否必填快照 */
    val required: Boolean,

    /** 字段在模板中的顺序快照 */
    @ColumnInfo(name = "sort_order") val sortOrder: Int,

    /** 用户填写的内容；未填为空串而非 null，省掉到处判空 */
    val content: String
) {
    object Type {
        /** 单行输入 */
        const val SINGLE = "single"

        /** 多行输入 */
        const val MULTI = "multi"
    }
}
