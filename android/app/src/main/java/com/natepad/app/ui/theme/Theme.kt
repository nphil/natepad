package com.natepad.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// ── Theme selection ───────────────────────────────────────────────────────────
//
// Each named theme is defined by three seed colours (primary / secondary /
// tertiary). Full Material 3 light *and* dark schemes are generated from those
// seeds by `buildScheme()` below (tonal palettes derived in HSL space), so every
// theme reacts to the system light/dark setting and stays internally consistent.
// `Material You` is special — it pulls the wallpaper palette on Android 12+.
//
// Palettes are drawn from popular, well-loved colour schemes (Nord, Gruvbox,
// Solarized, Tokyo Night, Catppuccin, Rosé Pine, Dracula, …) plus a spread of
// vibrant hues so no two themes read alike.

internal data class ThemeSeeds(val primary: Long, val secondary: Long, val tertiary: Long)

enum class AppTheme(val displayName: String, internal val seeds: ThemeSeeds?) {
    MATERIAL_YOU("Material You", null),
    PURPLE      ("Purple",       ThemeSeeds(0xFF7C4DFF, 0xFF9575CD, 0xFFFF80AB)),
    OCEAN       ("Ocean",        ThemeSeeds(0xFF00BCD4, 0xFF4DD0E1, 0xFF5C6BC0)),
    FOREST      ("Forest",       ThemeSeeds(0xFF43A047, 0xFF66BB6A, 0xFF9CCC65)),
    DRACULA     ("Dracula",      ThemeSeeds(0xFFFF79C6, 0xFFBD93F9, 0xFF8BE9FD)),
    SYNTHWAVE   ("Synthwave",    ThemeSeeds(0xFFF92AAD, 0xFF72F1F1, 0xFFFF7EDB)),
    LEMONADE    ("Lemonade",     ThemeSeeds(0xFF8BC34A, 0xFFC0CA33, 0xFFCDDC39)),
    NORD        ("Nord",         ThemeSeeds(0xFF88C0D0, 0xFF81A1C1, 0xFFB48EAD)),
    GRUVBOX     ("Gruvbox",      ThemeSeeds(0xFFFE8019, 0xFFB8BB26, 0xFFFABD2F)),
    SOLARIZED   ("Solarized",    ThemeSeeds(0xFF268BD2, 0xFF2AA198, 0xFFB58900)),
    MONOKAI     ("Monokai",      ThemeSeeds(0xFFA6E22E, 0xFFF92672, 0xFF66D9EF)),
    TOKYO_NIGHT ("Tokyo Night",  ThemeSeeds(0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF)),
    CATPPUCCIN  ("Catppuccin",   ThemeSeeds(0xFFCBA6F7, 0xFFF5C2E7, 0xFF94E2D5)),
    ROSE_PINE   ("Rosé Pine",    ThemeSeeds(0xFFEB6F92, 0xFFC4A7E7, 0xFF9CCFD8)),
    EVERFOREST  ("Everforest",   ThemeSeeds(0xFFA7C080, 0xFF83C092, 0xFFE69875)),
    ONE_DARK    ("One Dark",     ThemeSeeds(0xFF61AFEF, 0xFFC678DD, 0xFF98C379)),
    SUNSET      ("Sunset",       ThemeSeeds(0xFFFF6E40, 0xFFFF4081, 0xFFFFAB40)),
    SAKURA      ("Sakura",       ThemeSeeds(0xFFFF6F9C, 0xFFFF9EC4, 0xFFCE93D8)),
    MIDNIGHT    ("Midnight",     ThemeSeeds(0xFF5C6BC0, 0xFF7986CB, 0xFF64B5F6)),
    CRIMSON     ("Crimson",      ThemeSeeds(0xFFE63950, 0xFFFF6B6B, 0xFFFF8A65)),
    AMBER       ("Amber",        ThemeSeeds(0xFFFFB300, 0xFFFFA000, 0xFFFFD54F)),
    EMERALD     ("Emerald",      ThemeSeeds(0xFF10B981, 0xFF34D399, 0xFF6EE7B7)),
    SLATE       ("Slate",        ThemeSeeds(0xFF64748B, 0xFF94A3B8, 0xFF38BDF8)),
    MINT        ("Mint",         ThemeSeeds(0xFF3EB489, 0xFF66D9AB, 0xFF80CBC4)),
    GRAPE       ("Grape",        ThemeSeeds(0xFFB24BF3, 0xFFD580FF, 0xFFF361DC)),
    CORAL       ("Coral",        ThemeSeeds(0xFFFF6F61, 0xFFFF8A75, 0xFFFFB199)),
    SKY         ("Sky",          ThemeSeeds(0xFF38BDF8, 0xFF7DD3FC, 0xFF818CF8)),
    MOCHA       ("Mocha",        ThemeSeeds(0xFFA1887F, 0xFFBCAAA4, 0xFFD7A86E)),
    LAVENDER    ("Lavender",     ThemeSeeds(0xFFB39DDB, 0xFFCE93D8, 0xFF9FA8DA)),
    ROSE_GOLD   ("Rose Gold",    ThemeSeeds(0xFFE8A0A8, 0xFFF0C9A0, 0xFFD98E9C)),
    AURORA      ("Aurora",       ThemeSeeds(0xFF1DE9B6, 0xFF64FFDA, 0xFF40C4FF));
}

