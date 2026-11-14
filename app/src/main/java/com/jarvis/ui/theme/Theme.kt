package com.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jarvis.domain.orb.OrbSpec

/** The user-picked accent (Settings > Appearance), available everywhere. */
val LocalAccent = staticCompositionLocalOf { NeonBlue }

/** True when dark mode is active, false for Light glassmorphic mode. */
val LocalIsDark = staticCompositionLocalOf { true }

val JarvisTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        letterSpacing = 6.sp,
        color = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = 0.4.sp,
        color = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        color = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 19.sp,
        color = TextSecondary,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextMuted,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun JarvisTheme(
    themeMode: String = "dark",
    accentHex: String = "#00E5FF",
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val accent = Color(OrbSpec.colorInt(accentHex))

    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF04070C),
            secondary = NeonPurple,
            onSecondary = Color(0xFF0A0714),
            tertiary = NeonBlue,
            background = VoidBlack,
            onBackground = TextPrimary,
            surface = Panel,
            onSurface = TextPrimary,
            surfaceVariant = Color(0xFF141B2C),
            onSurfaceVariant = TextSecondary,
            error = DangerRed,
            onError = Color(0xFF1A0505),
            outline = GlassBorder,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFF04070C),
            secondary = Color(0xFF6B21A8),
            onSecondary = Color.White,
            background = Color(0xFF0E1729),
            onBackground = TextPrimary,
            surface = Color(0xFF16223A),
            onSurface = TextPrimary,
            surfaceVariant = Color(0xFF1E2D4B),
            onSurfaceVariant = TextSecondary,
            error = DangerRed,
            outline = GlassBorder,
        )
    }

    CompositionLocalProvider(
        LocalAccent provides accent,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = JarvisTypography,
            content = content,
        )
    }
}
