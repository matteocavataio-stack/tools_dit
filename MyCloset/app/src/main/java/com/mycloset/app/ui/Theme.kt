package com.mycloset.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Violet = Color(0xFF7C3AED)

private val Light = lightColorScheme(
    primary = Violet, onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF), onPrimaryContainer = Color(0xFF2A0A6B),
    secondary = Color(0xFFDB2777), tertiary = Color(0xFF0F766E),
    background = Color(0xFFFBF9FF), surface = Color(0xFFFBF9FF),
    surfaceVariant = Color(0xFFF1ECF9),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFC4A8FF), onPrimary = Color(0xFF2A0A6B),
    primaryContainer = Color(0xFF4A2A99), onPrimaryContainer = Color(0xFFEDE4FF),
    secondary = Color(0xFFF472B6), tertiary = Color(0xFF5EEAD4),
    background = Color(0xFF121016), surface = Color(0xFF121016),
    surfaceVariant = Color(0xFF26212F),
)

@Composable
fun MyClosetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
