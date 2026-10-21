package com.shiye.journal.domain

/**
 * 灵感草稿规则（规格 §6.1 / §6.2）。
 *
 * 灵感是"想到就立刻记下"的东西，规则应该越轻越好 ——
 * 所以这里只有三条：能不能发、超没超长、改没改。
 * 它们都跟界面无关，单独放纯 Kotlin 里可以直接单测，
 * 不用为了验证"空内容不能发送"去点一遍真机。
 */
object InspirationDraft {

    /** 单条灵感的长度上限。灵感是短文本，超长通常是误粘贴 */
    const val MAX_LENGTH = 2000

    /** 去掉首尾空白后的正文。存库、比较都用这一份，避免"看着一样其实不同" */
    fun normalize(content: String): String = content.trim()

    /** 可以发送：非空且没超长 */
    fun canSend(content: String): Boolean {
        val text = normalize(content)
        return text.isNotBlank() && text.length <= MAX_LENGTH
    }

    /**
     * 不能发送时给用户的提示；能发送则返回 null。
     *
     * 超长提示里带上当前字数：只说"太长"用户还得自己猜多了多少个。
     */
    fun messageFor(content: String): String? {
        val text = normalize(content)
        return when {
            text.isBlank() -> "先写点什么吧"
            text.length > MAX_LENGTH -> "灵感最多 ${MAX_LENGTH} 个字，现在 ${text.length} 个"
            else -> null
        }
    }

    /**
     * 相对原始内容是否发生了实质变化。
     *
     * 只动了几处空格不应该算改过 —— 否则"已编辑"标记会在用户什么都没改时冒出来，
     * 看了还以为是别人动了自己的记录。
     */
    fun changed(original: String, next: String): Boolean =
        normalize(original) != normalize(next)
}
