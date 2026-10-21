package com.shiye.journal.features.ai

import com.shiye.journal.util.MoneyUtil

/**
 * 「AI 建议」要用的最近七天摘要（规格 §8.5）。**纯 Kotlin，可单测。**
 *
 * 两条硬要求落在这里：
 * 1. **不发日记全文** —— 规格说"除非用户明确要求，否则不把完整历史日记全部发送给模型"。
 *    所以每篇只取前 [ENTRY_SNIPPET_CHARS] 个字，够模型抓住大意，又不至于整篇搬走。
 * 2. **借款绝不能混进收支** —— 借来的钱不是收入、还回去也不是支出。
 *    混进去会得出"这个月很宽裕"的鬼话，所以借款单独成段，且明确标注它不计入收支。
 */
object AiDigest {

    /** 每篇日记最多取这么多字 */
    const val ENTRY_SNIPPET_CHARS = 120

    /** 收支概况最多列几个分类，避免摘要本身变得很长 */
    const val TOP_CATEGORY_LIMIT = 5

    /**
     * @param entries 最近七天的日记，按日期倒序；每项为（日期, 正文摘要）
     * @param incomeCents 这段时间普通收入合计（分）
     * @param expenseCents 这段时间普通支出合计（分）
     * @param topExpenses 支出最多的几个分类，每项为（分类名, 金额分）
     * @param loans 借入借出提醒，每项为（方向说明, 对方, 金额分）
     */
    fun build(
        entries: List<Pair<String, String>>,
        incomeCents: Long,
        expenseCents: Long,
        topExpenses: List<Pair<String, Long>>,
        loans: List<Triple<String, String, Long>>
    ): String = buildString {
        appendLine("最近七天的日记：")
        if (entries.isEmpty()) {
            appendLine("（这段时间没有写日记）")
        } else {
            entries.forEach { (date, body) ->
                val snippet = body.trim().let {
                    if (it.length > ENTRY_SNIPPET_CHARS) it.take(ENTRY_SNIPPET_CHARS) + "…" else it
                }
                appendLine("- $date：$snippet")
            }
        }

        appendLine()
        appendLine("收支概况（不含借款）：")
        appendLine("- 收入：${MoneyUtil.format(incomeCents)}")
        appendLine("- 支出：${MoneyUtil.format(expenseCents)}")
        val net = incomeCents - expenseCents
        appendLine("- 结余：${MoneyUtil.format(net)}")
        if (topExpenses.isNotEmpty()) {
            appendLine("- 花得最多的几类：")
            topExpenses.take(TOP_CATEGORY_LIMIT).forEach { (name, cents) ->
                appendLine("  · $name ${MoneyUtil.format(cents)}")
            }
        }

        appendLine()
        appendLine("借入借出（这是债务提醒，不是收入也不是支出，不要计入上面的收支）：")
        if (loans.isEmpty()) {
            appendLine("（无）")
        } else {
            loans.forEach { (direction, person, cents) ->
                appendLine("- $direction $person ${MoneyUtil.format(cents)}")
            }
        }
    }

    /** 空摘要的判断：什么都没有就没必要去问模型 */
    fun isEmpty(
        entries: List<Pair<String, String>>,
        incomeCents: Long,
        expenseCents: Long,
        loans: List<Triple<String, String, Long>>
    ): Boolean = entries.isEmpty() && incomeCents == 0L && expenseCents == 0L && loans.isEmpty()
}
