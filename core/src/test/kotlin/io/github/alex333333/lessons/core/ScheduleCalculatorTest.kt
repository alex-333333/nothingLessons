package io.github.alex333333.lessons.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private fun date(day: Int) = LocalDate.of(2026, 10, day)

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        LocalDateTime.of(date(day), LocalTime.of(hour, minute, second))

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
        assertEquals(Upcoming(Slot(3, "История", LocalTime.of(10, 20), LocalTime.of(11, 5)), date(26), 0), state.next)
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
        assertEquals(Upcoming(Slot(1, "Физика", LocalTime.of(8, 30), LocalTime.of(9, 15)), date(27), 1), state.next)
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
        assertEquals(Upcoming(Slot(1, "Физика", LocalTime.of(8, 30), LocalTime.of(9, 15)), date(27), 1), state.next)
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
        assertEquals(Upcoming(Slot(1, "Математика", LocalTime.of(8, 30), LocalTime.of(9, 15)), LocalDate.of(2026, 11, 2), 2), state.next)
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

    // --- Days off, one-day changes and holidays ---

    private fun t(hour: Int, minute: Int) = LocalTime.of(hour, minute)

    /** Wednesday 28th becomes an excursion day with its own short bells. */
    private val excursion = DayOverride(
        date = date(28),
        bells = listOf(Bell(t(9, 0), t(9, 30)), Bell(t(9, 40), t(10, 10))),
        subjects = listOf("Экскурсия", "Музей"),
    )

    @Test
    fun `day off reason tells finished, empty and weekend days apart`() {
        assertEquals(DayOffReason.Finished, (ScheduleCalculator.stateAt(config, at(26, 15, 0)) as WidgetState.DayOff).reason)
        assertEquals(DayOffReason.NoLessons, (ScheduleCalculator.stateAt(config, at(28, 10, 0)) as WidgetState.DayOff).reason)
        assertEquals(DayOffReason.NoLessons, (ScheduleCalculator.stateAt(config, at(31, 10, 0)) as WidgetState.DayOff).reason)
    }

    @Test
    fun `regular lessons are not marked as changed`() {
        val state = ScheduleCalculator.stateAt(config, at(26, 9, 30)) as WidgetState.Lesson
        assertFalse(state.changed)
        assertFalse(state.next!!.changed)
        assertFalse((ScheduleCalculator.stateAt(config, at(26, 9, 15)) as WidgetState.Break).changed)
        assertFalse((ScheduleCalculator.stateAt(config, at(26, 7, 0)) as WidgetState.BeforeSchool).changed)
    }

    @Test
    fun `cancelled today is a day off pointing to the next school day`() {
        val cancelled = config.withCancelled(date(26), true)
        for (time in listOf(at(26, 7, 0), at(26, 9, 30), at(26, 15, 0))) {
            val state = ScheduleCalculator.stateAt(cancelled, time) as WidgetState.DayOff
            assertEquals(DayOffReason.Cancelled, state.reason)
            assertEquals(Upcoming(Slot(1, "Физика", t(8, 30), t(9, 15)), date(27), 1), state.next)
        }
        assertEquals(at(27, 0, 0), ScheduleCalculator.nextRefreshAt(cancelled, at(26, 9, 30)))
    }

    @Test
    fun `cancelled tomorrow is skipped by tonight's day off and the last lesson`() {
        val cancelled = config.withCancelled(date(27), true)
        val tonight = ScheduleCalculator.stateAt(cancelled, at(26, 15, 0)) as WidgetState.DayOff
        assertEquals(DayOffReason.Finished, tonight.reason)
        // Wednesday has no lessons either, so Thursday is next.
        assertEquals(Upcoming(Slot(2, "Биология", t(9, 25), t(10, 10)), date(29), 3), tonight.next)
        assertEquals(DayOfWeek.THURSDAY, tonight.next.day)

        val lastLesson = ScheduleCalculator.stateAt(cancelled, at(26, 11, 30)) as WidgetState.Lesson
        assertEquals(date(29), lastLesson.next?.date)
    }

    @Test
    fun `changed day uses its own bells and subjects`() {
        val changed = config.withOverride(excursion)
        val state = ScheduleCalculator.stateAt(changed, at(28, 9, 10)) as WidgetState.Lesson
        assertTrue(state.changed)
        assertEquals(Slot(1, "Экскурсия", t(9, 0), t(9, 30)), state.current)
        assertEquals(20, state.minutesLeft)
        assertEquals(Upcoming(Slot(2, "Музей", t(9, 40), t(10, 10)), date(28), 0, changed = true), state.next)

        val brk = ScheduleCalculator.stateAt(changed, at(28, 9, 35)) as WidgetState.Break
        assertTrue(brk.changed)
        assertEquals("Музей", brk.next.subject)

        val before = ScheduleCalculator.stateAt(changed, at(28, 8, 0)) as WidgetState.BeforeSchool
        assertTrue(before.changed)
        assertEquals(60, before.minutesUntil)

        val after = ScheduleCalculator.stateAt(changed, at(28, 12, 0)) as WidgetState.DayOff
        assertEquals(DayOffReason.Finished, after.reason)
        assertFalse(after.next.changed)
    }

    @Test
    fun `shortened day ignores the regular bell times`() {
        val short = DayOverride(
            date = date(26),
            bells = listOf(Bell(t(8, 30), t(9, 0)), Bell(t(9, 10), t(9, 40))),
            subjects = listOf("Математика", "Русский"),
        )
        // 9:05 is mid-lesson on a regular Monday, but a break on the shortened one.
        val state = ScheduleCalculator.stateAt(config.withOverride(short), at(26, 9, 5)) as WidgetState.Break
        assertTrue(state.changed)
        assertEquals(Slot(2, "Русский", t(9, 10), t(9, 40)), state.next)
        assertEquals(5, state.minutesUntil)
    }

    @Test
    fun `upcoming lesson from another day's change is marked as changed`() {
        val state = ScheduleCalculator.stateAt(config.withOverride(excursion), at(27, 15, 0)) as WidgetState.DayOff
        assertEquals(Upcoming(Slot(1, "Экскурсия", t(9, 0), t(9, 30)), date(28), 1, changed = true), state.next)
    }

    @Test
    fun `blank subjects in a change are free slots`() {
        val change = config.regularDay(date(26)).let { it.copy(subjects = listOf("", "Русский", "", "")) }
        val changed = config.withOverride(change)
        val before = ScheduleCalculator.stateAt(changed, at(26, 8, 40)) as WidgetState.BeforeSchool
        assertEquals(Slot(2, "Русский", t(9, 25), t(10, 10)), before.next)
        assertTrue(before.changed)
        val after = ScheduleCalculator.stateAt(changed, at(26, 10, 30)) as WidgetState.DayOff
        assertEquals(DayOffReason.Finished, after.reason)
        assertEquals(date(27), after.next.date)

        val empty = config.withOverride(change.copy(subjects = listOf("", " ")))
        val state = ScheduleCalculator.stateAt(empty, at(26, 9, 30)) as WidgetState.DayOff
        assertEquals(DayOffReason.NoLessons, state.reason)
    }

    @Test
    fun `holiday containing today shows days left until the next lesson`() {
        val autumn = Holiday(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 1))
        val inHoliday = config.withHoliday(autumn)
        for ((time, daysLeft) in listOf(at(26, 9, 30) to 7, at(28, 23, 0) to 5)) {
            val state = ScheduleCalculator.stateAt(inHoliday, time) as WidgetState.Holidays
            assertEquals(autumn, state.holiday)
            assertEquals(Upcoming(Slot(1, "Математика", t(8, 30), t(9, 15)), LocalDate.of(2026, 11, 2), daysLeft), state.next)
            assertEquals(daysLeft, state.daysLeft)
        }
        assertEquals(at(29, 0, 0), ScheduleCalculator.nextRefreshAt(inHoliday, at(28, 12, 0)))
    }

    @Test
    fun `holiday beats a change on the same day`() {
        val holiday = Holiday(date(28), date(28))
        val state = ScheduleCalculator.stateAt(config.withOverride(excursion).withHoliday(holiday), at(28, 9, 10))
        assertTrue(state is WidgetState.Holidays)
    }

    @Test
    fun `holiday that ended yesterday no longer matters`() {
        val past = config.withHoliday(Holiday(date(19), date(25)))
        val state = ScheduleCalculator.stateAt(past, at(26, 9, 30)) as WidgetState.Lesson
        assertEquals("Русский", state.current.subject)
    }

    @Test
    fun `holiday starting tomorrow pushes tonight's next lesson past it`() {
        val upcoming = config.withHoliday(Holiday(date(27), date(30)))
        val state = ScheduleCalculator.stateAt(upcoming, at(26, 15, 0)) as WidgetState.DayOff
        assertEquals(LocalDate.of(2026, 11, 2), state.next.date)
        assertEquals(7, state.next.daysAhead)
    }

    @Test
    fun `long summer holiday still finds the first lesson of september`() {
        val summer = Holiday(LocalDate.of(2027, 6, 1), LocalDate.of(2027, 8, 31))
        val now = LocalDateTime.of(LocalDate.of(2027, 6, 10), t(12, 0))
        val state = ScheduleCalculator.stateAt(config.withHoliday(summer), now) as WidgetState.Holidays
        // 1 September 2027 is a Wednesday, which has no lessons.
        assertEquals(LocalDate.of(2027, 9, 2), state.next?.date)
        assertEquals(DayOfWeek.THURSDAY, state.next?.day)
        assertEquals("Биология", state.next?.slot?.subject)
        assertEquals(84, state.daysLeft)
    }

    @Test
    fun `holiday with no lesson in sight counts days until it ends`() {
        val endless = Holiday(date(26), date(26).plusDays(500))
        val state = ScheduleCalculator.stateAt(config.withHoliday(endless), at(26, 9, 30)) as WidgetState.Holidays
        assertNull(state.next)
        assertEquals(501, state.daysLeft)
    }

    @Test
    fun `holiday with start after end is read as the swapped range`() {
        val backwards = config.withHoliday(Holiday(LocalDate.of(2026, 11, 1), date(24)))
        val state = ScheduleCalculator.stateAt(backwards, at(28, 10, 0)) as WidgetState.Holidays
        assertEquals(5, state.daysLeft)
    }

    @Test
    fun `next lesson more than a week away keeps its real date`() {
        val fridays = Config(bells = bells).withSubjects(DayOfWeek.FRIDAY, listOf("Литература"))
            .withCancelled(date(30), true)
        val state = ScheduleCalculator.stateAt(fridays, at(26, 12, 0)) as WidgetState.DayOff
        assertEquals(LocalDate.of(2026, 11, 6), state.next.date)
        assertEquals(DayOfWeek.FRIDAY, state.next.day)
        assertEquals(11, state.next.daysAhead)
    }

    @Test
    fun `a one-day change alone is enough to configure the widget`() {
        val only = Config().withOverride(excursion)
        val state = ScheduleCalculator.stateAt(only, at(27, 12, 0)) as WidgetState.DayOff
        assertEquals(date(28), state.next.date)
        // Once it is over there is nothing left to count down to.
        assertEquals(WidgetState.NotConfigured, ScheduleCalculator.stateAt(only, at(28, 12, 0)))
    }

    @Test
    fun `holidays alone do not configure the widget`() {
        val holidayOnly = Config(bells = bells).withHoliday(Holiday(date(24), date(30)))
        assertEquals(WidgetState.NotConfigured, ScheduleCalculator.stateAt(holidayOnly, at(26, 10, 0)))
        assertNull(ScheduleCalculator.nextRefreshAt(holidayOnly, at(26, 10, 0)))
    }

    @Test
    fun `lessons only beyond the search window count as not configured`() {
        val far = config.withHoliday(Holiday(date(27), date(27).plusDays(500)))
        assertEquals(WidgetState.NotConfigured, ScheduleCalculator.stateAt(far, at(26, 15, 0)))
    }
}
