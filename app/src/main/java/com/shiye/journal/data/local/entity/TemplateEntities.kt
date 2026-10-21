package com.shiye.journal.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * 日记模板。
 *
 * [version] 用于模板演进：每次改动模板结构（增删字段、改名）就 +1，
 * 已写好的日记保留写入时的版本号与字段快照，互不干扰。
 *
 * 列名约定：全库统一 snake_case，由 @ColumnInfo 显式指定。
 */
@Serializable
@Entity(
    tableName = "templates",
    indices = [Index(value = ["sort_order"])]
)
data class TemplateEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    /** 模板名，如「日常」「每日复盘」 */
    val name: String,

    /** 模板结构版本，从 1 开始 */
    val version: Int,

    /** 是否为新建日记时的默认模板；全表最多一个为 true，由 Repository 层保证 */
    @ColumnInfo(name = "is_default") val isDefault: Boolean,

    /** 模板列表中的排序 */
    @ColumnInfo(name = "sort_order") val sortOrder: Int,

    @ColumnInfo(name = "created_at") val createdAt: Long,

    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * 模板的一个字段定义。
 *
 * 删除模板时字段随之删除（级联），但**不会**级联删除已写日记里的字段快照 ——
 * 那部分存在 [DiaryFieldEntity] 里，属于日记自己的数据。
 */
@Serializable
@Entity(
    tableName = "template_fields",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["template_id"]),
        Index(value = ["template_id", "sort_order"])
    ]
)
data class TemplateFieldEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    /** 所属模板 id，外键级联删除 */
    @ColumnInfo(name = "template_id") val templateId: String,

    /**
     * 字段标识。同一模板内唯一，如「日常」模板里的 "mood" / "body" / "note"。
     * 切换模板时用它把同名字段的内容带过去。
     */
    @ColumnInfo(name = "field_key") val fieldKey: String,

    /** 展示给用户的标签 */
    val label: String,

    /** 输入类型，取值见 [DiaryFieldEntity.Type] */
    val type: String,

    /** 是否必填；必填项为空时日记状态为草稿 */
    val required: Boolean,

    /** 模板内的显示顺序 */
    @ColumnInfo(name = "sort_order") val sortOrder: Int
)
