package io.github.alex333333.lessons.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** What the widget should show at a given moment. */
sealed interface WidgetState {
    /** No lessons have been entered yet. */
    data object NotConfigured : WidgetState

    data class Lesson(
        val current: Slot,
        val next: Upcoming?,
        val minutesLeft: Int,
        /** How much of the current lesson has passed, 0..1. */
        val progress: Float,
    ) : WidgetState

    /** Between two lessons of the same day, including free periods. */
    data class Break(val next: Slot, val minutesUntil: Int) : WidgetState

    /** Today's lessons have not started yet. */
    data class BeforeSchool(val next: Slot, val minutesUntil: Int) : WidgetState

    /** No more lessons today; [next] is the first lesson of the next school day. */
    data class DayOff(val next: Upcoming) : WidgetState
}

object ScheduleCalculator {

    fun stateAt(config: Config, now: LocalDateTime): WidgetState {
        if (!config.hasLessons()) return WidgetState.NotConfigured
        val today = now.toLocalDate()
        val time = now.toLocalTime()
        val slots = config.slots(now.dayOfWeek)

        val current = slots.firstOrNull { !time.isBefore(it.start) && time.isBefore(it.end) }
        if (current != null) {
            val next = slots.firstOrNull { !it.start.isBefore(current.end) }
                ?.let { Upcoming(it, now.dayOfWeek, 0) }
                ?: firstLessonAfter(config, today)
            val total = Duration.between(current.start, current.end).seconds.toFloat()
            val elapsed = Duration.between(current.start, time).seconds.toFloat()
            return WidgetState.Lesson(
                current = current,
                next = next,
                minutesLeft = minutesUntil(now, today, current.end),
                progress = (elapsed / total).coerceIn(0f, 1f),
            )
        }

        val next = slots.firstOrNull { time.isBefore(it.start) }
        if (next != null) {
            val minutes = minutesUntil(now, today, next.start)
            val hadLessonToday = slots.any { !time.isBefore(it.end) }
            return if (hadLessonToday) WidgetState.Break(next, minutes) else WidgetState.BeforeSchool(next, minutes)
        }

        // hasLessons() guarantees a lesson within the next seven days.
        return WidgetState.DayOff(firstLessonAfter(config, today)!!)
    }

    /**
     * When the widget should redraw next: every minute while the countdown is running,
     * otherwise at midnight when the day changes. Null if there is nothing to show.
     */
    fun nextRefreshAt(config: Config, now: LocalDateTime): LocalDateTime? =
        when (stateAt(config, now)) {
            WidgetState.NotConfigured -> null
            is WidgetState.DayOff -> now.toLocalDate().plusDays(1).atStartOfDay()
            else -> now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        }

    private fun firstLessonAfter(config: Config, today: LocalDate): Upcoming? =
        (1..7).firstNotNullOfOrNull { daysAhead ->
            val day = today.plusDays(daysAhead.toLong()).dayOfWeek
            config.slots(day).firstOrNull()?.let { Upcoming(it, day, daysAhead) }
        }

    /** Whole minutes until [target], rounded up so a running countdown never reads zero. */
    private fun minutesUntil(now: LocalDateTime, today: LocalDate, target: LocalTime): Int {
        val seconds = Duration.between(now, today.atTime(target)).seconds
        return ceil(seconds / 60.0).toInt()
    }
}