@Composable
fun AppTheme.resolveColorScheme(darkTheme: Boolean): ColorScheme {
    if (this == AppTheme.MATERIAL_YOU) {
        val context = LocalContext.current
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            buildScheme(AppTheme.PURPLE.seeds!!, darkTheme)
        }
    }
    return buildScheme(seeds!!, darkTheme)
}

// ── Tonal palette generation ─────────────────────────────────────────────────
//
// A full M3 scheme is built from three accent seeds by taking each seed's hue &
// saturation and re-deriving Material's standard tone assignments in HSL space
// (dark: primary T80 / onPrimary T20 / container T30 / onContainer T90; light:
// primary T40 / container T90 / onContainer T10, etc.). Neutrals reuse the
// primary hue at a very low saturation so surfaces carry a subtle brand tint,
// the way real Material 3 schemes do.

private fun Color.hsl(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
    val h = when {
        d == 0f  -> 0f
        max == r -> 60f * ((((g - b) / d) % 6f + 6f) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else     -> 60f * (((r - g) / d) + 4f)
    }
    return floatArrayOf(h, s, l)
}

private fun hslColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else    -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f))
}

/** Tone of a seed at a target lightness, keeping its hue and (optionally scaled) saturation. */
private fun tone(seed: Color, l: Float, satScale: Float = 1f): Color {
    val hsl = seed.hsl()
    return hslColor(hsl[0], (hsl[1] * satScale).coerceIn(0f, 1f), l)
}

