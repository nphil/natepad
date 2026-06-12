package com.natepad.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Theme selection ───────────────────────────────────────────────────────────

enum class AppTheme(val displayName: String) {
    MATERIAL_YOU("Material You"),
    PURPLE("Purple"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    DRACULA("Dracula"),
    SYNTHWAVE("Synthwave"),
    LEMONADE("Lemonade");

    val swatchPrimaryColor: Color get() = when (this) {
        MATERIAL_YOU -> Color(0xFF6750A4)
        PURPLE       -> Color(0xFF6750A4)
        OCEAN        -> Color(0xFF47C8E8)
        FOREST       -> Color(0xFF55C774)
        DRACULA      -> Color(0xFFFF79C6)
        SYNTHWAVE    -> Color(0xFFF92AAD)
        LEMONADE     -> Color(0xFF4A7C0F)
    }

    val swatchBgColor: Color get() = when (this) {
        MATERIAL_YOU -> Color(0xFF1C1B1F)
        PURPLE       -> Color(0xFF381E72)
        OCEAN        -> Color(0xFF0F2030)
        FOREST       -> Color(0xFF111913)
        DRACULA      -> Color(0xFF282A36)
        SYNTHWAVE    -> Color(0xFF2B213A)
        LEMONADE     -> Color(0xFFF8FFF0)
    }
}

@Composable
fun AppTheme.resolveColorScheme(darkTheme: Boolean): ColorScheme = when (this) {
    AppTheme.MATERIAL_YOU -> {
        val context = LocalContext.current
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
        }
    }
    AppTheme.PURPLE    -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
    AppTheme.OCEAN     -> OceanColorScheme
    AppTheme.FOREST    -> ForestColorScheme
    AppTheme.DRACULA   -> DraculaColorScheme
    AppTheme.SYNTHWAVE -> SynthwaveColorScheme
    AppTheme.LEMONADE  -> if (darkTheme) LemonadeDarkColorScheme else LemonadeLightColorScheme
}

// ── Purple (default brand) ────────────────────────────────────────────────────

private val PurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFB3261E),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
)

private val PurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
)

// ── Ocean ─────────────────────────────────────────────────────────────────────

private val OceanColorScheme = darkColorScheme(
    primary = Color(0xFF47C8E8),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D64),
    onPrimaryContainer = Color(0xFFB3EEFF),
    secondary = Color(0xFF7BBCCF),
    onSecondary = Color(0xFF0A3040),
    secondaryContainer = Color(0xFF1A4055),
    onSecondaryContainer = Color(0xFFC8E8F5),
    tertiary = Color(0xFF9B8DC8),
    onTertiary = Color(0xFF1A103F),
    tertiaryContainer = Color(0xFF281A58),
    onTertiaryContainer = Color(0xFFD0C8F5),
    error = Color(0xFFFF8A80),
    background = Color(0xFF0F2030),
    onBackground = Color(0xFFC8DDE8),
    surface = Color(0xFF162B3F),
    onSurface = Color(0xFFC8DDE8),
    surfaceVariant = Color(0xFF1E3A50),
    onSurfaceVariant = Color(0xFF8BAFC3),
    outline = Color(0xFF4A7D95),
)

// ── Forest ────────────────────────────────────────────────────────────────────

private val ForestColorScheme = darkColorScheme(
    primary = Color(0xFF55C774),
    onPrimary = Color(0xFF003315),
    primaryContainer = Color(0xFF004D1F),
    onPrimaryContainer = Color(0xFF9AEFB0),
    secondary = Color(0xFF7FB88C),
    onSecondary = Color(0xFF0A2713),
    secondaryContainer = Color(0xFF1A3A24),
    onSecondaryContainer = Color(0xFFC0E8CA),
    tertiary = Color(0xFFA0C070),
    onTertiary = Color(0xFF243305),
    tertiaryContainer = Color(0xFF354D0C),
    onTertiaryContainer = Color(0xFFD5EDAA),
    error = Color(0xFFFF8A80),
    background = Color(0xFF111913),
    onBackground = Color(0xFFD0D8D0),
    surface = Color(0xFF161F17),
    onSurface = Color(0xFFD0D8D0),
    surfaceVariant = Color(0xFF243028),
    onSurfaceVariant = Color(0xFF90B098),
    outline = Color(0xFF507860),
)

// ── Dracula ───────────────────────────────────────────────────────────────────

