package com.open.wuling.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 全局卡片透明度，各 Screen 通过 LocalCardAlpha 读取
val LocalCardAlpha = compositionLocalOf { 0.95f }

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryGreen,
    tertiary = PrimaryOrange,
    background = BackgroundPrimary,
    surface = BackgroundCard,
    surfaceVariant = BackgroundElevated,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryGreen,
    tertiary = PrimaryOrange,
    background = LightBackgroundPrimary,
    surface = LightBackgroundCard,
    surfaceVariant = LightBackgroundElevated,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary
)

/**
 * 根据自定义颜色参数生成 ColorScheme
 */
fun buildCustomColorScheme(
    isDark: Boolean,
    primaryColor: Color,
    backgroundColor: Color,
    cardColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
): androidx.compose.material3.ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = primaryColor,
            background = backgroundColor,
            surface = cardColor,
            surfaceVariant = cardColor.copy(alpha = 0.7f),
            onPrimary = Color.White,
            onBackground = textPrimaryColor,
            onSurface = textPrimaryColor,
            onSurfaceVariant = textSecondaryColor
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            background = backgroundColor,
            surface = cardColor,
            surfaceVariant = cardColor.copy(alpha = 0.7f),
            onPrimary = Color.White,
            onBackground = textPrimaryColor,
            onSurface = textPrimaryColor,
            onSurfaceVariant = textSecondaryColor
        )
    }
}

@Composable
fun WulingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useCustomColors: Boolean = false,
    customColorScheme: androidx.compose.material3.ColorScheme? = null,
    cardAlpha: Float = 0.95f,
    content: @Composable () -> Unit
) {
    // 优先使用自定义颜色方案，否则使用预设方案
    val colorScheme = when {
        useCustomColors && customColorScheme != null -> customColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current

    // 更新系统状态栏颜色
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalCardAlpha provides cardAlpha) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
