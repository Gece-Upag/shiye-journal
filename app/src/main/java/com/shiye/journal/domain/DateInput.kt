package com.shiye.journal.domain

import com.shiye.journal.util.DateTimeUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** 手输日期的解析结果。UI 只管显示 [DateInputResult.Error.message]，不自己判断格式。 */
sealed interface DateInputResult {
    data class Ok(val date: LocalDate) : DateInputResult
    data class Error(val message: String) : DateInputResult
}

/**
 * 统计页顶部那个"可以直接敲日期"的输入框的解析规则。
 *
 * 为什么放到 domain 而不是 UI：这套"位数 → 日期 → 周期"的换算有几条容易搞错的边界
 * （2 月 30 日算不算、未来要不要拦、周粒度敲任意一天落到哪一周），
 * 必须能被单元测试单独按住，不能只在手机上点点看。
 */
object DateInput {

    /**
     * 输入框里的提示文案，随粒度变：日/周是完整日期，月是年月，年只有年。
     *
     * 这样用户一眼就知道"这个粒度下敲几位就够了"。
     */
    fun placeholder(kind: PeriodKind, today: LocalDate = DateTimeUtil.today()): String =
        when (kind) {
            PeriodKind.DAY, PeriodKind.WEEK -> DateTimeUtil.formatDate(today)
            PeriodKind.MONTH -> DateTimeUtil.formatDate(today).substring(0, 7)
            PeriodKind.YEAR -> today.year.toString()
        }

    /**
     * 把任意输入整理成"带分隔线的日期片段"。
     *
     * 只留数字、最多 8 位，并在第 4 / 6 位后自动补 `-`。
     * 用户敲 20261015 看到的就是 2026-10-15，不用自己敲横线，也不用担心粘进来一堆杂字符。
     */
    fun formatDigits(raw: String): String {
        val digits = raw.asSequence().filter { it.isDigit() }.take(8).toList()
        return buildString {
            digits.forEachIndexed { index, char ->
                if (index == 4 || index == 6) append('-')
                append(char)
            }
        }
    }

    /**
     * 一次编辑的结果：新的带分隔线文本，以及光标该落在哪。
     *
     * 之所以要连光标一起返回：分隔线是自动补的、不是用户敲的字符，
     * 光标的"字符串下标"和"第几个数字"并不是一回事，交给 UI 去猜一定会错。
     */
    data class DateEdit(val text: String, val caret: Int)

    /**
     * 把"用户刚敲完的原始文本 + 光标位置"换算成"格式化文本 + 光标位置"。
     *
     * 这是 `BasicTextField` 的 String 重载解决不了、必须自己接管光标的原因：
     * 补分隔线会让文本凭空变长，Compose 只能沿用旧下标，
     * 于是敲到第 5 位数字（自动补出第一个 `-`）时光标被留在 `-` 前面，
     * 再敲一个数字就插成了 `2026-10-61` 这种串 —— 表现为"日期输不进去"。
     *
     * 这里按**光标前有几个数字**来定位，而不是字符串下标，两件容易错的事一并处理：
     * - 补出来的 `-` 不占光标位置；
     * - 退格删掉的是 `-`（数字没少、字符少了）时，把 `-` 前面那个数字一起删掉，
     *   否则按一次退格什么都没少，手感像卡住了。
     *
     * @param previous 上一次的实际文本（含分隔线），用来判断这次是删了数字还是删了分隔线
     * @param raw 这次编辑后的原始文本
     * @param caret 这次编辑后的光标位置（字符串下标）
     */
    fun applyEdit(previous: String, raw: String, caret: Int): DateEdit {
        val safeCaret = caret.coerceIn(0, raw.length)
        var digitsBefore = raw.substring(0, safeCaret).count { it.isDigit() }

        val prevDigits = previous.filter { it.isDigit() }
        val rawDigits = raw.filter { it.isDigit() }

        // 字符少了、数字没少 → 删掉的是分隔线，而不是数字
        val erasedSeparator = raw.length < previous.length &&
            rawDigits.length == prevDigits.length &&
            digitsBefore > 0

        val kept: String = if (erasedSeparator) {
            // 光标前最后一个数字（第 digitsBefore 个）才是用户真正想删的
            val at = digitsBefore - 1
            digitsBefore = at
            StringBuilder(prevDigits).apply { deleteCharAt(at) }.toString()
        } else {
            rawDigits
        }

        val text = formatDigits(kept)
        return DateEdit(text, caretOf(text, digitsBefore))
    }

