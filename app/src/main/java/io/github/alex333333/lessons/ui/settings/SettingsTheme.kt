package io.github.alex333333.lessons.ui.settings

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.alex333333.lessons.core.FontChoice
import io.github.alex333333.lessons.core.FontWeightChoice
import io.github.alex333333.lessons.ui.AppFonts

/** The settings screen is always dark, in Nothing's black / white / red. */
internal object Palette {
    val Background = Color(0xFF000000)
    val Card = Color(0xFF111111)
    val Border = Color(0xFF3A3A3A)
    val Divider = Color(0xFF1F1F1F)
    val Muted = Color(0xFF5A5A5A)
    val Placeholder = Color(0xFF6E6E6E)
    val DotOff = Color(0xFF1A1A1A)
    val Text = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF8E8E8E)
    val Accent = Color(0xFFD71921)
    val Error = Color(0xFFFF5A5A)
}

/** Bundled fonts wrapped for Compose. Weights are baked into each typeface (variable fonts). */
@Immutable
internal class SettingsFonts(
    val mono: FontFamily,
    val monoBold: FontFamily,
    val body: FontFamily,
    val bodyMedium: FontFamily,
)

internal val LocalSettingsFonts = staticCompositionLocalOf<SettingsFonts> { error("SettingsTheme is missing") }

@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val fonts = remember(context) {
        SettingsFonts(
            mono = FontFamily(AppFonts.mono(context, 400)),
            monoBold = FontFamily(AppFonts.mono(context, 700)),
            body = FontFamily(AppFonts.subject(context, FontChoice.RobotoSerif, 400)),
            bodyMedium = FontFamily(AppFonts.subject(context, FontChoice.RobotoSerif, 500)),
        )
    }
    // Only the time picker dialog and text selection read the Material scheme.
    val colors = darkColorScheme(
        primary = Palette.Accent,
        onPrimary = Palette.Text,
        primaryContainer = Palette.Text,
        onPrimaryContainer = Palette.Background,
        background = Palette.Background,
        onBackground = Palette.Text,
        surface = Palette.Card,
        onSurface = Palette.Text,
        surfaceVariant = Palette.Divider,
        onSurfaceVariant = Palette.Secondary,
        surfaceTint = Color.Transparent,
        surfaceContainerHigh = Palette.Card,
        surfaceContainerHighest = Palette.Divider,
        outline = Palette.Border,
        outlineVariant = Palette.Divider,
        error = Palette.Error,
    )
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalSettingsFonts provides fonts,
            LocalContentColor provides Palette.Text,
            content = content,
        )
    }
}

/** Typeface for subject names in the user's chosen font and weight. */
@Composable
internal fun rememberSubjectFont(font: FontChoice, weight: FontWeightChoice): FontFamily {
    val context = LocalContext.current
    return remember(context, font, weight) { FontFamily(AppFonts.subject(context, font, weight.weight)) }
}

/** Small caps labels in Martian Mono. */
@Composable
@ReadOnlyComposable
internal fun monoStyle(
    size: TextUnit,
    bold: Boolean = false,
    color: Color = Palette.Secondary,
    spacing: Float = 0.08f,
): TextStyle {
    val fonts = LocalSettingsFonts.current
    return TextStyle(
        fontFamily = if (bold) fonts.monoBold else fonts.mono,
        fontSize = size,
        letterSpacing = spacing.em,
        color = color,
        fontSynthesis = FontSynthesis.None,
    )
}

/** Body copy in Roboto Serif. */
@Composable
@ReadOnlyComposable
internal fun bodyStyle(size: TextUnit, medium: Boolean = false, color: Color = Palette.Text): TextStyle {
    val fonts = LocalSettingsFonts.current
    return TextStyle(
        fontFamily = if (medium) fonts.bodyMedium else fonts.body,
        fontSize = size,
        color = color,
        fontSynthesis = FontSynthesis.None,
    )
}

/** Subject names in a typeface from [rememberSubjectFont]. */
internal fun subjectStyle(family: FontFamily, size: TextUnit, color: Color = Palette.Text): TextStyle =
    TextStyle(fontFamily = family, fontSize = size, color = color, fontSynthesis = FontSynthesis.None)
