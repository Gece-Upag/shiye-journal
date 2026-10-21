package com.shiye.journal.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色板。全部取自 UI 原型 local_diary_ui_prototype.html 的 CSS 变量，
 * 浅色看 `:root`，深色看 `.dark`，改色时两边对照着改，不要只改一半。
 *
 * 命名沿用 CSS 变量名（bg / surface / accent / danger …），
 * 方便以后拿着原型对照检查。
 */

// ---------------- 浅色（原型 :root） ----------------

val BgLight = Color(0xFFF7F6F2)
val SurfaceLight = Color(0xFFFFFFFF)
val Surface2Light = Color(0xFFF0EFE9)
val TextLight = Color(0xFF20231F)
val MutedLight = Color(0xFF777D74)
val LineLight = Color(0xFFE8E7DF)
val AccentLight = Color(0xFF51694F)
val AccentSoftLight = Color(0xFFE7EEE3)
val AccentStrongLight = Color(0xFF354D34)
val DangerLight = Color(0xFFB84D43)
val DangerSoftLight = Color(0xFFFBEDEB)
val GoldLight = Color(0xFFB6843C)

// ---------------- 深色（原型 .dark） ----------------

val BgDark = Color(0xFF171A17)
val SurfaceDark = Color(0xFF212621)
val Surface2Dark = Color(0xFF2B302B)
val TextDark = Color(0xFFEFF1E9)
val MutedDark = Color(0xFFA1A99C)
val LineDark = Color(0xFF343A33)
val AccentDark = Color(0xFFABC39C)
val AccentSoftDark = Color(0xFF303D2E)
val AccentStrongDark = Color(0xFFD9E9CE)
val DangerDark = Color(0xFFF09589)
val DangerSoftDark = Color(0xFF452C29)
val GoldDark = Color(0xFFD1AD6F)

/**
 * 原型里借款金额专用的中间色（.amount.borrow = #a77528），
 * 深色下用原型的 gold 值 #d1ad6f。
 */
val LoanLight = Color(0xFFA77528)
val LoanDark = GoldDark

/**
 * Material3 没有合适槽位、但又确实需要的颜色。
 *
 * 用法：`ShiyeTheme.extra.income`。
 * 之所以单独做一层而不硬编码：深色模式下这些颜色要跟着换，
 * 硬编码会漏掉某处，导致深色模式下出现看不清的字。
 */
data class ShiyeExtraColors(
    /** 收入金额色（原型 .income = var(--accent)） */
    val income: Color,

    /** 支出金额色（原型 .expense = var(--danger)） */
    val expense: Color,

    /** 借款金额色（原型 .amount.borrow） */
    val loan: Color,

    /** 次级背景，用于卡片内部区块与输入框底色 */
    val surface2: Color,

    /** 强调色的浅底，用于选中态与标签 */
    val accentSoft: Color,

    /** 强调色的重色，用于强调文字与按下态 */
    val accentStrong: Color,

    /** 危险色的浅底，用于删除确认的图标底色 */
    val dangerSoft: Color,

    /** 金色，用于完成状态徽章等点缀 */
    val gold: Color
)

val LightExtraColors = ShiyeExtraColors(
    income = AccentLight,
    expense = DangerLight,
    loan = LoanLight,
    surface2 = Surface2Light,
    accentSoft = AccentSoftLight,
    accentStrong = AccentStrongLight,
    dangerSoft = DangerSoftLight,
    gold = GoldLight
)

val DarkExtraColors = ShiyeExtraColors(
    income = AccentDark,
    expense = DangerDark,
    loan = LoanDark,
    surface2 = Surface2Dark,
    accentSoft = AccentSoftDark,
    accentStrong = AccentStrongDark,
    dangerSoft = DangerSoftDark,
    gold = GoldDark
)
