package io.github.alex333333.lessons.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alex333333.lessons.core.DotMatrix
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How a piece of text is drawn into a bitmap. */
class BitmapTextStyle(val typeface: Typeface, val size: Dp, val letterSpacingEm: Float = 0f)

/** A bitmap and the size to show it at: exactly its pixels, so the launcher never rescales it. */
class WidgetImage(val bitmap: Bitmap, val width: Dp, val height: Dp, val description: String)

/**
 * Glance can't use custom fonts, so text and dot-matrix digits are drawn here: white on
 * transparent at the screen density. The widget tints them with a color resource, which the
 * launcher resolves for the light or dark theme.
 */
object WidgetBitmaps {
    private const val UNLIT_ALPHA = 31 // ≈12%: the faint grid behind dot-matrix digits
    private const val TRACK_ALPHA = 46 // ≈18%: the part of the lesson still to go

    /** Width of [text] on a single line, without ellipsizing. */
    fun width(context: Context, text: String, style: BitmapTextStyle): Dp =
        (paint(context, style).measureText(text) / density(context)).dp

    /** One line of [text], ellipsized to [maxWidth]; null when nothing fits or there is no text. */
    fun text(context: Context, text: String, style: BitmapTextStyle, maxWidth: Dp): WidgetImage? {
        if (text.isBlank() || maxWidth <= 0.dp) return null
        val paint = paint(context, style)
        val line = TextUtils.ellipsize(text, paint, px(context, maxWidth), TextUtils.TruncateAt.END).toString()
        if (line.isBlank()) return null
        // Ink can stick out of the advance box or the font's ascent (serifs, "Й"), so grow to fit it.
        val ink = Rect().also { paint.getTextBounds(line, 0, line.length, it) }
        val metrics = paint.fontMetrics
        val left = min(0, ink.left)
        val top = min(metrics.ascent, ink.top.toFloat())
        val right = max(ceil(paint.measureText(line)).toInt(), ink.right)
        val bottom = max(metrics.descent, ink.bottom.toFloat())
        val bitmap = bitmap(right - left, ceil(bottom - top).toInt())
        Canvas(bitmap).drawText(line, -left.toFloat(), -top, paint)
        return image(context, bitmap, text)
    }

    /** [text] wrapped to at most [maxLines] lines of [maxWidth], the last one ellipsized. */
    fun paragraph(context: Context, text: String, style: BitmapTextStyle, maxWidth: Dp, maxLines: Int = 2): WidgetImage? {
        val width = px(context, maxWidth).toInt()
        if (text.isBlank() || width <= 0) return null
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint(context, style), width)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()
        val bitmap = bitmap(width, layout.height)
        layout.draw(Canvas(bitmap))
        return image(context, bitmap, text)
    }

    /**
     * [text] in [DotMatrix] glyphs: lit dots opaque, unlit ones faint. The gap column between
     * glyphs stays empty. Dots shrink if the result would be wider than [maxWidth].
     */
    fun dotMatrix(context: Context, text: String, dot: Dp, gap: Dp, maxWidth: Dp = Dp.Infinity): WidgetImage? {
        if (text.isEmpty()) return null
        val glyphs = text.map { DotMatrix.layout(it.toString()) }
        val columns = glyphs.sumOf { it.width } + glyphs.size - 1
        val k = fit(columns, dot, gap, maxWidth)
        val d = px(context, dot) * k
        val pitch = d + px(context, gap) * k
        val bitmap = bitmap(ceil(columns * pitch - pitch + d).toInt(), ceil(DotMatrix.ROWS * pitch - pitch + d).toInt())
        val canvas = Canvas(bitmap)
        val paint = dotPaint()
        var x0 = 0
        for (grid in glyphs) {
            for (x in 0 until grid.width) {
                for (y in 0 until grid.height) {
                    paint.alpha = if (grid[x, y]) 255 else UNLIT_ALPHA
                    canvas.drawCircle((x0 + x) * pitch + d / 2, y * pitch + d / 2, d / 2, paint)
                }
            }
            x0 += grid.width + 1
        }
        return image(context, bitmap, text)
    }

    /** A row of [count] dots, the first [progress] share of them filled. */
    fun progressDots(context: Context, progress: Float, dot: Dp, gap: Dp, maxWidth: Dp, count: Int = 20): WidgetImage {
        val k = fit(count, dot, gap, maxWidth)
        val d = px(context, dot) * k
        val pitch = d + px(context, gap) * k
        val filled = (progress.coerceIn(0f, 1f) * count).toInt()
        val bitmap = bitmap(ceil(count * pitch - pitch + d).toInt(), ceil(d).toInt())
        val canvas = Canvas(bitmap)
        val paint = dotPaint()
        repeat(count) { i ->
            paint.alpha = if (i < filled) 255 else TRACK_ALPHA
            canvas.drawCircle(i * pitch + d / 2, d / 2, d / 2, paint)
        }
        return image(context, bitmap, "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%")
    }

    /** Scale for [n] dots of [dot] with [gap] between them to fit into [maxWidth]. */
    private fun fit(n: Int, dot: Dp, gap: Dp, maxWidth: Dp): Float {
        val natural = dot * n + gap * (n - 1)
        return if (natural > maxWidth && maxWidth > 0.dp) maxWidth / natural else 1f
    }

    private fun paint(context: Context, style: BitmapTextStyle) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = style.typeface
            textSize = px(context, style.size)
            letterSpacing = style.letterSpacingEm
            color = Color.WHITE
        }

    private fun dotPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private fun bitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)

    private fun image(context: Context, bitmap: Bitmap, description: String): WidgetImage {
        val density = density(context)
        return WidgetImage(bitmap, (bitmap.width / density).dp, (bitmap.height / density).dp, description)
    }

    private fun density(context: Context): Float = context.resources.displayMetrics.density

    private fun px(context: Context, value: Dp): Float = value.value * density(context)
}