private val DraculaColorScheme = darkColorScheme(
    primary = Color(0xFFFF79C6),
    onPrimary = Color(0xFF4D0030),
    primaryContainer = Color(0xFF6D0044),
    onPrimaryContainer = Color(0xFFFFD0E8),
    secondary = Color(0xFFBD93F9),
    onSecondary = Color(0xFF2D0060),
    secondaryContainer = Color(0xFF440088),
    onSecondaryContainer = Color(0xFFE8D0FF),
    tertiary = Color(0xFF8BE9FD),
    onTertiary = Color(0xFF003845),
    tertiaryContainer = Color(0xFF005060),
    onTertiaryContainer = Color(0xFFC0FAFF),
    error = Color(0xFFFF5555),
    background = Color(0xFF282A36),
    onBackground = Color(0xFFF8F8F2),
    surface = Color(0xFF2C2E3F),
    onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF373A50),
    onSurfaceVariant = Color(0xFFCCCDD8),
    outline = Color(0xFF6272A4),
)

// ── Synthwave ─────────────────────────────────────────────────────────────────

private val SynthwaveColorScheme = darkColorScheme(
    primary = Color(0xFFF92AAD),
    onPrimary = Color(0xFF4D0030),
    primaryContainer = Color(0xFF7A0048),
    onPrimaryContainer = Color(0xFFFFD0E8),
    secondary = Color(0xFF72F1F1),
    onSecondary = Color(0xFF003838),
    secondaryContainer = Color(0xFF005050),
    onSecondaryContainer = Color(0xFFC0FBFB),
    tertiary = Color(0xFFFF7EDB),
    onTertiary = Color(0xFF4D0040),
    tertiaryContainer = Color(0xFF700060),
    onTertiaryContainer = Color(0xFFFFD0F8),
    error = Color(0xFFFF5555),
    background = Color(0xFF2B213A),
    onBackground = Color(0xFFF5F3FF),
    surface = Color(0xFF332848),
    onSurface = Color(0xFFF5F3FF),
    surfaceVariant = Color(0xFF453060),
    onSurfaceVariant = Color(0xFFC8C0DC),
    outline = Color(0xFF7060A0),
)

// ── Lemonade ──────────────────────────────────────────────────────────────────

private val LemonadeLightColorScheme = lightColorScheme(
    primary = Color(0xFF4A7C0F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F07A),
    onPrimaryContainer = Color(0xFF142300),
    secondary = Color(0xFF547800),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEF07A),
    onSecondaryContainer = Color(0xFF192200),
    tertiary = Color(0xFF376C00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB0F072),
    onTertiaryContainer = Color(0xFF0C2000),
    error = Color(0xFFB3261E),
    background = Color(0xFFF8FFF0),
    onBackground = Color(0xFF1C2100),
    surface = Color(0xFFF8FFF0),
    onSurface = Color(0xFF1C2100),
    surfaceVariant = Color(0xFFD8EDA8),
    onSurfaceVariant = Color(0xFF384D1A),
    outline = Color(0xFF5C7840),
)

private val LemonadeDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9ADA4A),
    onPrimary = Color(0xFF203A00),
    primaryContainer = Color(0xFF315700),
    onPrimaryContainer = Color(0xFFB5F564),
    secondary = Color(0xFFA8D456),
    onSecondary = Color(0xFF1E3500),
    secondaryContainer = Color(0xFF2C4D00),
    onSecondaryContainer = Color(0xFFC3F070),
    tertiary = Color(0xFF8CC840),
    onTertiary = Color(0xFF1A2E00),
    tertiaryContainer = Color(0xFF274500),
    onTertiaryContainer = Color(0xFFA8E858),
    error = Color(0xFFF2B8B5),
    background = Color(0xFF141F00),
    onBackground = Color(0xFFD8EDA0),
    surface = Color(0xFF141F00),
    onSurface = Color(0xFFD8EDA0),
    surfaceVariant = Color(0xFF2A3A10),
    onSurfaceVariant = Color(0xFFA0B878),
    outline = Color(0xFF708848),
)

// ── Typography ────────────────────────────────────────────────────────────────

val NatepadTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// ── Theme composable ──────────────────────────────────────────────────────────

@Composable
fun NatepadTheme(
    appTheme: AppTheme = AppTheme.MATERIAL_YOU,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = appTheme.resolveColorScheme(darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NatepadTypography,
        content = content
    )
}