private fun buildScheme(seeds: ThemeSeeds, dark: Boolean): ColorScheme {
    val primary = Color(seeds.primary)
    val secondary = Color(seeds.secondary)
    val tertiary = Color(seeds.tertiary)
    val nHue = primary.hsl()[0]
    // Neutral / neutral-variant tones share the primary hue at low chroma.
    fun n(l: Float) = hslColor(nHue, 0.05f, l)
    fun nv(l: Float) = hslColor(nHue, 0.12f, l)

    val scheme = if (dark) darkColorScheme(
        primary = tone(primary, 0.80f),
        onPrimary = tone(primary, 0.18f),
        primaryContainer = tone(primary, 0.32f),
        onPrimaryContainer = tone(primary, 0.90f),
        inversePrimary = tone(primary, 0.40f),
        secondary = tone(secondary, 0.80f, 0.55f),
        onSecondary = tone(secondary, 0.18f, 0.55f),
        secondaryContainer = tone(secondary, 0.30f, 0.55f),
        onSecondaryContainer = tone(secondary, 0.90f, 0.55f),
        tertiary = tone(tertiary, 0.80f),
        onTertiary = tone(tertiary, 0.18f),
        tertiaryContainer = tone(tertiary, 0.32f),
        onTertiaryContainer = tone(tertiary, 0.90f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = n(0.09f),
        onBackground = n(0.90f),
        surface = n(0.09f),
        onSurface = n(0.90f),
        surfaceVariant = nv(0.28f),
        onSurfaceVariant = nv(0.80f),
        outline = nv(0.58f),
        outlineVariant = nv(0.28f),
        inverseSurface = n(0.90f),
        inverseOnSurface = n(0.20f),
        surfaceTint = tone(primary, 0.80f),
        scrim = Color.Black,
    ) else lightColorScheme(
        primary = tone(primary, 0.40f),
        onPrimary = Color.White,
        primaryContainer = tone(primary, 0.90f),
        onPrimaryContainer = tone(primary, 0.10f),
        inversePrimary = tone(primary, 0.80f),
        secondary = tone(secondary, 0.40f, 0.55f),
        onSecondary = Color.White,
        secondaryContainer = tone(secondary, 0.90f, 0.55f),
        onSecondaryContainer = tone(secondary, 0.10f, 0.55f),
        tertiary = tone(tertiary, 0.40f),
        onTertiary = Color.White,
        tertiaryContainer = tone(tertiary, 0.90f),
        onTertiaryContainer = tone(tertiary, 0.10f),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = n(0.985f),
        onBackground = n(0.10f),
        surface = n(0.985f),
        onSurface = n(0.10f),
        surfaceVariant = nv(0.90f),
        onSurfaceVariant = nv(0.30f),
        outline = nv(0.50f),
        outlineVariant = nv(0.80f),
        inverseSurface = n(0.20f),
        inverseOnSurface = n(0.95f),
        surfaceTint = tone(primary, 0.40f),
        scrim = Color.Black,
    )
    return scheme.withDerivedContainers(dark)
}

// ── Surface container derivation ─────────────────────────────────────────────
//
// M3 components (navigation rail, cards, panes) layer on the surfaceContainer
// hierarchy. darkColorScheme() fills those roles with baseline purple-neutral
// defaults, which clash with the custom hues — derive them from each scheme's
// own background/onBackground instead.
private fun ColorScheme.withDerivedContainers(dark: Boolean): ColorScheme {
    val bg = background
    val on = onBackground
    return if (dark) copy(
        surfaceDim = lerp(bg, Color.Black, 0.22f),
        surfaceBright = lerp(bg, on, 0.12f),
        surfaceContainerLowest = lerp(bg, Color.Black, 0.35f),
        surfaceContainerLow = lerp(bg, on, 0.045f),
        surfaceContainer = lerp(bg, on, 0.07f),
        surfaceContainerHigh = lerp(bg, on, 0.11f),
        surfaceContainerHighest = lerp(bg, on, 0.15f),
    ) else copy(
        surfaceDim = lerp(bg, on, 0.13f),
        surfaceBright = bg,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = lerp(bg, on, 0.025f),
        surfaceContainer = lerp(bg, on, 0.045f),
        surfaceContainerHigh = lerp(bg, on, 0.07f),
        surfaceContainerHighest = lerp(bg, on, 0.10f),
    )
}

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

/** Rounder-than-baseline shape scale, in line with M3 Expressive. */
val NatepadShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NatepadTheme(
    appTheme: AppTheme = AppTheme.MATERIAL_YOU,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = appTheme.resolveColorScheme(darkTheme)
    // Note: material3 1.4.0 keeps MaterialExpressiveTheme/MotionScheme internal
    // (Expressive theme APIs ship publicly in the 1.5 alphas). Components animate
    // with standard M3 motion; NatepadMotion below carries the expressive spring
    // tokens for the app's own animations.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NatepadTypography,
        shapes = NatepadShapes,
        content = content
    )
}

// ── Motion tokens ─────────────────────────────────────────────────────────────
//
// Spring parameters from the M3 Expressive motion spec. material3 1.4.0 keeps its
// MotionScheme API internal, so we carry the same tokens ourselves; our animations
// use these, components keep their built-in M3 motion.
object NatepadMotion {
    /** Bouncy spring for things that move or change size. */
    fun <T> spatialDefault(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Snappier spatial spring for small, frequent movements (press feedback). */
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Non-bouncy spring for fades and color changes. */
    fun <T> effectsDefault(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 1600f)

    /** Fast non-bouncy spring for outgoing content. */
    fun <T> effectsFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 3800f)
}
