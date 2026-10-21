package com.shiye.journal.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * 一条灵感。
 *
 * 刻意保持极简：只有正文和时间。灵感的价值在于想到就立刻记下、之后能翻到，
 * 所以不需要分类、标签、置顶这些会让人停下来思考的字段。
 *
 * 列表默认按 created_at 倒序，新的在顶部。
 *
 * [pageId] 是后来加的"灵感页"归属（数据库 v3）：不同页之间彼此不通，
 * 删掉一个页会连页里的内容一起删。
 *
 * 列名约定：全库统一 snake_case，由 @ColumnInfo 显式指定。
 */
@Serializable
@Entity(
    tableName = "inspirations",
    indices = [Index(value = ["created_at"])]
)
data class InspirationEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    /** 灵感正文 */
    val content: String,

    /** 创建时间，epoch 毫秒 */
    @ColumnInfo(name = "created_at") val createdAt: Long,

    /** 最后编辑时间，epoch 毫秒 */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,

    /** 所属灵感页 */
    @ColumnInfo(name = "page_id") val pageId: String = InspirationPageEntity.DEFAULT_ID
)

/**
 * 一个灵感页。
 *
 * 灵感页是用户自己建的分组：可以给小说、工作、菜谱各开一页，互不干扰。
 * 页之间**不共享内容**，删页会连页里所有灵感一起删（所以要二次确认）。
 *
 * [DEFAULT_ID] 是固定常量而不是随机 UUID：数据库迁移要靠它把老灵感统一归到默认页，
 * 迁移 SQL 里没法用运行时生成的 id。
 */
@Serializable
@Entity(tableName = "inspiration_pages")
data class InspirationPageEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    /** 页名，用户自己起 */
    val name: String,

    /** 排序位，越小越靠前 */
    @ColumnInfo(name = "sort_order") val sortOrder: Long = 0,

    /** 创建时间，epoch 毫秒 */
    @ColumnInfo(name = "created_at") val createdAt: Long
) {
    companion object {
        /** 默认页 id。首次启动建它，老数据迁移时也归到它名下。 */
        const val DEFAULT_ID = "page-default"

        /** 默认页名 */
        const val DEFAULT_NAME = "随手记"
    }
}
