package io.github.alex333333.lessons.core

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * A single date that differs from the regular week: either [cancelled] (no lessons), or a
 * change with its own [bells] and [subjects] (aligned by index, blank = free slot), e.g. a
 * shortened day.
 */
@Serializable
data class DayOverride(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val cancelled: Boolean = false,
    val bells: List<Bell> = emptyList(),
    val subjects: List<String> = emptyList(),
) {
    fun slots(): List<Slot> = if (cancelled) emptyList() else slotsOf(bells, subjects)
}

/** School holidays from [start] to [end], both inclusive. Swapped ends are read as a range. */
@Serializable
data class Holiday(
    @Serializable(with = LocalDateSerializer::class) val start: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val end: LocalDate,
) {
    val first: LocalDate get() = minOf(start, end)
    val last: LocalDate get() = maxOf(start, end)

    operator fun contains(date: LocalDate): Boolean = date in first..last
}

/** Lessons on [date]: holidays win, then a cancellation or one-day change, then the regular week. */
fun Config.slots(date: LocalDate): List<Slot> = when {
    holidayOn(date) != null -> emptyList()
    else -> override(date)?.slots() ?: slots(date.dayOfWeek)
}

/** True when [date] follows a one-day change (not a cancellation, not a holiday). */
fun Config.isChanged(date: LocalDate): Boolean =
    holidayOn(date) == null && override(date)?.cancelled == false

fun Config.override(date: LocalDate): DayOverride? = overrides.firstOrNull { it.date == date }

fun Config.holidayOn(date: LocalDate): Holiday? = holidays.firstOrNull { date in it }

/** Replaces any change or cancellation on the same date. */
fun Config.withOverride(override: DayOverride): Config =
    copy(overrides = (overrides.filter { it.date != override.date } + override).sortedBy { it.date })

fun Config.withoutOverride(date: LocalDate): Config = copy(overrides = overrides.filter { it.date != date })

/**
 * Cancelling replaces whatever was set for that day; un-cancelling only removes a
 * cancellation and leaves a one-day change alone.
 */
fun Config.withCancelled(date: LocalDate, cancelled: Boolean): Config = when {
    cancelled -> withOverride(DayOverride(date, cancelled = true))
    override(date)?.cancelled == true -> withoutOverride(date)
    else -> this
}

fun Config.withHoliday(holiday: Holiday): Config =
    if (holiday in holidays) this else copy(holidays = (holidays + holiday).sortedBy { it.first })

fun Config.withoutHoliday(holiday: Holiday): Config = copy(holidays = holidays - holiday)

/** The regular lessons of [date] as an editable change, padded or cut to the bells. */
fun Config.regularDay(date: LocalDate): DayOverride {
    val weekly = if (isSchoolDay(date.dayOfWeek)) subjects(date.dayOfWeek) else emptyList()
    return DayOverride(date, bells = bells, subjects = List(bells.size) { weekly.getOrNull(it).orEmpty() })
}

/** Drops changes before [today] and holidays that are already over. */
fun Config.pruned(today: LocalDate): Config = copy(
    overrides = overrides.filter { !it.date.isBefore(today) },
    holidays = holidays.filter { !it.last.isBefore(today) },
)
