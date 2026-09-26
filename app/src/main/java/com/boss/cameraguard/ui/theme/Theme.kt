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

    // CameraGuard signature palette: Midnight Navy + Deep Blue + Sky Blue + Amber.
    // Safety-critical red/green remain semantic and are intentionally not recoloured.
    val Background: Color get() = if (dark) Color(0xFF040521) else Color(0xFFF1F5FA)
    val Surface: Color get() = if (dark) Color(0xFF081D56) else Color(0xFFF4F7FB)
    val Raised: Color get() = if (dark) Color(0xFF0D2D6B) else Color(0xFFE7EDF5)
    val Accent: Color get() = if (dark) Color(0xFF62AAE5) else Color(0xFF1E6DAF)
    val AccentContainer: Color get() = if (dark) Color(0xFF12376F) else Color(0xFFDCEEFF)
    val RouteBlue: Color get() = if (dark) Color(0xFF62AAE5) else Color(0xFF1769AA)
    val Highlight: Color get() = Color(0xFFF59A2F)
    val Text: Color get() = if (dark) Color(0xFFF7FAFF) else Color(0xFF040521)
    val Muted: Color get() = if (dark) Color(0xFFB6C9E0) else Color(0xFF4E6280)
    val Border: Color get() = if (dark) Color(0xFF264B86) else Color(0xFFB9CFE4)
    val Danger = Color(0xFFD93B4A)
    val Warning = Color(0xFFF59A2F)
    val Success = Color(0xFF1FA978)
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF62AAE5), onPrimary = Color(0xFF040521),
    primaryContainer = Color(0xFF12376F), onPrimaryContainer = Color(0xFFEAF5FF),
    secondary = Color(0xFFF59A2F), onSecondary = Color(0xFF040521),
    secondaryContainer = Color(0xFF563410), onSecondaryContainer = Color(0xFFFFE1B8),
    tertiary = Color(0xFF62AAE5), background = Color(0xFF040521), surface = Color(0xFF081D56),
    surfaceVariant = Color(0xFF0D2D6B), onBackground = Color(0xFFF7FAFF), onSurface = Color(0xFFF7FAFF),
    onSurfaceVariant = Color(0xFFB6C9E0), outline = Color(0xFF264B86), outlineVariant = Color(0xFF18396E),
    error = Color(0xFFFF6170), onError = Color(0xFF040521), errorContainer = Color(0xFF501C2A),
    onErrorContainer = Color(0xFFFFDADF), surfaceTint = Color.Transparent
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF081D56), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEFF), onPrimaryContainer = Color(0xFF040521),
    secondary = Color(0xFFB96500), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4BF), onSecondaryContainer = Color(0xFF3D2200),
    tertiary = Color(0xFF1E6DAF), background = Color(0xFFF1F5FA), surface = Color(0xFFF4F7FB),
    surfaceVariant = Color(0xFFE7EDF5), onBackground = Color(0xFF040521), onSurface = Color(0xFF040521),
    onSurfaceVariant = Color(0xFF4E6280), outline = Color(0xFFB9CFE4), outlineVariant = Color(0xFFD5E3F0),
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