    /**
     * "光标前有 [digitsBefore] 个数字"换算成带分隔线文本里的下标。
     *
     * 数字个数超出文本里的数字总数时（比如输入被 8 位上限截断了）返回文本末尾。
     */
    fun caretOf(formatted: String, digitsBefore: Int): Int {
        if (digitsBefore <= 0) return 0
        var seen = 0
        formatted.forEachIndexed { index, char ->
            if (char.isDigit() && ++seen == digitsBefore) return index + 1
        }
        return formatted.length
    }

    /**
     * 解析成**一个完整日期**：必须 8 位，且是真实存在的日子。
     *
     * 这里**不判断未来** —— 借款的到期日天然在未来，记一笔也可能补记/预记，
     * 日期本身有没有意义由调用方决定，不该在这里一刀切。
     *
     * 用 STRICT 解析而不是默认的 SMART：**SMART 会把 2 月 30 日静默改成 2 月 28 日**，
     * 用户以为填的是 30 号，实际上存成了 28 号，这种"悄悄改"比直接报错更糟。
     */
    fun parseDate(raw: String): DateInputResult {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return DateInputResult.Error("请输入日期")
        if (digits.length != FULL_LENGTH) {
            return DateInputResult.Error("请输入完整日期，如 2026-10-15")
        }

        val text = "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
        val date = runCatching { LocalDate.parse(text, STRICT_DATE) }.getOrNull()
            ?: return DateInputResult.Error("「$text」不是一个有效日期")
        return DateInputResult.Ok(date)
    }

    /**
     * 统计页专用：把输入落到对应的周期上，并**拦住未来**。
     *
     * 位数按粒度放开，粒度越粗允许越短：
     * - 日 / 周：必须 8 位（敲哪天就落到那一天所在周）；
     * - 月：6 位（年月）或 8 位；
     * - 年：4 位、6 位或 8 位。
     *
     * 未来的周期查出来一定是空的，与其让人翻（或敲）出一堆空页，
     * 不如跟左右箭头保持同一口径：就到"今天所在周期"为止。
     */
    fun parsePeriod(
        raw: String,
        kind: PeriodKind,
        today: LocalDate = DateTimeUtil.today()
    ): DateInputResult {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return DateInputResult.Error("请输入日期")

        val text = when (digits.length) {
            FULL_LENGTH ->
                "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"

            MONTH_LENGTH -> if (kind == PeriodKind.MONTH || kind == PeriodKind.YEAR) {
                "${digits.substring(0, 4)}-${digits.substring(4, 6)}-01"
            } else {
                null
            }

            YEAR_LENGTH -> if (kind == PeriodKind.YEAR) {
                "${digits.substring(0, 4)}-01-01"
            } else {
                null
            }

            else -> null
        }

        if (text == null) {
            return DateInputResult.Error(
                "看${kind.label}统计的话，输入 ${exampleOf(kind)}；也可以直接左右箭头翻"
            )
        }

        val date = runCatching { LocalDate.parse(text, STRICT_DATE) }.getOrNull()
            ?: return DateInputResult.Error("「$text」不是一个有效日期")

        val target = LedgerPeriod(kind, date)
        if (target.from.isAfter(LedgerPeriod.current(kind, today).from)) {
            return DateInputResult.Error(
                "${DateTimeUtil.formatDate(target.from)} 还没到，看不到未来的账"
            )
        }
        return DateInputResult.Ok(date)
    }

    private fun exampleOf(kind: PeriodKind): String = when (kind) {
        PeriodKind.DAY, PeriodKind.WEEK -> "2026-10-15"
        PeriodKind.MONTH -> "2026-10"
        PeriodKind.YEAR -> "2026"
    }

    /** 完整日期的位数：年年年年月月日日 */
    private const val FULL_LENGTH = 8
    private const val MONTH_LENGTH = 6
    private const val YEAR_LENGTH = 4

    /**
     * 严格日期格式。注意两点，都是踩过的坑：
     * - 用 `uuuu` 而不是 `yyyy`：STRICT 下 `yyyy`（era 年）没有 era 无法解析，会直接抛异常；
     * - 必须显式 withResolverStyle(STRICT)，否则 `DateTimeFormatter.ofPattern` 默认 SMART，会把非法日期"修正"掉。
     */
    private val STRICT_DATE: DateTimeFormatter = DateTimeFormatter
        .ofPattern("uuuu-MM-dd", Locale.US)
        .withResolverStyle(ResolverStyle.STRICT)
}
