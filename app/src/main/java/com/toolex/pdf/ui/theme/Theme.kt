package com.toolex.pdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF2F6FED)
private val Violet = Color(0xFF6C3BF4)

private val LightScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EDFF),
    onPrimaryContainer = Color(0xFF0B2A66),
    secondary = Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE6FF),
    onSecondaryContainer = Color(0xFF2A1266),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF11151C),
    surface = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFDFF),
    surfaceContainer = Color.White,
    onSurface = Color(0xFF11151C),
    surfaceVariant = Color(0xFFEDF0F7),
    onSurfaceVariant = Color(0xFF4A5468),
    outline = Color(0xFFCBD2E0),
    outlineVariant = Color(0xFFE1E6F0),
    error = Color(0xFFC2334D),
    errorContainer = Color(0xFFFFE7EB),
    onErrorContainer = Color(0xFF5C0A1C),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DBAFF),
    onPrimary = Color(0xFF002B6B),
    primaryContainer = Color(0xFF12386F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFC3AEFF),
    onSecondary = Color(0xFF2A1360),
    secondaryContainer = Color(0xFF2F1F63),
    onSecondaryContainer = Color(0xFFE9DEFF),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE7EAF1),
    surface = Color(0xFF161A21),
    surfaceContainerLowest = Color(0xFF12161C),
    surfaceContainerLow = Color(0xFF1B2028),
    surfaceContainer = Color(0xFF161A21),
    onSurface = Color(0xFFE7EAF1),
    surfaceVariant = Color(0xFF232935),
    onSurfaceVariant = Color(0xFFAFB8C9),
    outline = Color(0xFF3B4353),
    outlineVariant = Color(0xFF2A3140),
    error = Color(0xFFFFB3BF),
    errorContainer = Color(0xFF5C0A1C),
    onErrorContainer = Color(0xFFFFD9DF),
)

@Composable
fun ToolexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Branded colours on purpose (not wallpaper-based): the PDF preview and the
    // export cards should look the same on every phone.
    val scheme = if (darkTheme) DarkScheme else LightScheme

    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography.copy(
            headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
        ),
        content = content,
    )
}
