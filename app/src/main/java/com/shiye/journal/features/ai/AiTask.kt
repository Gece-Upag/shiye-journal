package com.shiye.journal.features.ai

/**
 * 一次 AI 任务的状态（规格 §8.6 的结果页 / §8.2 的"发送前告知"）。
 *
 * 用**一个**数据类而不是若干布尔量：润色和总结共用这套流程，
 * 状态之间是互斥的（不可能同时在确认又在跑），用枚举阶段能避免
 * "running=true 且 result!=null"这种自相矛盾的组合。
 */
data class AiTask(
    val kind: AiTaskKind,

    /** 润色针对的字段 id；总结针对整篇，为 null */
    val fieldId: String? = null,

    /** 发起请求那一刻的原文。**采纳与否都靠它判断**，也是回滚的依据 */
    val snapshot: String = "",

    val stage: AiStage = AiStage.CONFIRM_SEND,

    /** 模型返回的内容（stage=RESULT 时有值） */
    val result: String? = null,

    /** 失败原因（stage=FAILED 时有值） */
    val error: String? = null,

    /** 这次要发哪几个字段（只有"整篇"那类任务用得上；润色只针对单个字段） */
    val pickedFieldIds: Set<String> = emptySet()
) {
    /** 这次任务要发给模型的内容，用于"发送前告知"里展示 */
    val payloadPreview: String get() = snapshot

    /** 任务标题，弹窗用 */
    val title: String
        get() = when (kind) {
            AiTaskKind.POLISH -> "AI 润色"
            AiTaskKind.SUMMARY -> "AI 总结"
            AiTaskKind.ADVICE -> "AI 建议"
        }
}

/** 选字段弹窗里的一个候选：哪个字段、写了什么 */
data class AiFieldOption(
    val id: String,
    val label: String,
    val content: String
) {
    val isEmpty: Boolean get() = content.isBlank()
}

enum class AiTaskKind {
    /** 润色某个字段：只改正错别字和不通顺 */
    POLISH,

    /** 给整篇生成摘要 */
    SUMMARY,

    /** 最近七天的建议 */
    ADVICE
}

enum class AiStage {
    /**
     * 选内容：这篇日记有多个字段，先让用户挑**发哪几个**。
     *
     * 整篇一股脑发出去既浪费（空字段、不想给模型看的内容），
     * 也不尊重隐私 —— 有些字段用户就是不想让模型看到。
     */
    PICK_FIELDS,

    /** 还没发出去：先告诉用户要发什么、发到哪 */
    CONFIRM_SEND,

    /** 请求中 */
    RUNNING,

    /** 拿到结果，等用户决定采纳/复制/丢弃 */
    RESULT,

    /** 失败了 */
    FAILED
}
