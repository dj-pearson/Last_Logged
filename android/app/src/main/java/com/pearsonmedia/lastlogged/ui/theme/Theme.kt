package com.pearsonmedia.lastlogged.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val BrandPrimary = Color(0xFF4F46E5)
val BrandAccent = Color(0xFF10B981)
val BrandError = Color(0xFFEF4444)
val BrandWarning = Color(0xFFF59E0B)

/**
 * Centralized urgency color tokens. Used by TrackerRow, TrackerDetail, and widgets.
 * Keep in sync with iOS urgency colors.
 */
object UrgencyColors {
    val Good = Color(0xFF10B981)      // emerald-500
    val GoodSoft = Color(0x1A10B981)  // 10% tint
    val DueSoon = Color(0xFFF59E0B)   // amber-500
    val DueSoonSoft = Color(0x1AF59E0B)
    val Overdue = Color(0xFFEF4444)   // red-500
    val OverdueSoft = Color(0x1AEF4444)
}

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandAccent,
    error = BrandError,
    surface = Color(0xFFFFFBFE),
    background = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9D97FF),
    secondary = Color(0xFF6EE7B7),
    error = Color(0xFFFCA5A5),
    surface = Color(0xFF1C1B1F),
    background = Color(0xFF1C1B1F),
    onPrimary = Color(0xFF1C1B1F),
    onSecondary = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5)
)

@Composable
fun LastLoggedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
