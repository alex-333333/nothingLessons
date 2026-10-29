package io.github.alex333333.lessons.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** What the widget should show at a given moment. */
sealed interface WidgetState {
    /** No lessons have been entered yet, or none are coming up at all. */
    data object NotConfigured : WidgetState

    /** [changed]: today follows a one-day change rather than the regular week. */
    data class Lesson(
        val current: Slot,
        val next: Upcoming?,
        val minutesLeft: Int,
        /** How much of the current lesson has passed, 0..1. */
        val progress: Float,
        val changed: Boolean = false,
    ) : WidgetState

    /** Between two lessons of the same day, including free periods. */
    data class Break(val next: Slot, val minutesUntil: Int, val changed: Boolean = false) : WidgetState

    /** Today's lessons have not started yet. */
    data class BeforeSchool(val next: Slot, val minutesUntil: Int, val changed: Boolean = false) : WidgetState

    /** No more lessons today; [next] is the first lesson of the next school day. */
    data class DayOff(val next: Upcoming, val reason: DayOffReason) : WidgetState

    /**
     * Today is inside [holiday]. [daysLeft] counts days until [next], or until the day after
     * the holiday when no lesson is in sight.
     */
    data class Holidays(val holiday: Holiday, val next: Upcoming?, val daysLeft: Int) : WidgetState
}

enum class DayOffReason {
    /** Today's lessons are over. */
    Finished,

    /** A weekend, or a day without lessons. */
    NoLessons,

    /** Today was cancelled ("сегодня не учимся"). */
    Cancelled,
}

object ScheduleCalculator {

    /** How far ahead to look for the next lesson: long enough to get past the summer holidays. */
    private const val SEARCH_DAYS = 400

    fun stateAt(config: Config, now: LocalDateTime): WidgetState {
        if (!config.hasLessons()) return WidgetState.NotConfigured
        val today = now.toLocalDate()
        val time = now.toLocalTime()

        config.holidayOn(today)?.let { holiday ->
            val next = firstLessonAfter(config, today)
            val daysLeft = next?.daysAhead ?: ChronoUnit.DAYS.between(today, holiday.last.plusDays(1)).toInt()
            return WidgetState.Holidays(holiday, next, daysLeft)
        }

        val slots = config.slots(today)
        val changed = config.isChanged(today)

        val current = slots.firstOrNull { !time.isBefore(it.start) && time.isBefore(it.end) }
        if (current != null) {
            val next = slots.firstOrNull { !it.start.isBefore(current.end) }
                ?.let { Upcoming(it, today, 0, changed) }
                ?: firstLessonAfter(config, today)
            val total = Duration.between(current.start, current.end).seconds.toFloat()
            val elapsed = Duration.between(current.start, time).seconds.toFloat()
            return WidgetState.Lesson(
                current = current,
                next = next,
                minutesLeft = minutesUntil(now, today, current.end),
                progress = (elapsed / total).coerceIn(0f, 1f),
                changed = changed,
            )
        }

        val next = slots.firstOrNull { time.isBefore(it.start) }
        if (next != null) {
            val minutes = minutesUntil(now, today, next.start)
            val hadLessonToday = slots.any { !time.isBefore(it.end) }
            return if (hadLessonToday) {
                WidgetState.Break(next, minutes, changed)
            } else {
                WidgetState.BeforeSchool(next, minutes, changed)
            }
        }

        val reason = when {
            config.override(today)?.cancelled == true -> DayOffReason.Cancelled
            slots.isNotEmpty() -> DayOffReason.Finished
            else -> DayOffReason.NoLessons
        }
        // Nothing left to count down to (e.g. the only lessons were a past one-day change).
        val upcoming = firstLessonAfter(config, today) ?: return WidgetState.NotConfigured
        return WidgetState.DayOff(upcoming, reason)
    }

    /**
     * When the widget should redraw next: every minute while the countdown is running,
     * otherwise at midnight when the day changes. Null if there is nothing to show.
     */
    fun nextRefreshAt(config: Config, now: LocalDateTime): LocalDateTime? =
        when (stateAt(config, now)) {
            WidgetState.NotConfigured -> null
            is WidgetState.DayOff, is WidgetState.Holidays -> now.toLocalDate().plusDays(1).atStartOfDay()
            else -> now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        }

    private fun firstLessonAfter(config: Config, today: LocalDate): Upcoming? =
        (1..SEARCH_DAYS).firstNotNullOfOrNull { daysAhead ->
            val date = today.plusDays(daysAhead.toLong())
            config.slots(date).firstOrNull()?.let { Upcoming(it, date, daysAhead, config.isChanged(date)) }
        }

    /** Whole minutes until [target], rounded up so a running countdown never reads zero. */
    private fun minutesUntil(now: LocalDateTime, today: LocalDate, target: LocalTime): Int {
        val seconds = Duration.between(now, today.atTime(target)).seconds
        return ceil(seconds / 60.0).toInt()
    }
}
