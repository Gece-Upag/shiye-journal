package com.shiye.journal.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 一条存下来的 AI 结果（规格 §8.4「存入独立 AI 栏目」/ §8.6）。
 *
 * 为什么要单独一张表而不是塞进日记正文：
 * AI 生成的东西**不是**用户写的，混在日记里久了就分不清哪句是自己的记忆、
 * 哪句是模型的发挥。单独存着，用户随时能翻、能删，也不会污染原文。
 *
 * 只存结果本身，不存请求原文 —— 原文就在日记里，存两份只会让两者慢慢不一致。
 */
@Serializable
@Entity(tableName = "ai_notes")
data class AiNoteEntity(
    /** UUID */
    @PrimaryKey
    val id: String,

    /** 来源类型：润色 / 总结 / 建议，取值见 [AiNoteEntity.Kind] */
    val kind: String,

    /** 结果正文 */
    val content: String,

    /** 来自哪篇日记（yyyy-MM-dd）；建议这类没有具体来源时为空串 */
    @ColumnInfo(name = "source_date") val sourceDate: String = "",

    /** 来源说明，例如「正文」或「最近七天」 */
    @ColumnInfo(name = "source_label") val sourceLabel: String = "",

    /** 创建时间，epoch 毫秒 */
    @ColumnInfo(name = "created_at") val createdAt: Long
) {
    /** 来源类型。存 String 常量，由 UI 层决定怎么显示。 */
    object Kind {
        const val POLISH = "polish"
        const val SUMMARY = "summary"
        const val ADVICE = "advice"

        fun labelOf(kind: String): String = when (kind) {
            POLISH -> "润色结果"
            SUMMARY -> "摘要"
            ADVICE -> "建议"
            else -> "AI 结果"
        }
    }
}
