package io.github.alex333333.lessons.widget

import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import io.github.alex333333.lessons.R
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.core.Countdown
import io.github.alex333333.lessons.core.CountdownUnit
import io.github.alex333333.lessons.core.DayOffReason
import io.github.alex333333.lessons.core.Upcoming
import io.github.alex333333.lessons.core.WidgetState
import io.github.alex333333.lessons.ui.AppFonts
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Widgets at least this wide get the 4×2 layout. */
private val WIDE_MIN_WIDTH = 220.dp

/** Height the base sizes below are tuned for; other heights scale them. */
private val BASE_HEIGHT = 170.dp

@Composable
fun LessonWidgetContent(state: WidgetState, config: Config) {
    val size = LocalSize.current
    val s = (size.height / BASE_HEIGHT).coerceIn(0.8f, 1.35f)
    val width = if (size.width > 0.dp) size.width else BASE_HEIGHT
    val pen = Pen(LocalContext.current, s, config)
    val face = describe(state)
    val padding = pen.dp(14f)
    val modifier = GlanceModifier.fillMaxSize()
        .background(ImageProvider(R.drawable.widget_background))
        .padding(padding)
    if (width >= WIDE_MIN_WIDTH) {
        Wide(face, pen, width - padding * 2, modifier)
    } else {
        Compact(face, pen, width - padding * 2, modifier)
    }
}

/** What the widget says, independent of its size. Wide variants fall back to the short ones. */
private class Face(
    val live: Boolean,
    val status: String,
    val wideStatus: String = status,
    val current: String = "",
    val progress: Float? = null,
    /** Dots in the 4×2 progress row: 20 for a lesson, one per day for holidays. */
    val progressDots: Int = 20,
    val digits: String,
    val digitsDim: Boolean = false,
    /** Lines next to the digits in 2×2; joined into one under them in 4×2. */
    val unit: List<String> = emptyList(),
    val nextLabel: String = "",
    val wideNextLabel: String = nextLabel,
    val next: String,
    /** [next] is a hint rather than a subject: smaller, and may wrap. */
    val nextIsHint: Boolean = false,
)

@Composable
private fun Compact(face: Face, pen: Pen, inner: Dp, modifier: GlanceModifier) {
    Column(modifier) {
        StatusRow(face, pen, inner, wide = false)
        Spacer(GlanceModifier.defaultWeight())

        val units = face.unit.map { pen.text(it, pen.mono(8f), inner / 2) }
        val unitWidth = units.maxOfOrNull { it?.width ?: 0.dp } ?: 0.dp
        val gap = if (unitWidth > 0.dp) pen.dp(8f) else 0.dp
        Row(verticalAlignment = Alignment.Bottom) {
            Picture(pen.digits(face.digits, 5f, 1.5f, inner - gap - unitWidth), digitsTint(face))
            if (unitWidth > 0.dp) {
                Spacer(GlanceModifier.width(gap))
                Column { units.forEach { Picture(it) } }
            }
        }
        Spacer(GlanceModifier.defaultWeight())

        Picture(pen.text(face.nextLabel, pen.mono(9f), inner))
        Spacer(GlanceModifier.height(pen.dp(2f)))
        Next(face, pen, inner, size = 20f)
    }
}

@Composable
private fun Wide(face: Face, pen: Pen, inner: Dp, modifier: GlanceModifier) {
    // The right column is laid out first: the left one gets whatever width is left.
    val unit = pen.text(face.unit.joinToString(" "), pen.mono(8f), inner * 0.45f)
    val digits = pen.digits(face.digits, 7f, 2.5f, inner * 0.45f)
    val gap = pen.dp(14f)
    val left = inner - gap - max(unit?.width ?: 0.dp, digits?.width ?: 0.dp)

    Row(modifier) {
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            StatusRow(face, pen, left, wide = true)
            if (face.current.isNotEmpty()) {
                Spacer(GlanceModifier.height(pen.dp(6f)))
                Picture(pen.text(face.current, pen.subject(14f), left), R.color.widget_text)
            }
            face.progress?.let {
                Spacer(GlanceModifier.height(pen.dp(8f)))
                Picture(pen.progress(it, face.progressDots, left), R.color.widget_text)
            }
            Spacer(GlanceModifier.defaultWeight())
            val label = pen.mono(9f)
            Picture(pen.text(pen.firstFitting(label, left, face.wideNextLabel, face.nextLabel), label, left))
            Spacer(GlanceModifier.height(pen.dp(2f)))
            Next(face, pen, left, size = 24f)
        }
        Spacer(GlanceModifier.width(gap))
        Column(
            modifier = GlanceModifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.End,
        ) {
            Picture(digits, digitsTint(face))
            if (unit != null) {
                Spacer(GlanceModifier.height(pen.dp(8f)))
                Picture(unit)
            }
        }
    }
}

