package com.shiye.journal.util

import java.util.Locale

/**
 * 金额工具。
 *
 * 全应用（含数据库）金额一律用 **Long 分** 表示，只在显示与输入时换算成元。
 * 理由：0.1 + 0.2 != 0.3，记账里反复累加会让账对不上。
 */
object MoneyUtil {

    /**
     * 分 → 展示字符串，如 123456 → "¥1,234.56"。
     * 负数显示为 "-¥1,234.56"（结余为负时用）。
     */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return sign + "¥" + String.format(Locale.US, "%,d.%02d", abs / 100, abs % 100)
    }

    /**
     * 带正负号的金额，收入记 "+¥12.00"、支出记 "−¥12.00"。
     * 这里用 U+2212 减号而不是连字符，视觉上和加号对齐。
     */
    fun formatSigned(cents: Long, positive: Boolean): String {
        val sign = if (positive) "+" else "−"
        return sign + format(kotlin.math.abs(cents))
    }

    /**
     * 用户输入的元字符串 → 分。
     *
     * 只接受数字和小数点，最多两位小数；解析失败返回 null 由调用方提示。
     * 故意不用 Double：用户输入 "12.34" 直接按字符串拆成 1234 分，
     * 中间不经过浮点，不会出现 1233.9999 这种结果。
     */
    fun parseToCents(input: String): Long? {
        val text = input.trim().replace("¥", "").replace(",", "").replace("，", "")
        if (text.isEmpty()) return null
        if (!text.matches(Regex("""\d{1,12}(\.\d{0,2})?"""))) return null

        val parts = text.split('.')
        val yuan = parts[0].toLongOrNull() ?: return null
        val centPart = if (parts.size > 1) parts[1] else ""
        // "12.5" 要当成 12.50，所以右侧补零到两位
        val cent = (centPart + "00").substring(0, 2).toLongOrNull() ?: return null

        val result = yuan * 100 + cent
        // 溢出保护：单个账户金额到不了这个量级
        return if (result < 0) null else result
    }

    /** 分 → 输入框里显示的元字符串，如 1234 → "12.34"；0 显示为空，方便直接输入 */
    fun centsToInputText(cents: Long): String =
        if (cents == 0L) "" else String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)
}
