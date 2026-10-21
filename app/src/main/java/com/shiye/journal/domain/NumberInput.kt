package com.shiye.journal.domain

/**
 * 整数输入框的编辑处理。**纯 Kotlin，可单测。**
 *
 * 存在的原因和 `DateInput` 是同一个：这类输入框的值**不是用户直接写的**，
 * 而是由一个整数派生出来的字符串。一旦我们对它做了清洗
 * （丢掉非数字、去掉多余的前导零），文本长度就变了，
 * 而 Compose 的 `TextField(value: String)` 会**沿用旧的光标下标** ——
 * 表现就是光标跑到被删掉的字符前面，"看着能点进去，字却输不进"。
 *
 * 所以这里连同光标位置一起算出来，交给 UI 层用 `TextFieldValue` 接管。
 *
 * 定位光标的办法是**数光标前面保留下来几个数字**，而不是去追踪每个字符的增删：
 * 字符可能被批量删掉，但"光标前还剩几个值得留的东西"永远说得清楚。
 */
object NumberInput {

    /**
     * 清洗一次编辑结果。
     *
     * 规则：
     * 1. 只保留数字 —— 粘贴进来的单位、符号、空格一律丢掉；
     * 2. 去掉多余的前导零（"0060" → "60"），但单独一个 "0" 要留着，
     *    否则用户想输入 0 开头的数会一个字都敲不进去；
     * 3. 空串是合法的中间状态（正被人删空），不要急着替他填个数。
     *
     * @param text 编辑后的完整文本
     * @param cursor 编辑后的光标位置
     * @return 清洗后的文本和光标位置
     */
    fun sanitize(text: String, cursor: Int): Pair<String, Int> {
        // 先算"光标之前有几个要保留的数字"，顺便把非数字剔掉
        var keptBeforeCursor = 0
        val digits = StringBuilder()
        for (i in text.indices) {
            val c = text[i]
            val keep = c.isDigit()
            if (keep && i < cursor) keptBeforeCursor++
            if (keep) digits.append(c)
        }
        val allDigits = digits.toString()

        // 去掉多余前导零，留最后一个（用户只敲了一个 0 的情况）
        var leadingZeros = 0
        while (leadingZeros < allDigits.length - 1 && allDigits[leadingZeros] == '0') {
            leadingZeros++
        }
        val cleaned = if (leadingZeros > 0) allDigits.substring(leadingZeros) else allDigits

        // 光标左边有几个字符被吃掉了：前导零那部分最多吃掉自己数目的那些
        val removedBefore = leadingZeros.coerceAtMost(keptBeforeCursor)
        val newCursor = (keptBeforeCursor - removedBefore).coerceIn(0, cleaned.length)

        return cleaned to newCursor
    }

    /**
     * 把输入框里的文本落到范围内的整数。
     *
     * @param fallback 空串时用它（用户删空了，还不知道想填什么）
     */
    fun resolve(text: String, min: Int, max: Int, fallback: Int): Int {
        val value = text.toLongOrNull() ?: return fallback.coerceIn(min, max)
        return value.coerceIn(min.toLong(), max.toLong()).toInt()
    }
}
