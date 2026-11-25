package com.mycloset.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF6D4AFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFECE6FF), onPrimaryContainer = Color(0xFF2B1A7A),
    secondary = Color(0xFFFF6B8B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3EA), onSecondaryContainer = Color(0xFF5C1226),
    tertiary = Color(0xFF12A594), tertiaryContainer = Color(0xFFD7F5F0),
    background = Color(0xFFFAF8F5), onBackground = Color(0xFF1E1B22),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1E1B22),
    surfaceVariant = Color(0xFFF1EDE7), onSurfaceVariant = Color(0xFF6B6560),
    outline = Color(0xFFD9D2C9), outlineVariant = Color(0xFFE9E3DB),
    error = Color(0xFFD64550), errorContainer = Color(0xFFFFE1E3),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB9A6FF), onPrimary = Color(0xFF22106B),
    primaryContainer = Color(0xFF3B2A8F), onPrimaryContainer = Color(0xFFECE6FF),
    secondary = Color(0xFFFF8FA8), onSecondary = Color(0xFF4A0E20),
    secondaryContainer = Color(0xFF5C1F34), onSecondaryContainer = Color(0xFFFFE3EA),
    tertiary = Color(0xFF5EEAD4), tertiaryContainer = Color(0xFF0F4A42),
    background = Color(0xFF121014), onBackground = Color(0xFFEDE8F2),
    surface = Color(0xFF1C1A21), onSurface = Color(0xFFEDE8F2),
    surfaceVariant = Color(0xFF2A2630), onSurfaceVariant = Color(0xFFB4ADBA),
    outline = Color(0xFF4A4552), outlineVariant = Color(0xFF332F3B),
    error = Color(0xFFFF8A93), errorContainer = Color(0xFF5C1F26),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp),
)

private val Base = TextStyle()
private val AppTypography = Typography(
    displaySmall = Base.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = Base.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = Base.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = Base.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = Base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Base.copy(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = Base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = Base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = Base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MyClosetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = AppShapes, typography = AppTypography, content = content,
    )
}
