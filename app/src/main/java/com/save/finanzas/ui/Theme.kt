package com.save.finanzas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF5B5B5B),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFAFAF8),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF0F0EC),
    onSurfaceVariant = Color(0xFF6B6B66),
    surfaceContainer = Color(0xFFF3F3EF),
    surfaceContainerHigh = Color(0xFFEDEDE8),
    surfaceContainerLow = Color(0xFFF6F6F2),
    outline = Color(0xFFD6D6D0),
    outlineVariant = Color(0xFFE6E6E0),
    error = Color(0xFFC2410C),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF2F2EE),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFB0B0AA),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2EE),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF2F2EE),
    surfaceVariant = Color(0xFF1E1E1C),
    onSurfaceVariant = Color(0xFF9C9C96),
    surfaceContainer = Color(0xFF1C1C1A),
    surfaceContainerHigh = Color(0xFF242422),
    surfaceContainerLow = Color(0xFF181817),
    outline = Color(0xFF3A3A37),
    outlineVariant = Color(0xFF2A2A28),
    error = Color(0xFFFB923C),
)

object Tone {
    val incomeLight = Color(0xFF15803D)
    val incomeDark = Color(0xFF4ADE80)
    val expenseLight = Color(0xFFB91C1C)
    val expenseDark = Color(0xFFF87171)
    val saveLight = Color(0xFF1D4ED8)
    val saveDark = Color(0xFF93C5FD)

    val income: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) incomeDark else incomeLight
    val expense: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) expenseDark else expenseLight
    val save: Color @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) saveDark else saveLight
}

private val AppTypography = Typography(
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun SaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
