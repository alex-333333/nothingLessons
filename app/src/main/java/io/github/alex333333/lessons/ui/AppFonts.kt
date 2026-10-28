package io.github.alex333333.lessons.ui

import android.content.Context
import android.graphics.Typeface
import io.github.alex333333.lessons.core.FontChoice
import java.util.concurrent.ConcurrentHashMap

/**
 * Bundled OFL fonts (assets/fonts, subset to Latin + Cyrillic). Glance can't use custom
 * fonts, so the widget draws text into bitmaps with these typefaces; the settings screen
 * wraps the same typefaces for Compose.
 */
object AppFonts {
    /** Monospace for small caps labels ("СЕЙЧАС", "ДАЛЕЕ"). Pinned to 75% width. */
    const val MONO = "fonts/MartianMono.ttf"

    private val cache = ConcurrentHashMap<String, Typeface>()

    fun path(font: FontChoice): String = when (font) {
        FontChoice.NotoSerif -> "fonts/NotoSerif.ttf"
        FontChoice.RobotoSerif -> "fonts/RobotoSerif.ttf"
        FontChoice.Inter -> "fonts/Inter.ttf"
    }

    /** Typeface for subject names in [font] at CSS-style [weight] (100–900). */
    fun subject(context: Context, font: FontChoice, weight: Int): Typeface = load(context, path(font), weight)

    fun mono(context: Context, weight: Int = 400): Typeface = load(context, MONO, weight)

    /** Display name shown in settings. */
    fun displayName(font: FontChoice): String = when (font) {
        FontChoice.NotoSerif -> "Noto Serif"
        FontChoice.RobotoSerif -> "Roboto Serif"
        FontChoice.Inter -> "Inter"
    }

    private fun load(context: Context, path: String, weight: Int): Typeface =
        cache.getOrPut("$path@$weight") {
            Typeface.Builder(context.assets, path)
                .setFontVariationSettings("'wght' $weight")
                .setWeight(weight)
                .build() ?: Typeface.DEFAULT
        }
}
