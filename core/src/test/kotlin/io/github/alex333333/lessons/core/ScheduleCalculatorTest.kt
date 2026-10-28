package io.github.alex333333.lessons.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleCalculatorTest {

    private val bells = listOf(
        Bell(LocalTime.of(8, 30), LocalTime.of(9, 15)),
        Bell(LocalTime.of(9, 25), LocalTime.of(10, 10)),
        Bell(LocalTime.of(10, 20), LocalTime.of(11, 5)),
        Bell(LocalTime.of(11, 15), LocalTime.of(11, 55)),
    )

    private val config = Config(bells = bells)
        .withSubjects(DayOfWeek.MONDAY, listOf("Математика", "Русский", "История", "Алгебра"))
        // An empty middle slot is a free period ("окно").
        .withSubjects(DayOfWeek.TUESDAY, listOf("Физика", "", "Химия"))
        // Wednesday has no lessons at all.
        .withSubjects(DayOfWeek.THURSDAY, listOf("", "Биология"))
        .withSubjects(DayOfWeek.FRIDAY, listOf("Литература"))

    // 2026-10-26 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        LocalDateTime.of(LocalDate.of(2026, 10, day), LocalTime.of(hour, minute, second))

    @Test
    fun `empty config is not configured`() {
        assertEquals(WidgetState.NotConfigured, ScheduleCalculator.stateAt(Config(), at(26, 10, 0)))
        assertEquals(
            WidgetState.NotConfigured,
            ScheduleCalculator.stateAt(Config(bells = bells), at(26, 10, 0)),
        )
    }

    @Test
    fun `lesson in progress shows current, next, minutes left and progress`() {
        val state = ScheduleCalculator.stateAt(config, at(26, 9, 30)) as WidgetState.Lesson
        assertEquals(Slot(2, "Русский", LocalTime.of(9, 25), LocalTime.of(10, 10)), state.current)
        assertEquals(Upcoming(Slot(3, "История", LocalTime.of(10, 20), LocalTime.of(11, 5)), DayOfWeek.MONDAY, 0), state.next)
        assertEquals(40, state.minutesLeft)
        assertEquals(5f / 45f, state.progress, 0.0001f)
    }

    @Test
    fun `lesson starts exactly at the bell`() {
        val state = ScheduleCalculator.stateAt(config, at(26, 11, 15)) as WidgetState.Lesson
        assertEquals("Алгебра", state.current.subject)
        assertEquals(40, state.minutesLeft)
        assertEquals(0f, state.progress, 0.0001f)
    }

    @Test
    fun `minutes left round up so a running lesson never shows zero`() {
        val state = ScheduleCalculator.stateAt(config, at(26, 11, 54, 30)) as WidgetState.Lesson
        assertEquals(1, state.minutesLeft)
    }

    @Test
    fun `last lesson of the day points to the next school day`() {
        val state = ScheduleCalculator.stateAt(config, at(26, 11, 30)) as WidgetState.Lesson
        assertEquals(Upcoming(Slot(1, "Физика", LocalTime.of(8, 30), LocalTime.of(9, 15)), DayOfWeek.TUESDAY, 1), state.next)
    }

    @Test
    fun `lesson ends exactly at the bell and break begins`() {
        val state = ScheduleCalculator.stateAt(config, at(26, 9, 15)) as WidgetState.Break
        assertEquals("Русский", state.next.subject)
        assertEquals(10, state.minutesUntil)
    }

    @Test
    fun `free period in the middle of the day is a break until the next real lesson`() {
        val state = ScheduleCalculator.stateAt(config, at(27, 9, 40)) as WidgetState.Break
        assertEquals(Slot(3, "Химия", LocalTime.of(10, 20), LocalTime.of(11, 5)), state.next)
        assertEquals(40, state.minutesUntil)
    }

    @Test
    fun `morning before the first lesson`() {
        val state = ScheduleCalculator.stateAt(config, at(26, 7, 45)) as WidgetState.BeforeSchool
        assertEquals("Математика", state.next.subject)
        assertEquals(45, state.minutesUntil)
    }

    @Test
    fun `day starting with a free period counts as before school`() {
        val state = ScheduleCalculator.stateAt(config, at(29, 8, 40)) as WidgetState.BeforeSchool
        assertEquals(Slot(2, "Биология", LocalTime.of(9, 25), LocalTime.of(10, 10)), state.next)
        assertEquals(45, state.minutesUntil)
    }

    @Test
    fun `after the last lesson the next school day is shown`() {
        val state = ScheduleCalculator.stateAt(config, at(26, 15, 0)) as WidgetState.DayOff
        assertEquals(Upcoming(Slot(1, "Физика", LocalTime.of(8, 30), LocalTime.of(9, 15)), DayOfWeek.TUESDAY, 1), state.next)
    }

    @Test
    fun `day without lessons skips to the next school day`() {
        val state = ScheduleCalculator.stateAt(config, at(28, 10, 0)) as WidgetState.DayOff
        assertEquals(DayOfWeek.THURSDAY, state.next.day)
        assertEquals(1, state.next.daysAhead)
        assertEquals("Биология", state.next.slot.subject)
    }

    @Test
    fun `weekend wraps around to monday`() {
        val state = ScheduleCalculator.stateAt(config, at(31, 12, 0)) as WidgetState.DayOff
        assertEquals(Upcoming(Slot(1, "Математика", LocalTime.of(8, 30), LocalTime.of(9, 15)), DayOfWeek.MONDAY, 2), state.next)
    }

    @Test
    fun `sunday lessons are ignored while sunday is disabled`() {
        val sundayOnly = Config(bells = bells).withSubjects(DayOfWeek.SUNDAY, listOf("Кружок"))
        assertEquals(WidgetState.NotConfigured, ScheduleCalculator.stateAt(sundayOnly, at(26, 10, 0)))

        val enabled = sundayOnly.copy(sundayEnabled = true)
        val state = ScheduleCalculator.stateAt(enabled, at(26, 10, 0)) as WidgetState.DayOff
        assertEquals(DayOfWeek.SUNDAY, state.next.day)
        assertEquals(6, state.next.daysAhead)
    }

    @Test
    fun `only lesson of the week points to the same day next week`() {
        val single = Config(bells = bells).withSubjects(DayOfWeek.MONDAY, listOf("Математика"))
        val state = ScheduleCalculator.stateAt(single, at(26, 8, 40)) as WidgetState.Lesson
        assertEquals(DayOfWeek.MONDAY, state.next?.day)
        assertEquals(7, state.next?.daysAhead)
    }

    @Test
    fun `subject names are trimmed`() {
        val padded = Config(bells = bells).withSubjects(DayOfWeek.MONDAY, listOf("  Алгебра "))
        val state = ScheduleCalculator.stateAt(padded, at(26, 8, 40)) as WidgetState.Lesson
        assertEquals("Алгебра", state.current.subject)
    }

    @Test
    fun `refresh every minute during the school day`() {
        assertEquals(at(26, 9, 31), ScheduleCalculator.nextRefreshAt(config, at(26, 9, 30, 12)))
        assertEquals(at(26, 9, 16), ScheduleCalculator.nextRefreshAt(config, at(26, 9, 15)))
        assertEquals(at(26, 7, 46), ScheduleCalculator.nextRefreshAt(config, at(26, 7, 45, 59)))
    }

    @Test
    fun `refresh at midnight on a day off`() {
        assertEquals(at(27, 0, 0), ScheduleCalculator.nextRefreshAt(config, at(26, 15, 0)))
    }

    @Test
    fun `no refresh when not configured`() {
        assertNull(ScheduleCalculator.nextRefreshAt(Config(), at(26, 15, 0)))
    }
}
