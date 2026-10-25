package app.tare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.tare.data.settings.ThemeMode

/**
 * Tare's palette. Warm neutrals (paper in light mode, charcoal in dark) with one colour per
 * quantity: coral for calories, periwinkle for protein, mint for carbs, amber for fat.
 * Those four colours mean the same thing everywhere in the app — never reuse them for chrome.
 */
@Immutable
data class TareColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfacePressed: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val kcal: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val over: Color,
    val track: Color,
    val onAccent: Color,
    val scrim: Color,
)

val DarkColors = TareColors(
    isDark = true,
    background = Color(0xFF0E0D0C),
    surface = Color(0xFF181715),
    surfaceRaised = Color(0xFF211F1D),
    surfacePressed = Color(0xFF2B2926),
    outline = Color(0xFF2F2C29),
    text = Color(0xFFF4F1EC),
    textMuted = Color(0xFFA8A29A),
    textFaint = Color(0xFF6F6A63),
    kcal = Color(0xFFFF7A50),
    protein = Color(0xFF8E9BFF),
    carbs = Color(0xFF4FD6B4),
    fat = Color(0xFFFFC24F),
    over = Color(0xFFFF5370),
    track = Color(0xFF26231F),
    onAccent = Color(0xFF1A0A04),
    scrim = Color(0xCC000000),
)

val LightColors = TareColors(
    isDark = false,
    background = Color(0xFFF6F4F0),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEFECE6),
    surfacePressed = Color(0xFFE6E2DB),
    outline = Color(0xFFE2DDD5),
    text = Color(0xFF1B1A18),
    textMuted = Color(0xFF6C665E),
    textFaint = Color(0xFFA19A90),
    kcal = Color(0xFFEC5B2C),
    protein = Color(0xFF5B6CF0),
    carbs = Color(0xFF14A483),
    fat = Color(0xFFDB9A0E),
    over = Color(0xFFE5304F),
    track = Color(0xFFEAE6DF),
    onAccent = Color(0xFFFFFFFF),
    scrim = Color(0x99000000),
)

val LocalTareColors = staticCompositionLocalOf { DarkColors }
val LocalTareType = staticCompositionLocalOf { TareTypography }

/** Design tokens, read as `Tare.colors.kcal`, `Tare.type.display`. */
object Tare {
    val colors: TareColors
        @Composable @ReadOnlyComposable get() = LocalTareColors.current
    val type: TareType
        @Composable @ReadOnlyComposable get() = LocalTareType.current
}

/** Corner radii: cards 24, rows/fields 16, chips 12, pills full. */
object Radii {
    val card = RoundedCornerShape(24.dp)
    val row = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(percent = 50)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

@Composable
fun TareTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.kcal,
            onPrimary = colors.onAccent,
            // Stock Material components (time picker, dialogs, menus) take these slots; keep
            // them neutral so nothing outside the four quantity colours picks up a hue.
            primaryContainer = colors.surfacePressed,
            onPrimaryContainer = colors.text,
            secondaryContainer = colors.surfacePressed,
            onSecondaryContainer = colors.text,
            tertiaryContainer = colors.surfacePressed,
            onTertiaryContainer = colors.text,
            surfaceTint = colors.surface,
            secondary = colors.protein,
            tertiary = colors.carbs,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textMuted,
            surfaceContainerLowest = colors.background,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfacePressed,
            outline = colors.outline,
            outlineVariant = colors.outline,
            error = colors.over,
            inverseSurface = colors.text,
            inverseOnSurface = colors.background,
            scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.kcal,
            onPrimary = colors.onAccent,
            // Stock Material components (time picker, dialogs, menus) take these slots; keep
            // them neutral so nothing outside the four quantity colours picks up a hue.
            primaryContainer = colors.surfacePressed,
            onPrimaryContainer = colors.text,
            secondaryContainer = colors.surfacePressed,
            onSecondaryContainer = colors.text,
            tertiaryContainer = colors.surfacePressed,
            onTertiaryContainer = colors.text,
            surfaceTint = colors.surface,
            secondary = colors.protein,
            tertiary = colors.carbs,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textMuted,
            surfaceContainerLowest = colors.surface,
            surfaceContainerLow = colors.background,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surfaceRaised,
            outline = colors.outline,
            outlineVariant = colors.outline,
            error = colors.over,
            inverseSurface = colors.text,
            inverseOnSurface = colors.background,
            scrim = colors.scrim,
        )
    }
    CompositionLocalProvider(LocalTareColors provides colors, LocalTareType provides TareTypography) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TareTypography.material,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = Radii.chip,
                medium = Radii.row,
                large = Radii.card,
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
