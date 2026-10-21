package com.shiye.journal.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日期与时间工具。
 *
 * 全局约定（数据库里也按这个存）：
 * - 日期 = "yyyy-MM-dd" 字符串；
 * - 时间戳 = epoch 毫秒 Long（按系统默认时区换算）；
 * - 时间 = "HH:mm" 字符串。
 *
 * 之所以不直接存 Instant / OffsetDateTime：备份要导出成人能读的 JSON，
 * 且换时区时不希望历史日记的日期漂移。
 */
object DateTimeUtil {

    /** 日期存储与比较统一用这个格式，字典序即时间序，可以直接用 >= / <= 比较 */
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---------- 取当前时间 ----------

    fun today(): LocalDate = LocalDate.now(zone)

    fun todayString(): String = today().format(DATE_FORMATTER)

    fun nowTimeString(): String = LocalTime.now(zone).format(TIME_FORMATTER)

    fun nowMillis(): Long = System.currentTimeMillis()

    // ---------- 解析与格式化 ----------

    fun formatDate(date: LocalDate): String = date.format(DATE_FORMATTER)

    /** 解析失败返回 null，调用方决定兜底，不抛异常打断 UI */
    fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value, DATE_FORMATTER) }.getOrNull()

    /** "2026-10-15" → "2026年10月15日" */
    fun formatDateChinese(value: String): String {
        val d = parseDate(value) ?: return value
        return "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
    }

    /** "2026-10-15" → "10月15日 周三"（列表页用） */
    fun formatDateShortWithWeekday(value: String): String {
        val d = parseDate(value) ?: return value
        val weekday = WEEKDAY_NAMES[(d.dayOfWeek.value - 1).coerceIn(0, 6)]
        return "${d.monthValue}月${d.dayOfMonth}日 $weekday"
    }

    private val WEEKDAY_NAMES = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /**
     * 灵感列表的时间戳（规格 §6.1）。
     *
     * - 今天：`HH:mm`
     * - 昨天：`昨天 HH:mm`
     * - 更早：`MM-DD HH:mm`（月和日都补零，不然"3-05"和"10-15"宽度不一致，列表会参差）
     */
    fun formatInspirationTime(millis: Long): String {
        val dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)
        val time = dt.format(TIME_FORMATTER)
        val today = today()
        return when (dt.toLocalDate()) {
            today -> time
            today.minusDays(1) -> "昨天 $time"
            else -> "${String.format(Locale.US, "%02d-%02d", dt.monthValue, dt.dayOfMonth)} $time"
        }
    }

    /** 完整时间，如 "10-15 20:00" */
    fun formatDateTime(millis: Long): String {
        val dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)
        return "${dt.monthValue}-${String.format(Locale.US, "%02d", dt.dayOfMonth)} ${dt.format(TIME_FORMATTER)}"
    }

    /**
     * 流水列表里的"日期 + 时间"，如 "10-21 14:30"（规格 §5.1 的行尾信息）。
     *
     * 直接用流水自己存的 [date] / [time] 拼，**不走毫秒换算**：
     * [dateTime] 是写入那一刻按当时时区算出来的，换时区回算可能偏一天，
     * 而这里要显示的正是"用户填的那个日期"。
     *
     * 月和日**都补零**：只补日的话 "3-05" 和 "10-15" 宽度不一样，一列看下来会参差。
     * 不带年份 —— 首页只列最近几天，跨年的账另有编辑页显示完整日期。
     */
    fun formatShortDateTime(date: String, time: String): String {
        val d = parseDate(date) ?: return time
        return "${String.format(Locale.US, "%02d-%02d", d.monthValue, d.dayOfMonth)} $time"
    }

    /**
     * 带年份的完整日期时间，如 "2026-10-21 14:30"。
     *
     * 与 [formatShortDateTime] 一样用流水自己存的 [date] / [time]，不走毫秒换算。
     *
     * 只用在**搜索结果**这种跨时间范围的列表上：结果可能横跨好几年，
     * 省掉年份的话 "10-21" 到底是哪一年得靠猜。首页那种只看最近几天的地方仍然用短格式，
     * 年份在那里是纯噪声。
     */
    fun formatFullDateTime(date: String, time: String): String {
        val d = parseDate(date) ?: return time
        return "${d.year}-${String.format(Locale.US, "%02d-%02d", d.monthValue, d.dayOfMonth)} $time"
    }

    /** 相对今天的天数差，用于"上次备份 N 天前" */
    fun daysBetween(from: LocalDate, to: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(from, to)

    // ---------- 区间换算 ----------

    /**
     * 日期 + "HH:mm" → epoch 毫秒。
     * 只用于排序与区间筛选，不做跨时区换算。
     */
    fun millisOf(date: String, time: String): Long {
        val d = parseDate(date) ?: return 0L
        val t = runCatching { LocalTime.parse(time, TIME_FORMATTER) }.getOrDefault(LocalTime.MIDNIGHT)
        return LocalDateTime.of(d, t).atZone(zone).toInstant().toEpochMilli()
    }

    /** 某天 00:00 的毫秒值，用于区间左端点（含） */
    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 某天下一天 00:00 的毫秒值，用于区间右端点（不含） */
    fun startOfNextDayMillis(date: LocalDate): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** 某天 23:59:59.999 的毫秒值 */
    fun endOfDayMillis(date: LocalDate): Long = startOfNextDayMillis(date) - 1

    fun monthStart(date: LocalDate = today()): LocalDate = date.withDayOfMonth(1)

    fun monthEnd(date: LocalDate = today()): LocalDate = date.withDayOfMonth(date.lengthOfMonth())

    fun weekStart(date: LocalDate = today()): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun yearStart(date: LocalDate = today()): LocalDate = date.withDayOfYear(1)

    fun yearEnd(date: LocalDate = today()): LocalDate = date.withDayOfYear(date.lengthOfYear())

    // ---------- 与 Material3 日期选择器互转 ----------

    /**
     * Material3 的 DatePicker 用的是"UTC 当天 0 点"的毫秒值，
     * 直接拿系统时区去转会差一天（东八区会往前挪一天）。
     *
     * 这里统一按 UTC 互转，保证 LocalDate → 毫秒 → LocalDate 往返不变。
     * 这两个函数是纯函数，往返一致性有单元测试覆盖。
     */
    fun toPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(UTC_ZONE).toInstant().toEpochMilli()

    /** 解析失败或没选时返回 null */
    fun fromPickerMillis(millis: Long?): LocalDate? =
        millis?.let { java.time.Instant.ofEpochMilli(it).atZone(UTC_ZONE).toLocalDate() }

    private val UTC_ZONE: ZoneId = ZoneId.of("UTC")
}
