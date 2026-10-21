package com.shiye.journal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.shiye.journal.data.model.ThemeMode

/**
 * 浅色配色方案。按 Material3 的语义槽位逐一对应原型颜色，
 * 不偷懒用默认紫：这套色板是"纸感 + 低饱和绿"的调子，换成默认色整个气质就没了。
 */
private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = AccentStrongLight,
    secondary = GoldLight,
    onSecondary = Color.White,
    secondaryContainer = Surface2Light,
    onSecondaryContainer = TextLight,
    background = BgLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = Surface2Light,
    onSurfaceVariant = MutedLight,
    surfaceTint = AccentLight,
    outline = LineLight,
    outlineVariant = LineLight,
    error = DangerLight,
    onError = Color.White,
    errorContainer = DangerSoftLight,
    onErrorContainer = DangerLight,
    scrim = Color(0x9920241F)
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = BgDark,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentStrongDark,
    secondary = GoldDark,
    onSecondary = BgDark,
    secondaryContainer = Surface2Dark,
    onSecondaryContainer = TextDark,
    background = BgDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = Surface2Dark,
    onSurfaceVariant = MutedDark,
    surfaceTint = AccentDark,
    outline = LineDark,
    outlineVariant = LineDark,
    error = DangerDark,
    onError = BgDark,
    errorContainer = DangerSoftDark,
    onErrorContainer = DangerDark,
    scrim = Color(0x99000000)
)

/** 自定义扩展色的 CompositionLocal */
val LocalShiyeExtraColors = staticCompositionLocalOf { LightExtraColors }

/**
 * 应用主题。
 *
 * @param themeMode 用户在设置里选的模式；SYSTEM 时跟随系统深色开关。
 * @param content 内容。
 */
@Composable
fun ShiyeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    // 状态栏与导航栏跟着主题走，否则深色模式下会出现一条刺眼的亮色系统栏。
    // statusBarColor / navigationBarColor 在 API 35+ 被标记为过时（官方推荐改由
    // WindowInsetsController 统一控制），但本项目 minSdk 为 26，
    // 在旧版本上仍然只能用它们把系统栏设为透明，所以保留并显式压制警告。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalShiyeExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ShiyeTypography,
            content = content
        )
    }
}

/**
 * 在 Composable 里读取扩展色：`ShiyeExtraColors.income`。
 *
 * 用 object 名字和 data class 名字撞了会难读，所以取名为 [ShiyeExtras]。
 */
val ShiyeExtras: ShiyeExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalShiyeExtraColors.current
