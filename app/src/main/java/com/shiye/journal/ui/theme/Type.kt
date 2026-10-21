package com.shiye.journal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字号。取自 UI 原型的视觉比例：
 * - 正文 16sp（日记正文、灵感内容，长时间阅读不吃力）
 * - 标题 20sp（页面大标题）
 * - 辅助 12sp（时间戳、说明文字）
 *
 * 行高统一给 1.4～1.5 倍：中文没有西文那样的升降部，行距太挤会糊成一片。
 */
object ShiyeType {
    /** 页面大标题，如「DIARY」「MONEY, SIMPLY」下面的中文标题 */
    val pageTitle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)

    /** 卡片标题 / 列表主文案 */
    val cardTitle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)

    /** 正文，日记与灵感内容的阅读字号 */
    val body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp)

    /** 次级正文，如列表摘要 */
    val bodySecondary = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)

    /** 辅助说明、时间戳 */
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)

    /** 小标签，如统计卡片标题 */
    val label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)

    /** 金额。等宽感由字重与 tabular 处理，这里只定基本形态 */
    val amount = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.sp)

    /** 大数字，如本月结余 */
    val amountLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)

    /** 顶部栏品牌名 */
    val brand = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)

    /** 顶部栏副标题 */
    val brandSub = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp)

    /** 输入框文字 */
    val input = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp)
}

/**
 * Material3 排版映射。
 *
 * 只覆盖本项目真正用到的槽位，其余沿用 Material3 默认值，
 * 免得为了"填满"写出一堆用不上、将来也记不清为什么要这么定的样式。
 */
val ShiyeTypography = Typography(
    displaySmall = ShiyeType.pageTitle,
    headlineSmall = ShiyeType.pageTitle,
    titleLarge = ShiyeType.pageTitle,
    titleMedium = ShiyeType.cardTitle,
    titleSmall = ShiyeType.cardTitle,
    bodyLarge = ShiyeType.body,
    bodyMedium = ShiyeType.bodySecondary,
    bodySmall = ShiyeType.caption,
    labelLarge = ShiyeType.cardTitle,
    labelMedium = ShiyeType.label,
    labelSmall = ShiyeType.caption
)
