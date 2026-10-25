package com.yahyafati.poolprint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The colour roles of the Pool Print design (Claude Design project "Pool Print", Modernist system): ink on a warm
 * ground, one lime accent for the primary action, flat surfaces, no corner radius. Names follow the design tokens.
 */
@Immutable
data class PoolColors(
    val bg: Color,
    val on: Color,
    /** Secondary text. */
    val onVariant: Color,
    /** The strong 2dp section rule. */
    val rule: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** The primary action fill. */
    val lime: Color,
    val onLime: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    /** The thin 1dp row divider. */
    val outlineVariant: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val inverse: Color,
    val onInverse: Color,
    val inversePrimary: Color,
    val scrim: Color,
)

val LightPoolColors = PoolColors(
    bg = Color(0xFFFBFBF4),
    on = Color(0xFF1A1C16),
    onVariant = Color(0xFF44483D),
    rule = Color(0xFF1A1C16),
    primary = Color(0xFF4A6600),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F244),
    onPrimaryContainer = Color(0xFF141F00),
    lime = Color(0xFFC8F244),
    onLime = Color(0xFF141F00),
    surfaceLow = Color(0xFFF5F5EE),
    surface = Color(0xFFEFEFE8),
    surfaceHigh = Color(0xFFE3E3DC),
    outline = Color(0xFF75796C),
    outlineVariant = Color(0xFFC5C8B9),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    tertiary = Color(0xFF386663),
    tertiaryContainer = Color(0xFFBBECE7),
    onTertiaryContainer = Color(0xFF00201E),
    inverse = Color(0xFF2F312A),
    onInverse = Color(0xFFF1F1EA),
    inversePrimary = Color(0xFFACD62A),
    scrim = Color(0x66000000),
)

val DarkPoolColors = PoolColors(
    bg = Color(0xFF12140E),
    on = Color(0xFFE3E3DB),
    onVariant = Color(0xFFC5C8B9),
    rule = Color(0xFFE3E3DB),
    primary = Color(0xFFB4E237),
    onPrimary = Color(0xFF253600),
    primaryContainer = Color(0xFF374E00),
    onPrimaryContainer = Color(0xFFD8F98A),
    lime = Color(0xFFB4E237),
    onLime = Color(0xFF1A2600),
    surfaceLow = Color(0xFF1A1C16),
    surface = Color(0xFF1E201A),
    surfaceHigh = Color(0xFF292B24),
    outline = Color(0xFF8F9284),
    outlineVariant = Color(0xFF44483D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFA0D0CB),
    tertiaryContainer = Color(0xFF1F4E4B),
    onTertiaryContainer = Color(0xFFBBECE7),
    inverse = Color(0xFFE3E3DB),
    onInverse = Color(0xFF2F312A),
    inversePrimary = Color(0xFF4A6600),
    scrim = Color(0x99000000),
)

/**
 * The type scale of the design. [sans] is the design's Archivo when it is bundled, the platform sans otherwise;
 * every style is built from these two families, so swapping the font is one change in [PoolPrintTheme].
 */
@Immutable
class PoolTypography(sans: FontFamily, mono: FontFamily) {
    private val base = TextStyle(fontFamily = sans)

    /** Onboarding and result headlines (40). */
    val display = base.copy(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 42.sp, letterSpacing = (-0.02).em)

    /** The home screen's "Print a PDF" (44). */
    val hero = base.copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp, letterSpacing = (-0.025).em)

    /** Step titles (32). */
    val headline = base.copy(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 35.sp, letterSpacing = (-0.015).em)

    /** Top bars and dialog titles (22). */
    val title = base.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp)

    /** The document name on the print sheet (20). */
    val documentTitle = base.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp)

    /** Error block headlines (18). */
    val alert = base.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)

    /** The paragraph under a headline (17). */
    val lead = base.copy(fontSize = 17.sp, lineHeight = 25.sp)
    val body = base.copy(fontSize = 16.sp, lineHeight = 23.sp)
    val bodySmall = base.copy(fontSize = 15.sp, lineHeight = 22.sp)

    /** A list row's main line (16, bold). */
    val rowTitle = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
    val caption = base.copy(fontSize = 13.sp, lineHeight = 18.sp)

    /** Field labels and the small label above a row value. */
    val label = base.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)

    /** Uppercase section labels; the text is uppercased where it is used. */
    val caps = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em, lineHeight = 16.sp)

    /** Badges such as B/W and YOU. */
    val badge = base.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.06.em, lineHeight = 14.sp)
    val button = base.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
    val buttonSmall = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)

    /** Inline text actions ("Save as preset", "Test again"). */
    val action = base.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.sp)
    val mono = TextStyle(fontFamily = mono, fontSize = 16.sp, lineHeight = 24.sp)
    val monoSmall = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 19.sp)
}

val LocalPoolColors = staticCompositionLocalOf { LightPoolColors }
val LocalPoolType = staticCompositionLocalOf { PoolTypography(FontFamily.Default, FontFamily.Monospace) }

object PoolTheme {
    val colors: PoolColors @Composable get() = LocalPoolColors.current
    val type: PoolTypography @Composable get() = LocalPoolType.current
}

@Composable
fun PoolPrintTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkPoolColors else LightPoolColors
    val type = PoolTypography(FontFamily.Default, FontFamily.Monospace)
    val square = RoundedCornerShape(0.dp)
    MaterialTheme(
        colorScheme = colors.toMaterial(dark = colors == DarkPoolColors),
        shapes = Shapes(square, square, square, square, square),
        typography = Typography(bodyLarge = type.body, bodyMedium = type.bodySmall, labelLarge = type.buttonSmall),
    ) {
        CompositionLocalProvider(
            LocalPoolColors provides colors,
            LocalPoolType provides type,
            LocalContentColor provides colors.on,
            LocalTextSelectionColors provides TextSelectionColors(colors.primary, colors.primary.copy(alpha = 0.3f)),
        ) {
            Box(Modifier.fillMaxSize().background(colors.bg)) { content() }
        }
    }
}

/** Material components that are still used (menus, the password field) take their colours from here. */
private fun PoolColors.toMaterial(dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    background = bg,
    onBackground = on,
    surface = bg,
    onSurface = on,
    surfaceVariant = surface,
    onSurfaceVariant = onVariant,
    surfaceContainerLowest = bg,
    surfaceContainerLow = surfaceLow,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceHigh,
    surfaceContainerHighest = surfaceHigh,
    outline = outline,
    outlineVariant = outlineVariant,
    error = error,
    onError = bg,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    tertiary = tertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    inverseSurface = inverse,
    inverseOnSurface = onInverse,
    inversePrimary = inversePrimary,
    scrim = scrim,
)
