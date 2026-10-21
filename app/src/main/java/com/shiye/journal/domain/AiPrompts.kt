package com.shiye.journal.domain

import com.shiye.journal.features.ai.AiProtocol

/**
 * AI 的提示词与"能不能覆盖"的规则。**纯 Kotlin，可单测。**
 *
 * 提示词之所以单独抽出来：它是这个模块里最需要反复调的东西，
 * 放在业务代码里改一次就得重新读一遍上下文，也不好比较前后差异。
 *
 * 结构上是两段（这个划分很关键，见 [SAFETY_RULE]）：
 * 1. **安全底线** —— 固定不变，用户不能改；
 * 2. **任务要求** —— 用户在设置页可自定义，留空就用这里的默认。
 *
 * 用户能改任务要求（"要 AI 做什么"），但改不掉"不许编造"：
 * 日记是私人记录，模型往里添一句"今天和老朋友吃了饭"就是污染记忆，
 * 比润色错一个字严重得多。这条不能因为用户写了句随手的提示词就失效。
 */
object AiPrompts {

    /**
     * 所有请求**固定携带**的规则。
     *
     * 只有两条，都是"不许添油加醋"：
     * 1. 只输出结果本身 —— 免得返回一堆"好的，以下是润色结果…"的客套话被当成正文采纳；
     * 2. 不添加原文没有的信息 —— 这是保护私人记录的底线。
     */
    private const val SAFETY_RULE =
        "你在协助处理一段私人日记。以下规则必须遵守：\n" +
            "只输出要求的结果本身，不要解释、不要加标题、不要前后缀客套话。\n" +
            "不添加原文没有的信息，不推测事实，不补写情节。"

    /**
     * 润色的默认任务要求。
     *
     * 明确列出"不改语气 / 不补事实 / 不新增内容" ——
     * 只说"润色一下"，模型很容易顺手扩写成一篇更像样的文章，那不是用户要的。
     */
    const val DEFAULT_POLISH_RULE =
        "只改正错别字和不通顺的句子。\n" +
            "保持作者原本的语气与用词习惯，不要改变语气，也不要增加新内容。"

    /**
     * 一篇日记的 AI 建议的默认任务要求（用户口径：**要建议，不要流水账式的概括**）。
     *
     * 概括整篇日记没什么用 —— 那是把用户刚写的东西再说一遍。
     * 所以明确不许复述内容：复述出来的东西塞进日记里是废话。
     */
    const val DEFAULT_ENTRY_ADVICE_RULE =
        "针对这篇日记给出建议，总共不超过 300 字（这是全部内容的总字数上限，不是每条的）。\n" +
            "建议要贴合日记里实际写的事，具体可做。\n" +
            "把理由或做法说清楚，别为了简短而含糊，但也不要凑字数。\n" +
            "不要复述或概括日记内容，直接给建议。\n" +
            "可以分 3 到 5 条，条理清楚即可，不要加「小结」「建议如下」这类标题。"

    /**
     * 最近七天建议的默认任务要求（规格 §8.5）。
     *
     * 两条必须写进提示词的边界：
     * - 建议要具体，不许说"注意劳逸结合"这种放之四海皆准的废话；
     * - **借款不算收支**（数据那一段也写明了，这里是规则层面再强调一次）。
     */
    const val DEFAULT_ADVICE_RULE =
        "给出三条具体、可执行的建议，每条不超过 40 字。\n" +
            "建议要贴合这些记录里实际发生的事，不要说教，不要空泛的套话（例如「注意休息」这类）。\n" +
            "借入借出不是收入也不是支出，不能当作消费或收入来分析。"

    /**
     * 用户没填就用默认要求。
     *
     * 用户填的内容是**替换**默认任务要求，不是追加 ——
     * 追加的话会出现"只改错别字"和"改写成正式语气"同时存在的自相矛盾指令。
     */
    private fun taskRule(custom: String, fallback: String): String =
        custom.trim().ifBlank { fallback }

