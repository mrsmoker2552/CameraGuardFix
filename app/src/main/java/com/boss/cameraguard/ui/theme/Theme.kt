package com.boss.cameraguard.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.boss.cameraguard.data.AppThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared interface palette. Camera/traffic semantic colours stay independent. */
object CameraGuardPalette {
    private var dark: Boolean = true
    val isDark: Boolean get() = dark
    internal fun use(mode: AppThemeMode) { dark = mode == AppThemeMode.DARK }

    // CameraGuard signature palette: deep black / graphite + warm golden yellow, bold and
    // high-contrast. Safety-critical red/green remain semantic and are intentionally not
    // recoloured; the navigation route line keeps a blue option (RouteBlue) since the brief
    // explicitly allows blue to stay where it's needed for driving-safety clarity.
    val Background: Color get() = if (dark) Color(0xFF050505) else Color(0xFFF5F3EE)
    val Surface: Color get() = if (dark) Color(0xFF111111) else Color(0xFFFFFFFF)
    val Raised: Color get() = if (dark) Color(0xFF1A1A1A) else Color(0xFFEFEDE6)
    val Accent: Color get() = if (dark) Color(0xFFFFC400) else Color(0xFFE6AE00)
    val AccentContainer: Color get() = if (dark) Color(0xFF3A2E00) else Color(0xFFFFF3C4)
    val RouteBlue: Color get() = if (dark) Color(0xFF4FA8F0) else Color(0xFF1769AA)
    val Highlight: Color get() = if (dark) Color(0xFFFFCF33) else Color(0xFFD9A400)
    val Text: Color get() = if (dark) Color(0xFFFFFFFF) else Color(0xFF0A0A0A)
    val Muted: Color get() = if (dark) Color(0xFFD0D0D0) else Color(0xFF5C5C5C)
    val Border: Color get() = if (dark) Color(0xFF333333) else Color(0xFFD8D5CC)
    val Danger = Color(0xFFD93B4A)
    val Warning = Color(0xFFF59A2F)
    val Success = Color(0xFF1FA978)
    // Contrasting text/icon color to place on top of Accent (yellow) surfaces - always
    // near-black per the brief's "Primary = Yellow background + black text/icon" rule.
    val OnAccent: Color get() = Color(0xFF0A0A0A)
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFC400), onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFF3A2E00), onPrimaryContainer = Color(0xFFFFE9A8),
    secondary = Color(0xFFF59A2F), onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFF4A2E0D), onSecondaryContainer = Color(0xFFFFD9A8),
    tertiary = Color(0xFF4FA8F0), background = Color(0xFF050505), surface = Color(0xFF111111),
    surfaceVariant = Color(0xFF1A1A1A), onBackground = Color(0xFFFFFFFF), onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFD0D0D0), outline = Color(0xFF333333), outlineVariant = Color(0xFF242424),
    error = Color(0xFFFF6170), onError = Color(0xFF0A0A0A), errorContainer = Color(0xFF501C2A),
    onErrorContainer = Color(0xFFFFDADF), surfaceTint = Color.Transparent
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFE6AE00), onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFFFFF3C4), onPrimaryContainer = Color(0xFF3A2E00),
    secondary = Color(0xFFB96500), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4BF), onSecondaryContainer = Color(0xFF3D2200),
    tertiary = Color(0xFF1769AA), background = Color(0xFFF5F3EE), surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEFEDE6), onBackground = Color(0xFF0A0A0A), onSurface = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFF5C5C5C), outline = Color(0xFFD8D5CC), outlineVariant = Color(0xFFE5E2D8),
    error = Color(0xFFD93B4A), onError = Color.White, errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002), surfaceTint = Color.Transparent
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=24.sp,lineHeight=30.sp),
    titleLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=21.sp,lineHeight=27.sp),
    titleMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp),
    bodyLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=24.sp),
    bodyMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=20.sp),
    labelLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp)
)

@Composable
fun CameraGuardTheme(themeMode: AppThemeMode = AppThemeMode.DARK, content: @Composable () -> Unit) {
    CameraGuardPalette.use(themeMode)
    MaterialTheme(colorScheme = if (themeMode == AppThemeMode.DARK) DarkScheme else LightScheme, typography=AppTypography,
        shapes=Shapes(extraSmall=RoundedCornerShape(10.dp),small=RoundedCornerShape(14.dp),
            medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(26.dp),extraLarge=RoundedCornerShape(32.dp)),
        content=content)
}