/** Status dot and label; the 2×2 layout also squeezes the current subject in after them. */
@Composable
private fun StatusRow(face: Face, pen: Pen, maxWidth: Dp, wide: Boolean) {
    val dot = pen.dp(6f)
    val gap = pen.dp(6f)
    val style = pen.mono(9f)
    val status = if (wide) pen.firstFitting(style, maxWidth - dot - gap, face.wideStatus, face.status) else face.status
    val label = pen.text(status, style, maxWidth - dot - gap)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(if (face.live) R.drawable.dot_accent else R.drawable.dot_sub),
            contentDescription = null,
            modifier = GlanceModifier.size(dot),
        )
        Spacer(GlanceModifier.width(gap))
        Picture(label)
        if (!wide && label != null) {
            val subject = pen.text(face.current, pen.subject(11f), maxWidth - dot - gap * 2 - label.width)
            if (subject != null) {
                Spacer(GlanceModifier.width(gap))
                Picture(subject, R.color.widget_text)
            }
        }
    }
}

/** The next lesson: the biggest text on the widget. */
@Composable
private fun Next(face: Face, pen: Pen, maxWidth: Dp, size: Float) {
    val image = if (face.nextIsHint) {
        pen.paragraph(face.next, pen.subject(13f), maxWidth)
    } else {
        pen.text(face.next, pen.subject(size), maxWidth)
    }
    Picture(image, R.color.widget_text)
}

/** A [WidgetImage] at its exact size, tinted with a theme-aware color resource. */
@Composable
private fun Picture(image: WidgetImage?, @ColorRes tint: Int = R.color.widget_sub) {
    image ?: return
    Image(
        provider = ImageProvider(image.bitmap),
        contentDescription = image.description,
        modifier = GlanceModifier.size(image.width, image.height),
        colorFilter = ColorFilter.tint(ColorProvider(tint)),
    )
}

private fun digitsTint(face: Face) = if (face.digitsDim) R.color.widget_sub else R.color.widget_text

/** Draws text in the widget's fonts at base sizes scaled by [s]. */
private class Pen(private val context: Context, private val s: Float, config: Config) {
    private val monoFace = AppFonts.mono(context)
    private val subjectFace = AppFonts.subject(context, config.font, config.weight.weight)

    fun dp(base: Float): Dp = (base * s).dp

    fun mono(size: Float) = BitmapTextStyle(monoFace, dp(size), letterSpacingEm = 0.08f)

    fun subject(size: Float) = BitmapTextStyle(subjectFace, dp(size))

    fun text(text: String, style: BitmapTextStyle, maxWidth: Dp) = WidgetBitmaps.text(context, text, style, maxWidth)

    fun paragraph(text: String, style: BitmapTextStyle, maxWidth: Dp) =
        WidgetBitmaps.paragraph(context, text, style, maxWidth)

    fun digits(text: String, dot: Float, gap: Float, maxWidth: Dp) =
        WidgetBitmaps.dotMatrix(context, text, dp(dot), dp(gap), maxWidth)

    fun progress(progress: Float, count: Int, maxWidth: Dp) =
        WidgetBitmaps.progressDots(context, progress, dp(4.5f), dp(3.5f), maxWidth, count)

    /** The first of [texts] that fits on one line, else the last one (to be ellipsized). */
    fun firstFitting(style: BitmapTextStyle, maxWidth: Dp, vararg texts: String): String =
        texts.firstOrNull { WidgetBitmaps.width(context, it, style) <= maxWidth } ?: texts.last()
}