    /**
     * 润色（规格 §8.3）。
     *
     * @param source 要处理的原文
     * @param customRule 用户在设置页写的任务要求；留空用 [DEFAULT_POLISH_RULE]
     */
    fun polish(source: String, customRule: String = ""): List<AiProtocol.Message> = listOf(
        AiProtocol.Message.system(
            SAFETY_RULE + "\n" + taskRule(customRule, DEFAULT_POLISH_RULE)
        ),
        // user 这句只说"按要求处理"，不重复写"只改正错别字"——
        // 否则用户自定义了规则，这里还在说只改错别字，指令自相矛盾
        AiProtocol.Message.user("请按上面的要求处理下面这段文字：\n\n$source")
    )

    /**
     * 一篇日记的 AI 建议（规格 §8.4）。
     *
     * @param entryText 日记正文
     * @param customRule 用户在设置页写的任务要求；留空用 [DEFAULT_ENTRY_ADVICE_RULE]
     */
    fun entryAdvice(entryText: String, customRule: String = ""): List<AiProtocol.Message> = listOf(
        AiProtocol.Message.system(
            SAFETY_RULE + "\n" + taskRule(customRule, DEFAULT_ENTRY_ADVICE_RULE)
        ),
        AiProtocol.Message.user("这是今天的日记：\n\n$entryText\n\n请按上面的要求处理。")
    )

    /**
     * 最近七天的建议（规格 §8.5）。
     *
     * @param digest 最近七天的摘要（日记摘要 + 收支概况 + 借款）
     * @param customRule 用户在设置页写的任务要求；留空用 [DEFAULT_ADVICE_RULE]
     */
    fun advice(digest: String, customRule: String = ""): List<AiProtocol.Message> = listOf(
        AiProtocol.Message.system(
            SAFETY_RULE + "\n" + taskRule(customRule, DEFAULT_ADVICE_RULE)
        ),
        AiProtocol.Message.user(
            // "借款不计入收支"属于**数据说明**（这段数据是怎么来的），
            // 不是任务要求，所以即使自定义了规则也保留 —— 否则模型会看错数据
            "下面是最近七天的记录摘要（日记摘要 + 收支概况，借款已单独列出、不计入收支）：\n\n" +
                "$digest\n\n请按上面的要求处理。"
        )
    )

    /**
     * 把一篇日记整理成发给模型的文本。
     *
     * 只取有内容的字段：空字段发过去是白花 token，
     * 也会让模型以为"这篇日记就这些"。
     */
    fun entryText(fields: List<Pair<String, String>>): String =
        fields.filter { it.second.isNotBlank() }
            .joinToString("\n\n") { (label, content) -> "【$label】\n$content" }

    /** 单个字段也可以直接润色，这时不需要标题包装 */
    fun fieldText(content: String): String = content.trim()
}

/**
 * 采纳 AI 结果时的冲突判断（规格 §8.3：「不得自动覆盖用户仍在编辑中的新内容」）。
 *
 * 用户在等 AI 返回的那几十秒里很可能继续打字。这时直接把结果塞进去，
 * 等于把人家刚写的东西抹掉 —— 所以要先判一下，冲突就让用户自己决定。
 */
enum class AiApplyDecision {
    /** 内容没动过，直接替换 */
    REPLACE,

    /** 用户在这期间又改了内容，替换会覆盖，需要再确认一次 */
    NEEDS_CONFIRM,

    /** 结果和现在的内容一样，没什么可做的 */
    NO_CHANGE
}

fun decideApply(
    snapshotAtRequest: String,
    currentContent: String,
    aiResult: String
): AiApplyDecision = when {
    aiResult == currentContent -> AiApplyDecision.NO_CHANGE
    // 用户改过：只有"改回原样"才算没冲突
    currentContent != snapshotAtRequest -> AiApplyDecision.NEEDS_CONFIRM
    else -> AiApplyDecision.REPLACE
}
