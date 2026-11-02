package io.github.alex333333.bridge.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/** Nothing's black / white / red. The red [accent] is only for the primary action and status dots. */
@Immutable
class BridgePalette(
    val background: Color,
    val card: Color,
    val text: Color,
    val secondary: Color,
    val border: Color,
    val muted: Color,
    val dotOff: Color,
    val accent: Color,
    val error: Color,
)

val DarkPalette = BridgePalette(
    background = Color(0xFF000000),
    card = Color(0xFF1B1B1D),
    text = Color(0xFFFFFFFF),
    secondary = Color(0xFF8E8E8E),
    border = Color(0xFF3A3A3A),
    muted = Color(0xFF5A5A5A),
    dotOff = Color(0xFF2E2E30),
    accent = Color(0xFFD71921),
    error = Color(0xFFFF5A5A),
)

val LightPalette = BridgePalette(
    background = Color(0xFFF2F2F4),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF000000),
    secondary = Color(0xFF666666),
    border = Color(0xFFC8C8CC),
    muted = Color(0xFFA0A0A4),
    dotOff = Color(0xFFDADADD),
    accent = Color(0xFFD71921),
    error = Color(0xFFC0262D),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** Bundled fonts wrapped for Compose. Weights are baked into each typeface (variable fonts). */
@Immutable
class BridgeFonts(
    val mono: FontFamily,
    val monoBold: FontFamily,
    val body: FontFamily,
    val bodyMedium: FontFamily,
)

val LocalFonts = staticCompositionLocalOf<BridgeFonts> { error("BridgeTheme is missing") }

/** Martian Mono for labels and Noto Serif for body text, from assets/fonts. */
private fun loadTypeface(context: Context, path: String, weight: Int): Typeface =
    Typeface.Builder(context.assets, path)
        .setFontVariationSettings("'wght' $weight")
        .setWeight(weight)
        .build() ?: Typeface.DEFAULT

@Composable
fun BridgeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    val fonts = remember(context) {
        BridgeFonts(
            mono = FontFamily(loadTypeface(context, "fonts/MartianMono.ttf", 400)),
            monoBold = FontFamily(loadTypeface(context, "fonts/MartianMono.ttf", 700)),
            body = FontFamily(loadTypeface(context, "fonts/NotoSerif.ttf", 400)),
            bodyMedium = FontFamily(loadTypeface(context, "fonts/NotoSerif.ttf", 500)),
        )
    }
    // Only the dialog reads the Material scheme; everything else is drawn from the palette.
    val colors = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.text,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceContainerHigh = palette.card,
            outline = palette.border,
            error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceContainerHigh = palette.card,
            outline = palette.border,
            error = palette.error,
        )
    }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalFonts provides fonts,
            LocalContentColor provides palette.text,
            content = content,
        )
    }
}

/** Small caps labels in Martian Mono. [color] defaults to the palette's secondary text color. */
@Composable
fun monoStyle(size: TextUnit, bold: Boolean = false, color: Color = Color.Unspecified, spacing: Float = 0.08f): TextStyle {
    val fonts = LocalFonts.current
    return TextStyle(
        fontFamily = if (bold) fonts.monoBold else fonts.mono,
        fontSize = size,
        letterSpacing = spacing.em,
        color = if (color == Color.Unspecified) LocalPalette.current.secondary else color,
        fontSynthesis = FontSynthesis.None,
    )
}

/** Body copy in Noto Serif. [color] defaults to the palette's text color. */
@Composable
fun bodyStyle(size: TextUnit, medium: Boolean = false, color: Color = Color.Unspecified): TextStyle {
    val fonts = LocalFonts.current
    return TextStyle(
        fontFamily = if (medium) fonts.bodyMedium else fonts.body,
        fontSize = size,
        color = if (color == Color.Unspecified) LocalPalette.current.text else color,
        fontSynthesis = FontSynthesis.None,
    )
}