private fun describe(state: WidgetState): Face = when (state) {
    WidgetState.NotConfigured -> Face(
        live = true,
        status = "УРОКИ",
        digits = "--",
        digitsDim = true,
        next = "Нажми, чтобы добавить расписание",
        nextIsHint = true,
    )

    is WidgetState.Lesson -> {
        val current = state.current
        val countdown = Countdown.of(state.minutesLeft)
        Face(
            live = true,
            status = "СЕЙЧАС",
            wideStatus = "СЕЙЧАС · ${current.number} УРОК · ${hhmm(current.start)}–${hhmm(current.end)}",
            current = current.subject,
            progress = state.progress,
            digits = countdown.text,
            unit = listOf(unitName(countdown), "ДО ЗВОНКА"),
            nextLabel = state.next?.let { upcomingLabel(it, withNumber = false) } ?: "ДАЛЕЕ",
            wideNextLabel = state.next?.let { upcomingLabel(it, withNumber = true) } ?: "ДАЛЕЕ",
            next = state.next?.slot?.subject ?: "—",
        )
    }

    is WidgetState.Break -> {
        val countdown = Countdown.of(state.minutesUntil)
        Face(
            live = true,
            status = "ПЕРЕМЕНА",
            wideStatus = "ПЕРЕМЕНА · ДО ${hhmm(state.next.start)}",
            digits = countdown.text,
            unit = listOf(unitName(countdown), "ДО УРОКА"),
            nextLabel = "ДАЛЕЕ · ${hhmm(state.next.start)}",
            wideNextLabel = "ДАЛЕЕ · ${state.next.number} УРОК · ${hhmm(state.next.start)}",
            next = state.next.subject,
        )
    }

    is WidgetState.BeforeSchool -> {
        val countdown = Countdown.of(state.minutesUntil)
        Face(
            live = true,
            status = "ДО НАЧАЛА",
            digits = countdown.text,
            unit = listOf(unitName(countdown), "ДО УРОКА"),
            nextLabel = "ПЕРВЫЙ · ${hhmm(state.next.start)}",
            next = state.next.subject,
        )
    }

    is WidgetState.DayOff -> {
        val next = state.next
        val start = next.slot.start
        val tomorrow = next.daysAhead == 1
        Face(
            live = false,
            status = when (state.reason) {
                DayOffReason.Cancelled -> "УРОКИ ОТМЕНЕНЫ"
                DayOffReason.Finished -> "НА СЕГОДНЯ ВСЁ"
                DayOffReason.NoLessons -> "ВЫХОДНОЙ"
            },
            digits = "${start.hour}:${pad2(start.minute)}",
            unit = listOf("НАЧАЛО"),
            nextLabel = "${if (tomorrow) "ЗАВТРА" else dayName(next)} · ${next.slot.number} УРОК",
            wideNextLabel = listOfNotNull(
                "ЗАВТРА".takeIf { tomorrow },
                dayName(next),
                "${next.slot.number} УРОК",
            ).joinToString(" · "),
            next = next.slot.subject,
        )
    }

    is WidgetState.Holidays -> {
        val next = state.next
        val holiday = state.holiday
        // One dot per holiday day (at most 20), filled up to and including today.
        val total = ChronoUnit.DAYS.between(holiday.first, holiday.last).toInt() + 1
        val passed = ChronoUnit.DAYS.between(holiday.first, LocalDate.now()).toInt() + 1
        val label = next?.let { "${dayName(it.day)} · ${shortDate(it.date)} · ${it.slot.number} УРОК" } ?: "УЧЁБА"
        Face(
            live = false,
            status = "КАНИКУЛЫ · ДО ${shortDate(holiday.last)}",
            progress = passed.toFloat() / total,
            progressDots = total.coerceAtMost(20),
            digits = state.daysLeft.toString(),
            unit = listOf("ДН.", "ДО УЧЁБЫ"),
            nextLabel = label,
            next = next?.slot?.subject ?: "—",
        )
    }
}

/** "ДАЛЕЕ · 12:05", "ЗАВТРА · 08:30" or "ПН · 08:30"; [withNumber] adds the bell number. */
private fun upcomingLabel(upcoming: Upcoming, withNumber: Boolean): String {
    val day = when (upcoming.daysAhead) {
        0 -> "ДАЛЕЕ"
        1 -> "ЗАВТРА"
        else -> dayName(upcoming)
    }
    val number = if (withNumber) "${upcoming.slot.number} УРОК" else null
    return listOfNotNull(day, number, hhmm(upcoming.slot.start)).joinToString(" · ")
}

private fun unitName(countdown: Countdown) = when (countdown.unit) {
    CountdownUnit.Minutes -> "МИН"
    CountdownUnit.Hours -> "ЧАС"
}

/** "ПН" within the coming week, else a date like "10 НОЯ" so it isn't mistaken for this week. */
private fun dayName(upcoming: Upcoming) =
    if (upcoming.daysAhead <= 6) dayName(upcoming.day) else shortDate(upcoming.date)

private val monthNames = listOf("ЯНВ", "ФЕВ", "МАР", "АПР", "МАЯ", "ИЮН", "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК")

private fun shortDate(date: LocalDate) = "${date.dayOfMonth} ${monthNames[date.monthValue - 1]}"

private fun dayName(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> "ПН"
    DayOfWeek.TUESDAY -> "ВТ"
    DayOfWeek.WEDNESDAY -> "СР"
    DayOfWeek.THURSDAY -> "ЧТ"
    DayOfWeek.FRIDAY -> "ПТ"
    DayOfWeek.SATURDAY -> "СБ"
    DayOfWeek.SUNDAY -> "ВС"
}

private fun hhmm(time: LocalTime) = "${pad2(time.hour)}:${pad2(time.minute)}"

private fun pad2(value: Int) = value.toString().padStart(2, '0')
