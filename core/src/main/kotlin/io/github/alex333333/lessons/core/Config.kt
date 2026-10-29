package io.github.alex333333.lessons.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

/** One bell slot: when a lesson starts and ends. The same slots are used every day. */
@Serializable
data class Bell(
    @Serializable(with = LocalTimeSerializer::class) val start: LocalTime,
    @Serializable(with = LocalTimeSerializer::class) val end: LocalTime,
)

/**
 * Everything the user sets up in the settings screen.
 *
 * [week] has one entry per day, Monday first. Each entry lists subjects by bell index;
 * a blank or missing subject means there is no lesson in that slot.
 */
@Serializable
data class Config(
    val bells: List<Bell> = emptyList(),
    val week: List<List<String>> = List(7) { emptyList() },
    val sundayEnabled: Boolean = false,
    val font: FontChoice = FontChoice.NotoSerif,
    val weight: FontWeightChoice = FontWeightChoice.Regular,
    /** One-day changes and cancellations, sorted by date. See Calendar.kt. */
    val overrides: List<DayOverride> = emptyList(),
    /** School holidays, sorted by start. */
    val holidays: List<Holiday> = emptyList(),
) {
    fun subjects(day: DayOfWeek): List<String> = week.getOrNull(day.value - 1).orEmpty()

    fun withSubjects(day: DayOfWeek, subjects: List<String>): Config =
        copy(week = normalizedWeek().toMutableList().also { it[day.value - 1] = subjects })

    fun isSchoolDay(day: DayOfWeek): Boolean = day != DayOfWeek.SUNDAY || sundayEnabled

    /** Lessons with a subject on [day], in bell order. */
    fun slots(day: DayOfWeek): List<Slot> {
        if (!isSchoolDay(day)) return emptyList()
        return slotsOf(bells, subjects(day))
    }

    /** Whether there is anything to show: regular lessons, or a one-day change with lessons. */
    fun hasLessons(): Boolean =
        DayOfWeek.entries.any { slots(it).isNotEmpty() } ||
            overrides.any { !it.cancelled && it.slots().isNotEmpty() }

    internal fun normalizedWeek(): List<List<String>> = List(7) { week.getOrNull(it).orEmpty() }
}

/** Typeface for subject names on the widget and in the app. */
@Serializable
enum class FontChoice { NotoSerif, RobotoSerif, Inter }

/** Weight of subject names; bold reads loud, so regular is the default. */
@Serializable
enum class FontWeightChoice(val weight: Int) { Regular(400), Medium(500), Bold(700) }

/** A lesson on a particular day. [number] is the 1-based bell number shown to the user. */
data class Slot(
    val number: Int,
    val subject: String,
    val start: LocalTime,
    val end: LocalTime,
)

/**
 * A lesson that is coming up on [date], [daysAhead] days from today (0 = today).
 * [changed] when that day follows a one-day change rather than the regular week.
 */
data class Upcoming(
    val slot: Slot,
    val date: LocalDate,
    val daysAhead: Int,
    val changed: Boolean = false,
) {
    val day: DayOfWeek get() = date.dayOfWeek
}

/** Lessons with a subject, in bell order; blank subjects are free slots. */
internal fun slotsOf(bells: List<Bell>, subjects: List<String>): List<Slot> =
    bells.mapIndexedNotNull { index, bell ->
        val subject = subjects.getOrNull(index)?.trim().orEmpty()
        if (subject.isEmpty()) null else Slot(index + 1, subject, bell.start, bell.end)
    }

enum class BellError { EndBeforeStart, OverlapsPrevious }

/** Problems with the bell list, keyed by bell index. Empty when everything is fine. */
fun validateBells(bells: List<Bell>): Map<Int, BellError> = buildMap {
    bells.forEachIndexed { index, bell ->
        when {
            !bell.end.isAfter(bell.start) -> put(index, BellError.EndBeforeStart)
            index > 0 && bell.start.isBefore(bells[index - 1].end) -> put(index, BellError.OverlapsPrevious)
        }
    }
}

private const val DEFAULT_LESSON_MINUTES = 45
private const val DEFAULT_BREAK_MINUTES = 10
private const val LAST_MINUTE_OF_DAY = 23 * 60 + 59

/** A sensible next bell: a 10-minute break after the last lesson, then 45 minutes. */
fun suggestNextBell(bells: List<Bell>): Bell {
    val last = bells.lastOrNull() ?: return Bell(LocalTime.of(8, 30), LocalTime.of(9, 15))
    val start = minutesOfDay(last.end) + DEFAULT_BREAK_MINUTES
    return Bell(timeOf(start), timeOf(start + DEFAULT_LESSON_MINUTES))
}

private fun minutesOfDay(time: LocalTime) = time.hour * 60 + time.minute

private fun timeOf(minutes: Int): LocalTime {
    val clamped = minutes.coerceAtMost(LAST_MINUTE_OF_DAY)
    return LocalTime.of(clamped / 60, clamped % 60)
}
